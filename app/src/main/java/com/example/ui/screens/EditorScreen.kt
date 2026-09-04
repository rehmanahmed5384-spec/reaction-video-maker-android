package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.core.data.ProjectRepository
import com.example.core.hardware.CameraHelper
import com.example.core.media.ExportEngine
import com.example.core.media.ExportState
import com.example.core.model.*
import com.example.ui.components.PipLayerWrapper
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: String,
    repository: ProjectRepository,
    onNavigateBack: () -> Unit,
    onNavigateDiagnostics: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val activity = context as? Activity

    var project by remember { mutableStateOf<ProjectDocument?>(null) }
    var selectedLayerId by remember { mutableStateOf<String?>(null) }
    var isMasterPlaying by remember { mutableStateOf(false) }
    var currentPlayheadMs by remember { mutableLongStateOf(0L) }

    // History for Undo / Redo
    val undoStack = remember { mutableStateListOf<List<Layer>>() }
    val redoStack = remember { mutableStateListOf<List<Layer>>() }

    // Camera Helper & Torch
    val cameraHelper = remember { CameraHelper(context) }
    var isTorchActive by remember { mutableStateOf(false) }
    var isScreenLightActive by remember { mutableStateOf(false) }
    var cameraFacing by remember { mutableStateOf(CameraFacing.FRONT) }
    var activePreviewView by remember { mutableStateOf<PreviewView?>(null) }

    // Export Engine & Modal State
    val exportEngine = remember { ExportEngine(context, repository) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportState by remember { mutableStateOf<ExportState>(ExportState.Idle) }

    var activeBottomPanel by remember { mutableStateOf<String?>(null) } // null or layers, transform, audio, presets
    var isFitScreen by remember { mutableStateOf(true) } // Fit aspect ratio or Full-bleed fill
    var isUiOverlayVisible by remember { mutableStateOf(true) }

    // Load Project
    LaunchedEffect(projectId) {
        val loaded = repository.loadProject(projectId)
        if (loaded != null) {
            project = loaded
            selectedLayerId = loaded.layers.firstOrNull()?.id
            // Apply initial orientation
            applyOrientation(activity, loaded.canvas.aspectRatio)
        }
    }

    // Auto-save debounced
    LaunchedEffect(project) {
        val p = project ?: return@LaunchedEffect
        delay(800)
        repository.saveProject(p)
    }

    // Master playhead timer
    LaunchedEffect(isMasterPlaying) {
        while (isMasterPlaying) {
            delay(100)
            val p = project ?: break
            currentPlayheadMs = (currentPlayheadMs + 100L) % (p.durationMs.coerceAtLeast(1000L))
        }
    }

    // Initialize CameraX
    DisposableEffect(Unit) {
        cameraHelper.initialize {
            activePreviewView?.let { pv ->
                cameraHelper.startCamera(lifecycleOwner, pv, cameraFacing)
            }
        }
        onDispose {
            cameraHelper.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val currentProject = project
    if (currentProject == null) {
        Box(Modifier.fillMaxSize().background(StudioBackground), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = StudioPrimary)
        }
        return
    }

    fun pushUndo() {
        undoStack.add(currentProject.layers)
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val prev = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(currentProject.layers)
            project = currentProject.copy(layers = prev)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(currentProject.layers)
            project = currentProject.copy(layers = next)
        }
    }

    // Full-screen canvas container
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Front Screen-Light software illumination halo border
        if (isScreenLightActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(8.dp, Color.White)
            )
        }

        // Adaptive Full-Screen Canvas Area
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val screenWidth = maxWidth
            val screenHeight = maxHeight
            val ratio = currentProject.canvas.aspectRatio.ratio

            // Calculate maximized canvas dimensions: fills screen edge-to-edge
            val (canvasWidthDp, canvasHeightDp) = remember(screenWidth, screenHeight, ratio, isFitScreen) {
                if (!isFitScreen) {
                    screenWidth to screenHeight
                } else {
                    val screenRatio = screenWidth.value / screenHeight.value.coerceAtLeast(1f)
                    if (screenRatio > ratio) {
                        (screenHeight * ratio) to screenHeight
                    } else {
                        screenWidth to (screenWidth / ratio)
                    }
                }
            }

            // The Full Canvas Viewport
            Box(
                modifier = Modifier
                    .size(width = canvasWidthDp, height = canvasHeightDp)
                    .clip(RoundedCornerShape(if (isFitScreen) 8.dp else 0.dp))
                    .background(Color(currentProject.canvas.background.primaryColor))
                    .pointerInput(Unit) {
                        detectTapGestures {
                            // Tap on empty canvas background deselects layer or toggles UI
                            selectedLayerId = null
                        }
                    }
            ) {
                // Render layers sorted by zIndex
                val sortedLayers = currentProject.layers.sortedBy { it.zIndex }
                sortedLayers.forEach { layer ->
                    PipLayerWrapper(
                        layer = layer,
                        isSelected = layer.id == selectedLayerId,
                        canvasWidthDp = canvasWidthDp,
                        canvasHeightDp = canvasHeightDp,
                        onSelect = { selectedLayerId = layer.id },
                        onTransformChange = { newRect ->
                            project = currentProject.copy(
                                layers = currentProject.layers.map {
                                    if (it.id == layer.id) it.copy(rect = newRect) else it
                                }
                            )
                        },
                        onDelete = {
                            pushUndo()
                            project = currentProject.copy(
                                layers = currentProject.layers.filter { it.id != layer.id }
                            )
                            if (selectedLayerId == layer.id) {
                                selectedLayerId = project?.layers?.firstOrNull()?.id
                            }
                        },
                        onTogglePlay = {
                            pushUndo()
                            project = currentProject.copy(
                                layers = currentProject.layers.map {
                                    if (it.id == layer.id) it.copy(isPlaying = !it.isPlaying) else it
                                }
                            )
                        }
                    ) {
                        LayerContentRenderer(
                            layer = layer,
                            isMasterPlaying = isMasterPlaying,
                            onPreviewViewCreated = { pv ->
                                activePreviewView = pv
                                cameraHelper.startCamera(lifecycleOwner, pv, cameraFacing)
                            },
                            onFlipCamera = {
                                activePreviewView?.let { pv ->
                                    cameraHelper.switchCamera(lifecycleOwner, pv) { newFacing ->
                                        cameraFacing = newFacing
                                        isTorchActive = false
                                        project = currentProject.copy(
                                            layers = currentProject.layers.map {
                                                if (it.id == layer.id) it.copy(cameraFacing = newFacing) else it
                                            }
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // OVERLAY CONTROLS INSIDE THE CANVAS
        AnimatedVisibility(
            visible = isUiOverlayVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Floating Overlay Bar inside Canvas
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.size(36.dp).testTag("editor_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }

                        // Project Name & Ratio Chip
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = StudioSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text(
                                    currentProject.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    maxLines = 1
                                )
                                Text(
                                    "${currentProject.canvas.aspectRatio.displayName} â€¢ ${currentProject.layers.size}L",
                                    fontSize = 9.sp,
                                    color = StudioOnSurfaceVariant
                                )
                            }
                        }

                        // Aspect Ratio Switcher Button (Cycles 16:9, 9:16, 1:1)
                        IconButton(
                            onClick = {
                                pushUndo()
                                val nextAspect = when (currentProject.canvas.aspectRatio) {
                                    AspectRatio.LANDSCAPE_16_9 -> AspectRatio.PORTRAIT_9_16
                                    AspectRatio.PORTRAIT_9_16 -> AspectRatio.SQUARE_1_1
                                    AspectRatio.SQUARE_1_1 -> AspectRatio.LANDSCAPE_16_9
                                }
                                applyOrientation(activity, nextAspect)
                                val (w, h) = nextAspect.defaultWidth to nextAspect.defaultHeight
                                project = currentProject.copy(
                                    canvas = currentProject.canvas.copy(
                                        aspectRatio = nextAspect,
                                        width = w,
                                        height = h
                                    )
                                )
                            },
                            modifier = Modifier.size(36.dp).testTag("aspect_switch_button")
                        ) {
                            Icon(
                                Icons.Default.AspectRatio,
                                contentDescription = "Aspect Ratio",
                                tint = StudioPrimary
                            )
                        }

                        // Fullscreen Fit / Fill Toggle
                        IconButton(
                            onClick = { isFitScreen = !isFitScreen },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                if (isFitScreen) Icons.Default.Fullscreen else Icons.Default.FullscreenExit,
                                contentDescription = if (isFitScreen) "Fill Screen" else "Fit Screen",
                                tint = if (!isFitScreen) StudioSecondary else Color.White
                            )
                        }

                        // Torch / Flash button
                        IconButton(
                            onClick = {
                                if (cameraFacing == CameraFacing.FRONT && !cameraHelper.hasHardwareTorch()) {
                                    isScreenLightActive = !isScreenLightActive
                                    Toast.makeText(
                                        context,
                                        if (isScreenLightActive) "Screen Light Enabled" else "Screen Light Off",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    val target = !isTorchActive
                                    cameraHelper.toggleTorch(target) { success ->
                                        isTorchActive = success
                                    }
                                }
                            },
                            modifier = Modifier.size(36.dp).testTag("torch_toggle_button")
                        ) {
                            Icon(
                                if (isTorchActive || isScreenLightActive) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Flash/Torch",
                                tint = if (isTorchActive) StudioSecondary else if (isScreenLightActive) Color.White else StudioOnSurfaceVariant
                            )
                        }

                        // Camera Switch
                        IconButton(
                            onClick = {
                                activePreviewView?.let { pv ->
                                    cameraHelper.switchCamera(lifecycleOwner, pv) { newFacing ->
                                        cameraFacing = newFacing
                                        isTorchActive = false
                                    }
                                }
                            },
                            modifier = Modifier.size(36.dp).testTag("camera_switch_button")
                        ) {
                            Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Flip Camera", tint = Color.White)
                        }

                        // Undo / Redo
                        IconButton(
                            onClick = { undo() },
                            enabled = undoStack.isNotEmpty(),
                            modifier = Modifier.size(36.dp).testTag("undo_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo",
                                tint = if (undoStack.isNotEmpty()) Color.White else Color.Gray
                            )
                        }
                        IconButton(
                            onClick = { redo() },
                            enabled = redoStack.isNotEmpty(),
                            modifier = Modifier.size(36.dp).testTag("redo_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Redo",
                                tint = if (redoStack.isNotEmpty()) Color.White else Color.Gray
                            )
                        }

                        // Diagnostics
                        IconButton(
                            onClick = onNavigateDiagnostics,
                            modifier = Modifier.size(36.dp).testTag("diagnostics_button")
                        ) {
                            Icon(Icons.Default.BugReport, contentDescription = "Diagnostics", tint = Color.White)
                        }

                        // Export Button inside top overlay
                        Button(
                            onClick = { showExportDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = StudioPrimary),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.height(32.dp).testTag("open_export_button")
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, tint = StudioOnPrimary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Export", color = StudioOnPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                // Bottom Floating Controls Overlay inside Canvas
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Floating Deck Panel if active (Layers, Presets, Audio, Transform)
                    if (activeBottomPanel != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xF0131B2A),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StudioOutline.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth().height(160.dp)
                        ) {
                            Column(Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        when (activeBottomPanel) {
                                            "layers" -> "Layer Manager"
                                            "presets" -> "PiP Preset Layouts"
                                            "audio" -> "Audio & Playback Controls"
                                            "transform" -> "Transform & Alignment"
                                            else -> ""
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = StudioPrimary
                                    )
                                    IconButton(
                                        onClick = { activeBottomPanel = null },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Close Panel", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }

                                Box(Modifier.weight(1f)) {
                                    val selectedLayer = currentProject.layers.find { it.id == selectedLayerId }
                                    when (activeBottomPanel) {
                                        "layers" -> LayersTabContent(
                                            layers = currentProject.layers,
                                            selectedId = selectedLayerId,
                                            onSelect = { selectedLayerId = it },
                                            onAdd = { type ->
                                                pushUndo()
                                                val newLayer = Layer(
                                                    name = "New ${type.name.lowercase().replaceFirstChar { it.uppercase() }}",
                                                    type = type,
                                                    cameraFacing = cameraFacing,
                                                    rect = NormalizedRect(0.1f, 0.1f, 0.45f, 0.35f),
                                                    zIndex = currentProject.layers.size
                                                )
                                                project = currentProject.copy(layers = currentProject.layers + newLayer)
                                                selectedLayerId = newLayer.id
                                            },
                                            onAddFullCamera = {
                                                pushUndo()
                                                val newLayer = Layer(
                                                    name = "Full Canvas Camera",
                                                    type = LayerType.CAMERA,
                                                    cameraFacing = cameraFacing,
                                                    rect = NormalizedRect(0f, 0f, 1f, 1f),
                                                    zIndex = 0
                                                )
                                                val shifted = currentProject.layers.map { it.copy(zIndex = it.zIndex + 1) }
                                                project = currentProject.copy(layers = listOf(newLayer) + shifted)
                                                selectedLayerId = newLayer.id
                                            },
                                            onDelete = { id ->
                                                pushUndo()
                                                project = currentProject.copy(layers = currentProject.layers.filter { it.id != id })
                                                if (selectedLayerId == id) selectedLayerId = project?.layers?.firstOrNull()?.id
                                            },
                                            onDuplicate = { layer ->
                                                pushUndo()
                                                val dup = layer.copy(
                                                    id = UUID.randomUUID().toString(),
                                                    name = "${layer.name} (Copy)",
                                                    rect = layer.rect.copy(x = (layer.rect.x + 0.05f).coerceAtMost(0.9f)),
                                                    zIndex = currentProject.layers.size
                                                )
                                                project = currentProject.copy(layers = currentProject.layers + dup)
                                                selectedLayerId = dup.id
                                            }
                                        )
                                        "presets" -> PresetsTabContent(
                                            selectedLayer = selectedLayer,
                                            onApplyPreset = { preset ->
                                                selectedLayer?.let {
                                                    pushUndo()
                                                    project = currentProject.copy(
                                                        layers = currentProject.layers.map { l ->
                                                            if (l.id == it.id) l.copy(rect = preset.rect) else l
                                                        }
                                                    )
                                                }
                                            }
                                        )
                                        "audio" -> AudioPlaybackTabContent(
                                            selectedLayer = selectedLayer,
                                            onUpdate = { updated ->
                                                pushUndo()
                                                project = currentProject.copy(
                                                    layers = currentProject.layers.map { if (it.id == updated.id) updated else it }
                                                )
                                            }
                                        )
                                        "transform" -> TransformTabContent(
                                            selectedLayer = selectedLayer,
                                            onUpdate = { updated ->
                                                pushUndo()
                                                project = currentProject.copy(
                                                    layers = currentProject.layers.map { if (it.id == updated.id) updated else it }
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Floating Transport Bar (Playhead & Timeline)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { isMasterPlaying = !isMasterPlaying },
                                modifier = Modifier.size(36.dp).testTag("master_play_button")
                            ) {
                                Icon(
                                    if (isMasterPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Master Play",
                                    tint = StudioPrimary
                                )
                            }

                            Text(
                                formatTimecode(currentPlayheadMs) + " / " + formatTimecode(currentProject.durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )

                            Slider(
                                value = (currentPlayheadMs.toFloat() / currentProject.durationMs.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f),
                                onValueChange = { frac -> currentPlayheadMs = (frac * currentProject.durationMs).toLong() },
                                modifier = Modifier.weight(1f).testTag("timeline_slider"),
                                colors = SliderDefaults.colors(
                                    thumbColor = StudioPrimary,
                                    activeTrackColor = StudioPrimary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }

                    // Floating Action Buttons Deck (Quick Layers, Presets, Audio, Add Layers)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Quick Add Layer Buttons
                            AddLayerChip("+ Full Cam", Icons.Default.Fullscreen) {
                                pushUndo()
                                val newLayer = Layer(
                                    name = "Full Canvas Camera",
                                    type = LayerType.CAMERA,
                                    cameraFacing = cameraFacing,
                                    rect = NormalizedRect(0f, 0f, 1f, 1f),
                                    zIndex = 0
                                )
                                // If placed at zIndex 0, bump others so it acts as full-screen base
                                val shiftedLayers = currentProject.layers.map { it.copy(zIndex = it.zIndex + 1) }
                                project = currentProject.copy(layers = listOf(newLayer) + shiftedLayers)
                                selectedLayerId = newLayer.id
                            }
                            AddLayerChip("+ Cam PiP", Icons.Default.Videocam) {
                                pushUndo()
                                val newLayer = Layer(
                                    name = "Camera PiP",
                                    type = LayerType.CAMERA,
                                    cameraFacing = cameraFacing,
                                    rect = NormalizedRect(0.6f, 0.05f, 0.35f, 0.32f),
                                    zIndex = currentProject.layers.size
                                )
                                project = currentProject.copy(layers = currentProject.layers + newLayer)
                                selectedLayerId = newLayer.id
                            }
                            AddLayerChip("+ Video", Icons.Default.Movie) {
                                pushUndo()
                                val newLayer = Layer(
                                    name = "Video Layer",
                                    type = LayerType.VIDEO,
                                    rect = NormalizedRect(0.05f, 0.55f, 0.45f, 0.35f),
                                    zIndex = currentProject.layers.size
                                )
                                project = currentProject.copy(layers = currentProject.layers + newLayer)
                                selectedLayerId = newLayer.id
                            }
                            AddLayerChip("+ Image", Icons.Default.Image) {
                                pushUndo()
                                val newLayer = Layer(
                                    name = "Sticker / Img",
                                    type = LayerType.IMAGE,
                                    rect = NormalizedRect(0.7f, 0.7f, 0.25f, 0.25f),
                                    zIndex = currentProject.layers.size
                                )
                                project = currentProject.copy(layers = currentProject.layers + newLayer)
                                selectedLayerId = newLayer.id
                            }
                            AddLayerChip("+ Text", Icons.Default.TextFields) {
                                pushUndo()
                                val newLayer = Layer(
                                    name = "Reaction Text",
                                    type = LayerType.TEXT,
                                    text = "LIVE REACTION",
                                    rect = NormalizedRect(0.1f, 0.05f, 0.5f, 0.12f),
                                    zIndex = currentProject.layers.size
                                )
                                project = currentProject.copy(layers = currentProject.layers + newLayer)
                                selectedLayerId = newLayer.id
                            }

                            VerticalDivider(modifier = Modifier.height(20.dp), color = Color.White.copy(alpha = 0.2f))

                            // Tab Trigger Overlay Buttons
                            TabButton("Layers (${currentProject.layers.size})", Icons.Default.Layers, activeBottomPanel == "layers") {
                                activeBottomPanel = if (activeBottomPanel == "layers") null else "layers"
                            }
                            TabButton("Presets", Icons.Default.DashboardCustomize, activeBottomPanel == "presets") {
                                activeBottomPanel = if (activeBottomPanel == "presets") null else "presets"
                            }
                            TabButton("Audio", Icons.Default.VolumeUp, activeBottomPanel == "audio") {
                                activeBottomPanel = if (activeBottomPanel == "audio") null else "audio"
                            }
                            TabButton("Transform", Icons.Default.CropRotate, activeBottomPanel == "transform") {
                                activeBottomPanel = if (activeBottomPanel == "transform") null else "transform"
                            }

                            // Hide Controls Toggle
                            IconButton(
                                onClick = { isUiOverlayVisible = false },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = "Hide UI Overlays", tint = StudioOnSurfaceVariant, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // When UI overlays are hidden, show a floating "Show Controls" pill so the user can easily bring them back
        if (!isUiOverlayVisible) {
            Surface(
                onClick = { isUiOverlayVisible = true },
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.7f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = StudioPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tap to Show Controls", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // Export Dialog / Progress Modal
    if (showExportDialog || exportState !is ExportState.Idle) {
        ExportModalDialog(
            project = currentProject,
            exportState = exportState,
            onDismiss = {
                showExportDialog = false
                if (exportState is ExportState.Success || exportState is ExportState.Error || exportState is ExportState.Cancelled) {
                    exportState = ExportState.Idle
                }
            },
            onUpdateSettings = { newSettings ->
                project = currentProject.copy(exportSettings = newSettings)
            },
            onStartExport = {
                coroutineScope.launch {
                    exportEngine.runExport(currentProject) { state ->
                        exportState = state
                    }
                }
            },
            onCancelExport = {
                exportEngine.cancelExport()
            }
        )
    }
}

private fun applyOrientation(activity: Activity?, aspect: AspectRatio) {
    activity ?: return
    activity.requestedOrientation = when (aspect) {
        AspectRatio.LANDSCAPE_16_9 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        AspectRatio.PORTRAIT_9_16 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        AspectRatio.SQUARE_1_1 -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

@Composable
fun LayerContentRenderer(
    layer: Layer,
    isMasterPlaying: Boolean,
    onPreviewViewCreated: (PreviewView) -> Unit,
    onFlipCamera: (() -> Unit)? = null
) {
    val isActuallyPlaying = isMasterPlaying && layer.isPlaying && !layer.isFreezeFrame

    when (layer.type) {
        LayerType.CAMERA -> {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            onPreviewViewCreated(this)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                // Camera indicator badge with quick front/back flip tap
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .then(
                            if (onFlipCamera != null) {
                                Modifier.clickable { onFlipCamera() }
                            } else Modifier
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(7.dp).background(StudioError, CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (layer.cameraFacing == CameraFacing.FRONT) "FRONT CAM" else "BACK CAM",
                        fontSize = 9.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    if (onFlipCamera != null) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.FlipCameraAndroid,
                            contentDescription = "Flip Camera",
                            tint = StudioPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
        LayerType.VIDEO -> {
            // Video layer with independent play/pause and freeze frame
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E293B)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (isActuallyPlaying) Icons.Default.PlayCircle else Icons.Default.PauseCircle,
                        contentDescription = null,
                        tint = if (isActuallyPlaying) StudioPrimary else StudioSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        if (layer.isFreezeFrame) "FREEZE FRAME" else if (isActuallyPlaying) "PLAYING" else "PAUSED",
                        fontSize = 10.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(layer.name, fontSize = 11.sp, color = StudioOnSurfaceVariant)
                }
            }
        }
        LayerType.IMAGE -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF2A3952)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Image, contentDescription = null, tint = StudioPrimary, modifier = Modifier.size(24.dp))
            }
        }
        LayerType.TEXT -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(layer.backgroundColor)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = layer.text.ifBlank { "Sample Text" },
                    color = Color(layer.textColor),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        else -> {
            Box(Modifier.fillMaxSize().background(Color(0xFF1B263B)))
        }
    }
}

@Composable
fun TransportTimelineBar(
    isMasterPlaying: Boolean,
    currentMs: Long,
    totalMs: Long,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit
) {
    Surface(
        color = StudioSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(36.dp).testTag("master_play_button")
            ) {
                Icon(
                    if (isMasterPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Master Play",
                    tint = StudioPrimary
                )
            }

            Text(
                formatTimecode(currentMs) + " / " + formatTimecode(totalMs),
                style = MaterialTheme.typography.labelMedium,
                color = StudioOnSurface,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Slider(
                value = (currentMs.toFloat() / totalMs.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f),
                onValueChange = { frac -> onSeek((frac * totalMs).toLong()) },
                modifier = Modifier.weight(1f).testTag("timeline_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = StudioPrimary,
                    activeTrackColor = StudioPrimary,
                    inactiveTrackColor = StudioOutline
                )
            )
        }
    }
}

@Composable
fun BottomEditingDeck(
    project: ProjectDocument,
    selectedLayerId: String?,
    activeTab: String,
    onTabSelect: (String) -> Unit,
    onSelectLayer: (String) -> Unit,
    onAddLayer: (LayerType) -> Unit,
    onAddFullCamera: () -> Unit,
    onUpdateLayer: (Layer) -> Unit,
    onDeleteLayer: (String) -> Unit,
    onDuplicateLayer: (Layer) -> Unit
) {
    val selectedLayer = project.layers.find { it.id == selectedLayerId }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(StudioSurface)
            .padding(bottom = 8.dp)
    ) {
        // Tab Headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            TabButton("Layers", Icons.Default.Layers, activeTab == "layers") { onTabSelect("layers") }
            TabButton("PiP Presets", Icons.Default.DashboardCustomize, activeTab == "presets") { onTabSelect("presets") }
            TabButton("Audio & Playback", Icons.Default.VolumeUp, activeTab == "audio") { onTabSelect("audio") }
            TabButton("Transform", Icons.Default.CropRotate, activeTab == "transform") { onTabSelect("transform") }
        }

        HorizontalDivider(color = StudioOutline.copy(alpha = 0.4f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            when (activeTab) {
                "layers" -> LayersTabContent(
                    layers = project.layers,
                    selectedId = selectedLayerId,
                    onSelect = onSelectLayer,
                    onAdd = onAddLayer,
                    onAddFullCamera = onAddFullCamera,
                    onDelete = onDeleteLayer,
                    onDuplicate = onDuplicateLayer
                )
                "presets" -> PresetsTabContent(
                    selectedLayer = selectedLayer,
                    onApplyPreset = { preset ->
                        selectedLayer?.let { onUpdateLayer(it.copy(rect = preset.rect)) }
                    }
                )
                "audio" -> AudioPlaybackTabContent(
                    selectedLayer = selectedLayer,
                    onUpdate = onUpdateLayer
                )
                "transform" -> TransformTabContent(
                    selectedLayer = selectedLayer,
                    onUpdate = onUpdateLayer
                )
            }
        }
    }
}

@Composable
fun TabButton(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(if (selected) StudioPrimaryContainer.copy(alpha = 0.4f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) StudioPrimary else StudioOnSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(title, fontSize = 12.sp, color = if (selected) StudioPrimary else StudioOnSurfaceVariant, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun LayersTabContent(
    layers: List<Layer>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onAdd: (LayerType) -> Unit,
    onAddFullCamera: () -> Unit,
    onDelete: (String) -> Unit,
    onDuplicate: (Layer) -> Unit
) {
    Column {
        // Add layer buttons row
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AddLayerChip("+ Full Cam", Icons.Default.Fullscreen) { onAddFullCamera() }
            AddLayerChip("+ Cam PiP", Icons.Default.Videocam) { onAdd(LayerType.CAMERA) }
            AddLayerChip("+ Video", Icons.Default.Movie) { onAdd(LayerType.VIDEO) }
            AddLayerChip("+ Image", Icons.Default.Image) { onAdd(LayerType.IMAGE) }
            AddLayerChip("+ Text", Icons.Default.TextFields) { onAdd(LayerType.TEXT) }
        }

        // Layer List Cards
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(layers) { layer ->
                val isSelected = layer.id == selectedId
                Card(
                    modifier = Modifier
                        .width(130.dp)
                        .clickable { onSelect(layer.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) StudioPrimaryContainer.copy(alpha = 0.5f) else StudioSurfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(layer.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            IconButton(
                                onClick = { onDelete(layer.id) },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Delete", tint = StudioError, modifier = Modifier.size(14.dp))
                            }
                        }
                        Text(layer.type.name, fontSize = 9.sp, color = StudioPrimary)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(if (layer.visible) "Visible" else "Hidden", fontSize = 9.sp, color = StudioOnSurfaceVariant)
                            Text("â€¢", fontSize = 9.sp, color = StudioOutline)
                            Text(if (layer.isPlaying) "Play" else "Pause", fontSize = 9.sp, color = StudioSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddLayerChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = StudioSurfaceVariant,
        modifier = Modifier.height(26.dp)
    ) {
        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = StudioPrimary, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(3.dp))
            Text(label, fontSize = 10.sp, color = StudioOnSurface)
        }
    }
}

@Composable
fun PresetsTabContent(
    selectedLayer: Layer?,
    onApplyPreset: (LayerPreset) -> Unit
) {
    if (selectedLayer == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Select a layer on the canvas to apply PiP presets", fontSize = 12.sp, color = StudioOnSurfaceVariant)
        }
        return
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(LayerPreset.values()) { preset ->
            Button(
                onClick = { onApplyPreset(preset) },
                colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceVariant),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(60.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CropSquare, contentDescription = null, tint = StudioPrimary, modifier = Modifier.size(16.dp))
                    Text(preset.label, fontSize = 10.sp, color = StudioOnSurface)
                }
            }
        }
    }
}

@Composable
fun AudioPlaybackTabContent(
    selectedLayer: Layer?,
    onUpdate: (Layer) -> Unit
) {
    if (selectedLayer == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Select a layer to adjust audio & independent playback", fontSize = 12.sp, color = StudioOnSurfaceVariant)
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Independent Play / Pause button
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = { onUpdate(selectedLayer.copy(isPlaying = !selectedLayer.isPlaying)) },
                modifier = Modifier.background(StudioSurfaceVariant, CircleShape)
            ) {
                Icon(
                    if (selectedLayer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Independent Play/Pause",
                    tint = if (selectedLayer.isPlaying) StudioPrimary else StudioSecondary
                )
            }
            Text(if (selectedLayer.isPlaying) "Playing" else "Paused", fontSize = 10.sp, color = StudioOnSurface)
        }

        // Visibility Toggle (Playing + Hidden is legal!)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = { onUpdate(selectedLayer.copy(visible = !selectedLayer.visible)) },
                modifier = Modifier.background(StudioSurfaceVariant, CircleShape)
            ) {
                Icon(
                    if (selectedLayer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = "Visibility",
                    tint = if (selectedLayer.visible) StudioPrimary else StudioOnSurfaceVariant
                )
            }
            Text(if (selectedLayer.visible) "Visible" else "Hidden", fontSize = 10.sp, color = StudioOnSurface)
        }

        // Freeze frame button
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = { onUpdate(selectedLayer.copy(isFreezeFrame = !selectedLayer.isFreezeFrame)) },
                modifier = Modifier.background(
                    if (selectedLayer.isFreezeFrame) StudioSecondaryContainer else StudioSurfaceVariant,
                    CircleShape
                )
            ) {
                Icon(Icons.Default.AcUnit, contentDescription = "Freeze Frame", tint = StudioSecondary)
            }
            Text(if (selectedLayer.isFreezeFrame) "Frozen" else "Live", fontSize = 10.sp, color = StudioOnSurface)
        }

        // Volume Slider & Mute
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Layer Volume (${(selectedLayer.volume * 100).toInt()}%)", fontSize = 11.sp, color = StudioOnSurface)
                IconButton(
                    onClick = { onUpdate(selectedLayer.copy(muted = !selectedLayer.muted)) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        if (selectedLayer.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = "Mute",
                        tint = if (selectedLayer.muted) StudioError else StudioPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Slider(
                value = if (selectedLayer.muted) 0f else selectedLayer.volume,
                onValueChange = { onUpdate(selectedLayer.copy(volume = it, muted = false)) },
                colors = SliderDefaults.colors(thumbColor = StudioPrimary, activeTrackColor = StudioPrimary)
            )
        }
    }
}

@Composable
fun TransformTabContent(
    selectedLayer: Layer?,
    onUpdate: (Layer) -> Unit
) {
    if (selectedLayer == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Select a layer to adjust rotation, scale, and fit mode", fontSize = 12.sp, color = StudioOnSurfaceVariant)
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Quick Fullscreen stretch button
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Full Screen", fontSize = 10.sp, color = StudioOnSurfaceVariant)
            IconButton(
                onClick = { onUpdate(selectedLayer.copy(rect = NormalizedRect(0f, 0f, 1f, 1f))) },
                modifier = Modifier.background(StudioPrimaryContainer, CircleShape)
            ) {
                Icon(Icons.Default.Fullscreen, contentDescription = "Full Canvas", tint = StudioPrimary)
            }
        }

        // Quick rotation button
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Rotate 90°", fontSize = 10.sp, color = StudioOnSurfaceVariant)
            IconButton(
                onClick = { onUpdate(selectedLayer.copy(rotation = (selectedLayer.rotation + 90f) % 360f)) },
                modifier = Modifier.background(StudioSurfaceVariant, CircleShape)
            ) {
                Icon(Icons.Default.RotateRight, contentDescription = "Rotate", tint = StudioPrimary)
            }
        }

        // Stretch / Size controls
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Stretch W: ${(selectedLayer.rect.width * 100).toInt()}%  H: ${(selectedLayer.rect.height * 100).toInt()}%",
                    fontSize = 11.sp,
                    color = StudioOnSurface,
                    fontWeight = FontWeight.Bold
                )
                // Center layer button
                TextButton(
                    onClick = {
                        val cx = ((1f - selectedLayer.rect.width) / 2f).coerceAtLeast(0f)
                        val cy = ((1f - selectedLayer.rect.height) / 2f).coerceAtLeast(0f)
                        onUpdate(selectedLayer.copy(rect = selectedLayer.rect.copy(x = cx, y = cy)))
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text("Center", fontSize = 10.sp, color = StudioPrimary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FitMode.values().take(4).forEach { mode ->
                    val isSelected = selectedLayer.fitMode == mode
                    FilterChip(
                        selected = isSelected,
                        onClick = { onUpdate(selectedLayer.copy(fitMode = mode)) },
                        label = { Text(mode.name, fontSize = 9.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = StudioPrimaryContainer,
                            selectedLabelColor = StudioPrimary
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportModalDialog(
    project: ProjectDocument,
    exportState: ExportState,
    onDismiss: () -> Unit,
    onUpdateSettings: (ExportSettings) -> Unit,
    onStartExport: () -> Unit,
    onCancelExport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (exportState is ExportState.Idle || exportState is ExportState.Success || exportState is ExportState.Error || exportState is ExportState.Cancelled) {
                onDismiss()
            }
        },
        containerColor = StudioSurface,
        title = {
            Text("Video Export Pipeline", fontWeight = FontWeight.Bold, color = StudioOnSurface, fontSize = 18.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (exportState) {
                    is ExportState.Idle -> {
                        // Codec selector: Smart, H.264, HEVC
                        Text("Export Codec", style = MaterialTheme.typography.labelMedium, color = StudioPrimary)
                        CodecMode.values().forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onUpdateSettings(project.exportSettings.copy(codecMode = mode)) }
                                    .background(
                                        if (project.exportSettings.codecMode == mode) StudioPrimaryContainer.copy(alpha = 0.4f)
                                        else StudioSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = project.exportSettings.codecMode == mode,
                                    onClick = { onUpdateSettings(project.exportSettings.copy(codecMode = mode)) },
                                    colors = RadioButtonDefaults.colors(selectedColor = StudioPrimary)
                                )
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text(mode.label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = StudioOnSurface)
                                    Text(mode.description, fontSize = 10.sp, color = StudioOnSurfaceVariant)
                                }
                            }
                        }

                        // Resolution selection
                        Text("Resolution", style = MaterialTheme.typography.labelMedium, color = StudioPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(ExportResolution.RES_720P, ExportResolution.RES_1080P, ExportResolution.RES_1440P).forEach { res ->
                                FilterChip(
                                    selected = project.exportSettings.resolution == res,
                                    onClick = { onUpdateSettings(project.exportSettings.copy(resolution = res)) },
                                    label = { Text(res.label, fontSize = 10.sp) }
                                )
                            }
                        }

                        // Quality preset
                        Text("Quality Preset", style = MaterialTheme.typography.labelMedium, color = StudioPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            QualityPreset.values().forEach { q ->
                                FilterChip(
                                    selected = project.exportSettings.quality == q,
                                    onClick = { onUpdateSettings(project.exportSettings.copy(quality = q)) },
                                    label = { Text(q.label, fontSize = 10.sp) }
                                )
                            }
                        }
                    }

                    is ExportState.Preflight -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = StudioPrimary)
                            Spacer(Modifier.height(12.dp))
                            Text(exportState.message, fontSize = 13.sp, color = StudioOnSurface)
                        }
                    }

                    is ExportState.Running -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinearProgressIndicator(
                                progress = { exportState.progress },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = StudioPrimary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(exportState.stage, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = StudioPrimary)
                                Text("${(exportState.progress * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = StudioOnSurface)
                            }
                            Text("Active Codec: ${exportState.codecUsed}", fontSize = 11.sp, color = StudioOnSurfaceVariant)
                            Text("Resolution: ${exportState.resolution}", fontSize = 11.sp, color = StudioOnSurfaceVariant)
                        }
                    }

                    is ExportState.Success -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StudioSuccess, modifier = Modifier.size(44.dp))
                            Text("Export Completed & Validated!", fontWeight = FontWeight.Bold, color = StudioOnSurface, fontSize = 15.sp)
                            Text("Codec: ${exportState.codecUsed}", fontSize = 12.sp, color = StudioPrimary)
                            Text("File Size: ${String.format("%.2f", exportState.fileSizeMb)} MB", fontSize = 12.sp, color = StudioOnSurfaceVariant)
                            Text("Saved to Movies/ReactionStudioExports", fontSize = 11.sp, color = StudioOnSurfaceVariant)
                        }
                    }

                    is ExportState.Error -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = StudioError, modifier = Modifier.size(44.dp))
                            Text("Export Failed", fontWeight = FontWeight.Bold, color = StudioError, fontSize = 15.sp)
                            Text(exportState.message, fontSize = 12.sp, color = StudioOnSurfaceVariant)
                        }
                    }

                    is ExportState.Cancelled -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null, tint = StudioWarning, modifier = Modifier.size(44.dp))
                            Text("Export Cancelled", fontWeight = FontWeight.Bold, color = StudioWarning, fontSize = 15.sp)
                            Text("Temporary files have been securely removed.", fontSize = 12.sp, color = StudioOnSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (exportState) {
                is ExportState.Idle -> {
                    Button(
                        onClick = onStartExport,
                        colors = ButtonDefaults.buttonColors(containerColor = StudioPrimary),
                        modifier = Modifier.testTag("start_export_confirm_button")
                    ) {
                        Text("Start Export", color = StudioOnPrimary, fontWeight = FontWeight.Bold)
                    }
                }
                is ExportState.Running -> {
                    OutlinedButton(
                        onClick = onCancelExport,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioError)
                    ) {
                        Text("Cancel Export")
                    }
                }
                else -> {
                    Button(onClick = onDismiss) {
                        Text("Done")
                    }
                }
            }
        },
        dismissButton = {
            if (exportState is ExportState.Idle) {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = StudioOnSurfaceVariant)
                }
            }
        }
    )
}

private fun formatTimecode(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val millis = (ms % 1000) / 100
    return String.format("%02d:%02d.%01d", minutes, seconds, millis)
}
