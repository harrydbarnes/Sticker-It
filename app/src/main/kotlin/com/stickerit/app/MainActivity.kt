package com.stickerit.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.stickerit.app.ui.StickerItNavHost
import com.stickerit.app.ui.theme.StickerItTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Holds URI(s) from a share/view intent so the nav host can consume it
    var sharedImageUris by mutableStateOf<List<Uri>>(emptyList())
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            StickerItTheme {
                StickerItNavHost(
                    sharedImageUris = sharedImageUris,
                    onSharedUrisConsumed = { sharedImageUris = emptyList() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) sharedImageUris = listOf(uri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (!uris.isNullOrEmpty()) sharedImageUris = uris
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                if (uri != null) sharedImageUris = listOf(uri)
            }
        }
    }
}
