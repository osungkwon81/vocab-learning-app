package com.gwon.vocablearning

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.gwon.vocablearning.app.VocabLearningApplication
import com.gwon.vocablearning.ui.VocabLearningApp
import com.gwon.vocablearning.ui.theme.VocabLearningTheme
import com.gwon.vocablearning.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.factory((application as VocabLearningApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VocabLearningTheme {
                VocabLearningApp(viewModel = viewModel)
            }
        }
    }
}

