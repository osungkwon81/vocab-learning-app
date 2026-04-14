package com.gwon.vocablearning.data.remote

import com.gwon.vocablearning.domain.model.Language
import com.gwon.vocablearning.domain.model.SchoolGrade
import com.gwon.vocablearning.domain.model.WordEntry
import kotlinx.serialization.Serializable

@Serializable
data class VersionManifestDto(
    val version: Int,
    val files: Map<String, Int>,
)

@Serializable
data class WordSetDto(
    val language: String,
    val grade: String,
    val version: Int,
    val words: List<WordDto>,
)

@Serializable
data class WordDto(
    val wordId: Long,
    val word: String,
    val phonetic: String,
    val meanings: List<String>,
    val exampleSentence: String,
    val exampleTranslation: String,
    val wordAudioUrl: String,
    val exampleAudioUrl: String,
)

fun WordSetDto.toDomain(): List<WordEntry> {
    val language = Language.fromCode(language)
    val grade = SchoolGrade.fromCode(grade)
    return words.map { dto ->
        WordEntry(
            wordId = dto.wordId,
            language = language,
            grade = grade,
            word = dto.word,
            phonetic = dto.phonetic,
            meanings = dto.meanings,
            exampleSentence = dto.exampleSentence,
            exampleTranslation = dto.exampleTranslation,
            wordAudioUrl = dto.wordAudioUrl,
            exampleAudioUrl = dto.exampleAudioUrl,
        )
    }
}

