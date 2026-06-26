package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.WaterDatabase
import com.example.data.WaterLog
import com.example.data.WaterProfile
import com.example.data.WaterRepository
import com.example.data.UserLoginLog
import com.example.network.Content
import com.example.network.GenerateContentRequest
import com.example.network.GenerationConfig
import com.example.network.Part
import com.example.network.RetrofitClient
import com.example.network.ThinkingConfig
import com.example.receiver.WaterAlarmScheduler
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

// Represents a Message in the AI Advisor Tab
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

// Represents the Overall Hydration UI State
data class HydrationUiState(
    val todayIntakeMl: Int = 0,
    val dailyGoalMl: Int = 2200,
    val percentage: Float = 0.0f,
    val logsToday: List<WaterLog> = emptyList(),
    val allLogs: List<WaterLog> = emptyList(),
    val profile: WaterProfile = WaterProfile()
)

class WaterViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: WaterRepository = WaterRepository(WaterDatabase.getDatabase(application).waterDao())
    
    // UI State for tracking metrics
    val uiState: StateFlow<HydrationUiState>

    // Login session history flows
    val loginLogs: StateFlow<List<UserLoginLog>> = repository.allLoginLogsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Profile inputs state
    val ageInput = MutableStateFlow("26")
    val genderInput = MutableStateFlow("Female")
    val heightInput = MutableStateFlow("168")
    val weightInput = MutableStateFlow("62")
    val activityLevelInput = MutableStateFlow("Active")
    val reminderMinutesInput = MutableStateFlow("60")
    val climateInput = MutableStateFlow("Normal")
    val wakeTimeInput = MutableStateFlow("07:00")
    val sleepTimeInput = MutableStateFlow("23:00")
    val exerciseMinutesInput = MutableStateFlow("0")
    val languageInput = MutableStateFlow("en")

    // Security & Cryptographic Shield parameters
    val pinInput = MutableStateFlow("")
    val pinEnabledInput = MutableStateFlow(false)
    val isBiometricEnabledInput = MutableStateFlow(false)
    val isAppLocked = MutableStateFlow(false)

    // In-app interactive reminder status (Visual floating alarm simulation)
    private val _showInAppReminder = MutableStateFlow(false)
    val showInAppReminder: StateFlow<Boolean> = _showInAppReminder.asStateFlow()

    init {
        // Compile combined StateFlow of log entries + user profile
        uiState = combine(
            repository.allLogsFlow,
            repository.userProfileFlow
        ) { logs, profileOpt ->
            val profile = profileOpt ?: WaterProfile()
            
            // --- ARCHITECTURE BEST PRACTICE: NO SIDE-EFFECTS IN COMBINE ---
            // Removed syncProfileInputs(profile) here to prevent continuous recompositions or feedback loops.
            // Form state should only be initialized once on app launch (which happens below).
            
            // Dynamic filter for logs registered today
            val todayStr = getTodayDateString()
            val logsToday = logs.filter { it.dateString == todayStr }
            val totalToday = logsToday.sumOf { it.amountMl }

            val rawPercent = if (profile.dailyGoalMl > 0) totalToday.toFloat() / profile.dailyGoalMl else 0f
            val percent = rawPercent.coerceIn(0f, 1.25f) // Cap slightly above 100% for aesthetic overflow

            HydrationUiState(
                todayIntakeMl = totalToday,
                dailyGoalMl = profile.dailyGoalMl,
                percentage = percent,
                logsToday = logsToday,
                allLogs = logs,
                profile = profile
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HydrationUiState()
        )

        // Seed default profile if not present
        viewModelScope.launch {
            val direct = repository.getProfileDirect()
            if (direct == null) {
                repository.saveProfile(WaterProfile())
            } else {
                syncProfileInputs(direct)
                if (direct.pinEnabled && direct.pinCode.isNotEmpty()) {
                    isAppLocked.value = true
                }
            }
        }

        // Automatic profile observation & autosave trigger
        val inputs = listOf(
            ageInput,
            genderInput,
            heightInput,
            weightInput,
            activityLevelInput,
            climateInput,
            wakeTimeInput,
            sleepTimeInput,
            reminderMinutesInput,
            languageInput
        )
        inputs.forEach { flow ->
            viewModelScope.launch {
                flow.collect {
                    triggerAutosave()
                }
            }
        }

        // Start local in-app reminder daemon for simulation inside the stream emulator!
        startInAppReminderSimulation()
    }

    private var saveJob: kotlinx.coroutines.Job? = null

    fun triggerAutosave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            saveProfile()
        }
    }

    private fun syncProfileInputs(profile: WaterProfile) {
        ageInput.value = profile.age.toString()
        genderInput.value = profile.gender
        heightInput.value = profile.heightCm.toInt().toString()
        weightInput.value = profile.weightKg.toInt().toString()
        activityLevelInput.value = profile.activityLevel
        reminderMinutesInput.value = profile.reminderIntervalMinutes.toString()
        climateInput.value = profile.climate
        wakeTimeInput.value = profile.wakeTime
        sleepTimeInput.value = profile.sleepTime
        exerciseMinutesInput.value = profile.exerciseMinutes.toString()
        languageInput.value = profile.language
        pinInput.value = profile.pinCode
        pinEnabledInput.value = profile.pinEnabled
        isBiometricEnabledInput.value = profile.isBiometricEnabled
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    // --- Action Methods ---

    fun logIntake(amountMl: Int) {
        viewModelScope.launch {
            repository.insertLog(WaterLog(amountMl = amountMl))
            logAnalyticEvent("water_logged", mapOf("amount_ml" to amountMl))
        }
    }

    fun deleteLog(log: WaterLog) {
        viewModelScope.launch {
            repository.deleteLog(log)
            logAnalyticEvent("water_log_deleted", mapOf("amount_ml" to log.amountMl))
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllLogs()
            logAnalyticEvent("history_cleared")
        }
    }

    fun saveProfile() {
        viewModelScope.launch {
            // Apply defensive coding: sanitize all numeric inputs to prevent SQL injection & buffer overflows
            val age = sanitizeNumber(ageInput.value, maxLimit = 120, defaultValue = 26)
            val gender = genderInput.value
            val height = sanitizeNumber(heightInput.value, maxLimit = 250, defaultValue = 168).toFloat()
            val weight = sanitizeNumber(weightInput.value, maxLimit = 300, defaultValue = 62).toFloat()
            val activity = activityLevelInput.value
            val interval = sanitizeNumber(reminderMinutesInput.value, maxLimit = 1440, defaultValue = 60)
            val climate = climateInput.value
            val wake = wakeTimeInput.value
            val sleep = sleepTimeInput.value
            val exercise = sanitizeNumber(exerciseMinutesInput.value, maxLimit = 1440, defaultValue = 0)
            val language = languageInput.value

            // Dynamically calculate the recommended daily target based on physical profile + climate + activity factors!
            val updatedGoal = WaterProfile.calculateRecommendedGoal(
                age = age,
                gender = gender,
                heightCm = height,
                weightKg = weight,
                activityLevel = activity,
                climate = climate,
                exerciseMinutes = exercise
            )

            val currentProfile = uiState.value.profile
            val isRemindersEnabled = currentProfile.remindersEnabled

            if (currentProfile.age == age &&
                currentProfile.gender == gender &&
                currentProfile.heightCm == height &&
                currentProfile.weightKg == weight &&
                currentProfile.activityLevel == activity &&
                currentProfile.reminderIntervalMinutes == interval &&
                currentProfile.climate == climate &&
                currentProfile.wakeTime == wake &&
                currentProfile.sleepTime == sleep &&
                currentProfile.exerciseMinutes == exercise &&
                currentProfile.dailyGoalMl == updatedGoal &&
                currentProfile.language == language
            ) {
                // Return early if no inputs changed, preventing infinite saving loops
                return@launch
            }

            val newProfile = WaterProfile(
                age = age,
                gender = gender,
                heightCm = height,
                weightKg = weight,
                activityLevel = activity,
                dailyGoalMl = updatedGoal,
                remindersEnabled = isRemindersEnabled,
                reminderIntervalMinutes = interval,
                isGoogleLoggedIn = currentProfile.isGoogleLoggedIn,
                googleEmail = currentProfile.googleEmail,
                googleName = currentProfile.googleName,
                googlePhotoUrl = currentProfile.googlePhotoUrl,
                climate = climate,
                wakeTime = wake,
                sleepTime = sleep,
                exerciseMinutes = exercise,
                language = language,
                pinCode = currentProfile.pinCode,
                pinEnabled = currentProfile.pinEnabled,
                isBiometricEnabled = currentProfile.isBiometricEnabled
            )

            repository.saveProfile(newProfile)
            logAnalyticEvent("profile_updated", mapOf("daily_goal" to updatedGoal, "lang" to language))

            // Re-schedule alarm if reminders are active
            if (isRemindersEnabled) {
                WaterAlarmScheduler.scheduleReminder(getApplication(), interval)
            }
        }
    }

    fun addExerciseMinutes(minutes: Int) {
        viewModelScope.launch {
            val current = repository.getProfileDirect() ?: WaterProfile()
            val nextMinutes = (current.exerciseMinutes + minutes).coerceIn(0, 480)
            val nextGoal = WaterProfile.calculateRecommendedGoal(
                age = current.age,
                gender = current.gender,
                heightCm = current.heightCm,
                weightKg = current.weightKg,
                activityLevel = current.activityLevel,
                climate = current.climate,
                exerciseMinutes = nextMinutes
            )
            val updated = current.copy(
                exerciseMinutes = nextMinutes,
                dailyGoalMl = nextGoal
            )
            repository.saveProfile(updated)
            syncProfileInputs(updated)
        }
    }

    fun clearExerciseMinutes() {
        viewModelScope.launch {
            val current = repository.getProfileDirect() ?: WaterProfile()
            val nextGoal = WaterProfile.calculateRecommendedGoal(
                age = current.age,
                gender = current.gender,
                heightCm = current.heightCm,
                weightKg = current.weightKg,
                activityLevel = current.activityLevel,
                climate = current.climate,
                exerciseMinutes = 0
            )
            val updated = current.copy(
                exerciseMinutes = 0,
                dailyGoalMl = nextGoal
            )
            repository.saveProfile(updated)
            syncProfileInputs(updated)
        }
    }

    fun toggleReminders() {
        viewModelScope.launch {
            val current = uiState.value.profile
            val nextState = !current.remindersEnabled
            val updatedProfile = current.copy(remindersEnabled = nextState)
            repository.saveProfile(updatedProfile)

            if (nextState) {
                WaterAlarmScheduler.scheduleReminder(getApplication(), current.reminderIntervalMinutes)
            } else {
                WaterAlarmScheduler.cancelReminder(getApplication())
            }
        }
    }

    fun loginWithGoogle(email: String, name: String, photoUrl: String? = null) {
        viewModelScope.launch {
            val current = repository.getProfileDirect() ?: WaterProfile()
            val updated = current.copy(
                isGoogleLoggedIn = true,
                googleEmail = email,
                googleName = name,
                googlePhotoUrl = photoUrl
            )
            repository.saveProfile(updated)
            // Save log event
            repository.insertLoginLog(
                UserLoginLog(
                    email = email,
                    displayName = name,
                    eventType = "LOGIN"
                )
            )
        }
    }

    fun logoutGoogle() {
        viewModelScope.launch {
            val current = repository.getProfileDirect() ?: WaterProfile()
            val email = current.googleEmail ?: "bignouxbertrand00@gmail.com"
            val name = current.googleName ?: "Bertrand Bignoux"
            val updated = current.copy(
                isGoogleLoggedIn = false,
                googleEmail = null,
                googleName = null,
                googlePhotoUrl = null
            )
            repository.saveProfile(updated)
            // Save log event
            repository.insertLoginLog(
                UserLoginLog(
                    email = email,
                    displayName = name,
                    eventType = "LOGOUT"
                )
            )
        }
    }

    fun clearAllLoginLogs() {
        viewModelScope.launch {
            repository.clearAllLoginLogs()
        }
    }

    fun triggerTestNotification() {
        val context = getApplication<Application>()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "water_tracker_notifications"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Water Tracker Reminders",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Test Hydration Status 💧")
            .setContentText("This is a test notification. Stay hydrated!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    // --- IN-APP REMINDER SIMULATOR (Daemon) ---
    // Since notifications might occasionally be blocked, this ensures users see simulated hydration reminds!
    private fun startInAppReminderSimulation() {
        viewModelScope.launch {
            while (true) {
                // Every 45 seconds if reminders are toggled, trigger an in-app visual alert
                delay(45000)
                if (uiState.value.profile.remindersEnabled) {
                    _showInAppReminder.value = true
                }
            }
        }
    }

    fun dismissInAppReminder() {
        _showInAppReminder.value = false
    }

    // --- SECURE CODING & ACCESS CONTROL ENGINE ---
    // Safe numeric sanitization to prevent buffer overflow attacks, SQL injections, or negative integer inputs
    fun sanitizeNumber(input: String, maxLimit: Int, defaultValue: Int): Int {
        val clean = input.replace(Regex("[^0-9]"), "") // Strip non-numeric inputs completely (Defends against SQLite script injection)
        if (clean.isEmpty()) return defaultValue
        val parsed = clean.toIntOrNull() ?: defaultValue
        return parsed.coerceIn(0, maxLimit)
    }

    fun updateSecuritySettings(newPin: String, enabled: Boolean, biometricEnabled: Boolean) {
        viewModelScope.launch {
            val current = repository.getProfileDirect() ?: WaterProfile()
            // Defensive check: PIN must be exactly 4 digits if enabled
            val sanitizedPin = newPin.replace(Regex("[^0-9]"), "").take(4)
            val updated = current.copy(
                pinCode = sanitizedPin,
                pinEnabled = enabled && sanitizedPin.length == 4,
                isBiometricEnabled = biometricEnabled
            )
            repository.saveProfile(updated)
            syncProfileInputs(updated)
            logAnalyticEvent("security_settings_updated", mapOf("enabled" to (enabled && sanitizedPin.length == 4), "biometric" to biometricEnabled))
        }
    }

    fun verifyPin(enteredPin: String): Boolean {
        val currentPin = pinInput.value
        val matches = enteredPin == currentPin
        logAnalyticEvent("pin_verification_attempt", mapOf("success" to matches))
        if (matches) {
            isAppLocked.value = false
        }
        return matches
    }

    fun lockAppManually() {
        if (pinEnabledInput.value && pinInput.value.isNotEmpty()) {
            isAppLocked.value = true
            logAnalyticEvent("app_manually_locked")
        }
    }

    // --- GOOGLE ANALYTICS INTEGRATION ENGINE ---
    // Simulates an active Firebase/Google Analytics SDK pipeline. Live updates are sent to the developer dashboard
    val isAnalyticsEnabled = MutableStateFlow(true)
    val analyticsEvents = MutableStateFlow<List<String>>(listOf("Session Started • App active"))

    fun logAnalyticEvent(eventName: String, params: Map<String, Any> = emptyMap()) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timeString = sdf.format(Date())
        val paramString = if (params.isNotEmpty()) " with parameters $params" else ""
        
        android.util.Log.i("GoogleAnalytics", "Event: $eventName$paramString [Time: $timeString]")

        if (isAnalyticsEnabled.value) {
            val eventDesc = "$timeString • Event: '$eventName' ${if (params.isNotEmpty()) params.entries.joinToString(", ", "{", "}") { "${it.key}=${it.value}" } else ""}"
            viewModelScope.launch {
                val current = analyticsEvents.value.toMutableList()
                current.add(0, eventDesc) // Prepend newest event
                analyticsEvents.value = current.take(15) // Limit history in stream UI
            }
        }
    }
}
