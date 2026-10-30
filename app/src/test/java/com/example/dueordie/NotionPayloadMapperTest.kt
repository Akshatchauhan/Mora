package com.example.dueordie

import com.example.dueordie.data.model.Task
import com.example.dueordie.data.model.TaskStatus
import com.example.dueordie.data.notion.NotionPayloadMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

class NotionPayloadMapperTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2024, 5, 15).atStartOfDay(zone).toInstant().toEpochMilli()
    private val now = today + 12 * 60 * 60 * 1000

    @Test fun serializesOnlyTheFourHumanFriendlyProperties() {
        val task = Task(id = 7, title = "Write spec", description = "Draft", deadlineTimestamp = today + 18 * 60 * 60 * 1000)
        val json = NotionPayloadMapper.buildTaskPropertiesJson(NotionPayloadMapper.TASK_PROPERTY, task, zone, now)

        assertEquals(setOf("Task", "Priority", "Today's Plan", "When", "Notes"), json.keys().asSequence().toSet())
        assertEquals("Write spec", json.getJSONObject("Task").getJSONArray("title").getJSONObject(0).getJSONObject("text").getString("content"))
        assertEquals("Medium", json.getJSONObject("Priority").getJSONObject("select").getString("name"))
        assertEquals(NotionPayloadMapper.PLAN_TODAY, json.getJSONObject("Today's Plan").getJSONObject("select").getString("name"))
        assertEquals("2024-05-15T18:00:00Z", json.getJSONObject("When").getJSONObject("date").getString("start"))
        assertEquals("Draft", json.getJSONObject("Notes").getJSONArray("rich_text").getJSONObject(0).getJSONObject("text").getString("content"))
    }

    @Test fun todayTomorrowOverdueAndCompletedHaveExpectedPlanLabels() {
        val dueToday = Task(title = "Today", deadlineTimestamp = today + 20 * 60 * 60 * 1000)
        val dueTomorrow = Task(title = "Tomorrow", deadlineTimestamp = today + 36 * 60 * 60 * 1000)
        val overdue = Task(title = "Overdue", deadlineTimestamp = now - 1, status = TaskStatus.PENDING)
        val completed = Task(title = "Done", deadlineTimestamp = now - 1, status = TaskStatus.COMPLETED)

        assertEquals(NotionPayloadMapper.PLAN_TODAY, NotionPayloadMapper.planFor(dueToday, zone, now))
        assertEquals(NotionPayloadMapper.PLAN_LATER, NotionPayloadMapper.planFor(dueTomorrow, zone, now))
        assertEquals(NotionPayloadMapper.PLAN_OVERDUE, NotionPayloadMapper.planFor(overdue, zone, now))
        assertEquals(NotionPayloadMapper.PLAN_DONE, NotionPayloadMapper.planFor(completed, zone, now))
        assertFalse(NotionPayloadMapper.planFor(completed, zone, now) == NotionPayloadMapper.PLAN_OVERDUE)
    }

    @Test fun explicitlyOverdueStatusMapsToDoFirst() {
        val task = Task(title = "Pay bill", deadlineTimestamp = today + 20 * 60 * 60 * 1000, status = TaskStatus.OVERDUE)
        assertEquals(NotionPayloadMapper.PLAN_OVERDUE, NotionPayloadMapper.planFor(task, zone, now))
        assertTrue(NotionPayloadMapper.buildTaskPropertiesJson("Task", task, zone).has("Today's Plan"))
    }

    @Test fun blankDescriptionProducesAnEmptyNotesValueInsteadOfInvalidEmptyText() {
        val task = Task(title = "No notes", deadlineTimestamp = today + 20 * 60 * 60 * 1000)
        val notes = NotionPayloadMapper.buildTaskPropertiesJson("Task", task, zone, now)
            .getJSONObject("Notes").getJSONArray("rich_text")
        assertEquals(0, notes.length())
    }

    @Test fun remoteDoneRowReconcilesToCompleted() {
        val remote = parseRemote("✅ Done", "2024-05-15T18:00:00Z")
        val merged = NotionPayloadMapper.reconcileRemoteTaskWithLocal(remote, null, now, zone)
        assertEquals(TaskStatus.COMPLETED, merged.status)
        assertEquals(now, merged.completedAtTimestamp)
    }

    @Test fun remoteLaterMovesTodayDeadlineToTomorrowKeepingTime() {
        val local = Task(id = 4, title = "Existing", deadlineTimestamp = today + 14 * 60 * 60 * 1000L + 30 * 60_000L)
        val remote = parseRemote("⏳ Not Today (Later)", "2024-05-15T14:30:00Z")
        val merged = NotionPayloadMapper.reconcileRemoteTaskWithLocal(remote, local, now, zone)
        assertEquals(today + 24 * 60 * 60 * 1000L + 14 * 60 * 60 * 1000L + 30 * 60_000L, merged.deadlineTimestamp)
    }

    @Test fun dateOnlyEditPreservesLocalTimeAndRemoteTaskDoesNotEnableFocusLockByDefault() {
        val local = Task(id = 8, title = "Existing", deadlineTimestamp = today + 14 * 60 * 60 * 1000L + 30 * 60_000L)
        val dateOnly = parseRemote("⏳ Not Today (Later)", "2024-05-16")
        val merged = NotionPayloadMapper.reconcileRemoteTaskWithLocal(dateOnly, local, now, zone)
        assertEquals(today + 24 * 60 * 60 * 1000L + 14 * 60 * 60 * 1000L + 30 * 60_000L, merged.deadlineTimestamp)

        val newTask = NotionPayloadMapper.reconcileRemoteTaskWithLocal(dateOnly, null, now, zone)
        assertFalse(newTask.isHostageModeEnabled)
        assertFalse(newTask.notionSyncDirty)
    }

    @Test fun malformedRemotePageIsIgnoredAndInvalidDateDoesNotCrash() {
        assertEquals(null, NotionPayloadMapper.parseRemotePage(JSONObject().put("properties", JSONObject()), zoneId = zone))
        val malformed = JSONObject().put("id", "broken").put("properties", JSONObject()
            .put("Task", JSONObject().put("title", rich("Broken date")))
            .put("When", JSONObject().put("date", JSONObject().put("start", "not-a-date"))))
        val parsed = NotionPayloadMapper.parseRemotePage(malformed, zoneId = zone)
        assertEquals(null, parsed?.deadlineEpochMs)
        assertEquals("Broken date", parsed?.title)
    }

    @Test fun archivedBlankTitleStillParsesAsDeletionSignal() {
        val page = JSONObject().put("id", "archived-page").put("in_trash", true).put("properties", JSONObject())
        val parsed = NotionPayloadMapper.parseRemotePage(page, zoneId = zone)
        assertTrue(parsed?.isArchived == true)
        assertEquals("archived-page", parsed?.pageId)
    }

    private fun parseRemote(plan: String, date: String): NotionPayloadMapper.RemoteNotionTask {
        val properties = JSONObject()
            .put("Task", JSONObject().put("type", "title").put("title", rich("Read book")))
            .put("Today's Plan", JSONObject().put("select", JSONObject().put("name", plan)))
            .put("When", JSONObject().put("date", JSONObject().put("start", date)))
            .put("Notes", JSONObject().put("rich_text", rich("Notes")))
        return NotionPayloadMapper.parseRemotePage(
            JSONObject().put("id", "page-1").put("last_edited_time", "2024-05-15T12:00:00Z").put("properties", properties),
            zoneId = zone
        )!!
    }

    private fun rich(value: String) = JSONArray().put(JSONObject().put("plain_text", value))
}
