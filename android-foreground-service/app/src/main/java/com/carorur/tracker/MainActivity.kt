package com.carorur.tracker

import android.annotation.SuppressLint
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.JavascriptInterface
import android.webkit.WebResourceResponse
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader
    private var mediaPlayer: MediaPlayer? = null

    private val pickAudioLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) {
                dispatchSelectionError("No se selecciono ninguna cancion.")
                return@registerForActivityResult
            }
            handlePickedAudioUri(uri)
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)

        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.addJavascriptInterface(NativeMusicBridge(), "CarorurNativeMusic")
        webView.loadUrl("https://appassets.androidplatform.net/assets/web/index.html")
    }

    override fun onDestroy() {
        stopNativeAudio()
        webView.removeJavascriptInterface("CarorurNativeMusic")
        webView.destroy()
        super.onDestroy()
    }

    private fun handlePickedAudioUri(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }

        Thread {
            val meta = readAudioMetadata(uri)
            runOnUiThread {
                dispatchNativeAudioSelected(uri, meta.title, meta.artist)
            }
        }.start()
    }

    private fun readAudioMetadata(uri: Uri): AudioMeta {
        var displayName = "Cancion local"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    displayName = cursor.getString(idx).orEmpty().ifBlank { "Cancion local" }
                }
            }
        }

        var title = displayName.substringBeforeLast('.')
        var artist = ""

        var mmr: MediaMetadataRetriever? = null
        try {
            mmr = MediaMetadataRetriever()
            mmr.setDataSource(this, uri)
            title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: title
            artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() } ?: ""
        } catch (_: Exception) {
        } finally {
            try {
                mmr?.release()
            } catch (_: Exception) {
            }
        }

        return AudioMeta(title.ifBlank { "Cancion local" }, artist)
    }

    private fun dispatchNativeAudioSelected(uri: Uri, title: String, artist: String) {
        val payload = JSONObject()
            .put("uri", uri.toString())
            .put("title", title)
            .put("artist", artist)
            .toString()

        val js = """
            (function() {
              var payload = $payload;
              if (window.onNativeAudioSelected) {
                window.onNativeAudioSelected(payload);
                return;
              }
              var frames = document.getElementsByTagName('iframe');
              for (var i = 0; i < frames.length; i++) {
                try {
                  if (frames[i].contentWindow && frames[i].contentWindow.onNativeAudioSelected) {
                    frames[i].contentWindow.onNativeAudioSelected(payload);
                    return;
                  }
                } catch (e) {}
              }
            })();
        """.trimIndent()

        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }

    private fun dispatchSelectionError(message: String) {
        val safeMessage = JSONObject.quote(message)
        val js = """
            (function() {
              var msg = $safeMessage;
              if (window.onNativeAudioPickerError) {
                window.onNativeAudioPickerError(msg);
                return;
              }
              var frames = document.getElementsByTagName('iframe');
              for (var i = 0; i < frames.length; i++) {
                try {
                  if (frames[i].contentWindow && frames[i].contentWindow.onNativeAudioPickerError) {
                    frames[i].contentWindow.onNativeAudioPickerError(msg);
                    return;
                  }
                } catch (e) {}
              }
            })();
        """.trimIndent()

        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }

    private fun playNativeAudio(uriString: String) {
        if (uriString.isBlank()) return
        val uri = Uri.parse(uriString)

        stopNativeAudio()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@MainActivity, uri)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { stopNativeAudio() }
                prepareAsync()
            }
        } catch (_: Exception) {
            stopNativeAudio()
            dispatchSelectionError("No se ha podido reproducir esta cancion local.")
        }
    }

    private fun stopNativeAudio() {
        mediaPlayer?.run {
            try {
                if (isPlaying) stop()
            } catch (_: Exception) {
            }
            release()
        }
        mediaPlayer = null
    }

    inner class NativeMusicBridge {
        @JavascriptInterface
        fun pickAudioForPlaylist() {
            runOnUiThread {
                pickAudioLauncher.launch(arrayOf("audio/*"))
            }
        }

        @JavascriptInterface
        fun playNativeAudio(uri: String) {
            runOnUiThread {
                this@MainActivity.playNativeAudio(uri)
            }
        }

        @JavascriptInterface
        fun stopNativeAudio() {
            runOnUiThread {
                this@MainActivity.stopNativeAudio()
            }
        }
    }

    private data class AudioMeta(
        val title: String,
        val artist: String
    )
}
