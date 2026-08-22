package com.example.recme.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.example.recme.ai.speaker.SpeakerProfileManager
import com.example.recme.data.db.AppDatabase
import com.example.recme.service.VadRecordingService
import com.example.recme.storage.StorageManager
import com.example.recme.vault.VaultManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Root Koin DI Module providing singleton dependencies across StemSpoor.
 */
val appModule = module {
    single<SharedPreferences> {
        androidContext().getSharedPreferences(VadRecordingService.PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Core Managers
    single { StorageManager(androidContext()) }
    single { VaultManager(androidContext()) }
    single { SpeakerProfileManager(androidContext()) }

    // Room Database & DAOs
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).fallbackToDestructiveMigration().build()
    }

    single { get<AppDatabase>().recordingDao() }
    single { get<AppDatabase>().speechSegmentDao() }
    single { get<AppDatabase>().speakerProfileDao() }
    single { get<AppDatabase>().gateAuditDao() }
    single { get<AppDatabase>().vaultIndexDao() }
    single { get<AppDatabase>().appConfigDao() }
}
