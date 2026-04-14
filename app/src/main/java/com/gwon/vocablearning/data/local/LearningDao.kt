package com.gwon.vocablearning.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WordStatDao {
    @Query("SELECT * FROM word_stat")
    suspend fun getAll(): List<WordStatEntity>

    @Query("SELECT * FROM word_stat WHERE word_id = :wordId")
    suspend fun getByWordId(wordId: Long): WordStatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WordStatEntity)
}

@Dao
interface QuizHistoryDao {
    @Insert
    suspend fun insert(entity: QuizHistoryEntity)

    @Query("SELECT * FROM quiz_history ORDER BY solved_at DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<QuizHistoryEntity>
}

