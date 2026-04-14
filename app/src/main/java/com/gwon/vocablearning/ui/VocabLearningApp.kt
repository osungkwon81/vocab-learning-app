package com.gwon.vocablearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Rule
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gwon.vocablearning.domain.model.AudioType
import com.gwon.vocablearning.domain.model.DashboardSnapshot
import com.gwon.vocablearning.domain.model.QuizQuestion
import com.gwon.vocablearning.domain.model.QuizType
import com.gwon.vocablearning.domain.model.ReviewItem
import com.gwon.vocablearning.domain.model.SchoolGrade
import com.gwon.vocablearning.domain.model.WordEntry
import com.gwon.vocablearning.domain.model.WordProgress
import com.gwon.vocablearning.ui.theme.Clay
import com.gwon.vocablearning.ui.theme.Cream
import com.gwon.vocablearning.ui.theme.Ink
import com.gwon.vocablearning.ui.viewmodel.MainUiState
import com.gwon.vocablearning.ui.viewmodel.MainViewModel
import com.gwon.vocablearning.ui.viewmodel.QuizSessionState

private enum class AppTab(
    val label: String,
    val icon: ImageVector,
) {
    STUDY("학습", Icons.Rounded.AutoStories),
    QUIZ("퀴즈", Icons.AutoMirrored.Rounded.Rule),
    REVIEW("복습", Icons.Rounded.TaskAlt),
    STATS("통계", Icons.Rounded.Assessment),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabLearningApp(
    viewModel: MainViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var currentTab by rememberSaveable { mutableStateOf(AppTab.STUDY) }

    LaunchedEffect(uiState.noticeMessage) {
        uiState.noticeMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissNotice()
        }
    }

    Scaffold(
        containerColor = Cream,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("시험 대비 단어 학습")
                        Text(
                            text = "영어 우선, JSON + SQLite 구조",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.syncCatalog() },
                        enabled = !uiState.isSyncing,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "동기화",
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            if (uiState.isSyncing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            GradeSelector(
                selectedGrade = uiState.selectedGrade,
                onSelectGrade = viewModel::selectGrade,
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (currentTab) {
                AppTab.STUDY -> StudyTab(
                    uiState = uiState,
                    onSelectWord = viewModel::selectWord,
                    onPlayWordAudio = { viewModel.playAudio(it, AudioType.WORD) },
                    onPlayExampleAudio = { viewModel.playAudio(it, AudioType.EXAMPLE) },
                )

                AppTab.QUIZ -> QuizTab(
                    uiState = uiState,
                    onSelectType = viewModel::updateQuizType,
                    onSelectCount = viewModel::updateQuestionCount,
                    onStartQuiz = viewModel::startQuiz,
                    onSubmitAnswer = viewModel::submitAnswer,
                )

                AppTab.REVIEW -> ReviewTab(
                    items = uiState.reviewItems,
                    onPlayWordAudio = { viewModel.playAudio(it, AudioType.WORD) },
                )

                AppTab.STATS -> StatsTab(
                    dashboard = uiState.dashboard,
                    wordProgress = uiState.wordProgress,
                )
            }
        }
    }
}

@Composable
private fun GradeSelector(
    selectedGrade: SchoolGrade,
    onSelectGrade: (SchoolGrade) -> Unit,
) {
    Column {
        Text(
            text = "학년 선택",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SchoolGrade.entries) { grade ->
                FilterChip(
                    selected = selectedGrade == grade,
                    onClick = { onSelectGrade(grade) },
                    label = { Text(grade.label) },
                )
            }
        }
    }
}

@Composable
private fun StudyTab(
    uiState: MainUiState,
    onSelectWord: (WordEntry) -> Unit,
    onPlayWordAudio: (WordEntry) -> Unit,
    onPlayExampleAudio: (WordEntry) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        WordDetailCard(
            word = uiState.selectedWord,
            onPlayWordAudio = onPlayWordAudio,
            onPlayExampleAudio = onPlayExampleAudio,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "단어 목록",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(uiState.wordProgress, key = { it.entry.wordId }) { progress ->
                WordListItem(
                    progress = progress,
                    isSelected = uiState.selectedWord?.wordId == progress.entry.wordId,
                    onClick = { onSelectWord(progress.entry) },
                )
            }
        }
    }
}

@Composable
private fun WordDetailCard(
    word: WordEntry?,
    onPlayWordAudio: (WordEntry) -> Unit,
    onPlayExampleAudio: (WordEntry) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        if (word == null) {
            EmptyState("표시할 단어가 없습니다.")
            return@ElevatedCard
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = word.word,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = word.phonetic,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { onPlayWordAudio(word) },
                        label = { Text("단어 발음") },
                        leadingIcon = { Icon(Icons.Rounded.Campaign, contentDescription = null) },
                    )
                    AssistChip(
                        onClick = { onPlayExampleAudio(word) },
                        label = { Text("예문 듣기") },
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "뜻",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = word.meanings.joinToString(", "),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "예문",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = word.exampleSentence)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = word.exampleTranslation,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WordListItem(
    progress: WordProgress,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = progress.entry.word,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "시도 ${progress.stat.totalSolvedCount}회",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = progress.entry.meanings.joinToString(", "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (progress.stat.needReview) {
                Spacer(modifier = Modifier.height(8.dp))
                Tag("복습 필요")
            }
        }
    }
}

@Composable
private fun QuizTab(
    uiState: MainUiState,
    onSelectType: (QuizType) -> Unit,
    onSelectCount: (Int) -> Unit,
    onStartQuiz: () -> Unit,
    onSubmitAnswer: (Int) -> Unit,
) {
    val quizSession = uiState.quizSession
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "퀴즈 설정",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("문제 유형")
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(QuizType.entries) { type ->
                        FilterChip(
                            selected = uiState.quizConfig.type == type,
                            onClick = { onSelectType(type) },
                            label = { Text(type.label) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("문제 수")
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(10, 30, 50, 100)) { count ->
                        FilterChip(
                            selected = uiState.quizConfig.questionCount == count,
                            onClick = { onSelectCount(count) },
                            label = { Text(count.toString()) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onStartQuiz,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("퀴즈 시작")
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        when (quizSession) {
            QuizSessionState.Idle -> EmptyState("설정을 고르고 퀴즈를 시작하세요.")
            is QuizSessionState.Running -> QuizQuestionCard(
                session = quizSession,
                onSubmitAnswer = onSubmitAnswer,
            )

            is QuizSessionState.Finished -> QuizResultCard(session = quizSession)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun QuizQuestionCard(
    session: QuizSessionState.Running,
    onSubmitAnswer: (Int) -> Unit,
) {
    val question = session.currentQuestion
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${session.currentIndex + 1} / ${session.questions.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = question.type.label,
                style = MaterialTheme.typography.titleSmall,
                color = Clay,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = question.prompt,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (question.supportText.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = question.supportText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            question.options.forEachIndexed { index, option ->
                OutlinedButton(
                    onClick = { onSubmitAnswer(index) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = option,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun QuizResultCard(
    session: QuizSessionState.Finished,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "퀴즈 완료",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${session.correctCount} / ${session.totalCount}",
                style = MaterialTheme.typography.displaySmall,
                color = Ink,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("아래 통계/복습 탭에서 바로 반영된 결과를 볼 수 있습니다.")
        }
    }
}

@Composable
private fun ReviewTab(
    items: List<ReviewItem>,
    onPlayWordAudio: (WordEntry) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyState("현재 복습이 필요한 단어가 없습니다.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(items, key = { it.progress.entry.wordId }) { item ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = item.progress.entry.word,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = item.progress.entry.meanings.joinToString(", "),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AssistChip(
                            onClick = { onPlayWordAudio(item.progress.entry) },
                            label = { Text("듣기") },
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(item.reasons) { reason ->
                            Tag(reason.label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsTab(
    dashboard: DashboardSnapshot,
    wordProgress: List<WordProgress>,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            DashboardCards(dashboard = dashboard)
        }
        items(wordProgress, key = { it.entry.wordId }) { progress ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = progress.entry.word,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (progress.stat.needReview) {
                            Tag("복습 필요")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("정답 ${progress.stat.correctCount} / 오답 ${progress.stat.wrongCount}")
                    Text("평균 풀이 ${formatElapsed(progress.stat.averageElapsedMs)}")
                    Text("마지막 풀이 ${formatSolvedAt(progress.stat.lastSolvedAt)}")
                }
            }
        }
    }
}

@Composable
private fun DashboardCards(
    dashboard: DashboardSnapshot,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatCard("총 단어", dashboard.totalWords.toString(), Modifier.weight(1f))
            StatCard("풀이한 단어", dashboard.solvedWords.toString(), Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatCard("총 시도", dashboard.totalAttempts.toString(), Modifier.weight(1f))
            StatCard("복습 대상", dashboard.reviewCount.toString(), Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatCard("정답", dashboard.correctAnswers.toString(), Modifier.weight(1f))
            StatCard("평균 시간", formatElapsed(dashboard.averageElapsedMs), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun EmptyState(
    text: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(24.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Tag(
    text: String,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatElapsed(elapsedMs: Long): String {
    if (elapsedMs <= 0) return "-"
    return "${elapsedMs / 1000.0}s"
}

private fun formatSolvedAt(timestamp: Long?): String {
    if (timestamp == null) return "-"
    val seconds = (System.currentTimeMillis() - timestamp) / 1000
    return when {
        seconds < 60 -> "방금 전"
        seconds < 3600 -> "${seconds / 60}분 전"
        seconds < 86_400 -> "${seconds / 3600}시간 전"
        else -> "${seconds / 86_400}일 전"
    }
}
