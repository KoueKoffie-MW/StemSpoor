package com.example.recme.domain.repository

import kotlinx.coroutines.flow.Flow

interface ConfigRepository {
    suspend fun getLong(key: String, defaultValue: Long): Long
    suspend fun setLong(key: String, value: Long)
    suspend fun getString(key: String, defaultValue: String): String
    suspend fun setString(key: String, value: String)
    suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean
    suspend fun setBoolean(key: String, value: Boolean)
    fun getLongFlow(key: String, defaultValue: Long): Flow<Long>
}
