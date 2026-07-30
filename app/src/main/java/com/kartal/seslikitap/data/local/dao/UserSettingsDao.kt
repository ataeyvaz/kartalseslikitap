package com.kartal.seslikitap.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kartal.seslikitap.data.local.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSettingsDao {

    @Query("SELECT * FROM user_settings WHERE id = :id")
    fun observeSettings(id: Int = UserSettingsEntity.SINGLETON_ID): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE id = :id")
    suspend fun getSettings(id: Int = UserSettingsEntity.SINGLETON_ID): UserSettingsEntity?

    @Upsert
    suspend fun upsert(settings: UserSettingsEntity)
}
