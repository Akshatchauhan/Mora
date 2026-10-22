package com.example.dueordie

import com.example.dueordie.data.notion.NotionApiClient
import com.example.dueordie.data.notion.NotionHttpResponse
import com.example.dueordie.data.notion.NotionHttpTransport
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotionApiPullTest {
    @Test fun queryPaginatesEveryPageAndKeepsRows() = runBlocking {
        var calls = 0
        val transport = NotionHttpTransport { method, path, _, body ->
            calls++
            assertEquals("POST", method)
            assertEquals("/databases/db-1/query", path)
            assertEquals(100, body?.optInt("page_size"))
            if (calls == 1) {
                NotionHttpResponse(200, JSONObject().put("results", JSONArray().put(row("one")))
                    .put("has_more", true).put("next_cursor", "next"))
            } else {
                assertEquals("next", body?.optString("start_cursor"))
                NotionHttpResponse(200, JSONObject().put("results", JSONArray().put(row("two")))
                    .put("has_more", false))
            }
        }
        val result = NotionApiClient(transport).queryDatabasePages("token", "db-1")
        assertTrue(result.isSuccess)
        assertEquals(listOf("one", "two"), result.getOrThrow().map { it.pageId })
        assertEquals(2, calls)
    }

    @Test fun paginationWithoutCursorFailsWithoutReturningPartialData() = runBlocking {
        val transport = NotionHttpTransport { _, _, _, _ ->
            NotionHttpResponse(200, JSONObject().put("results", JSONArray().put(row("one")))
                .put("has_more", true).put("next_cursor", ""))
        }
        val result = NotionApiClient(transport).queryDatabasePages("token", "db-1")
        assertTrue(result.isFailure)
    }

    private fun row(id: String): JSONObject {
        val rich = JSONArray().put(JSONObject().put("plain_text", "Task $id"))
        return JSONObject()
            .put("id", id)
            .put("last_edited_time", "2026-10-12T12:00:00Z")
            .put("properties", JSONObject()
                .put("Task", JSONObject().put("type", "title").put("title", rich))
                .put("Today's Plan", JSONObject().put("select", JSONObject().put("name", "🔥 Do Today")))
                .put("When", JSONObject().put("date", JSONObject().put("start", "2026-10-12T16:00:00Z")))
                .put("Notes", JSONObject().put("rich_text", JSONArray())))
    }
}
