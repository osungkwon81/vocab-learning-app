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
    suspend fun loadWordSet(grade: SchoolGrade): WordSetDto =
        withContext(Dispatchers.IO) {
            val localFile = localCatalogFile(grade)
            val raw = if (localFile.exists()) {
                localFile.readText()
            } else {
                context.assets.open(grade.assetPath).bufferedReader().use { it.readText() }
            }
            json.decodeFromString<WordSetDto>(raw)
        }

    suspend fun loadBundledManifest(): VersionManifestDto =
        withContext(Dispatchers.IO) {
            val raw = context.assets.open("data/version.json").bufferedReader().use { it.readText() }
            json.decodeFromString<VersionManifestDto>(raw)
        }

    suspend fun saveCatalog(grade: SchoolGrade, payload: ByteArray) {
        withContext(Dispatchers.IO) {
            val target = localCatalogFile(grade)
            target.parentFile?.mkdirs()
            target.writeBytes(payload)
        }
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
}
