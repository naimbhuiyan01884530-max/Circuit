package com.example.model

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

object UnitFormatter {

    data class SiPrefix(val symbol: String, val multiplier: Double)

    val prefixes = listOf(
        SiPrefix("p", 1e-12),
        SiPrefix("n", 1e-9),
        SiPrefix("μ", 1e-6),
        SiPrefix("m", 1e-3),
        SiPrefix("", 1.0),
        SiPrefix("k", 1e3),
        SiPrefix("M", 1e6),
        SiPrefix("G", 1e9)
    )

    fun format(value: Double, unit: String, decimals: Int = 2): String {
        if (value.isNaN()) return "NaN"
        if (value.isInfinite()) return "∞ $unit".trim()
        val absVal = abs(value)
        if (absVal < 1e-15) return "0.${"0".repeat(decimals)} $unit".trim()
        if (absVal >= 1e12) return "∞ $unit".trim()

        val exp3 = kotlin.math.floor(log10(absVal) / 3.0).toInt().coerceIn(-4, 3)
        val scale = 10.0.pow(exp3 * 3)
        val scaled = value / scale
        val prefix = when (exp3) {
            -4 -> "p"
            -3 -> "n"
            -2 -> "μ"
            -1 -> "m"
            0 -> ""
            1 -> "k"
            2 -> "M"
            3 -> "G"
            else -> ""
        }
        val fmt = "%.${decimals}f"
        val numberStr = String.format(java.util.Locale.US, fmt, scaled)
            .let { str ->
                if (str.contains('.')) str.trimEnd('0').trimEnd('.') else str
            }
        return if (unit.isEmpty()) "$numberStr$prefix" else "$numberStr $prefix$unit"
    }

    fun formatCompact(value: Double, unit: String): String {
        if (value.isNaN()) return "NaN"
        if (value.isInfinite() || abs(value) >= 1e9) return "OL"
        val absVal = abs(value)
        if (absVal < 1e-12) return "0$unit"
        val exp3 = kotlin.math.floor(log10(absVal) / 3.0).toInt().coerceIn(-4, 3)
        val scaled = value / 10.0.pow(exp3 * 3)
        val prefix = when (exp3) {
            -4 -> "p"
            -3 -> "n"
            -2 -> "μ"
            -1 -> "m"
            0 -> ""
            1 -> "k"
            2 -> "M"
            3 -> "G"
            else -> ""
        }
        val rounded = when {
            abs(scaled) >= 100 -> String.format(java.util.Locale.US, "%.0f", scaled)
            abs(scaled) >= 10 -> String.format(java.util.Locale.US, "%.1f", scaled)
            else -> String.format(java.util.Locale.US, "%.2f", scaled)
        }.let { if (it.contains('.')) it.trimEnd('0').trimEnd('.') else it }
        return "$rounded$prefix$unit"
    }

    fun parse(input: String): Double? {
        val trimmed = input.trim()
            .replace("Ω", "")
            .replace("Hz", "")
            .replace("V", "")
            .replace("A", "")
            .replace("F", "")
            .replace("H", "")
            .replace("W", "")
            .trim()
        if (trimmed.isEmpty()) return null

        val last = trimmed.last()
        val mult = when (last) {
            'p', 'P' -> 1e-12
            'n', 'N' -> 1e-9
            'u', 'U', 'μ' -> 1e-6
            'm' -> 1e-3
            'k', 'K' -> 1e3
            'M' -> 1e6
            'g', 'G' -> 1e9
            else -> null
        }
        return if (mult != null) {
            trimmed.dropLast(1).trim().toDoubleOrNull()?.times(mult)
        } else {
            trimmed.toDoubleOrNull()
        }
    }
}
