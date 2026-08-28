package uz.kmax.documents.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("lumora_prefs", Context.MODE_PRIVATE)

    var appearance: Int
        get() = prefs.getInt(KEY_APPEARANCE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) = prefs.edit().putInt(KEY_APPEARANCE, value).apply()

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    var ocrDailyCount: Int
        get() = prefs.getInt(KEY_OCR_DAILY_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_OCR_DAILY_COUNT, value).apply()

    var ocrLastDate: Long
        get() = prefs.getLong(KEY_OCR_LAST_DATE, 0L)
        set(value) = prefs.edit().putLong(KEY_OCR_LAST_DATE, value).apply()

    companion object {
        private const val KEY_APPEARANCE = "appearance"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_OCR_DAILY_COUNT = "ocr_daily_count"
        private const val KEY_OCR_LAST_DATE = "ocr_last_date"
    }
}
