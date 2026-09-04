package com.example.core.data

import android.content.Context
import com.example.core.model.AspectRatio
import com.example.core.model.Layer
import com.example.core.model.LayerType
import com.example.core.model.NormalizedRect
import com.example.core.model.ProjectDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ProjectRepository(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val projectDao = database.projectDao()
    private val exportJobDao = database.exportJobDao()
    private val fileManager = ProjectFileManager(context)

    val allProjects: Flow<List<ProjectEntity>> = projectDao.getAllProjects()
    val allExportJobs: Flow<List<ExportJobEntity>> = exportJobDao.getAllExportJobs()

    suspend fun createNewProject(
        name: String,
        aspectRatio: AspectRatio,
        mainCanvasCamera: Boolean = false
    ): ProjectDocument = withContext(Dispatchers.IO) {
        val (w, h) = aspectRatio.defaultWidth to aspectRatio.defaultHeight
        val defaultLayers = if (mainCanvasCamera) {
            listOf(
                Layer(
                    name = "Full Canvas Camera",
                    type = LayerType.CAMERA,
                    rect = NormalizedRect(0f, 0f, 1f, 1f),
                    zIndex = 0
                ),
                Layer(
                    name = "Video PiP",
                    type = LayerType.VIDEO,
                    rect = NormalizedRect(0.64f, 0.04f, 0.32f, 0.32f),
                    zIndex = 1
                )
            )
        } else {
            listOf(
                Layer(
                    name = "Main Video",
                    type = LayerType.VIDEO,
                    rect = NormalizedRect(0f, 0f, 1f, 1f),
                    zIndex = 0
                ),
                Layer(
                    name = "Reaction Camera",
                    type = LayerType.CAMERA,
                    rect = NormalizedRect(0.64f, 0.04f, 0.32f, 0.32f),
                    zIndex = 1
                )
            )
        }

        val project = ProjectDocument(
            name = name.ifBlank { if (mainCanvasCamera) "Camera Reaction Project" else "New Reaction Project" },
            canvas = com.example.core.model.CanvasSpec(
                aspectRatio = aspectRatio,
                width = w,
                height = h
            ),
            layers = defaultLayers
        )

        saveProject(project)
        project
    }

    suspend fun saveProject(project: ProjectDocument) = withContext(Dispatchers.IO) {
        val updated = project.copy(updatedAt = System.currentTimeMillis())
        fileManager.saveProject(updated)
        projectDao.insertProject(
            ProjectEntity(
                id = updated.id,
                name = updated.name,
                aspectRatio = updated.canvas.aspectRatio.name,
                width = updated.canvas.width,
                height = updated.canvas.height,
                fps = updated.canvas.fps,
                layerCount = updated.layers.size,
                durationMs = updated.durationMs,
                updatedAt = updated.updatedAt
            )
        )
    }

    suspend fun loadProject(id: String): ProjectDocument? = withContext(Dispatchers.IO) {
        fileManager.loadProject(id)
    }

    suspend fun loadRecoveryProject(): ProjectDocument? = withContext(Dispatchers.IO) {
        fileManager.loadLastSafeRecovery()
    }

    suspend fun deleteProject(id: String) = withContext(Dispatchers.IO) {
        fileManager.deleteProject(id)
        projectDao.deleteProjectById(id)
    }

    suspend fun logExportJob(job: ExportJobEntity) = withContext(Dispatchers.IO) {
        exportJobDao.insertJob(job)
    }

    suspend fun updateExportProgress(jobId: String, progress: Float, status: String) = withContext(Dispatchers.IO) {
        exportJobDao.updateProgress(jobId, progress, status)
    }

    suspend fun completeExportJob(jobId: String, outputPath: String) = withContext(Dispatchers.IO) {
        exportJobDao.completeJob(jobId, outputPath)
    }

    suspend fun failExportJob(jobId: String, error: String) = withContext(Dispatchers.IO) {
        exportJobDao.failJob(jobId, error)
    }
}
