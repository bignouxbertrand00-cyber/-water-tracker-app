package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterDao {
    @Query("SELECT * FROM water_logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<WaterLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: WaterLog)

    @Delete
    suspend fun deleteLog(log: WaterLog)

    @Query("DELETE FROM water_logs")
    suspend fun clearAllLogs()

    // Profile Operations
    @Query("SELECT * FROM user_profiles WHERE id = 1 LIMIT 1")
    fun getProfileFlow(): Flow<WaterProfile?>

    @Query("SELECT * FROM user_profiles WHERE id = 1 LIMIT 1")
    suspend fun getProfileDirect(): WaterProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: WaterProfile)

    // Login session logs Operations
    @Query("SELECT * FROM user_login_logs ORDER BY timestamp DESC")
    fun getAllLoginLogsFlow(): Flow<List<UserLoginLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoginLog(log: UserLoginLog)

    @Query("DELETE FROM user_login_logs")
    suspend fun clearAllLoginLogs()
}
