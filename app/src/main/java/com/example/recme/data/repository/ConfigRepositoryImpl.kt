package com.example.recme.data.repository

import com.example.recme.data.db.dao.AppConfigDao
import com.example.recme.data.db.entity.AppConfigEntity
import com.example.recme.domain.repository.ConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConfigRepositoryImpl(
    private val appConfigDao: AppConfigDao
) : ConfigRepository {

    override suspend fun getLong(key: String, defaultValue: Long): Long {
        return appConfigDao.getLongValue(key) ?: defaultValue
    }

    override suspend fun setLong(key: String, value: Long) {
        appConfigDao.setConfig(
            AppConfigEntity(
                key = key,
                longValue = value,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun getString(key: String, defaultValue: String): String {
        return appConfigDao.getStringValue(key) ?: defaultValue
    }

    override suspend fun setString(key: String, value: String) {
        appConfigDao.setConfig(
            AppConfigEntity(
                key = key,
                stringValue = value,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return appConfigDao.getBooleanValue(key) ?: defaultValue
    }

    override suspend fun setBoolean(key: String, value: Boolean) {
        appConfigDao.setConfig(
            AppConfigEntity(
                key = key,
                booleanValue = value,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override fun getLongFlow(key: String, defaultValue: Long): Flow<Long> {
        return appConfigDao.getConfigFlow(key).map { it?.longValue ?: defaultValue }
    }
}
