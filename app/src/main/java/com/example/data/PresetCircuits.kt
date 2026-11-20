package com.example.data

import com.example.model.CircuitComponent
import com.example.model.CircuitSchematic
import com.example.model.ComponentType
import com.example.model.GridPoint
import com.example.model.MultimeterMode
import com.example.model.WaveformType
import com.example.model.WireSegment

object PresetCircuits {

    fun allPresets(): List<CircuitSchematic> = listOf(
        rcLowPassFilter(),
        fullWaveRectifier(),
        bjtCommonEmitterAmp(),
        opAmpInvertingAmp(),
        rlcResonantTank(),
        nmosInverterSwitch(),
        dependentSourceAmp(),
        wheatstoneBridge()
    )

    fun rcLowPassFilter(): CircuitSchematic {
        // AC Source (left) -> Resistor (top) -> Capacitor (right) -> Ground (bottom)
        val vs = CircuitComponent(
            id = "rc_v1", type = ComponentType.AC_VOLTAGE_SOURCE, label = "V1",
            x = -4, y = 0, rotation = 90, // + at top (-4,-2), - at bottom (-4,2)
            primaryValue = 5.0, secondaryValue = 120.0, waveform = WaveformType.SINE
        )
        val r1 = CircuitComponent(
            id = "rc_r1", type = ComponentType.RESISTOR, label = "R1",
            x = 0, y = -4, rotation = 0, // (-2,-4) to (2,-4)
            primaryValue = 1000.0
        )
        val c1 = CircuitComponent(
            id = "rc_c1", type = ComponentType.CAPACITOR, label = "C1",
            x = 4, y = 0, rotation = 90, // (4,-2) to (4,2)
            primaryValue = 1e-6
        )
        val vm = CircuitComponent(
            id = "rc_vm", type = ComponentType.VOLTMETER, label = "VM1",
            x = 8, y = 0, rotation = 90 // (8,-2) to (8,2)
        )
        val gnd = CircuitComponent(
            id = "rc_gnd", type = ComponentType.GROUND, label = "GND",
            x = 0, y = 4
        )
        val ch1 = CircuitComponent(
            id = "rc_ch1", type = ComponentType.SCOPE_PROBE, label = "Vin",
            x = -4, y = -4, probeChannel = 1
        )
        val ch2 = CircuitComponent(
            id = "rc_ch2", type = ComponentType.SCOPE_PROBE, label = "Vout",
            x = 4, y = -4, probeChannel = 2
        )

        val wires = listOf(
            WireSegment("w1", listOf(GridPoint(-4, -2), GridPoint(-4, -4), GridPoint(-2, -4))),
            WireSegment("w2", listOf(GridPoint(2, -4), GridPoint(4, -4), GridPoint(4, -2))),
            WireSegment("w3", listOf(GridPoint(4, -4), GridPoint(8, -4), GridPoint(8, -2))),
            WireSegment("w4", listOf(GridPoint(-4, 2), GridPoint(-4, 4), GridPoint(0, 4))),
            WireSegment("w5", listOf(GridPoint(0, 4), GridPoint(4, 4), GridPoint(4, 2))),
            WireSegment("w6", listOf(GridPoint(4, 4), GridPoint(8, 4), GridPoint(8, 2)))
        )

        return CircuitSchematic(
            id = "preset_rc_lowpass",
            name = "RC Low-Pass Filter",
            description = "1kΩ + 1μF passive low-pass filter (fc ≈ 159 Hz) with 120Hz sine source and 2-ch scope probes.",
            components = listOf(vs, r1, c1, vm, gnd, ch1, ch2),
            wires = wires,
            timeStepMicros = 80.0,
            simSpeedMultiplier = 1.0
        )
    }

    fun fullWaveRectifier(): CircuitSchematic {
        // AC Source feeding 4-diode bridge + smoothing cap + 1k load
        val vs = CircuitComponent(
            id = "rec_v1", type = ComponentType.AC_VOLTAGE_SOURCE, label = "V1",
            x = -8, y = 0, rotation = 90,
            primaryValue = 10.0, secondaryValue = 60.0, waveform = WaveformType.SINE
        )
        val d1 = CircuitComponent(
            id = "rec_d1", type = ComponentType.DIODE, label = "D1",
            x = -2, y = -3, rotation = 0 // A(-4,-3) -> K(0,-3)
        )
        val d2 = CircuitComponent(
            id = "rec_d2", type = ComponentType.DIODE, label = "D2",
            x = -2, y = 3, rotation = 0 // A(-4,3) -> K(0,3)
        )
        val d3 = CircuitComponent(
            id = "rec_d3", type = ComponentType.DIODE, label = "D3",
            x = -2, y = -6, rotation = 180 // K(-4,-6) <- A(0,-6)
        )
        val d4 = CircuitComponent(
            id = "rec_d4", type = ComponentType.DIODE, label = "D4",
            x = -2, y = 6, rotation = 180 // K(-4,6) <- A(0,6)
        )
        val c1 = CircuitComponent(
            id = "rec_c1", type = ComponentType.CAPACITOR, label = "C1",
            x = 4, y = 0, rotation = 90, primaryValue = 47e-6
        )
        val rLoad = CircuitComponent(
            id = "rec_r1", type = ComponentType.RESISTOR, label = "Rload",
            x = 8, y = 0, rotation = 90, primaryValue = 1000.0
        )
        val gnd = CircuitComponent(
            id = "rec_gnd", type = ComponentType.GROUND, label = "GND",
            x = 4, y = 6
        )
        val ch1 = CircuitComponent(
            id = "rec_ch1", type = ComponentType.SCOPE_PROBE, label = "Vac",
            x = -8, y = -3, probeChannel = 1
        )
        val ch2 = CircuitComponent(
            id = "rec_ch2", type = ComponentType.SCOPE_PROBE, label = "Vdc",
            x = 4, y = -3, probeChannel = 2
        )

        // AC+ at (-8,-2) connects to D1 anode (-4,-3) and D3 cathode (-4,-6)
        // AC- at (-8,2) connects to D2 anode (-4,3) and D4 cathode (-4,6)
        // DC+ bus: D1 K(0,-3) & D2 K(0,3) -> C1+(4,-2) & Rload+(8,-2)
        // DC- bus (GND): D3 A(0,-6) & D4 A(0,6) -> C1-(4,2) & Rload-(8,2) & GND(4,6)
        val wires = listOf(
            WireSegment("rw1", listOf(GridPoint(-8, -2), GridPoint(-8, -3), GridPoint(-4, -3))),
            WireSegment("rw2", listOf(GridPoint(-4, -3), GridPoint(-4, -6))),
            WireSegment("rw3", listOf(GridPoint(-8, 2), GridPoint(-8, 3), GridPoint(-4, 3))),
            WireSegment("rw4", listOf(GridPoint(-4, 3), GridPoint(-4, 6))),
            WireSegment("rw5", listOf(GridPoint(0, -3), GridPoint(2, -3), GridPoint(2, 3), GridPoint(0, 3))),
            WireSegment("rw6", listOf(GridPoint(2, -3), GridPoint(4, -3), GridPoint(4, -2))),
            WireSegment("rw7", listOf(GridPoint(4, -3), GridPoint(8, -3), GridPoint(8, -2))),
            WireSegment("rw8", listOf(GridPoint(0, -6), GridPoint(10, -6), GridPoint(10, 6), GridPoint(4, 6))),
            WireSegment("rw9", listOf(GridPoint(0, 6), GridPoint(4, 6))),
            WireSegment("rw10", listOf(GridPoint(4, 2), GridPoint(4, 6))),
            WireSegment("rw11", listOf(GridPoint(8, 2), GridPoint(8, 6), GridPoint(4, 6)))
        )

        return CircuitSchematic(
            id = "preset_rectifier",
            name = "Full-Wave Bridge Rectifier",
            description = "4-diode bridge rectifier with 47μF filter capacitor and 1kΩ load.",
            components = listOf(vs, d1, d2, d3, d4, c1, rLoad, gnd, ch1, ch2),
            wires = wires,
            timeStepMicros = 120.0,
            simSpeedMultiplier = 1.0
        )
    }

    fun bjtCommonEmitterAmp(): CircuitSchematic {
        val vcc = CircuitComponent(
            id = "bjt_vcc", type = ComponentType.DC_VOLTAGE_SOURCE, label = "Vcc",
            x = -10, y = 0, rotation = 90, primaryValue = 12.0
        )
        val vin = CircuitComponent(
            id = "bjt_vin", type = ComponentType.AC_VOLTAGE_SOURCE, label = "Vin",
            x = -6, y = 2, rotation = 90, primaryValue = 0.05, secondaryValue = 200.0
        )
        val cin = CircuitComponent(
            id = "bjt_cin", type = ComponentType.CAPACITOR, label = "Cin",
            x = -3, y = 0, rotation = 0, primaryValue = 10e-6
        )
        val rb1 = CircuitComponent(
            id = "bjt_rb1", type = ComponentType.RESISTOR, label = "R1",
            x = 0, y = -4, rotation = 90, primaryValue = 47000.0
        )
        val rb2 = CircuitComponent(
            id = "bjt_rb2", type = ComponentType.RESISTOR, label = "R2",
            x = 0, y = 4, rotation = 90, primaryValue = 10000.0
        )
        val rc = CircuitComponent(
            id = "bjt_rc", type = ComponentType.RESISTOR, label = "Rc",
            x = 4, y = -4, rotation = 90, primaryValue = 2200.0
        )
        val re = CircuitComponent(
            id = "bjt_re", type = ComponentType.RESISTOR, label = "Re",
            x = 4, y = 4, rotation = 90, primaryValue = 470.0
        )
        val q1 = CircuitComponent(
            id = "bjt_q1", type = ComponentType.NPN_BJT, label = "Q1",
            x = 3, y = 0, rotation = 0, primaryValue = 150.0
        )
        val gnd = CircuitComponent(
            id = "bjt_gnd", type = ComponentType.GROUND, label = "GND",
            x = 0, y = 8
        )
        val ch1 = CircuitComponent(
            id = "bjt_ch1", type = ComponentType.SCOPE_PROBE, label = "Base",
            x = 1, y = 0, probeChannel = 1
        )
        val ch2 = CircuitComponent(
            id = "bjt_ch2", type = ComponentType.SCOPE_PROBE, label = "Vout",
            x = 4, y = -2, probeChannel = 2
        )

        val wires = listOf(
            // Vcc+ (-10,-2) -> rail y=-7 -> Rb1 top (0,-6) & Rc top (4,-6)
            WireSegment("bw1", listOf(GridPoint(-10, -2), GridPoint(-10, -7), GridPoint(4, -7), GridPoint(4, -6))),
            WireSegment("bw2", listOf(GridPoint(0, -7), GridPoint(0, -6))),
            // Rb1 bottom (0,-2) -> Base (1,0) -> Rb2 top (0,2)
            WireSegment("bw3", listOf(GridPoint(0, -2), GridPoint(0, 0), GridPoint(1, 0))),
            WireSegment("bw4", listOf(GridPoint(0, 0), GridPoint(0, 2))),
            // Vin+ (-6,0) -> Cin (-5,0); Cin+ (-1,0) -> Base (0,0)
            WireSegment("bw5", listOf(GridPoint(-6, 0), GridPoint(-5, 0))),
            WireSegment("bw6", listOf(GridPoint(-1, 0), GridPoint(0, 0))),
            // GND rail y=8
            WireSegment("bw7", listOf(GridPoint(-10, 2), GridPoint(-10, 8), GridPoint(4, 8), GridPoint(4, 6))),
            WireSegment("bw8", listOf(GridPoint(-6, 4), GridPoint(-6, 8))),
            WireSegment("bw9", listOf(GridPoint(0, 6), GridPoint(0, 8)))
        )

        return CircuitSchematic(
            id = "preset_bjt_amp",
            name = "BJT Common-Emitter Amp",
            description = "Voltage-divider biased NPN amplifier amplifying a 50mV 200Hz input signal.",
            components = listOf(vcc, vin, cin, rb1, rb2, rc, re, q1, gnd, ch1, ch2),
            wires = wires,
            timeStepMicros = 60.0,
            simSpeedMultiplier = 1.0
        )
    }

    fun opAmpInvertingAmp(): CircuitSchematic {
        // Inverting Op-Amp: Gain = -Rf/Rin = -10k / 2k = -5
        val vin = CircuitComponent(
            id = "oa_v1", type = ComponentType.AC_VOLTAGE_SOURCE, label = "Vin",
            x = -8, y = 2, rotation = 90, primaryValue = 1.0, secondaryValue = 100.0
        )
        val rin = CircuitComponent(
            id = "oa_rin", type = ComponentType.RESISTOR, label = "Rin",
            x = -4, y = 1, rotation = 0, primaryValue = 2000.0
        )
        val rf = CircuitComponent(
            id = "oa_rf", type = ComponentType.RESISTOR, label = "Rf",
            x = 1, y = 3, rotation = 0, primaryValue = 10000.0
        )
        val op = CircuitComponent(
            id = "oa_u1", type = ComponentType.OP_AMP, label = "U1",
            x = 1, y = 0, rotation = 0, primaryValue = 100000.0, secondaryValue = 15.0, tertiaryValue = -15.0
        )
        val rload = CircuitComponent(
            id = "oa_rl", type = ComponentType.RESISTOR, label = "Rload",
            x = 6, y = 2, rotation = 90, primaryValue = 5000.0
        )
        val gnd = CircuitComponent(
            id = "oa_gnd", type = ComponentType.GROUND, label = "GND",
            x = -2, y = 5
        )
        val ch1 = CircuitComponent(
            id = "oa_ch1", type = ComponentType.SCOPE_PROBE, label = "Vin",
            x = -8, y = 0, probeChannel = 1
        )
        val ch2 = CircuitComponent(
            id = "oa_ch2", type = ComponentType.SCOPE_PROBE, label = "Vout",
            x = 4, y = 0, probeChannel = 2
        )

        // OpAmp: + at (-1,-1), - at (-1,1), OUT at (3,0)
        // Rin: (-6,1) to (-2,1); Rf: (-1,3) to (3,3)
        val wires = listOf(
            WireSegment("ow1", listOf(GridPoint(-8, 0), GridPoint(-8, 1), GridPoint(-6, 1))),
            WireSegment("ow2", listOf(GridPoint(-2, 1), GridPoint(-1, 1))),
            WireSegment("ow3", listOf(GridPoint(-1, 1), GridPoint(-1, 3))),
            WireSegment("ow4", listOf(GridPoint(3, 3), GridPoint(3, 0))),
            WireSegment("ow5", listOf(GridPoint(3, 0), GridPoint(6, 0))),
            // + input (-1,-1) routed above Vin to GND without touching feedback or inverting node
            WireSegment("ow6", listOf(GridPoint(-1, -1), GridPoint(-1, -3), GridPoint(-10, -3), GridPoint(-10, 5), GridPoint(-2, 5))),
            WireSegment("ow7", listOf(GridPoint(-8, 4), GridPoint(-8, 5), GridPoint(-2, 5))),
            WireSegment("ow8", listOf(GridPoint(6, 4), GridPoint(6, 5), GridPoint(-2, 5)))
        )

        return CircuitSchematic(
            id = "preset_opamp_inv",
            name = "Op-Amp Inverting Amp (-5x)",
            description = "Inverting amplifier with Rf=10kΩ, Rin=2kΩ (Gain = -5). Shows 180° phase inversion.",
            components = listOf(vin, rin, rf, op, rload, gnd, ch1, ch2),
            wires = wires,
            timeStepMicros = 80.0
        )
    }

    fun rlcResonantTank(): CircuitSchematic {
        val vs = CircuitComponent(
            id = "rlc_v1", type = ComponentType.AC_VOLTAGE_SOURCE, label = "V1",
            x = -6, y = 0, rotation = 90, primaryValue = 5.0, secondaryValue = 160.0, waveform = WaveformType.SQUARE
        )
        val r1 = CircuitComponent(
            id = "rlc_r1", type = ComponentType.RESISTOR, label = "R1",
            x = -2, y = -3, rotation = 0, primaryValue = 15.0
        )
        val l1 = CircuitComponent(
            id = "rlc_l1", type = ComponentType.INDUCTOR, label = "L1",
            x = 2, y = -3, rotation = 0, primaryValue = 100e-3
        )
        val c1 = CircuitComponent(
            id = "rlc_c1", type = ComponentType.CAPACITOR, label = "C1",
            x = 6, y = 0, rotation = 90, primaryValue = 10e-6
        )
        val am = CircuitComponent(
            id = "rlc_am", type = ComponentType.AMMETER, label = "I_tank",
            x = 0, y = 3, rotation = 0
        )
        val gnd = CircuitComponent(
            id = "rlc_gnd", type = ComponentType.GROUND, label = "GND",
            x = -6, y = 4
        )
        val ch1 = CircuitComponent(
            id = "rlc_ch1", type = ComponentType.SCOPE_PROBE, label = "Vsq",
            x = -6, y = -3, probeChannel = 1
        )
        val ch2 = CircuitComponent(
            id = "rlc_ch2", type = ComponentType.SCOPE_PROBE, label = "Vcap",
            x = 6, y = -3, probeChannel = 2
        )

        val wires = listOf(
            WireSegment("lw1", listOf(GridPoint(-6, -2), GridPoint(-6, -3), GridPoint(-4, -3))),
            WireSegment("lw2", listOf(GridPoint(4, -3), GridPoint(6, -3), GridPoint(6, -2))),
            WireSegment("lw3", listOf(GridPoint(6, 2), GridPoint(6, 3), GridPoint(2, 3))),
            WireSegment("lw4", listOf(GridPoint(-2, 3), GridPoint(-6, 3), GridPoint(-6, 2))),
            WireSegment("lw5", listOf(GridPoint(-6, 3), GridPoint(-6, 4)))
        )

        return CircuitSchematic(
            id = "preset_rlc_tank",
            name = "RLC Underdamped Resonator",
            description = "Square-wave excited series RLC circuit showing damped sinusoidal ringing on C1.",
            components = listOf(vs, r1, l1, c1, am, gnd, ch1, ch2),
            wires = wires,
            timeStepMicros = 50.0
        )
    }

    fun nmosInverterSwitch(): CircuitSchematic {
        val vdd = CircuitComponent(
            id = "mos_vdd", type = ComponentType.DC_VOLTAGE_SOURCE, label = "Vdd",
            x = -8, y = 0, rotation = 90, primaryValue = 5.0
        )
        val vin = CircuitComponent(
            id = "mos_vin", type = ComponentType.AC_VOLTAGE_SOURCE, label = "Vgate",
            x = -3, y = 2, rotation = 90, primaryValue = 2.5, secondaryValue = 100.0, tertiaryValue = 2.5, waveform = WaveformType.SQUARE
        )
        val rd = CircuitComponent(
            id = "mos_rd", type = ComponentType.RESISTOR, label = "Rd",
            x = 3, y = -4, rotation = 90, primaryValue = 1000.0
        )
        val m1 = CircuitComponent(
            id = "mos_m1", type = ComponentType.NMOS_FET, label = "M1",
            x = 2, y = 0, rotation = 0, primaryValue = 10e-3, secondaryValue = 1.5
        )
        val gnd = CircuitComponent(
            id = "mos_gnd", type = ComponentType.GROUND, label = "GND",
            x = 0, y = 5
        )
        val ch1 = CircuitComponent(
            id = "mos_ch1", type = ComponentType.SCOPE_PROBE, label = "Vgate",
            x = 0, y = 0, probeChannel = 1
        )
        val ch2 = CircuitComponent(
            id = "mos_ch2", type = ComponentType.SCOPE_PROBE, label = "Vdrain",
            x = 3, y = -2, probeChannel = 2
        )

        // M1: G(0,0), D(3,-2), S(3,2)
        val wires = listOf(
            WireSegment("mw1", listOf(GridPoint(-8, -2), GridPoint(-8, -6), GridPoint(3, -6))),
            WireSegment("mw2", listOf(GridPoint(-3, 0), GridPoint(0, 0))),
            WireSegment("mw3", listOf(GridPoint(-8, 2), GridPoint(-8, 5), GridPoint(3, 5), GridPoint(3, 2))),
            WireSegment("mw4", listOf(GridPoint(-3, 4), GridPoint(-3, 5)))
        )

        return CircuitSchematic(
            id = "preset_nmos_inv",
            name = "NMOS Logic Inverter",
            description = "NMOSFET switching inverter driven by a 0–5V 100Hz square wave.",
            components = listOf(vdd, vin, rd, m1, gnd, ch1, ch2),
            wires = wires,
            timeStepMicros = 50.0
        )
    }

    fun dependentSourceAmp(): CircuitSchematic {
        val vin = CircuitComponent(
            id = "dep_v1", type = ComponentType.AC_VOLTAGE_SOURCE, label = "Vin",
            x = -7, y = 0, rotation = 90, primaryValue = 1.0, secondaryValue = 100.0
        )
        val rIn = CircuitComponent(
            id = "dep_r1", type = ComponentType.RESISTOR, label = "Rin",
            x = -4, y = -2, rotation = 90, primaryValue = 1000.0
        )
        val vcvs = CircuitComponent(
            id = "dep_e1", type = ComponentType.VCVS, label = "E1",
            x = 1, y = 0, rotation = 0, primaryValue = 4.0
        )
        val rLoad = CircuitComponent(
            id = "dep_rl", type = ComponentType.RESISTOR, label = "Rload",
            x = 6, y = 0, rotation = 90, primaryValue = 500.0
        )
        val wm = CircuitComponent(
            id = "dep_wm", type = ComponentType.VOLTMETER, label = "Vout",
            x = 10, y = 0, rotation = 90
        )
        val gnd = CircuitComponent(
            id = "dep_gnd", type = ComponentType.GROUND, label = "GND",
            x = 1, y = 4
        )
        val ch1 = CircuitComponent(
            id = "dep_ch1", type = ComponentType.SCOPE_PROBE, label = "Vin",
            x = -4, y = -4, probeChannel = 1
        )
        val ch2 = CircuitComponent(
            id = "dep_ch2", type = ComponentType.SCOPE_PROBE, label = "Vout",
            x = 6, y = -2, probeChannel = 2
        )

        // VCVS: S+(-1,-1), S-(-1,1), O+(3,-1), O-(3,1)
        val wires = listOf(
            WireSegment("dw1", listOf(GridPoint(-7, -2), GridPoint(-7, -4), GridPoint(-4, -4))),
            WireSegment("dw2", listOf(GridPoint(-4, -4), GridPoint(-1, -4), GridPoint(-1, -1))),
            WireSegment("dw3", listOf(GridPoint(-7, 2), GridPoint(-7, 4), GridPoint(1, 4))),
            WireSegment("dw4", listOf(GridPoint(-4, 0), GridPoint(-4, 4))),
            WireSegment("dw5", listOf(GridPoint(-1, 1), GridPoint(-1, 4))),
            WireSegment("dw6", listOf(GridPoint(3, -1), GridPoint(3, -2), GridPoint(10, -2))),
            WireSegment("dw7", listOf(GridPoint(3, 1), GridPoint(3, 4), GridPoint(10, 4), GridPoint(10, 2))),
            WireSegment("dw8", listOf(GridPoint(6, 2), GridPoint(6, 4))),
            WireSegment("dw9", listOf(GridPoint(1, 4), GridPoint(3, 4)))
        )

        return CircuitSchematic(
            id = "preset_dependent_vcvs",
            name = "VCVS Gain Stage (4x)",
            description = "Voltage-Controlled Voltage Source (E=4 V/V) driving a 500Ω load with live voltmeter.",
            components = listOf(vin, rIn, vcvs, rLoad, wm, gnd, ch1, ch2),
            wires = wires,
            timeStepMicros = 80.0
        )
    }

    fun wheatstoneBridge(): CircuitSchematic {
        val vdc = CircuitComponent(
            id = "wb_v1", type = ComponentType.DC_VOLTAGE_SOURCE, label = "Vs",
            x = -8, y = 0, rotation = 90, primaryValue = 10.0
        )
        val r1 = CircuitComponent(
            id = "wb_r1", type = ComponentType.RESISTOR, label = "R1",
            x = -2, y = -3, rotation = 90, primaryValue = 1000.0
        )
        val r2 = CircuitComponent(
            id = "wb_r2", type = ComponentType.RESISTOR, label = "R2",
            x = -2, y = 3, rotation = 90, primaryValue = 1000.0
        )
        val r3 = CircuitComponent(
            id = "wb_r3", type = ComponentType.RESISTOR, label = "R3",
            x = 4, y = -3, rotation = 90, primaryValue = 1000.0
        )
        val r4 = CircuitComponent(
            id = "wb_r4", type = ComponentType.RESISTOR, label = "Rx",
            x = 4, y = 3, rotation = 90, primaryValue = 2200.0
        )
        val vm = CircuitComponent(
            id = "wb_vm", type = ComponentType.VOLTMETER, label = "Vbridge",
            x = 1, y = 0, rotation = 0
        )
        val om = CircuitComponent(
            id = "wb_om", type = ComponentType.OHMMETER, label = "R_th",
            x = 8, y = 0, rotation = 90
        )
        val dmm = CircuitComponent(
            id = "wb_dmm", type = ComponentType.MULTIMETER, label = "DMM",
            x = -5, y = -5, rotation = 0, multimeterMode = MultimeterMode.AMPS_DC
        )
        val gnd = CircuitComponent(
            id = "wb_gnd", type = ComponentType.GROUND, label = "GND",
            x = 1, y = 6
        )

        val wires = listOf(
            WireSegment("ww1", listOf(GridPoint(-8, -2), GridPoint(-8, -5), GridPoint(-7, -5))),
            WireSegment("ww2", listOf(GridPoint(-3, -5), GridPoint(4, -5))),
            WireSegment("ww3", listOf(GridPoint(-2, -1), GridPoint(-2, 1))),
            WireSegment("ww4", listOf(GridPoint(-2, 0), GridPoint(-1, 0))),
            WireSegment("ww5", listOf(GridPoint(4, -1), GridPoint(4, 1))),
            WireSegment("ww6", listOf(GridPoint(3, 0), GridPoint(4, 0))),
            WireSegment("ww7", listOf(GridPoint(4, 0), GridPoint(6, 0), GridPoint(6, -2), GridPoint(8, -2))),
            WireSegment("ww8", listOf(GridPoint(4, 6), GridPoint(8, 6), GridPoint(8, 2))),
            WireSegment("ww9", listOf(GridPoint(-8, 2), GridPoint(-8, 6), GridPoint(4, 6), GridPoint(4, 5))),
            WireSegment("ww10", listOf(GridPoint(-2, 5), GridPoint(-2, 6)))
        )

        return CircuitSchematic(
            id = "preset_wheatstone",
            name = "Wheatstone Bridge & Meters",
            description = "Unbalanced bridge with Ammeter (DMM), Bridge Voltmeter, and Thévenin Ohmmeter.",
            components = listOf(vdc, r1, r2, r3, r4, vm, om, dmm, gnd),
            wires = wires,
            timeStepMicros = 200.0
        )
    }
}
