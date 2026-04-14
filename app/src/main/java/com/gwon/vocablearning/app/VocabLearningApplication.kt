package com.gwon.vocablearning.app

import android.app.Application

class VocabLearningApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
