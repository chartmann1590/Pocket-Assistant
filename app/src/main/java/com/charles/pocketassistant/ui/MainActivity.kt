package com.charles.pocketassistant.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.charles.pocketassistant.ui.app.AppNav
import com.charles.pocketassistant.ui.theme.PocketAssistantTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val importViewModel: ImportViewModel by viewModels()
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermissionIfPossible(it)
            importViewModel.importUri(it, "image")
        }
    }
    private val pickPdf = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermissionIfPossible(it)
            importViewModel.importUri(it, "pdf")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShareIntent(intent)
        setContent {
            PocketAssistantTheme {
                AppNav(
                    pickImage = { pickImage.launch("image/*") },
                    pickPdf = { pickPdf.launch("application/pdf") },
                    importViewModel = importViewModel
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        runCatching {
            if (intent == null) return
            if (intent.action == Intent.ACTION_SEND) {
                when {
                    intent.type == "text/plain" -> {
                        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                        if (text.isNotBlank()) importViewModel.importText(text, sourceApp = callingPackage ?: intent.`package`)
                    }
                    intent.type?.startsWith("image/") == true || intent.type == "application/pdf" -> {
                        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                        if (uri != null) {
                            contentResolver.takePersistableUriPermissionIfPossible(uri)
                            importViewModel.importUri(
                                uri = uri,
                                type = if (intent.type == "application/pdf") "pdf" else "screenshot",
                                sourceApp = callingPackage ?: intent.`package`
                            )
                        }
                    }
                }
            } else if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
                uris.forEach {
                    contentResolver.takePersistableUriPermissionIfPossible(it)
                    importViewModel.importUri(it, "image", sourceApp = callingPackage ?: intent.`package`)
                }
            }
        }
    }
}

private fun android.content.ContentResolver.takePersistableUriPermissionIfPossible(uri: Uri) {
    runCatching {
        takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
}
