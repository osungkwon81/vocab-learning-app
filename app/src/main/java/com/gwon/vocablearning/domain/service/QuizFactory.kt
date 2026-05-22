package com.gwon.vocablearning.domain.service

import com.gwon.vocablearning.domain.model.QuizQuestion
import com.gwon.vocablearning.domain.model.QuizType
import com.gwon.vocablearning.domain.model.WordEntry
import com.gwon.vocablearning.domain.model.WordProgress
import kotlin.random.Random

class QuizFactory(
    private val random: Random = Random.Default,
) {
    fun createQuestions(
        progress: List<WordProgress>,
        count: Int,
    ): List<QuizQuestion> {
        if (progress.isEmpty()) return emptyList()

        val safeCount = count.coerceAtLeast(1)
        val rotation = List(safeCount) { index -> progress[index % progress.size] }
        val pool = progress.map { it.entry }

        return rotation.mapIndexed { index, item ->
            createQuestion(
                entry = item.entry,
                pool = pool,
                type = selectQuizType(item, index),
            )
        }
    }

    fun createQuestions(
        words: List<WordEntry>,
        count: Int,
        type: QuizType,
    ): List<QuizQuestion> {
        if (words.isEmpty()) return emptyList()

        val safeCount = count.coerceAtLeast(1)
        val rotation = generateSequence { words.shuffled(random) }
            .flatten()
            .take(safeCount)
            .toList()

        return rotation.map { entry -> createQuestion(entry, words, type) }
    }

    private fun createQuestion(
        entry: WordEntry,
        pool: List<WordEntry>,
        type: QuizType,
    ): QuizQuestion =
        when (type) {
            QuizType.LEARNING_CARD -> createWordToMeaning(entry, pool)
            QuizType.WORD_TO_MEANING -> createWordToMeaning(entry, pool)
            QuizType.MEANING_TO_WORD -> createMeaningToWord(entry, pool)
            QuizType.SENTENCE_BLANK -> createSentenceBlank(entry, pool)
        }

    private fun selectQuizType(
        progress: WordProgress,
        index: Int,
    ): QuizType {
        val stat = progress.stat
        return when {
            stat.totalSolvedCount == 0 || stat.memoryStrength <= 1 -> QuizType.WORD_TO_MEANING
            stat.needReview || stat.wrongCount >= stat.correctCount -> QuizType.MEANING_TO_WORD
            stat.memoryStrength >= 4 || index % 3 == 2 -> QuizType.SENTENCE_BLANK
            else -> QuizType.MEANING_TO_WORD
        }
    }

    private fun createWordToMeaning(
        entry: WordEntry,
        pool: List<WordEntry>,
    ): QuizQuestion {
        val answer = entry.meanings.first()
        val options = buildOptions(
            answer = answer,
            wrongPool = pool
                .flatMap { it.meanings }
                .filterNot { it == answer },
        )
        return QuizQuestion(
            wordId = entry.wordId,
            type = QuizType.WORD_TO_MEANING,
            prompt = entry.word,
            supportText = entry.phonetic,
            options = options,
            answerIndex = options.indexOf(answer),
            explanation = entry.meanings.joinToString(", "),
        )
    }

    private fun createMeaningToWord(
        entry: WordEntry,
        pool: List<WordEntry>,
    ): QuizQuestion {
        val answer = entry.word
        val options = buildOptions(
            answer = answer,
            wrongPool = pool.map { it.word }.filterNot { it == answer },
        )
        return QuizQuestion(
            wordId = entry.wordId,
            type = QuizType.MEANING_TO_WORD,
            prompt = entry.meanings.first(),
            supportText = entry.exampleTranslation,
            options = options,
            answerIndex = options.indexOf(answer),
            explanation = entry.exampleSentence,
        )
    }

    private fun createSentenceBlank(
        entry: WordEntry,
        pool: List<WordEntry>,
    ): QuizQuestion {
        val answer = entry.word
        val prompt = buildBlankSentence(entry.exampleSentence, answer)
        val options = buildOptions(
            answer = answer,
            wrongPool = pool.map { it.word }.filterNot { it == answer },
        )
        return QuizQuestion(
            wordId = entry.wordId,
            type = QuizType.SENTENCE_BLANK,
            prompt = prompt,
            supportText = entry.exampleTranslation,
            options = options,
            answerIndex = options.indexOf(answer),
            explanation = entry.word,
        )
    }

    private fun buildOptions(
        answer: String,
        wrongPool: List<String>,
    ): List<String> =
        (
            wrongPool
                .distinct()
                .shuffled(random)
                .take(3) + answer
            )
            .distinct()
            .shuffled(random)

    private fun buildBlankSentence(
        sentence: String,
        answer: String,
    ): String {
        val regex = Regex("\\b${Regex.escape(answer)}\\b", RegexOption.IGNORE_CASE)
        return if (regex.containsMatchIn(sentence)) {
            sentence.replaceFirst(regex, "_____")
        } else {
            "$sentence (정답: _____)"
        }
    }
}
