package app.contrail.data

import android.content.Context

/** User settings in app-private SharedPreferences. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("contrail", Context.MODE_PRIVATE)

    var calendarIds: Set<Long>
        get() = prefs.getStringSet("calendarIds", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
        set(v) = prefs.edit().putStringSet("calendarIds", v.map { it.toString() }.toSet()).apply()
}
