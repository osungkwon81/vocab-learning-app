package com.gwon.vocablearning.domain.service

import com.gwon.vocablearning.domain.model.WordProgress

class StudyDeckPlanner {
    fun prioritize(
        progress: List<WordProgress>,
        count: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<WordProgress> {
        val sorted = progress.sortedWith(
            compareBy<WordProgress> { priorityBucket(it, nowMillis) }
                .thenByDescending { it.stat.wrongCount }
                .thenBy { it.stat.lastSolvedAt ?: Long.MAX_VALUE }
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

    private fun priorityBucket(
        progress: WordProgress,
        nowMillis: Long,
    ): Int =
        when {
            progress.stat.wrongCount > 0 -> 0
            progress.stat.lastSolvedAt != null &&
                nowMillis - progress.stat.lastSolvedAt >= OLD_WORD_THRESHOLD_MS -> 1
            progress.stat.averageElapsedMs >= SLOW_RESPONSE_THRESHOLD_MS -> 2
            else -> 3
        }

    companion object {
        const val SLOW_RESPONSE_THRESHOLD_MS = 8_000L
        const val OLD_WORD_THRESHOLD_MS = 1000L * 60 * 60 * 24 * 3
    }
}
