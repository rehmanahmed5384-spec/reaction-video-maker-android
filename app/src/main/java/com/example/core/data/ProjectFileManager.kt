package com.example.core.data

import android.content.Context
import com.example.core.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class ProjectFileManager(private val context: Context) {

    private val projectsDir: File
        get() = File(context.filesDir, "projects").apply { if (!exists()) mkdirs() }

    private val recoveryDir: File
        get() = File(context.filesDir, "recovery").apply { if (!exists()) mkdirs() }

    fun saveProject(project: ProjectDocument): Boolean {
        return try {
            val json = serializeProject(project)
            val projectFolder = File(projectsDir, project.id).apply { if (!exists()) mkdirs() }
            val targetFile = File(projectFolder, "project.json")
            val tempFile = File(projectFolder, "project.json.tmp")

            FileOutputStream(tempFile).use { it.write(json.toByteArray()) }
            tempFile.renameTo(targetFile)

            // Save crash recovery snapshot
            saveRecoverySnapshot(project, json)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun saveRecoverySnapshot(project: ProjectDocument, json: String) {
        try {
            val recoveryFile = File(recoveryDir, "last_safe_project.json")
            val tempFile = File(recoveryDir, "last_safe_project.json.tmp")
            FileOutputStream(tempFile).use { it.write(json.toByteArray()) }
            tempFile.renameTo(recoveryFile)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadProject(id: String): ProjectDocument? {
        val file = File(File(projectsDir, id), "project.json")
        if (!file.exists()) return null
        return try {
            val json = file.readText()
            deserializeProject(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun loadLastSafeRecovery(): ProjectDocument? {
        val file = File(recoveryDir, "last_safe_project.json")
        if (!file.exists()) return null
        return try {
            val json = file.readText()
            deserializeProject(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteProject(id: String) {
        val folder = File(projectsDir, id)
        if (folder.exists()) {
            folder.deleteRecursively()
        }
    }

    fun serializeProject(p: ProjectDocument): String {
        val obj = JSONObject()
        obj.put("schemaVersion", p.schemaVersion)
        obj.put("id", p.id)
        obj.put("name", p.name)
        obj.put("durationMs", p.durationMs)
        obj.put("createdAt", p.createdAt)
        obj.put("updatedAt", p.updatedAt)

        // Canvas
        val canvasObj = JSONObject()
        canvasObj.put("aspectRatio", p.canvas.aspectRatio.name)
        canvasObj.put("width", p.canvas.width)
        canvasObj.put("height", p.canvas.height)
        canvasObj.put("fps", p.canvas.fps)
        val bgObj = JSONObject()
        bgObj.put("type", p.canvas.background.type.name)
        bgObj.put("primaryColor", p.canvas.background.primaryColor)
        bgObj.put("secondaryColor", p.canvas.background.secondaryColor)
        canvasObj.put("background", bgObj)
        obj.put("canvas", canvasObj)

        // Export Settings
        val exportObj = JSONObject()
        exportObj.put("codecMode", p.exportSettings.codecMode.name)
        exportObj.put("resolution", p.exportSettings.resolution.name)
        exportObj.put("quality", p.exportSettings.quality.name)
        exportObj.put("fps", p.exportSettings.fps)
        obj.put("exportSettings", exportObj)

        // Layers
        val layersArr = JSONArray()
        p.layers.forEach { layer ->
            val lObj = JSONObject()
            lObj.put("id", layer.id)
            lObj.put("name", layer.name)
            lObj.put("type", layer.type.name)
            lObj.put("sourceUri", layer.sourceUri ?: "")
            lObj.put("text", layer.text)
            lObj.put("textColor", layer.textColor)
            lObj.put("backgroundColor", layer.backgroundColor)
            lObj.put("visible", layer.visible)
            lObj.put("locked", layer.locked)
            lObj.put("muted", layer.muted)
            lObj.put("volume", layer.volume.toDouble())
            lObj.put("zIndex", layer.zIndex)
            lObj.put("rotation", layer.rotation.toDouble())
            lObj.put("scale", layer.scale.toDouble())
            lObj.put("opacity", layer.opacity.toDouble())
            lObj.put("fitMode", layer.fitMode.name)
            lObj.put("speed", layer.speed.toDouble())
            lObj.put("isPlaying", layer.isPlaying)
            lObj.put("cameraFacing", layer.cameraFacing.name)
            lObj.put("isFreezeFrame", layer.isFreezeFrame)
            lObj.put("startTimeMs", layer.startTimeMs)
            lObj.put("durationMs", layer.durationMs)

            val rectObj = JSONObject()
            rectObj.put("x", layer.rect.x.toDouble())
            rectObj.put("y", layer.rect.y.toDouble())
            rectObj.put("width", layer.rect.width.toDouble())
            rectObj.put("height", layer.rect.height.toDouble())
            lObj.put("rect", rectObj)

            layersArr.put(lObj)
        }
        obj.put("layers", layersArr)

        return obj.toString(2)
    }

    fun deserializeProject(jsonStr: String): ProjectDocument {
        val obj = JSONObject(jsonStr)
        val schemaVersion = obj.optInt("schemaVersion", 1)
        val id = obj.getString("id")
        val name = obj.optString("name", "Untitled")
        val durationMs = obj.optLong("durationMs", 30_000L)
        val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
        val updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())

        val canvasObj = obj.optJSONObject("canvas") ?: JSONObject()
        val aspect = try {
            AspectRatio.valueOf(canvasObj.optString("aspectRatio", AspectRatio.LANDSCAPE_16_9.name))
        } catch (_: Exception) {
            AspectRatio.LANDSCAPE_16_9
        }
        val canvasWidth = canvasObj.optInt("width", aspect.defaultWidth)
        val canvasHeight = canvasObj.optInt("height", aspect.defaultHeight)
        val fps = canvasObj.optInt("fps", 30)

        val bgObj = canvasObj.optJSONObject("background") ?: JSONObject()
        val bgType = try {
            BackgroundType.valueOf(bgObj.optString("type", BackgroundType.GRADIENT.name))
        } catch (_: Exception) {
            BackgroundType.GRADIENT
        }
        val bgSpec = BackgroundSpec(
            type = bgType,
            primaryColor = bgObj.optLong("primaryColor", 0xFF0A0F1DL),
            secondaryColor = bgObj.optLong("secondaryColor", 0xFF162238L)
        )

        val canvasSpec = CanvasSpec(
            aspectRatio = aspect,
            width = canvasWidth,
            height = canvasHeight,
            fps = fps,
            background = bgSpec
        )

        val exportObj = obj.optJSONObject("exportSettings") ?: JSONObject()
        val codecMode = try {
            CodecMode.valueOf(exportObj.optString("codecMode", CodecMode.SMART.name))
        } catch (_: Exception) {
            CodecMode.SMART
        }
        val resolution = try {
            ExportResolution.valueOf(exportObj.optString("resolution", ExportResolution.RES_1080P.name))
        } catch (_: Exception) {
            ExportResolution.RES_1080P
        }
        val quality = try {
            QualityPreset.valueOf(exportObj.optString("quality", QualityPreset.BALANCED.name))
        } catch (_: Exception) {
            QualityPreset.BALANCED
        }
        val exportSettings = ExportSettings(
            codecMode = codecMode,
            resolution = resolution,
            quality = quality,
            fps = exportObj.optInt("fps", 30)
        )

        val layers = mutableListOf<Layer>()
        val layersArr = obj.optJSONArray("layers") ?: JSONArray()
        for (i in 0 until layersArr.length()) {
            val lObj = layersArr.getJSONObject(i)
            val rectObj = lObj.optJSONObject("rect") ?: JSONObject()
            val rect = NormalizedRect(
                x = rectObj.optDouble("x", 0.0).toFloat(),
                y = rectObj.optDouble("y", 0.0).toFloat(),
                width = rectObj.optDouble("width", 0.4).toFloat(),
                height = rectObj.optDouble("height", 0.4).toFloat()
            )

            val type = try {
                LayerType.valueOf(lObj.optString("type", LayerType.TEXT.name))
            } catch (_: Exception) {
                LayerType.TEXT
            }
            val fitMode = try {
                FitMode.valueOf(lObj.optString("fitMode", FitMode.FIT.name))
            } catch (_: Exception) {
                FitMode.FIT
            }
            val cameraFacing = try {
                CameraFacing.valueOf(lObj.optString("cameraFacing", CameraFacing.FRONT.name))
            } catch (_: Exception) {
                CameraFacing.FRONT
            }

            layers.add(
                Layer(
                    id = lObj.getString("id"),
                    name = lObj.optString("name", "Layer"),
                    type = type,
                    sourceUri = lObj.optString("sourceUri").takeIf { it.isNotBlank() },
                    text = lObj.optString("text", ""),
                    textColor = lObj.optLong("textColor", 0xFFFFFFFFL),
                    backgroundColor = lObj.optLong("backgroundColor", 0x00000000L),
                    visible = lObj.optBoolean("visible", true),
                    locked = lObj.optBoolean("locked", false),
                    muted = lObj.optBoolean("muted", false),
                    volume = lObj.optDouble("volume", 1.0).toFloat(),
                    zIndex = lObj.optInt("zIndex", i),
                    rect = rect,
                    rotation = lObj.optDouble("rotation", 0.0).toFloat(),
                    scale = lObj.optDouble("scale", 1.0).toFloat(),
                    opacity = lObj.optDouble("opacity", 1.0).toFloat(),
                    fitMode = fitMode,
                    speed = lObj.optDouble("speed", 1.0).toFloat(),
                    isPlaying = lObj.optBoolean("isPlaying", true),
                    cameraFacing = cameraFacing,
                    isFreezeFrame = lObj.optBoolean("isFreezeFrame", false),
                    startTimeMs = lObj.optLong("startTimeMs", 0L),
                    durationMs = lObj.optLong("durationMs", durationMs)
                )
            )
        }

        return ProjectDocument(
            schemaVersion = schemaVersion,
            id = id,
            name = name,
            canvas = canvasSpec,
            layers = layers,
            durationMs = durationMs,
            exportSettings = exportSettings,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
