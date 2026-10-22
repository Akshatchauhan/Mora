package com.example.dueordie.data.notion

import com.example.dueordie.data.model.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class NotionHttpResponse(val status: Int, val json: JSONObject)

fun interface NotionHttpTransport {
    fun execute(method: String, path: String, token: String, body: JSONObject?): NotionHttpResponse
}

private class UrlConnectionNotionTransport : NotionHttpTransport {
    override fun execute(method: String, path: String, token: String, body: JSONObject?): NotionHttpResponse {
        val connection = (URL("https://api.notion.com/v1$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Notion-Version", "2022-06-28")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (body != null) doOutput = true
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return NotionHttpResponse(status, runCatching { JSONObject(responseBody) }.getOrDefault(JSONObject()))
        } finally {
            connection.disconnect()
        }
    }
}

class NotionApiClient(private val transport: NotionHttpTransport = UrlConnectionNotionTransport()) {
    suspend fun queryDatabasePages(
        token: String,
        databaseId: String,
        titlePropertyName: String = NotionPayloadMapper.TASK_PROPERTY
    ): Result<List<NotionPayloadMapper.RemoteNotionTask>> = withContext(Dispatchers.IO) {
        runCatching {
            require(token.isNotBlank()) { "Notion token is missing." }
            require(databaseId.isNotBlank()) { "Notion database is missing." }
            val pages = mutableListOf<NotionPayloadMapper.RemoteNotionTask>()
            var cursor: String? = null
            var pageCount = 0
            do {
                check(pageCount++ < 100) { "Notion returned too many pages to sync safely." }
                val body = JSONObject().put("page_size", 100).also { if (cursor != null) it.put("start_cursor", cursor) }
                val response = request("POST", "/databases/$databaseId/query", token, body)
                if (response.status !in 200..299) error(apiError(response.json, response.status))
                val results = response.json.optJSONArray("results") ?: JSONArray()
                for (index in 0 until results.length()) {
                    val page = results.optJSONObject(index) ?: continue
                    NotionPayloadMapper.parseRemotePage(page, titlePropertyName)?.let(pages::add)
                }
                cursor = if (response.json.optBoolean("has_more")) {
                    response.json.optString("next_cursor").takeIf(String::isNotBlank)
                        ?: error("Notion pagination response was missing its cursor.")
                } else null
            } while (cursor != null)
            pages
        }
    }

    suspend fun connectAndEnsureDatabase(token: String, rawPageOrDbInput: String = ""): Result<NotionDatabaseInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(token.isNotBlank()) { "Enter a Notion integration token." }
                val uuid = NotionPreferences.extractNotionUuid(rawPageOrDbInput)
                if (uuid == null && rawPageOrDbInput.isNotBlank()) {
                    error("Enter a valid Notion page or database link, or leave it blank to find an accessible page automatically.")
                }
                if (uuid == null) discoverDatabaseOrCreate(token) else connectToTarget(token, uuid)
            }
        }

    suspend fun upsertTaskPage(token: String, databaseId: String, titlePropName: String, task: Task): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val properties = NotionPayloadMapper.buildTaskPropertiesJson(titlePropName, task)
                val pageId = task.notionPageId
                if (pageId == null) createTaskPage(token, databaseId, properties) else {
                    val updated = request("PATCH", "/pages/$pageId", token,
                        JSONObject().put("properties", properties).put("archived", false))
                    if (updated.status == 404) createTaskPage(token, databaseId, properties)
                    else if (updated.status in 200..299) updated.json.getString("id")
                    else error(apiError(updated.json, updated.status))
                }
            }
        }

    suspend fun archiveTaskPage(token: String, notionPageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = request("PATCH", "/pages/$notionPageId", token, JSONObject().put("archived", true))
            if (response.status !in 200..299 && response.status != 404) error(apiError(response.json, response.status))
        }
    }

    private fun discoverDatabaseOrCreate(token: String): NotionDatabaseInfo {
        var cursor: String? = null
        var firstPageId: String? = null
        var pagesRead = 0
        while (pagesRead++ < 5) {
            val body = JSONObject().put("page_size", 100).also { if (cursor != null) it.put("start_cursor", cursor) }
            val response = request("POST", "/search", token, body)
            if (response.status !in 200..299) error(apiError(response.json, response.status))
            val results = response.json.optJSONArray("results") ?: JSONArray()
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                if (item.optBoolean("archived") || item.optBoolean("in_trash")) continue
                when (item.optString("object")) {
                    "database" -> if (databaseTitle(item) in setOf(NotionPayloadMapper.DATABASE_TITLE, LEGACY_DATABASE_TITLE)) {
                        val databaseId = item.optString("id")
                        ensureSchema(token, databaseId, item.optJSONObject("properties") ?: JSONObject())
                        renameDatabaseIfNeeded(token, databaseId, item)
                        return NotionDatabaseInfo(databaseId, NotionPayloadMapper.TASK_PROPERTY, NotionPayloadMapper.DATABASE_TITLE)
                    }
                    "page" -> if (firstPageId == null) firstPageId = item.optString("id").takeIf(String::isNotBlank)
                }
            }
            if (!response.json.optBoolean("has_more")) break
            cursor = response.json.optString("next_cursor").takeIf(String::isNotBlank) ?: break
        }
        val pageId = firstPageId ?: error("No accessible Notion page was found. Share a page with your integration, or paste its link.")
        ensureVisualGuide(token, pageId)
        return createDatabase(token, pageId)
    }

    private fun connectToTarget(token: String, uuid: String): NotionDatabaseInfo {
        val databaseResponse = request("GET", "/databases/$uuid", token)
        if (databaseResponse.status == 200) {
            val root = databaseResponse.json
            ensureSchema(token, uuid, root.optJSONObject("properties") ?: JSONObject())
            renameDatabaseIfNeeded(token, uuid, root)
            return NotionDatabaseInfo(uuid, NotionPayloadMapper.TASK_PROPERTY, NotionPayloadMapper.DATABASE_TITLE)
        }
        val pageResponse = request("GET", "/pages/$uuid", token)
        if (pageResponse.status != 200) {
            when (pageResponse.status) {
                401 -> error("That integration token is invalid. Check it in Notion and try again.")
                403, 404 -> error("Notion could not access this page. Share it with your integration using Options (...) -> Connections, then try again.")
                else -> error(apiError(pageResponse.json, pageResponse.status))
            }
        }
        val existing = findChildDatabase(token, uuid)
        if (existing != null) {
            val id = existing.optString("id")
            ensureSchema(token, id, existing.optJSONObject("properties") ?: JSONObject())
            renameDatabaseIfNeeded(token, id, existing)
            return NotionDatabaseInfo(id, NotionPayloadMapper.TASK_PROPERTY, NotionPayloadMapper.DATABASE_TITLE)
        }
        ensureVisualGuide(token, uuid)
        return createDatabase(token, uuid)
    }

    private fun createDatabase(token: String, parentPageId: String): NotionDatabaseInfo {
        val created = request("POST", "/databases", token,
            JSONObject()
                .put("parent", JSONObject().put("type", "page_id").put("page_id", parentPageId))
                .put("title", richText(NotionPayloadMapper.DATABASE_TITLE))
                .put("properties", databaseProperties()))
        if (created.status !in 200..299) error(apiError(created.json, created.status))
        return NotionDatabaseInfo(created.json.getString("id"), NotionPayloadMapper.TASK_PROPERTY, NotionPayloadMapper.DATABASE_TITLE)
    }

    private fun ensureSchema(token: String, databaseId: String, current: JSONObject) {
        val changes = JSONObject()
        val titleName = findTitleProperty(current)
            ?: error("This Notion database has no title property. Keep its title column and reconnect.")
        if (titleName != NotionPayloadMapper.TASK_PROPERTY) {
            changes.put(titleName, JSONObject().put("name", NotionPayloadMapper.TASK_PROPERTY))
        }

        val expected = databaseProperties()
        for (name in listOf(NotionPayloadMapper.PRIORITY_PROPERTY, NotionPayloadMapper.PLAN_PROPERTY, NotionPayloadMapper.WHEN_PROPERTY, NotionPayloadMapper.NOTES_PROPERTY)) {
            if (!current.has(name)) {
                val canRenameLegacyDate = name == NotionPayloadMapper.WHEN_PROPERTY &&
                    current.optJSONObject("Due Date")?.optString("type") == "date"
                if (!canRenameLegacyDate) changes.put(name, expected.getJSONObject(name))
            }
            else {
                val type = expected.getJSONObject(name).keys().next()
                if (current.optJSONObject(name)?.optString("type") != type) {
                    error("The Notion property ‘$name’ must be a $type property. Change that column type, then reconnect.")
                }
            }
        }

        // Migrate the previous Mora/DueOrDie schema. Only columns owned by the old app are removed.
        val legacyNames = listOf("Status", "Due Date", "Completed At", "Outcome", "Focus Lock", "App Task ID")
        legacyNames.forEach { name ->
            if (current.has(name)) {
                if (name == "Due Date" && !current.has(NotionPayloadMapper.WHEN_PROPERTY) && current.optJSONObject(name)?.optString("type") == "date") {
                    changes.put(name, JSONObject().put("name", NotionPayloadMapper.WHEN_PROPERTY))
                } else changes.put(name, JSONObject.NULL)
            }
        }
        if (changes.length() == 0) return
        val patched = request("PATCH", "/databases/$databaseId", token, JSONObject().put("properties", changes))
        if (patched.status !in 200..299) error(apiError(patched.json, patched.status))
    }

    private fun renameDatabaseIfNeeded(token: String, databaseId: String, database: JSONObject) {
        if (databaseTitle(database) == NotionPayloadMapper.DATABASE_TITLE) return
        val response = request("PATCH", "/databases/$databaseId", token,
            JSONObject().put("title", richText(NotionPayloadMapper.DATABASE_TITLE)))
        if (response.status !in 200..299) error(apiError(response.json, response.status))
    }

    private fun ensureVisualGuide(token: String, pageId: String) {
        val response = request("GET", "/blocks/$pageId/children?page_size=100", token)
        if (response.status !in 200..299) error(apiError(response.json, response.status))
        val results = response.json.optJSONArray("results") ?: JSONArray()
        val guidePrefix = "🎯 Do Today:"
        for (i in 0 until results.length()) {
            val block = results.optJSONObject(i) ?: continue
            val type = block.optString("type")
            val text = block.optJSONObject(type)?.optJSONArray("rich_text")?.let(::plainText).orEmpty()
            if (text.startsWith(guidePrefix)) return
        }
        val guide = "🎯 Do Today: Focus on tasks marked ‘🔥 Do Today’ or ‘⚠️ Overdue - Do First’. Tasks marked ‘⏳ Not Today (Later)’ can wait."
        val appended = request("PATCH", "/blocks/$pageId/children", token,
            JSONObject().put("children", JSONArray().put(
                JSONObject().put("object", "block").put("type", "callout")
                    .put("callout", JSONObject().put("rich_text", richText(guide)))
            )))
        if (appended.status !in 200..299) error(apiError(appended.json, appended.status))
    }

    private fun findChildDatabase(token: String, pageId: String): JSONObject? {
        var cursor: String? = null
        repeat(5) {
            val path = "/blocks/$pageId/children?page_size=100" + (cursor?.let { "&start_cursor=$it" } ?: "")
            val response = request("GET", path, token)
            if (response.status !in 200..299) return null
            val results = response.json.optJSONArray("results") ?: JSONArray()
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val title = item.optJSONObject("child_database")?.optString("title").orEmpty()
                if (item.optString("type") == "child_database" && title in setOf(NotionPayloadMapper.DATABASE_TITLE, LEGACY_DATABASE_TITLE)) {
                    val fetched = request("GET", "/databases/${item.optString("id")}", token)
                    if (fetched.status in 200..299) return fetched.json
                }
            }
            if (!response.json.optBoolean("has_more")) return null
            cursor = response.json.optString("next_cursor").takeIf(String::isNotBlank) ?: return null
        }
        return null
    }

    private fun databaseProperties(): JSONObject = JSONObject()
        .put(NotionPayloadMapper.TASK_PROPERTY, JSONObject().put("title", JSONObject()))
        .put(NotionPayloadMapper.PRIORITY_PROPERTY, JSONObject().put("select", JSONObject().put("options", JSONArray().apply {
            put(option(NotionPayloadMapper.PRIORITY_HIGH, "red"))
            put(option(NotionPayloadMapper.PRIORITY_MEDIUM, "yellow"))
            put(option(NotionPayloadMapper.PRIORITY_LOW, "blue"))
        })))
        .put(NotionPayloadMapper.PLAN_PROPERTY, JSONObject().put("select", JSONObject().put("options", JSONArray().apply {
            put(option(NotionPayloadMapper.PLAN_OVERDUE, "red"))
            put(option(NotionPayloadMapper.PLAN_TODAY, "orange"))
            put(option(NotionPayloadMapper.PLAN_LATER, "gray"))
            put(option(NotionPayloadMapper.PLAN_DONE, "green"))
        })))
        .put(NotionPayloadMapper.WHEN_PROPERTY, JSONObject().put("date", JSONObject()))
        .put(NotionPayloadMapper.NOTES_PROPERTY, JSONObject().put("rich_text", JSONObject()))

    private fun option(name: String, color: String) = JSONObject().put("name", name).put("color", color)

    private fun createTaskPage(token: String, databaseId: String, properties: JSONObject): String {
        val response = request("POST", "/pages", token,
            JSONObject().put("parent", JSONObject().put("database_id", databaseId)).put("properties", properties))
        if (response.status !in 200..299) error(apiError(response.json, response.status))
        return response.json.getString("id")
    }

    private fun findTitleProperty(properties: JSONObject): String? {
        val keys = properties.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (properties.optJSONObject(key)?.optString("type") == "title") return key
        }
        return null
    }

    private fun databaseTitle(root: JSONObject): String {
        val title = root.optJSONArray("title") ?: root.optJSONObject("title")?.optJSONArray("title") ?: JSONArray()
        return plainText(title)
    }

    private fun plainText(array: JSONArray): String = (0 until array.length()).mapNotNull { index ->
        val part = array.optJSONObject(index) ?: return@mapNotNull null
        part.optJSONObject("text")?.optString("content") ?: part.optString("plain_text").takeIf(String::isNotBlank)
    }.joinToString("")

    private fun richText(value: String) = JSONArray().put(
        JSONObject().put("type", "text").put("text", JSONObject().put("content", value))
    )

    private fun apiError(json: JSONObject, status: Int): String = json.optString("message")
        .ifBlank { "Notion request failed (HTTP $status). Check the token, sharing access, and database permissions." }

    private fun request(method: String, path: String, token: String, body: JSONObject? = null): NotionHttpResponse =
        transport.execute(method, path, token, body)

    companion object {
        const val LEGACY_DATABASE_TITLE = "DueOrDie Tasks"
    }
}
