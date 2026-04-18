package com.gwon.vocablearning.data.repository

import com.gwon.vocablearning.domain.model.Language
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
    fun wrongWordsArePrioritizedBeforeOldAndSlowWords() {
        val ordered = planner.prioritize(
            progress = listOf(
                progress(wordId = 1, word = "general"),
                progress(wordId = 2, word = "old", lastSolvedAt = now - StudyDeckPlanner.OLD_WORD_THRESHOLD_MS - 1),
                progress(wordId = 3, word = "slow", averageElapsedMs = StudyDeckPlanner.SLOW_RESPONSE_THRESHOLD_MS + 1),
                progress(wordId = 4, word = "wrong", wrongCount = 2),
            ),
            count = 4,
            nowMillis = now,
        )

        assertEquals(listOf("wrong", "old", "slow", "general"), ordered.map { it.entry.word })
    }

    @Test
    fun plannerHonorsRequestedCount() {
        val ordered = planner.prioritize(
            progress = listOf(
                progress(wordId = 1, word = "a"),
                progress(wordId = 2, word = "b", wrongCount = 1),
                progress(wordId = 3, word = "c"),
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
        wrongCount: Int = 0,
        averageElapsedMs: Long = 0,
        lastSolvedAt: Long? = null,
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
                wrongCount = wrongCount,
                averageElapsedMs = averageElapsedMs,
                lastSolvedAt = lastSolvedAt,
            ),
        )
}
