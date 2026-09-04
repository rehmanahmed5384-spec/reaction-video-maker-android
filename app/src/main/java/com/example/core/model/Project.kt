package com.example.core.model

import java.util.UUID

enum class AspectRatio(val displayName: String, val ratio: Float, val defaultWidth: Int, val defaultHeight: Int) {
    LANDSCAPE_16_9("16:9 Landscape", 16f / 9f, 1920, 1080),
    PORTRAIT_9_16("9:16 Portrait", 9f / 16f, 1080, 1920),
    SQUARE_1_1("1:1 Square", 1f, 1080, 1080)
}

enum class LayerType {
    VIDEO,
    CAMERA,
    IMAGE,
    TEXT,
    AUDIO,
    SHAPE,
    STICKER
}

enum class FitMode {
    FIT,
    FILL,
    STRETCH,
    CROP,
    CUSTOM
}

enum class CameraFacing {
    FRONT,
    BACK
}

enum class BackgroundType {
    SOLID,
    GRADIENT,
    BLUR
}

data class NormalizedRect(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 1f,
    val height: Float = 1f
) {
    fun clamped(): NormalizedRect {
        val w = width.coerceIn(0.05f, 1.0f)
        val h = height.coerceIn(0.05f, 1.0f)
        val cx = x.coerceIn(0f, 1.0f - w)
        val cy = y.coerceIn(0f, 1.0f - h)
        return NormalizedRect(cx, cy, w, h)
    }
}

enum class LayerPreset(val label: String, val rect: NormalizedRect) {
    FULL_SCREEN("Full Screen", NormalizedRect(0f, 0f, 1f, 1f)),
    TOP_LEFT("Top Left", NormalizedRect(0.02f, 0.02f, 0.35f, 0.35f)),
    TOP_RIGHT("Top Right", NormalizedRect(0.63f, 0.02f, 0.35f, 0.35f)),
    BOTTOM_LEFT("Bottom Left", NormalizedRect(0.02f, 0.63f, 0.35f, 0.35f)),
    BOTTOM_RIGHT("Bottom Right", NormalizedRect(0.63f, 0.63f, 0.35f, 0.35f)),
    CENTER("Center", NormalizedRect(0.25f, 0.25f, 0.5f, 0.5f)),
    SPLIT_50_50_H("50/50 Horizontal", NormalizedRect(0f, 0f, 0.5f, 1f)),
    SPLIT_50_50_V("50/50 Vertical", NormalizedRect(0f, 0f, 1f, 0.5f)),
    SPLIT_70_30_H("70/30 Left", NormalizedRect(0f, 0f, 0.7f, 1f)),
    SPLIT_70_30_V("70/30 Top", NormalizedRect(0f, 0f, 1f, 0.7f)),
    QUARTER("Quarter", NormalizedRect(0.5f, 0.5f, 0.5f, 0.5f))
}

data class BackgroundSpec(
    val type: BackgroundType = BackgroundType.GRADIENT,
    val primaryColor: Long = 0xFF0A0F1D,
    val secondaryColor: Long = 0xFF162238
)

data class CanvasSpec(
    val aspectRatio: AspectRatio = AspectRatio.LANDSCAPE_16_9,
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 30,
    val background: BackgroundSpec = BackgroundSpec()
)

data class Layer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: LayerType,
    val sourceUri: String? = null,
    val text: String = "",
    val textColor: Long = 0xFFFFFFFF,
    val backgroundColor: Long = 0x00000000,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val muted: Boolean = false,
    val volume: Float = 1.0f,
    val zIndex: Int = 0,
    val rect: NormalizedRect = NormalizedRect(0.05f, 0.05f, 0.4f, 0.4f),
    val rotation: Float = 0f,
    val scale: Float = 1.0f,
    val opacity: Float = 1.0f,
    val fitMode: FitMode = FitMode.FIT,
    val speed: Float = 1.0f,
    val isPlaying: Boolean = true, // Critical: Independent layer pause/play
    val cameraFacing: CameraFacing = CameraFacing.FRONT,
    val isFreezeFrame: Boolean = false,
    val startTimeMs: Long = 0L,
    val durationMs: Long = 60_000L
)

enum class CodecMode(val label: String, val description: String) {
    SMART("Smart Auto", "Auto-selects HEVC if hardware available, falls back to H.264"),
    H264("H.264 / AVC", "Universally compatible, highly reliable hardware encoder"),
    HEVC("H.265 / HEVC", "Higher compression efficiency, requires supported hardware encoder")
}

enum class ExportResolution(val label: String, val landscapeWidth: Int, val landscapeHeight: Int) {
    RES_480P("480p SD", 854, 480),
    RES_720P("720p HD", 1280, 720),
    RES_1080P("1080p Full HD", 1920, 1080),
    RES_1440P("1440p 2K", 2560, 1440),
    RES_2160P("2160p 4K UHD", 3840, 2160);

    fun dimensionsFor(aspectRatio: AspectRatio): Pair<Int, Int> {
        return when (aspectRatio) {
            AspectRatio.LANDSCAPE_16_9 -> landscapeWidth to landscapeHeight
            AspectRatio.PORTRAIT_9_16 -> landscapeHeight to landscapeWidth
            AspectRatio.SQUARE_1_1 -> landscapeHeight to landscapeHeight
        }
    }
}

enum class QualityPreset(val label: String, val h264BitrateMultiplier: Float, val hevcBitrateMultiplier: Float) {
    FAST("Fast (Draft)", 0.6f, 0.5f),
    BALANCED("Balanced", 1.0f, 0.8f),
    HIGH("High Quality", 1.4f, 1.1f),
    MAXIMUM("Maximum Quality", 1.8f, 1.4f)
}

data class ExportSettings(
    val codecMode: CodecMode = CodecMode.SMART,
    val resolution: ExportResolution = ExportResolution.RES_1080P,
    val quality: QualityPreset = QualityPreset.BALANCED,
    val fps: Int = 30
)

data class ProjectDocument(
    val schemaVersion: Int = 1,
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Untitled Reaction",
    val canvas: CanvasSpec = CanvasSpec(),
    val layers: List<Layer> = emptyList(),
    val durationMs: Long = 30_000L,
    val exportSettings: ExportSettings = ExportSettings(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
