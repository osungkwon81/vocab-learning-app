package com.gwon.vocablearning

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gwon.vocablearning.app.VocabLearningApplication
import com.gwon.vocablearning.ui.VocabLearningApp
import com.gwon.vocablearning.ui.theme.VocabLearningTheme
import com.gwon.vocablearning.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val containerResult = runCatching {
            (application as VocabLearningApplication).container
        }

        setContent {
            VocabLearningTheme {
                containerResult.fold(
                    onSuccess = { container ->
                        val viewModel: MainViewModel = viewModel(
                            factory = MainViewModel.factory(container),
                        )
                        VocabLearningApp(viewModel = viewModel)
                    },
                    onFailure = {
                        StartupErrorScreen()
                    },
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun StartupErrorScreen() {
    Surface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "앱 데이터를 불러오는 중 문제가 발생했습니다.",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "기기 업데이트 후 기존 학습 데이터 마이그레이션에 실패했을 수 있습니다. 앱을 다시 실행해 보고, 계속 같으면 앱 데이터 초기화 후 다시 실행해 주세요.",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
