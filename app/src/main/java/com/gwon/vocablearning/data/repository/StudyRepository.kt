package com.gwon.vocablearning.data.repository

import com.gwon.vocablearning.domain.model.DashboardSnapshot
import com.gwon.vocablearning.domain.model.QuizType
import com.gwon.vocablearning.domain.model.ReviewItem
import com.gwon.vocablearning.domain.model.SchoolGrade
import com.gwon.vocablearning.domain.model.SyncStatus
import com.gwon.vocablearning.domain.model.SyncSummary
import com.gwon.vocablearning.domain.model.WordEntry
import com.gwon.vocablearning.domain.model.WordProgress

interface StudyRepository {
    suspend fun getNickname(): String
    suspend fun setNickname(nickname: String)
    suspend fun getSelectedGrade(): SchoolGrade
    suspend fun setSelectedGrade(grade: SchoolGrade)
    suspend fun getLearningCount(): Int
    suspend fun setLearningCount(count: Int)
    suspend fun hasCompletedOnboarding(): Boolean
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun loadWords(grade: SchoolGrade): List<WordEntry>
    suspend fun getSyncStatus(grade: SchoolGrade): SyncStatus
    suspend fun loadDashboard(grade: SchoolGrade): DashboardSnapshot
    suspend fun loadWordProgress(grade: SchoolGrade): List<WordProgress>
    suspend fun loadStudyDeck(grade: SchoolGrade, count: Int): List<WordProgress>
    suspend fun loadReviewItems(grade: SchoolGrade): List<ReviewItem>
    suspend fun recordLearningResult(
        wordId: Long,
        knewIt: Boolean,
        elapsedMs: Long,
    )

    suspend fun recordQuizResult(
        wordId: Long,
        quizType: QuizType,
        isCorrect: Boolean,
        elapsedMs: Long,
    )

    suspend fun syncCatalog(
        selectedGrade: SchoolGrade? = null,
        forceSelectedGrade: Boolean = false,
    ): SyncSummary
}
