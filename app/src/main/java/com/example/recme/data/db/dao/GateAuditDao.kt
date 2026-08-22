package com.example.recme.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.recme.data.db.entity.GateAuditEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GateAuditDao {

    @Query("SELECT * FROM gate_audit ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentAuditsFlow(limit: Int = 100): Flow<List<GateAuditEntity>>

    @Query("SELECT COUNT(*) FROM gate_audit WHERE timestamp >= :sinceTimestamp")
    suspend fun getTotalDecisionsCount(sinceTimestamp: Long): Int

    @Query("SELECT COUNT(*) FROM gate_audit WHERE decision = 'ALLOWED' AND timestamp >= :sinceTimestamp")
    suspend fun getAllowedDecisionsCount(sinceTimestamp: Long): Int

    @Query("SELECT COUNT(*) FROM gate_audit WHERE decision LIKE 'DENIED%' AND timestamp >= :sinceTimestamp")
    suspend fun getDeniedDecisionsCount(sinceTimestamp: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(audit: GateAuditEntity)

    @Query("DELETE FROM gate_audit WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)
}
