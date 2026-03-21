package com.charles.pocketassistant.data.repository

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Retrofit requires an absolute URL with a scheme. Users often enter `192.168.1.5:11434`
 * without `http://`, which breaks requests even though "Test connection" may have been
 * tried with a corrected URL in some flows.
 *
 * If someone pastes a full API path (e.g. `http://host:11434/api/tags`), Retrofit would
 * resolve `api/tags` under that path and hit `/api/api/tags`. Strip to server root when
 * the path is Ollama's `/api/...` tree.
 */
object OllamaUrlNormalizer {
    fun normalize(raw: String): String {
        var u = raw.trim()
        if (u.isEmpty()) return u
        if (!u.startsWith("http://", ignoreCase = true) && !u.startsWith("https://", ignoreCase = true)) {
            u = "http://$u"
        }
        val parsed = u.toHttpUrlOrNull()
        if (parsed != null) {
            val path = parsed.encodedPath
            if (path == "/api" || path.startsWith("/api/")) {
                u = parsed.newBuilder()
                    .encodedPath("/")
                    .query(null)
                    .fragment(null)
                    .build()
                    .toString()
            }
        }
        return if (u.endsWith("/")) u else "$u/"
    }
}
