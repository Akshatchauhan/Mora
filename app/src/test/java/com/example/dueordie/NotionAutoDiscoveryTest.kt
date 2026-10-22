package com.example.dueordie

import com.example.dueordie.data.notion.NotionApiClient
import com.example.dueordie.data.notion.NotionHttpResponse
import com.example.dueordie.data.notion.NotionHttpTransport
import com.example.dueordie.data.notion.NotionPayloadMapper
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotionAutoDiscoveryTest {
    @Test fun blankTargetSearchesWorkspaceAndCreatesFourColumnPlanInFirstSharedPage() = runBlocking {
        val transport = RecordingTransport()
        val result = NotionApiClient(transport).connectAndEnsureDatabase("test-token", "")

        assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
        assertEquals("database-id", result.getOrThrow().databaseId)
        assertEquals("Mora - Today's Plan", result.getOrThrow().databaseTitle)
        assertTrue(transport.calls.any { it.method == "POST" && it.path == "/search" })

        val guide = transport.calls.first { it.method == "PATCH" && it.path == "/blocks/page-id/children" }.body!!
        assertEquals("callout", guide.getJSONArray("children").getJSONObject(0).getString("type"))
        assertTrue(guide.toString().contains("🎯 Do Today"))

        val databaseBody = transport.calls.first { it.method == "POST" && it.path == "/databases" }.body!!
        val properties = databaseBody.getJSONObject("properties")
        assertEquals(setOf("Task", "Priority", "Today's Plan", "When", "Notes"), properties.keys().asSequence().toSet())
        val options = properties.getJSONObject("Today's Plan").getJSONObject("select").getJSONArray("options")
        assertEquals(4, options.length())
        assertEquals(NotionPayloadMapper.PLAN_OVERDUE, options.getJSONObject(0).getString("name"))
    }

    private class RecordingTransport : NotionHttpTransport {
        data class Call(val method: String, val path: String, val body: JSONObject?)
        val calls = mutableListOf<Call>()

        override fun execute(method: String, path: String, token: String, body: JSONObject?): NotionHttpResponse {
            calls += Call(method, path, body)
            return when {
                method == "POST" && path == "/search" -> NotionHttpResponse(200,
                    JSONObject().put("results", JSONArray().put(JSONObject()
                        .put("object", "page").put("id", "page-id").put("archived", false)))
                        .put("has_more", false))
                method == "GET" && path == "/blocks/page-id/children?page_size=100" -> NotionHttpResponse(200,
                    JSONObject().put("results", JSONArray()).put("has_more", false))
                method == "PATCH" && path == "/blocks/page-id/children" -> NotionHttpResponse(200, JSONObject())
                method == "POST" && path == "/databases" -> NotionHttpResponse(200,
                    JSONObject().put("id", "database-id").put("title", JSONArray().put(JSONObject()
                        .put("type", "text").put("text", JSONObject().put("content", "Mora - Today's Plan")))))
                else -> error("Unexpected request: $method $path")
            }
        }
    }
}
