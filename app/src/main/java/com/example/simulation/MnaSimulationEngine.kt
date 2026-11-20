package com.example.simulation

import com.example.model.CircuitComponent
import com.example.model.CircuitSchematic
import com.example.model.ComponentType
import com.example.model.GridPoint
import com.example.model.MultimeterMode
import com.example.model.WaveformType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

data class MeterReading(
    val componentId: String,
    val primaryDisplay: Double = 0.0,
    val rmsValue: Double = 0.0,
    val avgValue: Double = 0.0,
    val peakToPeak: Double = 0.0,
    val secondaryValue: Double = 0.0, // e.g., current for wattmeter or power
    val resistanceOhms: Double = Double.POSITIVE_INFINITY,
    val unit: String = "V"
)

data class ScopeSample(
    val timeSeconds: Double,
    val ch1: Double?,
    val ch2: Double?,
    val ch3: Double?,
    val ch4: Double?
)

data class SimulationSnapshot(
    val simTimeSeconds: Double = 0.0,
    val nodeVoltages: Map<Int, Double> = emptyMap(),
    val pointVoltages: Map<GridPoint, Double> = emptyMap(),
    val branchCurrents: Map<String, Double> = emptyMap(),
    val componentPowers: Map<String, Double> = emptyMap(),
    val meterReadings: Map<String, MeterReading> = emptyMap(),
    val wireCurrents: Map<String, Double> = emptyMap(),
    val scopeSamples: List<ScopeSample> = emptyList(),
    val hasExplicitGround: Boolean = true,
    val convergenceWarning: String? = null,
    val stepsPerSecond: Long = 0L,
    val isRunning: Boolean = true
)

class MnaSimulationEngine(
    private val solverDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(2)
) {
    companion object {
        private const val G_MIN = 1e-11
        private const val VT = 0.02585
        private const val MAX_EXP_ARG = 38.0
        private const val SCOPE_CAPACITY = 1600
        private const val METER_WINDOW_SAMPLES = 256
    }

    private val scope = CoroutineScope(SupervisorJob() + solverDispatcher)
    private var simJob: Job? = null

    private val _snapshot = MutableStateFlow(SimulationSnapshot())
    val snapshot: StateFlow<SimulationSnapshot> = _snapshot.asStateFlow()

    @Volatile
    private var currentSchematic: CircuitSchematic = CircuitSchematic()
    @Volatile
    private var currentNetlist: CompiledNetlist = NetlistCompiler.compile(currentSchematic)
    @Volatile
    private var isRunning: Boolean = true
    @Volatile
    private var topologyDirty: Boolean = true

    // Persistent transient state across timesteps
    private var simTime = 0.0
    private var lastSolution = DoubleArray(0)
    private val capPrevVoltage = HashMap<String, Double>()
    private val capPrevCurrent = HashMap<String, Double>()
    private val indPrevVoltage = HashMap<String, Double>()
    private val indPrevCurrent = HashMap<String, Double>()

    // Ring buffers for meters & scope
    private val scopeBuffer = ArrayDeque<ScopeSample>(SCOPE_CAPACITY + 16)
    private val meterHistory = HashMap<String, ArrayDeque<Double>>()
    private val meterSecondaryHistory = HashMap<String, ArrayDeque<Double>>()
    private val cachedOhmmeterValues = HashMap<String, Double>()

    private val mainMatrix = MnaMatrix(32)
    private val ohmMatrix = MnaMatrix(32)

    private var frameCounter = 0

    fun updateSchematic(schematic: CircuitSchematic, resetState: Boolean = false) {
        currentSchematic = schematic
        currentNetlist = NetlistCompiler.compile(schematic)
        if (resetState) {
            resetSimulationState()
        } else {
            topologyDirty = true
        }
        // Immediately solve at least one step if paused so UI shows DC state
        if (!isRunning) {
            scope.launch {
                val snap = stepBatchAndBuildSnapshot(steps = 1, dtOverride = null)
                _snapshot.value = snap.copy(isRunning = false)
            }
        }
    }

    fun setRunning(running: Boolean) {
        isRunning = running
        _snapshot.value = _snapshot.value.copy(isRunning = running)
    }

    fun stepOnce() {
        scope.launch {
            val snap = stepBatchAndBuildSnapshot(steps = 1, dtOverride = null)
            _snapshot.value = snap.copy(isRunning = isRunning)
        }
    }

    fun reset() {
        resetSimulationState()
        scope.launch {
            val snap = stepBatchAndBuildSnapshot(steps = 1, dtOverride = null)
            _snapshot.value = snap.copy(isRunning = isRunning)
        }
    }

    private fun resetSimulationState() {
        simTime = 0.0
        lastSolution = DoubleArray(0)
        capPrevVoltage.clear()
        capPrevCurrent.clear()
        indPrevVoltage.clear()
        indPrevCurrent.clear()
        scopeBuffer.clear()
        meterHistory.clear()
        meterSecondaryHistory.clear()
        cachedOhmmeterValues.clear()
        topologyDirty = true
    }

    fun startLoop() {
        if (simJob?.isActive == true) return
        simJob = scope.launch {
            var lastTickNs = System.nanoTime()
            while (isActive) {
                if (isRunning) {
                    val nowNs = System.nanoTime()
                    val elapsedSec = ((nowNs - lastTickNs) / 1e9).coerceIn(0.001, 0.05)
                    lastTickNs = nowNs

                    val schematic = currentSchematic
                    val dt = (schematic.timeStepMicros * 1e-6).coerceIn(1e-7, 0.05)
                    val targetSimAdvance = dt * 12.0 * schematic.simSpeedMultiplier.coerceIn(0.1, 10.0)
                    val subSteps = (targetSimAdvance / dt).toInt().coerceIn(1, 24)

                    val snap = stepBatchAndBuildSnapshot(subSteps, dt, elapsedSec)
                    _snapshot.value = snap
                } else {
                    lastTickNs = System.nanoTime()
                }
                delay(16L) // ~60fps publish cadence
            }
        }
    }

    fun stopLoop() {
        simJob?.cancel()
        simJob = null
    }

    /**
     * Synchronous deterministic solver entry point (also used directly in unit tests).
     */
    fun solveSynchronously(steps: Int = 1, dtSeconds: Double? = null): SimulationSnapshot {
        return stepBatchAndBuildSnapshot(steps, dtSeconds, elapsedWallSec = 0.016)
    }

    private fun stepBatchAndBuildSnapshot(
        steps: Int,
        dtOverride: Double? = null,
        elapsedWallSec: Double = 0.016
    ): SimulationSnapshot {
        val schematic = currentSchematic
        val netlist = currentNetlist
        val comps = schematic.components

        if (comps.isEmpty() || netlist.nodeCount == 0) {
            return SimulationSnapshot(
                simTimeSeconds = simTime,
                hasExplicitGround = netlist.hasExplicitGround,
                isRunning = isRunning
            )
        }

        val dt = dtOverride ?: (schematic.timeStepMicros * 1e-6).coerceIn(1e-7, 0.05)

        // Assign extra MNA branch current rows for Voltage Sources, Inductors, VCVS, CCVS, CCCS sense, Ammeters, Wattmeters
        val branchIndexMap = HashMap<String, Int>() // componentId -> 0-based branch variable index
        val senseBranchIndexMap = HashMap<String, Int>() // for CCVS/CCCS/Wattmeter sense branch
        var branchCount = 0

        for (c in comps) {
            when (c.type) {
                ComponentType.DC_VOLTAGE_SOURCE,
                ComponentType.AC_VOLTAGE_SOURCE,
                ComponentType.INDUCTOR,
                ComponentType.VCVS,
                ComponentType.AMMETER -> {
                    branchIndexMap[c.id] = branchCount++
                }
                ComponentType.CCCS -> {
                    if (c.controlComponentId == null) {
                        senseBranchIndexMap[c.id] = branchCount++
                    }
                }
                ComponentType.CCVS -> {
                    if (c.controlComponentId == null) {
                        senseBranchIndexMap[c.id] = branchCount++
                    }
                    branchIndexMap[c.id] = branchCount++
                }
                ComponentType.WATTMETER -> {
                    branchIndexMap[c.id] = branchCount++
                }
                ComponentType.MULTIMETER -> {
                    if (c.multimeterMode == MultimeterMode.AMPS_DC || c.multimeterMode == MultimeterMode.AMPS_AC_RMS) {
                        branchIndexMap[c.id] = branchCount++
                    }
                }
                else -> {}
            }
        }

        val totalSize = netlist.nodeCount + branchCount
        if (lastSolution.size != totalSize || topologyDirty) {
            val prev = lastSolution
            lastSolution = DoubleArray(totalSize)
            val copyLen = min(prev.size, totalSize)
            for (i in 0 until copyLen) lastSolution[i] = prev[i]
            // Initialize capacitors with initial voltage if reset
            for (c in comps) {
                if (c.type == ComponentType.CAPACITOR && !capPrevVoltage.containsKey(c.id)) {
                    capPrevVoltage[c.id] = c.secondaryValue
                    capPrevCurrent[c.id] = 0.0
                }
                if (c.type == ComponentType.INDUCTOR && !indPrevCurrent.containsKey(c.id)) {
                    indPrevCurrent[c.id] = c.secondaryValue
                    indPrevVoltage[c.id] = 0.0
                }
            }
            topologyDirty = false
        }

        val hasNonlinear = comps.any {
            it.type == ComponentType.DIODE ||
                it.type == ComponentType.NPN_BJT ||
                it.type == ComponentType.PNP_BJT ||
                it.type == ComponentType.NMOS_FET ||
                it.type == ComponentType.PMOS_FET ||
                it.type == ComponentType.OP_AMP
        }

        var warning: String? = null
        val compById = comps.associateBy { it.id }

        for (stepIdx in 0 until steps) {
            simTime += dt

            val maxNrIter = if (hasNonlinear) 28 else 1
            var converged = false

            // Working solution vector for Newton-Raphson iterations
            val nrSol = lastSolution.copyOf()

            for (iter in 0 until maxNrIter) {
                mainMatrix.reset(totalSize)

                // Tiny Gmin to ground on all nodes to prevent singular matrices for floating islands
                for (n in 1..netlist.nodeCount) {
                    mainMatrix.addA(n - 1, n - 1, G_MIN)
                }

                // Stamp all components
                for (c in comps) {
                    val nodes = netlist.componentNodes[c.id] ?: continue
                    when (c.type) {
                        ComponentType.GROUND, ComponentType.SCOPE_PROBE -> Unit

                        ComponentType.RESISTOR -> {
                            val r = max(c.primaryValue, 1e-6)
                            mainMatrix.stampConductance(nodes[0], nodes[1], 1.0 / r)
                        }

                        ComponentType.CAPACITOR -> {
                            val cap = max(c.primaryValue, 1e-15)
                            val geq = (2.0 * cap) / dt
                            val vPrev = capPrevVoltage[c.id] ?: 0.0
                            val iPrev = capPrevCurrent[c.id] ?: 0.0
                            val iHist = geq * vPrev + iPrev
                            mainMatrix.stampConductance(nodes[0], nodes[1], geq)
                            // Current from n+ to n- is geq*(V+ - V-) - iHist
                            // So iHist enters n+ and leaves n-
                            if (nodes[0] > 0) mainMatrix.addB(nodes[0] - 1, iHist)
                            if (nodes[1] > 0) mainMatrix.addB(nodes[1] - 1, -iHist)
                        }

                        ComponentType.INDUCTOR -> {
                            val ind = max(c.primaryValue, 1e-12)
                            val bCol = netlist.nodeCount + (branchIndexMap[c.id] ?: continue)
                            val req = (2.0 * ind) / dt
                            val vPrev = indPrevVoltage[c.id] ?: 0.0
                            val iPrev = indPrevCurrent[c.id] ?: 0.0
                            stampBranchKvl(mainMatrix, nodes[0], nodes[1], bCol)
                            mainMatrix.addA(bCol, bCol, -req)
                            mainMatrix.addB(bCol, -(vPrev + req * iPrev))
                        }

                        ComponentType.DC_VOLTAGE_SOURCE -> {
                            val bCol = netlist.nodeCount + (branchIndexMap[c.id] ?: continue)
                            stampBranchKvl(mainMatrix, nodes[0], nodes[1], bCol)
                            mainMatrix.addB(bCol, c.primaryValue)
                        }

                        ComponentType.AC_VOLTAGE_SOURCE -> {
                            val bCol = netlist.nodeCount + (branchIndexMap[c.id] ?: continue)
                            val vNow = evalSourceWaveform(c, simTime)
                            stampBranchKvl(mainMatrix, nodes[0], nodes[1], bCol)
                            mainMatrix.addB(bCol, vNow)
                        }

                        ComponentType.DC_CURRENT_SOURCE -> {
                            // Arrow from - (nodes[1]) to + (nodes[0]): pushes current into nodes[0]
                            mainMatrix.stampCurrentSource(nodes[1], nodes[0], c.primaryValue)
                        }

                        ComponentType.AC_CURRENT_SOURCE -> {
                            val iNow = evalSourceWaveform(c, simTime)
                            mainMatrix.stampCurrentSource(nodes[1], nodes[0], iNow)
                        }

                        ComponentType.VCVS -> {
                            val bCol = netlist.nodeCount + (branchIndexMap[c.id] ?: continue)
                            val gain = c.primaryValue
                            // V(O+) - V(O-) - gain*(V(S+) - V(S-)) = 0
                            stampBranchKvl(mainMatrix, nodes[2], nodes[3], bCol)
                            if (nodes[0] > 0) mainMatrix.addA(bCol, nodes[0] - 1, -gain)
                            if (nodes[1] > 0) mainMatrix.addA(bCol, nodes[1] - 1, gain)
                        }

                        ComponentType.VCCS -> {
                            val g = c.primaryValue
                            val sP = nodes[0]
                            val sM = nodes[1]
                            val oP = nodes[2]
                            val oM = nodes[3]
                            // Current g*(V(sP)-V(sM)) enters oP (or flows from oP to oM externally -> leaves oP, enters oM)
                            if (oP > 0 && sP > 0) mainMatrix.addA(oP - 1, sP - 1, g)
                            if (oP > 0 && sM > 0) mainMatrix.addA(oP - 1, sM - 1, -g)
                            if (oM > 0 && sP > 0) mainMatrix.addA(oM - 1, sP - 1, -g)
                            if (oM > 0 && sM > 0) mainMatrix.addA(oM - 1, sM - 1, g)
                        }

                        ComponentType.CCCS -> {
                            val fGain = c.primaryValue
                            val ctrlBranch = c.controlComponentId?.let { branchIndexMap[it] }
                                ?: senseBranchIndexMap[c.id]
                            if (senseBranchIndexMap.containsKey(c.id)) {
                                val sCol = netlist.nodeCount + senseBranchIndexMap.getValue(c.id)
                                stampBranchKvl(mainMatrix, nodes[0], nodes[1], sCol)
                            }
                            if (ctrlBranch != null) {
                                val cCol = netlist.nodeCount + ctrlBranch
                                if (nodes[2] > 0) mainMatrix.addA(nodes[2] - 1, cCol, fGain)
                                if (nodes[3] > 0) mainMatrix.addA(nodes[3] - 1, cCol, -fGain)
                            }
                        }

                        ComponentType.CCVS -> {
                            val hGain = c.primaryValue
                            val bCol = netlist.nodeCount + (branchIndexMap[c.id] ?: continue)
                            val ctrlBranch = c.controlComponentId?.let { branchIndexMap[it] }
                                ?: senseBranchIndexMap[c.id]
                            if (senseBranchIndexMap.containsKey(c.id)) {
                                val sCol = netlist.nodeCount + senseBranchIndexMap.getValue(c.id)
                                stampBranchKvl(mainMatrix, nodes[0], nodes[1], sCol)
                            }
                            stampBranchKvl(mainMatrix, nodes[2], nodes[3], bCol)
                            if (ctrlBranch != null) {
                                val cCol = netlist.nodeCount + ctrlBranch
                                mainMatrix.addA(bCol, cCol, -hGain)
                            }
                        }

                        ComponentType.VOLTMETER -> {
                            mainMatrix.stampConductance(nodes[0], nodes[1], 1.0 / max(c.primaryValue, 1e6))
                        }

                        ComponentType.AMMETER -> {
                            val bCol = netlist.nodeCount + (branchIndexMap[c.id] ?: continue)
                            stampBranchKvl(mainMatrix, nodes[0], nodes[1], bCol)
                            // 1 mOhm shunt resistance for realism
                            mainMatrix.addA(bCol, bCol, -1e-3)
                        }

                        ComponentType.OHMMETER -> {
                            // High impedance during live circuit sim so it doesn't load circuit;
                            // injects tiny 1uA probe if powered off, and uses dedicated Thevenin solver for exact Ohms!
                            mainMatrix.stampConductance(nodes[0], nodes[1], 1e-8)
                        }

                        ComponentType.WATTMETER -> {
                            // Terminals 0,1 = Current series coil (0V shunt); Terminals 2,3 = Voltage sense coil (10MOhm)
                            val bCol = netlist.nodeCount + (branchIndexMap[c.id] ?: continue)
                            stampBranchKvl(mainMatrix, nodes[0], nodes[1], bCol)
                            mainMatrix.addA(bCol, bCol, -1e-3)
                            mainMatrix.stampConductance(nodes[2], nodes[3], 1.0 / max(c.primaryValue, 1e6))
                        }

                        ComponentType.MULTIMETER -> {
                            when (c.multimeterMode) {
                                MultimeterMode.AMPS_DC, MultimeterMode.AMPS_AC_RMS -> {
                                    val bCol = netlist.nodeCount + (branchIndexMap[c.id] ?: continue)
                                    stampBranchKvl(mainMatrix, nodes[0], nodes[1], bCol)
                                    mainMatrix.addA(bCol, bCol, -1e-3)
                                }
                                MultimeterMode.DIODE_VF -> {
                                    // 1mA forward test current from + to -
                                    mainMatrix.stampConductance(nodes[0], nodes[1], 1e-7)
                                    mainMatrix.stampCurrentSource(nodes[1], nodes[0], 1e-3)
                                }
                                else -> {
                                    mainMatrix.stampConductance(nodes[0], nodes[1], 1.0 / max(c.primaryValue, 1e6))
                                }
                            }
                        }

                        ComponentType.DIODE -> {
                            stampDiode(mainMatrix, c, nodes[0], nodes[1], nrSol)
                        }

                        ComponentType.NPN_BJT -> {
                            stampBjt(mainMatrix, c, nodes[0], nodes[1], nodes[2], polarity = 1.0, nrSol)
                        }

                        ComponentType.PNP_BJT -> {
                            stampBjt(mainMatrix, c, nodes[0], nodes[1], nodes[2], polarity = -1.0, nrSol)
                        }

                        ComponentType.NMOS_FET -> {
                            stampMosfet(mainMatrix, c, nodes[0], nodes[1], nodes[2], polarity = 1.0, nrSol)
                        }

                        ComponentType.PMOS_FET -> {
                            stampMosfet(mainMatrix, c, nodes[0], nodes[1], nodes[2], polarity = -1.0, nrSol)
                        }

                        ComponentType.OP_AMP -> {
                            stampOpAmp(mainMatrix, c, nodes[0], nodes[1], nodes[2], nrSol)
                        }
                    }
                }

                if (!mainMatrix.solve()) {
                    warning = "Singular circuit matrix (check floating nodes or shorted voltage sources)"
                    break
                }

                // Check NR convergence
                var maxDelta = 0.0
                for (i in 0 until netlist.nodeCount) {
                    val d = abs(mainMatrix.x[i] - nrSol[i])
                    if (d > maxDelta) maxDelta = d
                    nrSol[i] = if (hasNonlinear && d > 5.0) {
                        nrSol[i] + 5.0 * kotlin.math.sign(mainMatrix.x[i] - nrSol[i])
                    } else {
                        mainMatrix.x[i]
                    }
                }
                for (i in netlist.nodeCount until totalSize) {
                    val d = abs(mainMatrix.x[i] - nrSol[i])
                    if (d > maxDelta) maxDelta = d
                    nrSol[i] = mainMatrix.x[i]
                }

                if (!hasNonlinear || maxDelta < 1e-5) {
                    converged = true
                    break
                }
            }

            if (hasNonlinear && !converged && warning == null) {
                // Keep best estimate; only warn if wildly diverged
                val maxV = nrSol.take(netlist.nodeCount).maxOfOrNull { abs(it) } ?: 0.0
                if (maxV > 1e5) warning = "Nonlinear solver instability detected"
            }

            lastSolution = nrSol

            // Update reactive companion history
            for (c in comps) {
                val nodes = netlist.componentNodes[c.id] ?: continue
                when (c.type) {
                    ComponentType.CAPACITOR -> {
                        val cap = max(c.primaryValue, 1e-15)
                        val geq = (2.0 * cap) / dt
                        val vNow = nodeV(nrSol, nodes[0]) - nodeV(nrSol, nodes[1])
                        val vPrev = capPrevVoltage[c.id] ?: 0.0
                        val iPrev = capPrevCurrent[c.id] ?: 0.0
                        val iNow = geq * (vNow - vPrev) - iPrev
                        capPrevVoltage[c.id] = vNow
                        capPrevCurrent[c.id] = iNow
                    }
                    ComponentType.INDUCTOR -> {
                        val bCol = netlist.nodeCount + (branchIndexMap[c.id] ?: continue)
                        val vNow = nodeV(nrSol, nodes[0]) - nodeV(nrSol, nodes[1])
                        val iNow = nrSol[bCol]
                        indPrevVoltage[c.id] = vNow
                        indPrevCurrent[c.id] = iNow
                    }
                    else -> {}
                }
            }

            // Record oscilloscope & meter samples per step
            recordScopeAndMeters(comps, netlist, nrSol, branchIndexMap, compById, dt)
        }

        // Compute Ohms asynchronously / periodically on parallel worker if any Ohmmeter or DMM(OHMS) exists
        frameCounter++
        val needsOhms = comps.any {
            it.type == ComponentType.OHMMETER ||
                (it.type == ComponentType.MULTIMETER && it.multimeterMode == MultimeterMode.OHMS)
        }
        if (needsOhms && (frameCounter % 4 == 1 || steps == 1)) {
            val ohmMeters = comps.filter {
                it.type == ComponentType.OHMMETER ||
                    (it.type == ComponentType.MULTIMETER && it.multimeterMode == MultimeterMode.OHMS)
            }
            for (om in ohmMeters) {
                cachedOhmmeterValues[om.id] = solveTheveninOhms(om, comps, netlist)
            }
        }

        // Build snapshot maps
        val nodeVoltages = HashMap<Int, Double>(netlist.nodeCount + 1)
        nodeVoltages[0] = 0.0
        for (n in 1..netlist.nodeCount) {
            nodeVoltages[n] = lastSolution[n - 1]
        }
        val pointVoltages = HashMap<GridPoint, Double>(netlist.pointToNode.size)
        for ((pt, n) in netlist.pointToNode) {
            pointVoltages[pt] = nodeVoltages[n] ?: 0.0
        }

        val branchCurrents = HashMap<String, Double>(comps.size)
        val componentPowers = HashMap<String, Double>(comps.size)
        for (c in comps) {
            val nodes = netlist.componentNodes[c.id] ?: continue
            val i = computeComponentCurrent(c, nodes, lastSolution, netlist.nodeCount, branchIndexMap)
            branchCurrents[c.id] = i
            val vDrop = if (nodes.size >= 2) nodeV(lastSolution, nodes[0]) - nodeV(lastSolution, nodes[1]) else 0.0
            componentPowers[c.id] = abs(vDrop * i)
        }

        val meterReadings = buildMeterReadings(comps, netlist, lastSolution, branchIndexMap, branchCurrents)
        val wireCurrents = estimateWireCurrents(schematic, netlist, branchCurrents)

        val stepsPerSec = if (elapsedWallSec > 0) (steps / elapsedWallSec).toLong() else 0L

        return SimulationSnapshot(
            simTimeSeconds = simTime,
            nodeVoltages = nodeVoltages,
            pointVoltages = pointVoltages,
            branchCurrents = branchCurrents,
            componentPowers = componentPowers,
            meterReadings = meterReadings,
            wireCurrents = wireCurrents,
            scopeSamples = scopeBuffer.toList(),
            hasExplicitGround = netlist.hasExplicitGround,
            convergenceWarning = warning,
            stepsPerSecond = stepsPerSec,
            isRunning = isRunning
        )
    }

    private fun stampBranchKvl(m: MnaMatrix, nPlus: Int, nMinus: Int, branchRowCol: Int) {
        if (nPlus > 0) {
            m.addA(nPlus - 1, branchRowCol, 1.0)
            m.addA(branchRowCol, nPlus - 1, 1.0)
        }
        if (nMinus > 0) {
            m.addA(nMinus - 1, branchRowCol, -1.0)
            m.addA(branchRowCol, nMinus - 1, -1.0)
        }
    }

    private fun evalSourceWaveform(c: CircuitComponent, t: Double): Double {
        val amp = c.primaryValue
        val freq = max(c.secondaryValue, 0.0)
        val offset = c.tertiaryValue
        val phaseRad = Math.toRadians(c.quaternaryValue)
        if (c.waveform == WaveformType.DC || freq <= 0.0) {
            return amp + offset
        }
        val omega = 2.0 * PI * freq
        val angle = omega * t + phaseRad
        val wave = when (c.waveform) {
            WaveformType.DC -> 1.0
            WaveformType.SINE -> sin(angle)
            WaveformType.SQUARE -> if (sin(angle) >= 0.0) 1.0 else -1.0
            WaveformType.TRIANGLE -> {
                val frac = ((freq * t + c.quaternaryValue / 360.0) % 1.0 + 1.0) % 1.0
                when {
                    frac < 0.25 -> 4.0 * frac
                    frac < 0.75 -> 2.0 - 4.0 * frac
                    else -> -4.0 + 4.0 * frac
                }
            }
            WaveformType.PULSE -> {
                val frac = ((freq * t + c.quaternaryValue / 360.0) % 1.0 + 1.0) % 1.0
                if (frac < 0.5) 1.0 else 0.0
            }
        }
        return amp * wave + offset
    }

    private fun stampDiode(m: MnaMatrix, c: CircuitComponent, nA: Int, nK: Int, sol: DoubleArray) {
        val iSat = max(c.primaryValue, 1e-16)
        val nIdeality = max(c.secondaryValue, 1.0)
        val nVt = nIdeality * VT
        val vz = c.tertiaryValue

        val vd = (nodeV(sol, nA) - nodeV(sol, nK)).coerceIn(-100.0, 2.0)
        val expArg = (vd / nVt).coerceAtMost(MAX_EXP_ARG)
        val expVal = exp(expArg)
        var id = iSat * (expVal - 1.0) + G_MIN * vd
        var gd = (iSat / nVt) * expVal + G_MIN

        if (vz > 0.1 && vd < -vz + 0.5) {
            val revArg = ((-vd - vz) / VT).coerceAtMost(MAX_EXP_ARG)
            val revExp = exp(revArg)
            id -= iSat * (revExp - 1.0)
            gd += (iSat / VT) * revExp
        }

        val iEq = id - gd * vd
        m.stampConductance(nA, nK, gd)
        m.stampCurrentSource(nA, nK, iEq)
    }

    private fun stampBjt(
        m: MnaMatrix,
        c: CircuitComponent,
        nB: Int,
        nC: Int,
        nE: Int,
        polarity: Double,
        sol: DoubleArray
    ) {
        val betaF = max(c.primaryValue, 1.0)
        val betaR = 2.0
        val iSat = max(c.secondaryValue, 1e-16)
        val vaf = max(c.tertiaryValue, 10.0)

        val vB = nodeV(sol, nB)
        val vC = nodeV(sol, nC)
        val vE = nodeV(sol, nE)

        val vbe = (polarity * (vB - vE)).coerceIn(-20.0, 1.2)
        val vbc = (polarity * (vB - vC)).coerceIn(-20.0, 1.2)

        val eBe = exp((vbe / VT).coerceAtMost(MAX_EXP_ARG))
        val eBc = exp((vbc / VT).coerceAtMost(MAX_EXP_ARG))

        val iF = iSat * (eBe - 1.0)
        val gF = (iSat / VT) * eBe
        val iR = iSat * (eBc - 1.0)
        val gR = (iSat / VT) * eBc

        val vce = vbe - vbc
        val early = 1.0 + max(0.0, vce) / vaf
        val iCt = (iF - iR) * early

        val iB = polarity * (iF / betaF + iR / betaR)
        val iC = polarity * (iCt - iR / betaR)

        val gPi = gF / betaF + G_MIN
        val gMu = gR / betaR + G_MIN
        val gM = gF * early
        val gBc = gR * early + gR / betaR + G_MIN

        // Stamp base-emitter and base-collector linearized conductances
        m.stampConductance(nB, nE, gPi)
        m.stampConductance(nB, nC, gMu)

        // Stamp collector-emitter transconductance gm*(V_B - V_E)
        if (nC > 0 && nB > 0) m.addA(nC - 1, nB - 1, gM)
        if (nC > 0 && nE > 0) m.addA(nC - 1, nE - 1, -gM)
        if (nE > 0 && nB > 0) m.addA(nE - 1, nB - 1, -gM)
        if (nE > 0) m.addA(nE - 1, nE - 1, gM)

        // Stamp collector-base output conductance gBc*(V_C - V_B)
        m.stampConductance(nC, nB, gBc)

        // Residual currents at operating point
        val dVbE = vB - vE
        val dVbC = vB - vC
        val iBEq = iB - gPi * dVbE - gMu * dVbC
        val iCEq = iC - gM * dVbE + gBc * dVbC
        val iEEq = -(iBEq + iCEq)

        if (nB > 0) m.addB(nB - 1, -iBEq)
        if (nC > 0) m.addB(nC - 1, -iCEq)
        if (nE > 0) m.addB(nE - 1, -iEEq)
    }

    private fun stampMosfet(
        m: MnaMatrix,
        c: CircuitComponent,
        nG: Int,
        nD: Int,
        nS: Int,
        polarity: Double,
        sol: DoubleArray
    ) {
        val kp = max(c.primaryValue, 1e-6)
        val vth = abs(c.secondaryValue)
        val lambda = max(c.tertiaryValue, 0.0)

        val vG = nodeV(sol, nG)
        val vD = nodeV(sol, nD)
        val vS = nodeV(sol, nS)

        val rawVds = polarity * (vD - vS)
        val reversed = rawVds < 0.0
        val effD = if (reversed) nS else nD
        val effS = if (reversed) nD else nS
        val effVD = nodeV(sol, effD)
        val effVS = nodeV(sol, effS)

        val vgs = polarity * (vG - effVS)
        val vds = polarity * (effVD - effVS)

        val idInt: Double
        val gm: Double
        val gds: Double

        if (vgs <= vth) {
            idInt = G_MIN * vds
            gm = 0.0
            gds = G_MIN
        } else {
            val vov = vgs - vth
            if (vds < vov) {
                // Triode
                idInt = kp * (vov * vds - 0.5 * vds * vds) * (1.0 + lambda * vds)
                gm = kp * vds * (1.0 + lambda * vds)
                gds = kp * (vov - vds) * (1.0 + lambda * vds) +
                    kp * (vov * vds - 0.5 * vds * vds) * lambda + G_MIN
            } else {
                // Saturation
                idInt = 0.5 * kp * vov * vov * (1.0 + lambda * vds)
                gm = kp * vov * (1.0 + lambda * vds)
                gds = 0.5 * kp * vov * vov * lambda + G_MIN
            }
        }

        val iDrain = polarity * idInt
        m.stampConductance(effD, effS, gds)

        if (effD > 0 && nG > 0) m.addA(effD - 1, nG - 1, gm)
        if (effD > 0 && effS > 0) m.addA(effD - 1, effS - 1, -gm)
        if (effS > 0 && nG > 0) m.addA(effS - 1, nG - 1, -gm)
        if (effS > 0) m.addA(effS - 1, effS - 1, gm)

        // Gate leakage G_MIN to prevent completely floating gate
        m.stampConductance(nG, effS, G_MIN)

        val iEq = iDrain - gds * (effVD - effVS) - gm * (vG - effVS)
        m.stampCurrentSource(effD, effS, iEq)
    }

    private fun stampOpAmp(
        m: MnaMatrix,
        c: CircuitComponent,
        nPlus: Int,
        nMinus: Int,
        nOut: Int,
        sol: DoubleArray
    ) {
        val aol = max(c.primaryValue, 100.0)
        val vcc = c.secondaryValue
        val vee = c.tertiaryValue
        val vMid = 0.5 * (vcc + vee)
        val vSpan = max(0.5 * (vcc - vee), 1.0)

        // High differential input resistance 10 MOhm
        m.stampConductance(nPlus, nMinus, 1e-7)

        val vDiff = nodeV(sol, nPlus) - nodeV(sol, nMinus)
        val arg = ((aol * vDiff) / vSpan).coerceIn(-20.0, 20.0)
        val th = tanh(arg)
        val sech2 = 1.0 - th * th
        val vIdeal = vMid + vSpan * th
        val effGain = aol * sech2 + 1e-4

        val rOut = 50.0
        val gOut = 1.0 / rOut

        // Output impedance to ground + VCVS Norton stamp: I_out = gOut*(V_out - (vIdeal + effGain*(vDiff_new - vDiff)))
        if (nOut > 0) m.addA(nOut - 1, nOut - 1, gOut)
        if (nOut > 0 && nPlus > 0) m.addA(nOut - 1, nPlus - 1, -gOut * effGain)
        if (nOut > 0 && nMinus > 0) m.addA(nOut - 1, nMinus - 1, gOut * effGain)

        val vOffset = vIdeal - effGain * vDiff
        if (nOut > 0) m.addB(nOut - 1, gOut * vOffset)
    }

    private fun computeComponentCurrent(
        c: CircuitComponent,
        nodes: List<Int>,
        sol: DoubleArray,
        nodeCount: Int,
        branchIndexMap: Map<String, Int>
    ): Double {
        return when (c.type) {
            ComponentType.RESISTOR -> {
                val v = nodeV(sol, nodes[0]) - nodeV(sol, nodes[1])
                v / max(c.primaryValue, 1e-6)
            }
            ComponentType.CAPACITOR -> capPrevCurrent[c.id] ?: 0.0
            ComponentType.DC_VOLTAGE_SOURCE,
            ComponentType.AC_VOLTAGE_SOURCE,
            ComponentType.INDUCTOR,
            ComponentType.VCVS,
            ComponentType.CCVS,
            ComponentType.AMMETER,
            ComponentType.WATTMETER -> {
                val bIdx = branchIndexMap[c.id] ?: return 0.0
                sol[nodeCount + bIdx]
            }
            ComponentType.DC_CURRENT_SOURCE -> c.primaryValue
            ComponentType.AC_CURRENT_SOURCE -> evalSourceWaveform(c, simTime)
            ComponentType.VCCS -> {
                val vSense = nodeV(sol, nodes[0]) - nodeV(sol, nodes[1])
                c.primaryValue * vSense
            }
            ComponentType.CCCS -> {
                val ctrlIdx = c.controlComponentId?.let { branchIndexMap[it] } ?: return 0.0
                c.primaryValue * sol[nodeCount + ctrlIdx]
            }
            ComponentType.DIODE -> {
                val vd = (nodeV(sol, nodes[0]) - nodeV(sol, nodes[1])).coerceIn(-100.0, 2.0)
                val iSat = max(c.primaryValue, 1e-16)
                val nVt = max(c.secondaryValue, 1.0) * VT
                var id = iSat * (exp((vd / nVt).coerceAtMost(MAX_EXP_ARG)) - 1.0)
                if (c.tertiaryValue > 0.1 && vd < -c.tertiaryValue + 0.5) {
                    id -= iSat * (exp(((-vd - c.tertiaryValue) / VT).coerceAtMost(MAX_EXP_ARG)) - 1.0)
                }
                id
            }
            ComponentType.MULTIMETER -> {
                val bIdx = branchIndexMap[c.id]
                if (bIdx != null) sol[nodeCount + bIdx] else 0.0
            }
            ComponentType.VOLTMETER -> {
                (nodeV(sol, nodes[0]) - nodeV(sol, nodes[1])) / max(c.primaryValue, 1e6)
            }
            ComponentType.OP_AMP -> {
                val vDiff = nodeV(sol, nodes[0]) - nodeV(sol, nodes[1])
                val vcc = c.secondaryValue
                val vee = c.tertiaryValue
                val vMid = 0.5 * (vcc + vee)
                val vSpan = max(0.5 * (vcc - vee), 1.0)
                val vIdeal = vMid + vSpan * tanh(((max(c.primaryValue, 100.0) * vDiff) / vSpan).coerceIn(-20.0, 20.0))
                (vIdeal - nodeV(sol, nodes[2])) / 50.0
            }
            else -> 0.0
        }
    }

    private fun recordScopeAndMeters(
        comps: List<CircuitComponent>,
        netlist: CompiledNetlist,
        sol: DoubleArray,
        branchIndexMap: Map<String, Int>,
        compById: Map<String, CircuitComponent>,
        dt: Double
    ) {
        var ch1: Double? = null
        var ch2: Double? = null
        var ch3: Double? = null
        var ch4: Double? = null

        for (c in comps) {
            val nodes = netlist.componentNodes[c.id] ?: continue
            if (c.type == ComponentType.SCOPE_PROBE) {
                val v = nodeV(sol, nodes[0])
                when (c.probeChannel) {
                    1 -> ch1 = v
                    2 -> ch2 = v
                    3 -> ch3 = v
                    4 -> ch4 = v
                }
            } else if (c.type == ComponentType.VOLTMETER ||
                c.type == ComponentType.AMMETER ||
                c.type == ComponentType.WATTMETER ||
                c.type == ComponentType.MULTIMETER
            ) {
                val valToTrack = when (c.type) {
                    ComponentType.VOLTMETER -> nodeV(sol, nodes[0]) - nodeV(sol, nodes[1])
                    ComponentType.AMMETER -> {
                        val bIdx = branchIndexMap[c.id]
                        if (bIdx != null) sol[netlist.nodeCount + bIdx] else 0.0
                    }
                    ComponentType.WATTMETER -> {
                        val bIdx = branchIndexMap[c.id]
                        val i = if (bIdx != null) sol[netlist.nodeCount + bIdx] else 0.0
                        val v = nodeV(sol, nodes[2]) - nodeV(sol, nodes[3])
                        val secDeque = meterSecondaryHistory.getOrPut(c.id) { ArrayDeque(METER_WINDOW_SAMPLES) }
                        if (secDeque.size >= METER_WINDOW_SAMPLES) secDeque.removeFirst()
                        secDeque.addLast(v)
                        v * i
                    }
                    ComponentType.MULTIMETER -> {
                        when (c.multimeterMode) {
                            MultimeterMode.AMPS_DC, MultimeterMode.AMPS_AC_RMS -> {
                                val bIdx = branchIndexMap[c.id]
                                if (bIdx != null) sol[netlist.nodeCount + bIdx] else 0.0
                            }
                            MultimeterMode.WATTS -> {
                                val v = nodeV(sol, nodes[0]) - nodeV(sol, nodes[1])
                                val i = v / max(c.primaryValue, 1e6)
                                v * i
                            }
                            else -> nodeV(sol, nodes[0]) - nodeV(sol, nodes[1])
                        }
                    }
                    else -> 0.0
                }
                val deque = meterHistory.getOrPut(c.id) { ArrayDeque(METER_WINDOW_SAMPLES) }
                if (deque.size >= METER_WINDOW_SAMPLES) deque.removeFirst()
                deque.addLast(valToTrack)
            }
        }

        // If user hasn't placed Scope Probes yet, auto-record nothing unless probes exist
        if (ch1 != null || ch2 != null || ch3 != null || ch4 != null) {
            if (scopeBuffer.size >= SCOPE_CAPACITY) {
                scopeBuffer.removeFirst()
            }
            scopeBuffer.addLast(ScopeSample(simTime, ch1, ch2, ch3, ch4))
        }
    }

    private fun buildMeterReadings(
        comps: List<CircuitComponent>,
        netlist: CompiledNetlist,
        sol: DoubleArray,
        branchIndexMap: Map<String, Int>,
        branchCurrents: Map<String, Double>
    ): Map<String, MeterReading> {
        val result = HashMap<String, MeterReading>()
        for (c in comps) {
            val nodes = netlist.componentNodes[c.id] ?: continue
            val hist = meterHistory[c.id]
            val avg = if (hist != null && hist.isNotEmpty()) hist.average() else 0.0
            val rms = if (hist != null && hist.isNotEmpty()) sqrt(hist.sumOf { it * it } / hist.size) else 0.0
            val vpp = if (hist != null && hist.isNotEmpty()) (hist.max() - hist.min()) else 0.0

            when (c.type) {
                ComponentType.VOLTMETER -> {
                    val inst = nodeV(sol, nodes[0]) - nodeV(sol, nodes[1])
                    val isAc = vpp > 0.05 && abs(avg) < 0.75 * rms
                    result[c.id] = MeterReading(
                        componentId = c.id,
                        primaryDisplay = if (isAc) rms else inst,
                        rmsValue = rms,
                        avgValue = avg,
                        peakToPeak = vpp,
                        unit = "V"
                    )
                }
                ComponentType.AMMETER -> {
                    val inst = branchCurrents[c.id] ?: 0.0
                    val isAc = vpp > 1e-4 && abs(avg) < 0.75 * rms
                    result[c.id] = MeterReading(
                        componentId = c.id,
                        primaryDisplay = if (isAc) rms else inst,
                        rmsValue = rms,
                        avgValue = avg,
                        peakToPeak = vpp,
                        unit = "A"
                    )
                }
                ComponentType.OHMMETER -> {
                    val ohms = cachedOhmmeterValues[c.id] ?: Double.POSITIVE_INFINITY
                    result[c.id] = MeterReading(
                        componentId = c.id,
                        primaryDisplay = ohms,
                        resistanceOhms = ohms,
                        unit = "Ω"
                    )
                }
                ComponentType.WATTMETER -> {
                    val i = branchCurrents[c.id] ?: 0.0
                    val v = nodeV(sol, nodes[2]) - nodeV(sol, nodes[3])
                    val pInst = v * i
                    result[c.id] = MeterReading(
                        componentId = c.id,
                        primaryDisplay = avg,
                        rmsValue = rms,
                        avgValue = avg,
                        secondaryValue = pInst,
                        unit = "W"
                    )
                }
                ComponentType.MULTIMETER -> {
                    val inst = when (c.multimeterMode) {
                        MultimeterMode.AMPS_DC, MultimeterMode.AMPS_AC_RMS -> branchCurrents[c.id] ?: 0.0
                        else -> nodeV(sol, nodes[0]) - nodeV(sol, nodes[1])
                    }
                    val ohms = cachedOhmmeterValues[c.id] ?: Double.POSITIVE_INFINITY
                    val display = when (c.multimeterMode) {
                        MultimeterMode.VOLTS_DC -> avg
                        MultimeterMode.VOLTS_AC_RMS -> rms
                        MultimeterMode.AMPS_DC -> avg
                        MultimeterMode.AMPS_AC_RMS -> rms
                        MultimeterMode.OHMS -> ohms
                        MultimeterMode.WATTS -> abs(avg * (inst / max(c.primaryValue, 1e6)))
                        MultimeterMode.DIODE_VF -> inst
                    }
                    result[c.id] = MeterReading(
                        componentId = c.id,
                        primaryDisplay = display,
                        rmsValue = rms,
                        avgValue = avg,
                        peakToPeak = vpp,
                        resistanceOhms = ohms,
                        unit = c.multimeterMode.unit
                    )
                }
                else -> {}
            }
        }
        return result
    }

    /**
     * Computes exact Thevenin equivalent resistance between the two terminals of an Ohmmeter
     * by deactivating all independent sources (short V, open I), opening C, shorting L, and injecting 1A.
     */
    private fun solveTheveninOhms(
        meter: CircuitComponent,
        comps: List<CircuitComponent>,
        netlist: CompiledNetlist
    ): Double {
        val meterNodes = netlist.componentNodes[meter.id] ?: return Double.POSITIVE_INFINITY
        if (meterNodes.size < 2) return Double.POSITIVE_INFINITY
        val nPlus = meterNodes[0]
        val nMinus = meterNodes[1]
        if (nPlus == nMinus) return 0.0

        var bCount = 0
        val bMap = HashMap<String, Int>()
        for (c in comps) {
            if (c.type == ComponentType.DC_VOLTAGE_SOURCE ||
                c.type == ComponentType.AC_VOLTAGE_SOURCE ||
                c.type == ComponentType.INDUCTOR ||
                c.type == ComponentType.AMMETER ||
                c.type == ComponentType.WATTMETER ||
                c.type == ComponentType.VCVS ||
                (c.type == ComponentType.MULTIMETER &&
                    (c.multimeterMode == MultimeterMode.AMPS_DC || c.multimeterMode == MultimeterMode.AMPS_AC_RMS))
            ) {
                bMap[c.id] = bCount++
            }
        }
        val size = netlist.nodeCount + bCount
        ohmMatrix.reset(size)

        for (n in 1..netlist.nodeCount) {
            ohmMatrix.addA(n - 1, n - 1, 1e-12)
        }

        for (c in comps) {
            if (c.id == meter.id) continue
            val nodes = netlist.componentNodes[c.id] ?: continue
            when (c.type) {
                ComponentType.RESISTOR -> ohmMatrix.stampConductance(nodes[0], nodes[1], 1.0 / max(c.primaryValue, 1e-6))
                ComponentType.DC_VOLTAGE_SOURCE,
                ComponentType.AC_VOLTAGE_SOURCE,
                ComponentType.INDUCTOR -> {
                    val bCol = netlist.nodeCount + (bMap[c.id] ?: continue)
                    stampBranchKvl(ohmMatrix, nodes[0], nodes[1], bCol) // 0V short
                }
                ComponentType.AMMETER,
                ComponentType.WATTMETER -> {
                    val bCol = netlist.nodeCount + (bMap[c.id] ?: continue)
                    stampBranchKvl(ohmMatrix, nodes[0], nodes[1], bCol)
                    ohmMatrix.addA(bCol, bCol, -1e-3)
                }
                ComponentType.MULTIMETER -> {
                    if (c.multimeterMode == MultimeterMode.AMPS_DC || c.multimeterMode == MultimeterMode.AMPS_AC_RMS) {
                        val bCol = netlist.nodeCount + (bMap[c.id] ?: continue)
                        stampBranchKvl(ohmMatrix, nodes[0], nodes[1], bCol)
                        ohmMatrix.addA(bCol, bCol, -1e-3)
                    }
                }
                ComponentType.VCVS -> {
                    val bCol = netlist.nodeCount + (bMap[c.id] ?: continue)
                    stampBranchKvl(ohmMatrix, nodes[2], nodes[3], bCol)
                    if (nodes[0] > 0) ohmMatrix.addA(bCol, nodes[0] - 1, -c.primaryValue)
                    if (nodes[1] > 0) ohmMatrix.addA(bCol, nodes[1] - 1, c.primaryValue)
                }
                ComponentType.VCCS -> {
                    val g = c.primaryValue
                    if (nodes[2] > 0 && nodes[0] > 0) ohmMatrix.addA(nodes[2] - 1, nodes[0] - 1, g)
                    if (nodes[2] > 0 && nodes[1] > 0) ohmMatrix.addA(nodes[2] - 1, nodes[1] - 1, -g)
                    if (nodes[3] > 0 && nodes[0] > 0) ohmMatrix.addA(nodes[3] - 1, nodes[0] - 1, -g)
                    if (nodes[3] > 0 && nodes[1] > 0) ohmMatrix.addA(nodes[3] - 1, nodes[1] - 1, g)
                }
                ComponentType.DIODE -> {
                    // Small signal resistance around operating point or 1MOhm off
                    ohmMatrix.stampConductance(nodes[0], nodes[1], 1e-6)
                }
                else -> Unit
            }
        }

        // Inject 1A test current into nPlus, out of nMinus
        if (nPlus > 0) ohmMatrix.addB(nPlus - 1, 1.0)
        if (nMinus > 0) ohmMatrix.addB(nMinus - 1, -1.0)

        if (!ohmMatrix.solve()) return Double.POSITIVE_INFINITY
        val vPlus = if (nPlus > 0) ohmMatrix.x[nPlus - 1] else 0.0
        val vMinus = if (nMinus > 0) ohmMatrix.x[nMinus - 1] else 0.0
        val rTh = abs(vPlus - vMinus)
        return if (rTh > 1e9) Double.POSITIVE_INFINITY else rTh
    }

    private fun estimateWireCurrents(
        schematic: CircuitSchematic,
        netlist: CompiledNetlist,
        branchCurrents: Map<String, Double>
    ): Map<String, Double> {
        val result = HashMap<String, Double>(schematic.wires.size)
        // Estimate average current per electrical node from attached component branch currents
        val nodeMaxCurrent = HashMap<Int, Double>()
        for (c in schematic.components) {
            val i = branchCurrents[c.id] ?: 0.0
            val nodes = netlist.componentNodes[c.id] ?: continue
            for (n in nodes) {
                val cur = nodeMaxCurrent[n] ?: 0.0
                if (abs(i) > abs(cur)) nodeMaxCurrent[n] = i
            }
        }
        for (w in schematic.wires) {
            val firstPt = w.points.firstOrNull() ?: continue
            val node = netlist.pointToNode[firstPt] ?: 0
            result[w.id] = nodeMaxCurrent[node] ?: 0.0
        }
        return result
    }

    private fun nodeV(sol: DoubleArray, node: Int): Double =
        if (node <= 0 || node - 1 >= sol.size) 0.0 else sol[node - 1]
}
