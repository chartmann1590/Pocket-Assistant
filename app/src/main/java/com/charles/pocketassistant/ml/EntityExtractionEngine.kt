package com.charles.pocketassistant.ml

import android.util.Log
import com.google.mlkit.nl.entityextraction.DateTimeEntity
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityAnnotation
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractor
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.google.mlkit.nl.entityextraction.MoneyEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * On-device ML entity extraction using ML Kit.
 * Extracts dates, amounts, addresses, phone numbers, emails, etc.
 * from raw text without needing an LLM.
 *
 * Runs instantly (~50ms) compared to LLM inference (~60s).
 */
@Singleton
class EntityExtractionEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val TAG = "EntityExtraction"
    }

    private val extractor: EntityExtractor = EntityExtraction.getClient(
        EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
    )

    private var modelReady = false

    /**
     * Ensure the model is downloaded. Call once at app startup or before first use.
     * The model is small (~2MB) and downloads automatically.
     */
    suspend fun ensureModelReady() {
        if (modelReady) return
        try {
            withContext(Dispatchers.IO) {
                extractor.downloadModelIfNeeded().await()
            }
            modelReady = true
            Log.d(TAG, "Entity extraction model ready")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download entity extraction model", e)
        }
    }

    /**
     * Extract structured entities from text.
     * Returns a [PreExtractedEntities] with dates, amounts, addresses, etc.
     */
    suspend fun extract(text: String): PreExtractedEntities {
        if (!modelReady) ensureModelReady()
        if (!modelReady) return PreExtractedEntities()

        return try {
            withContext(Dispatchers.IO) {
                val params = EntityExtractionParams.Builder(text).build()
                val annotations = extractor.annotate(params).await()
                parseAnnotations(text, annotations)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Entity extraction failed", e)
            PreExtractedEntities()
        }
    }

    private fun parseAnnotations(text: String, annotations: List<EntityAnnotation>): PreExtractedEntities {
        val dates = mutableListOf<ExtractedDate>()
        val amounts = mutableListOf<ExtractedAmount>()
        val addresses = mutableListOf<String>()
        val phoneNumbers = mutableListOf<String>()
        val emails = mutableListOf<String>()
        val urls = mutableListOf<String>()
        val trackingNumbers = mutableListOf<String>()
        val flightNumbers = mutableListOf<String>()

        for (annotation in annotations) {
            val annotatedText = text.substring(annotation.start, annotation.end)
            for (entity in annotation.entities) {
                when (entity.type) {
                    Entity.TYPE_DATE_TIME -> {
                        val dateTime = entity as? DateTimeEntity
                        dates.add(
                            ExtractedDate(
                                text = annotatedText,
                                timestampMillis = dateTime?.timestampMillis ?: 0L,
                                granularity = dateTime?.dateTimeGranularity ?: DateTimeEntity.GRANULARITY_DAY
                            )
                        )
                    }
                    Entity.TYPE_MONEY -> {
                        val money = entity as? MoneyEntity
                        amounts.add(
                            ExtractedAmount(
                                text = annotatedText,
                                amount = money?.unnormalizedCurrency?.toDoubleOrNull(),
                                currencyCode = money?.unnormalizedCurrency.orEmpty()
                            )
                        )
                    }
                    Entity.TYPE_ADDRESS -> addresses.add(annotatedText)
                    Entity.TYPE_PHONE -> phoneNumbers.add(annotatedText)
                    Entity.TYPE_EMAIL -> emails.add(annotatedText)
                    Entity.TYPE_URL -> urls.add(annotatedText)
                    Entity.TYPE_TRACKING_NUMBER -> trackingNumbers.add(annotatedText)
                    Entity.TYPE_FLIGHT_NUMBER -> flightNumbers.add(annotatedText)
                }
            }
        }

        val result = PreExtractedEntities(
            dates = dates.distinctBy { it.text },
            amounts = amounts.distinctBy { it.text },
            addresses = addresses.distinct(),
            phoneNumbers = phoneNumbers.distinct(),
            emails = emails.distinct(),
            urls = urls.distinct(),
            trackingNumbers = trackingNumbers.distinct(),
            flightNumbers = flightNumbers.distinct()
        )
        Log.d(TAG, "Extracted: ${dates.size} dates, ${amounts.size} amounts, " +
            "${addresses.size} addresses, ${phoneNumbers.size} phones, ${emails.size} emails")
        return result
    }

    /**
     * Format pre-extracted entities as a hint block for the LLM prompt.
     * This gives the LLM reliable entity data so it can focus on
     * classification and summarization instead of entity hunting.
     */
    fun formatAsPromptHint(entities: PreExtractedEntities): String {
        if (entities.isEmpty()) return ""
        return buildString {
            appendLine("Pre-extracted entities (verified by ML):")
            if (entities.dates.isNotEmpty()) {
                appendLine("  Dates: ${entities.dates.joinToString(", ") { it.text }}")
            }
            if (entities.amounts.isNotEmpty()) {
                appendLine("  Amounts: ${entities.amounts.joinToString(", ") { it.text }}")
            }
            if (entities.addresses.isNotEmpty()) {
                appendLine("  Addresses: ${entities.addresses.joinToString(", ")}")
            }
            if (entities.phoneNumbers.isNotEmpty()) {
                appendLine("  Phone numbers: ${entities.phoneNumbers.joinToString(", ")}")
            }
            if (entities.emails.isNotEmpty()) {
                appendLine("  Emails: ${entities.emails.joinToString(", ")}")
            }
        }.trimEnd()
    }

    /**
     * Merge ML-extracted entities into an LLM extraction result,
     * backfilling any entities the LLM missed.
     */
    fun backfillEntities(
        llmResult: com.charles.pocketassistant.domain.model.AiExtractionResult,
        mlEntities: PreExtractedEntities
    ): com.charles.pocketassistant.domain.model.AiExtractionResult {
        val entities = llmResult.entities
        val mergedDates = (entities.dates + mlEntities.dates.map { it.text }).distinct()
        val mergedAmounts = (entities.amounts + mlEntities.amounts.map { it.text }).distinct()
        val mergedLocations = (entities.locations + mlEntities.addresses).distinct()

        // Backfill billInfo if LLM missed it
        var billInfo = llmResult.billInfo
        if (billInfo.dueDate.isBlank() && mlEntities.dates.isNotEmpty()) {
            billInfo = billInfo.copy(dueDate = mlEntities.dates.first().text)
        }
        if (billInfo.amount.isBlank() && mlEntities.amounts.isNotEmpty()) {
            billInfo = billInfo.copy(amount = mlEntities.amounts.first().text)
        }

        // Backfill appointmentInfo
        var appointmentInfo = llmResult.appointmentInfo
        if (appointmentInfo.date.isBlank() && mlEntities.dates.isNotEmpty()) {
            appointmentInfo = appointmentInfo.copy(date = mlEntities.dates.first().text)
        }
        if (appointmentInfo.location.isBlank() && mlEntities.addresses.isNotEmpty()) {
            appointmentInfo = appointmentInfo.copy(location = mlEntities.addresses.first())
        }

        return llmResult.copy(
            entities = entities.copy(
                dates = mergedDates,
                amounts = mergedAmounts,
                locations = mergedLocations
            ),
            billInfo = billInfo,
            appointmentInfo = appointmentInfo
        )
    }
}

data class ExtractedDate(
    val text: String,
    val timestampMillis: Long,
    val granularity: Int
)

data class ExtractedAmount(
    val text: String,
    val amount: Double?,
    val currencyCode: String
)

data class PreExtractedEntities(
    val dates: List<ExtractedDate> = emptyList(),
    val amounts: List<ExtractedAmount> = emptyList(),
    val addresses: List<String> = emptyList(),
    val phoneNumbers: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val urls: List<String> = emptyList(),
    val trackingNumbers: List<String> = emptyList(),
    val flightNumbers: List<String> = emptyList()
) {
    fun isEmpty(): Boolean = dates.isEmpty() && amounts.isEmpty() && addresses.isEmpty()
        && phoneNumbers.isEmpty() && emails.isEmpty() && urls.isEmpty()
        && trackingNumbers.isEmpty() && flightNumbers.isEmpty()
}
