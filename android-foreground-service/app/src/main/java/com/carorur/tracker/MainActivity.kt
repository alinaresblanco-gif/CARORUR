package com.carorur.tracker

import android.annotation.SuppressLint
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceResponse
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var prefs: SharedPreferences
    private lateinit var assetLoader: WebViewAssetLoader
    private var mediaPlayer: MediaPlayer? = null
    private var isPickerOpen: Boolean = false
    private var pendingNativeSelectionPayload: String? = null
    private var dispatchRetryCount: Int = 0
    private var pendingLibraryRequest: Boolean = false

    private val pickAudioLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            isPickerOpen = false
            if (uri == null) {
                dispatchSelectionError("No se selecciono ninguna cancion.")
                return@registerForActivityResult
            }
            handlePickedAudioUri(uri)
        }

    private val requestAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!pendingLibraryRequest) return@registerForActivityResult
            pendingLibraryRequest = false
            if (granted) {
                loadNativeAudioLibrary()
            } else {
                dispatchNativeAudioLibraryError("Concede permiso de audio para ver tu musica del movil.")
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        webView = findViewById(R.id.webView)
        val btnSetTestUrl: Button = findViewById(R.id.btnSetTestUrl)
        val btnClearCacheReload: Button = findViewById(R.id.btnClearCacheReload)

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

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                tryDispatchPendingNativeSelection()
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
            cacheMode = WebSettings.LOAD_NO_CACHE
        }

        btnSetTestUrl.setOnClickListener { showTestUrlDialog() }
        btnClearCacheReload.setOnClickListener { clearCacheAndReload() }

        webView.addJavascriptInterface(NativeMusicBridge(), "CarorurNativeMusic")
        loadConfiguredUrl()
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
        } catch (_: Throwable) {
        }

        Thread {
            try {
                val meta = readAudioMetadata(uri)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    dispatchNativeAudioSelected(uri, meta.title, meta.artist)
                }
            } catch (_: Throwable) {
                runOnUiThread {
                    dispatchSelectionError("No se pudo procesar el audio seleccionado.")
                }
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

        val title = displayName.substringBeforeLast('.').ifBlank { "Cancion local" }
        val artist = ""

        return AudioMeta(title, artist)
    }

    private fun requiredAudioPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, requiredAudioPermission()) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureAudioPermissionAndLoadLibrary() {
        if (hasAudioPermission()) {
            loadNativeAudioLibrary()
            return
        }
        pendingLibraryRequest = true
        requestAudioPermissionLauncher.launch(requiredAudioPermission())
    }

    private fun loadNativeAudioLibrary() {
        Thread {
            try {
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.RELATIVE_PATH,
                    MediaStore.Audio.Media.IS_MUSIC
                )
                val selection = MediaStore.Audio.Media.IS_MUSIC + " != 0"
                val sortOrder = MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
                val tracks = JSONArray()
                var total = 0
                var truncated = false

                contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val relativeIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)

                    while (cursor.moveToNext()) {
                        total++
                        if (tracks.length() >= 1500) {
                            truncated = true
                            continue
                        }
                        val id = cursor.getLong(idIdx)
                        val uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString()).toString()
                        val title = cursor.getString(titleIdx)?.ifBlank { "Cancion local" } ?: "Cancion local"
                        val artist = cursor.getString(artistIdx)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: ""
                        val relative = cursor.getString(relativeIdx) ?: ""
                        val folder = relative.trim('/').ifBlank { "Sin carpeta" }
                        tracks.put(
                            JSONObject()
                                .put("uri", uri)
                                .put("title", title)
                                .put("artist", artist)
                                .put("folder", folder)
                        )
                    }
                }

                val payload = JSONObject()
                    .put("tracks", tracks)
                    .put("total", total)
                    .put("truncated", truncated)
                    .toString()
                dispatchNativeAudioLibrary(payload)
            } catch (_: SecurityException) {
                dispatchNativeAudioLibraryError("No hay permiso para leer la musica del movil.")
            } catch (_: Throwable) {
                dispatchNativeAudioLibraryError("No se pudo cargar tu biblioteca de musica.")
            }
        }.start()
    }

    private fun dispatchNativeAudioLibrary(payload: String) {
        if (isFinishing || isDestroyed) return
        val js = """
            (function() {
              var payload = $payload;
              if (window.onNativeAudioLibrary) {
                window.onNativeAudioLibrary(payload);
                return;
              }
              var frames = document.getElementsByTagName('iframe');
              for (var i = 0; i < frames.length; i++) {
                try {
                  if (frames[i].contentWindow && frames[i].contentWindow.onNativeAudioLibrary) {
                    frames[i].contentWindow.onNativeAudioLibrary(payload);
                    return;
                  }
                } catch (e) {}
              }
            })();
        """.trimIndent()
        webView.post {
            if (isFinishing || isDestroyed) return@post
            webView.evaluateJavascript(js, null)
        }
    }

    private fun dispatchNativeAudioLibraryError(message: String) {
        if (isFinishing || isDestroyed) return
        val safeMessage = JSONObject.quote(message)
        val js = """
            (function() {
              var msg = $safeMessage;
              if (window.onNativeAudioLibraryError) {
                window.onNativeAudioLibraryError(msg);
                return;
              }
              var frames = document.getElementsByTagName('iframe');
              for (var i = 0; i < frames.length; i++) {
                try {
                  if (frames[i].contentWindow && frames[i].contentWindow.onNativeAudioLibraryError) {
                    frames[i].contentWindow.onNativeAudioLibraryError(msg);
                    return;
                  }
                } catch (e) {}
              }
            })();
        """.trimIndent()
        webView.post {
            if (isFinishing || isDestroyed) return@post
            webView.evaluateJavascript(js, null)
        }
    }

    private fun dispatchNativeAudioSelected(uri: Uri, title: String, artist: String) {
        if (isFinishing || isDestroyed) return
        val payload = JSONObject()
            .put("uri", uri.toString())
            .put("title", title)
            .put("artist", artist)
            .toString()
                pendingNativeSelectionPayload = payload
                dispatchRetryCount = 0
                tryDispatchPendingNativeSelection()
        }

        private fun tryDispatchPendingNativeSelection() {
                if (isFinishing || isDestroyed) return
                val payload = pendingNativeSelectionPayload ?: return

        val js = """
            (function() {
              var payload = $payload;
              if (window.onNativeAudioSelected) {
                window.onNativeAudioSelected(payload);
                                return 'OK';
              }
              var frames = document.getElementsByTagName('iframe');
              for (var i = 0; i < frames.length; i++) {
                try {
                  if (frames[i].contentWindow && frames[i].contentWindow.onNativeAudioSelected) {
                    frames[i].contentWindow.onNativeAudioSelected(payload);
                                        return 'OK';
                  }
                } catch (e) {}
              }
                            return 'PENDING';
            })();
        """.trimIndent()

        webView.post {
            if (isFinishing || isDestroyed) return@post
                        webView.evaluateJavascript(js) { result ->
                                val ok = result?.contains("OK") == true
                                if (ok) {
                                        pendingNativeSelectionPayload = null
                                        dispatchRetryCount = 0
                                        return@evaluateJavascript
                                }
                                if (pendingNativeSelectionPayload == null) return@evaluateJavascript
                                if (dispatchRetryCount >= 8) return@evaluateJavascript
                                dispatchRetryCount += 1
                                webView.postDelayed({ tryDispatchPendingNativeSelection() }, 350L)
                        }
        }
    }

    private fun dispatchSelectionError(message: String) {
        if (isFinishing || isDestroyed) return
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
            if (isFinishing || isDestroyed) return@post
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

    private fun loadConfiguredUrl() {
        val remoteUrl = prefs.getString(PREF_TEST_URL, "")?.trim().orEmpty()
        if (remoteUrl.startsWith("http://") || remoteUrl.startsWith("https://")) {
            webView.loadUrl(remoteUrl)
            return
        }
        webView.loadUrl(LOCAL_ASSET_URL)
    }

    private fun showTestUrlDialog() {
        val input = EditText(this)
        input.hint = "https://tu-url/index.html"
        input.setText(prefs.getString(PREF_TEST_URL, ""))

        AlertDialog.Builder(this)
            .setTitle("URL de pruebas")
            .setMessage("Pon una URL remota para probar cambios sin reinstalar. Dejalo vacio para volver al APK local.")
            .setView(input)
            .setPositiveButton("Guardar y cargar") { _, _ ->
                val newUrl = input.text?.toString()?.trim().orEmpty()
                if (newUrl.isNotEmpty() && !newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
                    Toast.makeText(this, "URL invalida. Usa http:// o https://", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                prefs.edit().putString(PREF_TEST_URL, newUrl).apply()
                clearCacheAndReload()
            }
            .setNeutralButton("Usar APK local") { _, _ ->
                prefs.edit().remove(PREF_TEST_URL).apply()
                clearCacheAndReload()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun clearCacheAndReload() {
        try {
            webView.stopLoading()
            webView.clearHistory()
            webView.clearCache(true)
            webView.clearFormData()
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        } catch (_: Exception) {
        }
        loadConfiguredUrl()
        Toast.makeText(this, "Cache limpiada y vista recargada", Toast.LENGTH_SHORT).show()
    }

    inner class NativeMusicBridge {
        @JavascriptInterface
        fun pickAudioForPlaylist() {
            runOnUiThread {
                if (isPickerOpen) return@runOnUiThread
                isPickerOpen = true
                pickAudioLauncher.launch(arrayOf("audio/*"))
            }
        }

        @JavascriptInterface
        fun listDeviceAudio() {
            runOnUiThread {
                ensureAudioPermissionAndLoadLibrary()
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

    companion object {
        private const val PREFS_NAME = "carorur_settings"
        private const val PREF_TEST_URL = "test_url"
        private const val LOCAL_ASSET_URL = "https://appassets.androidplatform.net/assets/web/index.html"
    }
}
