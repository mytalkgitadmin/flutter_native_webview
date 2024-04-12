package com.hisaichi5518.native_webview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.core.content.ContextCompat.startActivity
import io.flutter.plugin.common.MethodChannel
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class NativeWebView(context: Context, channel: MethodChannel, options: WebViewOptions) : InputAwareWebView(context, null as View?) {
    init {
        webViewClient = NativeWebViewClient(channel, options)
        webChromeClient = NativeWebChromeClient(channel)
        @SuppressLint("SetJavaScriptEnabled")
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.loadsImagesAutomatically = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        addJavascriptInterface(JavascriptHandler(channel), NativeWebChromeClient.JAVASCRIPT_BRIDGE_NAME)

        setDownloadListener { url, _, _, mimetype, _ ->
            val intent = Intent(Intent.ACTION_VIEW)
            intent.type = mimetype
            intent.data = Uri.parse(url)

            val activity = Locator.activity ?: return@setDownloadListener
            if (intent.resolveActivity(activity.packageManager) != null) {
                startActivity(activity, intent, Bundle())
            }
        }
    }

    fun load(initialData: Map<String, String>?, initialFile: String?, initialURL: String, initialHeaders: Map<String, String>?) {
        initialData?.let {
            val data = it["data"]
            val mimeType = it["mimeType"]
            val encoding = it["encoding"]
            val baseUrl = it["baseUrl"]
            val historyUrl = it["historyUrl"]
            loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl)
            return
        }

        initialFile?.let { path ->
            val filename = Locator.binding!!.flutterAssets.getAssetFilePathByName(path)
            loadUrl("file:///android_asset/${filename}", initialHeaders)
            return
        }

        loadUrl(initialURL, initialHeaders)
    }

    @SuppressLint("WebViewApiAvailability")
    @Suppress("DEPRECATION")
    fun postUrl(postUrl: String, additionalHttpHeaders: Map<String, String>?) {

        val savedWebViewClient = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webViewClient
        } else {
            TODO("VERSION.SDK_INT < O")
        }

        webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {

                if (url != postUrl) {
                    view.post {
                        webViewClient = savedWebViewClient
                    }
                    return savedWebViewClient?.shouldInterceptRequest(view, url)
                }

                val httpsUrl = URL(url)
                val conn: HttpsURLConnection = httpsUrl.openConnection() as HttpsURLConnection
                conn.requestMethod = "POST"
                additionalHttpHeaders?.forEach { header ->
                    conn.addRequestProperty(header.key, header.value)
                }

                conn.outputStream.close()

                val responseCode = conn.responseCode
                Log.d("WebView extension", "responseCode = ${responseCode} ${conn.contentType}")
                view.post {
                    webViewClient = savedWebViewClient
                }

                // typical conn.contentType is "text/html; charset=UTF-8"
                return WebResourceResponse(conn.contentType.substringBefore(";"), "utf-8", conn.inputStream)
            }
        }

        loadUrl(postUrl, additionalHttpHeaders)
    }
}