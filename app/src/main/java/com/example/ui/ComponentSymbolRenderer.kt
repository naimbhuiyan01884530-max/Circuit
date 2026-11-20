package com.example.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.model.CircuitComponent
import com.example.model.ComponentType
import com.example.model.UnitFormatter
import com.example.simulation.MeterReading
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LabAmber
import com.example.ui.theme.LabCyan
import com.example.ui.theme.LabEmerald
import com.example.ui.theme.LabRose
import com.example.ui.theme.ScopeCh1Color
import com.example.ui.theme.ScopeCh2Color
import com.example.ui.theme.ScopeCh3Color
import com.example.ui.theme.ScopeCh4Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object ComponentSymbolRenderer {

    fun scopeChannelColor(channel: Int): Color = when (channel) {
        1 -> ScopeCh1Color
        2 -> ScopeCh2Color
        3 -> ScopeCh3Color
        4 -> ScopeCh4Color
        else -> ScopeCh1Color
    }

    fun drawComponent(
        scope: DrawScope,
        comp: CircuitComponent,
        gridSizePx: Float,
        strokeColor: Color,
        accentColor: Color,
        terminalColor: Color,
        isSelected: Boolean,
        isDark: Boolean,
        textMeasurer: TextMeasurer?,
        meterReading: MeterReading? = null
    ) {
        val cx = comp.x * gridSizePx
        val cy = comp.y * gridSizePx
        val baseStroke = (gridSizePx * 0.085f).coerceIn(2f, 5f)
        val stroke = Stroke(width = baseStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)

        with(scope) {
            if (isSelected) {
                val r = if (comp.type == ComponentType.GROUND || comp.type == ComponentType.SCOPE_PROBE) {
                    gridSizePx * 1.1f
                } else {
                    gridSizePx * 2.25f
                }
                drawCircle(
                    color = accentColor.copy(alpha = 0.14f),
                    radius = r,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = accentColor.copy(alpha = 0.65f),
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = baseStroke * 0.7f)
                )
            }

            withTransform({
                translate(cx, cy)
                rotate(comp.rotation.toFloat(), pivot = Offset.Zero)
                if (comp.flipped) scale(-1f, 1f, pivot = Offset.Zero)
            }) {
                drawSymbolGeometry(
                    comp = comp,
                    g = gridSizePx,
                    color = if (comp.type == ComponentType.SCOPE_PROBE) scopeChannelColor(comp.probeChannel) else strokeColor,
                    accent = accentColor,
                    stroke = stroke,
                    baseStroke = baseStroke,
                    isDark = isDark
                )
            }

            // Draw terminal pins in world space
            for ((_, gp) in comp.getWorldTerminals()) {
                val tx = gp.x * gridSizePx
                val ty = gp.y * gridSizePx
                drawCircle(
                    color = if (comp.type == ComponentType.SCOPE_PROBE) scopeChannelColor(comp.probeChannel) else terminalColor,
                    radius = (gridSizePx * 0.14f).coerceIn(3.5f, 7f),
                    center = Offset(tx, ty)
                )
            }

            // Draw labels and live meter readouts
            if (textMeasurer != null) {
                drawComponentLabels(
                    comp = comp,
                    cx = cx,
                    cy = cy,
                    g = gridSizePx,
                    strokeColor = strokeColor,
                    isDark = isDark,
                    textMeasurer = textMeasurer,
                    meterReading = meterReading
                )
            }
        }
    }

    fun drawPaletteIcon(
        scope: DrawScope,
        type: ComponentType,
        color: Color,
        accent: Color
    ) {
        val g = scope.size.minDimension / 5.2f
        val stroke = Stroke(width = (g * 0.16f).coerceIn(2f, 4f), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val dummy = CircuitComponent(id = "preview", type = type, label = type.shortCode, x = 0, y = 0)
        scope.withTransform({
            translate(scope.size.width / 2f, scope.size.height / 2f)
        }) {
            drawSymbolGeometry(dummy, g, color, accent, stroke, stroke.width, isDark = true)
        }
    }

    private fun DrawScope.drawSymbolGeometry(
        comp: CircuitComponent,
        g: Float,
        color: Color,
        accent: Color,
        stroke: Stroke,
        baseStroke: Float,
        isDark: Boolean
    ) {
        when (comp.type) {
            ComponentType.GROUND -> {
                drawLine(color, Offset(0f, 0f), Offset(0f, g * 0.7f), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(-g * 0.75f, g * 0.7f), Offset(g * 0.75f, g * 0.7f), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(-g * 0.48f, g * 1.05f), Offset(g * 0.48f, g * 1.05f), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(-g * 0.22f, g * 1.4f), Offset(g * 0.22f, g * 1.4f), strokeWidth = baseStroke, cap = StrokeCap.Round)
            }

            ComponentType.SCOPE_PROBE -> {
                val pColor = scopeChannelColor(comp.probeChannel)
                val flag = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(g * 0.55f, -g * 0.9f)
                    lineTo(g * 1.35f, -g * 0.9f)
                    lineTo(g * 1.35f, -g * 0.25f)
                    lineTo(g * 0.65f, -g * 0.25f)
                    close()
                }
                drawPath(flag, color = pColor.copy(alpha = 0.28f))
                drawPath(flag, color = pColor, style = stroke)
            }

            ComponentType.RESISTOR -> {
                val path = Path().apply {
                    moveTo(-2f * g, 0f)
                    lineTo(-1.1f * g, 0f)
                    lineTo(-0.9f * g, -0.45f * g)
                    lineTo(-0.55f * g, 0.45f * g)
                    lineTo(-0.18f * g, -0.45f * g)
                    lineTo(0.18f * g, 0.45f * g)
                    lineTo(0.55f * g, -0.45f * g)
                    lineTo(0.9f * g, 0.45f * g)
                    lineTo(1.1f * g, 0f)
                    lineTo(2f * g, 0f)
                }
                drawPath(path, color = color, style = stroke)
            }

            ComponentType.CAPACITOR -> {
                drawLine(color, Offset(-2f * g, 0f), Offset(-0.22f * g, 0f), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(0.22f * g, 0f), Offset(2f * g, 0f), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(-0.22f * g, -0.78f * g), Offset(-0.22f * g, 0.78f * g), strokeWidth = baseStroke * 1.25f, cap = StrokeCap.Round)
                drawLine(color, Offset(0.22f * g, -0.78f * g), Offset(0.22f * g, 0.78f * g), strokeWidth = baseStroke * 1.25f, cap = StrokeCap.Round)
            }

            ComponentType.INDUCTOR -> {
                drawLine(color, Offset(-2f * g, 0f), Offset(-1.2f * g, 0f), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(1.2f * g, 0f), Offset(2f * g, 0f), strokeWidth = baseStroke, cap = StrokeCap.Round)
                val coilRadius = 0.3f * g
                for (i in 0 until 4) {
                    val left = -1.2f * g + i * (2f * coilRadius)
                    drawArc(
                        color = color,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(left, -coilRadius),
                        size = Size(2f * coilRadius, 2f * coilRadius),
                        style = stroke
                    )
                }
            }

            ComponentType.DIODE -> {
                drawLine(color, Offset(-2f * g, 0f), Offset(-0.45f * g, 0f), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(0.45f * g, 0f), Offset(2f * g, 0f), strokeWidth = baseStroke, cap = StrokeCap.Round)
                val tri = Path().apply {
                    moveTo(-0.45f * g, -0.58f * g)
                    lineTo(-0.45f * g, 0.58f * g)
                    lineTo(0.45f * g, 0f)
                    close()
                }
                drawPath(tri, color = color.copy(alpha = 0.25f))
                drawPath(tri, color = color, style = stroke)
                drawLine(color, Offset(0.45f * g, -0.6f * g), Offset(0.45f * g, 0.6f * g), strokeWidth = baseStroke * 1.2f, cap = StrokeCap.Round)
                if (comp.tertiaryValue > 0.1) {
                    // Zener wings
                    drawLine(color, Offset(0.45f * g, -0.6f * g), Offset(0.2f * g, -0.78f * g), strokeWidth = baseStroke, cap = StrokeCap.Round)
                    drawLine(color, Offset(0.45f * g, 0.6f * g), Offset(0.7f * g, 0.78f * g), strokeWidth = baseStroke, cap = StrokeCap.Round)
                }
            }

            ComponentType.DC_VOLTAGE_SOURCE -> {
                // + is at -2g, - is at +2g
                drawLine(color, Offset(-2f * g, 0f), Offset(-0.35f * g, 0f), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(0.35f * g, 0f), Offset(2f * g, 0f), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawCircle(color = accent.copy(alpha = 0.08f), radius = 0.9f * g, center = Offset.Zero)
                // Long positive plate at -0.35g, short negative plate at +0.35g
                drawLine(LabEmerald, Offset(-0.35f * g, -0.75f * g), Offset(-0.35f * g, 0.75f * g), strokeWidth = baseStroke * 1.2f, cap = StrokeCap.Round)
                drawLine(LabRose, Offset(0.35f * g, -0.4f * g), Offset(0.35f * g, 0.4f * g), strokeWidth = baseStroke * 1.5f, cap = StrokeCap.Round)
                // Plus sign near -0.8g
                drawLine(LabEmerald, Offset(-1.05f * g, -0.35f * g), Offset(-0.65f * g, -0.35f * g), strokeWidth = baseStroke * 0.75f)
                drawLine(LabEmerald, Offset(-0.85f * g, -0.55f * g), Offset(-0.85f * g, -0.15f * g), strokeWidth = baseStroke * 0.75f)
            }

            ComponentType.AC_VOLTAGE_SOURCE -> {
                drawLine(color, Offset(-2f * g, 0f), Offset(-0.9f * g, 0f), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(0.9f * g, 0f), Offset(2f * g, 0f), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawCircle(color = LabCyan.copy(alpha = 0.12f), radius = 0.9f * g, center = Offset.Zero)
                drawCircle(color = color, radius = 0.9f * g, center = Offset.Zero, style = stroke)
                // Sine wave inside
                val wave = Path()
                for (i in 0..24) {
                    val t = i / 24f
                    val px = (-0.5f + t) * 1.0f * g
                    val py = -sin(t * 2f * PI.toFloat()) * 0.35f * g
                    if (i == 0) wave.moveTo(px, py) else wave.lineTo(px, py)
                }
                drawPath(wave, color = LabCyan, style = stroke)
            }

            ComponentType.DC_CURRENT_SOURCE,
            ComponentType.AC_CURRENT_SOURCE -> {
                drawLine(color, Offset(-2f * g, 0f), Offset(-0.9f * g, 0f), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(0.9f * g, 0f), Offset(2f * g, 0f), strokeWidth = baseStroke, cap = StrokeCap.Round)
                val ringColor = if (comp.type == ComponentType.AC_CURRENT_SOURCE) LabAmber else color
                drawCircle(color = LabAmber.copy(alpha = 0.12f), radius = 0.9f * g, center = Offset.Zero)
                drawCircle(color = ringColor, radius = 0.9f * g, center = Offset.Zero, style = stroke)
                // Arrow pointing from +2g (-) toward -2g (+)
                drawLine(LabAmber, Offset(0.5f * g, 0f), Offset(-0.5f * g, 0f), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(LabAmber, Offset(-0.5f * g, 0f), Offset(-0.15f * g, -0.3f * g), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(LabAmber, Offset(-0.5f * g, 0f), Offset(-0.15f * g, 0.3f * g), strokeWidth = baseStroke, cap = StrokeCap.Round)
            }

            ComponentType.NPN_BJT,
            ComponentType.PNP_BJT -> {
                val isNpn = comp.type == ComponentType.NPN_BJT
                drawCircle(color = color.copy(alpha = 0.08f), radius = 1.25f * g, center = Offset(0.25f * g, 0f))
                drawCircle(color = color, radius = 1.25f * g, center = Offset(0.25f * g, 0f), style = Stroke(baseStroke * 0.8f))
                // Base lead (-2,0) to (-0.2,0)
                drawLine(color, Offset(-2f * g, 0f), Offset(-0.2f * g, 0f), strokeWidth = baseStroke, cap = StrokeCap.Round)
                // Vertical base bar
                drawLine(color, Offset(-0.2f * g, -0.7f * g), Offset(-0.2f * g, 0.7f * g), strokeWidth = baseStroke * 1.2f, cap = StrokeCap.Round)
                // Collector (1,-2)
                drawLine(color, Offset(-0.2f * g, -0.35f * g), Offset(1f * g, -1.1f * g), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(1f * g, -1.1f * g), Offset(1f * g, -2f * g), strokeWidth = baseStroke, cap = StrokeCap.Round)
                // Emitter (1,2)
                drawLine(color, Offset(-0.2f * g, 0.35f * g), Offset(1f * g, 1.1f * g), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(1f * g, 1.1f * g), Offset(1f * g, 2f * g), strokeWidth = baseStroke, cap = StrokeCap.Round)
                // Emitter arrow
                val ax = if (isNpn) 0.72f * g else 0.15f * g
                val ay = if (isNpn) 0.92f * g else 0.56f * g
                val angle = if (isNpn) 0.55 else (0.55 + PI)
                val tip = Offset(ax, ay)
                val p1 = Offset(ax - (0.35f * g * cos(angle - 0.5)).toFloat(), ay - (0.35f * g * sin(angle - 0.5)).toFloat())
                val p2 = Offset(ax - (0.35f * g * cos(angle + 0.5)).toFloat(), ay - (0.35f * g * sin(angle + 0.5)).toFloat())
                val arrow = Path().apply { moveTo(tip.x, tip.y); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); close() }
                drawPath(arrow, color = if (isNpn) LabCyan else LabAmber)
            }

            ComponentType.NMOS_FET,
            ComponentType.PMOS_FET -> {
                val isNmos = comp.type == ComponentType.NMOS_FET
                drawCircle(color = color.copy(alpha = 0.08f), radius = 1.25f * g, center = Offset(0.25f * g, 0f))
                drawCircle(color = color, radius = 1.25f * g, center = Offset(0.25f * g, 0f), style = Stroke(baseStroke * 0.8f))
                // Gate (-2,0) to (-0.35,0) + insulated gate plate
                drawLine(color, Offset(-2f * g, 0f), Offset(-0.35f * g, 0f), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(-0.35f * g, -0.65f * g), Offset(-0.35f * g, 0.65f * g), strokeWidth = baseStroke, cap = StrokeCap.Round)
                // Broken 3-segment channel at x = 0.05g
                drawLine(color, Offset(0.05f * g, -0.7f * g), Offset(0.05f * g, -0.3f * g), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(0.05f * g, -0.15f * g), Offset(0.05f * g, 0.15f * g), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(0.05f * g, 0.3f * g), Offset(0.05f * g, 0.7f * g), strokeWidth = baseStroke, cap = StrokeCap.Round)
                // Drain (1,-2) & Source (1,2)
                drawLine(color, Offset(0.05f * g, -0.5f * g), Offset(1f * g, -0.5f * g), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(1f * g, -0.5f * g), Offset(1f * g, -2f * g), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(0.05f * g, 0.5f * g), Offset(1f * g, 0.5f * g), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(1f * g, 0f), Offset(1f * g, 2f * g), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(0.05f * g, 0f), Offset(1f * g, 0f), strokeWidth = baseStroke * 0.9f, cap = StrokeCap.Round)
                // Body arrow
                val arrowColor = if (isNmos) LabCyan else LabAmber
                if (isNmos) {
                    drawLine(arrowColor, Offset(0.15f * g, 0f), Offset(0.45f * g, -0.22f * g), strokeWidth = baseStroke)
                    drawLine(arrowColor, Offset(0.15f * g, 0f), Offset(0.45f * g, 0.22f * g), strokeWidth = baseStroke)
                } else {
                    drawLine(arrowColor, Offset(0.7f * g, 0f), Offset(0.4f * g, -0.22f * g), strokeWidth = baseStroke)
                    drawLine(arrowColor, Offset(0.7f * g, 0f), Offset(0.4f * g, 0.22f * g), strokeWidth = baseStroke)
                }
            }

            ComponentType.OP_AMP -> {
                // + at (-2,-1), - at (-2,1), OUT at (2,0)
                drawLine(color, Offset(-2f * g, -1f * g), Offset(-1.1f * g, -1f * g), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(-2f * g, 1f * g), Offset(-1.1f * g, 1f * g), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(1.25f * g, 0f), Offset(2f * g, 0f), strokeWidth = baseStroke, cap = StrokeCap.Round)
                val tri = Path().apply {
                    moveTo(-1.1f * g, -1.6f * g)
                    lineTo(-1.1f * g, 1.6f * g)
                    lineTo(1.25f * g, 0f)
                    close()
                }
                drawPath(tri, color = accent.copy(alpha = 0.14f))
                drawPath(tri, color = color, style = stroke)
                // + and - inside triangle
                drawLine(LabEmerald, Offset(-0.85f * g, -0.8f * g), Offset(-0.45f * g, -0.8f * g), strokeWidth = baseStroke * 0.85f)
                drawLine(LabEmerald, Offset(-0.65f * g, -1.0f * g), Offset(-0.65f * g, -0.6f * g), strokeWidth = baseStroke * 0.85f)
                drawLine(LabRose, Offset(-0.85f * g, 0.8f * g), Offset(-0.45f * g, 0.8f * g), strokeWidth = baseStroke * 0.85f)
            }

            ComponentType.VCVS,
            ComponentType.VCCS,
            ComponentType.CCVS,
            ComponentType.CCCS -> {
                // Sense leads (-2,-1) and (-2,1), Output leads (2,-1) and (2,1)
                drawLine(color, Offset(-2f * g, -1f * g), Offset(-0.9f * g, -1f * g), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(-2f * g, 1f * g), Offset(-0.9f * g, 1f * g), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(-0.9f * g, -1f * g), Offset(-0.9f * g, 1f * g), strokeWidth = baseStroke * 0.65f, cap = StrokeCap.Round)
                drawLine(color, Offset(0.9f * g, -1f * g), Offset(2f * g, -1f * g), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(0.9f * g, 1f * g), Offset(2f * g, 1f * g), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(0.9f * g, -1f * g), Offset(0.9f * g, -0.95f * g), strokeWidth = baseStroke)
                // Diamond in center-right
                val diamond = Path().apply {
                    moveTo(0.9f * g, -1.05f * g)
                    lineTo(1.85f * g, 0f)
                    lineTo(0.9f * g, 1.05f * g)
                    lineTo(-0.05f * g, 0f)
                    close()
                }
                drawPath(diamond, color = LabAmber.copy(alpha = 0.16f))
                drawPath(diamond, color = LabAmber, style = stroke)
            }

            ComponentType.VOLTMETER,
            ComponentType.AMMETER,
            ComponentType.OHMMETER,
            ComponentType.MULTIMETER -> {
                val meterColor = when (comp.type) {
                    ComponentType.VOLTMETER -> LabCyan
                    ComponentType.AMMETER -> LabAmber
                    ComponentType.OHMMETER -> LabEmerald
                    else -> accent
                }
                drawLine(color, Offset(-2f * g, 0f), Offset(-0.95f * g, 0f), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(0.95f * g, 0f), Offset(2f * g, 0f), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawCircle(
                    color = if (isDark) Color(0xFF0E1722) else Color.White,
                    radius = 0.95f * g,
                    center = Offset.Zero
                )
                drawCircle(color = meterColor, radius = 0.95f * g, center = Offset.Zero, style = stroke)
            }

            ComponentType.WATTMETER -> {
                drawLine(color, Offset(-2f * g, -1f * g), Offset(2f * g, -1f * g), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawLine(color, Offset(-2f * g, 1f * g), Offset(2f * g, 1f * g), strokeWidth = baseStroke, cap = StrokeCap.Round)
                drawRoundRect(
                    color = if (isDark) Color(0xFF0E1722) else Color.White,
                    topLeft = Offset(-1.1f * g, -1.35f * g),
                    size = Size(2.2f * g, 2.7f * g),
                    cornerRadius = CornerRadius(0.3f * g, 0.3f * g)
                )
                drawRoundRect(
                    color = LabEmerald,
                    topLeft = Offset(-1.1f * g, -1.35f * g),
                    size = Size(2.2f * g, 2.7f * g),
                    cornerRadius = CornerRadius(0.3f * g, 0.3f * g),
                    style = stroke
                )
            }
        }
    }

    private fun DrawScope.drawComponentLabels(
        comp: CircuitComponent,
        cx: Float,
        cy: Float,
        g: Float,
        strokeColor: Color,
        isDark: Boolean,
        textMeasurer: TextMeasurer,
        meterReading: MeterReading?
    ) {
        if (g < 14f) return
        val labelFontSize = (g * 0.36f).coerceIn(8.5f, 13f).sp
        val valueFontSize = (g * 0.32f).coerceIn(8f, 12f).sp

        if (comp.type == ComponentType.SCOPE_PROBE) {
            val chColor = scopeChannelColor(comp.probeChannel)
            val txt = textMeasurer.measure(
                "CH${comp.probeChannel}",
                TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = labelFontSize,
                    color = chColor
                )
            )
            drawText(txt, topLeft = Offset(cx + g * 0.48f, cy - g * 0.85f))
            return
        }

        if (comp.type == ComponentType.GROUND) {
            val txt = textMeasurer.measure(
                "GND",
                TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = valueFontSize,
                    color = strokeColor.copy(alpha = 0.7f)
                )
            )
            drawText(txt, topLeft = Offset(cx - txt.size.width / 2f, cy + g * 1.5f))
            return
        }

        // Meter live OLED readout inside/near meter
        if (meterReading != null) {
            val readingStr = UnitFormatter.formatCompact(meterReading.primaryDisplay, meterReading.unit)
            val badgeColor = when (comp.type) {
                ComponentType.VOLTMETER -> LabCyan
                ComponentType.AMMETER -> LabAmber
                ComponentType.OHMMETER -> LabEmerald
                ComponentType.WATTMETER -> LabEmerald
                else -> LabCyan
            }
            val measured = textMeasurer.measure(
                readingStr,
                TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = (g * 0.38f).coerceIn(9f, 14f).sp,
                    color = badgeColor
                )
            )
            val padX = g * 0.22f
            val padY = g * 0.1f
            val boxW = measured.size.width + padX * 2
            val boxH = measured.size.height + padY * 2
            val boxTop = cy - boxH / 2f
            val boxLeft = cx - boxW / 2f
            drawRoundRect(
                color = if (isDark) Color(0xEE090E15) else Color(0xEEF8FAFC),
                topLeft = Offset(boxLeft, boxTop),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(g * 0.18f, g * 0.18f)
            )
            drawRoundRect(
                color = badgeColor.copy(alpha = 0.75f),
                topLeft = Offset(boxLeft, boxTop),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(g * 0.18f, g * 0.18f),
                style = Stroke(width = 1.5f)
            )
            drawText(measured, topLeft = Offset(boxLeft + padX, boxTop + padY))
        }

        val isVertical = (comp.rotation % 180) != 0
        val labelMeasured = textMeasurer.measure(
            comp.label,
            TextStyle(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = labelFontSize,
                color = strokeColor
            )
        )
        val valText = if (meterReading != null) "" else comp.summaryValueText()
        val valMeasured = if (valText.isNotEmpty()) {
            textMeasurer.measure(
                valText,
                TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = valueFontSize,
                    color = strokeColor.copy(alpha = 0.72f)
                )
            )
        } else null

        if (isVertical) {
            val offsetX = cx + g * 1.15f
            drawText(labelMeasured, topLeft = Offset(offsetX, cy - labelMeasured.size.height))
            if (valMeasured != null) {
                drawText(valMeasured, topLeft = Offset(offsetX, cy + 2f))
            }
        } else {
            val topY = cy - g * 1.1f - labelMeasured.size.height
            drawText(labelMeasured, topLeft = Offset(cx - labelMeasured.size.width / 2f, topY))
            if (valMeasured != null) {
                drawText(valMeasured, topLeft = Offset(cx - valMeasured.size.width / 2f, cy + g * 0.9f))
            }
        }
    }
}
