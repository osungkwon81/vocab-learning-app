package com.gwon.vocablearning.data.remote

import android.content.Context
import com.gwon.vocablearning.domain.model.AudioType
import com.gwon.vocablearning.domain.model.SchoolGrade
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class CatalogFileStore(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val wordSetCache = mutableMapOf<SchoolGrade, WordSetDto>()

    suspend fun loadWordSet(grade: SchoolGrade): WordSetDto =
        withContext(Dispatchers.IO) {
            wordSetCache[grade]?.let { return@withContext it }
            val localFile = localCatalogFile(grade)
            val wordSet = if (localFile.exists()) {
                runCatching {
                    json.decodeFromString<WordSetDto>(localFile.readText())
                }.getOrElse {
                    // Ignore a broken remote cache and fall back to the bundled asset.
                    localFile.delete()
                    loadBundledWordSet(grade)
                }
            } else {
                loadBundledWordSet(grade)
            }
            wordSetCache[grade] = wordSet
            wordSet
        }

    suspend fun loadBundledManifest(): VersionManifestDto =
        withContext(Dispatchers.IO) {
            val raw = context.assets.open("data/version.json").bufferedReader().use { it.readText() }
            parseVersionManifest(json, raw)
        }

    suspend fun saveCatalog(grade: SchoolGrade, payload: ByteArray) {
        withContext(Dispatchers.IO) {
            val target = localCatalogFile(grade)
            target.parentFile?.mkdirs()
            target.writeBytes(payload)
            wordSetCache.remove(grade)
        }
    }

    suspend fun validateCatalogPayload(payload: ByteArray): Int =
        withContext(Dispatchers.IO) {
            json.decodeFromString<WordSetDto>(payload.decodeToString()).words.size
        }

    suspend fun resolveAudioFile(wordId: Long, audioType: AudioType): File =
        withContext(Dispatchers.IO) {
            val directory = when (audioType) {
                AudioType.WORD -> File(context.filesDir, "audio/words")
                AudioType.EXAMPLE -> File(context.filesDir, "audio/sentences")
            }
            directory.mkdirs()
            File(directory, "$wordId.mp3")
        }

    private fun localCatalogFile(grade: SchoolGrade): File =
        File(context.filesDir, "catalog/${grade.fileKey}.json")

    private fun loadBundledWordSet(grade: SchoolGrade): WordSetDto {
        val raw = context.assets.open(grade.assetPath).bufferedReader().use { it.readText() }
        return json.decodeFromString(raw)
    }
}
