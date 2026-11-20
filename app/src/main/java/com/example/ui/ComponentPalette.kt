package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ComponentCategory
import com.example.model.ComponentType
import com.example.ui.theme.LabBorder
import com.example.ui.theme.LabCyan
import com.example.ui.theme.LabSurface
import com.example.ui.theme.LabSurfaceElevated
import com.example.ui.theme.LocalVoltLabColors

@Composable
fun ComponentPalette(
    onTapComponent: (ComponentType) -> Unit,
    onStartDrag: (ComponentType, Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(ComponentCategory.PASSIVES) }
    val componentsInCategory = remember(selectedCategory) {
        ComponentType.entries.filter { it.category == selectedCategory }
    }
    val labColors = LocalVoltLabColors.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .testTag("component_palette"),
        color = LabSurface,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, LabBorder)
                .padding(top = 6.dp, bottom = 8.dp)
        ) {
            // Category Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ComponentCategory.entries.forEach { cat ->
                    val active = cat == selectedCategory
                    Surface(
                        color = if (active) LabCyan.copy(alpha = 0.18f) else LabSurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = if (active) LabCyan else LabBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedCategory = cat }
                            .testTag("palette_cat_${cat.name.lowercase()}")
                    ) {
                        Text(
                            text = cat.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) LabCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Draggable & Tappable Component Cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                componentsInCategory.forEach { compType ->
                    var itemRootPos by remember { mutableStateOf(Offset.Zero) }
                    Surface(
                        color = LabSurfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .width(76.dp)
                            .border(1.dp, LabBorder, RoundedCornerShape(10.dp))
                            .onGloballyPositioned { coords ->
                                itemRootPos = coords.positionInRoot()
                            }
                            .clickable { onTapComponent(compType) }
                            .pointerInput(compType) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { localOffset ->
                                        onStartDrag(compType, itemRootPos + localOffset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        onDragMove(itemRootPos + change.position)
                                    },
                                    onDragEnd = { onDragEnd() },
                                    onDragCancel = { onDragEnd() }
                                )
                            }
                            .testTag("palette_item_${compType.name.lowercase()}")
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Canvas(modifier = Modifier.size(34.dp)) {
                                ComponentSymbolRenderer.drawPaletteIcon(
                                    scope = this,
                                    type = compType,
                                    color = labColors.componentStroke,
                                    accent = LabCyan
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = compType.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
