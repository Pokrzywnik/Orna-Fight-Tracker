package com.frodo.fighttracker

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.webkit.WebView

@SuppressLint("SetJavaScriptEnabled")
class AuraWebView(context: Context) : WebView(context) {

    init {
        settings.apply {
            javaScriptEnabled = false
            allowFileAccess = true
            builtInZoomControls = false
            displayZoomControls = false
        }
        setBackgroundColor(Color.TRANSPARENT)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    fun loadAura(auraName: String) {
        val html = """
            <html>
            <body style="margin:0;padding:0;width:100vw;height:100vh;overflow:hidden;background:transparent;">
                <img src="file:///android_asset/aura/$auraName" 
                     style="width:100vw;height:100vh;object-fit:cover;" />
            </body>
            </html>
        """.trimIndent()
        loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
}