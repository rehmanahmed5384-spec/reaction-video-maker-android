package com.example.core.hardware

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.core.model.CameraFacing

class CameraHelper(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var activeCamera: Camera? = null
    private var currentFacing: CameraFacing = CameraFacing.FRONT
    private var isTorchOn: Boolean = false

    fun initialize(onReady: () -> Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                cameraProvider = future.get()
                onReady()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        facing: CameraFacing = currentFacing,
        onCameraBound: ((Camera) -> Unit)? = null
    ) {
        val provider = cameraProvider ?: return
        currentFacing = facing

        try {
            provider.unbindAll()

            val selector = if (facing == CameraFacing.FRONT) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview
            )
            activeCamera = camera
            onCameraBound?.invoke(camera)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun switchCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onSwitched: (CameraFacing) -> Unit
    ) {
        val nextFacing = if (currentFacing == CameraFacing.FRONT) CameraFacing.BACK else CameraFacing.FRONT
        startCamera(lifecycleOwner, previewView, nextFacing) {
            onSwitched(nextFacing)
        }
    }

    fun hasHardwareTorch(): Boolean {
        return activeCamera?.cameraInfo?.hasFlashUnit() == true
    }

    fun toggleTorch(enable: Boolean, onResult: (Boolean) -> Unit) {
        val camera = activeCamera ?: return onResult(false)
        if (camera.cameraInfo.hasFlashUnit()) {
            camera.cameraControl.enableTorch(enable)
            isTorchOn = enable
            onResult(enable)
        } else {
            onResult(false)
        }
    }

    fun setZoom(ratio: Float) {
        activeCamera?.cameraControl?.setZoomRatio(ratio)
    }

    fun release() {
        try {
            cameraProvider?.unbindAll()
            activeCamera = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
