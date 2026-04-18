package com.gwon.vocablearning.domain.model

data class WordEntry(
    val wordId: Long,
    val language: Language,
    val grade: SchoolGrade,
    val word: String,
    val phonetic: String,
    val meanings: List<String>,
    val exampleSentence: String,
    val exampleTranslation: String,
    val wordAudioUrl: String,
    val exampleAudioUrl: String,
)

enum class QuizType(val label: String) {
    LEARNING_CARD("학습 카드"),
    WORD_TO_MEANING("단어 -> 뜻"),
    MEANING_TO_WORD("뜻 -> 단어"),
    SENTENCE_BLANK("예문 빈칸"),
}

enum class AudioType {
    WORD,
    EXAMPLE,
}

data class WordStat(
    val wordId: Long,
    val totalSolvedCount: Int = 0,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val totalElapsedMs: Long = 0,
    val averageElapsedMs: Long = 0,
    val lastSolvedAt: Long? = null,
    val needReview: Boolean = false,
)

data class WordProgress(
    val entry: WordEntry,
    val stat: WordStat,
)

enum class ReviewReason(val label: String) {
    MANY_WRONG("오답이 많음"),
    LONG_TIME_NO_SEE("오래 안 봄"),
    SLOW_RESPONSE("풀이 시간이 김"),
    EXPLICIT_REVIEW("복습 필요"),
}

data class ReviewItem(
    val progress: WordProgress,
    val reasons: List<ReviewReason>,
)

data class DashboardSnapshot(
    val totalWords: Int = 0,
    val solvedWords: Int = 0,
    val totalAttempts: Int = 0,
    val correctAnswers: Int = 0,
    val wrongAnswers: Int = 0,
    val averageElapsedMs: Long = 0,
    val reviewCount: Int = 0,
)

data class QuizQuestion(
    val wordId: Long,
    val type: QuizType,
    val prompt: String,
    val supportText: String,
    val options: List<String>,
    val answerIndex: Int,
    val explanation: String,
)

data class SyncSummary(
    val remoteConfigured: Boolean,
    val manifestVersion: Int?,
    val updatedFiles: List<String>,
    val errorMessage: String? = null,
)

data class SyncStatus(
    val manifestVersion: Int,
    val fileVersion: Int,
)
