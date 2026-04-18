package com.gwon.vocablearning.data.repository

import com.gwon.vocablearning.data.remote.RemoteCatalogService
import com.gwon.vocablearning.domain.model.SchoolGrade
import java.lang.reflect.Method
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteCatalogServiceTest {
    private val service = RemoteCatalogService()

    @Test
    fun firebaseStorageBaseUrlIsConvertedToAltMediaObjectUrl() {
        val url = resolveObjectUrl(
            baseUrl = "https://firebasestorage.googleapis.com/v0/b/vocab-learning-ff783.firebasestorage.app/o",
            objectPath = "catalog/en/english_middle3.json",
        )

        assertEquals(
            "https://firebasestorage.googleapis.com/v0/b/vocab-learning-ff783.firebasestorage.app/o/catalog%2Fen%2Fenglish_middle3.json?alt=media",
            url,
        )
    }

    @Test
    fun genericBaseUrlKeepsPathStyle() {
        val url = resolveObjectUrl(
            baseUrl = "https://example.com/storage",
            objectPath = SchoolGrade.MIDDLE_3.remotePath,
        )

        assertEquals(
            "https://example.com/storage/catalog/en/english_middle3.json",
            url,
        )
    }

    private fun resolveObjectUrl(
        baseUrl: String,
        objectPath: String,
    ): String {
        val method: Method = RemoteCatalogService::class.java.getDeclaredMethod(
            "resolveObjectUrl",
            String::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(service, baseUrl, objectPath) as String
    }
}
