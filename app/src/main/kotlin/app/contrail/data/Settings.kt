package app.contrail.data

import android.content.Context
import app.contrail.core.aerodatabox.AdbProvider

/**
 * User settings in app-private SharedPreferences. The API key never ships in the code or the repo;
 * the user pastes their own key on the Settings screen.
 */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("contrail", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString("apiKey", "").orEmpty()
        set(v) = prefs.edit().putString("apiKey", v.trim()).apply()

    var provider: AdbProvider
        get() = runCatching { AdbProvider.valueOf(prefs.getString("provider", null) ?: "") }.getOrDefault(AdbProvider.RapidApi)
        set(v) = prefs.edit().putString("provider", v.name).apply()

    var calendarIds: Set<Long>
        get() = prefs.getStringSet("calendarIds", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
        set(v) = prefs.edit().putStringSet("calendarIds", v.map { it.toString() }.toSet()).apply()

    var alertsEnabled: Boolean
        get() = prefs.getBoolean("alerts", true)
        set(v) = prefs.edit().putBoolean("alerts", v).apply()
}
