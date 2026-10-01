package com.vineyard.aivideostudio.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vineyard.aivideostudio.core.model.PipelineStatus
import com.vineyard.aivideostudio.data.local.database.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun getProjectByIdFlow(id: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE status = :status ORDER BY updatedAt DESC LIMIT 1")
    fun getActiveProject(status: PipelineStatus): Flow<ProjectEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("UPDATE projects SET status = :status, currentStage = :stage, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProjectStatus(id: String, status: PipelineStatus, stage: PipelineStatus, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE projects SET currentVideoUri = :uri, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCurrentVideoUri(id: String, uri: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE projects SET finalVideoUri = :uri, status = 'COMPLETED', updatedAt = :updatedAt WHERE id = :id")
    suspend fun markProjectCompleted(id: String, uri: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE projects SET lastError = :error, status = 'FAILED', updatedAt = :updatedAt WHERE id = :id")
    suspend fun markProjectFailed(id: String, error: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)
}
