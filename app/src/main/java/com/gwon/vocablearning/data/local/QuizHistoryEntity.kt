package com.gwon.vocablearning.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quiz_history")
data class QuizHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "word_id")
    val wordId: Long,
    @ColumnInfo(name = "quiz_type")
    val quizType: String,
    @ColumnInfo(name = "is_correct")
    val isCorrect: Boolean,
    @ColumnInfo(name = "elapsed_ms")
    val elapsedMs: Long,
    @ColumnInfo(name = "solved_at")
    val solvedAt: Long,
)

