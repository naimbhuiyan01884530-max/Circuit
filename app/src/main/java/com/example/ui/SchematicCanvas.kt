package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import com.example.model.CircuitComponent
import com.example.model.CircuitSchematic
import com.example.model.GridPoint
import com.example.simulation.CompiledNetlist
import com.example.simulation.SimulationSnapshot
import com.example.ui.theme.LabAmber
import com.example.ui.theme.LabCyan
import com.example.ui.theme.LocalVoltLabColors
import com.example.ui.theme.VoltageHigh
import com.example.ui.theme.VoltageNegative
import com.example.ui.theme.VoltagePositive
import com.example.ui.theme.VoltageZero
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.roundToInt

@Stable
class CanvasViewportState {
    var panOffset by mutableStateOf(Offset.Zero)
    var gridSizePx by mutableFloatStateOf(36f)
    var canvasSize by mutableStateOf(IntSize.Zero)

    fun screenToWorld(screenPos: Offset): Offset {
        val cx = canvasSize.width / 2f + panOffset.x
        val cy = canvasSize.height / 2f + panOffset.y
        return Offset(
            (screenPos.x - cx) / gridSizePx,
            (screenPos.y - cy) / gridSizePx
        )
    }

    fun screenToGrid(screenPos: Offset): GridPoint {
        val w = screenToWorld(screenPos)
        return GridPoint(w.x.roundToInt(), w.y.roundToInt())
    }

    fun resetView() {
        panOffset = Offset.Zero
        gridSizePx = 36f
    }

    fun fitToSchematic(schematic: CircuitSchematic) {
        if (schematic.components.isEmpty() || canvasSize.width == 0 || canvasSize.height == 0) {
            resetView()
            return
        }
        var minX = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var minY = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE
        for (c in schematic.components) {
            minX = minOf(minX, c.x - 3)
            maxX = maxOf(maxX, c.x + 3)
            minY = minOf(minY, c.y - 3)
            maxY = maxOf(maxY, c.y + 3)
        }
        val spanX = (maxX - minX).coerceAtLeast(10)
        val spanY = (maxY - minY).coerceAtLeast(8)
        val newGrid = minOf(canvasSize.width / (spanX + 2f), canvasSize.height / (spanY + 2f)).coerceIn(18f, 52f)
        val midX = (minX + maxX) / 2f
        val midY = (minY + maxY) / 2f
        gridSizePx = newGrid
        panOffset = Offset(-midX * newGrid, -midY * newGrid)
    }
}

@Composable
fun SchematicCanvas(
    schematic: CircuitSchematic,
    netlist: CompiledNetlist,
    snapshot: SimulationSnapshot,
    activeTool: WorkbenchTool,
    selectedComponentId: String?,
    selectedWireId: String?,
    wireStartPoint: GridPoint?,
    wirePreviewEnd: GridPoint?,
    paletteDrag: PaletteDragState?,
    showVoltageOverlay: Boolean,
    showCurrentDots: Boolean,
    viewportState: CanvasViewportState,
    onTapSelect: (worldX: Float, worldY: Float) -> Unit,
    onBeginComponentDrag: () -> Unit,
    onDragComponent: (id: String, gridX: Int, gridY: Int) -> Unit,
    onCommitComponentDrag: () -> Unit,
    onWireTap: (worldX: Float, worldY: Float) -> Unit,
    onWirePreviewMove: (worldX: Float, worldY: Float) -> Unit,
    onProbeTap: (worldX: Float, worldY: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val labColors = LocalVoltLabColors.current
    val textMeasurer = rememberTextMeasurer()

    // 60fps animation phase for current-flow particles
    var animPhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(snapshot.isRunning, showCurrentDots) {
        var lastNs = 0L
        while (true) {
            withFrameNanos { ns ->
                if (lastNs != 0L && snapshot.isRunning && showCurrentDots) {
                    val dt = ((ns - lastNs) / 1e9f).coerceAtMost(0.05f)
                    animPhase = (animPhase + dt) % 1000f
                }
                lastNs = ns
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(labColors.canvasBackground)
            .onSizeChanged { size ->
                val wasZero = viewportState.canvasSize == IntSize.Zero
                viewportState.canvasSize = size
                if (wasZero && size.width > 0) {
                    viewportState.fitToSchematic(schematic)
                }
            }
            .pointerInput(activeTool, schematic, selectedComponentId) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val startScreen = firstDown.position
                    val startWorld = viewportState.screenToWorld(startScreen)

                    val hitComp = if (activeTool == WorkbenchTool.SELECT) {
                        SmartWireRouter.findComponentAt(startWorld.x, startWorld.y, schematic)
                    } else null

                    var draggingCompId: String? = null
                    var movedTotal = 0f
                    var isMultiTouch = false
                    var dragBegun = false

                    if (hitComp != null && hitComp.id == selectedComponentId) {
                        draggingCompId = hitComp.id
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount > 1) {
                            isMultiTouch = true
                            draggingCompId = null
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroid = event.calculateCentroid()
                            val oldGrid = viewportState.gridSizePx
                            val newGrid = (oldGrid * zoomChange).coerceIn(14f, 76f)
                            val scaleRatio = newGrid / oldGrid
                            val center = Offset(
                                viewportState.canvasSize.width / 2f,
                                viewportState.canvasSize.height / 2f
                            )
                            val relCentroid = centroid - center
                            viewportState.panOffset =
                                (viewportState.panOffset - relCentroid) * scaleRatio + relCentroid + panChange
                            viewportState.gridSizePx = newGrid
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (pressedCount == 1) {
                            val change = event.changes.first()
                            val delta = change.position - change.previousPosition
                            movedTotal += hypot(delta.x, delta.y)

                            if (!isMultiTouch && movedTotal > 8f) {
                                if (draggingCompId != null) {
                                    if (!dragBegun) {
                                        onBeginComponentDrag()
                                        dragBegun = true
                                    }
                                    val gp = viewportState.screenToGrid(change.position)
                                    onDragComponent(draggingCompId, gp.x, gp.y)
                                    change.consume()
                                } else if (activeTool == WorkbenchTool.WIRE && wireStartPoint != null) {
                                    val w = viewportState.screenToWorld(change.position)
                                    onWirePreviewMove(w.x, w.y)
                                    change.consume()
                                } else {
                                    // Pan the canvas
                                    viewportState.panOffset += delta
                                    change.consume()
                                }
                            }
                        } else {
                            // Released
                            if (!isMultiTouch && movedTotal <= 12f) {
                                val w = viewportState.screenToWorld(startScreen)
                                when (activeTool) {
                                    WorkbenchTool.SELECT -> onTapSelect(w.x, w.y)
                                    WorkbenchTool.WIRE -> onWireTap(w.x, w.y)
                                    WorkbenchTool.PROBE -> onProbeTap(w.x, w.y)
                                }
                            } else if (dragBegun) {
                                onCommitComponentDrag()
                            } else if (activeTool == WorkbenchTool.WIRE && wireStartPoint != null && !isMultiTouch && movedTotal > 12f) {
                                val endPos = event.changes.firstOrNull()?.position ?: startScreen
                                val w = viewportState.screenToWorld(endPos)
                                onWireTap(w.x, w.y)
                            }
                            break
                        }
                    }
                }
            }
            .testTag("schematic_canvas")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val g = viewportState.gridSizePx
            val centerX = size.width / 2f + viewportState.panOffset.x
            val centerY = size.height / 2f + viewportState.panOffset.y

            // 1. Technical Engineering Grid
            drawEngineeringGrid(centerX, centerY, g, labColors.gridMinor, labColors.gridMajor)

            // 2. Wires with voltage coloring & animated current particles
            for (wire in schematic.wires) {
                if (wire.points.size < 2) continue
                val isWireSelected = wire.id == selectedWireId
                val nodeId = wire.points.firstOrNull()?.let { netlist.pointToNode[it] } ?: 0
                val voltage = snapshot.nodeVoltages[nodeId] ?: 0.0
                val wireColor = when {
                    isWireSelected -> labColors.selectionGlow
                    showVoltageOverlay -> voltageToColor(voltage, labColors.wireIdle)
                    else -> labColors.wireIdle
                }

                val path = Path()
                wire.points.forEachIndexed { idx, pt ->
                    val sx = centerX + pt.x * g
                    val sy = centerY + pt.y * g
                    if (idx == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                }

                if (isWireSelected) {
                    drawPath(
                        path = path,
                        color = labColors.selectionGlow.copy(alpha = 0.25f),
                        style = Stroke(width = (g * 0.34f).coerceIn(8f, 14f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                drawPath(
                    path = path,
                    color = wireColor,
                    style = Stroke(width = (g * 0.09f).coerceIn(2.2f, 4.5f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Current-flow dots along wire
                if (showCurrentDots) {
                    val iWire = snapshot.wireCurrents[wire.id] ?: 0.0
                    if (abs(iWire) > 1e-6) {
                        drawCurrentFlowParticles(
                            points = wire.points,
                            centerX = centerX,
                            centerY = centerY,
                            g = g,
                            currentAmps = iWire,
                            animPhase = animPhase
                        )
                    }
                }
            }

            // 3. Smart Wire Preview
            if (wireStartPoint != null && wirePreviewEnd != null) {
                val previewPts = SmartWireRouter.route(wireStartPoint, wirePreviewEnd, schematic.components)
                val pPath = Path()
                previewPts.forEachIndexed { idx, pt ->
                    val sx = centerX + pt.x * g
                    val sy = centerY + pt.y * g
                    if (idx == 0) pPath.moveTo(sx, sy) else pPath.lineTo(sx, sy)
                }
                drawPath(
                    path = pPath,
                    color = LabCyan,
                    style = Stroke(
                        width = (g * 0.1f).coerceIn(2.5f, 4.5f),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                    )
                )
                drawCircle(
                    color = LabAmber,
                    radius = (g * 0.22f).coerceIn(5f, 9f),
                    center = Offset(centerX + wireStartPoint.x * g, centerY + wireStartPoint.y * g)
                )
            }

            // 4. Junction Dots
            for (dot in netlist.junctionDots) {
                val nodeId = netlist.pointToNode[dot] ?: 0
                val v = snapshot.nodeVoltages[nodeId] ?: 0.0
                val dotColor = if (showVoltageOverlay) voltageToColor(v, labColors.wireIdle) else labColors.wireIdle
                drawCircle(
                    color = dotColor,
                    radius = (g * 0.15f).coerceIn(3.5f, 7f),
                    center = Offset(centerX + dot.x * g, centerY + dot.y * g)
                )
            }

            // 5. Components & Live Meter Readouts
            // Translate into world-centered coordinates
            val worldOriginX = centerX
            val worldOriginY = centerY
            drawContext.canvas.save()
            drawContext.canvas.translate(worldOriginX, worldOriginY)
            for (comp in schematic.components) {
                ComponentSymbolRenderer.drawComponent(
                    scope = this,
                    comp = comp,
                    gridSizePx = g,
                    strokeColor = labColors.componentStroke,
                    accentColor = labColors.selectionGlow,
                    terminalColor = labColors.terminalDot,
                    isSelected = comp.id == selectedComponentId,
                    isDark = labColors.isDark,
                    textMeasurer = textMeasurer,
                    meterReading = snapshot.meterReadings[comp.id]
                )
            }

            // 6. Drag-and-Drop Ghost Preview from Component Palette
            val drag = paletteDrag
            if (drag?.gridPoint != null) {
                val ghost = CircuitComponent(
                    id = "ghost_preview",
                    type = drag.type,
                    label = drag.type.shortCode,
                    x = drag.gridPoint.x,
                    y = drag.gridPoint.y
                )
                ComponentSymbolRenderer.drawComponent(
                    scope = this,
                    comp = ghost,
                    gridSizePx = g,
                    strokeColor = LabCyan.copy(alpha = 0.75f),
                    accentColor = LabAmber,
                    terminalColor = LabAmber,
                    isSelected = true,
                    isDark = labColors.isDark,
                    textMeasurer = null,
                    meterReading = null
                )
            }
            drawContext.canvas.restore()
        }
    }
}

private fun DrawScope.drawEngineeringGrid(
    centerX: Float,
    centerY: Float,
    gridPx: Float,
    minorColor: Color,
    majorColor: Color
) {
    val startCol = kotlin.math.floor(-centerX / gridPx).toInt() - 1
    val endCol = kotlin.math.ceil((size.width - centerX) / gridPx).toInt() + 1
    val startRow = kotlin.math.floor(-centerY / gridPx).toInt() - 1
    val endRow = kotlin.math.ceil((size.height - centerY) / gridPx).toInt() + 1

    for (col in startCol..endCol) {
        val x = centerX + col * gridPx
        val isMajor = col % 4 == 0
        drawLine(
            color = if (isMajor) majorColor else minorColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = if (isMajor) 1.4f else 0.8f
        )
    }
    for (row in startRow..endRow) {
        val y = centerY + row * gridPx
        val isMajor = row % 4 == 0
        drawLine(
            color = if (isMajor) majorColor else minorColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = if (isMajor) 1.4f else 0.8f
        )
    }
}

private fun DrawScope.drawCurrentFlowParticles(
    points: List<GridPoint>,
    centerX: Float,
    centerY: Float,
    g: Float,
    currentAmps: Double,
    animPhase: Float
) {
    val mag = abs(currentAmps)
    val speedGridPerSec = (0.7 + 1.4 * ln(1.0 + mag * 1000.0)).coerceIn(0.6, 5.5).toFloat()
    val spacing = g * 1.4f
    val dir = if (currentAmps >= 0) 1f else -1f

    for (i in 0 until points.size - 1) {
        val ax = centerX + points[i].x * g
        val ay = centerY + points[i].y * g
        val bx = centerX + points[i + 1].x * g
        val by = centerY + points[i + 1].y * g
        val dx = bx - ax
        val dy = by - ay
        val segLen = hypot(dx, dy)
        if (segLen < 2f) continue

        val phasePx = ((animPhase * speedGridPerSec * g * dir) % spacing + spacing) % spacing
        var d = phasePx
        while (d < segLen) {
            val t = d / segLen
            drawCircle(
                color = LabAmber,
                radius = (g * 0.095f).coerceIn(2.2f, 4.5f),
                center = Offset(ax + dx * t, ay + dy * t)
            )
            d += spacing
        }
    }
}

fun voltageToColor(voltage: Double, zeroColor: Color): Color {
    val v = voltage.coerceIn(-12.0, 12.0)
    return when {
        abs(v) < 0.08 -> zeroColor
        v > 0 -> {
            val t = (v / 8.0).coerceIn(0.0, 1.0).toFloat()
            if (t < 0.5f) {
                lerp(zeroColor, VoltagePositive, t * 2f)
            } else {
                lerp(VoltagePositive, VoltageHigh, (t - 0.5f) * 2f)
            }
        }
        else -> {
            val t = (-v / 8.0).coerceIn(0.0, 1.0).toFloat()
            lerp(zeroColor, VoltageNegative, t)
        }
    }
}
