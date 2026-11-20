package com.example.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.CircuitComponent
import com.example.model.ComponentType
import com.example.model.MultimeterMode
import com.example.model.UnitFormatter
import com.example.model.WaveformType
import com.example.simulation.MeterReading
import com.example.ui.theme.LabAmber
import com.example.ui.theme.LabBorder
import com.example.ui.theme.LabCyan
import com.example.ui.theme.LabEmerald
import com.example.ui.theme.LabRose
import com.example.ui.theme.LabSurface
import com.example.ui.theme.LabSurfaceElevated

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentInspectorSheet(
    component: CircuitComponent,
    allComponents: List<CircuitComponent>,
    branchCurrent: Double?,
    powerWatts: Double?,
    meterReading: MeterReading?,
    onUpdate: (CircuitComponent) -> Unit,
    onRotate: () -> Unit,
    onFlip: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var labelText by remember(component.id) { mutableStateOf(component.label) }
    var primaryText by remember(component.id) {
        mutableStateOf(UnitFormatter.formatCompact(component.primaryValue, ""))
    }
    var secondaryText by remember(component.id) {
        mutableStateOf(UnitFormatter.formatCompact(component.secondaryValue, ""))
    }
    var tertiaryText by remember(component.id) {
        mutableStateOf(UnitFormatter.formatCompact(component.tertiaryValue, ""))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LabSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header + Transform Icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = component.type.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = LabCyan
                    )
                    Text(
                        text = "${component.label} • Pos (${component.x}, ${component.y}) • ${component.rotation}°",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = onRotate, modifier = Modifier.testTag("inspector_rotate_btn")) {
                        Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90°", tint = LabCyan)
                    }
                    IconButton(onClick = onFlip, modifier = Modifier.testTag("inspector_flip_btn")) {
                        Icon(Icons.Default.Flip, contentDescription = "Flip", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = onDuplicate, modifier = Modifier.testTag("inspector_duplicate_btn")) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.testTag("inspector_delete_btn")) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = LabRose)
                    }
                }
            }

            // Live Telemetry Banner
            if (branchCurrent != null || meterReading != null) {
                Surface(
                    color = LabSurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, LabBorder, RoundedCornerShape(10.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (meterReading != null) {
                            Column {
                                Text("LIVE METER", style = MaterialTheme.typography.labelSmall, color = LabEmerald)
                                Text(
                                    text = UnitFormatter.format(meterReading.primaryDisplay, meterReading.unit, 3),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = LabEmerald,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "RMS: ${UnitFormatter.format(meterReading.rmsValue, meterReading.unit, 3)}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = "Vpp: ${UnitFormatter.format(meterReading.peakToPeak, meterReading.unit, 3)}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        } else {
                            Text(
                                text = "I = ${UnitFormatter.format(branchCurrent ?: 0.0, "A", 3)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = LabAmber
                            )
                            Text(
                                text = "P = ${UnitFormatter.format(powerWatts ?: 0.0, "W", 3)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = LabCyan
                            )
                        }
                    }
                }
            }

            // Label field
            OutlinedTextField(
                value = labelText,
                onValueChange = {
                    labelText = it
                    onUpdate(component.copy(label = it))
                },
                label = { Text("Component Reference Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("inspector_label_input")
            )

            // Type-specific parameter editors
            when (component.type) {
                ComponentType.GROUND -> {
                    Text(
                        text = "0 V Global Reference Node",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ComponentType.SCOPE_PROBE -> {
                    Text("Oscilloscope Channel", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..4).forEach { ch ->
                            val chColor = ComponentSymbolRenderer.scopeChannelColor(ch)
                            FilterChip(
                                selected = component.probeChannel == ch,
                                onClick = {
                                    onUpdate(component.copy(probeChannel = ch, label = "CH$ch"))
                                },
                                label = { Text("CH$ch", color = chColor, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                }

                ComponentType.MULTIMETER -> {
                    Text("Multimeter Mode", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MultimeterMode.entries.forEach { mode ->
                            FilterChip(
                                selected = component.multimeterMode == mode,
                                onClick = { onUpdate(component.copy(multimeterMode = mode)) },
                                label = { Text(mode.shortLabel) }
                            )
                        }
                    }
                }

                ComponentType.VOLTMETER,
                ComponentType.AMMETER,
                ComponentType.OHMMETER,
                ComponentType.WATTMETER -> {
                    SiValueEditor(
                        title = "${component.type.primaryLabel} (${component.type.primaryUnit})",
                        text = primaryText,
                        unit = component.type.primaryUnit,
                        onTextChange = { primaryText = it },
                        onApplyNumeric = { v -> onUpdate(component.copy(primaryValue = v)) }
                    )
                }

                else -> {
                    // Primary Value with SI prefix chips
                    SiValueEditor(
                        title = "${component.type.primaryLabel} (${component.type.primaryUnit})",
                        text = primaryText,
                        unit = component.type.primaryUnit,
                        onTextChange = { primaryText = it },
                        onApplyNumeric = { v -> onUpdate(component.copy(primaryValue = v)) }
                    )

                    // Waveform & Frequency for AC Sources
                    if (component.type == ComponentType.AC_VOLTAGE_SOURCE ||
                        component.type == ComponentType.AC_CURRENT_SOURCE
                    ) {
                        Text("Waveform", style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            WaveformType.entries.forEach { wf ->
                                FilterChip(
                                    selected = component.waveform == wf,
                                    onClick = { onUpdate(component.copy(waveform = wf)) },
                                    label = { Text(wf.displayName) }
                                )
                            }
                        }

                        SiValueEditor(
                            title = "Frequency (Hz)",
                            text = secondaryText,
                            unit = "Hz",
                            onTextChange = { secondaryText = it },
                            onApplyNumeric = { v -> onUpdate(component.copy(secondaryValue = v)) }
                        )

                        SiValueEditor(
                            title = "DC Offset (${component.type.primaryUnit})",
                            text = tertiaryText,
                            unit = component.type.primaryUnit,
                            onTextChange = { tertiaryText = it },
                            onApplyNumeric = { v -> onUpdate(component.copy(tertiaryValue = v)) }
                        )
                    }

                    // Diode Zener option
                    if (component.type == ComponentType.DIODE) {
                        SiValueEditor(
                            title = "Zener Breakdown Vz (0 = Standard Diode)",
                            text = tertiaryText,
                            unit = "V",
                            onTextChange = { tertiaryText = it },
                            onApplyNumeric = { v -> onUpdate(component.copy(tertiaryValue = v)) }
                        )
                    }

                    // MOSFET Threshold
                    if (component.type == ComponentType.NMOS_FET || component.type == ComponentType.PMOS_FET) {
                        SiValueEditor(
                            title = "Threshold Voltage Vth (V)",
                            text = secondaryText,
                            unit = "V",
                            onTextChange = { secondaryText = it },
                            onApplyNumeric = { v -> onUpdate(component.copy(secondaryValue = v)) }
                        )
                    }

                    // Op-Amp Supply Rails
                    if (component.type == ComponentType.OP_AMP) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = secondaryText,
                                onValueChange = {
                                    secondaryText = it
                                    UnitFormatter.parse(it)?.let { v -> onUpdate(component.copy(secondaryValue = v)) }
                                },
                                label = { Text("+Vcc (V)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = tertiaryText,
                                onValueChange = {
                                    tertiaryText = it
                                    UnitFormatter.parse(it)?.let { v -> onUpdate(component.copy(tertiaryValue = v)) }
                                },
                                label = { Text("-Vee (V)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // CCVS / CCCS Controlled Branch Selector (optional override of S+/S- loop)
                    if (component.type == ComponentType.CCVS || component.type == ComponentType.CCCS) {
                        val branchSources = allComponents.filter {
                            it.id != component.id && (
                                it.type == ComponentType.DC_VOLTAGE_SOURCE ||
                                    it.type == ComponentType.AC_VOLTAGE_SOURCE ||
                                    it.type == ComponentType.AMMETER ||
                                    it.type == ComponentType.INDUCTOR
                                )
                        }
                        Text("Sense Current Source (Optional; or wire S+/S-)", style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = component.controlComponentId == null,
                                onClick = { onUpdate(component.copy(controlComponentId = null)) },
                                label = { Text("S+/S- Pins") }
                            )
                            branchSources.forEach { src ->
                                FilterChip(
                                    selected = component.controlComponentId == src.id,
                                    onClick = { onUpdate(component.copy(controlComponentId = src.id)) },
                                    label = { Text(src.label) }
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = LabCyan),
                modifier = Modifier.fillMaxWidth().testTag("inspector_done_btn")
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SiValueEditor(
    title: String,
    text: String,
    unit: String,
    onTextChange: (String) -> Unit,
    onApplyNumeric: (Double) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                onTextChange(raw)
                UnitFormatter.parse(raw)?.let { onApplyNumeric(it) }
            },
            label = { Text(title) },
            placeholder = { Text("e.g. 4.7k, 100u, 10m") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("inspector_value_input")
        )
        // Quick SI multiplier chips
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val baseNum = text.trim()
                .trimEnd('p', 'P', 'n', 'N', 'u', 'U', 'μ', 'm', 'k', 'K', 'M', 'g', 'G')
                .toDoubleOrNull() ?: 1.0
            UnitFormatter.prefixes.forEach { p ->
                Surface(
                    color = LabSurfaceElevated,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .border(1.dp, LabBorder, RoundedCornerShape(6.dp))
                        .clickable {
                            val newVal = baseNum * p.multiplier
                            val formatted = UnitFormatter.formatCompact(newVal, "")
                            onTextChange(formatted)
                            onApplyNumeric(newVal)
                        }
                ) {
                    Text(
                        text = if (p.symbol.isEmpty()) "1×$unit" else "${p.symbol}$unit",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}
