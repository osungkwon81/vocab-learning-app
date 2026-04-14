package com.gwon.vocablearning.app

import android.content.Context
import com.gwon.vocablearning.data.local.LearningDatabase
import com.gwon.vocablearning.data.preferences.SettingsPreferencesRepository
import com.gwon.vocablearning.data.remote.CatalogFileStore
import com.gwon.vocablearning.data.remote.RemoteCatalogService
import com.gwon.vocablearning.data.repository.OfflineFirstStudyRepository
import com.gwon.vocablearning.data.repository.StudyRepository
import com.gwon.vocablearning.domain.service.AndroidAudioPlayer
import com.gwon.vocablearning.domain.service.AudioCacheManager
import com.gwon.vocablearning.domain.service.AudioPlayer
import com.gwon.vocablearning.domain.service.QuizFactory

class AppContainer(
    context: Context,
) {
    private val applicationContext = context.applicationContext
    private val database = LearningDatabase.build(applicationContext)
    private val settingsRepository = SettingsPreferencesRepository(applicationContext)
    private val catalogFileStore = CatalogFileStore(applicationContext)
    private val remoteCatalogService = RemoteCatalogService()

    val studyRepository: StudyRepository =
        OfflineFirstStudyRepository(
            settingsRepository = settingsRepository,
            catalogFileStore = catalogFileStore,
            remoteCatalogService = remoteCatalogService,
            wordStatDao = database.wordStatDao(),
            quizHistoryDao = database.quizHistoryDao(),
        )

    val audioPlayer: AudioPlayer = AndroidAudioPlayer()
    val audioCacheManager = AudioCacheManager(catalogFileStore, remoteCatalogService)
    val quizFactory = QuizFactory()
}

