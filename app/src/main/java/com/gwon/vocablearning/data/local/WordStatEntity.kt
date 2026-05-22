package com.gwon.vocablearning.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.gwon.vocablearning.domain.model.LearningResponse
import com.gwon.vocablearning.domain.model.WordStat

@Entity(tableName = "word_stat")
data class WordStatEntity(
    @PrimaryKey
    @ColumnInfo(name = "word_id")
    val wordId: Long,
    @ColumnInfo(name = "total_solved_count")
    val totalSolvedCount: Int,
    @ColumnInfo(name = "correct_count")
    val correctCount: Int,
    @ColumnInfo(name = "wrong_count")
    val wrongCount: Int,
    @ColumnInfo(name = "total_elapsed_ms")
    val totalElapsedMs: Long,
    @ColumnInfo(name = "average_elapsed_ms")
    val averageElapsedMs: Long,
    @ColumnInfo(name = "last_solved_at")
    val lastSolvedAt: Long?,
    @ColumnInfo(name = "need_review")
    val needReview: Boolean,
    @ColumnInfo(name = "next_review_at")
    val nextReviewAt: Long?,
    @ColumnInfo(name = "memory_strength")
    val memoryStrength: Int,
    @ColumnInfo(name = "consecutive_correct_count")
    val consecutiveCorrectCount: Int,
    @ColumnInfo(name = "last_learning_response")
    val lastLearningResponse: String?,
)

fun WordStatEntity.toDomain(): WordStat =
    WordStat(
        wordId = wordId,
        totalSolvedCount = totalSolvedCount,
        correctCount = correctCount,
        wrongCount = wrongCount,
        totalElapsedMs = totalElapsedMs,
        averageElapsedMs = averageElapsedMs,
        lastSolvedAt = lastSolvedAt,
        needReview = needReview,
        nextReviewAt = nextReviewAt,
        memoryStrength = memoryStrength,
        consecutiveCorrectCount = consecutiveCorrectCount,
        lastLearningResponse = lastLearningResponse?.let(LearningResponse::valueOf),
    )
