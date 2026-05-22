package com.gwon.vocablearning.data.repository

import com.gwon.vocablearning.domain.model.Language
import com.gwon.vocablearning.domain.model.LearningResponse
import com.gwon.vocablearning.domain.model.SchoolGrade
import com.gwon.vocablearning.domain.model.WordEntry
import com.gwon.vocablearning.domain.model.WordProgress
import com.gwon.vocablearning.domain.model.WordStat
import com.gwon.vocablearning.domain.service.StudyDeckPlanner
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyDeckPlannerTest {
    private val planner = StudyDeckPlanner()
    private val now = 1_000_000L

    @Test
    fun wrongAndSlowWordsArePrioritizedBeforeOldWords() {
        val ordered = planner.prioritize(
            progress = listOf(
                progress(wordId = 1, word = "general", totalSolvedCount = 1, lastSolvedAt = now),
                progress(wordId = 2, word = "old", totalSolvedCount = 1, lastSolvedAt = now - StudyDeckPlanner.OLD_WORD_THRESHOLD_MS - 1),
                progress(wordId = 3, word = "slow", totalSolvedCount = 1, averageElapsedMs = StudyDeckPlanner.SLOW_RESPONSE_THRESHOLD_MS + 1, lastSolvedAt = now),
                progress(wordId = 4, word = "wrong", wrongCount = 2),
            ),
            count = 4,
            nowMillis = now,
        )

        assertEquals(listOf("wrong", "slow", "old", "general"), ordered.map { it.entry.word })
    }

    @Test
    fun unseenWordsArePrioritizedBeforeReviewedWords() {
        val ordered = planner.prioritize(
            progress = listOf(
                progress(
                    wordId = 1,
                    word = "review",
                    wrongCount = 2,
                    correctCount = 1,
                    needReview = true,
                    nextReviewAt = now - 1,
                ),
                progress(wordId = 2, word = "unseen"),
                progress(wordId = 3, word = "known", correctCount = 1, lastSolvedAt = now),
            ),
            count = 3,
            nowMillis = now,
        )

        assertEquals(listOf("unseen", "review", "known"), ordered.map { it.entry.word })
    }

    @Test
    fun learningDeckMixesUnseenAndDueWords() {
        val ordered = planner.buildLearningDeck(
            progress = listOf(
                progress(wordId = 1, word = "new-1"),
                progress(wordId = 2, word = "new-2"),
                progress(wordId = 3, word = "due-1", totalSolvedCount = 1, nextReviewAt = now - 1, memoryStrength = 1),
                progress(wordId = 4, word = "due-2", totalSolvedCount = 2, nextReviewAt = now - 2, memoryStrength = 2),
                progress(wordId = 5, word = "weak", totalSolvedCount = 1, memoryStrength = 1, needReview = true),
            ),
            count = 4,
            nowMillis = now,
        )

        assertEquals(4, ordered.size)
        assertEquals(listOf("new-1", "due-2", "due-1", "weak"), ordered.map { it.entry.word })
    }

    @Test
    fun correctAnswersPushWordsBelowNewWords() {
        val ordered = planner.prioritize(
            progress = listOf(
                progress(wordId = 1, word = "known", correctCount = 1, lastSolvedAt = now),
                progress(wordId = 2, word = "new"),
                progress(wordId = 3, word = "mastered", correctCount = 3, lastSolvedAt = now),
            ),
            count = 3,
            nowMillis = now,
        )

        assertEquals(listOf("new", "known", "mastered"), ordered.map { it.entry.word })
    }

    @Test
    fun quizWrongRaisesAndLaterCorrectAnswersLowerPriority() {
        val ordered = planner.prioritize(
            progress = listOf(
                progress(wordId = 1, word = "still-hard", wrongCount = 2, correctCount = 1, needReview = true),
                progress(wordId = 2, word = "recovered", wrongCount = 1, correctCount = 2),
                progress(wordId = 3, word = "new"),
            ),
            count = 3,
            nowMillis = now,
        )

        assertEquals(listOf("new", "still-hard", "recovered"), ordered.map { it.entry.word })
    }

    @Test
    fun plannerHonorsRequestedCount() {
        val ordered = planner.prioritize(
            progress = listOf(
                progress(wordId = 1, word = "a", totalSolvedCount = 1),
                progress(wordId = 2, word = "b", wrongCount = 1),
                progress(wordId = 3, word = "c", totalSolvedCount = 1),
            ),
            count = 2,
            nowMillis = now,
        )

        assertEquals(2, ordered.size)
        assertEquals("b", ordered.first().entry.word)
    }

    @Test
    fun plannerRepeatsWordsWhenRequestedCountExceedsPool() {
        val ordered = planner.prioritize(
            progress = listOf(
                progress(wordId = 1, word = "a"),
                progress(wordId = 2, word = "b"),
            ),
            count = 5,
            nowMillis = now,
        )

        assertEquals(5, ordered.size)
        assertEquals(listOf("a", "b", "a", "b", "a"), ordered.map { it.entry.word })
    }

    private fun progress(
        wordId: Long,
        word: String,
        correctCount: Int = 0,
        wrongCount: Int = 0,
        totalSolvedCount: Int = correctCount + wrongCount,
        averageElapsedMs: Long = 0,
        lastSolvedAt: Long? = null,
        needReview: Boolean = false,
        nextReviewAt: Long? = null,
        memoryStrength: Int = 0,
    ): WordProgress =
        WordProgress(
            entry = WordEntry(
                wordId = wordId,
                language = Language.ENGLISH,
                grade = SchoolGrade.HIGH_1,
                word = word,
                phonetic = "",
                meanings = listOf(word),
                exampleSentence = "",
                exampleTranslation = "",
                wordAudioUrl = "",
                exampleAudioUrl = "",
            ),
            stat = WordStat(
                wordId = wordId,
                totalSolvedCount = totalSolvedCount,
                correctCount = correctCount,
                wrongCount = wrongCount,
                averageElapsedMs = averageElapsedMs,
                lastSolvedAt = lastSolvedAt,
                needReview = needReview,
                nextReviewAt = nextReviewAt,
                memoryStrength = memoryStrength,
                lastLearningResponse = if (totalSolvedCount == 0) null else LearningResponse.KNOWN,
            ),
        )
}
