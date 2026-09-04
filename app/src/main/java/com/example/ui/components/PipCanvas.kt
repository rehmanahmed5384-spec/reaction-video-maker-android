package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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

    // Keep latest values in refs to avoid closure stale state during fast gestures
    val currentRect by rememberUpdatedState(rect)
    val currentOnTransformChange by rememberUpdatedState(onTransformChange)
    val currentOnSelect by rememberUpdatedState(onSelect)

    Box(
        modifier = Modifier
            .offset(x = offsetXDp, y = offsetYDp)
            .size(width = layerWidthDp, height = layerHeightDp)
            .rotate(layer.rotation)
            .pointerInput(layer.id) {
                detectTapGestures {
                    currentOnSelect()
                }
            }
            .then(
                if (isSelected && !layer.locked) {
                    // Support 1-finger drag and 2-finger pinch-to-zoom/stretch across the layer body
                    Modifier.pointerInput(layer.id + "_transform") {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val normDx = pan.x / canvasWidthPx.coerceAtLeast(1f)
                            val normDy = pan.y / canvasHeightPx.coerceAtLeast(1f)

                            val r = currentRect
                            if (zoom != 1f) {
                                // Pinch to stretch/scale proportionally from centroid
                                val newW = (r.width * zoom).coerceIn(0.08f, 1.0f)
                                val newH = (r.height * zoom).coerceIn(0.08f, 1.0f)
                                val deltaW = newW - r.width
                                val deltaH = newH - r.height
                                val newX = (r.x - deltaW / 2f + normDx).coerceIn(0f, (1f - newW).coerceAtLeast(0f))
                                val newY = (r.y - deltaH / 2f + normDy).coerceIn(0f, (1f - newH).coerceAtLeast(0f))
                                currentOnTransformChange(NormalizedRect(newX, newY, newW, newH))
                            } else {
                                // 1-finger smooth pan/drag
                                val newX = (r.x + normDx).coerceIn(0f, (1f - r.width).coerceAtLeast(0f))
                                val newY = (r.y + normDy).coerceIn(0f, (1f - r.height).coerceAtLeast(0f))
                                currentOnTransformChange(r.copy(x = newX, y = newY))
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
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

        // 8 Interactive PiP resize & stretch handles when selected
        if (isSelected && !layer.locked) {
            PipEightHandles(
                onHandleDrag = { handle, dragDeltaX, dragDeltaY ->
                    val normDx = dragDeltaX / canvasWidthPx.coerceAtLeast(1f)
                    val normDy = dragDeltaY / canvasHeightPx.coerceAtLeast(1f)
                    val newRect = calculateResizedRect(currentRect, handle, normDx, normDy)
                    currentOnTransformChange(newRect.clamped())
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

    val currentOnHandleDrag by rememberUpdatedState(onHandleDrag)

    handles.forEach { (position, alignment) ->
        Box(
            modifier = Modifier
                .align(alignment)
                // Touch target is minimum 48dp per accessibility guidelines
                .size(48.dp)
                .pointerInput(position) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        currentOnHandleDrag(position, dragAmount.x, dragAmount.y)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Visible handle is clean, high-contrast, and tactile
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(StudioPrimary, CircleShape)
                    .border(2.dp, Color.White, CircleShape)
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

    val minSize = 0.08f

    when (handle) {
        HandlePosition.TOP_LEFT -> {
            val newW = (w - dx).coerceAtLeast(minSize)
            val newH = (h - dy).coerceAtLeast(minSize)
            x += (w - newW)
            y += (h - newH)
            w = newW
            h = newH
        }
        HandlePosition.TOP_CENTER -> {
            val newH = (h - dy).coerceAtLeast(minSize)
            y += (h - newH)
            h = newH
        }
        HandlePosition.TOP_RIGHT -> {
            val newW = (w + dx).coerceAtLeast(minSize)
            val newH = (h - dy).coerceAtLeast(minSize)
            y += (h - newH)
            w = newW
            h = newH
        }
        HandlePosition.CENTER_LEFT -> {
            val newW = (w - dx).coerceAtLeast(minSize)
            x += (w - newW)
            w = newW
        }
        HandlePosition.CENTER_RIGHT -> {
            w = (w + dx).coerceAtLeast(minSize)
        }
        HandlePosition.BOTTOM_LEFT -> {
            val newW = (w - dx).coerceAtLeast(minSize)
            val newH = (h + dy).coerceAtLeast(minSize)
            x += (w - newW)
            w = newW
            h = newH
        }
        HandlePosition.BOTTOM_CENTER -> {
            h = (h + dy).coerceAtLeast(minSize)
        }
        HandlePosition.BOTTOM_RIGHT -> {
            w = (w + dx).coerceAtLeast(minSize)
            h = (h + dy).coerceAtLeast(minSize)
        }
    }

    // Keep bounds constrained to 0..1
    x = x.coerceIn(0f, 1f - minSize)
    y = y.coerceIn(0f, 1f - minSize)
    w = w.coerceIn(minSize, 1f - x)
    h = h.coerceIn(minSize, 1f - y)

    // Snap to edges if close enough
    val snapThreshold = 0.02f
    if (x < snapThreshold) {
        w += x
        x = 0f
    }
    if (y < snapThreshold) {
        h += y
        y = 0f
    }
    if (1f - (x + w) < snapThreshold) {
        w = 1f - x
    }
    if (1f - (y + h) < snapThreshold) {
        h = 1f - y
    }

    return NormalizedRect(x, y, w, h)
}
