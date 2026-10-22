package com.example.dueordie

import com.example.dueordie.data.notion.NotionPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotionIdParserTest {
    private val expected = "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d"

    @Test fun fullPageUrlWithSlugAndQueryParses() {
        assertEquals(expected, NotionPreferences.extractNotionUuid("https://www.notion.so/My-Workspace/DueOrDie-Accountability-1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d?pvs=4"))
    }

    @Test fun databaseUrlWithViewQueryParses() {
        assertEquals(expected, NotionPreferences.extractNotionUuid("https://www.notion.so/1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d?v=abc"))
    }

    @Test fun rawAndHyphenatedIdsParseCanonically() {
        assertEquals(expected, NotionPreferences.extractNotionUuid("1A2B3C4D5E6F7A8B9C0D1E2F3A4B5C6D"))
        assertEquals(expected, NotionPreferences.extractNotionUuid(expected.uppercase()))
    }

    @Test fun invalidInputsReturnNull() {
        assertNull(NotionPreferences.extractNotionUuid("not a notion link"))
        assertNull(NotionPreferences.extractNotionUuid("https://www.notion.so/page-without-id"))
    }
}
