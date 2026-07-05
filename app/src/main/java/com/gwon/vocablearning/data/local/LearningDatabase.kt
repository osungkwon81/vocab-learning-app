package com.gwon.vocablearning.data.local

import android.content.Context
import android.util.Log
import androidx.room.migration.Migration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase.Builder
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
            val requiresVersionUpgrade = protection.requiresVersionUpgrade()
            protection.backupBeforeVersionChange()

            val database = createBuilder(context).build()

            return try {
                // Force the first open here so migration failures happen after a safety backup exists.
                open(database)
                database
            } catch (throwable: RuntimeException) {
                runCatching { database.close() }
                val restored = protection.restoreLatestBackupIfDatabaseMissing()
                protection.recordOpenFailure(throwable, restored)
                if (!requiresVersionUpgrade) {
                    throw throwable
                }

                recoverWithDestructiveMigration(context, throwable)
            }
        }

        private fun createBuilder(context: Context): Builder<LearningDatabase> =
            Room.databaseBuilder(
                context,
                LearningDatabase::class.java,
                LEARNING_DB_NAME,
            ).addMigrations(MIGRATION_1_2)

        private fun open(database: LearningDatabase) {
            database.openHelper.writableDatabase
        }

        private fun recoverWithDestructiveMigration(
            context: Context,
            originalThrowable: RuntimeException,
        ): LearningDatabase {
            Log.e(TAG, "Migration open failed. Falling back to destructive migration.", originalThrowable)
            val fallbackDatabase = createBuilder(context)
                .fallbackToDestructiveMigration()
                .build()

            return try {
                open(fallbackDatabase)
                fallbackDatabase
            } catch (fallbackThrowable: RuntimeException) {
                runCatching { fallbackDatabase.close() }
                fallbackThrowable.addSuppressed(originalThrowable)
                throw fallbackThrowable
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

        private const val TAG = "LearningDatabase"
    }
}
