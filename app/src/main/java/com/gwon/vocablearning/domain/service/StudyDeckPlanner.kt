package com.gwon.vocablearning.domain.service

import com.gwon.vocablearning.domain.model.WordProgress
import com.gwon.vocablearning.domain.model.WordStat

class StudyDeckPlanner {
    fun buildLearningDeck(
        progress: List<WordProgress>,
        count: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<WordProgress> = buildSessionDeck(progress, count, nowMillis, learningQuota)

    fun buildQuizDeck(
        progress: List<WordProgress>,
        count: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<WordProgress> = buildSessionDeck(progress, count, nowMillis, quizQuota)

    fun rank(
        progress: List<WordProgress>,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<WordProgress> =
        progress.sortedWith(
            compareByDescending<WordProgress> { priorityScore(it, nowMillis) }
                .thenBy { it.stat.nextReviewAt ?: Long.MAX_VALUE }
                .thenBy { it.stat.lastSolvedAt ?: 0L }
                .thenByDescending { it.stat.averageElapsedMs }
                .thenBy { it.stat.memoryStrength }
                .thenBy { it.stat.totalSolvedCount }
                .thenBy { it.entry.word },
        )

    fun prioritize(
        progress: List<WordProgress>,
        count: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<WordProgress> = buildLearningDeck(progress, count, nowMillis)

    private fun priorityScore(
        progress: WordProgress,
        nowMillis: Long,
    ): Int {
        val stat = progress.stat
        val unseenScore = if (isUnseen(stat)) UNSEEN_WEIGHT else 0
        val dueScore = when {
            isDue(stat, nowMillis) -> DUE_WEIGHT + overdueHours(stat, nowMillis).coerceAtMost(MAX_OVERDUE_HOURS)
            stat.needReview -> REVIEW_WEIGHT
            else -> 0
        }
        val strengthScore = (MAX_MEMORY_STRENGTH - stat.memoryStrength.coerceIn(0, MAX_MEMORY_STRENGTH)) * MEMORY_GAP_WEIGHT
        val answerScore = (stat.wrongCount * WRONG_WEIGHT) - (stat.correctCount * CORRECT_WEIGHT)
        val reviewScore = when {
            stat.averageElapsedMs >= SLOW_RESPONSE_THRESHOLD_MS -> SLOW_RESPONSE_WEIGHT
            stat.lastSolvedAt != null && nowMillis - stat.lastSolvedAt >= OLD_WORD_THRESHOLD_MS -> OLD_WORD_WEIGHT
            else -> 0
        }
        return unseenScore + dueScore + strengthScore + answerScore + reviewScore
    }

    private fun buildSessionDeck(
        progress: List<WordProgress>,
        count: Int,
        nowMillis: Long,
        quota: SessionQuota,
    ): List<WordProgress> {
        val ranked = rank(progress, nowMillis)
        if (ranked.isEmpty()) return emptyList()

        val targetCount = count.coerceAtLeast(1)
        val uniqueTarget = minOf(targetCount, ranked.size)
        val buckets = bucketize(ranked, nowMillis)
        val picks = mutableListOf<WordProgress>()
        val quotas = quota.allocate(uniqueTarget)

        fun takeFrom(bucket: Bucket, limit: Int) {
            if (limit <= 0) return
            var added = 0
            for (next in buckets.getValue(bucket)) {
                if (picks.size >= uniqueTarget || added >= limit) {
                    break
                }
                if (next !in picks) {
                    picks += next
                    added += 1
                }
            }
        }

        takeFrom(Bucket.UNSEEN, quotas.unseen)
        takeFrom(Bucket.DUE, quotas.due)
        takeFrom(Bucket.STRENGTHENING, quotas.strengthening)
        takeFrom(Bucket.MASTERED, uniqueTarget)

        ranked.forEach { candidate ->
            if (picks.size >= uniqueTarget) return@forEach
            if (candidate !in picks) picks += candidate
        }

        return List(targetCount) { index -> picks[index % picks.size] }
    }

    private fun bucketize(
        ranked: List<WordProgress>,
        nowMillis: Long,
    ): Map<Bucket, MutableList<WordProgress>> =
        Bucket.entries.associateWithTo(linkedMapOf()) { mutableListOf<WordProgress>() }.also { buckets ->
            ranked.forEach { progress ->
                buckets.getValue(classify(progress, nowMillis)).add(progress)
            }
        }

    private fun classify(
        progress: WordProgress,
        nowMillis: Long,
    ): Bucket {
        val stat = progress.stat
        return when {
            isUnseen(stat) -> Bucket.UNSEEN
            isDue(stat, nowMillis) -> Bucket.DUE
            stat.needReview || stat.memoryStrength <= 1 || stat.wrongCount >= stat.correctCount -> Bucket.STRENGTHENING
            else -> Bucket.MASTERED
        }
    }

    private fun isUnseen(stat: WordStat): Boolean =
        stat.totalSolvedCount == 0

    private fun isDue(
        stat: WordStat,
        nowMillis: Long,
    ): Boolean = stat.totalSolvedCount > 0 && ((stat.nextReviewAt ?: Long.MAX_VALUE) <= nowMillis)

    private fun overdueHours(
        stat: WordStat,
        nowMillis: Long,
    ): Int {
        val nextReviewAt = stat.nextReviewAt ?: return 0
        return ((nowMillis - nextReviewAt) / HOUR_MS).toInt().coerceAtLeast(0)
    }

    data class SessionQuota(
        val unseenPercent: Int,
        val duePercent: Int,
        val strengtheningPercent: Int,
    ) {
        fun allocate(count: Int): SessionQuotaAllocation {
            val unseen = (count * unseenPercent) / 100
            val due = (count * duePercent) / 100
            val strengthening = (count * strengtheningPercent) / 100
            var remainder = count - unseen - due - strengthening

            var unseenAdjusted = unseen
            var dueAdjusted = due
            var strengtheningAdjusted = strengthening

            if (remainder > 0) {
                dueAdjusted += 1
                remainder -= 1
            }
            if (remainder > 0) {
                unseenAdjusted += 1
                remainder -= 1
            }
            if (remainder > 0) {
                strengtheningAdjusted += remainder
            }

            return SessionQuotaAllocation(
                unseen = unseenAdjusted,
                due = dueAdjusted,
                strengthening = strengtheningAdjusted,
            )
        }
    }

    data class SessionQuotaAllocation(
        val unseen: Int,
        val due: Int,
        val strengthening: Int,
    )

    private enum class Bucket {
        UNSEEN,
        DUE,
        STRENGTHENING,
        MASTERED,
    }

    companion object {
        const val SLOW_RESPONSE_THRESHOLD_MS = 8_000L
        const val OLD_WORD_THRESHOLD_MS = 1000L * 60 * 60 * 24 * 3
        private const val HOUR_MS = 1000L * 60 * 60
        private const val MAX_MEMORY_STRENGTH = 5
        private const val MAX_OVERDUE_HOURS = 72
        private const val UNSEEN_WEIGHT = 10_000
        private const val DUE_WEIGHT = 6_000
        private const val WRONG_WEIGHT = 4
        private const val CORRECT_WEIGHT = 2
        private const val REVIEW_WEIGHT = 3_000
        private const val MEMORY_GAP_WEIGHT = 300
        private const val SLOW_RESPONSE_WEIGHT = 2
        private const val OLD_WORD_WEIGHT = 1
        private val learningQuota = SessionQuota(unseenPercent = 30, duePercent = 50, strengtheningPercent = 20)
        private val quizQuota = SessionQuota(unseenPercent = 20, duePercent = 50, strengtheningPercent = 30)
    }
}
