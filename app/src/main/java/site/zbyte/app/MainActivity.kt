package site.zbyte.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import site.zbyte.zeb.Zeb
import site.zbyte.zeb.data.SharedObject

class MainActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        val src=findViewById<WebView>(R.id.zv)
        val zv=Zeb(src)

        zv.addJsObject("TestService", SharedObject(TestService(),true))

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        src.webViewClient= object :WebViewClient(){
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val res=assetLoader.shouldInterceptRequest(request.url)
                if(request.url.toString().endsWith(".js")){
                    res?.mimeType="text/javascript"
                }
                return res
            }
        }

        val webViewSettings: WebSettings = src.settings
        webViewSettings.javaScriptEnabled=true
        webViewSettings.allowFileAccess=false
        webViewSettings.allowContentAccess=false

        src.loadUrl("http://192.168.0.127:3000")
    }
}