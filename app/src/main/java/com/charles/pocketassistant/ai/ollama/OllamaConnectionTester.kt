package com.charles.pocketassistant.ai.ollama

import com.charles.pocketassistant.data.repository.OllamaRepositoryImpl
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OllamaConnectionTester @Inject constructor(
    private val ollamaRepository: OllamaRepositoryImpl
) {
    suspend fun test(): Result<List<String>> = ollamaRepository.testConnection()
}
