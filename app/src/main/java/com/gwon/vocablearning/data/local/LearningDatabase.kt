package com.gwon.vocablearning.data.local

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

private const val LEARNING_DB_NAME = "learning.db"
private const val LEARNING_DB_VERSION = 2

@Database(
    entities = [WordStatEntity::class, QuizHistoryEntity::class],
    version = LEARNING_DB_VERSION,
    exportSchema = true,
)
abstract class LearningDatabase : RoomDatabase() {
    abstract fun wordStatDao(): WordStatDao
    abstract fun quizHistoryDao(): QuizHistoryDao

    companion object {
        fun build(context: Context): LearningDatabase {
            val protection = LearningDatabaseProtection(
                context = context,
                databaseName = LEARNING_DB_NAME,
                targetVersion = LEARNING_DB_VERSION,
            )
            protection.backupBeforeVersionChange()

            val database = Room.databaseBuilder(
                context,
                LearningDatabase::class.java,
                LEARNING_DB_NAME,
            ).addMigrations(MIGRATION_1_2)
                .build()

            return try {
                // Force the first open here so migration failures happen after a safety backup exists.
                database.openHelper.writableDatabase
                database
            } catch (throwable: RuntimeException) {
                runCatching { database.close() }
                val restored = protection.restoreLatestBackupIfDatabaseMissing()
                protection.recordOpenFailure(throwable, restored)
                throw throwable
            }
        }

        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        "ALTER TABLE word_stat ADD COLUMN next_review_at INTEGER",
                    )
                    database.execSQL(
                        "ALTER TABLE word_stat ADD COLUMN memory_strength INTEGER NOT NULL DEFAULT 0",
                    )
                    database.execSQL(
                        "ALTER TABLE word_stat ADD COLUMN consecutive_correct_count INTEGER NOT NULL DEFAULT 0",
                    )
                    database.execSQL(
                        "ALTER TABLE word_stat ADD COLUMN last_learning_response TEXT",
                    )
                }
            }
    }
}
