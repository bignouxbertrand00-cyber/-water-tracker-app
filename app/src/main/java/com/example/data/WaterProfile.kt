package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class WaterProfile(
    @PrimaryKey val id: Int = 1, // Singleton row
    val age: Int = 26,
    val gender: String = "Female", // "Male", "Female", "Other"
    val heightCm: Float = 168f,
    val weightKg: Float = 62f,
    val activityLevel: String = "Active", // "Sedentary", "Active", "Highly Active"
    val dailyGoalMl: Int = 2200,
    val remindersEnabled: Boolean = true,
    val reminderIntervalMinutes: Int = 60,
    val isGoogleLoggedIn: Boolean = false,
    val googleEmail: String? = null,
    val googleName: String? = null,
    val googlePhotoUrl: String? = null,
    val climate: String = "Normal", // "Normal", "Hot/Tropical", "Cold"
    val wakeTime: String = "07:00", // e.g. "07:00"
    val sleepTime: String = "23:00", // e.g. "23:00"
    val exerciseMinutes: Int = 0, // intense dynamic physical activity minutes logged for today
    val language: String = "en", // "en", "fr", "es", "de"
    val pinCode: String = "", // App Lock 4-digit PIN
    val pinEnabled: Boolean = false, // Is the security PIN lock enabled?
    val isBiometricEnabled: Boolean = false // Is biometric/face security simulated/active?
) {
    companion object {
        fun calculateRecommendedGoal(
            age: Int,
            gender: String,
            heightCm: Float,
            weightKg: Float,
            activityLevel: String,
            climate: String = "Normal",
            exerciseMinutes: Int = 0
        ): Int {
            // Scientific baseline: ~35ml of liquid per kilogram of body weight
            val baseByWeight = weightKg * 35.0f

            // Age impact factor
            val ageFactor = when {
                age < 30 -> 1.10f
                age <= 55 -> 1.00f
                else -> 0.85f
            }

            // Height-based surface area metabolic adjustment
            val heightAdjustment = (heightCm - 165f) * 3f // 3ml per cm difference from 165cm

            // Gender metabolic adjustment
            val genderBonus = when (gender.lowercase()) {
                "male" -> 350
                "other" -> 150
                else -> 0
            }

            // Exertion level adjustment
            val activityBonus = when (activityLevel.lowercase()) {
                "sedentary" -> 0
                "active" -> 500
                "highly active" -> 1000
                else -> 300
            }

            // Dynamic Climate Factor
            // Mauritius tropical climate shift daily target upwards (+500ml)
            val climateBonus = when (climate.lowercase()) {
                "hot/tropical", "tropical" -> 500
                "cold" -> -200
                else -> 0
            }

            // Dynamic Exercise Tracking Factor
            // Add ~350 - 500ml (recommended 400ml) for every 30 minutes of intense physical activity
            val exerciseBonus = (exerciseMinutes / 30) * 400

            val computed = (baseByWeight * ageFactor + heightAdjustment + genderBonus + activityBonus + climateBonus + exerciseBonus).toInt()

            // Keep daily goals within standard safe ranges: 1000ml to 5500ml
            return computed.coerceIn(1000, 5500)
        }
    }
}
