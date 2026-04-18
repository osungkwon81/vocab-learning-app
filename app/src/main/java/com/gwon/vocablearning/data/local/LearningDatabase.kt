package com.gwon.vocablearning.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

private const val LEARNING_DB_NAME = "learning.db"
private const val LEARNING_DB_VERSION = 1

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
            ).build()

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
    }
}
