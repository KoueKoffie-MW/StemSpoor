package com.example.recme.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.recme.data.db.entity.AppConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppConfigDao {

    @Query("SELECT * FROM app_config WHERE `key` = :key LIMIT 1")
    suspend fun getConfig(key: String): AppConfigEntity?

    @Query("SELECT * FROM app_config WHERE `key` = :key LIMIT 1")
    fun getConfigFlow(key: String): Flow<AppConfigEntity?>

    @Query("SELECT longValue FROM app_config WHERE `key` = :key LIMIT 1")
    suspend fun getLongValue(key: String): Long?

    @Query("SELECT stringValue FROM app_config WHERE `key` = :key LIMIT 1")
    suspend fun getStringValue(key: String): String?

    @Query("SELECT booleanValue FROM app_config WHERE `key` = :key LIMIT 1")
    suspend fun getBooleanValue(key: String): Boolean?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setConfig(config: AppConfigEntity)
}
