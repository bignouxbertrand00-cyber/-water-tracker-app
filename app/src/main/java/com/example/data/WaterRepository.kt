package com.example.data

import kotlinx.coroutines.flow.Flow

class WaterRepository(private val waterDao: WaterDao) {
    val allLogsFlow: Flow<List<WaterLog>> = waterDao.getAllLogsFlow()
    val userProfileFlow: Flow<WaterProfile?> = waterDao.getProfileFlow()
    val allLoginLogsFlow: Flow<List<UserLoginLog>> = waterDao.getAllLoginLogsFlow()

    suspend fun insertLog(log: WaterLog) {
        waterDao.insertLog(log)
    }

    suspend fun deleteLog(log: WaterLog) {
        waterDao.deleteLog(log)
    }

    suspend fun clearAllLogs() {
        waterDao.clearAllLogs()
    }

    suspend fun getProfileDirect(): WaterProfile? {
        return waterDao.getProfileDirect()
    }

    suspend fun saveProfile(profile: WaterProfile) {
        waterDao.insertOrUpdateProfile(profile)
    }

    suspend fun insertLoginLog(log: UserLoginLog) {
        waterDao.insertLoginLog(log)
    }

    suspend fun clearAllLoginLogs() {
        waterDao.clearAllLoginLogs()
    }
}
