package com.gwon.vocablearning.domain.service

import android.media.AudioAttributes
import android.media.MediaPlayer
import com.gwon.vocablearning.data.remote.CatalogFileStore
import com.gwon.vocablearning.data.remote.RemoteCatalogService
import com.gwon.vocablearning.domain.model.AudioType
import com.gwon.vocablearning.domain.model.WordEntry

interface AudioPlayer {
    fun play(source: String)
    fun release()
}

class AndroidAudioPlayer : AudioPlayer {
    private var mediaPlayer: MediaPlayer? = null

    override fun play(source: String) {
        release()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build(),
            )
            setDataSource(source)
            setOnPreparedListener { it.start() }
            setOnCompletionListener {
                it.release()
                if (mediaPlayer === it) {
                    mediaPlayer = null
                }
            }
            prepareAsync()
        }
    }

    override fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

class AudioCacheManager(
    private val fileStore: CatalogFileStore,
    private val remoteCatalogService: RemoteCatalogService,
) {
    suspend fun prepare(entry: WordEntry, audioType: AudioType): String? {
        val url = when (audioType) {
            AudioType.WORD -> entry.wordAudioUrl
            AudioType.EXAMPLE -> entry.exampleAudioUrl
        }.trim()

        if (url.isBlank()) {
            return null
        }

        val cachedFile = fileStore.resolveAudioFile(entry.wordId, audioType)
        if (cachedFile.exists()) {
            return cachedFile.absolutePath
        }

        return runCatching {
            remoteCatalogService.downloadToFile(url, cachedFile)
            cachedFile.absolutePath
        }.getOrElse {
            // If caching fails, still try streaming from the original URL.
            url
        }
    }
}

