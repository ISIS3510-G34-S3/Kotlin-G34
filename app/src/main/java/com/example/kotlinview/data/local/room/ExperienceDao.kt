package com.example.kotlinview.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ExperienceDao {

    @Query("SELECT * FROM experiences")
    suspend fun getAll(): List<ExperienceEntity>

    @Query("SELECT * FROM experiences WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<ExperienceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ExperienceEntity>)

    @Query("DELETE FROM experiences")
    suspend fun clearAll()
}
