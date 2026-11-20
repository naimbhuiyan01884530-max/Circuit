package com.example

import com.example.data.PresetCircuits
import com.example.model.CircuitComponent
import com.example.model.CircuitSchematic
import com.example.model.ComponentType
import com.example.model.GridPoint
import com.example.model.WireSegment
import com.example.simulation.MnaSimulationEngine
import com.example.ui.SmartWireRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MnaSimulationEngineTest {

    @Test
    fun `resistor voltage divider computes exact node voltage and current`() {
        val v1 = CircuitComponent(
            id = "v1", type = ComponentType.DC_VOLTAGE_SOURCE, label = "V1",
            x = -4, y = 0, rotation = 90, primaryValue = 12.0
        )
        val r1 = CircuitComponent(
            id = "r1", type = ComponentType.RESISTOR, label = "R1",
            x = 0, y = -3, rotation = 0, primaryValue = 1000.0
        )
        val r2 = CircuitComponent(
            id = "r2", type = ComponentType.RESISTOR, label = "R2",
            x = 4, y = 0, rotation = 90, primaryValue = 2000.0
        )
        val gnd = CircuitComponent(
            id = "gnd", type = ComponentType.GROUND, label = "GND",
            x = -4, y = 3
        )
        val schematic = CircuitSchematic(
            components = listOf(v1, r1, r2, gnd),
            wires = listOf(
                WireSegment(points = listOf(GridPoint(-4, -2), GridPoint(-4, -3), GridPoint(-2, -3))),
                WireSegment(points = listOf(GridPoint(2, -3), GridPoint(4, -3), GridPoint(4, -2))),
                WireSegment(points = listOf(GridPoint(-4, 2), GridPoint(-4, 3), GridPoint(4, 3), GridPoint(4, 2)))
            )
        )

        val engine = MnaSimulationEngine()
        engine.updateSchematic(schematic, resetState = true)
        val snap = engine.solveSynchronously(steps = 2, dtSeconds = 1e-4)

        val vOut = snap.pointVoltages[GridPoint(4, -2)] ?: 0.0
        assertEquals(8.0, vOut, 1e-3)
        val iR2 = abs(snap.branchCurrents["r2"] ?: 0.0)
        assertEquals(0.004, iR2, 1e-5)
    }

    @Test
    fun `rc step response matches analytical exponential charging at 1 tau`() {
        val v1 = CircuitComponent(
            id = "v1", type = ComponentType.DC_VOLTAGE_SOURCE, label = "V1",
            x = -4, y = 0, rotation = 90, primaryValue = 5.0
        )
        val r1 = CircuitComponent(
            id = "r1", type = ComponentType.RESISTOR, label = "R1",
            x = 0, y = -3, rotation = 0, primaryValue = 1000.0
        )
        val c1 = CircuitComponent(
            id = "c1", type = ComponentType.CAPACITOR, label = "C1",
            x = 4, y = 0, rotation = 90, primaryValue = 1e-6
        )
        val gnd = CircuitComponent(
            id = "gnd", type = ComponentType.GROUND, label = "GND",
            x = 0, y = 3
        )
        val schematic = CircuitSchematic(
            components = listOf(v1, r1, c1, gnd),
            wires = listOf(
                WireSegment(points = listOf(GridPoint(-4, -2), GridPoint(-4, -3), GridPoint(-2, -3))),
                WireSegment(points = listOf(GridPoint(2, -3), GridPoint(4, -3), GridPoint(4, -2))),
                WireSegment(points = listOf(GridPoint(-4, 2), GridPoint(-4, 3), GridPoint(4, 3), GridPoint(4, 2)))
            )
        )

        val engine = MnaSimulationEngine()
        engine.updateSchematic(schematic, resetState = true)
        // tau = R*C = 1ms. Simulate 100 steps of 10us = 1ms.
        val snap = engine.solveSynchronously(steps = 100, dtSeconds = 10e-6)
        val vCap = snap.pointVoltages[GridPoint(4, -2)] ?: 0.0
        val expected = 5.0 * (1.0 - kotlin.math.exp(-1.0)) // ~3.1606 V
        assertEquals(expected, vCap, 0.05)
    }

    @Test
    fun `diode forward drop converges cleanly via Newton-Raphson`() {
        val v1 = CircuitComponent(
            id = "v1", type = ComponentType.DC_VOLTAGE_SOURCE, label = "V1",
            x = -4, y = 0, rotation = 90, primaryValue = 5.0
        )
        val r1 = CircuitComponent(
            id = "r1", type = ComponentType.RESISTOR, label = "R1",
            x = 0, y = -3, rotation = 0, primaryValue = 1000.0
        )
        val d1 = CircuitComponent(
            id = "d1", type = ComponentType.DIODE, label = "D1",
            x = 4, y = 0, rotation = 90
        )
        val gnd = CircuitComponent(
            id = "gnd", type = ComponentType.GROUND, label = "GND",
            x = 0, y = 3
        )
        val schematic = CircuitSchematic(
            components = listOf(v1, r1, d1, gnd),
            wires = listOf(
                WireSegment(points = listOf(GridPoint(-4, -2), GridPoint(-4, -3), GridPoint(-2, -3))),
                WireSegment(points = listOf(GridPoint(2, -3), GridPoint(4, -3), GridPoint(4, -2))),
                WireSegment(points = listOf(GridPoint(-4, 2), GridPoint(-4, 3), GridPoint(4, 3), GridPoint(4, 2)))
            )
        )

        val engine = MnaSimulationEngine()
        engine.updateSchematic(schematic, resetState = true)
        val snap = engine.solveSynchronously(steps = 5, dtSeconds = 1e-4)

        val vAnode = snap.pointVoltages[GridPoint(4, -2)] ?: 0.0
        assertTrue("Expected Silicon diode Vf in 0.55..0.95V, got $vAnode", vAnode in 0.55..0.95)
    }

    @Test
    fun `wheatstone bridge preset computes exact voltmeter ammeter and ohmmeter readings`() {
        val schematic = PresetCircuits.wheatstoneBridge()
        val engine = MnaSimulationEngine()
        engine.updateSchematic(schematic, resetState = true)
        val snap = engine.solveSynchronously(steps = 4, dtSeconds = 1e-4)

        // Left divider: 1k/(1k+1k) * 10V = 5.0V
        // Right divider: 2.2k/(1k+2.2k) * 10V = 6.875V
        // Bridge voltmeter (left - right) = -1.875V
        val vmReading = snap.meterReadings["wb_vm"]
        val vm = vmReading?.primaryDisplay ?: 0.0
        val vLeft = snap.pointVoltages[GridPoint(-1, 0)]
        val vRight = snap.pointVoltages[GridPoint(3, 0)]
        val vTop = snap.pointVoltages[GridPoint(-3, -5)]
        assertEquals(
            "vLeft=$vLeft vRight=$vRight vTop=$vTop reading=$vmReading nodes=${snap.nodeVoltages}",
            -1.875,
            vm,
            0.02
        )

        // Ohmmeter Thevenin across right node: 1k || 2.2k = 687.5 Ohm
        val om = snap.meterReadings["wb_om"]?.resistanceOhms ?: 0.0
        assertEquals(687.5, om, 2.0)

        // Total current from 10V source: 10 / 2k + 10 / 3.2k = 5mA + 3.125mA = 8.125mA
        val dmm = abs(snap.meterReadings["wb_dmm"]?.primaryDisplay ?: 0.0)
        assertEquals(0.008125, dmm, 1e-4)
    }

    @Test
    fun `op amp inverting amplifier preset produces -5x inverted gain`() {
        val schematic = PresetCircuits.opAmpInvertingAmp()
        val engine = MnaSimulationEngine()
        engine.updateSchematic(schematic, resetState = true)
        // 100Hz sine wave: at t = 2.5ms (quarter period), Vin = +1.0V -> Vout = -5.0V
        val snap = engine.solveSynchronously(steps = 25, dtSeconds = 1e-4)
        val vIn = snap.pointVoltages[GridPoint(-8, 0)] ?: 0.0
        val vOut = snap.pointVoltages[GridPoint(4, 0)] ?: 0.0
        assertEquals(-5.0 * vIn, vOut, 0.08)
    }

    @Test
    fun `smart wire router produces orthogonal path avoiding obstacles`() {
        val obstacle = CircuitComponent(
            id = "obs", type = ComponentType.OP_AMP, label = "U1", x = 0, y = 0
        )
        val path = SmartWireRouter.route(GridPoint(-4, 0), GridPoint(4, 0), listOf(obstacle))
        assertTrue(path.size >= 2)
        assertEquals(GridPoint(-4, 0), path.first())
        assertEquals(GridPoint(4, 0), path.last())
        for (i in 0 until path.size - 1) {
            val a = path[i]
            val b = path[i + 1]
            assertTrue("Wire segment must be orthogonal", a.x == b.x || a.y == b.y)
        }
    }
}
