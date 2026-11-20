package com.example.model

import com.squareup.moshi.JsonClass
import java.util.UUID

@JsonClass(generateAdapter = true)
data class GridPoint(val x: Int, val y: Int) {
    operator fun plus(other: GridPoint) = GridPoint(x + other.x, y + other.y)
    operator fun minus(other: GridPoint) = GridPoint(x - other.x, y - other.y)
}

enum class ComponentCategory(val title: String) {
    SOURCES("Sources"),
    PASSIVES("Passives"),
    ACTIVE("Semiconductors"),
    DEPENDENT("Dependent"),
    METERS("Meters & Probes")
}

enum class ComponentType(
    val displayName: String,
    val shortCode: String,
    val prefix: String,
    val category: ComponentCategory,
    val primaryLabel: String,
    val primaryUnit: String,
    val defaultPrimary: Double,
    val defaultSecondary: Double = 0.0,
    val defaultTertiary: Double = 0.0,
    val defaultQuaternary: Double = 0.0
) {
    // Sources & Reference
    GROUND("Ground", "GND", "GND", ComponentCategory.SOURCES, "Reference", "V", 0.0),
    DC_VOLTAGE_SOURCE("DC Voltage", "VDC", "V", ComponentCategory.SOURCES, "Voltage", "V", 5.0),
    AC_VOLTAGE_SOURCE("AC Voltage", "VAC", "V", ComponentCategory.SOURCES, "Amplitude", "V", 5.0, 60.0, 0.0, 0.0),
    DC_CURRENT_SOURCE("DC Current", "IDC", "I", ComponentCategory.SOURCES, "Current", "A", 0.01),
    AC_CURRENT_SOURCE("AC Current", "IAC", "I", ComponentCategory.SOURCES, "Amplitude", "A", 0.01, 60.0, 0.0, 0.0),

    // Passives
    RESISTOR("Resistor", "RES", "R", ComponentCategory.PASSIVES, "Resistance", "Ω", 1000.0),
    CAPACITOR("Capacitor", "CAP", "C", ComponentCategory.PASSIVES, "Capacitance", "F", 10e-6, 0.0),
    INDUCTOR("Inductor", "IND", "L", ComponentCategory.PASSIVES, "Inductance", "H", 10e-3, 0.0),

    // Semiconductors & Active
    DIODE("Diode", "D", "D", ComponentCategory.ACTIVE, "Sat. Current Is", "A", 1e-12, 1.5, 0.0),
    NPN_BJT("NPN BJT", "NPN", "Q", ComponentCategory.ACTIVE, "Gain β", "", 150.0, 1e-13, 100.0),
    PNP_BJT("PNP BJT", "PNP", "Q", ComponentCategory.ACTIVE, "Gain β", "", 150.0, 1e-13, 100.0),
    NMOS_FET("NMOS FET", "NMOS", "M", ComponentCategory.ACTIVE, "Kp (μCoxW/L)", "A/V²", 2e-3, 1.5, 0.02),
    PMOS_FET("PMOS FET", "PMOS", "M", ComponentCategory.ACTIVE, "Kp (μCoxW/L)", "A/V²", 2e-3, -1.5, 0.02),
    OP_AMP("Op-Amp", "OPAMP", "U", ComponentCategory.ACTIVE, "Open-Loop Gain", "", 100000.0, 15.0, -15.0),

    // Dependent Sources (Controlled)
    VCVS("VCVS", "VCVS", "E", ComponentCategory.DEPENDENT, "Voltage Gain E", "V/V", 2.0),
    VCCS("VCCS", "VCCS", "G", ComponentCategory.DEPENDENT, "Transcond. G", "A/V", 0.01),
    CCVS("CCVS", "CCVS", "H", ComponentCategory.DEPENDENT, "Transres. H", "V/A", 1000.0),
    CCCS("CCCS", "CCCS", "F", ComponentCategory.DEPENDENT, "Current Gain F", "A/A", 5.0),

    // Interactive Meters & Probes
    VOLTMETER("Voltmeter", "VM", "VM", ComponentCategory.METERS, "Impedance", "Ω", 1e7),
    AMMETER("Ammeter", "AM", "AM", ComponentCategory.METERS, "Shunt R", "Ω", 1e-3),
    OHMMETER("Ohmmeter", "ΩM", "OM", ComponentCategory.METERS, "Test Current", "A", 1e-3),
    WATTMETER("Wattmeter", "WM", "WM", ComponentCategory.METERS, "Sense R", "Ω", 1e7),
    MULTIMETER("Multimeter", "DMM", "DMM", ComponentCategory.METERS, "Impedance", "Ω", 1e7),
    SCOPE_PROBE("Scope Probe", "CH", "CH", ComponentCategory.METERS, "Channel", "", 1.0)
}

enum class WaveformType(val displayName: String) {
    DC("DC"),
    SINE("Sine"),
    SQUARE("Square"),
    TRIANGLE("Triangle"),
    PULSE("Pulse")
}

enum class MultimeterMode(val shortLabel: String, val fullLabel: String, val unit: String) {
    VOLTS_DC("V DC", "DC Voltage", "V"),
    VOLTS_AC_RMS("V RMS", "AC RMS Voltage", "V"),
    AMPS_DC("A DC", "DC Current", "A"),
    AMPS_AC_RMS("A RMS", "AC RMS Current", "A"),
    OHMS("Ω", "Resistance", "Ω"),
    WATTS("W", "Power", "W"),
    DIODE_VF("Diode", "Diode Drop", "V")
}

data class TerminalDef(
    val name: String,
    val localOffset: GridPoint
)

@JsonClass(generateAdapter = true)
data class CircuitComponent(
    val id: String = UUID.randomUUID().toString(),
    val type: ComponentType,
    val label: String,
    val x: Int,
    val y: Int,
    val rotation: Int = 0, // 0, 90, 180, 270
    val flipped: Boolean = false,
    val primaryValue: Double = type.defaultPrimary,
    val secondaryValue: Double = type.defaultSecondary,
    val tertiaryValue: Double = type.defaultTertiary,
    val quaternaryValue: Double = type.defaultQuaternary,
    val waveform: WaveformType = when (type) {
        ComponentType.AC_VOLTAGE_SOURCE, ComponentType.AC_CURRENT_SOURCE -> WaveformType.SINE
        else -> WaveformType.DC
    },
    val controlComponentId: String? = null,
    val multimeterMode: MultimeterMode = MultimeterMode.VOLTS_DC,
    val probeChannel: Int = 1 // 1..4
) {
    fun getLocalTerminals(): List<TerminalDef> = when (type) {
        ComponentType.GROUND,
        ComponentType.SCOPE_PROBE -> listOf(
            TerminalDef("P", GridPoint(0, 0))
        )

        ComponentType.RESISTOR,
        ComponentType.CAPACITOR,
        ComponentType.INDUCTOR,
        ComponentType.VOLTMETER,
        ComponentType.AMMETER,
        ComponentType.OHMMETER,
        ComponentType.MULTIMETER -> listOf(
            TerminalDef("+", GridPoint(-2, 0)),
            TerminalDef("-", GridPoint(2, 0))
        )

        ComponentType.DIODE -> listOf(
            TerminalDef("A", GridPoint(-2, 0)),
            TerminalDef("K", GridPoint(2, 0))
        )

        ComponentType.DC_VOLTAGE_SOURCE,
        ComponentType.AC_VOLTAGE_SOURCE,
        ComponentType.DC_CURRENT_SOURCE,
        ComponentType.AC_CURRENT_SOURCE -> listOf(
            TerminalDef("+", GridPoint(-2, 0)),
            TerminalDef("-", GridPoint(2, 0))
        )

        ComponentType.NPN_BJT,
        ComponentType.PNP_BJT -> listOf(
            TerminalDef("B", GridPoint(-2, 0)),
            TerminalDef("C", GridPoint(1, -2)),
            TerminalDef("E", GridPoint(1, 2))
        )

        ComponentType.NMOS_FET,
        ComponentType.PMOS_FET -> listOf(
            TerminalDef("G", GridPoint(-2, 0)),
            TerminalDef("D", GridPoint(1, -2)),
            TerminalDef("S", GridPoint(1, 2))
        )

        ComponentType.OP_AMP -> listOf(
            TerminalDef("+", GridPoint(-2, -1)),
            TerminalDef("-", GridPoint(-2, 1)),
            TerminalDef("OUT", GridPoint(2, 0))
        )

        ComponentType.VCVS,
        ComponentType.VCCS,
        ComponentType.CCVS,
        ComponentType.CCCS -> listOf(
            TerminalDef("S+", GridPoint(-2, -1)),
            TerminalDef("S-", GridPoint(-2, 1)),
            TerminalDef("O+", GridPoint(2, -1)),
            TerminalDef("O-", GridPoint(2, 1))
        )

        ComponentType.WATTMETER -> listOf(
            TerminalDef("I+", GridPoint(-2, -1)),
            TerminalDef("I-", GridPoint(2, -1)),
            TerminalDef("V+", GridPoint(-2, 1)),
            TerminalDef("V-", GridPoint(2, 1))
        )
    }

    fun transformLocalPoint(local: GridPoint): GridPoint {
        val fx = if (flipped) -local.x else local.x
        val fy = local.y
        val rot = ((rotation % 360) + 360) % 360
        val (rx, ry) = when (rot) {
            90 -> -fy to fx
            180 -> -fx to -fy
            270 -> fy to -fx
            else -> fx to fy
        }
        return GridPoint(x + rx, y + ry)
    }

    fun getWorldTerminals(): List<Pair<String, GridPoint>> =
        getLocalTerminals().map { it.name to transformLocalPoint(it.localOffset) }

    fun summaryValueText(): String = when (type) {
        ComponentType.GROUND -> "0 V"
        ComponentType.SCOPE_PROBE -> "CH$probeChannel"
        ComponentType.RESISTOR -> UnitFormatter.formatCompact(primaryValue, "Ω")
        ComponentType.CAPACITOR -> UnitFormatter.formatCompact(primaryValue, "F")
        ComponentType.INDUCTOR -> UnitFormatter.formatCompact(primaryValue, "H")
        ComponentType.DC_VOLTAGE_SOURCE -> UnitFormatter.formatCompact(primaryValue, "V")
        ComponentType.AC_VOLTAGE_SOURCE -> "${UnitFormatter.formatCompact(primaryValue, "V")} ${UnitFormatter.formatCompact(secondaryValue, "Hz")}"
        ComponentType.DC_CURRENT_SOURCE -> UnitFormatter.formatCompact(primaryValue, "A")
        ComponentType.AC_CURRENT_SOURCE -> "${UnitFormatter.formatCompact(primaryValue, "A")} ${UnitFormatter.formatCompact(secondaryValue, "Hz")}"
        ComponentType.DIODE -> if (tertiaryValue > 0.1) "Vz=${UnitFormatter.formatCompact(tertiaryValue, "V")}" else "Si 0.7V"
        ComponentType.NPN_BJT -> "NPN β=${primaryValue.toInt()}"
        ComponentType.PNP_BJT -> "PNP β=${primaryValue.toInt()}"
        ComponentType.NMOS_FET -> "NMOS Vt=${UnitFormatter.formatCompact(secondaryValue, "V")}"
        ComponentType.PMOS_FET -> "PMOS Vt=${UnitFormatter.formatCompact(secondaryValue, "V")}"
        ComponentType.OP_AMP -> "±${secondaryValue.toInt()}V"
        ComponentType.VCVS -> "E=${UnitFormatter.formatCompact(primaryValue, "")}"
        ComponentType.VCCS -> "G=${UnitFormatter.formatCompact(primaryValue, "S")}"
        ComponentType.CCVS -> "H=${UnitFormatter.formatCompact(primaryValue, "Ω")}"
        ComponentType.CCCS -> "F=${UnitFormatter.formatCompact(primaryValue, "")}"
        ComponentType.VOLTMETER -> "V-Meter"
        ComponentType.AMMETER -> "A-Meter"
        ComponentType.OHMMETER -> "Ω-Meter"
        ComponentType.WATTMETER -> "W-Meter"
        ComponentType.MULTIMETER -> multimeterMode.shortLabel
    }
}

@JsonClass(generateAdapter = true)
data class WireSegment(
    val id: String = UUID.randomUUID().toString(),
    val points: List<GridPoint>
)

@JsonClass(generateAdapter = true)
data class CircuitSchematic(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Untitled Circuit",
    val description: String = "",
    val components: List<CircuitComponent> = emptyList(),
    val wires: List<WireSegment> = emptyList(),
    val timeStepMicros: Double = 100.0,
    val simSpeedMultiplier: Double = 1.0
)
