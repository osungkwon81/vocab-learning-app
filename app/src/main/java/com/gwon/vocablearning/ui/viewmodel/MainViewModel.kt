package com.gwon.vocablearning.ui.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.gwon.vocablearning.app.AppContainer
import com.gwon.vocablearning.domain.model.AudioType
import com.gwon.vocablearning.domain.model.DashboardSnapshot
import com.gwon.vocablearning.domain.model.LearningResponse
import com.gwon.vocablearning.domain.model.QuizQuestion
import com.gwon.vocablearning.domain.model.QuizType
import com.gwon.vocablearning.domain.model.ReviewItem
import com.gwon.vocablearning.domain.model.ReviewReason
import com.gwon.vocablearning.domain.model.SchoolGrade
import com.gwon.vocablearning.domain.model.WordEntry
import com.gwon.vocablearning.domain.model.WordProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SetupStep {
    NICKNAME,
    GRADE,
    READY,
}

data class LearningCardProgressState(
    val isRevealed: Boolean = false,
    val response: LearningResponse? = null,
    val startedAtElapsed: Long = 0L,
)

data class LearningDeckItem(
    val id: Int,
    val progress: WordProgress,
)

data class LearningSessionState(
    val deck: List<LearningDeckItem> = emptyList(),
    val cards: Map<Int, LearningCardProgressState> = emptyMap(),
    val currentPage: Int = 0,
    val targetCount: Int = 20,
) {
    val currentItem: LearningDeckItem?
        get() = deck.getOrNull(currentPage)

    val currentCardState: LearningCardProgressState?
        get() = currentItem?.let { cards[it.id] }

    val completedCount: Int
        get() = cards.values.count { it.response != null }

    val knownCount: Int
        get() = cards.values.count { it.response == LearningResponse.KNOWN }

    val needsPracticeCount: Int
        get() = cards.values.count { it.response == LearningResponse.HESITANT || it.response == LearningResponse.UNKNOWN }

    val isComplete: Boolean
        get() = deck.isNotEmpty() && completedCount == deck.size
}

data class QuizSessionState(
    val questions: List<QuizQuestion> = emptyList(),
    val answers: Map<Int, Int> = emptyMap(),
    val currentIndex: Int = 0,
    val targetCount: Int = 20,
    val startedAtElapsed: Long = 0L,
) {
    val currentQuestion: QuizQuestion?
        get() = if (isComplete) null else questions.getOrNull(currentIndex)

    val completedCount: Int
        get() = answers.size

    val correctCount: Int
        get() = answers.count { (index, selectedIndex) ->
            questions.getOrNull(index)?.answerIndex == selectedIndex
        }

    val wrongCount: Int
        get() = completedCount - correctCount

    val isComplete: Boolean
        get() = questions.isNotEmpty() && answers.size == questions.size
}

data class MainUiState(
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val setupStep: SetupStep = SetupStep.NICKNAME,
    val nicknameInput: String = "",
    val nickname: String = "",
    val selectedGrade: SchoolGrade = SchoolGrade.HIGH_3,
    val learningCount: Int = 20,
    val quizCount: Int = 20,
    val showStudyStartDialog: Boolean = false,
    val showQuizStartDialog: Boolean = false,
    val deletedWordProgress: List<WordProgress> = emptyList(),
    val sourceBookOptions: List<String> = emptyList(),
    val selectedSourceBook: String? = null,
    val hasCatalogUpdate: Boolean = false,
    val catalogUpdateMessage: String? = null,
    val dashboard: DashboardSnapshot = DashboardSnapshot(),
    val wordProgress: List<WordProgress> = emptyList(),
    val reviewItems: List<ReviewItem> = emptyList(),
    val learningSession: LearningSessionState? = null,
    val quizSession: QuizSessionState? = null,
    val noticeMessage: String? = null,
)

class MainViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val repository = container.studyRepository
    private val audioPlayer = container.audioPlayer
    private val audioCacheManager = container.audioCacheManager
    private val quizFactory = container.quizFactory

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val nickname = repository.getNickname()
            val grade = repository.getSelectedGrade()
            val learningCount = repository.getLearningCount()
            val hasCompletedOnboarding = repository.hasCompletedOnboarding()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    setupStep = when {
                        hasCompletedOnboarding -> SetupStep.READY
                        nickname.isBlank() -> SetupStep.NICKNAME
                        else -> SetupStep.GRADE
                    },
                    nicknameInput = nickname,
                    nickname = nickname,
                    selectedGrade = grade,
                    learningCount = learningCount,
                    quizCount = learningCount,
                )
            }

            if (hasCompletedOnboarding) {
                refreshReferenceData(grade)
                checkCatalogUpdate(grade)
            }
        }
    }

    fun updateNicknameInput(value: String) {
        _uiState.update { it.copy(nicknameInput = value.take(20)) }
    }

    fun confirmNickname() {
        val nickname = _uiState.value.nicknameInput.trim()
        if (nickname.isBlank()) {
            _uiState.update { it.copy(noticeMessage = "닉네임을 입력해 주세요.") }
            return
        }

        viewModelScope.launch {
            repository.setNickname(nickname)
            _uiState.update {
                it.copy(
                    nickname = nickname,
                    setupStep = SetupStep.GRADE,
                    noticeMessage = null,
                )
            }
        }
    }

    fun saveNickname() {
        val nickname = _uiState.value.nicknameInput.trim()
        if (nickname.isBlank()) {
            _uiState.update { it.copy(noticeMessage = "닉네임을 입력해 주세요.") }
            return
        }

        viewModelScope.launch {
            repository.setNickname(nickname)
            _uiState.update {
                it.copy(
                    nickname = nickname,
                    noticeMessage = "닉네임을 저장했습니다.",
                )
            }
        }
    }

    fun selectGrade(grade: SchoolGrade) {
        _uiState.update { it.copy(selectedGrade = grade, selectedSourceBook = null) }

        viewModelScope.launch {
            repository.setSelectedGrade(grade)
            if (_uiState.value.setupStep == SetupStep.READY) {
                refreshReferenceData(grade)
                checkCatalogUpdate(grade)
                _uiState.update {
                    it.copy(
                        learningSession = null,
                        quizSession = null,
                        noticeMessage = "학년이 변경되었습니다. 학습이나 퀴즈를 다시 시작해 주세요.",
                    )
                }
            }
        }
    }

    fun completeGradeSetup() {
        viewModelScope.launch {
            repository.setSelectedGrade(_uiState.value.selectedGrade)
            repository.setOnboardingCompleted(true)
            _uiState.update { it.copy(setupStep = SetupStep.READY) }
            refreshReferenceData(_uiState.value.selectedGrade)
            checkCatalogUpdate(_uiState.value.selectedGrade)
        }
    }

    fun updateLearningCount(count: Int) {
        _uiState.update { it.copy(learningCount = count.coerceIn(1, 999)) }
    }

    fun selectSourceBook(sourceBook: String?) {
        _uiState.update {
            it.copy(
                selectedSourceBook = sourceBook?.takeIf { book -> book.isNotBlank() },
                learningSession = null,
                quizSession = null,
            )
        }

        viewModelScope.launch {
            refreshReferenceData(_uiState.value.selectedGrade)
        }
    }

    fun adjustLearningCount(delta: Int) {
        updateLearningCount(_uiState.value.learningCount + delta)
    }

    fun openStudyStartDialog() {
        _uiState.update { it.copy(showStudyStartDialog = true) }
    }

    fun closeStudyStartDialog() {
        _uiState.update { it.copy(showStudyStartDialog = false) }
    }

    fun updateQuizCount(count: Int) {
        _uiState.update { it.copy(quizCount = count.coerceIn(1, 999)) }
    }

    fun adjustQuizCount(delta: Int) {
        updateQuizCount(_uiState.value.quizCount + delta)
    }

    fun openQuizStartDialog() {
        _uiState.update { it.copy(showQuizStartDialog = true) }
    }

    fun closeQuizStartDialog() {
        _uiState.update { it.copy(showQuizStartDialog = false) }
    }

    fun startLearningSession() {
        viewModelScope.launch {
            repository.setLearningCount(_uiState.value.learningCount)
            buildLearningSession()
            _uiState.update {
                it.copy(
                    showStudyStartDialog = false,
                    noticeMessage = null,
                )
            }
        }
    }

    fun startQuizSession() {
        viewModelScope.launch {
            buildQuizSession()
            _uiState.update {
                it.copy(
                    showQuizStartDialog = false,
                    noticeMessage = null,
                )
            }
        }
    }

    fun clearLearningSession() {
        _uiState.update { it.copy(learningSession = null) }
    }

    fun clearQuizSession() {
        _uiState.update { it.copy(quizSession = null) }
    }

    fun onLearningPageChanged(page: Int) {
        val session = _uiState.value.learningSession ?: return
        if (page !in session.deck.indices) return

        val currentItemId = session.deck[page].id
        val currentCard = session.cards[currentItemId] ?: return
        if (page == session.currentPage && currentCard.startedAtElapsed != 0L) return

        _uiState.update {
            val updatedCards = it.learningSession?.cards.orEmpty().toMutableMap().apply {
                this[currentItemId] = currentCard.copy(
                    startedAtElapsed = currentCard.startedAtElapsed.takeIf { started -> started > 0L }
                        ?: SystemClock.elapsedRealtime(),
                )
            }
            it.copy(
                learningSession = it.learningSession?.copy(
                    currentPage = page,
                    cards = updatedCards,
                ),
            )
        }
    }

    fun revealCurrentCard() {
        val session = _uiState.value.learningSession ?: return
        val itemId = session.currentItem?.id ?: return
        val currentCard = session.cards[itemId] ?: return
        if (currentCard.isRevealed) return

        _uiState.update {
            val updatedCards = it.learningSession?.cards.orEmpty().toMutableMap().apply {
                this[itemId] = currentCard.copy(isRevealed = true)
            }
            it.copy(
                learningSession = it.learningSession?.copy(cards = updatedCards),
            )
        }
    }

    fun hideCurrentCardDetails() {
        val session = _uiState.value.learningSession ?: return
        val itemId = session.currentItem?.id ?: return
        val currentCard = session.cards[itemId] ?: return
        if (!currentCard.isRevealed || currentCard.response != null) return

        _uiState.update {
            val updatedCards = it.learningSession?.cards.orEmpty().toMutableMap().apply {
                this[itemId] = currentCard.copy(isRevealed = false)
            }
            it.copy(
                learningSession = it.learningSession?.copy(cards = updatedCards),
            )
        }
    }

    fun answerCurrentCard(response: LearningResponse) {
        val session = _uiState.value.learningSession ?: return
        val item = session.currentItem ?: return
        val progress = item.progress
        val currentCard = session.currentCardState ?: return
        if (!currentCard.isRevealed || currentCard.response != null) return

        val elapsedMs = (SystemClock.elapsedRealtime() - currentCard.startedAtElapsed).coerceAtLeast(500L)

        viewModelScope.launch {
            repository.recordLearningResult(
                wordId = progress.entry.wordId,
                response = response,
                elapsedMs = elapsedMs,
            )

            _uiState.update {
                val updatedCards = it.learningSession?.cards.orEmpty().toMutableMap().apply {
                    this[item.id] = currentCard.copy(response = response)
                }
                val updatedSession = it.learningSession
                    ?.copy(cards = updatedCards)
                    ?.let { session ->
                        if (response == LearningResponse.KNOWN || session.deck.count { deckItem ->
                                deckItem.progress.entry.wordId == progress.entry.wordId
                            } >= MAX_SAME_SESSION_OCCURRENCES
                        ) {
                            session
                        } else {
                            session.scheduleRetryAfterCurrentPage(item.progress)
                        }
                    }
                it.copy(
                    learningSession = updatedSession,
                    noticeMessage = if (updatedSession?.isComplete == true) "학습 세션이 끝났습니다." else null,
                )
            }

            refreshReferenceData(_uiState.value.selectedGrade)
        }
    }

    private fun LearningSessionState.scheduleRetryAfterCurrentPage(progress: WordProgress): LearningSessionState {
        val retryItem = LearningDeckItem(
            id = (deck.maxOfOrNull { it.id } ?: -1) + 1,
            progress = progress,
        )
        val insertIndex = (currentPage + RETRY_GAP + 1).coerceAtMost(deck.size)
        val updatedDeck = deck.toMutableList().apply {
            add(insertIndex, retryItem)
        }
        return copy(
            deck = updatedDeck,
            cards = cards + (retryItem.id to LearningCardProgressState()),
        )
    }

    fun answerCurrentQuizQuestion(selectedIndex: Int) {
        val session = _uiState.value.quizSession ?: return
        val question = session.currentQuestion ?: return
        if (session.answers.containsKey(session.currentIndex)) return

        val elapsedMs = (SystemClock.elapsedRealtime() - session.startedAtElapsed).coerceAtLeast(500L)
        val isCorrect = selectedIndex == question.answerIndex

        viewModelScope.launch {
            repository.recordQuizResult(
                wordId = question.wordId,
                quizType = question.type,
                isCorrect = isCorrect,
                elapsedMs = elapsedMs,
            )

            val nextIndex = session.currentIndex + 1
            val updatedAnswers = session.answers + (session.currentIndex to selectedIndex)

            _uiState.update {
                val questionCount = session.questions.size
                val isComplete = updatedAnswers.size == questionCount
                it.copy(
                    quizSession = session.copy(
                        answers = updatedAnswers,
                        currentIndex = if (isComplete) questionCount else nextIndex,
                        startedAtElapsed = if (isComplete) 0L else SystemClock.elapsedRealtime(),
                    ),
                    noticeMessage = if (isComplete) "퀴즈가 끝났습니다." else null,
                )
            }

            refreshReferenceData(_uiState.value.selectedGrade)
        }
    }

    fun playAudio(wordEntry: WordEntry, audioType: AudioType) {
        viewModelScope.launch {
            val source = audioCacheManager.prepare(wordEntry, audioType)
            if (source == null) {
                _uiState.update { it.copy(noticeMessage = "재생할 오디오가 없습니다.") }
                return@launch
            }

            runCatching {
                audioPlayer.play(source)
            }.onFailure {
                _uiState.update { state ->
                    state.copy(noticeMessage = "오디오 재생에 실패했습니다.")
                }
            }
        }
    }

    fun hideWord(word: WordEntry) {
        viewModelScope.launch {
            repository.hideWord(word.wordId)
            refreshReferenceData(_uiState.value.selectedGrade)
            _uiState.update {
                it.copy(
                    learningSession = null,
                    quizSession = null,
                    noticeMessage = "'${word.word}' 단어를 삭제 목록으로 보냈습니다.",
                )
            }
        }
    }

    fun restoreWord(word: WordEntry) {
        viewModelScope.launch {
            repository.restoreWord(word.wordId)
            refreshReferenceData(_uiState.value.selectedGrade)
            _uiState.update {
                it.copy(noticeMessage = "'${word.word}' 단어를 복원했습니다.")
            }
        }
    }

    fun syncCatalog(manual: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            runCatching {
                val selectedGrade = _uiState.value.selectedGrade
                val summary = repository.syncCatalog(
                    selectedGrade = selectedGrade,
                    forceSelectedGrade = manual,
                )
                if (_uiState.value.setupStep == SetupStep.READY && (manual || summary.updatedFiles.isNotEmpty())) {
                    refreshReferenceData(selectedGrade)
                }
                val syncStatus = repository.getSyncStatus(selectedGrade)
                val currentWordCount = _uiState.value.wordProgress.size
                _uiState.update { state ->
                    state.copy(
                        hasCatalogUpdate = summary.pendingFiles.isNotEmpty() && summary.updatedFiles.isEmpty(),
                        catalogUpdateMessage = summary.pendingFiles
                            .takeIf { it.isNotEmpty() && summary.updatedFiles.isEmpty() }
                            ?.let { buildCatalogUpdateMessage(selectedGrade, it.size) },
                        noticeMessage = when {
                            !summary.remoteConfigured && manual -> "아직 연결된 원격 저장소 주소가 없습니다."
                            !summary.remoteConfigured -> null
                            summary.errorMessage != null -> buildSyncFailureMessage(summary.errorMessage)
                            manual -> buildManualSyncMessage(
                                grade = selectedGrade,
                                wordCount = currentWordCount,
                                manifestVersion = syncStatus.manifestVersion,
                                fileVersion = syncStatus.fileVersion,
                            )
                            summary.updatedFiles.isEmpty() -> "최신 단어장입니다."
                            else -> "새 단어장을 받아서 반영했습니다."
                        },
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        noticeMessage = buildSyncFailureMessage(throwable.message),
                    )
                }
            }
            _uiState.update { it.copy(isSyncing = false) }
        }
    }

    private fun checkCatalogUpdate(grade: SchoolGrade) {
        viewModelScope.launch {
            runCatching {
                repository.checkCatalogUpdate(grade)
            }.onSuccess { summary ->
                _uiState.update {
                    it.copy(
                        hasCatalogUpdate = summary.pendingFiles.isNotEmpty(),
                        catalogUpdateMessage = summary.pendingFiles
                            .takeIf { files -> files.isNotEmpty() }
                            ?.let { buildCatalogUpdateMessage(grade, it.size) },
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        hasCatalogUpdate = false,
                        catalogUpdateMessage = null,
                    )
                }
            }
        }
    }

    fun dismissNotice() {
        _uiState.update { it.copy(noticeMessage = null) }
    }

    override fun onCleared() {
        audioPlayer.release()
        super.onCleared()
    }

    private suspend fun buildLearningSession() {
        val grade = _uiState.value.selectedGrade
        val targetCount = _uiState.value.learningCount
        val sourceBook = _uiState.value.selectedSourceBook
        val deck = repository.loadStudyDeck(grade, targetCount, sourceBook).mapIndexed { index, progress ->
            LearningDeckItem(
                id = index,
                progress = progress,
            )
        }
        val now = SystemClock.elapsedRealtime()
        val initialCards = deck.associate { item ->
            item.id to LearningCardProgressState(
                startedAtElapsed = if (item == deck.firstOrNull()) now else 0L,
            )
        }

        _uiState.update {
            it.copy(
                learningSession = LearningSessionState(
                    deck = deck,
                    cards = initialCards,
                    currentPage = 0,
                    targetCount = targetCount,
                ),
            )
        }
    }

    private suspend fun buildQuizSession() {
        val grade = _uiState.value.selectedGrade
        val targetCount = _uiState.value.quizCount
        val sourceBook = _uiState.value.selectedSourceBook
        val deck = repository.loadQuizDeck(grade, targetCount, sourceBook)
        val questions = quizFactory.createQuestions(
            progress = deck,
            count = targetCount,
        )

        _uiState.update {
            it.copy(
                quizSession = QuizSessionState(
                    questions = questions,
                    currentIndex = 0,
                    targetCount = targetCount,
                    startedAtElapsed = SystemClock.elapsedRealtime(),
                ),
            )
        }
    }

    private fun buildManualSyncMessage(
        grade: SchoolGrade,
        wordCount: Int,
        manifestVersion: Int,
        fileVersion: Int,
    ): String = buildString {
        append("${grade.label} 단어장을 확인했어요.")
        append('\n')
        append("저장된 단어 ${wordCount}개 · 전체 버전 ${manifestVersion} · 단어장 버전 ${fileVersion}")
    }

    private fun buildCatalogUpdateMessage(
        grade: SchoolGrade,
        pendingCount: Int,
    ): String = when {
        pendingCount <= 1 -> "${grade.label} 단어장 업데이트가 있습니다."
        else -> "새 단어장 업데이트 ${pendingCount}건이 있습니다."
    }

    private fun buildSyncFailureMessage(rawMessage: String?): String {
        val detail = rawMessage
            ?.substringBefore('\n')
            ?.replace("HTTP ", "서버 응답 ")
            ?.takeIf { it.isNotBlank() }
            ?: "알 수 없는 오류"
        return "단어장을 불러오지 못했어요.\n$detail"
    }

    private suspend fun refreshReferenceData(grade: SchoolGrade) {
        val allVisibleProgress = repository.loadWordProgress(grade)
        val sourceBookOptions = allVisibleProgress.map { it.entry }.sourceBookOptions()
        val selectedSourceBook = _uiState.value.selectedSourceBook
            ?.takeIf { it in sourceBookOptions }
        val wordProgress = allVisibleProgress.filterBySourceBook(selectedSourceBook)
        val dashboard = wordProgress.toDashboardSnapshot()
        val reviewItems = wordProgress.toReviewItems()
        val deletedWordProgress = repository.loadDeletedWordProgress(grade)

        _uiState.update {
            it.copy(
                deletedWordProgress = deletedWordProgress,
                sourceBookOptions = sourceBookOptions,
                selectedSourceBook = selectedSourceBook,
                dashboard = dashboard,
                wordProgress = wordProgress,
                reviewItems = reviewItems,
            )
        }
    }

    private fun List<WordEntry>.sourceBookOptions(): List<String> =
        flatMap { entry -> entry.sources.map { source -> source.book.trim() } }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

    private fun List<WordProgress>.filterBySourceBook(sourceBook: String?): List<WordProgress> {
        val selectedBook = sourceBook?.trim().orEmpty()
        if (selectedBook.isBlank()) return this
        return filter { progress ->
            progress.entry.sources.any { source -> source.book.trim() == selectedBook }
        }
    }

    private fun List<WordProgress>.toDashboardSnapshot(): DashboardSnapshot {
        val now = System.currentTimeMillis()
        val stats = map { it.stat }
        val totalAttempts = stats.sumOf { it.totalSolvedCount }
        val totalElapsed = stats.sumOf { it.totalElapsedMs }

        return DashboardSnapshot(
            totalWords = size,
            solvedWords = count { it.stat.totalSolvedCount > 0 },
            totalAttempts = totalAttempts,
            correctAnswers = stats.sumOf { it.correctCount },
            wrongAnswers = stats.sumOf { it.wrongCount },
            averageElapsedMs = if (totalAttempts == 0) 0 else totalElapsed / totalAttempts,
            reviewCount = count { item ->
                item.stat.totalSolvedCount > 0 &&
                    (item.stat.needReview || (item.stat.nextReviewAt ?: Long.MAX_VALUE) <= now)
            },
        )
    }

    private fun List<WordProgress>.toReviewItems(): List<ReviewItem> {
        val now = System.currentTimeMillis()
        return mapNotNull { progress ->
            val reasons = buildList {
                if (progress.stat.wrongCount >= 2) add(ReviewReason.MANY_WRONG)
                if (
                    progress.stat.lastSolvedAt != null &&
                    now - progress.stat.lastSolvedAt >= com.gwon.vocablearning.domain.service.StudyDeckPlanner.OLD_WORD_THRESHOLD_MS
                ) {
                    add(ReviewReason.LONG_TIME_NO_SEE)
                }
                if (progress.stat.averageElapsedMs >= com.gwon.vocablearning.domain.service.StudyDeckPlanner.SLOW_RESPONSE_THRESHOLD_MS) {
                    add(ReviewReason.SLOW_RESPONSE)
                }
                if (
                    progress.stat.needReview ||
                    (progress.stat.nextReviewAt ?: Long.MAX_VALUE) <= now
                ) {
                    add(ReviewReason.EXPLICIT_REVIEW)
                }
            }.distinct()

            if (reasons.isEmpty()) null else ReviewItem(progress = progress, reasons = reasons)
        }
    }

    companion object {
        private const val RETRY_GAP = 3
        private const val MAX_SAME_SESSION_OCCURRENCES = 2

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T {
                    @Suppress("UNCHECKED_CAST")
                    return MainViewModel(container) as T
                }
            }
    }
}
