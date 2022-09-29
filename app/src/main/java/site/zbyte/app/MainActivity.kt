package site.zbyte.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import site.zbyte.zebview.JavascriptClass
import site.zbyte.zebview.callback.Callback
import site.zbyte.zebview.callback.CallbackObject
import site.zbyte.zebview.callback.Promise
import site.zbyte.zebview.ZebView
import site.zbyte.zebview.value.JsNumber

class MainActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        val zv=findViewById<ZebView>(R.id.zv)

        zv.addGlobalJsObject("TestService", TestService)

        zv.clearCache(true)


//        val assetLoader = WebViewAssetLoader.Builder()
//            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
//            .build()

//        zv.webViewClient= object :WebViewClient(){
//            override fun shouldInterceptRequest(
//                view: WebView,
//                request: WebResourceRequest
//            ): WebResourceResponse? {
//                val res=assetLoader.shouldInterceptRequest(request.url)
//                if(request.url.toString().endsWith(".js")){
//                    res?.mimeType="text/javascript"
//                }
//                return res
//            }
//        }
        //设置回调线程
        zv.setCallbackHandler(Handler(Looper.getMainLooper()))

        val webViewSettings: WebSettings = zv.settings
        webViewSettings.javaScriptEnabled=true
        webViewSettings.allowFileAccess=false
        webViewSettings.allowContentAccess=false

//        zv.loadUrl("https://appassets.androidplatform.net/assets/index.html")
        zv.loadUrl("http://192.168.0.127:3000")
    }
}

@JavascriptClass
object TestService{
    @JavascriptInterface
    fun test(
        argInt:JsNumber,
        argStr:String,
        argBool:Boolean,
        argArr:Array<Any>,
        argCallback: Callback,
        argObject: CallbackObject
    ){
        println(Thread.currentThread())
        println("$argInt $argStr $argBool")
        argArr.forEach {
            println(it)
        }
        argCallback.call(123456,"test string",true, arrayOf(456798,"string in array",false))

        argObject.call("success","return")
    }

    private val promise= Promise<String>{
        println("working!!!!!!!!1")
        it.reject("Promise work done")
    }

    @JavascriptInterface
    fun manyWork(): Promise<String> {
        return promise
    }
}