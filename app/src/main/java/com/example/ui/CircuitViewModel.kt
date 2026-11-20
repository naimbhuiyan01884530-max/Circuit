package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CircuitDatabase
import com.example.data.CircuitJsonMapper
import com.example.data.CircuitRepository
import com.example.data.PresetCircuits
import com.example.data.SavedCircuitSummary
import com.example.model.CircuitComponent
import com.example.model.CircuitSchematic
import com.example.model.ComponentType
import com.example.model.GridPoint
import com.example.model.MultimeterMode
import com.example.model.UnitFormatter
import com.example.model.WaveformType
import com.example.model.WireSegment
import com.example.simulation.CompiledNetlist
import com.example.simulation.MnaSimulationEngine
import com.example.simulation.NetlistCompiler
import com.example.simulation.SimulationSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

enum class WorkbenchTool(val label: String) {
    SELECT("Select / Move"),
    WIRE("Smart Wire"),
    PROBE("Probe")
}

enum class ScopeTriggerMode(val label: String) {
    AUTO("Auto"),
    RISING("Rising"),
    FALLING("Falling")
}

data class ScopeConfig(
    val timePerDivMs: Double = 2.0,
    val voltsPerDiv: Double = 2.0,
    val showCh1: Boolean = true,
    val showCh2: Boolean = true,
    val showCh3: Boolean = true,
    val showCh4: Boolean = true,
    val triggerMode: ScopeTriggerMode = ScopeTriggerMode.RISING,
    val triggerLevel: Double = 0.0,
    val xyMode: Boolean = false,
    val frozen: Boolean = false
)

data class ProbeReadout(
    val title: String,
    val voltageText: String,
    val currentText: String?,
    val powerText: String?,
    val extraText: String? = null
)

data class PaletteDragState(
    val type: ComponentType,
    val screenX: Float,
    val screenY: Float,
    val gridPoint: GridPoint?
)

class CircuitViewModel(application: Application) : AndroidViewModel(application) {

    private val database = CircuitDatabase.getInstance(application)
    private val repository = CircuitRepository(database.circuitDao())
    private val engine = MnaSimulationEngine()

    private val _schematic = MutableStateFlow(PresetCircuits.rcLowPassFilter())
    val schematic: StateFlow<CircuitSchematic> = _schematic.asStateFlow()

    private val _netlist = MutableStateFlow(NetlistCompiler.compile(_schematic.value))
    val netlist: StateFlow<CompiledNetlist> = _netlist.asStateFlow()

    val simulationSnapshot: StateFlow<SimulationSnapshot> = engine.snapshot

    val savedCircuits: StateFlow<List<SavedCircuitSummary>> = repository.allCircuits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeTool = MutableStateFlow(WorkbenchTool.SELECT)
    val activeTool: StateFlow<WorkbenchTool> = _activeTool.asStateFlow()

    private val _selectedComponentId = MutableStateFlow<String?>(null)
    val selectedComponentId: StateFlow<String?> = _selectedComponentId.asStateFlow()

    private val _selectedWireId = MutableStateFlow<String?>(null)
    val selectedWireId: StateFlow<String?> = _selectedWireId.asStateFlow()

    private val _wireStartPoint = MutableStateFlow<GridPoint?>(null)
    val wireStartPoint: StateFlow<GridPoint?> = _wireStartPoint.asStateFlow()

    private val _wirePreviewEnd = MutableStateFlow<GridPoint?>(null)
    val wirePreviewEnd: StateFlow<GridPoint?> = _wirePreviewEnd.asStateFlow()

    private val _paletteDrag = MutableStateFlow<PaletteDragState?>(null)
    val paletteDrag: StateFlow<PaletteDragState?> = _paletteDrag.asStateFlow()

    private val _probeReadout = MutableStateFlow<ProbeReadout?>(null)
    val probeReadout: StateFlow<ProbeReadout?> = _probeReadout.asStateFlow()

    private val _isScopeVisible = MutableStateFlow(true)
    val isScopeVisible: StateFlow<Boolean> = _isScopeVisible.asStateFlow()

    private val _scopeConfig = MutableStateFlow(ScopeConfig())
    val scopeConfig: StateFlow<ScopeConfig> = _scopeConfig.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _showVoltageOverlay = MutableStateFlow(true)
    val showVoltageOverlay: StateFlow<Boolean> = _showVoltageOverlay.asStateFlow()

    private val _showCurrentDots = MutableStateFlow(true)
    val showCurrentDots: StateFlow<Boolean> = _showCurrentDots.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    // Undo/Redo stacks
    private val undoStack = ArrayDeque<CircuitSchematic>(32)
    private val redoStack = ArrayDeque<CircuitSchematic>(32)
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var dragStartSchematic: CircuitSchematic? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.ensurePresetsSeeded()
        }
        engine.updateSchematic(_schematic.value, resetState = true)
        engine.startLoop()
    }

    override fun onCleared() {
        engine.stopLoop()
        super.onCleared()
    }

    private fun pushHistory(previous: CircuitSchematic) {
        if (undoStack.size >= 30) undoStack.removeFirst()
        undoStack.addLast(previous)
        redoStack.clear()
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = false
    }

    private fun applySchematic(newSchematic: CircuitSchematic, resetSim: Boolean = false, recordUndo: Boolean = true) {
        if (recordUndo) {
            pushHistory(_schematic.value)
        }
        _schematic.value = newSchematic
        _netlist.value = NetlistCompiler.compile(newSchematic)
        engine.updateSchematic(newSchematic, resetState = resetSim)
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_schematic.value)
        _schematic.value = prev
        _netlist.value = NetlistCompiler.compile(prev)
        engine.updateSchematic(prev, resetState = false)
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_schematic.value)
        _schematic.value = next
        _netlist.value = NetlistCompiler.compile(next)
        engine.updateSchematic(next, resetState = false)
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    fun setTool(tool: WorkbenchTool) {
        _activeTool.value = tool
        _wireStartPoint.value = null
        _wirePreviewEnd.value = null
        if (tool != WorkbenchTool.PROBE) _probeReadout.value = null
    }

    fun toggleRunPause() {
        val running = simulationSnapshot.value.isRunning
        engine.setRunning(!running)
    }

    fun stepSimulation() {
        engine.setRunning(false)
        engine.stepOnce()
    }

    fun resetSimulation() {
        engine.reset()
    }

    fun toggleScopeVisible() {
        _isScopeVisible.value = !_isScopeVisible.value
    }

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    fun toggleVoltageOverlay() {
        _showVoltageOverlay.value = !_showVoltageOverlay.value
    }

    fun toggleCurrentDots() {
        _showCurrentDots.value = !_showCurrentDots.value
    }

    fun updateScopeConfig(transform: (ScopeConfig) -> ScopeConfig) {
        _scopeConfig.value = transform(_scopeConfig.value)
    }

    fun clearSelection() {
        _selectedComponentId.value = null
        _selectedWireId.value = null
        _wireStartPoint.value = null
        _wirePreviewEnd.value = null
    }

    fun selectComponent(id: String?) {
        _selectedComponentId.value = id
        _selectedWireId.value = null
    }

    fun selectWire(id: String?) {
        _selectedWireId.value = id
        _selectedComponentId.value = null
    }

    // --- Component Placement & Dragging ---

    fun nextLabelFor(type: ComponentType, existing: List<CircuitComponent>): String {
        if (type == ComponentType.GROUND) return "GND"
        val prefix = type.prefix
        var idx = 1
        val used = existing.map { it.label }.toSet()
        while ("$prefix$idx" in used) idx++
        return "$prefix$idx"
    }

    fun addComponentAt(type: ComponentType, gridX: Int, gridY: Int) {
        val cur = _schematic.value
        val nextChannel = if (type == ComponentType.SCOPE_PROBE) {
            val used = cur.components.filter { it.type == ComponentType.SCOPE_PROBE }.map { it.probeChannel }.toSet()
            (1..4).firstOrNull { it !in used } ?: 1
        } else 1

        val comp = CircuitComponent(
            type = type,
            label = if (type == ComponentType.SCOPE_PROBE) "CH$nextChannel" else nextLabelFor(type, cur.components),
            x = gridX,
            y = gridY,
            probeChannel = nextChannel
        )
        applySchematic(cur.copy(components = cur.components + comp))
        _selectedComponentId.value = comp.id
        _selectedWireId.value = null
    }

    fun updatePaletteDrag(dragState: PaletteDragState?) {
        _paletteDrag.value = dragState
    }

    fun beginComponentDrag() {
        dragStartSchematic = _schematic.value
    }

    fun moveSelectedComponentTo(id: String, newGridX: Int, newGridY: Int) {
        val cur = _schematic.value
        val oldComp = cur.components.find { it.id == id } ?: return
        if (oldComp.x == newGridX && oldComp.y == newGridY) return
        val dx = newGridX - oldComp.x
        val dy = newGridY - oldComp.y

        // Dynamically re-route wire endpoints attached to old terminals
        val oldTerminals = oldComp.getWorldTerminals().map { it.second }.toSet()
        val updatedWires = cur.wires.map { wire ->
            val pts = wire.points
            if (pts.size < 2) return@map wire
            val firstMoved = if (pts.first() in oldTerminals) GridPoint(pts.first().x + dx, pts.first().y + dy) else pts.first()
            val lastMoved = if (pts.last() in oldTerminals) GridPoint(pts.last().x + dx, pts.last().y + dy) else pts.last()
            if (firstMoved == pts.first() && lastMoved == pts.last()) {
                wire
            } else {
                wire.copy(points = SmartWireRouter.route(firstMoved, lastMoved, cur.components.filter { it.id != id }))
            }
        }

        val updatedComps = cur.components.map {
            if (it.id == id) it.copy(x = newGridX, y = newGridY) else it
        }
        // Apply live without pushing undo on every pixel
        applySchematic(cur.copy(components = updatedComps, wires = updatedWires), resetSim = false, recordUndo = false)
    }

    fun commitComponentDrag() {
        val before = dragStartSchematic
        dragStartSchematic = null
        if (before != null && before != _schematic.value) {
            pushHistory(before)
        }
    }

    fun rotateSelectedComponent() {
        val id = _selectedComponentId.value ?: return
        val cur = _schematic.value
        val updated = cur.components.map {
            if (it.id == id) it.copy(rotation = (it.rotation + 90) % 360) else it
        }
        applySchematic(cur.copy(components = updated))
    }

    fun flipSelectedComponent() {
        val id = _selectedComponentId.value ?: return
        val cur = _schematic.value
        val updated = cur.components.map {
            if (it.id == id) it.copy(flipped = !it.flipped) else it
        }
        applySchematic(cur.copy(components = updated))
    }

    fun duplicateSelectedComponent() {
        val id = _selectedComponentId.value ?: return
        val cur = _schematic.value
        val src = cur.components.find { it.id == id } ?: return
        val copy = src.copy(
            id = UUID.randomUUID().toString(),
            label = nextLabelFor(src.type, cur.components),
            x = src.x + 2,
            y = src.y + 2
        )
        applySchematic(cur.copy(components = cur.components + copy))
        _selectedComponentId.value = copy.id
    }

    fun deleteSelected() {
        val cur = _schematic.value
        val cId = _selectedComponentId.value
        val wId = _selectedWireId.value
        if (cId != null) {
            applySchematic(cur.copy(components = cur.components.filterNot { it.id == cId }))
            _selectedComponentId.value = null
        } else if (wId != null) {
            applySchematic(cur.copy(wires = cur.wires.filterNot { it.id == wId }))
            _selectedWireId.value = null
        }
    }

    fun updateComponent(updated: CircuitComponent) {
        val cur = _schematic.value
        applySchematic(
            cur.copy(components = cur.components.map { if (it.id == updated.id) updated else it })
        )
    }

    // --- Smart Wiring ---

    fun startOrFinishWire(worldX: Float, worldY: Float) {
        val cur = _schematic.value
        val snapped = SmartWireRouter.findNearestSnapTerminalOrWire(worldX, worldY, cur)
            ?: GridPoint(worldX.roundToInt(), worldY.roundToInt())

        val start = _wireStartPoint.value
        if (start == null) {
            _wireStartPoint.value = snapped
            _wirePreviewEnd.value = snapped
        } else if (start != snapped) {
            val routed = SmartWireRouter.route(start, snapped, cur.components)
            val newWire = WireSegment(points = routed)
            applySchematic(cur.copy(wires = cur.wires + newWire))
            _wireStartPoint.value = null
            _wirePreviewEnd.value = null
            _selectedWireId.value = newWire.id
        } else {
            _wireStartPoint.value = null
            _wirePreviewEnd.value = null
        }
    }

    fun updateWirePreview(worldX: Float, worldY: Float) {
        if (_wireStartPoint.value == null) return
        val cur = _schematic.value
        val snapped = SmartWireRouter.findNearestSnapTerminalOrWire(worldX, worldY, cur)
            ?: GridPoint(worldX.roundToInt(), worldY.roundToInt())
        _wirePreviewEnd.value = snapped
    }

    // --- Live Probing ---

    fun probeAt(worldX: Float, worldY: Float) {
        val cur = _schematic.value
        val snap = simulationSnapshot.value
        val comp = SmartWireRouter.findComponentAt(worldX, worldY, cur)
        if (comp != null) {
            val nodes = _netlist.value.componentNodes[comp.id].orEmpty()
            val vDiff = if (nodes.size >= 2) {
                (snap.nodeVoltages[nodes[0]] ?: 0.0) - (snap.nodeVoltages[nodes[1]] ?: 0.0)
            } else {
                snap.nodeVoltages[nodes.firstOrNull() ?: 0] ?: 0.0
            }
            val i = snap.branchCurrents[comp.id] ?: 0.0
            val p = snap.componentPowers[comp.id] ?: abs(vDiff * i)
            val meter = snap.meterReadings[comp.id]
            val extra = when {
                meter != null -> "${meter.componentId.take(0)}Meter: ${UnitFormatter.format(meter.primaryDisplay, meter.unit)}"
                comp.type == ComponentType.RESISTOR -> "R = ${UnitFormatter.format(comp.primaryValue, "Ω")}"
                else -> comp.summaryValueText()
            }
            _probeReadout.value = ProbeReadout(
                title = "${comp.label} (${comp.type.displayName})",
                voltageText = "ΔV = ${UnitFormatter.format(vDiff, "V", 3)}",
                currentText = "I = ${UnitFormatter.format(i, "A", 3)}",
                powerText = "P = ${UnitFormatter.format(p, "W", 3)}",
                extraText = extra
            )
            return
        }

        val wire = SmartWireRouter.findWireAt(worldX, worldY, cur.wires)
        if (wire != null) {
            val pt = wire.points.firstOrNull()
            val nodeId = pt?.let { _netlist.value.pointToNode[it] } ?: 0
            val v = snap.nodeVoltages[nodeId] ?: 0.0
            val i = snap.wireCurrents[wire.id] ?: 0.0
            _probeReadout.value = ProbeReadout(
                title = "Node N$nodeId",
                voltageText = "V = ${UnitFormatter.format(v, "V", 3)}",
                currentText = "I_branch ≈ ${UnitFormatter.format(i, "A", 3)}",
                powerText = null,
                extraText = if (nodeId == 0) "Ground Reference (0 V)" else "Tap Probe + to attach Scope"
            )
            return
        }

        val gp = GridPoint(worldX.roundToInt(), worldY.roundToInt())
        val v = snap.pointVoltages[gp]
        if (v != null) {
            val nodeId = _netlist.value.pointToNode[gp] ?: 0
            _probeReadout.value = ProbeReadout(
                title = "Node N$nodeId (${gp.x}, ${gp.y})",
                voltageText = "V = ${UnitFormatter.format(v, "V", 3)}",
                currentText = null,
                powerText = null
            )
        } else {
            _probeReadout.value = null
        }
    }

    fun dismissProbeReadout() {
        _probeReadout.value = null
    }

    // --- Simulation Settings ---

    fun updateTimeStepAndSpeed(timeStepMicros: Double, speedMultiplier: Double) {
        val cur = _schematic.value
        applySchematic(
            cur.copy(timeStepMicros = timeStepMicros, simSpeedMultiplier = speedMultiplier),
            resetSim = false,
            recordUndo = false
        )
    }

    // --- Local SQLite / JSON Persistence ---

    fun createNewBlankCircuit() {
        val blank = CircuitSchematic(
            name = "New Circuit",
            description = "Custom offline schematic",
            components = listOf(
                CircuitComponent(
                    type = ComponentType.DC_VOLTAGE_SOURCE,
                    label = "V1",
                    x = -4,
                    y = 0,
                    rotation = 90,
                    primaryValue = 5.0
                ),
                CircuitComponent(
                    type = ComponentType.RESISTOR,
                    label = "R1",
                    x = 2,
                    y = 0,
                    rotation = 90,
                    primaryValue = 1000.0
                ),
                CircuitComponent(
                    type = ComponentType.GROUND,
                    label = "GND",
                    x = -4,
                    y = 4
                )
            ),
            wires = listOf(
                WireSegment(points = listOf(GridPoint(-4, -2), GridPoint(-4, -3), GridPoint(2, -3), GridPoint(2, -2))),
                WireSegment(points = listOf(GridPoint(-4, 2), GridPoint(-4, 4))),
                WireSegment(points = listOf(GridPoint(-4, 4), GridPoint(2, 4), GridPoint(2, 2)))
            )
        )
        undoStack.clear()
        redoStack.clear()
        _canUndo.value = false
        _canRedo.value = false
        clearSelection()
        applySchematic(blank, resetSim = true, recordUndo = false)
    }

    fun saveCurrentCircuit(name: String, description: String = _schematic.value.description) {
        val cur = _schematic.value
        val toSave = if (cur.id.startsWith("preset_")) {
            // Save as a new user copy when modifying a preset
            cur.copy(id = UUID.randomUUID().toString(), name = name, description = description)
        } else {
            cur.copy(name = name, description = description)
        }
        _schematic.value = toSave
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveCircuit(toSave, isPreset = false)
            _statusBanner.value = "Saved \"${toSave.name}\" to local SQLite"
        }
    }

    fun loadCircuitFromLibrary(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = repository.loadCircuit(id)
            if (loaded != null) {
                undoStack.clear()
                redoStack.clear()
                _canUndo.value = false
                _canRedo.value = false
                clearSelection()
                _probeReadout.value = null
                _schematic.value = loaded
                _netlist.value = NetlistCompiler.compile(loaded)
                engine.updateSchematic(loaded, resetState = true)
            }
        }
    }

    fun deleteSavedCircuit(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCircuit(id)
        }
    }

    fun exportCurrentJson(): String = CircuitJsonMapper.toJson(_schematic.value)

    fun importCircuitJson(json: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = repository.importFromJson(json)
            res.onSuccess { imported ->
                val active = imported.copy(id = UUID.randomUUID().toString())
                undoStack.clear()
                redoStack.clear()
                _canUndo.value = false
                _canRedo.value = false
                _schematic.value = active
                _netlist.value = NetlistCompiler.compile(active)
                engine.updateSchematic(active, resetState = true)
                onResult(true, "Imported \"${active.name}\"")
            }.onFailure { err ->
                onResult(false, err.message ?: "Invalid JSON format")
            }
        }
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }
}
