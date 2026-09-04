package com.example.core.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)
}

@Dao
interface ExportJobDao {
    @Query("SELECT * FROM export_jobs ORDER BY timestamp DESC")
    fun getAllExportJobs(): Flow<List<ExportJobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: ExportJobEntity)

    @Query("UPDATE export_jobs SET progress = :progress, status = :status WHERE id = :id")
    suspend fun updateProgress(id: String, progress: Float, status: String)

    @Query("UPDATE export_jobs SET progress = 1.0, status = 'COMPLETED', outputPath = :outputPath WHERE id = :id")
    suspend fun completeJob(id: String, outputPath: String)

    @Query("UPDATE export_jobs SET status = 'FAILED', errorMessage = :error WHERE id = :id")
    suspend fun failJob(id: String, error: String)

    @Query("UPDATE export_jobs SET status = 'CANCELLED' WHERE id = :id")
    suspend fun cancelJob(id: String)
}

@Database(
    entities = [ProjectEntity::class, ExportJobEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun exportJobDao(): ExportJobDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "reaction_studio_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
