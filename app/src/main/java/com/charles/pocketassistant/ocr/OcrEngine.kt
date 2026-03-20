package com.charles.pocketassistant.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Singleton
class OcrEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val MAX_PDF_PAGES_MVP = 5
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun fromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri).orEmpty()
        return@withContext if (mime == "application/pdf") {
            extractPdfText(uri)
        } else {
            val image = InputImage.fromFilePath(context, uri)
            TextCleanupUtil.normalize(recognizer.process(image).await().text)
        }
    }

    suspend fun fromBitmap(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)
        TextCleanupUtil.normalize(recognizer.process(image).await().text)
    }

    private suspend fun extractPdfText(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val pagesToRead = minOf(renderer.pageCount, MAX_PDF_PAGES_MVP)
                val all = StringBuilder()
                repeat(pagesToRead) { index ->
                    renderer.openPage(index).use { page ->
                        val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val image = InputImage.fromBitmap(bmp, 0)
                        val text = TextCleanupUtil.normalize(recognizer.process(image).await().text)
                        if (text.isNotBlank()) {
                            all.append(text).append("\n")
                        }
                    }
                }
                return@withContext TextCleanupUtil.normalize(all.toString())
            }
        }
        ""
    }
}
