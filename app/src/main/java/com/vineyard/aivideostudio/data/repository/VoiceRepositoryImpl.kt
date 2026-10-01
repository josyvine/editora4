package com.vineyard.aivideostudio.data.repository

import com.vineyard.aivideostudio.data.local.database.dao.VoiceDao
import com.vineyard.aivideostudio.data.local.database.entity.VoiceEntity
import com.vineyard.aivideostudio.voice.model.VoiceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class VoiceRepositoryImpl(
    private val voiceDao: VoiceDao
) {
    fun getAllVoices(): Flow<List<VoiceProfile>> {
        return voiceDao.getAllVoices().map { list ->
            list.map {
                VoiceProfile(
                    id = it.id,
                    name = it.name,
                    description = it.description ?: "",
                    isReplicated = it.isReplicated,
                    sampleAudioUri = it.sampleAudioUri,
                    consentVerified = it.consentVerified,
                    createdAt = it.createdAt
                )
            }
        }
    }

    suspend fun getVoiceById(id: String): VoiceProfile? {
        val entity = voiceDao.getVoiceById(id) ?: return null
        return VoiceProfile(
            id = entity.id,
            name = entity.name,
            description = entity.description ?: "",
            isReplicated = entity.isReplicated,
            sampleAudioUri = entity.sampleAudioUri,
            consentVerified = entity.consentVerified,
            createdAt = entity.createdAt
        )
    }

    suspend fun createVoice(
        name: String,
        description: String,
        isReplicated: Boolean,
        sampleAudioUri: String?,
        consentVerified: Boolean
    ): VoiceProfile {
        val voice = VoiceProfile(
            id = "voice_${UUID.randomUUID().toString().take(8)}",
            name = name,
            description = description,
            isReplicated = isReplicated,
            sampleAudioUri = sampleAudioUri,
            consentVerified = consentVerified,
            createdAt = System.currentTimeMillis()
        )
        voiceDao.insertVoice(
            VoiceEntity(
                id = voice.id,
                name = voice.name,
                description = voice.description,
                isReplicated = voice.isReplicated,
                sampleAudioUri = voice.sampleAudioUri,
                consentVerified = voice.consentVerified,
                createdAt = voice.createdAt
            )
        )
        return voice
    }

    suspend fun deleteVoice(id: String) {
        voiceDao.deleteVoiceById(id)
    }
}
