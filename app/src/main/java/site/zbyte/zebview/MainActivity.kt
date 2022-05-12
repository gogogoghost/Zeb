package site.zbyte.zebview

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader


class MainActivity : AppCompatActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        val zv=findViewById<Zebview>(R.id.zv)

        zv.addService("JavaService",JavaService)

        zv.clearCache(true)


        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        zv.webViewClient= object :WebViewClient(){
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
        val webViewSettings: WebSettings = zv.settings
        webViewSettings.javaScriptEnabled=true
        webViewSettings.allowFileAccess=false
        webViewSettings.allowContentAccess=false

//        zv.loadUrl("https://appassets.androidplatform.net/assets/index.html")
        zv.loadUrl("http://192.168.0.137:3000")
    }
}

object JavaService{
    @JavascriptInterface
    fun doSomething(
        argInt:Int,
        argStr:String,
        argBool:Boolean,
        argArr:Array<Any>,
        argCallback:Callback,
        argObject:CallbackObject
    ){
        println("$argInt $argStr $argBool")
        argArr.forEach {
            println(it)
        }
        argCallback.call(123456,"test string",true, arrayOf(456798,"string in array",false))
        argObject.call("success","I am in object")
        argCallback.release()
        argObject.release()
    }
}