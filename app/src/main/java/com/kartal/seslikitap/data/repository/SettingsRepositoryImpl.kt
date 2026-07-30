package com.kartal.seslikitap.data.repository

import com.kartal.seslikitap.data.local.dao.UserSettingsDao
import com.kartal.seslikitap.data.local.mapper.toDomain
import com.kartal.seslikitap.data.local.mapper.toEntity
import com.kartal.seslikitap.di.IoDispatcher
import com.kartal.seslikitap.domain.model.UserSettings
import com.kartal.seslikitap.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dao: UserSettingsDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SettingsRepository {

    private val writeMutex = Mutex()

    override fun observeSettings(): Flow<UserSettings> =
        dao.observeSettings().map { it?.toDomain() ?: UserSettings.Default }

    override suspend fun getSettings(): UserSettings = withContext(ioDispatcher) {
        dao.getSettings()?.toDomain() ?: UserSettings.Default
    }

    override suspend fun updateSettings(transform: (UserSettings) -> UserSettings) {
        withContext(ioDispatcher) {
            // Oku-değiştir-yaz döngüsünü serileştir; aksi halde eşzamanlı iki güncelleme
            // birbirini ezebilir.
            writeMutex.withLock {
                val current = dao.getSettings()?.toDomain() ?: UserSettings.Default
                dao.upsert(transform(current).toEntity())
            }
        }
    }
}
