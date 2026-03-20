package com.charles.pocketassistant.data.remote.ollama

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

@Serializable
data class OllamaModelListResponse(
    @SerialName("models") val models: List<OllamaModel> = emptyList()
)

@Serializable
data class OllamaModel(
    val name: String
)

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
    val message: OllamaMessage? = null
)

interface OllamaApiService {
    @GET("api/tags")
    suspend fun listModels(): OllamaModelListResponse

    @POST("api/chat")
    suspend fun chat(@Body request: OllamaChatRequest): OllamaChatResponse
}
