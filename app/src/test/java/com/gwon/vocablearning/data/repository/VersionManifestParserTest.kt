package com.gwon.vocablearning.data.repository

import com.gwon.vocablearning.data.remote.parseVersionManifest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class VersionManifestParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesMapStyleManifest() {
        val manifest = parseVersionManifest(
            json = json,
            raw = """
                {
                  "version": 3,
                  "files": {
                    "english_middle3": 2
                  }
                }
            """.trimIndent(),
        )

        assertEquals(3, manifest.version)
        assertEquals(2, manifest.files["english_middle3"])
    }

    @Test
    fun parsesArrayStyleManifest() {
        val manifest = parseVersionManifest(
            json = json,
            raw = """
                {
                  "version": 1,
                  "files": [
                    {
                      "path": "catalog/en/english_middle3.json",
                      "version": 1
                    }
                  ]
                }
            """.trimIndent(),
        )

        assertEquals(1, manifest.version)
        assertEquals(1, manifest.files["english_middle3"])
    }
}
