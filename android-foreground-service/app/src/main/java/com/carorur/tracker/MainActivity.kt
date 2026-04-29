package com.carorur.tracker

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var prefs: SharedPreferences
    private lateinit var assetLoader: WebViewAssetLoader

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

        loadConfiguredUrl()
    }

    override fun onDestroy() {
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

    companion object {
        private const val PREFS_NAME = "carorur_settings"
        private const val PREF_TEST_URL = "test_url"
        private const val LOCAL_ASSET_URL = "https://appassets.androidplatform.net/assets/web/index.html"
    }
}
