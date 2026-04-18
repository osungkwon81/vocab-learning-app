package com.gwon.vocablearning.data.remote

import com.gwon.vocablearning.domain.model.SchoolGrade
import java.io.File
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class RemoteCatalogService(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun fetchManifest(baseUrl: String): VersionManifestDto =
        withContext(Dispatchers.IO) {
            val response = readBytes(resolveObjectUrl(baseUrl, "version.json"))
            parseVersionManifest(json, response.decodeToString())
        }

    suspend fun downloadCatalog(baseUrl: String, grade: SchoolGrade): ByteArray =
        withContext(Dispatchers.IO) {
            readBytes(resolveObjectUrl(baseUrl, grade.remotePath))
        }

    suspend fun downloadToFile(url: String, target: File): File =
        withContext(Dispatchers.IO) {
            target.parentFile?.mkdirs()
            target.writeBytes(readBytes(url))
            target
        }

    private fun readBytes(rawUrl: String): ByteArray {
        val normalized = rawUrl.trim().trimEnd('/')
        val connection = URL(normalized).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5_000
        connection.readTimeout = 10_000
        connection.instanceFollowRedirects = true

        return try {
            val status = connection.responseCode
            if (status !in 200..299) {
                error("HTTP $status for $normalized")
            }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun resolveObjectUrl(
        baseUrl: String,
        objectPath: String,
    ): String {
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        val normalizedObjectPath = objectPath.trim().trimStart('/')

        return if (normalizedBaseUrl.contains("/v0/b/") && normalizedBaseUrl.endsWith("/o")) {
            val encodedPath = URLEncoder.encode(normalizedObjectPath, StandardCharsets.UTF_8.toString())
            "$normalizedBaseUrl/$encodedPath?alt=media"
        } else {
            "$normalizedBaseUrl/$normalizedObjectPath"
        }
    }
}
