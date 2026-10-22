package com.example.dueordie.data.notion

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

class NotionPreferences(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var integrationToken: String
        get() = prefs.getString(KEY_TOKEN, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_TOKEN, value.trim()).apply() }
    var rawTargetInput: String
        get() = prefs.getString(KEY_TARGET, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_TARGET, value.trim()).apply() }
    var parentPageId: String?
        get() = prefs.getString(KEY_PARENT_PAGE, null)
        set(value) { prefs.edit().putString(KEY_PARENT_PAGE, value).apply() }
    var tasksDatabaseId: String?
        get() = prefs.getString(KEY_DATABASE_ID, null)
        set(value) { prefs.edit().putString(KEY_DATABASE_ID, value).apply() }
    var databaseTitle: String?
        get() = prefs.getString(KEY_DATABASE_TITLE, null)
        set(value) { prefs.edit().putString(KEY_DATABASE_TITLE, value).apply() }
    var titlePropertyName: String
        get() = prefs.getString(KEY_TITLE_PROPERTY, "Task").orEmpty().ifBlank { "Task" }
        set(value) { prefs.edit().putString(KEY_TITLE_PROPERTY, value).apply() }
    var isAutoSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SYNC, true)
        set(value) { prefs.edit().putBoolean(KEY_AUTO_SYNC, value).apply() }
    var lastSyncTimestamp: Long?
        get() = if (prefs.contains(KEY_LAST_SYNC)) prefs.getLong(KEY_LAST_SYNC, 0L) else null
        set(value) { prefs.edit().also { if (value == null) it.remove(KEY_LAST_SYNC) else it.putLong(KEY_LAST_SYNC, value) }.apply() }
    var lastSyncError: String?
        get() = prefs.getString(KEY_LAST_ERROR, null)
        set(value) { prefs.edit().putString(KEY_LAST_ERROR, value).apply() }
    var lastPlanRefreshDate: String?
        get() = prefs.getString(KEY_LAST_PLAN_REFRESH_DATE, null)
        set(value) { prefs.edit().putString(KEY_LAST_PLAN_REFRESH_DATE, value).apply() }
    var schemaVersion: Int
        get() = prefs.getInt(KEY_SCHEMA_VERSION, 0)
        set(value) { prefs.edit().putInt(KEY_SCHEMA_VERSION, value).apply() }

    val pendingArchivedNotionPageIds: Set<String>
        get() = prefs.getStringSet(KEY_PENDING_ARCHIVES, emptySet()).orEmpty().toSet()

    fun registerListener(listener: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val wrapped = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> listener() }
        prefs.registerOnSharedPreferenceChangeListener(wrapped)
        return wrapped
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    @Synchronized
    fun addPendingArchivePageId(pageId: String) {
        val updated = pendingArchivedNotionPageIds + pageId
        prefs.edit().putStringSet(KEY_PENDING_ARCHIVES, updated).apply()
    }

    @Synchronized
    fun removePendingArchivePageId(pageId: String) {
        val updated = pendingArchivedNotionPageIds - pageId
        prefs.edit().putStringSet(KEY_PENDING_ARCHIVES, updated).apply()
    }

    fun saveConnection(token: String, rawTarget: String, info: NotionDatabaseInfo, parentId: String?) {
        prefs.edit()
            .putString(KEY_TOKEN, token.trim())
            .putString(KEY_TARGET, rawTarget.trim())
            .putString(KEY_DATABASE_ID, info.databaseId)
            .putString(KEY_DATABASE_TITLE, info.databaseTitle)
            .putString(KEY_TITLE_PROPERTY, info.titlePropertyName)
            .putString(KEY_PARENT_PAGE, parentId)
            .putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
            .putBoolean(KEY_AUTO_SYNC, true)
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    fun disconnect() {
        prefs.edit()
            .remove(KEY_TOKEN).remove(KEY_TARGET).remove(KEY_PARENT_PAGE)
            .remove(KEY_DATABASE_ID).remove(KEY_DATABASE_TITLE).remove(KEY_TITLE_PROPERTY)
            .remove(KEY_LAST_SYNC).remove(KEY_LAST_ERROR).remove(KEY_PENDING_ARCHIVES)
            .remove(KEY_LAST_PLAN_REFRESH_DATE).remove(KEY_SCHEMA_VERSION)
            .putBoolean(KEY_AUTO_SYNC, true)
            .apply()
    }

    companion object {
        const val PREFS_NAME = "due_or_die_notion_prefs"
        private const val KEY_TOKEN = "integration_token"
        private const val KEY_TARGET = "raw_target_input"
        private const val KEY_PARENT_PAGE = "parent_page_id"
        private const val KEY_DATABASE_ID = "tasks_database_id"
        private const val KEY_DATABASE_TITLE = "database_title"
        private const val KEY_TITLE_PROPERTY = "title_property_name"
        private const val KEY_AUTO_SYNC = "auto_sync_enabled"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val KEY_LAST_ERROR = "last_sync_error"
        private const val KEY_LAST_PLAN_REFRESH_DATE = "last_plan_refresh_date"
        private const val KEY_SCHEMA_VERSION = "notion_schema_version"
        const val CURRENT_SCHEMA_VERSION = 2
        private const val KEY_PENDING_ARCHIVES = "pending_archived_notion_page_ids"

        private val COMPACT_UUID = Regex("(?i)([0-9a-f]{32})$")
        private val HYPHENATED_UUID = Regex("(?i)([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$")
        private val URL_SUFFIX_UUID = Regex("(?i)(?:^|[-/])([0-9a-f]{32}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$")

        fun extractNotionUuid(input: String): String? {
            val clean = input.trim().substringBefore('?').substringBefore('#').trimEnd('/')
            val candidate = clean.substringAfterLast('/')
            val match = HYPHENATED_UUID.find(candidate)?.groupValues?.get(1)
                ?: COMPACT_UUID.find(candidate)?.groupValues?.get(1)
                ?: URL_SUFFIX_UUID.find(candidate)?.groupValues?.get(1)
                ?: return null
            val hex = match.replace("-", "").lowercase(Locale.ROOT)
            if (hex.length != 32) return null
            return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
        }
    }
}

data class NotionDatabaseInfo(
    val databaseId: String,
    val titlePropertyName: String,
    val databaseTitle: String
)
