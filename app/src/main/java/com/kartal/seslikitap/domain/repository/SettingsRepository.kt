package com.kartal.seslikitap.domain.repository

import com.kartal.seslikitap.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    fun observeSettings(): Flow<UserSettings>

    suspend fun getSettings(): UserSettings

    suspend fun updateSettings(transform: (UserSettings) -> UserSettings)
}
