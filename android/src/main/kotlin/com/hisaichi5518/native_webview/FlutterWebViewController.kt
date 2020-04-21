package com.hisaichi5518.native_webview

import android.view.View
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView

class FlutterWebViewController(
    private val channel: MethodChannel,
    args: Map<String, Any>
) : PlatformView, MethodChannel.MethodCallHandler {
    private val webview: InputAwareWebView

    init {
        val initialData = args["initialData"] as? Map<String, String>
        val initialFile = args["initialFile"] as? String
        val initialUrl = args["initialUrl"] as? String ?: "about:blank"
        val initialHeaders = args["initialHeaders"] as? Map<String, String>

        channel.setMethodCallHandler(this)

        webview = NativeWebView(channel, Locator.activity!!)
        webview.load(initialData, initialFile, initialUrl, initialHeaders)
    }

    override fun getView(): View {
        return webview
    }

    override fun dispose() {
        channel.setMethodCallHandler(null)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "evaluateJavascript" -> {
                val javaScriptString = call.arguments as String

                webview.post {
                    webview.evaluateJavascript(javaScriptString) {
                        result.success(it)
                    }
                }
            }
            "currentUrl" -> {
                result.success(webview.url)
            }
            "loadUrl" -> {
                val arguments = call.arguments as Map<String, Any>
                val url = arguments["url"] as? String
                if (url == null) {
                    result.error("loadUrl", "Can not find url", null)
                    return
                }

                webview.loadUrl(url, arguments["headers"] as? Map<String, String>)
                result.success(true)
            }
        }
    }

    override fun onFlutterViewAttached(flutterView: View) {
        webview.setContainerView(flutterView)
    }

    override fun onFlutterViewDetached() {
        webview.setContainerView(null)
    }
}