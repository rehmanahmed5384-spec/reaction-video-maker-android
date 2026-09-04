package com.example.core.hardware

import android.app.ActivityManager
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.example.core.model.CameraHardwareInfo
import com.example.core.model.CodecInfo
import com.example.core.model.DeviceDiagnostics

object HardwareCapabilityDetector {

    fun detectCapabilities(context: Context): DeviceDiagnostics {
        val h264Enc = findCodecInfo(MediaFormat.MIMETYPE_VIDEO_AVC, isEncoder = true)
        val h264Dec = findCodecInfo(MediaFormat.MIMETYPE_VIDEO_AVC, isEncoder = false)
        val hevcEnc = findCodecInfo(MediaFormat.MIMETYPE_VIDEO_HEVC, isEncoder = true)
        val hevcDec = findCodecInfo(MediaFormat.MIMETYPE_VIDEO_HEVC, isEncoder = false)

        val cameraInfo = inspectCameras(context)

        // Memory info
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024 * 1024)

        // Storage info
        val stat = StatFs(context.filesDir.absolutePath)
        val availStorageMb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)

        return DeviceDiagnostics(
            deviceModel = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            osVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            totalRamMb = totalRamMb,
            availableStorageMb = availStorageMb,
            h264Encoder = h264Enc,
            h264Decoder = h264Dec,
            hevcEncoder = hevcEnc,
            hevcDecoder = hevcDec,
            cameraInfo = cameraInfo
        )
    }

    private fun findCodecInfo(mimeType: String, isEncoder: Boolean): CodecInfo? {
        return try {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in list.codecInfos) {
                if (info.isEncoder != isEncoder) continue
                val types = info.supportedTypes
                for (type in types) {
                    if (type.equals(mimeType, ignoreCase = true)) {
                        val caps = info.getCapabilitiesForType(type)
                        val videoCaps = caps.videoCapabilities

                        val isHw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            info.isHardwareAccelerated
                        } else {
                            !info.name.startsWith("OMX.google.") && !info.name.startsWith("c2.android.")
                        }

                        val maxWidth = videoCaps?.supportedWidths?.upper ?: 1920
                        val maxHeight = videoCaps?.supportedHeights?.upper ?: 1080
                        val maxBitrate = videoCaps?.bitrateRange?.upper ?: 20_000_000

                        return CodecInfo(
                            mimeType = mimeType,
                            isEncoder = isEncoder,
                            isHardwareAccelerated = isHw,
                            codecName = info.name,
                            maxSupportedWidth = maxWidth,
                            maxSupportedHeight = maxHeight,
                            maxBitrateBps = maxBitrate
                        )
                    }
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun inspectCameras(context: Context): CameraHardwareInfo {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return CameraHardwareInfo()

            val ids = cm.cameraIdList
            var hasFront = false
            var hasBack = false
            var backTorch = false
            var frontTorch = false
            var maxZoom = 1.0f

            for (id in ids) {
                val chars = cm.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                val flash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                val zoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
                if (zoom > maxZoom) maxZoom = zoom

                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    hasFront = true
                    if (flash) frontTorch = true
                } else if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    hasBack = true
                    if (flash) backTorch = true
                }
            }

            var concurrent = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val concurrentSets = cm.concurrentCameraIds
                    concurrent = concurrentSets.isNotEmpty()
                } catch (_: Exception) {
                    concurrent = false
                }
            }

            CameraHardwareInfo(
                hasFrontCamera = hasFront,
                hasBackCamera = hasBack,
                totalCameras = ids.size,
                backCameraHasTorch = backTorch,
                frontCameraHasTorch = frontTorch,
                supportsConcurrentCameras = concurrent,
                maxZoomRatio = maxZoom
            )
        } catch (e: Exception) {
            e.printStackTrace()
            CameraHardwareInfo()
        }
    }
}
