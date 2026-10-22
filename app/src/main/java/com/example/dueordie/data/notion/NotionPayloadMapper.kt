package com.example.dueordie.data.notion

import com.example.dueordie.data.model.Task
import com.example.dueordie.data.model.TaskPriority
import com.example.dueordie.data.model.TaskStatus
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object NotionPayloadMapper {
    const val DATABASE_TITLE = "Mora - Today's Plan"
    const val TASK_PROPERTY = "Task"
    const val PRIORITY_PROPERTY = "Priority"
    const val PLAN_PROPERTY = "Today's Plan"
    const val WHEN_PROPERTY = "When"
    const val NOTES_PROPERTY = "Notes"

    const val PLAN_OVERDUE = "⚠️ Overdue - Do First"
    const val PLAN_TODAY = "🔥 Do Today"
    const val PLAN_LATER = "⏳ Not Today (Later)"
    const val PLAN_DONE = "✅ Done"

    const val PRIORITY_HIGH = "High"
    const val PRIORITY_MEDIUM = "Medium"
    const val PRIORITY_LOW = "Low"

    private val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    data class RemoteNotionTask(
        val pageId: String,
        val title: String,
        val priority: TaskPriority = TaskPriority.MEDIUM,
        val planOption: String?,
        val deadlineEpochMs: Long?,
        val hasExplicitTime: Boolean,
        val notes: String,
        val isArchived: Boolean,
        val lastEditedEpochMs: Long
    )

    fun parseRemotePage(
        pageJson: JSONObject,
        titlePropertyName: String = TASK_PROPERTY,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): RemoteNotionTask? {
        val pageId = pageJson.optString("id").takeIf(String::isNotBlank) ?: return null
        val archived = pageJson.optBoolean("archived") || pageJson.optBoolean("in_trash") || pageJson.optBoolean("is_archived")
        val properties = pageJson.optJSONObject("properties") ?: JSONObject()
        var title = richTextValue(properties.optJSONObject(titlePropertyName)?.optJSONArray("title"))
        if (title.isBlank()) {
            val keys = properties.keys()
            while (keys.hasNext() && title.isBlank()) {
                val property = properties.optJSONObject(keys.next()) ?: continue
                if (property.optString("type") == "title") title = richTextValue(property.optJSONArray("title"))
            }
        }
        if (title.isBlank() && !archived) return null

        val priorityText = properties.optJSONObject(PRIORITY_PROPERTY)?.optJSONObject("select")?.optString("name")
        val priority = TaskPriority.fromDisplayName(priorityText)

        val plan = properties.optJSONObject(PLAN_PROPERTY)?.optJSONObject("select")?.optString("name")
            ?.takeIf(String::isNotBlank)
            ?: properties.optJSONObject("Status")?.optJSONObject("select")?.optString("name")?.takeIf(String::isNotBlank)
        val dateText = properties.optJSONObject(WHEN_PROPERTY)?.optJSONObject("date")?.optString("start")
            ?.takeIf(String::isNotBlank)
            ?: properties.optJSONObject("Due")?.optJSONObject("date")?.optString("start")?.takeIf(String::isNotBlank)
        val explicitTime = dateText?.contains('T') == true
        val deadline = dateText?.let { value -> runCatching {
            if (explicitTime) OffsetDateTime.parse(value).toInstant().toEpochMilli()
            else LocalDate.parse(value).atTime(18, 0).atZone(zoneId).toInstant().toEpochMilli()
        }.getOrNull() }
        val notes = richTextValue(properties.optJSONObject(NOTES_PROPERTY)?.optJSONArray("rich_text"))
        val edited = runCatching { Instant.parse(pageJson.optString("last_edited_time")).toEpochMilli() }.getOrDefault(0L)
        return RemoteNotionTask(pageId, title, priority, plan, deadline, explicitTime, notes, archived, edited)
    }

    fun reconcileRemoteTaskWithLocal(
        remote: RemoteNotionTask,
        existingLocal: Task?,
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Task {
        var deadline = remote.deadlineEpochMs ?: existingLocal?.deadlineTimestamp ?: nowMs + 2 * 60 * 60 * 1000L
        if (remote.deadlineEpochMs != null && !remote.hasExplicitTime && existingLocal != null) {
            val localTime = Instant.ofEpochMilli(existingLocal.deadlineTimestamp).atZone(zoneId).toLocalTime()
            deadline = Instant.ofEpochMilli(remote.deadlineEpochMs).atZone(zoneId).toLocalDate()
                .atTime(localTime).atZone(zoneId).toInstant().toEpochMilli()
        }
        val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        val endToday = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L
        if (remote.planOption == PLAN_LATER && deadline <= endToday) {
            val time = Instant.ofEpochMilli(deadline).atZone(zoneId).toLocalTime()
            deadline = today.plusDays(1).atTime(time).atZone(zoneId).toInstant().toEpochMilli()
        } else if (remote.planOption == PLAN_TODAY && deadline > endToday) {
            val time = Instant.ofEpochMilli(deadline).atZone(zoneId).toLocalTime()
            val sameDay = today.atTime(time).atZone(zoneId).toInstant().toEpochMilli()
            deadline = if (sameDay <= nowMs) nowMs + 2 * 60 * 60 * 1000L else sameDay
        }
        val done = remote.planOption == PLAN_DONE || remote.planOption == "Completed"
        return Task(
            id = existingLocal?.id ?: 0L,
            title = remote.title.ifBlank { existingLocal?.title.orEmpty() },
            description = remote.notes,
            deadlineTimestamp = deadline,
            priority = remote.priority,
            status = if (done) TaskStatus.COMPLETED else if (deadline <= nowMs) TaskStatus.OVERDUE else TaskStatus.PENDING,
            createdAtTimestamp = existingLocal?.createdAtTimestamp ?: nowMs,
            completedAtTimestamp = if (done) existingLocal?.completedAtTimestamp ?: nowMs else null,
            requirePhotoProof = existingLocal?.requirePhotoProof ?: false,
            photoPath = existingLocal?.photoPath,
            isHostageModeEnabled = existingLocal?.isHostageModeEnabled ?: true,
            notionPageId = remote.pageId,
            notionSyncDirty = false,
            lastSyncedAtTimestamp = nowMs
        )
    }

    private fun richTextValue(array: JSONArray?): String = array?.let { values ->
        (0 until values.length()).mapNotNull { index ->
            val part = values.optJSONObject(index) ?: return@mapNotNull null
            part.optString("plain_text").takeIf(String::isNotBlank)
                ?: part.optJSONObject("text")?.optString("content")?.takeIf(String::isNotBlank)
        }.joinToString("")
    }.orEmpty()

    fun buildTaskPropertiesJson(
        titlePropName: String,
        task: Task,
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowMs: Long = System.currentTimeMillis()
    ): JSONObject {
        val properties = JSONObject()
        properties.put(titlePropName, JSONObject().put("title", richText(task.title)))
        properties.put(PRIORITY_PROPERTY, JSONObject().put("select", JSONObject().put("name", task.priority.displayName)))
        properties.put(PLAN_PROPERTY, JSONObject().put("select", JSONObject().put("name", planFor(task, zoneId, nowMs))))
        properties.put(WHEN_PROPERTY, JSONObject().put("date", JSONObject().put("start", formatTimestamp(task.deadlineTimestamp, zoneId))))
        properties.put(NOTES_PROPERTY, JSONObject().put("rich_text", richText(task.description)))
        return properties
    }

    fun planFor(task: Task, zoneId: ZoneId = ZoneId.systemDefault(), nowMs: Long = System.currentTimeMillis()): String {
        if (task.status == TaskStatus.COMPLETED) return PLAN_DONE
        if (task.status == TaskStatus.OVERDUE || task.isOverdueAt(nowMs)) return PLAN_OVERDUE
        val dueDate = Instant.ofEpochMilli(task.deadlineTimestamp).atZone(zoneId).toLocalDate()
        val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        return if (dueDate == today) PLAN_TODAY else PLAN_LATER
    }

    private fun richText(value: String) = JSONArray().apply {
        if (value.isNotEmpty()) {
            put(JSONObject().put("type", "text").put("text", JSONObject().put("content", value)))
        }
    }

    private fun formatTimestamp(timestamp: Long, zoneId: ZoneId): String =
        formatter.format(Instant.ofEpochMilli(timestamp).atZone(zoneId))
}
