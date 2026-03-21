package com.charles.pocketassistant.util

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantDateTimeParserTest {

    @Test
    fun parsesIsoDate() {
        assertNotNull(AssistantDateTimeParser.parseToEpochMillis("2026-03-28"))
    }

    @Test
    fun parsesIsoDateTime() {
        assertNotNull(AssistantDateTimeParser.parseToEpochMillis("2026-03-28T14:30:00"))
    }

    @Test
    fun parsesHumanDateMonthDayYear() {
        assertNotNull(AssistantDateTimeParser.parseToEpochMillis("March 28, 2026"))
    }

    @Test
    fun parsesHumanDateAbbreviatedMonth() {
        assertNotNull(AssistantDateTimeParser.parseToEpochMillis("Mar 28, 2026"))
    }

    @Test
    fun parsesSlashFormat() {
        assertNotNull(AssistantDateTimeParser.parseToEpochMillis("03/28/2026"))
    }

    @Test
    fun parsesSlashFormatNoLeadingZero() {
        assertNotNull(AssistantDateTimeParser.parseToEpochMillis("3/28/2026"))
    }

    @Test
    fun returnsNullForBlank() {
        assertNull(AssistantDateTimeParser.parseToEpochMillis(""))
        assertNull(AssistantDateTimeParser.parseToEpochMillis("   "))
    }

    @Test
    fun returnsNullForGarbage() {
        assertNull(AssistantDateTimeParser.parseToEpochMillis("not a date"))
    }

    @Test
    fun formatForDisplayReturnsEmptyForNull() {
        assert(AssistantDateTimeParser.formatForDisplay(null).isEmpty())
    }

    @Test
    fun formatForDisplayReturnsNonEmptyForValidMillis() {
        val millis = AssistantDateTimeParser.parseToEpochMillis("2026-03-28")!!
        assert(AssistantDateTimeParser.formatForDisplay(millis).isNotEmpty())
    }
}
