package com.example.dueordie.data.notion

interface NotionSyncTrigger {
    fun triggerBackgroundSync()
    fun queueArchivePageId(pageId: String)
}

fun isNotionSyncFixture(title: String): Boolean = title.startsWith("[Example] ") || title == "Submit_quarterly_report"
