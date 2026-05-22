package com.gwon.vocablearning.data.repository

import com.gwon.vocablearning.BuildConfig
import com.gwon.vocablearning.data.local.QuizHistoryDao
import com.gwon.vocablearning.data.local.QuizHistoryEntity
import com.gwon.vocablearning.data.local.WordStatDao
import com.gwon.vocablearning.data.local.WordStatEntity
import com.gwon.vocablearning.data.local.toDomain
import com.gwon.vocablearning.data.preferences.SettingsPreferencesRepository
import com.gwon.vocablearning.data.remote.CatalogFileStore
import com.gwon.vocablearning.data.remote.RemoteCatalogService
import com.gwon.vocablearning.data.remote.toDomain
import com.gwon.vocablearning.domain.model.DashboardSnapshot
import com.gwon.vocablearning.domain.model.LearningResponse
import com.gwon.vocablearning.domain.model.QuizType
import com.gwon.vocablearning.domain.model.ReviewItem
import com.gwon.vocablearning.domain.model.ReviewReason
import com.gwon.vocablearning.domain.model.SchoolGrade
import com.gwon.vocablearning.domain.model.SyncStatus
import com.gwon.vocablearning.domain.model.SyncSummary
import com.gwon.vocablearning.domain.model.WordEntry
import com.gwon.vocablearning.domain.model.WordProgress
import com.gwon.vocablearning.domain.model.WordStat
import com.gwon.vocablearning.domain.service.StudyDeckPlanner

class OfflineFirstStudyRepository(
    private val settingsRepository: SettingsPreferencesRepository,
    private val catalogFileStore: CatalogFileStore,
    private val remoteCatalogService: RemoteCatalogService,
    private val wordStatDao: WordStatDao,
    private val quizHistoryDao: QuizHistoryDao,
    private val studyDeckPlanner: StudyDeckPlanner,
) : StudyRepository {
    override suspend fun getNickname(): String =
        settingsRepository.getNickname()

    override suspend fun setNickname(nickname: String) {
        settingsRepository.setNickname(nickname)
    }

    override suspend fun getSelectedGrade(): SchoolGrade =
        settingsRepository.getSelectedGrade()

    override suspend fun setSelectedGrade(grade: SchoolGrade) {
        settingsRepository.setSelectedGrade(grade)
    }

    override suspend fun getLearningCount(): Int =
        settingsRepository.getLearningCount()

    override suspend fun setLearningCount(count: Int) {
        settingsRepository.setLearningCount(count)
    }

    override suspend fun hasCompletedOnboarding(): Boolean =
        settingsRepository.hasCompletedOnboarding()

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        settingsRepository.setOnboardingCompleted(completed)
    }

    override suspend fun loadWords(grade: SchoolGrade): List<WordEntry> =
        catalogFileStore.loadWordSet(grade).toDomain()

    override suspend fun getSyncStatus(grade: SchoolGrade): SyncStatus {
        val syncState = settingsRepository.getSyncState()
        return SyncStatus(
            manifestVersion = syncState.manifestVersion,
            fileVersion = syncState.fileVersions[grade.fileKey] ?: 0,
        )
    }

    override suspend fun loadDashboard(
        grade: SchoolGrade,
        sourceBook: String?,
    ): DashboardSnapshot {
        val progress = loadWordProgress(grade, sourceBook)
        val now = System.currentTimeMillis()
        val stats = progress.map { it.stat }
        val totalAttempts = stats.sumOf { it.totalSolvedCount }
        val totalElapsed = stats.sumOf { it.totalElapsedMs }

        return DashboardSnapshot(
            totalWords = progress.size,
            solvedWords = progress.count { it.stat.totalSolvedCount > 0 },
            totalAttempts = totalAttempts,
            correctAnswers = stats.sumOf { it.correctCount },
            wrongAnswers = stats.sumOf { it.wrongCount },
            averageElapsedMs = if (totalAttempts == 0) 0 else totalElapsed / totalAttempts,
            reviewCount = progress.count { item ->
                item.stat.totalSolvedCount > 0 &&
                    (item.stat.needReview || (item.stat.nextReviewAt ?: Long.MAX_VALUE) <= now)
            },
        )
    }

    override suspend fun loadWordProgress(
        grade: SchoolGrade,
        sourceBook: String?,
    ): List<WordProgress> {
        val words = loadWords(grade).filterBySourceBook(sourceBook)
        val stats = wordStatDao.getAll()
            .associateBy { it.wordId }

        return words.map { entry ->
            WordProgress(
                entry = entry,
                stat = stats[entry.wordId]?.toDomain() ?: WordStat(wordId = entry.wordId),
            )
        }
    }

    override suspend fun loadStudyDeck(
        grade: SchoolGrade,
        count: Int,
        sourceBook: String?,
    ): List<WordProgress> = studyDeckPlanner.buildLearningDeck(loadWordProgress(grade, sourceBook), count)

    override suspend fun loadQuizDeck(
        grade: SchoolGrade,
        count: Int,
        sourceBook: String?,
    ): List<WordProgress> = studyDeckPlanner.buildQuizDeck(loadWordProgress(grade, sourceBook), count)

    override suspend fun loadReviewItems(
        grade: SchoolGrade,
        sourceBook: String?,
    ): List<ReviewItem> =
        loadWordProgress(grade, sourceBook)
            .mapNotNull { progress ->
                val now = System.currentTimeMillis()
                val reasons = buildList {
                    if (progress.stat.wrongCount >= 2) add(ReviewReason.MANY_WRONG)
                    if (
                        progress.stat.lastSolvedAt != null &&
                        now - progress.stat.lastSolvedAt >= StudyDeckPlanner.OLD_WORD_THRESHOLD_MS
                    ) {
                        add(ReviewReason.LONG_TIME_NO_SEE)
                    }
                    if (progress.stat.averageElapsedMs >= StudyDeckPlanner.SLOW_RESPONSE_THRESHOLD_MS) {
                        add(ReviewReason.SLOW_RESPONSE)
                    }
                    if (
                        progress.stat.needReview ||
                        (progress.stat.nextReviewAt ?: Long.MAX_VALUE) <= now
                    ) {
                        add(ReviewReason.EXPLICIT_REVIEW)
                    }
                }.distinct()

                if (reasons.isEmpty()) {
                    null
                } else {
                    ReviewItem(progress = progress, reasons = reasons)
                }
            }

    private fun List<WordEntry>.filterBySourceBook(sourceBook: String?): List<WordEntry> {
        val selectedBook = sourceBook?.trim().orEmpty()
        if (selectedBook.isBlank()) return this
        return filter { entry ->
            entry.sources.any { source -> source.book.trim() == selectedBook }
        }
    }

    override suspend fun recordLearningResult(
        wordId: Long,
        response: LearningResponse,
        elapsedMs: Long,
    ) {
        persistResult(
            wordId = wordId,
            quizType = QuizType.LEARNING_CARD,
            isCorrect = response.isCorrect,
            elapsedMs = elapsedMs,
            wrongWeight = if (response == LearningResponse.UNKNOWN) 2 else 1,
            response = response,
        )
    }

    override suspend fun recordQuizResult(
        wordId: Long,
        quizType: QuizType,
        isCorrect: Boolean,
        elapsedMs: Long,
    ) {
        persistResult(
            wordId = wordId,
            quizType = quizType,
            isCorrect = isCorrect,
            elapsedMs = elapsedMs,
            response = if (isCorrect) LearningResponse.KNOWN else LearningResponse.UNKNOWN,
        )
    }

    private suspend fun persistResult(
        wordId: Long,
        quizType: QuizType,
        isCorrect: Boolean,
        elapsedMs: Long,
        wrongWeight: Int = 1,
        response: LearningResponse,
    ) {
        val current = wordStatDao.getByWordId(wordId)
        val now = System.currentTimeMillis()
        val updated = if (current == null) {
            val memoryStrength = initialMemoryStrength(response)
            val nextReviewAt = calculateNextReviewAt(
                now = now,
                response = response,
                memoryStrength = memoryStrength,
            )
            WordStatEntity(
                wordId = wordId,
                totalSolvedCount = 1,
                correctCount = if (isCorrect) 1 else 0,
                wrongCount = if (isCorrect) 0 else wrongWeight,
                totalElapsedMs = elapsedMs,
                averageElapsedMs = elapsedMs,
                lastSolvedAt = now,
                needReview = shouldNeedReview(
                    isCorrect = isCorrect,
                    response = response,
                    averageElapsedMs = elapsedMs,
                    memoryStrength = memoryStrength,
                ),
                nextReviewAt = nextReviewAt,
                memoryStrength = memoryStrength,
                consecutiveCorrectCount = if (isCorrect) 1 else 0,
                lastLearningResponse = response.name,
            )
        } else {
            val totalSolvedCount = current.totalSolvedCount + 1
            val correctCount = current.correctCount + if (isCorrect) 1 else 0
            val wrongCount = current.wrongCount + if (isCorrect) 0 else wrongWeight
            val totalElapsed = current.totalElapsedMs + elapsedMs
            val averageElapsed = totalElapsed / totalSolvedCount
            val consecutiveCorrectCount = if (isCorrect) current.consecutiveCorrectCount + 1 else 0
            val memoryStrength = updatedMemoryStrength(current.memoryStrength, response)
            val nextReviewAt = calculateNextReviewAt(
                now = now,
                response = response,
                memoryStrength = memoryStrength,
            )
            WordStatEntity(
                wordId = wordId,
                totalSolvedCount = totalSolvedCount,
                correctCount = correctCount,
                wrongCount = wrongCount,
                totalElapsedMs = totalElapsed,
                averageElapsedMs = averageElapsed,
                lastSolvedAt = now,
                needReview = shouldNeedReview(
                    isCorrect = isCorrect,
                    response = response,
                    averageElapsedMs = averageElapsed,
                    memoryStrength = memoryStrength,
                ),
                nextReviewAt = nextReviewAt,
                memoryStrength = memoryStrength,
                consecutiveCorrectCount = consecutiveCorrectCount,
                lastLearningResponse = response.name,
            )
        }

        wordStatDao.upsert(updated)
        quizHistoryDao.insert(
            QuizHistoryEntity(
                wordId = wordId,
                quizType = quizType.name,
                isCorrect = isCorrect,
                elapsedMs = elapsedMs,
                solvedAt = now,
            ),
        )
    }

    private fun initialMemoryStrength(response: LearningResponse): Int =
        when (response) {
            LearningResponse.KNOWN -> 1
            LearningResponse.HESITANT -> 0
            LearningResponse.UNKNOWN -> 0
        }

    private fun updatedMemoryStrength(
        currentStrength: Int,
        response: LearningResponse,
    ): Int =
        when (response) {
            LearningResponse.KNOWN -> (currentStrength + 1).coerceAtMost(MAX_MEMORY_STRENGTH)
            LearningResponse.HESITANT -> currentStrength.coerceIn(0, MAX_MEMORY_STRENGTH)
            LearningResponse.UNKNOWN -> (currentStrength - 2).coerceAtLeast(0)
        }

    private fun calculateNextReviewAt(
        now: Long,
        response: LearningResponse,
        memoryStrength: Int,
    ): Long =
        now + when (response) {
            LearningResponse.UNKNOWN -> 30L * 60 * 1000
            LearningResponse.HESITANT -> 6L * 60 * 60 * 1000
            LearningResponse.KNOWN -> when (memoryStrength) {
                0 -> 6L * 60 * 60 * 1000
                1 -> 12L * 60 * 60 * 1000
                2 -> 1L * 24 * 60 * 60 * 1000
                3 -> 3L * 24 * 60 * 60 * 1000
                4 -> 7L * 24 * 60 * 60 * 1000
                else -> 14L * 24 * 60 * 60 * 1000
            }
        }

    private fun shouldNeedReview(
        isCorrect: Boolean,
        response: LearningResponse,
        averageElapsedMs: Long,
        memoryStrength: Int,
    ): Boolean =
        !isCorrect ||
            response != LearningResponse.KNOWN ||
            averageElapsedMs >= StudyDeckPlanner.SLOW_RESPONSE_THRESHOLD_MS ||
            memoryStrength <= 1

    private companion object {
        const val MAX_MEMORY_STRENGTH = 5
    }

    override suspend fun syncCatalog(
        selectedGrade: SchoolGrade?,
        forceSelectedGrade: Boolean,
    ): SyncSummary {
        val baseUrl = settingsRepository.getRemoteBaseUrl(BuildConfig.DEFAULT_STORAGE_BASE_URL).trim()
        if (baseUrl.isBlank()) {
            return SyncSummary(
                remoteConfigured = false,
                manifestVersion = null,
                updatedFiles = emptyList(),
            )
        }

        val bundledManifest = catalogFileStore.loadBundledManifest()
        val localSyncState = settingsRepository.getSyncState()
        val remoteManifest = runCatching { remoteCatalogService.fetchManifest(baseUrl) }
            .getOrElse { bundledManifest }

        val updatedFiles = mutableListOf<String>()
        var errorMessage: String? = null

        SchoolGrade.entries.forEach { grade ->
            val remoteVersion = remoteManifest.files[grade.fileKey] ?: return@forEach
            val localVersion = localSyncState.fileVersions[grade.fileKey] ?: 0
            val shouldDownload = remoteVersion > localVersion || (forceSelectedGrade && selectedGrade == grade)
            if (shouldDownload) {
                runCatching {
                    remoteCatalogService.downloadCatalog(baseUrl, grade)
                }.onSuccess { payload ->
                    catalogFileStore.validateCatalogPayload(payload)
                    catalogFileStore.saveCatalog(grade, payload)
                    updatedFiles += grade.fileKey
                }.onFailure { throwable ->
                    if (selectedGrade == grade && errorMessage == null) {
                        errorMessage = throwable.message ?: "선택한 학년 파일을 읽지 못했습니다."
                    }
                }
            }
        }

        val mergedVersions = localSyncState.fileVersions.toMutableMap().apply {
            updatedFiles.forEach { key ->
                remoteManifest.files[key]?.let { put(key, it) }
            }
        }
        settingsRepository.updateSyncState(
            manifestVersion = maxOf(localSyncState.manifestVersion, remoteManifest.version),
            fileVersions = mergedVersions,
        )

        return SyncSummary(
            remoteConfigured = true,
            manifestVersion = remoteManifest.version,
            updatedFiles = updatedFiles,
            errorMessage = errorMessage,
        )
    }
}
