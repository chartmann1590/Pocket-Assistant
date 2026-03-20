package com.charles.pocketassistant.ocr

object TextCleanupUtil {
    fun normalize(input: String): String {
        return input
            .replace("\r", "\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .replace(Regex("[ \\t]{2,}"), " ")
    }
}
