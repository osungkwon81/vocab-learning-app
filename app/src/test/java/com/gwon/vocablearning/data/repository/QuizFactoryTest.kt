package com.gwon.vocablearning.data.repository

import com.gwon.vocablearning.domain.model.Language
import com.gwon.vocablearning.domain.model.QuizType
import com.gwon.vocablearning.domain.model.SchoolGrade
import com.gwon.vocablearning.domain.model.WordEntry
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
}
