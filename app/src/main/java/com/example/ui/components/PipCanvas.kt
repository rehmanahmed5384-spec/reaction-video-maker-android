package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.FitMode
import com.example.core.model.Layer
import com.example.core.model.LayerType
import com.example.core.model.NormalizedRect
import com.example.ui.theme.*
import kotlin.math.roundToInt

enum class HandlePosition {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}

@Composable
fun PipLayerWrapper(
    layer: Layer,
    isSelected: Boolean,
    canvasWidthDp: androidx.compose.ui.unit.Dp,
    canvasHeightDp: androidx.compose.ui.unit.Dp,
    onSelect: () -> Unit,
    onTransformChange: (NormalizedRect) -> Unit,
    onDelete: (() -> Unit)? = null,
    onTogglePlay: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    if (!layer.visible) return

    val density = androidx.compose.ui.platform.LocalDensity.current
    val canvasWidthPx = with(density) { canvasWidthDp.toPx() }
    val canvasHeightPx = with(density) { canvasHeightDp.toPx() }

    val rect = layer.rect
    val layerWidthDp = (canvasWidthDp * rect.width).coerceAtLeast(32.dp)
    val layerHeightDp = (canvasHeightDp * rect.height).coerceAtLeast(32.dp)
    val offsetXDp = canvasWidthDp * rect.x
    val offsetYDp = canvasHeightDp * rect.y

    Box(
        modifier = Modifier
            .offset(x = offsetXDp, y = offsetYDp)
            .size(width = layerWidthDp, height = layerHeightDp)
            .rotate(layer.rotation)
            .pointerInput(layer.id) {
                detectTapGestures {
                    onSelect()
                }
            }
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, StudioPrimary, RoundedCornerShape(6.dp))
                } else {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                }
            )
    ) {
        // Layer media or text content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
        ) {
            content()
        }

        // Floating quick actions badge when selected
        if (isSelected) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (onTogglePlay != null) {
                    IconButton(
                        onClick = onTogglePlay,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            if (layer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause Layer",
                            tint = StudioPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Delete Layer",
                            tint = StudioError,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // 8 Interactive PiP handles when selected
        if (isSelected && !layer.locked) {
            PipEightHandles(
                onHandleDrag = { handle, dragDeltaX, dragDeltaY ->
                    val normDx = dragDeltaX / canvasWidthPx.coerceAtLeast(1f)
                    val normDy = dragDeltaY / canvasHeightPx.coerceAtLeast(1f)
                    val newRect = calculateResizedRect(rect, handle, normDx, normDy)
                    onTransformChange(newRect.clamped())
                }
            )

            // Center drag handle to move layer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(layer.id + "_drag") {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val normDx = dragAmount.x / canvasWidthPx.coerceAtLeast(1f)
                            val normDy = dragAmount.y / canvasHeightPx.coerceAtLeast(1f)
                            val moved = rect.copy(
                                x = (rect.x + normDx).coerceIn(0f, (1f - rect.width).coerceAtLeast(0f)),
                                y = (rect.y + normDy).coerceIn(0f, (1f - rect.height).coerceAtLeast(0f))
                            )
                            onTransformChange(moved)
                        }
                    }
            )
        }
    }
}

@Composable
fun BoxScope.PipEightHandles(
    onHandleDrag: (HandlePosition, Float, Float) -> Unit
) {
    val handles = listOf(
        HandlePosition.TOP_LEFT to Alignment.TopStart,
        HandlePosition.TOP_CENTER to Alignment.TopCenter,
        HandlePosition.TOP_RIGHT to Alignment.TopEnd,
        HandlePosition.CENTER_LEFT to Alignment.CenterStart,
        HandlePosition.CENTER_RIGHT to Alignment.CenterEnd,
        HandlePosition.BOTTOM_LEFT to Alignment.BottomStart,
        HandlePosition.BOTTOM_CENTER to Alignment.BottomCenter,
        HandlePosition.BOTTOM_RIGHT to Alignment.BottomEnd
    )

    handles.forEach { (position, alignment) ->
        Box(
            modifier = Modifier
                .align(alignment)
                // Touch target is minimum 48dp per accessibility guidelines
                .size(48.dp)
                .pointerInput(position) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onHandleDrag(position, dragAmount.x, dragAmount.y)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Visible handle is clean and sharp (12dp)
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(StudioPrimary, CircleShape)
                    .border(1.5.dp, Color.White, CircleShape)
            )
        }
    }
}

private fun calculateResizedRect(
    current: NormalizedRect,
    handle: HandlePosition,
    dx: Float,
    dy: Float
): NormalizedRect {
    var x = current.x
    var y = current.y
    var w = current.width
    var h = current.height

    when (handle) {
        HandlePosition.TOP_LEFT -> {
            x += dx
            y += dy
            w -= dx
            h -= dy
        }
        HandlePosition.TOP_CENTER -> {
            y += dy
            h -= dy
        }
        HandlePosition.TOP_RIGHT -> {
            y += dy
            w += dx
            h -= dy
        }
        HandlePosition.CENTER_LEFT -> {
            x += dx
            w -= dx
        }
        HandlePosition.CENTER_RIGHT -> {
            w += dx
        }
        HandlePosition.BOTTOM_LEFT -> {
            x += dx
            w -= dx
            h += dy
        }
        HandlePosition.BOTTOM_CENTER -> {
            h += dy
        }
        HandlePosition.BOTTOM_RIGHT -> {
            w += dx
            h += dy
        }
    }

    // Snap to horizontal & vertical center or canvas edges
    val snapThreshold = 0.02f
    if (kotlin.math.abs(x) < snapThreshold) x = 0f
    if (kotlin.math.abs(y) < snapThreshold) y = 0f
    if (kotlin.math.abs(x + w - 1f) < snapThreshold) w = 1f - x
    if (kotlin.math.abs(y + h - 1f) < snapThreshold) h = 1f - y

    return NormalizedRect(x, y, w, h)
}
