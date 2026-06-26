package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "user_login_logs")
data class UserLoginLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val email: String,
    val displayName: String,
    val eventType: String, // "LOGIN" or "LOGOUT"
    val timestamp: Long = System.currentTimeMillis()
) {
    val formattedDateTime: String
        get() {
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
}
