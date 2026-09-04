package com.example.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val aspectRatio: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val layerCount: Int,
    val durationMs: Long,
    val thumbnailUri: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "export_jobs")
data class ExportJobEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val projectName: String,
    val codec: String,
    val resolution: String,
    val quality: String,
    val status: String, // QUEUED, PREPARING, ENCODING, MUXING, VALIDATING, COMPLETED, FAILED, CANCELLED
    val progress: Float,
    val outputPath: String? = null,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
