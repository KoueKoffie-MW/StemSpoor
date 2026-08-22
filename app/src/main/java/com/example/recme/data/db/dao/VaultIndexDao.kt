package com.example.recme.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.recme.data.db.entity.VaultIndexEntity

@Dao
interface VaultIndexDao {

    @Query("SELECT * FROM vault_index WHERE date = :date ORDER BY lastIndexed DESC")
    suspend fun getIndexForDate(date: String): List<VaultIndexEntity>

    @Query("SELECT * FROM vault_index WHERE textSnippet LIKE '%' || :query || '%' ORDER BY lastIndexed DESC LIMIT :limit")
    suspend fun searchKeyword(query: String, limit: Int = 50): List<VaultIndexEntity>

    @Query("SELECT * FROM vault_index WHERE embedding IS NOT NULL")
    suspend fun getAllWithEmbeddings(): List<VaultIndexEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entry: VaultIndexEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<VaultIndexEntity>)

    @Query("DELETE FROM vault_index WHERE id = :id")
    suspend fun deleteById(id: String)
}
