package com.gwon.vocablearning.app

import android.app.Application

class VocabLearningApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.NONE) {
        AppContainer(this)
    }
}
