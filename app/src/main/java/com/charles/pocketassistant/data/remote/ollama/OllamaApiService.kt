package com.charles.pocketassistant.data.remote.ollama

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Streaming

@Serializable
data class OllamaModelListResponse(
    @SerialName("models") val models: List<OllamaModel>? = null
)

@Serializable
data class OllamaModel(
    val name: String? = null,
    @SerialName("model") val model: String? = null
) {
    fun resolvedName(): String =
        name?.takeIf { it.isNotBlank() } ?: model?.takeIf { it.isNotBlank() }.orEmpty()
}


@Serializable
data class OllamaMessage(
    val role: String,
    val content: String
)

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = false
)

@Serializable
data class OllamaChatResponse(
    val message: OllamaMessage? = null,
    val done: Boolean = false
)

interface OllamaApiService {
    @GET("api/tags")
    suspend fun listModels(): OllamaModelListResponse

    @POST("api/chat")
    @Streaming
    suspend fun chat(@Body request: OllamaChatRequest): ResponseBody
}
