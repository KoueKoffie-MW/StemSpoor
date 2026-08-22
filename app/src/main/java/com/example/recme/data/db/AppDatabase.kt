package com.example.recme.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.recme.data.db.converters.StringListConverter
import com.example.recme.data.db.dao.AppConfigDao
import com.example.recme.data.db.dao.GateAuditDao
import com.example.recme.data.db.dao.RecordingDao
import com.example.recme.data.db.dao.SpeakerProfileDao
import com.example.recme.data.db.dao.SpeechSegmentDao
import com.example.recme.data.db.dao.VaultIndexDao
import com.example.recme.data.db.entity.AppConfigEntity
import com.example.recme.data.db.entity.GateAuditEntity
import com.example.recme.data.db.entity.RecordingEntity
import com.example.recme.data.db.entity.SpeakerProfileEntity
import com.example.recme.data.db.entity.SpeechSegmentEntity
import com.example.recme.data.db.entity.VaultIndexEntity

/**
 * StemSpoor Room SQLite Database.
 * Acts as a fast relational query projection over audio sidecars and metadata.
 */
@Database(
    entities = [
        RecordingEntity::class,
        SpeechSegmentEntity::class,
        SpeakerProfileEntity::class,
        GateAuditEntity::class,
        VaultIndexEntity::class,
        AppConfigEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(StringListConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recordingDao(): RecordingDao
    abstract fun speechSegmentDao(): SpeechSegmentDao
    abstract fun speakerProfileDao(): SpeakerProfileDao
    abstract fun gateAuditDao(): GateAuditDao
    abstract fun vaultIndexDao(): VaultIndexDao
    abstract fun appConfigDao(): AppConfigDao

    companion object {
        const val DATABASE_NAME = "stemspoor_db"
    }
}
