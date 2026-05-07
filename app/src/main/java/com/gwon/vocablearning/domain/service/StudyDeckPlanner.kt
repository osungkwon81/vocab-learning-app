package com.gwon.vocablearning.domain.service

import com.gwon.vocablearning.domain.model.WordProgress

class StudyDeckPlanner {
    fun prioritize(
        progress: List<WordProgress>,
        count: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<WordProgress> {
        val sorted = progress.sortedWith(
            compareByDescending<WordProgress> { priorityScore(it, nowMillis) }
                .thenBy { it.stat.lastSolvedAt ?: 0L }
                .thenByDescending { it.stat.averageElapsedMs }
                .thenBy { it.stat.totalSolvedCount }
                .thenBy { it.entry.word },
        )

        if (sorted.isEmpty()) {
            return emptyList()
        }

        val targetCount = count.coerceAtLeast(1)
        return List(targetCount) { index -> sorted[index % sorted.size] }
    }

    private fun priorityScore(
        progress: WordProgress,
        nowMillis: Long,
    ): Int {
        val stat = progress.stat
        val answerScore = (stat.wrongCount * WRONG_WEIGHT) - (stat.correctCount * CORRECT_WEIGHT)
        val reviewScore = when {
            stat.needReview -> REVIEW_WEIGHT
            stat.averageElapsedMs >= SLOW_RESPONSE_THRESHOLD_MS -> SLOW_RESPONSE_WEIGHT
            stat.lastSolvedAt != null && nowMillis - stat.lastSolvedAt >= OLD_WORD_THRESHOLD_MS -> OLD_WORD_WEIGHT
            else -> 0
        }
        return answerScore + reviewScore
    }

    companion object {
        const val SLOW_RESPONSE_THRESHOLD_MS = 8_000L
        const val OLD_WORD_THRESHOLD_MS = 1000L * 60 * 60 * 24 * 3
        private const val WRONG_WEIGHT = 4
        private const val CORRECT_WEIGHT = 2
        private const val REVIEW_WEIGHT = 3
        private const val SLOW_RESPONSE_WEIGHT = 2
        private const val OLD_WORD_WEIGHT = 1
    }
}
