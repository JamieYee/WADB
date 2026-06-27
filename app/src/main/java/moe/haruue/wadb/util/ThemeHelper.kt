package moe.haruue.wadb.util

import moe.haruue.wadb.WadbApplication

object ThemeHelper {
    const val THEME_DEFAULT = "default"
    const val THEME_GREEN = "green"
    const val THEME_PINK = "pink"
    const val KEY_LIGHT_THEME = "pref_light_theme"

    @JvmStatic
    fun setLightTheme(theme: String) {
        WadbApplication.defaultSharedPreferences.edit().putString(KEY_LIGHT_THEME, theme).apply()
    }

    @JvmStatic
    fun getTheme(): String {
        return WadbApplication.defaultSharedPreferences.getString(KEY_LIGHT_THEME, THEME_DEFAULT) ?: THEME_DEFAULT
    }
}
