package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.UnitFormatter
import com.example.simulation.ScopeSample
import com.example.ui.theme.LabAmber
import com.example.ui.theme.LabBorder
import com.example.ui.theme.LabCyan
import com.example.ui.theme.LabSurface
import com.example.ui.theme.LabSurfaceElevated
import com.example.ui.theme.ScopeCh1Color
import com.example.ui.theme.ScopeCh2Color
import com.example.ui.theme.ScopeCh3Color
import com.example.ui.theme.ScopeCh4Color
import kotlin.math.abs
import kotlin.math.sqrt

private data class ChannelStats(
    val vpp: Double,
    val vrms: Double,
    val vavg: Double,
    val freqHz: Double?
)

@Composable
fun OscilloscopePanel(
    samples: List<ScopeSample>,
    config: ScopeConfig,
    onUpdateConfig: ((ScopeConfig) -> ScopeConfig) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var frozenSamples by remember { mutableStateOf<List<ScopeSample>>(emptyList()) }
    val activeSamples = if (config.frozen) frozenSamples else samples
    var cursorFraction by remember { mutableStateOf<Float?>(null) }

    val timeStepsMs = listOf(0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0)
    val voltSteps = listOf(0.2, 0.5, 1.0, 2.0, 5.0, 10.0)

    val ch1Stats = remember(activeSamples) { computeChannelStats(activeSamples) { it.ch1 } }
    val ch2Stats = remember(activeSamples) { computeChannelStats(activeSamples) { it.ch2 } }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("oscilloscope_panel"),
        color = LabSurface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, LabBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Top Scope Header + Channel Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "DSO",
                        style = MaterialTheme.typography.labelLarge,
                        color = LabCyan,
                        fontWeight = FontWeight.Bold
                    )
                    ScopeChannelChip("CH1", config.showCh1, ScopeCh1Color) {
                        onUpdateConfig { it.copy(showCh1 = !it.showCh1) }
                    }
                    ScopeChannelChip("CH2", config.showCh2, ScopeCh2Color) {
                        onUpdateConfig { it.copy(showCh2 = !it.showCh2) }
                    }
                    ScopeChannelChip("CH3", config.showCh3, ScopeCh3Color) {
                        onUpdateConfig { it.copy(showCh3 = !it.showCh3) }
                    }
                    ScopeChannelChip("CH4", config.showCh4, ScopeCh4Color) {
                        onUpdateConfig { it.copy(showCh4 = !it.showCh4) }
                    }
                    ScopeChannelChip("XY", config.xyMode, LabAmber) {
                        onUpdateConfig { it.copy(xyMode = !it.xyMode) }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (!config.frozen) frozenSamples = samples
                            onUpdateConfig { it.copy(frozen = !it.frozen) }
                        },
                        modifier = Modifier.size(36.dp).testTag("scope_freeze_button")
                    ) {
                        Icon(
                            imageVector = if (config.frozen) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (config.frozen) "Resume Scope" else "Freeze Scope",
                            tint = if (config.frozen) LabAmber else LabCyan
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(36.dp).testTag("scope_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Oscilloscope",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Waveform Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(156.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF070A0F))
                    .border(1.dp, Color(0xFF1F2A3A), RoundedCornerShape(10.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                cursorFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                cursorFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            },
                            onDragEnd = { },
                            onDragCancel = { cursorFraction = null }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawScopeGraticule()
                    if (activeSamples.isEmpty()) return@Canvas

                    val totalWindowSec = (config.timePerDivMs * 10.0) * 1e-3
                    val latestTime = activeSamples.last().timeSeconds
                    val triggerStart = findTriggerWindowStart(
                        samples = activeSamples,
                        windowSec = totalWindowSec,
                        latestTime = latestTime,
                        mode = config.triggerMode,
                        level = config.triggerLevel
                    )

                    val windowSamples = activeSamples.filter {
                        it.timeSeconds >= triggerStart && it.timeSeconds <= triggerStart + totalWindowSec
                    }

                    if (config.xyMode) {
                        drawXyLissajous(windowSamples, config.voltsPerDiv)
                    } else {
                        if (config.showCh1) {
                            drawTrace(windowSamples, triggerStart, totalWindowSec, config.voltsPerDiv, ScopeCh1Color) { it.ch1 }
                        }
                        if (config.showCh2) {
                            drawTrace(windowSamples, triggerStart, totalWindowSec, config.voltsPerDiv, ScopeCh2Color) { it.ch2 }
                        }
                        if (config.showCh3) {
                            drawTrace(windowSamples, triggerStart, totalWindowSec, config.voltsPerDiv, ScopeCh3Color) { it.ch3 }
                        }
                        if (config.showCh4) {
                            drawTrace(windowSamples, triggerStart, totalWindowSec, config.voltsPerDiv, ScopeCh4Color) { it.ch4 }
                        }
                    }

                    // Cursor line
                    cursorFraction?.let { frac ->
                        val cx = frac * size.width
                        drawLine(
                            color = Color.White.copy(alpha = 0.65f),
                            start = Offset(cx, 0f),
                            end = Offset(cx, size.height),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                    }
                }

                if (activeSamples.isEmpty()) {
                    Text(
                        text = "Place a Scope Probe (CH1–CH4) on any node to capture live waveforms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp)
                    )
                } else if (cursorFraction != null) {
                    val idx = ((cursorFraction ?: 0f) * (activeSamples.size - 1)).toInt().coerceIn(0, activeSamples.lastIndex)
                    val s = activeSamples[idx]
                    Surface(
                        color = Color(0xCC0D1420),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                    ) {
                        val txt = buildString {
                            append("t=${UnitFormatter.formatCompact(s.timeSeconds, "s")}")
                            s.ch1?.let { append("  1:${UnitFormatter.formatCompact(it, "V")}") }
                            s.ch2?.let { append("  2:${UnitFormatter.formatCompact(it, "V")}") }
                        }
                        Text(
                            text = txt,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Controls + Measurements Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CycleChip(
                    label = "Time/div: ${config.timePerDivMs} ms",
                    onClick = {
                        val nextIdx = (timeStepsMs.indexOf(config.timePerDivMs) + 1) % timeStepsMs.size
                        onUpdateConfig { it.copy(timePerDivMs = timeStepsMs[nextIdx]) }
                    }
                )
                CycleChip(
                    label = "V/div: ${config.voltsPerDiv} V",
                    onClick = {
                        val nextIdx = (voltSteps.indexOf(config.voltsPerDiv) + 1) % voltSteps.size
                        onUpdateConfig { it.copy(voltsPerDiv = voltSteps[nextIdx]) }
                    }
                )
                CycleChip(
                    label = "Trig: ${config.triggerMode.label}",
                    onClick = {
                        val modes = ScopeTriggerMode.entries
                        val next = modes[(modes.indexOf(config.triggerMode) + 1) % modes.size]
                        onUpdateConfig { it.copy(triggerMode = next) }
                    }
                )

                ch1Stats?.let { st ->
                    val fTxt = st.freqHz?.let { " ${UnitFormatter.formatCompact(it, "Hz")}" } ?: ""
                    Text(
                        text = "CH1 Vpp:${UnitFormatter.formatCompact(st.vpp, "V")} Vrms:${UnitFormatter.formatCompact(st.vrms, "V")}$fTxt",
                        style = MaterialTheme.typography.labelSmall,
                        color = ScopeCh1Color
                    )
                }
                ch2Stats?.let { st ->
                    Text(
                        text = "CH2 Vpp:${UnitFormatter.formatCompact(st.vpp, "V")} Vrms:${UnitFormatter.formatCompact(st.vrms, "V")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ScopeCh2Color
                    )
                }
            }
        }
    }
}

@Composable
private fun ScopeChannelChip(
    text: String,
    active: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        color = if (active) color.copy(alpha = 0.2f) else LabSurfaceElevated,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .border(1.dp, if (active) color else LabBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) color else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CycleChip(label: String, onClick: () -> Unit) {
    Surface(
        color = LabSurfaceElevated,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .border(1.dp, LabBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

private fun DrawScope.drawScopeGraticule() {
    val cols = 10
    val rows = 8
    val dx = size.width / cols
    val dy = size.height / rows
    val gridColor = Color(0xFF172230)
    val axisColor = Color(0xFF283A52)

    for (c in 1 until cols) {
        val x = c * dx
        drawLine(
            color = if (c == cols / 2) axisColor else gridColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = if (c == cols / 2) 1.4f else 1f
        )
    }
    for (r in 1 until rows) {
        val y = r * dy
        drawLine(
            color = if (r == rows / 2) axisColor else gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = if (r == rows / 2) 1.4f else 1f
        )
    }
}

private fun findTriggerWindowStart(
    samples: List<ScopeSample>,
    windowSec: Double,
    latestTime: Double,
    mode: ScopeTriggerMode,
    level: Double
): Double {
    val fallbackStart = (latestTime - windowSec).coerceAtLeast(samples.first().timeSeconds)
    if (mode == ScopeTriggerMode.AUTO || samples.size < 4) return fallbackStart

    // Search backwards for a rising/falling edge crossing that leaves enough data to fill windowSec
    val minEndSearchTime = latestTime - windowSec
    for (i in samples.size - 2 downTo 1) {
        val sCurr = samples[i]
        if (sCurr.timeSeconds > minEndSearchTime) continue
        val vPrev = samples[i - 1].ch1 ?: samples[i - 1].ch2 ?: continue
        val vCurr = sCurr.ch1 ?: sCurr.ch2 ?: continue
        val triggered = when (mode) {
            ScopeTriggerMode.RISING -> vPrev < level && vCurr >= level
            ScopeTriggerMode.FALLING -> vPrev > level && vCurr <= level
            ScopeTriggerMode.AUTO -> false
        }
        if (triggered) return sCurr.timeSeconds
    }
    return fallbackStart
}

private fun DrawScope.drawTrace(
    samples: List<ScopeSample>,
    startTime: Double,
    windowSec: Double,
    voltsPerDiv: Double,
    color: Color,
    selector: (ScopeSample) -> Double?
) {
    if (samples.size < 2 || windowSec <= 0.0) return
    val halfHeight = size.height / 2f
    val pxPerVolt = (size.height / 8.0 / voltsPerDiv).toFloat()

    val path = Path()
    var started = false
    for (s in samples) {
        val v = selector(s) ?: continue
        val x = (((s.timeSeconds - startTime) / windowSec) * size.width).toFloat().coerceIn(0f, size.width)
        val y = (halfHeight - v.toFloat() * pxPerVolt).coerceIn(2f, size.height - 2f)
        if (!started) {
            path.moveTo(x, y)
            started = true
        } else {
            path.lineTo(x, y)
        }
    }
    if (started) {
        drawPath(
            path = path,
            color = color.copy(alpha = 0.25f),
            style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

private fun DrawScope.drawXyLissajous(samples: List<ScopeSample>, voltsPerDiv: Double) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val pxPerVoltX = (size.width / 10.0 / voltsPerDiv).toFloat()
    val pxPerVoltY = (size.height / 8.0 / voltsPerDiv).toFloat()
    val path = Path()
    var started = false
    for (s in samples) {
        val vx = s.ch1 ?: continue
        val vy = s.ch2 ?: continue
        val px = (cx + vx.toFloat() * pxPerVoltX).coerceIn(2f, size.width - 2f)
        val py = (cy - vy.toFloat() * pxPerVoltY).coerceIn(2f, size.height - 2f)
        if (!started) {
            path.moveTo(px, py)
            started = true
        } else {
            path.lineTo(px, py)
        }
    }
    if (started) {
        drawPath(path, color = ScopeCh1Color, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun computeChannelStats(
    samples: List<ScopeSample>,
    selector: (ScopeSample) -> Double?
): ChannelStats? {
    val vals = samples.mapNotNull { s -> selector(s)?.let { s.timeSeconds to it } }
    if (vals.size < 2) return null

    var minV = Double.POSITIVE_INFINITY
    var maxV = Double.NEGATIVE_INFINITY
    var sum = 0.0
    var sumSq = 0.0
    for ((_, v) in vals) {
        if (v < minV) minV = v
        if (v > maxV) maxV = v
        sum += v
        sumSq += v * v
    }
    val avg = sum / vals.size
    val rms = sqrt(sumSq / vals.size)
    val vpp = maxV - minV

    // Frequency estimate from rising midpoint crossings
    var freqHz: Double? = null
    if (vpp > 1e-3) {
        val mid = 0.5 * (minV + maxV)
        val crossings = ArrayList<Double>()
        for (i in 1 until vals.size) {
            val (t0, v0) = vals[i - 1]
            val (t1, v1) = vals[i]
            if (v0 < mid && v1 >= mid) {
                val frac = (mid - v0) / (v1 - v0)
                crossings.add(t0 + frac * (t1 - t0))
            }
        }
        if (crossings.size >= 2) {
            val avgPeriod = (crossings.last() - crossings.first()) / (crossings.size - 1)
            if (avgPeriod > 1e-8) freqHz = 1.0 / avgPeriod
        }
    }
    return ChannelStats(vpp = vpp, vrms = rms, vavg = avg, freqHz = freqHz)
}
