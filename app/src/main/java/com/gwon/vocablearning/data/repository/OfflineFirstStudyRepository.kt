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
import com.gwon.vocablearning.domain.model.SyncSummary
import com.gwon.vocablearning.domain.model.WordEntry
import com.gwon.vocablearning.domain.model.WordProgress
import com.gwon.vocablearning.domain.model.WordStat

class OfflineFirstStudyRepository(
    private val settingsRepository: SettingsPreferencesRepository,
    private val catalogFileStore: CatalogFileStore,
    private val remoteCatalogService: RemoteCatalogService,
    private val wordStatDao: WordStatDao,
    private val quizHistoryDao: QuizHistoryDao,
) : StudyRepository {
    override suspend fun getSelectedGrade(): SchoolGrade =
        settingsRepository.getSelectedGrade()

    override suspend fun setSelectedGrade(grade: SchoolGrade) {
        settingsRepository.setSelectedGrade(grade)
    }

    override suspend fun loadWords(grade: SchoolGrade): List<WordEntry> =
        catalogFileStore.loadWordSet(grade).toDomain()

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
        }.sortedWith(
            compareByDescending<WordProgress> { it.stat.needReview }
                .thenByDescending { it.stat.wrongCount }
                .thenBy { it.entry.word },
        )
    }

    override suspend fun loadReviewItems(grade: SchoolGrade): List<ReviewItem> =
        loadWordProgress(grade)
            .mapNotNull { progress ->
                val reasons = buildList {
                    if (progress.stat.wrongCount >= 2) add(ReviewReason.MANY_WRONG)
                    if (progress.stat.averageElapsedMs >= SLOW_RESPONSE_THRESHOLD_MS) add(ReviewReason.SLOW_RESPONSE)
                    if (progress.stat.needReview) add(ReviewReason.EXPLICIT_REVIEW)
                }.distinct()

                if (reasons.isEmpty()) {
                    null
                } else {
                    ReviewItem(progress = progress, reasons = reasons)
                }
            }

    override suspend fun recordQuizResult(
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
                needReview = !isCorrect || elapsedMs >= SLOW_RESPONSE_THRESHOLD_MS,
            )
        } else {
            val totalSolvedCount = current.totalSolvedCount + 1
            val correctCount = current.correctCount + if (isCorrect) 1 else 0
            val wrongCount = current.wrongCount + if (isCorrect) 0 else 1
            val totalElapsed = current.totalElapsedMs + elapsedMs
            WordStatEntity(
                wordId = wordId,
                totalSolvedCount = totalSolvedCount,
                correctCount = correctCount,
                wrongCount = wrongCount,
                totalElapsedMs = totalElapsed,
                averageElapsedMs = totalElapsed / totalSolvedCount,
                lastSolvedAt = System.currentTimeMillis(),
                needReview = current.needReview || !isCorrect || elapsedMs >= SLOW_RESPONSE_THRESHOLD_MS,
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

    override suspend fun syncCatalog(): SyncSummary {
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

        SchoolGrade.entries.forEach { grade ->
            val remoteVersion = remoteManifest.files[grade.fileKey] ?: return@forEach
            val localVersion = localSyncState.fileVersions[grade.fileKey] ?: 0
            if (remoteVersion > localVersion) {
                runCatching {
                    remoteCatalogService.downloadCatalog(baseUrl, grade)
                }.onSuccess { payload ->
                    catalogFileStore.saveCatalog(grade, payload)
                    updatedFiles += grade.fileKey
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
        )
    }

    private companion object {
        const val SLOW_RESPONSE_THRESHOLD_MS = 8_000L
    }
}

