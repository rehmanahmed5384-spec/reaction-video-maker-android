package com.example.core.model

data class CodecInfo(
    val mimeType: String,
    val isEncoder: Boolean,
    val isHardwareAccelerated: Boolean,
    val codecName: String,
    val maxSupportedWidth: Int = 1920,
    val maxSupportedHeight: Int = 1080,
    val maxBitrateBps: Int = 20_000_000
)

data class CameraHardwareInfo(
    val hasFrontCamera: Boolean = false,
    val hasBackCamera: Boolean = false,
    val totalCameras: Int = 0,
    val backCameraHasTorch: Boolean = false,
    val frontCameraHasTorch: Boolean = false,
    val supportsConcurrentCameras: Boolean = false,
    val maxZoomRatio: Float = 1.0f
)

data class DeviceDiagnostics(
    val deviceModel: String = "",
    val manufacturer: String = "",
    val osVersion: String = "",
    val apiLevel: Int = 0,
    val totalRamMb: Long = 0,
    val availableStorageMb: Long = 0,
    val h264Encoder: CodecInfo? = null,
    val h264Decoder: CodecInfo? = null,
    val hevcEncoder: CodecInfo? = null,
    val hevcDecoder: CodecInfo? = null,
    val cameraInfo: CameraHardwareInfo = CameraHardwareInfo()
) {
    fun toFormattedReport(): String {
        return buildString {
            appendLine("=== AHMED REACTION STUDIO HARDWARE REPORT ===")
            appendLine("Device: $manufacturer $deviceModel (API $apiLevel, Android $osVersion)")
            appendLine("Memory: Total RAM ${totalRamMb}MB | Storage: ${availableStorageMb}MB available")
            appendLine()
            appendLine("[Video Codecs]")
            appendLine("H.264 Encoder: ${h264Encoder?.let { "${it.codecName} (HW: ${it.isHardwareAccelerated}, Max: ${it.maxSupportedWidth}x${it.maxSupportedHeight})" } ?: "UNAVAILABLE"}")
            appendLine("H.264 Decoder: ${h264Decoder?.let { "${it.codecName} (HW: ${it.isHardwareAccelerated})" } ?: "UNAVAILABLE"}")
            appendLine("HEVC Encoder:  ${hevcEncoder?.let { "${it.codecName} (HW: ${it.isHardwareAccelerated}, Max: ${it.maxSupportedWidth}x${it.maxSupportedHeight})" } ?: "UNAVAILABLE"}")
            appendLine("HEVC Decoder:  ${hevcDecoder?.let { "${it.codecName} (HW: ${it.isHardwareAccelerated})" } ?: "UNAVAILABLE"}")
            appendLine()
            appendLine("[Camera System]")
            appendLine("Total Cameras: ${cameraInfo.totalCameras}")
            appendLine("Front Camera: ${if (cameraInfo.hasFrontCamera) "Available" else "Not detected"}")
            appendLine("Back Camera: ${if (cameraInfo.hasBackCamera) "Available" else "Not detected"}")
            appendLine("Rear Flash/Torch: ${if (cameraInfo.backCameraHasTorch) "Hardware Unit Present" else "No Hardware Flash"}")
            appendLine("Front Flash/Torch: ${if (cameraInfo.frontCameraHasTorch) "Hardware Unit Present" else "Software Screen Illumination Fallback"}")
            appendLine("Concurrent Dual-Camera: ${if (cameraInfo.supportsConcurrentCameras) "Supported by Device" else "Single Active Camera (Device Constraint)"}")
            appendLine("=============================================")
        }
    }
}
