package com.bttrcal.app.data.local

import android.content.Context
import com.bttrcal.app.data.security.SecretVault
import com.bttrcal.app.domain.model.AiConfig
import com.bttrcal.app.domain.model.AiProviderType
import com.bttrcal.app.domain.model.CalendarViewMode
import com.bttrcal.app.domain.model.GlassVariant
import com.bttrcal.app.domain.model.SyncIntervalOption
import com.bttrcal.app.domain.model.ThemeMode
import com.bttrcal.app.domain.model.WidgetStyleConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.LIQUID_GLASS,
    val glassVariant: GlassVariant = GlassVariant.LIGHT_GLASS,
    val defaultViewMode: CalendarViewMode = CalendarViewMode.MONTH,
    val syncInterval: SyncIntervalOption = SyncIntervalOption.MIN_30,
    val wifiOnlySync: Boolean = false,
    val syncOnLaunch: Boolean = true,
    val syncAfterEdit: Boolean = true,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val showWeekNumbers: Boolean = true,
    val defaultReminderMinutes: Int = 15,
    val defaultEventDurationMinutes: Int = 60,
    val aiConfig: AiConfig = AiConfig(),
    val widgetStyle: WidgetStyleConfig = WidgetStyleConfig(),
    val lastFullSyncAt: Long = 0L,
    val lastSyncSummary: String = "Noch nicht synchronisiert"
)

class PreferencesRepository(
    context: Context,
    private val secretVault: SecretVault
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val themeMode = enumOrDefault(prefs.getString(KEY_THEME_MODE, null), ThemeMode.LIQUID_GLASS)
        val glassVariant = enumOrDefault(prefs.getString(KEY_GLASS_VARIANT, null), GlassVariant.LIGHT_GLASS)
        val viewMode = enumOrDefault(prefs.getString(KEY_DEFAULT_VIEW, null), CalendarViewMode.MONTH)
        val syncInterval = SyncIntervalOption.fromMinutes(prefs.getLong(KEY_SYNC_INTERVAL_MIN, 30L))
        val firstDay = enumOrDefault(prefs.getString(KEY_FIRST_DOW, null), DayOfWeek.MONDAY)
        val aiProvider = enumOrDefault(prefs.getString(KEY_AI_PROVIDER, null), AiProviderType.GEMINI)

        val widgetTheme = enumOrDefault(prefs.getString(KEY_WIDGET_THEME, null), ThemeMode.LIQUID_GLASS)
        val widgetGlassVariant = enumOrDefault(prefs.getString(KEY_WIDGET_GLASS_VARIANT, null), GlassVariant.LIGHT_GLASS)

        val aiConfig = AiConfig(
            provider = aiProvider,
            model = prefs.getString(KEY_AI_MODEL, aiProvider.defaultModel) ?: aiProvider.defaultModel,
            baseUrl = prefs.getString(KEY_AI_BASE_URL, aiProvider.defaultBaseUrl) ?: aiProvider.defaultBaseUrl,
            enabled = prefs.getBoolean(KEY_AI_ENABLED, true),
            hasStoredKey = secretVault.hasSecret(secretVault.aiKeyName(aiProvider.name))
        )

        val widgetStyle = WidgetStyleConfig(
            themeMode = widgetTheme,
            glassVariant = widgetGlassVariant,
            glassOpacity = prefs.getFloat(KEY_WIDGET_OPACITY, 0.78f),
            showWeekNumbers = prefs.getBoolean(KEY_SHOW_WEEK_NUMBERS, true),
            firstDayOfWeek = firstDay,
            compactMode = prefs.getBoolean(KEY_WIDGET_COMPACT, false)
        )

        return AppSettings(
            themeMode = themeMode,
            glassVariant = glassVariant,
            defaultViewMode = viewMode,
            syncInterval = syncInterval,
            wifiOnlySync = prefs.getBoolean(KEY_WIFI_ONLY, false),
            syncOnLaunch = prefs.getBoolean(KEY_SYNC_ON_LAUNCH, true),
            syncAfterEdit = prefs.getBoolean(KEY_SYNC_AFTER_EDIT, true),
            firstDayOfWeek = firstDay,
            showWeekNumbers = prefs.getBoolean(KEY_SHOW_WEEK_NUMBERS, true),
            defaultReminderMinutes = prefs.getInt(KEY_DEFAULT_REMINDER, 15),
            defaultEventDurationMinutes = prefs.getInt(KEY_DEFAULT_DURATION, 60),
            aiConfig = aiConfig,
            widgetStyle = widgetStyle,
            lastFullSyncAt = prefs.getLong(KEY_LAST_SYNC_AT, 0L),
            lastSyncSummary = prefs.getString(KEY_LAST_SYNC_SUMMARY, "Bereit") ?: "Bereit"
        )
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    fun setGlassVariant(variant: GlassVariant) {
        prefs.edit().putString(KEY_GLASS_VARIANT, variant.name).apply()
        _settings.value = _settings.value.copy(glassVariant = variant)
    }

    fun setDefaultViewMode(mode: CalendarViewMode) {
        prefs.edit().putString(KEY_DEFAULT_VIEW, mode.name).apply()
        _settings.value = _settings.value.copy(defaultViewMode = mode)
    }

    fun setSyncInterval(option: SyncIntervalOption) {
        prefs.edit().putLong(KEY_SYNC_INTERVAL_MIN, option.minutes).apply()
        _settings.value = _settings.value.copy(syncInterval = option)
    }

    fun setWifiOnlySync(wifiOnly: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, wifiOnly).apply()
        _settings.value = _settings.value.copy(wifiOnlySync = wifiOnly)
    }

    fun setSyncOnLaunch(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SYNC_ON_LAUNCH, enabled).apply()
        _settings.value = _settings.value.copy(syncOnLaunch = enabled)
    }

    fun setSyncAfterEdit(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SYNC_AFTER_EDIT, enabled).apply()
        _settings.value = _settings.value.copy(syncAfterEdit = enabled)
    }

    fun setFirstDayOfWeek(day: DayOfWeek) {
        prefs.edit().putString(KEY_FIRST_DOW, day.name).apply()
        _settings.value = _settings.value.copy(
            firstDayOfWeek = day,
            widgetStyle = _settings.value.widgetStyle.copy(firstDayOfWeek = day)
        )
    }

    fun setShowWeekNumbers(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_WEEK_NUMBERS, show).apply()
        _settings.value = _settings.value.copy(
            showWeekNumbers = show,
            widgetStyle = _settings.value.widgetStyle.copy(showWeekNumbers = show)
        )
    }

    fun setDefaultReminderMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_DEFAULT_REMINDER, minutes).apply()
        _settings.value = _settings.value.copy(defaultReminderMinutes = minutes)
    }

    fun setDefaultEventDurationMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_DEFAULT_DURATION, minutes).apply()
        _settings.value = _settings.value.copy(defaultEventDurationMinutes = minutes)
    }

    fun updateAiConfig(
        provider: AiProviderType,
        model: String,
        baseUrl: String,
        enabled: Boolean,
        newApiKey: String? = null
    ) {
        val secretName = secretVault.aiKeyName(provider.name)
        if (newApiKey != null) {
            if (newApiKey.isBlank()) {
                secretVault.removeSecret(secretName)
            } else {
                secretVault.putSecret(secretName, newApiKey.trim())
            }
        }
        val cleanModel = model.trim().ifBlank { provider.defaultModel }
        val cleanUrl = baseUrl.trim().ifBlank { provider.defaultBaseUrl }

        prefs.edit()
            .putString(KEY_AI_PROVIDER, provider.name)
            .putString(KEY_AI_MODEL, cleanModel)
            .putString(KEY_AI_BASE_URL, cleanUrl)
            .putBoolean(KEY_AI_ENABLED, enabled)
            .apply()

        _settings.value = _settings.value.copy(
            aiConfig = AiConfig(
                provider = provider,
                model = cleanModel,
                baseUrl = cleanUrl,
                enabled = enabled,
                hasStoredKey = secretVault.hasSecret(secretName)
            )
        )
    }

    fun getActiveAiKey(provider: AiProviderType = _settings.value.aiConfig.provider): String? =
        secretVault.getSecret(secretVault.aiKeyName(provider.name))

    fun updateWidgetStyle(style: WidgetStyleConfig) {
        prefs.edit()
            .putString(KEY_WIDGET_THEME, style.themeMode.name)
            .putString(KEY_WIDGET_GLASS_VARIANT, style.glassVariant.name)
            .putFloat(KEY_WIDGET_OPACITY, style.glassOpacity.coerceIn(0.35f, 1.0f))
            .putBoolean(KEY_WIDGET_COMPACT, style.compactMode)
            .apply()
        _settings.value = _settings.value.copy(widgetStyle = style)
    }

    fun recordSyncResult(timestamp: Long, summary: String) {
        prefs.edit()
            .putLong(KEY_LAST_SYNC_AT, timestamp)
            .putString(KEY_LAST_SYNC_SUMMARY, summary)
            .apply()
        _settings.value = _settings.value.copy(
            lastFullSyncAt = timestamp,
            lastSyncSummary = summary
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, fallback: T): T {
        if (raw.isNullOrBlank()) return fallback
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
    }

    companion object {
        private const val PREFS_NAME = "bttrcal_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_GLASS_VARIANT = "glass_variant"
        private const val KEY_DEFAULT_VIEW = "default_view"
        private const val KEY_SYNC_INTERVAL_MIN = "sync_interval_min"
        private const val KEY_WIFI_ONLY = "wifi_only_sync"
        private const val KEY_SYNC_ON_LAUNCH = "sync_on_launch"
        private const val KEY_SYNC_AFTER_EDIT = "sync_after_edit"
        private const val KEY_FIRST_DOW = "first_day_of_week"
        private const val KEY_SHOW_WEEK_NUMBERS = "show_week_numbers"
        private const val KEY_DEFAULT_REMINDER = "default_reminder_min"
        private const val KEY_DEFAULT_DURATION = "default_duration_min"
        private const val KEY_AI_PROVIDER = "ai_provider"
        private const val KEY_AI_MODEL = "ai_model"
        private const val KEY_AI_BASE_URL = "ai_base_url"
        private const val KEY_AI_ENABLED = "ai_enabled"
        private const val KEY_WIDGET_THEME = "widget_theme"
        private const val KEY_WIDGET_GLASS_VARIANT = "widget_glass_variant"
        private const val KEY_WIDGET_OPACITY = "widget_opacity"
        private const val KEY_WIDGET_COMPACT = "widget_compact"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_LAST_SYNC_SUMMARY = "last_sync_summary"
    }
}
