package com.gwon.vocablearning.ui.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.gwon.vocablearning.app.AppContainer
import com.gwon.vocablearning.domain.model.AudioType
import com.gwon.vocablearning.domain.model.DashboardSnapshot
import com.gwon.vocablearning.domain.model.QuizQuestion
import com.gwon.vocablearning.domain.model.QuizType
import com.gwon.vocablearning.domain.model.ReviewItem
import com.gwon.vocablearning.domain.model.SchoolGrade
import com.gwon.vocablearning.domain.model.WordEntry
import com.gwon.vocablearning.domain.model.WordProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuizConfig(
    val type: QuizType = QuizType.WORD_TO_MEANING,
    val questionCount: Int = 10,
)

sealed interface QuizSessionState {
    data object Idle : QuizSessionState

    data class Running(
        val questions: List<QuizQuestion>,
        val currentIndex: Int,
        val answers: List<Boolean>,
        val questionStartedAtElapsed: Long,
    ) : QuizSessionState {
        val currentQuestion: QuizQuestion
            get() = questions[currentIndex]
    }

    data class Finished(
        val totalCount: Int,
        val correctCount: Int,
    ) : QuizSessionState
}

data class MainUiState(
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val selectedGrade: SchoolGrade = SchoolGrade.HIGH_3,
    val words: List<WordEntry> = emptyList(),
    val selectedWord: WordEntry? = null,
    val dashboard: DashboardSnapshot = DashboardSnapshot(),
    val wordProgress: List<WordProgress> = emptyList(),
    val reviewItems: List<ReviewItem> = emptyList(),
    val quizConfig: QuizConfig = QuizConfig(),
    val quizSession: QuizSessionState = QuizSessionState.Idle,
    val noticeMessage: String? = null,
)

class MainViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val repository = container.studyRepository
    private val quizFactory = container.quizFactory
    private val audioPlayer = container.audioPlayer
    private val audioCacheManager = container.audioCacheManager

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val grade = repository.getSelectedGrade()
            refreshAll(grade)
            syncCatalog(manual = false)
        }
    }

    fun selectGrade(grade: SchoolGrade) {
        viewModelScope.launch {
            repository.setSelectedGrade(grade)
            refreshAll(grade)
        }
    }

    fun selectWord(word: WordEntry) {
        _uiState.update { it.copy(selectedWord = word) }
    }

    fun updateQuizType(type: QuizType) {
        _uiState.update {
            it.copy(
                quizConfig = it.quizConfig.copy(type = type),
                quizSession = QuizSessionState.Idle,
            )
        }
    }

    fun updateQuestionCount(count: Int) {
        _uiState.update {
            it.copy(
                quizConfig = it.quizConfig.copy(questionCount = count),
                quizSession = QuizSessionState.Idle,
            )
        }
    }

    fun startQuiz() {
        val words = _uiState.value.words
        val quizConfig = _uiState.value.quizConfig
        val questions = quizFactory.createQuestions(
            words = words,
            count = quizConfig.questionCount,
            type = quizConfig.type,
        )
        _uiState.update {
            it.copy(
                quizSession = if (questions.isEmpty()) {
                    QuizSessionState.Idle
                } else {
                    QuizSessionState.Running(
                        questions = questions,
                        currentIndex = 0,
                        answers = emptyList(),
                        questionStartedAtElapsed = SystemClock.elapsedRealtime(),
                    )
                },
                noticeMessage = if (questions.isEmpty()) "표시할 단어가 없습니다." else null,
            )
        }
    }

    fun submitAnswer(optionIndex: Int) {
        val running = _uiState.value.quizSession as? QuizSessionState.Running ?: return
        val question = running.currentQuestion
        val isCorrect = optionIndex == question.answerIndex
        val elapsedMs = SystemClock.elapsedRealtime() - running.questionStartedAtElapsed

        viewModelScope.launch {
            repository.recordQuizResult(
                wordId = question.wordId,
                quizType = question.type,
                isCorrect = isCorrect,
                elapsedMs = elapsedMs,
            )

            val answers = running.answers + isCorrect
            val nextIndex = running.currentIndex + 1
            if (nextIndex >= running.questions.size) {
                _uiState.update {
                    it.copy(
                        quizSession = QuizSessionState.Finished(
                            totalCount = answers.size,
                            correctCount = answers.count { result -> result },
                        ),
                        noticeMessage = if (isCorrect) "정답입니다." else "오답입니다.",
                    )
                }
                refreshCurrentGrade()
            } else {
                _uiState.update {
                    it.copy(
                        quizSession = QuizSessionState.Running(
                            questions = running.questions,
                            currentIndex = nextIndex,
                            answers = answers,
                            questionStartedAtElapsed = SystemClock.elapsedRealtime(),
                        ),
                        noticeMessage = if (isCorrect) "정답입니다." else "오답입니다.",
                    )
                }
            }
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

    fun syncCatalog(manual: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            val summary = repository.syncCatalog()
            refreshCurrentGrade()
            _uiState.update { state ->
                state.copy(
                    isSyncing = false,
                    noticeMessage = when {
                        !summary.remoteConfigured && manual -> "원격 저장소 URL이 아직 설정되지 않았습니다."
                        !summary.remoteConfigured -> null
                        summary.updatedFiles.isEmpty() -> "최신 데이터입니다."
                        else -> "${summary.updatedFiles.size}개 데이터 파일을 업데이트했습니다."
                    },
                )
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

    private suspend fun refreshCurrentGrade() {
        refreshAll(_uiState.value.selectedGrade)
    }

    private suspend fun refreshAll(grade: SchoolGrade) {
        val words = repository.loadWords(grade)
        val wordProgress = repository.loadWordProgress(grade)
        val dashboard = repository.loadDashboard(grade)
        val reviewItems = repository.loadReviewItems(grade)
        val selectedWordId = _uiState.value.selectedWord?.wordId

        _uiState.update {
            it.copy(
                isLoading = false,
                selectedGrade = grade,
                words = words,
                selectedWord = words.firstOrNull { entry -> entry.wordId == selectedWordId } ?: words.firstOrNull(),
                dashboard = dashboard,
                wordProgress = wordProgress,
                reviewItems = reviewItems,
            )
        }
    }

    companion object {
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
