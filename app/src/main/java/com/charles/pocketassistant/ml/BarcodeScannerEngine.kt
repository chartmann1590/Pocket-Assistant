package com.charles.pocketassistant.ml

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * On-device barcode and QR code scanning using ML Kit.
 * Scans all standard formats: QR, EAN, UPC, Code 128, PDF417, etc.
 */
@Singleton
class BarcodeScannerEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val TAG = "BarcodeScanner"
    }

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_PDF417,
                Barcode.FORMAT_DATA_MATRIX,
                Barcode.FORMAT_AZTEC
            )
            .build()
    )

    /**
     * Scan barcodes from an image URI.
     */
    suspend fun scanFromUri(uri: Uri): List<ScannedBarcode> = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromFilePath(context, uri)
            processImage(image)
        } catch (e: Exception) {
            Log.e(TAG, "Barcode scan from URI failed", e)
            emptyList()
        }
    }

    /**
     * Scan barcodes from a bitmap.
     */
    suspend fun scanFromBitmap(bitmap: Bitmap): List<ScannedBarcode> = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            processImage(image)
        } catch (e: Exception) {
            Log.e(TAG, "Barcode scan from bitmap failed", e)
            emptyList()
        }
    }

    private suspend fun processImage(image: InputImage): List<ScannedBarcode> {
        val barcodes = scanner.process(image).await()
        return barcodes.mapNotNull { barcode ->
            val rawValue = barcode.rawValue ?: return@mapNotNull null
            val type = when (barcode.valueType) {
                Barcode.TYPE_URL -> BarcodeType.URL
                Barcode.TYPE_EMAIL -> BarcodeType.EMAIL
                Barcode.TYPE_PHONE -> BarcodeType.PHONE
                Barcode.TYPE_WIFI -> BarcodeType.WIFI
                Barcode.TYPE_CONTACT_INFO -> BarcodeType.CONTACT
                Barcode.TYPE_CALENDAR_EVENT -> BarcodeType.CALENDAR_EVENT
                Barcode.TYPE_TEXT -> BarcodeType.TEXT
                Barcode.TYPE_PRODUCT -> BarcodeType.PRODUCT
                else -> BarcodeType.OTHER
            }
            ScannedBarcode(
                rawValue = rawValue,
                displayValue = barcode.displayValue ?: rawValue,
                format = barcode.format,
                type = type,
                url = barcode.url?.url,
                email = barcode.email?.address,
                phone = barcode.phone?.number
            ).also { Log.d(TAG, "Scanned: $type = $rawValue") }
        }
    }
}

data class ScannedBarcode(
    val rawValue: String,
    val displayValue: String,
    val format: Int,
    val type: BarcodeType,
    val url: String? = null,
    val email: String? = null,
    val phone: String? = null
)

enum class BarcodeType {
    URL, EMAIL, PHONE, WIFI, CONTACT, CALENDAR_EVENT, TEXT, PRODUCT, OTHER
}
