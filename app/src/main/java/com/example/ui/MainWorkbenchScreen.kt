package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.UnitFormatter
import com.example.ui.theme.LabAmber
import com.example.ui.theme.LabBorder
import com.example.ui.theme.LabCyan
import com.example.ui.theme.LabEmerald
import com.example.ui.theme.LabRose
import com.example.ui.theme.LabSurface
import com.example.ui.theme.LabSurfaceElevated
import com.example.ui.theme.LocalVoltLabColors
import kotlin.math.roundToInt

@Composable
fun MainWorkbenchScreen(
    viewModel: CircuitViewModel
) {
    val context = LocalContext.current
    val schematic by viewModel.schematic.collectAsStateWithLifecycle()
    val netlist by viewModel.netlist.collectAsStateWithLifecycle()
    val snapshot by viewModel.simulationSnapshot.collectAsStateWithLifecycle()
    val savedCircuits by viewModel.savedCircuits.collectAsStateWithLifecycle()
    val activeTool by viewModel.activeTool.collectAsStateWithLifecycle()
    val selectedComponentId by viewModel.selectedComponentId.collectAsStateWithLifecycle()
    val selectedWireId by viewModel.selectedWireId.collectAsStateWithLifecycle()
    val wireStartPoint by viewModel.wireStartPoint.collectAsStateWithLifecycle()
    val wirePreviewEnd by viewModel.wirePreviewEnd.collectAsStateWithLifecycle()
    val paletteDrag by viewModel.paletteDrag.collectAsStateWithLifecycle()
    val probeReadout by viewModel.probeReadout.collectAsStateWithLifecycle()
    val isScopeVisible by viewModel.isScopeVisible.collectAsStateWithLifecycle()
    val scopeConfig by viewModel.scopeConfig.collectAsStateWithLifecycle()
    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val showVoltageOverlay by viewModel.showVoltageOverlay.collectAsStateWithLifecycle()
    val showCurrentDots by viewModel.showCurrentDots.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    val viewportState = remember { CanvasViewportState() }
    var canvasRootOrigin by remember { mutableStateOf(Offset.Zero) }

    var showInspector by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var showSimSettings by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val selectedComponent = remember(schematic.components, selectedComponentId) {
        schematic.components.find { it.id == selectedComponentId }
    }

    // SAF JSON Export & Import
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(viewModel.exportCurrentJson().toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                Toast.makeText(context, "Exported JSON schematic", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Export failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: ""
            }.onSuccess { text ->
                if (text.isNotBlank()) {
                    viewModel.importCircuitJson(text) { ok, msg ->
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (ok) showLibrary = false
                        }
                    }
                }
            }.onFailure {
                Toast.makeText(context, "Import failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(statusBanner) {
        statusBanner?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearStatusBanner()
        }
    }

    val labColors = LocalVoltLabColors.current

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Engineering Toolbar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Circuit Name & Library button
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                            .clickable { showLibrary = true }
                            .testTag("open_library_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Open Circuit Library",
                                tint = LabCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = schematic.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    // Sim Controls (Run/Pause, Step, Reset, Undo/Redo, Fit, Menu)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Surface(
                            color = if (snapshot.isRunning) LabEmerald.copy(alpha = 0.2f) else LabAmber.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .border(
                                    1.dp,
                                    if (snapshot.isRunning) LabEmerald else LabAmber,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.toggleRunPause() }
                                .testTag("sim_run_pause_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (snapshot.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (snapshot.isRunning) "Pause" else "Run",
                                    tint = if (snapshot.isRunning) LabEmerald else LabAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = if (snapshot.isRunning) "RUN" else "HOLD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (snapshot.isRunning) LabEmerald else LabAmber,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.stepSimulation() },
                            modifier = Modifier.size(36.dp).testTag("sim_step_button")
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Step", modifier = Modifier.size(20.dp))
                        }

                        IconButton(
                            onClick = { viewModel.resetSimulation() },
                            modifier = Modifier.size(36.dp).testTag("sim_reset_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset Sim", modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = { viewModel.undo() },
                            enabled = canUndo,
                            modifier = Modifier.size(34.dp).testTag("undo_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = { viewModel.redo() },
                            enabled = canRedo,
                            modifier = Modifier.size(34.dp).testTag("redo_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", modifier = Modifier.size(18.dp))
                        }

                        Box {
                            IconButton(
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier.size(34.dp).testTag("overflow_menu_button")
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More Options", modifier = Modifier.size(20.dp))
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Fit Circuit to Screen") },
                                    leadingIcon = { Icon(Icons.Default.CenterFocusStrong, null) },
                                    onClick = {
                                        viewportState.fitToSchematic(schematic)
                                        showOverflowMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Simulation Timestep & Speed") },
                                    leadingIcon = { Icon(Icons.Default.Tune, null) },
                                    onClick = {
                                        showSimSettings = true
                                        showOverflowMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (showVoltageOverlay) "Hide Voltage Colors" else "Show Voltage Colors") },
                                    onClick = {
                                        viewModel.toggleVoltageOverlay()
                                        showOverflowMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (showCurrentDots) "Hide Current Flow" else "Show Current Flow") },
                                    onClick = {
                                        viewModel.toggleCurrentDots()
                                        showOverflowMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (isDarkTheme) "Daylight Blueprint Theme" else "Dark Lab Theme") },
                                    onClick = {
                                        viewModel.toggleTheme()
                                        showOverflowMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Main Adaptive Workspace (Canvas + Optional Split Oscilloscope)
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val isWide = maxWidth >= 700.dp

                if (isWide) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1.35f)
                                .fillMaxHeight()
                                .onGloballyPositioned { coords ->
                                    canvasRootOrigin = coords.positionInRoot()
                                }
                        ) {
                            CanvasWithOverlays(
                                viewModel = viewModel,
                                schematic = schematic,
                                netlist = netlist,
                                snapshot = snapshot,
                                activeTool = activeTool,
                                selectedComponentId = selectedComponentId,
                                selectedWireId = selectedWireId,
                                wireStartPoint = wireStartPoint,
                                wirePreviewEnd = wirePreviewEnd,
                                paletteDrag = paletteDrag,
                                showVoltageOverlay = showVoltageOverlay,
                                showCurrentDots = showCurrentDots,
                                viewportState = viewportState,
                                selectedComponent = selectedComponent,
                                probeReadout = probeReadout,
                                isScopeVisible = isScopeVisible,
                                onOpenInspector = { showInspector = true }
                            )
                        }
                        if (isScopeVisible) {
                            OscilloscopePanel(
                                samples = snapshot.scopeSamples,
                                config = scopeConfig,
                                onUpdateConfig = { viewModel.updateScopeConfig(it) },
                                onClose = { viewModel.toggleScopeVisible() },
                                modifier = Modifier.weight(0.95f).fillMaxHeight()
                            )
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .onGloballyPositioned { coords ->
                                    canvasRootOrigin = coords.positionInRoot()
                                }
                        ) {
                            CanvasWithOverlays(
                                viewModel = viewModel,
                                schematic = schematic,
                                netlist = netlist,
                                snapshot = snapshot,
                                activeTool = activeTool,
                                selectedComponentId = selectedComponentId,
                                selectedWireId = selectedWireId,
                                wireStartPoint = wireStartPoint,
                                wirePreviewEnd = wirePreviewEnd,
                                paletteDrag = paletteDrag,
                                showVoltageOverlay = showVoltageOverlay,
                                showCurrentDots = showCurrentDots,
                                viewportState = viewportState,
                                selectedComponent = selectedComponent,
                                probeReadout = probeReadout,
                                isScopeVisible = isScopeVisible,
                                onOpenInspector = { showInspector = true }
                            )
                        }
                        AnimatedVisibility(visible = isScopeVisible) {
                            OscilloscopePanel(
                                samples = snapshot.scopeSamples,
                                config = scopeConfig,
                                onUpdateConfig = { viewModel.updateScopeConfig(it) },
                                onClose = { viewModel.toggleScopeVisible() }
                            )
                        }
                    }
                }
            }

            // Bottom Component Palette (Drag-and-drop + Tap-to-place)
            ComponentPalette(
                onTapComponent = { type ->
                    // Place cleanly near current viewport center
                    val centerGrid = viewportState.screenToGrid(
                        Offset(viewportState.canvasSize.width / 2f, viewportState.canvasSize.height / 2f)
                    )
                    viewModel.addComponentAt(type, centerGrid.x, centerGrid.y)
                },
                onStartDrag = { type, rootPos ->
                    val localCanvasPos = rootPos - canvasRootOrigin
                    val gp = if (localCanvasPos.y in 0f..viewportState.canvasSize.height.toFloat()) {
                        viewportState.screenToGrid(localCanvasPos)
                    } else null
                    viewModel.updatePaletteDrag(PaletteDragState(type, rootPos.x, rootPos.y, gp))
                },
                onDragMove = { rootPos ->
                    val cur = viewModel.paletteDrag.value ?: return@ComponentPalette
                    val localCanvasPos = rootPos - canvasRootOrigin
                    val gp = if (localCanvasPos.y in 0f..viewportState.canvasSize.height.toFloat()) {
                        viewportState.screenToGrid(localCanvasPos)
                    } else null
                    viewModel.updatePaletteDrag(cur.copy(screenX = rootPos.x, screenY = rootPos.y, gridPoint = gp))
                },
                onDragEnd = {
                    val cur = viewModel.paletteDrag.value
                    if (cur?.gridPoint != null) {
                        viewModel.addComponentAt(cur.type, cur.gridPoint.x, cur.gridPoint.y)
                    }
                    viewModel.updatePaletteDrag(null)
                }
            )
        }

        // Dragging Ghost Floating Badge above Finger
        paletteDrag?.let { drag ->
            Surface(
                color = LabCyan.copy(alpha = 0.9f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .offset { IntOffset((drag.screenX - 48).roundToInt(), (drag.screenY - 120).roundToInt()) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Canvas(modifier = Modifier.size(24.dp)) {
                        ComponentSymbolRenderer.drawPaletteIcon(
                            scope = this,
                            type = drag.type,
                            color = Color.Black,
                            accent = Color.Black
                        )
                    }
                    Text(
                        text = drag.type.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Inspector Bottom Sheet
    if (showInspector && selectedComponent != null) {
        ComponentInspectorSheet(
            component = selectedComponent,
            allComponents = schematic.components,
            branchCurrent = snapshot.branchCurrents[selectedComponent.id],
            powerWatts = snapshot.componentPowers[selectedComponent.id],
            meterReading = snapshot.meterReadings[selectedComponent.id],
            onUpdate = { viewModel.updateComponent(it) },
            onRotate = { viewModel.rotateSelectedComponent() },
            onFlip = { viewModel.flipSelectedComponent() },
            onDuplicate = { viewModel.duplicateSelectedComponent() },
            onDelete = {
                viewModel.deleteSelected()
                showInspector = false
            },
            onDismiss = { showInspector = false }
        )
    }

    // Circuit Library & JSON Sheet
    if (showLibrary) {
        CircuitLibrarySheet(
            currentName = schematic.name,
            circuits = savedCircuits,
            exportedJson = viewModel.exportCurrentJson(),
            onLoadCircuit = { id ->
                viewModel.loadCircuitFromLibrary(id)
                viewportState.resetView()
            },
            onSaveCurrent = { name -> viewModel.saveCurrentCircuit(name) },
            onNewBlank = {
                viewModel.createNewBlankCircuit()
                viewportState.resetView()
            },
            onDeleteCircuit = { id -> viewModel.deleteSavedCircuit(id) },
            onImportJsonText = { txt, cb -> viewModel.importCircuitJson(txt, cb) },
            onRequestExportFile = {
                val safeName = schematic.name.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
                exportLauncher.launch("$safeName.voltlab.json")
            },
            onRequestImportFile = {
                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            },
            onDismiss = { showLibrary = false }
        )
    }

    // Simulation Timestep & Speed Modal
    if (showSimSettings) {
        var dtUs by remember { mutableFloatStateOf(schematic.timeStepMicros.toFloat()) }
        var speed by remember { mutableFloatStateOf(schematic.simSpeedMultiplier.toFloat()) }

        AlertDialog(
            onDismissRequest = { showSimSettings = false },
            title = { Text("MNA Solver Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Timestep Δt: ${dtUs.toInt()} μs",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = dtUs,
                        onValueChange = { dtUs = it },
                        valueRange = 10f..1000f
                    )
                    Text(
                        text = "Simulation Speed: ${String.format(java.util.Locale.US, "%.1f", speed)}×",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = speed,
                        onValueChange = { speed = it },
                        valueRange = 0.2f..5.0f
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateTimeStepAndSpeed(dtUs.toDouble(), speed.toDouble())
                    showSimSettings = false
                }) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSimSettings = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CanvasWithOverlays(
    viewModel: CircuitViewModel,
    schematic: com.example.model.CircuitSchematic,
    netlist: com.example.simulation.CompiledNetlist,
    snapshot: com.example.simulation.SimulationSnapshot,
    activeTool: WorkbenchTool,
    selectedComponentId: String?,
    selectedWireId: String?,
    wireStartPoint: com.example.model.GridPoint?,
    wirePreviewEnd: com.example.model.GridPoint?,
    paletteDrag: PaletteDragState?,
    showVoltageOverlay: Boolean,
    showCurrentDots: Boolean,
    viewportState: CanvasViewportState,
    selectedComponent: com.example.model.CircuitComponent?,
    probeReadout: ProbeReadout?,
    isScopeVisible: Boolean,
    onOpenInspector: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        SchematicCanvas(
            schematic = schematic,
            netlist = netlist,
            snapshot = snapshot,
            activeTool = activeTool,
            selectedComponentId = selectedComponentId,
            selectedWireId = selectedWireId,
            wireStartPoint = wireStartPoint,
            wirePreviewEnd = wirePreviewEnd,
            paletteDrag = paletteDrag,
            showVoltageOverlay = showVoltageOverlay,
            showCurrentDots = showCurrentDots,
            viewportState = viewportState,
            onTapSelect = { wx, wy ->
                val comp = SmartWireRouter.findComponentAt(wx, wy, schematic)
                if (comp != null) {
                    if (comp.id == selectedComponentId) {
                        onOpenInspector()
                    } else {
                        viewModel.selectComponent(comp.id)
                    }
                } else {
                    val wire = SmartWireRouter.findWireAt(wx, wy, schematic.wires)
                    if (wire != null) {
                        viewModel.selectWire(wire.id)
                    } else {
                        viewModel.clearSelection()
                    }
                }
            },
            onBeginComponentDrag = { viewModel.beginComponentDrag() },
            onDragComponent = { id, gx, gy -> viewModel.moveSelectedComponentTo(id, gx, gy) },
            onCommitComponentDrag = { viewModel.commitComponentDrag() },
            onWireTap = { wx, wy -> viewModel.startOrFinishWire(wx, wy) },
            onWirePreviewMove = { wx, wy -> viewModel.updateWirePreview(wx, wy) },
            onProbeTap = { wx, wy -> viewModel.probeAt(wx, wy) }
        )

        // Top-Left Tool Mode Switcher Pill + Scope Toggle
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolPill(
                icon = Icons.Default.NearMe,
                label = "Move",
                selected = activeTool == WorkbenchTool.SELECT,
                onClick = { viewModel.setTool(WorkbenchTool.SELECT) },
                tag = "tool_select"
            )
            ToolPill(
                icon = Icons.Default.Cable,
                label = "Wire",
                selected = activeTool == WorkbenchTool.WIRE,
                onClick = { viewModel.setTool(WorkbenchTool.WIRE) },
                tag = "tool_wire"
            )
            ToolPill(
                icon = Icons.Default.Sensors,
                label = "Probe",
                selected = activeTool == WorkbenchTool.PROBE,
                onClick = { viewModel.setTool(WorkbenchTool.PROBE) },
                tag = "tool_probe"
            )
            ToolPill(
                icon = Icons.Default.ShowChart,
                label = "Scope",
                selected = isScopeVisible,
                onClick = { viewModel.toggleScopeVisible() },
                tag = "tool_scope"
            )
        }

        // Top-Right Simulation Time & Telemetry Pill
        Surface(
            color = LabSurface.copy(alpha = 0.88f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .border(1.dp, LabBorder, RoundedCornerShape(8.dp))
        ) {
            Text(
                text = "t = ${UnitFormatter.formatCompact(snapshot.simTimeSeconds, "s")}",
                style = MaterialTheme.typography.labelSmall,
                color = LabCyan,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }

        // Ground or Convergence Warning Banner
        val warningMsg = snapshot.convergenceWarning ?: if (!snapshot.hasExplicitGround) {
            "Auto-GND reference active (place GND for 0V node)"
        } else null

        if (warningMsg != null) {
            Surface(
                color = LabAmber.copy(alpha = 0.16f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 52.dp, start = 12.dp, end = 12.dp)
                    .border(1.dp, LabAmber.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = warningMsg,
                    style = MaterialTheme.typography.labelSmall,
                    color = LabAmber,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // Live Probe Readout Card
        probeReadout?.let { readout ->
            Surface(
                color = LabSurface.copy(alpha = 0.94f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .border(1.dp, LabCyan, RoundedCornerShape(12.dp))
                    .testTag("probe_readout_card")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column {
                        Text(readout.title, style = MaterialTheme.typography.labelMedium, color = LabCyan, fontWeight = FontWeight.Bold)
                        Text(readout.voltageText, style = MaterialTheme.typography.labelLarge, color = LabEmerald)
                        readout.currentText?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = LabAmber)
                        }
                        readout.powerText?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        readout.extraText?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { viewModel.dismissProbeReadout() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss Probe", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Contextual Selection Action Pill (Rotate / Edit / Delete)
        if (selectedComponent != null || selectedWireId != null) {
            Surface(
                color = LabSurface.copy(alpha = 0.94f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .border(1.dp, LabCyan.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .testTag("selection_action_bar")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (selectedComponent != null) {
                        Text(
                            text = "${selectedComponent.label}: ${selectedComponent.summaryValueText()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = LabCyan,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        IconButton(
                            onClick = { viewModel.rotateSelectedComponent() },
                            modifier = Modifier.size(36.dp).testTag("quick_rotate_button")
                        ) {
                            Icon(Icons.Default.RotateRight, contentDescription = "Rotate", tint = LabCyan, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = onOpenInspector,
                            modifier = Modifier.size(36.dp).testTag("quick_edit_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Value", tint = LabAmber, modifier = Modifier.size(18.dp))
                        }
                    } else {
                        Text(
                            text = "Wire selected",
                            style = MaterialTheme.typography.labelSmall,
                            color = LabCyan,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.deleteSelected() },
                        modifier = Modifier.size(36.dp).testTag("quick_delete_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = LabRose, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolPill(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        color = if (selected) LabCyan.copy(alpha = 0.22f) else LabSurfaceElevated.copy(alpha = 0.9f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .border(1.dp, if (selected) LabCyan else LabBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) LabCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) LabCyan else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
