package com.example.myapplication.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.myapplication.data.local.doo.GpsPointDao
import com.example.myapplication.data.local.doo.WorkoutSessionDao
import com.example.myapplication.data.local.entity.GpsPointEntity
import com.example.myapplication.data.local.entity.WorkoutSessionEntity

/**
 * AppDatabase — Room baza podatkov za Offline-First strategijo (Faza 3).
 *
 * Tabele:
 *  - workout_sessions: povzetki aktivnosti (id = Firestore doc.id kot PK)
 *  - gps_points:       GPS koordinate (lokalni surovi podatki ali Firestore kompresija)
 *
 * Zgodovina verzij:
 *  v1 → Faza 3: začetna shema (workout_sessions, gps_points brez status polja)
 *  v2 → Faza 15: dodan stolpec status TEXT NOT NULL DEFAULT 'COMPLETED' v workout_sessions
 *                (obnava po OOM kill — IN_PROGRESS seje preživijo restart servisa)
 *  v3 → Faza 33: schema mismatch fix — entity sprememba brez eksplicitne migracije.
 *                fallbackToDestructiveMigration() zbriše staro lokalno bazo in jo ustvari znova.
 *                (Lokalni Room podatki so samo cache Firestore — izguba ni kritična.)
 *
 * Ime datoteke: glow_upp_offline.db
 *
 * ⚠️ FIX (Faza 31.8): Dodan MIGRATION_1_2 — prepreči izbris IN_PROGRESS sej med schema upgradami.
 *    fallbackToDestructiveMigration() ostane kot zadnja varovalka za nepričakovane bodoče napake.
 */
@Database(
    entities = [WorkoutSessionEntity::class, GpsPointEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun gpsPointDao(): GpsPointDao

    companion object {

        /**
         * Migracija v1 → v2: dodan stolpec `status` v `workout_sessions`.
         * ALTER TABLE + DEFAULT 'COMPLETED' ohrani vse obstoječe vrstice (teki) nedotaknjene.
         * Brez te migracije je Room izbrisal celotno bazo — IN_PROGRESS (aktivni tek) je bil izgubljen.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE workout_sessions ADD COLUMN status TEXT NOT NULL DEFAULT 'COMPLETED'"
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "glow_upp_offline.db"
                )
                    .addMigrations(MIGRATION_1_2)       // v1→v2: ohrani aktivne seje (IN_PROGRESS)
                    .fallbackToDestructiveMigration()   // v2→v3+: brez eksplicitne migracije → zbriši in ustvari znova
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

