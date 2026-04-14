package com.gwon.vocablearning.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WordStatEntity::class, QuizHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class LearningDatabase : RoomDatabase() {
    abstract fun wordStatDao(): WordStatDao
    abstract fun quizHistoryDao(): QuizHistoryDao

    companion object {
        fun build(context: Context): LearningDatabase =
            Room.databaseBuilder(
                context,
                LearningDatabase::class.java,
                "learning.db",
            ).build()
    }
}

