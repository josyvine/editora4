package com.vineyard.aivideostudio.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.vineyard.aivideostudio.core.common.AppConstants
import com.vineyard.aivideostudio.data.local.database.dao.AiRequestDao
import com.vineyard.aivideostudio.data.local.database.dao.CaptionDao
import com.vineyard.aivideostudio.data.local.database.dao.CommentaryDao
import com.vineyard.aivideostudio.data.local.database.dao.MediaArtifactDao
import com.vineyard.aivideostudio.data.local.database.dao.ModelConfigurationDao
import com.vineyard.aivideostudio.data.local.database.dao.PersistentLogDao
import com.vineyard.aivideostudio.data.local.database.dao.PipelineStepDao
import com.vineyard.aivideostudio.data.local.database.dao.ProjectDao
import com.vineyard.aivideostudio.data.local.database.dao.QaResultDao
import com.vineyard.aivideostudio.data.local.database.dao.TimelineSegmentDao
import com.vineyard.aivideostudio.data.local.database.dao.TranscriptSegmentDao
import com.vineyard.aivideostudio.data.local.database.dao.VoiceDao
import com.vineyard.aivideostudio.data.local.database.entity.AiRequestEntity
import com.vineyard.aivideostudio.data.local.database.entity.CaptionEntity
import com.vineyard.aivideostudio.data.local.database.entity.CommentaryEntity
import com.vineyard.aivideostudio.data.local.database.entity.MediaArtifactEntity
import com.vineyard.aivideostudio.data.local.database.entity.ModelConfigurationEntity
import com.vineyard.aivideostudio.data.local.database.entity.PersistentLogEntity
import com.vineyard.aivideostudio.data.local.database.entity.PipelineStepEntity
import com.vineyard.aivideostudio.data.local.database.entity.ProjectEntity
import com.vineyard.aivideostudio.data.local.database.entity.QaResultEntity
import com.vineyard.aivideostudio.data.local.database.entity.TimelineSegmentEntity
import com.vineyard.aivideostudio.data.local.database.entity.TranscriptSegmentEntity
import com.vineyard.aivideostudio.data.local.database.entity.VoiceEntity

@Database(
    entities = [
        ProjectEntity::class,
        PipelineStepEntity::class,
        MediaArtifactEntity::class,
        TimelineSegmentEntity::class,
        TranscriptSegmentEntity::class,
        CaptionEntity::class,
        CommentaryEntity::class,
        AiRequestEntity::class,
        QaResultEntity::class,
        ModelConfigurationEntity::class,
        VoiceEntity::class,
        PersistentLogEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun pipelineStepDao(): PipelineStepDao
    abstract fun mediaArtifactDao(): MediaArtifactDao
    abstract fun timelineSegmentDao(): TimelineSegmentDao
    abstract fun transcriptSegmentDao(): TranscriptSegmentDao
    abstract fun captionDao(): CaptionDao
    abstract fun commentaryDao(): CommentaryDao
    abstract fun aiRequestDao(): AiRequestDao
    abstract fun qaResultDao(): QaResultDao
    abstract fun modelConfigurationDao(): ModelConfigurationDao
    abstract fun voiceDao(): VoiceDao
    abstract fun persistentLogDao(): PersistentLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    AppConstants.DATABASE_NAME
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}