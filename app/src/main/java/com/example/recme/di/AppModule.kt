package com.example.recme.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.example.recme.ai.speaker.SpeakerProfileManager
import com.example.recme.data.db.AppDatabase
import com.example.recme.data.repository.ConfigRepositoryImpl
import com.example.recme.data.repository.GateAuditRepositoryImpl
import com.example.recme.data.repository.RecordingRepositoryImpl
import com.example.recme.data.repository.SpeakerRepositoryImpl
import com.example.recme.domain.repository.ConfigRepository
import com.example.recme.domain.repository.GateAuditRepository
import com.example.recme.domain.repository.RecordingRepository
import com.example.recme.domain.repository.SpeakerRepository
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

    // Repositories
    single<RecordingRepository> { RecordingRepositoryImpl(get(), get()) }
    single<SpeakerRepository> { SpeakerRepositoryImpl(get()) }
    single<GateAuditRepository> { GateAuditRepositoryImpl(get()) }
    single<ConfigRepository> { ConfigRepositoryImpl(get()) }

    // AI & Voice Gate Engines
    single { com.example.recme.ai.speaker.SpeakerEmbeddingEngine(androidContext()) }
    single { com.example.recme.ai.speaker.SpeakerDiarizationEngine(androidContext(), get(), get(), get(), get()) }
    single { com.example.recme.ai.voicegate.VoiceGateEvaluator(get(), get(), get(), get()) }

    // Bootstrap & Synchronization
    single { com.example.recme.data.bootstrap.DatabaseBootstrapManager(get(), get(), get(), get(), get()) }
}
