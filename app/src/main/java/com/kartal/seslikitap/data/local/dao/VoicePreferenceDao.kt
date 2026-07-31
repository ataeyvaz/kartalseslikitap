package com.kartal.seslikitap.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kartal.seslikitap.data.local.entity.VoicePreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VoicePreferenceDao {

    @Query("SELECT * FROM voice_preferences")
    fun observeAll(): Flow<List<VoicePreferenceEntity>>

    @Query("SELECT * FROM voice_preferences WHERE provider_id = :providerId")
    suspend fun get(providerId: String): VoicePreferenceEntity?

    @Upsert
    suspend fun upsert(preference: VoicePreferenceEntity)

    @Query("DELETE FROM voice_preferences WHERE provider_id = :providerId")
    suspend fun delete(providerId: String)
}
