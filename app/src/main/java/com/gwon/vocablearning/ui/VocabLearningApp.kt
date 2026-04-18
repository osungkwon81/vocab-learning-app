package com.gwon.vocablearning.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gwon.vocablearning.domain.model.AudioType
import com.gwon.vocablearning.domain.model.DashboardSnapshot
import com.gwon.vocablearning.domain.model.QuizQuestion
import com.gwon.vocablearning.domain.model.ReviewItem
import com.gwon.vocablearning.domain.model.SchoolGrade
import com.gwon.vocablearning.domain.model.WordEntry
import com.gwon.vocablearning.domain.model.WordProgress
import com.gwon.vocablearning.ui.theme.Clay
import com.gwon.vocablearning.ui.theme.Cream
import com.gwon.vocablearning.ui.theme.Ink
import com.gwon.vocablearning.ui.viewmodel.LearningCardProgressState
import com.gwon.vocablearning.ui.viewmodel.LearningSessionState
import com.gwon.vocablearning.ui.viewmodel.MainUiState
import com.gwon.vocablearning.ui.viewmodel.MainViewModel
import com.gwon.vocablearning.ui.viewmodel.QuizSessionState
import com.gwon.vocablearning.ui.viewmodel.SetupStep
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class AppTab(
    val label: String,
    val icon: ImageVector,
) {
    STUDY("학습", Icons.AutoMirrored.Rounded.MenuBook),
    QUIZ("퀴즈", Icons.Rounded.Campaign),
    REVIEW("복습", Icons.Rounded.TaskAlt),
    STATS("통계", Icons.Rounded.Assessment),
    SETTINGS("설정", Icons.Rounded.Settings),
}

private data class CountPreset(
    val label: String,
    val count: Int,
)

private val countPresets = listOf(
    CountPreset("여유롭게", 20),
    CountPreset("적당하게", 30),
    CountPreset("열심히", 50),
    CountPreset("집중해서", 100),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabLearningApp(
    viewModel: MainViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.noticeMessage) {
        uiState.noticeMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissNotice()
        }
    }

    when {
        uiState.isLoading -> LoadingScreen()
        uiState.setupStep != SetupStep.READY -> OnboardingFlow(
            uiState = uiState,
            viewModel = viewModel,
        )

        else -> MainAppShell(
            uiState = uiState,
            viewModel = viewModel,
            snackbarHostState = snackbarHostState,
        )
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun OnboardingFlow(
    uiState: MainUiState,
    viewModel: MainViewModel,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Cream,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (uiState.setupStep) {
                SetupStep.NICKNAME -> NicknameSetupScreen(
                    nicknameInput = uiState.nicknameInput,
                    onNicknameChange = viewModel::updateNicknameInput,
                    onConfirm = viewModel::confirmNickname,
                )

                SetupStep.GRADE -> GradeSetupScreen(
                    selectedGrade = uiState.selectedGrade,
                    onSelectGrade = viewModel::selectGrade,
                    onConfirm = viewModel::completeGradeSetup,
                )

                SetupStep.READY -> Unit
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAppShell(
    uiState: MainUiState,
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
) {
    var currentTab by rememberSaveable { mutableStateOf(AppTab.STUDY) }

    if (uiState.showStudyStartDialog) {
        StudyStartDialog(
            learningCount = uiState.learningCount,
            onDismiss = viewModel::closeStudyStartDialog,
            onSelectPreset = viewModel::updateLearningCount,
            onAdjustCount = viewModel::adjustLearningCount,
            onConfirm = viewModel::startLearningSession,
        )
    }

    if (uiState.showQuizStartDialog) {
        QuizStartDialog(
            quizCount = uiState.quizCount,
            onDismiss = viewModel::closeQuizStartDialog,
            onSelectPreset = viewModel::updateQuizCount,
            onAdjustCount = viewModel::adjustQuizCount,
            onConfirm = viewModel::startQuizSession,
        )
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
                            text = "${uiState.nickname} · ${uiState.selectedGrade.label}",
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
                        Icon(Icons.Rounded.Refresh, contentDescription = "동기화")
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

            when (currentTab) {
                AppTab.STUDY -> StudyTab(
                    uiState = uiState,
                    onOpenStudyStartDialog = viewModel::openStudyStartDialog,
                    onClearLearningSession = viewModel::clearLearningSession,
                    onPageChanged = viewModel::onLearningPageChanged,
                    onRevealCard = viewModel::revealCurrentCard,
                    onHideCardDetails = viewModel::hideCurrentCardDetails,
                    onAnswerCard = viewModel::answerCurrentCard,
                    onPlayWordAudio = { viewModel.playAudio(it, AudioType.WORD) },
                    onPlayExampleAudio = { viewModel.playAudio(it, AudioType.EXAMPLE) },
                )

                AppTab.QUIZ -> QuizTab(
                    uiState = uiState,
                    onOpenQuizStartDialog = viewModel::openQuizStartDialog,
                    onClearQuizSession = viewModel::clearQuizSession,
                    onAnswerQuestion = viewModel::answerCurrentQuizQuestion,
                )

                AppTab.REVIEW -> ReviewTab(
                    items = uiState.reviewItems,
                    onPlayWordAudio = { viewModel.playAudio(it, AudioType.WORD) },
                )

                AppTab.STATS -> StatsTab(
                    dashboard = uiState.dashboard,
                    wordProgress = uiState.wordProgress,
                )

                AppTab.SETTINGS -> SettingsTab(
                    uiState = uiState,
                    onNicknameChange = viewModel::updateNicknameInput,
                    onSaveNickname = viewModel::saveNickname,
                    onSelectGrade = viewModel::selectGrade,
                )
            }
        }
    }
}

@Composable
private fun NicknameSetupScreen(
    nicknameInput: String,
    onNicknameChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "처음 사용할 이름을 입력해 주세요",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "회원가입은 없고, 이 이름이 로컬 학습 기록의 기준이 됩니다.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = nicknameInput,
                onValueChange = onNicknameChange,
                label = { Text("닉네임") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("다음")
            }
        }
    }
}

@Composable
private fun GradeSetupScreen(
    selectedGrade: SchoolGrade,
    onSelectGrade: (SchoolGrade) -> Unit,
    onConfirm: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "학년 선택",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "학습 개수는 메인 화면의 학습 시작 버튼에서 매번 고를 수 있습니다.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GradeSelector(selectedGrade = selectedGrade, onSelectGrade = onSelectGrade)
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("시작 준비 완료")
            }
        }
    }
}

@Composable
private fun StudyTab(
    uiState: MainUiState,
    onOpenStudyStartDialog: () -> Unit,
    onClearLearningSession: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onRevealCard: () -> Unit,
    onHideCardDetails: () -> Unit,
    onAnswerCard: (Boolean) -> Unit,
    onPlayWordAudio: (WordEntry) -> Unit,
    onPlayExampleAudio: (WordEntry) -> Unit,
) {
    val session = uiState.learningSession

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onOpenStudyStartDialog,
                modifier = Modifier.weight(1f),
            ) {
                Text("학습 시작")
            }
            if (session != null && !session.isComplete) {
                OutlinedButton(
                    onClick = onClearLearningSession,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("세션 닫기")
                }
            }
        }

        if (session == null) {
            EmptyState("학습 시작을 누르면 개수 선택 화면이 열리고 카드 학습이 시작됩니다.")
            return@Column
        }

        SessionHeader(session = session)

        if (session.isComplete) {
            SessionCompleteCard(
                session = session,
                onRestartSession = onOpenStudyStartDialog,
            )
            return@Column
        }

        val pagerState = rememberPagerState(pageCount = { session.deck.size })
        val coroutineScope = rememberCoroutineScope()
        val pagerHeight by animateDpAsState(
            targetValue = if (session.currentCardState?.isRevealed == true) 700.dp else 520.dp,
            label = "studyPagerHeight",
        )

        LaunchedEffect(session.deck.map { item -> item.progress.entry.wordId }) {
            pagerState.scrollToPage(session.currentPage.coerceAtMost((session.deck.size - 1).coerceAtLeast(0)))
        }

        LaunchedEffect(pagerState.currentPage) {
            onPageChanged(pagerState.currentPage)
        }

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = session.currentCardState?.response != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(pagerHeight),
        ) { page ->
            val item = session.deck[page]
            val progress = item.progress
            val cardState = session.cards[item.id] ?: LearningCardProgressState()
            val isLastCard = page == session.deck.lastIndex

            LaunchedEffect(cardState.response, pagerState.currentPage) {
                if (page == pagerState.currentPage && cardState.response != null && !isLastCard) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(page + 1)
                    }
                }
            }

            LearningWordCard(
                progress = progress,
                cardState = cardState,
                onRevealCard = onRevealCard,
                onHideCardDetails = onHideCardDetails,
                onAnswerCard = onAnswerCard,
                onMoveNext = {
                    if (page < session.deck.lastIndex) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(page + 1)
                        }
                    }
                },
                isLastCard = isLastCard,
                onPlayWordAudio = onPlayWordAudio,
                onPlayExampleAudio = onPlayExampleAudio,
            )
        }
    }
}

@Composable
private fun QuizTab(
    uiState: MainUiState,
    onOpenQuizStartDialog: () -> Unit,
    onClearQuizSession: () -> Unit,
    onAnswerQuestion: (Int) -> Unit,
) {
    val session = uiState.quizSession

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onOpenQuizStartDialog,
                modifier = Modifier.weight(1f),
            ) {
                Text("퀴즈 시작")
            }
            if (session != null && !session.isComplete) {
                OutlinedButton(
                    onClick = onClearQuizSession,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("퀴즈 닫기")
                }
            }
        }

        if (session == null) {
            QuizIntroCard(
                gradeLabel = uiState.selectedGrade.label,
                onStartQuiz = onOpenQuizStartDialog,
            )
            return@Column
        }

        QuizHeader(session = session)

        if (session.isComplete) {
            QuizCompleteCard(
                session = session,
                onRestartQuiz = onOpenQuizStartDialog,
            )
            return@Column
        }

        session.currentQuestion?.let { question ->
            QuizQuestionCard(
                question = question,
                currentIndex = session.currentIndex,
                totalCount = session.questions.size,
                onAnswerQuestion = onAnswerQuestion,
            )
        }
    }
}

@Composable
private fun QuizIntroCard(
    gradeLabel: String,
    onStartQuiz: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "객관식 퀴즈",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Tag(gradeLabel)
            Text(
                text = "단어를 보고 보기 4개 중 정답을 고르는 방식입니다. 답을 누르면 바로 다음 문제로 넘어갑니다.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onStartQuiz,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("시작하기")
            }
        }
    }
}

@Composable
private fun QuizHeader(
    session: QuizSessionState,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${session.completedCount} / ${session.questions.size} 완료",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = {
                    if (session.questions.isEmpty()) 0f else session.completedCount.toFloat() / session.questions.size.toFloat()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun QuizQuestionCard(
    question: QuizQuestion,
    currentIndex: Int,
    totalCount: Int,
    onAnswerQuestion: (Int) -> Unit,
) {
    var selectedOptionIndex by remember(question.wordId, currentIndex) { mutableStateOf<Int?>(null) }

    LaunchedEffect(selectedOptionIndex) {
        val pendingSelection = selectedOptionIndex ?: return@LaunchedEffect
        delay(350)
        onAnswerQuestion(pendingSelection)
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Tag("${currentIndex + 1} / $totalCount")
            Text(
                text = question.prompt,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Ink,
            )
            if (question.supportText.isNotBlank()) {
                Text(
                    text = question.supportText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                question.options.chunked(2).forEachIndexed { rowIndex, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEachIndexed { columnIndex, option ->
                            val optionIndex = rowIndex * 2 + columnIndex
                            val optionContainerColor = when {
                                selectedOptionIndex == null -> MaterialTheme.colorScheme.surfaceVariant
                                optionIndex == question.answerIndex -> MaterialTheme.colorScheme.tertiaryContainer
                                optionIndex == selectedOptionIndex -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                            ElevatedCard(
                                onClick = {
                                    if (selectedOptionIndex == null) {
                                        selectedOptionIndex = optionIndex
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = optionContainerColor,
                                ),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 92.dp)
                                        .padding(16.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizCompleteCard(
    session: QuizSessionState,
    onRestartQuiz: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "퀴즈 완료",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text("정답 ${session.correctCount}개 · 오답 ${session.wrongCount}개")
            Text(
                text = "틀린 문제도 통계에 반영됩니다.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onRestartQuiz,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("다시 시작")
            }
        }
    }
}

@Composable
private fun StudyStartDialog(
    learningCount: Int,
    onDismiss: () -> Unit,
    onSelectPreset: (Int) -> Unit,
    onAdjustCount: (Int) -> Unit,
    onConfirm: () -> Unit,
) {
    CountSelectionDialog(
        title = "자신에게 적당한 하루 목표를 선택해보세요.",
        count = learningCount,
        unitLabel = "개",
        onDismiss = onDismiss,
        onSelectPreset = onSelectPreset,
        onAdjustCount = onAdjustCount,
        onConfirm = onConfirm,
    )
}

@Composable
private fun QuizStartDialog(
    quizCount: Int,
    onDismiss: () -> Unit,
    onSelectPreset: (Int) -> Unit,
    onAdjustCount: (Int) -> Unit,
    onConfirm: () -> Unit,
) {
    CountSelectionDialog(
        title = "몇 문제를 풀지 선택해보세요.",
        count = quizCount,
        unitLabel = "문제",
        onDismiss = onDismiss,
        onSelectPreset = onSelectPreset,
        onAdjustCount = onAdjustCount,
        onConfirm = onConfirm,
    )
}

@Composable
private fun CountSelectionDialog(
    title: String,
    count: Int,
    unitLabel: String,
    onDismiss: () -> Unit,
    onSelectPreset: (Int) -> Unit,
    onAdjustCount: (Int) -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                CountPresetGrid(
                    learningCount = count,
                    onSelectPreset = onSelectPreset,
                )
                CustomCountCard(
                    learningCount = count,
                    unitLabel = unitLabel,
                    onAdjustCount = onAdjustCount,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("취소")
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("결정")
                    }
                }
            }
        }
    }
}

@Composable
private fun CountPresetGrid(
    learningCount: Int,
    onSelectPreset: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            countPresets.take(2).forEach { preset ->
                CountPresetCard(
                    preset = preset,
                    isSelected = learningCount == preset.count,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectPreset(preset.count) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            countPresets.drop(2).take(2).forEach { preset ->
                CountPresetCard(
                    preset = preset,
                    isSelected = learningCount == preset.count,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectPreset(preset.count) },
                )
            }
        }
    }
}

@Composable
private fun CountPresetCard(
    preset: CountPreset,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = preset.label,
                color = if (isSelected) Clay else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = preset.count.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "개",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CustomCountCard(
    learningCount: Int,
    unitLabel: String,
    onAdjustCount: (Int) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "자유 설정",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OutlinedButton(onClick = { onAdjustCount(-1) }) {
                    Text("-")
                }
                Text(
                    text = learningCount.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = unitLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { onAdjustCount(1) }) {
                    Text("+")
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(
    uiState: MainUiState,
    onNicknameChange: (String) -> Unit,
    onSaveNickname: () -> Unit,
    onSelectGrade: (SchoolGrade) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "프로필",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = uiState.nicknameInput,
                    onValueChange = onNicknameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("닉네임") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                Button(
                    onClick = onSaveNickname,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("닉네임 저장")
                }
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "학습 설정",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "학년을 바꾸면 현재 진행 중인 세션은 닫히고, 학습 시작 버튼에서 새로 시작해야 합니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GradeSelector(
                    selectedGrade = uiState.selectedGrade,
                    onSelectGrade = onSelectGrade,
                )
                Tag("학습 개수는 학습 탭의 시작 버튼에서 선택")
            }
        }
    }
}

@Composable
private fun SessionHeader(
    session: LearningSessionState,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${session.completedCount} / ${session.deck.size} 완료",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = {
                    if (session.deck.isEmpty()) 0f else session.completedCount.toFloat() / session.deck.size.toFloat()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LearningWordCard(
    progress: WordProgress,
    cardState: LearningCardProgressState,
    onRevealCard: () -> Unit,
    onHideCardDetails: () -> Unit,
    onAnswerCard: (Boolean) -> Unit,
    onMoveNext: () -> Unit,
    isLastCard: Boolean,
    onPlayWordAudio: (WordEntry) -> Unit,
    onPlayExampleAudio: (WordEntry) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = progress.entry.word,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = progress.entry.phonetic,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AssistChip(
                    onClick = { onPlayWordAudio(progress.entry) },
                    label = { Text("발음 듣기") },
                    leadingIcon = { Icon(Icons.Rounded.Campaign, contentDescription = null) },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            RevealPanel(
                progress = progress,
                cardState = cardState,
                onRevealCard = onRevealCard,
                onHideCardDetails = onHideCardDetails,
                onAnswerCard = onAnswerCard,
                onMoveNext = onMoveNext,
                isLastCard = isLastCard,
                onPlayExampleAudio = onPlayExampleAudio,
                modifier = Modifier
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RevealPanel(
    progress: WordProgress,
    cardState: LearningCardProgressState,
    onRevealCard: () -> Unit,
    onHideCardDetails: () -> Unit,
    onAnswerCard: (Boolean) -> Unit,
    onMoveNext: () -> Unit,
    isLastCard: Boolean,
    onPlayExampleAudio: (WordEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .animateContentSize()
            .clip(RoundedCornerShape(28.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        if (!cardState.isRevealed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp)
                    .padding(18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Button(
                    onClick = onRevealCard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                ) {
                    Text("뜻/예문 보기")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "뜻",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(text = progress.entry.meanings.joinToString(", "))
                    Text(
                        text = "예문",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(text = progress.entry.exampleSentence)
                    Text(
                        text = progress.entry.exampleTranslation,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AssistChip(
                        onClick = { onPlayExampleAudio(progress.entry) },
                        label = { Text("예문 발음 듣기") },
                    )
                }
                if (cardState.response == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onAnswerCard(true) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("O")
                        }
                        OutlinedButton(
                            onClick = { onAnswerCard(false) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("X")
                        }
                    }
                    OutlinedButton(
                        onClick = onHideCardDetails,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("닫기")
                    }
                } else if (isLastCard) {
                    Tag("마지막 단어까지 기록했습니다.")
                }
            }
        }
    }
}

@Composable
private fun SessionCompleteCard(
    session: LearningSessionState,
    onRestartSession: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "학습 완료",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text("알겠다 ${session.knownCount}개 · 모르겠다 ${session.unknownCount}개")
            Text(
                text = "모르겠다고 표시한 단어는 다음 세션에서 우선적으로 다시 나옵니다.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onRestartSession,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("다시 학습 시작")
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
            text = "학년",
            style = MaterialTheme.typography.titleSmall,
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
