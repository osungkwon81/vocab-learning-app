package com.gwon.vocablearning.data.remote

import com.gwon.vocablearning.domain.model.Language
import com.gwon.vocablearning.domain.model.SchoolGrade
import com.gwon.vocablearning.domain.model.WordEntry
import com.gwon.vocablearning.domain.model.WordSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class VersionManifestDto(
    val version: Int,
    val files: Map<String, Int>,
)

@Serializable
private data class VersionManifestArrayItemDto(
    val path: String,
    val version: Int,
)

fun parseVersionManifest(
    json: Json,
    raw: String,
): VersionManifestDto {
    val root = json.parseToJsonElement(raw).jsonObject
    val version = root.getValue("version").jsonPrimitive.content.toInt()
    val filesElement = root["files"] ?: error("files is missing in version manifest")

    val files = when (filesElement) {
        is JsonObject -> {
            filesElement.entries.associate { (key, value) ->
                key to value.jsonPrimitive.content.toInt()
            }
        }

        is JsonArray -> {
            filesElement.map { item ->
                val dto = json.decodeFromJsonElement<VersionManifestArrayItemDto>(item)
                pathToFileKey(dto.path) to dto.version
            }.toMap()
        }

        else -> error("Unsupported files format in version manifest")
    }

    return VersionManifestDto(
        version = version,
        files = files,
    )
}

private fun pathToFileKey(path: String): String =
    path.substringAfterLast('/').removeSuffix(".json")

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
    val type: String = "word",
    val pos: List<String> = emptyList(),
    val phonetic: String,
    val meanings: List<String>,
    val meaningsEn: List<String> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val exampleSentence: String,
    val exampleTranslation: String,
    val sources: List<WordSourceDto> = emptyList(),
    val wordAudioUrl: String,
    val exampleAudioUrl: String,
)

@Serializable
data class WordSourceDto(
    val book: String = "",
    val day: String = "",
    val section: String = "",
    val originalNo: String = "",
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
            lexicalType = dto.type,
            partsOfSpeech = dto.pos,
            phonetic = dto.phonetic,
            meanings = dto.meanings,
            meaningsEn = dto.meaningsEn,
            synonyms = dto.synonyms,
            antonyms = dto.antonyms,
            exampleSentence = dto.exampleSentence,
            exampleTranslation = dto.exampleTranslation,
            sources = dto.sources.map { source ->
                WordSource(
                    book = source.book,
                    day = source.day,
                    section = source.section,
                    originalNo = source.originalNo,
                )
            },
            wordAudioUrl = dto.wordAudioUrl,
            exampleAudioUrl = dto.exampleAudioUrl,
        )
    }
}
