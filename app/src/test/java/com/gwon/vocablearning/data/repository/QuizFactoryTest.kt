package com.gwon.vocablearning.data.repository

import com.gwon.vocablearning.domain.model.Language
import com.gwon.vocablearning.domain.model.LearningResponse
import com.gwon.vocablearning.domain.model.QuizType
import com.gwon.vocablearning.domain.model.SchoolGrade
import com.gwon.vocablearning.domain.model.WordEntry
import com.gwon.vocablearning.domain.model.WordProgress
import com.gwon.vocablearning.domain.model.WordStat
import com.gwon.vocablearning.domain.service.QuizFactory
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizFactoryTest {
    private val sampleWords = listOf(
        wordEntry(1, "run", listOf("달리다"), "I run every morning."),
        wordEntry(2, "walk", listOf("걷다"), "I walk to school every day."),
        wordEntry(3, "read", listOf("읽다"), "Students read books in class."),
        wordEntry(4, "write", listOf("쓰다"), "Please write your name here."),
    )

    @Test
    fun sentenceBlankQuestionReplacesTargetWord() {
        val factory = QuizFactory(Random(0))

        val question = factory.createQuestions(
            words = sampleWords,
            count = 1,
            type = QuizType.SENTENCE_BLANK,
        ).single()

        assertTrue(question.prompt.contains("_____"))
        assertTrue(question.options.contains(question.explanation))
    }

    @Test
    fun requestedQuestionCountIsRespectedEvenWhenWordsRepeat() {
        val factory = QuizFactory(Random(1))

        val questions = factory.createQuestions(
            words = sampleWords,
            count = 10,
            type = QuizType.WORD_TO_MEANING,
        )

        assertEquals(10, questions.size)
    }

    @Test
    fun quizTypesFollowLearningStage() {
        val factory = QuizFactory(Random(2))

        val questions = factory.createQuestions(
            progress = listOf(
                progress(wordEntry(1, "run", listOf("달리다"), "I run every morning.")),
                progress(wordEntry(2, "walk", listOf("걷다"), "I walk to school every day."), totalSolvedCount = 2, wrongCount = 2, needReview = true),
                progress(wordEntry(3, "read", listOf("읽다"), "Students read books in class."), totalSolvedCount = 5, correctCount = 5, memoryStrength = 4),
            ),
            count = 3,
        )

        assertEquals(
            listOf(QuizType.WORD_TO_MEANING, QuizType.MEANING_TO_WORD, QuizType.SENTENCE_BLANK),
            questions.map { it.type },
        )
    }

    private fun wordEntry(
        id: Long,
        word: String,
        meanings: List<String>,
        exampleSentence: String,
    ) = WordEntry(
        wordId = id,
        language = Language.ENGLISH,
        grade = SchoolGrade.HIGH_1,
        word = word,
        phonetic = "",
        meanings = meanings,
        exampleSentence = exampleSentence,
        exampleTranslation = "",
        wordAudioUrl = "",
        exampleAudioUrl = "",
    )

    private fun progress(
        entry: WordEntry,
        totalSolvedCount: Int = 0,
        correctCount: Int = 0,
        wrongCount: Int = 0,
        needReview: Boolean = false,
        memoryStrength: Int = 0,
    ) = WordProgress(
        entry = entry,
        stat = WordStat(
            wordId = entry.wordId,
            totalSolvedCount = totalSolvedCount,
            correctCount = correctCount,
            wrongCount = wrongCount,
            needReview = needReview,
            memoryStrength = memoryStrength,
            lastLearningResponse = if (totalSolvedCount == 0) null else LearningResponse.KNOWN,
        ),
    )
}
