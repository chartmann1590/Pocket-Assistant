package com.charles.pocketassistant.ml

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ML Kit Document Scanner integration.
 * Provides auto edge detection, perspective correction, and image enhancement.
 * Runs via Google Play Services (minimal APK impact).
 */
@Singleton
class DocumentScannerHelper @Inject constructor() {
    private companion object {
        const val TAG = "DocScanner"
    }

    private val scanner: GmsDocumentScanner = GmsDocumentScanning.getClient(
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(10)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    )

    /**
     * Get the scanner's IntentSender to launch the scanning activity.
     * Call this from your Activity/Fragment and launch it with an ActivityResultLauncher.
     */
    fun getStartIntent(activity: Activity, callback: (IntentSenderRequest) -> Unit) {
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                callback(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to start document scanner", e)
            }
    }

    /**
     * Parse the scanning result from the ActivityResult.
     * Returns scanned page URIs and optional PDF URI.
     */
    fun handleResult(result: ActivityResult): DocumentScanResult? {
        if (result.resultCode != Activity.RESULT_OK) return null
        val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        if (scanResult == null) {
            Log.w(TAG, "No scan result in activity result")
            return null
        }

        val pageUris = scanResult.pages?.map { it.imageUri } ?: emptyList()
        val pdfUri = scanResult.pdf?.uri

        Log.d(TAG, "Scanned ${pageUris.size} pages, pdf=${pdfUri != null}")
        return DocumentScanResult(
            pageImageUris = pageUris,
            pdfUri = pdfUri
        )
    }
}

data class DocumentScanResult(
    val pageImageUris: List<Uri>,
    val pdfUri: Uri?
)
