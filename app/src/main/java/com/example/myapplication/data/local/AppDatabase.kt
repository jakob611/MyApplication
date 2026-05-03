package com.example.myapplication.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * AppDatabase — Room baza podatkov za Offline-First strategijo (Faza 3).
 *
 * Tabele:
 *  - workout_sessions: povzetki aktivnosti (id = Firestore doc.id kot PK)
 *  - gps_points:       GPS koordinate (lokalni surovi podatki ali Firestore kompresija)
 *
 * Verzija: 1 (začetna)
 * Ime datoteke: glow_upp_offline.db
 */
@Database(
    entities = [WorkoutSessionEntity::class, GpsPointEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun gpsPointDao(): GpsPointDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "glow_upp_offline.db"
                )
                    .fallbackToDestructiveMigration() // V1: dev/beta — pri spremembi sheme obnovi DB
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

