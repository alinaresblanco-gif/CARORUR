package com.carorur.tracker

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private val maxNativeAudioItems = 180

    private data class NativeSelectionLimitResult(
        val files: JSONArray,
        val acceptedCount: Int,
        val totalSelectedCount: Int,
        val totalAcceptedBytes: Long,
        val truncated: Boolean,
        val limitReason: String?
    )

    private lateinit var webView: WebView
    private lateinit var prefs: SharedPreferences
    private lateinit var assetLoader: WebViewAssetLoader
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var nativeMusicMode: String? = null
    private var isNativePickerOpen = false
    private var nativeFolderCounter = 0

    private data class NativeAudioItem(
        val uri: Uri,
        val name: String,
        val sizeBytes: Long,
        val mimeType: String,
        val relativePath: String,
        val folderName: String
    )

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (nativeMusicMode != null) {
                handleNativeMusicResult(result.resultCode, result.data)
                return@registerForActivityResult
            }

            val callback = fileChooserCallback
            fileChooserCallback = null
            if (callback == null) return@registerForActivityResult

            val uris = try {
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            } catch (_: Throwable) {
                null
            }

            if (uris != null) {
                callback.onReceiveValue(uris)
                return@registerForActivityResult
            }

            val data = result.data
            if (result.resultCode != RESULT_OK || data == null) {
                callback.onReceiveValue(null)
                return@registerForActivityResult
            }

            val fromClip = data.clipData
            if (fromClip != null && fromClip.itemCount > 0) {
                val out = ArrayList<Uri>(fromClip.itemCount)
                for (i in 0 until fromClip.itemCount) {
                    val uri = fromClip.getItemAt(i).uri
                    if (uri != null) out.add(uri)
                }
                callback.onReceiveValue(if (out.isEmpty()) null else out.toTypedArray())
                return@registerForActivityResult
            }

            val single = data.data
            callback.onReceiveValue(if (single != null) arrayOf(single) else null)
        }

    private class NativeMusicBridge(private val host: MainActivity) {
        @JavascriptInterface
        fun pickAudioFiles() {
            host.runOnUiThread {
                host.launchNativeAudioPicker(mode = "files")
            }
        }

        @JavascriptInterface
        fun pickAudioFolder() {
            host.runOnUiThread {
                host.launchNativeAudioPicker(mode = "folder")
            }
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
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (filePathCallback == null) return false

                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback

                val chooserIntent = try {
                    fileChooserParams?.createIntent()
                } catch (_: Throwable) {
                    null
                } ?: Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }

                chooserIntent.addCategory(Intent.CATEGORY_OPENABLE)
                if (chooserIntent.type.isNullOrBlank()) {
                    chooserIntent.type = "*/*"
                }

                if (fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    chooserIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }

                try {
                    fileChooserLauncher.launch(chooserIntent)
                    return true
                } catch (_: Throwable) {
                    fileChooserCallback?.onReceiveValue(null)
                    fileChooserCallback = null
                    Toast.makeText(this@MainActivity, "No se pudo abrir el selector de archivos", Toast.LENGTH_SHORT).show()
                    return false
                }
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
        webView.addJavascriptInterface(NativeMusicBridge(this), "CarorurNativeMusic")

        btnSetTestUrl.setOnClickListener { showTestUrlDialog() }
        btnClearCacheReload.setOnClickListener { clearCacheAndReload() }

        loadConfiguredUrl()
    }

    override fun onDestroy() {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        webView.destroy()
        super.onDestroy()
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

    private fun launchNativeAudioPicker(mode: String) {
        if (isNativePickerOpen) {
            sendNativeMusicResultError("Ya hay un selector abierto. Cierra el selector actual e intentalo de nuevo.")
            return
        }

        val intent = when (mode) {
            "folder" -> Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            else -> Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        }

        nativeMusicMode = mode
        isNativePickerOpen = true
        try {
            fileChooserLauncher.launch(intent)
        } catch (_: Throwable) {
            isNativePickerOpen = false
            nativeMusicMode = null
            sendNativeMusicResultError("No se pudo abrir el selector nativo de audio.")
        }
    }

    private fun handleNativeMusicResult(resultCode: Int, data: Intent?) {
        val mode = nativeMusicMode
        nativeMusicMode = null
        isNativePickerOpen = false

        if (mode == null) return
        if (resultCode != RESULT_OK || data == null) {
            sendNativeMusicResultCancelled(mode)
            return
        }

        if (mode == "folder") {
            handleNativeFolderResult(data)
            return
        }

        handleNativeFilesResult(data)
    }

    private fun handleNativeFilesResult(data: Intent) {
        val uris = ArrayList<Uri>()
        val clip = data.clipData
        if (clip != null && clip.itemCount > 0) {
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i)?.uri?.let { uris.add(it) }
            }
        } else {
            data.data?.let { uris.add(it) }
        }

        if (uris.isEmpty()) {
            sendNativeMusicResultError("No se seleccionaron canciones.")
            return
        }

        val takeFlags = data.flags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val out = JSONArray()
        var acceptedCount = 0
        var truncated = false
        var limitReason: String? = null

        for (uri in uris) {
            if (acceptedCount >= maxNativeAudioItems) {
                truncated = true
                limitReason = "Se han cargado solo las primeras $maxNativeAudioItems canciones para mantener estable la app."
                break
            }
            tryTakePersistablePermission(uri, takeFlags)
            val item = buildAudioItemFromUri(uri, folderName = "ARCHIVOS SUELTOS", relativePath = "") ?: continue
            out.put(audioItemToJson(item))
            acceptedCount += 1
        }

        sendNativeMusicResult(
            mode = "files",
            files = out,
            acceptedCount = acceptedCount,
            totalSelectedCount = uris.size,
            totalAcceptedBytes = 0L,
            truncated = truncated,
            limitReason = limitReason
        )
    }

    private fun handleNativeFolderResult(data: Intent) {
        val treeUri = data.data
        if (treeUri == null) {
            sendNativeMusicResultError("No se pudo abrir la carpeta seleccionada.")
            return
        }

        val takeFlags = data.flags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        tryTakePersistablePermission(treeUri, takeFlags)

        val root = DocumentFile.fromTreeUri(this, treeUri)
        if (root == null || !root.exists() || !root.isDirectory) {
            sendNativeMusicResultError("La carpeta seleccionada no es valida.")
            return
        }

        nativeFolderCounter = 0
        val limitResult = collectAudioFromTree(root, root.name ?: "CARPETA", "")

        if (limitResult.files.length() == 0) {
            sendNativeMusicResultError("No se detectaron audios en la carpeta seleccionada.")
            return
        }

        sendNativeMusicResult(
            mode = "folder",
            files = limitResult.files,
            acceptedCount = limitResult.acceptedCount,
            totalSelectedCount = limitResult.totalSelectedCount,
            totalAcceptedBytes = limitResult.totalAcceptedBytes,
            truncated = limitResult.truncated,
            limitReason = limitResult.limitReason
        )
    }

    private fun collectAudioFromTree(
        folder: DocumentFile,
        folderName: String,
        relativePath: String
    ): NativeSelectionLimitResult {
        val sink = JSONArray()
        var totalFound = 0
        var truncated = false
        var limitReason: String? = null

        fun walk(currentFolder: DocumentFile, currentRelativePath: String) {
            if (nativeFolderCounter >= maxNativeAudioItems || truncated) return
        val children = try {
                currentFolder.listFiles()
        } catch (_: Throwable) {
            emptyArray()
        }

        for (child in children) {
                if (nativeFolderCounter >= maxNativeAudioItems || truncated) return
            if (child.isDirectory) {
                    val nextPath = if (currentRelativePath.isBlank()) {
                    child.name ?: ""
                } else {
                        "$currentRelativePath/${child.name ?: ""}"
                }
                    walk(child, nextPath)
                continue
            }

            val mime = child.type.orEmpty().lowercase()
            if (!mime.startsWith("audio/")) continue
                totalFound += 1

                if (nativeFolderCounter >= maxNativeAudioItems) {
                    truncated = true
                    limitReason = "Se han cargado solo las primeras $maxNativeAudioItems canciones de la carpeta para mantener estable la app."
                    return
                }

            val name = child.name ?: "Cancion"
                val rel = if (currentRelativePath.isBlank()) name else "$currentRelativePath/$name"
            val item = NativeAudioItem(
                uri = child.uri,
                name = name,
                sizeBytes = child.length(),
                mimeType = child.type ?: "audio/*",
                relativePath = rel,
                folderName = folderName
            )
            sink.put(audioItemToJson(item))
            nativeFolderCounter += 1
        }
        }

        walk(folder, relativePath)

        return NativeSelectionLimitResult(
            files = sink,
            acceptedCount = nativeFolderCounter,
            totalSelectedCount = totalFound,
            totalAcceptedBytes = 0L,
            truncated = truncated,
            limitReason = limitReason
        )
    }

    private fun buildAudioItemFromUri(uri: Uri, folderName: String, relativePath: String): NativeAudioItem? {
        val resolver = contentResolver
        val mime = resolver.getType(uri) ?: "audio/*"
        if (!mime.lowercase().startsWith("audio/")) return null

        var displayName = "Cancion"
        var size = 0L

        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) {
                            displayName = cursor.getString(nameIdx) ?: displayName
                        }
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            size = cursor.getLong(sizeIdx)
                        }
                    }
                }
        } catch (_: Throwable) {
        }

        return NativeAudioItem(
            uri = uri,
            name = displayName,
            sizeBytes = size,
            mimeType = mime,
            relativePath = relativePath,
            folderName = folderName
        )
    }

    private fun tryTakePersistablePermission(uri: Uri, flags: Int) {
        val grantFlags = flags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (grantFlags == 0) return

        try {
            contentResolver.takePersistableUriPermission(uri, grantFlags)
        } catch (_: SecurityException) {
        } catch (_: Throwable) {
        }
    }

    private fun audioItemToJson(item: NativeAudioItem): JSONObject {
        return JSONObject().apply {
            put("uri", item.uri.toString())
            put("name", item.name)
            put("size", item.sizeBytes)
            put("mime", item.mimeType)
            put("relativePath", item.relativePath)
            put("folderName", item.folderName)
        }
    }

    private fun sendNativeMusicResult(
        mode: String,
        files: JSONArray,
        acceptedCount: Int,
        totalSelectedCount: Int,
        totalAcceptedBytes: Long,
        truncated: Boolean,
        limitReason: String?
    ) {
        val payload = JSONObject().apply {
            put("ok", true)
            put("mode", mode)
            put("files", files)
            put("acceptedCount", acceptedCount)
            put("totalSelectedCount", totalSelectedCount)
            put("totalAcceptedBytes", totalAcceptedBytes)
            put("truncated", truncated)
            put("limitReason", limitReason ?: JSONObject.NULL)
        }
        sendNativeMusicPayload(payload)
    }

    private fun sendNativeMusicResultCancelled(mode: String) {
        val payload = JSONObject().apply {
            put("ok", false)
            put("mode", mode)
            put("cancelled", true)
        }
        sendNativeMusicPayload(payload)
    }

    private fun sendNativeMusicResultError(message: String) {
        val payload = JSONObject().apply {
            put("ok", false)
            put("error", message)
        }
        sendNativeMusicPayload(payload)
    }

    private fun sendNativeMusicPayload(payload: JSONObject) {
        val quotedPayload = JSONObject.quote(payload.toString())
        val js = """
            (function(raw){
                var attempts = 0;
                var maxAttempts = 12;

                function deliver(target) {
                    if (!target) return false;
                    var fn = target.__carorurNativeMusicReceive;
                    if (typeof fn !== 'function') return false;
                    try {
                        fn(raw);
                        return true;
                    } catch (e) {
                        return false;
                    }
                }

                function tryDeliver() {
                    attempts += 1;

                    if (deliver(window)) return;

                    try {
                        var iframe = document.getElementById('iframe-vista');
                        if (iframe && iframe.contentWindow && deliver(iframe.contentWindow)) return;
                    } catch (e) {
                    }

                    try {
                        var frames = window.frames || [];
                        for (var i = 0; i < frames.length; i++) {
                            if (deliver(frames[i])) return;
                        }
                    } catch (e) {
                    }

                    if (attempts < maxAttempts) {
                        setTimeout(tryDeliver, 180);
                        return;
                    }

                    try {
                        if (window.console && typeof console.warn === 'function') {
                            console.warn('CARORUR: callback __carorurNativeMusicReceive no encontrado.');
                        }
                    } catch (e) {
                    }
                }

                tryDeliver();
            })($quotedPayload);
        """.trimIndent()

        webView.post {
            try {
                webView.evaluateJavascript(js, null)
            } catch (t: Throwable) {
                Log.w("CARORUR", "No se pudo entregar resultado nativo de musica", t)
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "carorur_settings"
        private const val PREF_TEST_URL = "test_url"
        private const val LOCAL_ASSET_URL = "https://appassets.androidplatform.net/assets/web/index.html"
    }
}
