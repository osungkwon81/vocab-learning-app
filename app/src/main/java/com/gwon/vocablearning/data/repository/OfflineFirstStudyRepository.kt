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

    override suspend fun loadDashboard(grade: SchoolGrade): DashboardSnapshot {
        val progress = loadWordProgress(grade)
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
            reviewCount = progress.count { it.stat.needReview },
        )
    }

    override suspend fun loadWordProgress(grade: SchoolGrade): List<WordProgress> {
        val words = loadWords(grade)
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
    ): List<WordProgress> = studyDeckPlanner.prioritize(loadWordProgress(grade), count)

    override suspend fun loadReviewItems(grade: SchoolGrade): List<ReviewItem> =
        loadWordProgress(grade)
            .mapNotNull { progress ->
                val reasons = buildList {
                    if (progress.stat.wrongCount >= 2) add(ReviewReason.MANY_WRONG)
                    if (
                        progress.stat.lastSolvedAt != null &&
                        System.currentTimeMillis() - progress.stat.lastSolvedAt >= StudyDeckPlanner.OLD_WORD_THRESHOLD_MS
                    ) {
                        add(ReviewReason.LONG_TIME_NO_SEE)
                    }
                    if (progress.stat.averageElapsedMs >= StudyDeckPlanner.SLOW_RESPONSE_THRESHOLD_MS) {
                        add(ReviewReason.SLOW_RESPONSE)
                    }
                    if (progress.stat.needReview) add(ReviewReason.EXPLICIT_REVIEW)
                }.distinct()

                if (reasons.isEmpty()) {
                    null
                } else {
                    ReviewItem(progress = progress, reasons = reasons)
                }
            }

    override suspend fun recordLearningResult(
        wordId: Long,
        knewIt: Boolean,
        elapsedMs: Long,
    ) {
        persistResult(
            wordId = wordId,
            quizType = QuizType.LEARNING_CARD,
            isCorrect = knewIt,
            elapsedMs = elapsedMs,
        )
    }

    override suspend fun recordQuizResult(
        wordId: Long,
        quizType: QuizType,
        isCorrect: Boolean,
        elapsedMs: Long,
    ) {
        persistResult(wordId, quizType, isCorrect, elapsedMs)
    }

    private suspend fun persistResult(
        wordId: Long,
        quizType: QuizType,
        isCorrect: Boolean,
        elapsedMs: Long,
    ) {
        val current = wordStatDao.getByWordId(wordId)
        val updated = if (current == null) {
            WordStatEntity(
                wordId = wordId,
                totalSolvedCount = 1,
                correctCount = if (isCorrect) 1 else 0,
                wrongCount = if (isCorrect) 0 else 1,
                totalElapsedMs = elapsedMs,
                averageElapsedMs = elapsedMs,
                lastSolvedAt = System.currentTimeMillis(),
                needReview = !isCorrect || elapsedMs >= StudyDeckPlanner.SLOW_RESPONSE_THRESHOLD_MS,
            )
        } else {
            val totalSolvedCount = current.totalSolvedCount + 1
            val correctCount = current.correctCount + if (isCorrect) 1 else 0
            val wrongCount = current.wrongCount + if (isCorrect) 0 else 1
            val totalElapsed = current.totalElapsedMs + elapsedMs
            val averageElapsed = totalElapsed / totalSolvedCount
            WordStatEntity(
                wordId = wordId,
                totalSolvedCount = totalSolvedCount,
                correctCount = correctCount,
                wrongCount = wrongCount,
                totalElapsedMs = totalElapsed,
                averageElapsedMs = averageElapsed,
                lastSolvedAt = System.currentTimeMillis(),
                needReview = wrongCount > 0 || averageElapsed >= StudyDeckPlanner.SLOW_RESPONSE_THRESHOLD_MS,
            )
        }

        wordStatDao.upsert(updated)
        quizHistoryDao.insert(
            QuizHistoryEntity(
                wordId = wordId,
                quizType = quizType.name,
                isCorrect = isCorrect,
                elapsedMs = elapsedMs,
                solvedAt = System.currentTimeMillis(),
            ),
        )
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
