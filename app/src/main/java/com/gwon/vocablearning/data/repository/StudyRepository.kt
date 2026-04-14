package com.gwon.vocablearning.data.repository

import com.gwon.vocablearning.domain.model.DashboardSnapshot
import com.gwon.vocablearning.domain.model.QuizType
import com.gwon.vocablearning.domain.model.ReviewItem
import com.gwon.vocablearning.domain.model.SchoolGrade
import com.gwon.vocablearning.domain.model.SyncSummary
import com.gwon.vocablearning.domain.model.WordEntry
import com.gwon.vocablearning.domain.model.WordProgress

interface StudyRepository {
    suspend fun getSelectedGrade(): SchoolGrade
    suspend fun setSelectedGrade(grade: SchoolGrade)
    suspend fun loadWords(grade: SchoolGrade): List<WordEntry>
    suspend fun loadDashboard(grade: SchoolGrade): DashboardSnapshot
    suspend fun loadWordProgress(grade: SchoolGrade): List<WordProgress>
    suspend fun loadReviewItems(grade: SchoolGrade): List<ReviewItem>
    suspend fun recordQuizResult(
        wordId: Long,
        quizType: QuizType,
        isCorrect: Boolean,
        elapsedMs: Long,
    )

    suspend fun syncCatalog(): SyncSummary
}

