package site.zbyte.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import site.zbyte.zebview.JavascriptClass
import site.zbyte.zebview.callback.Callback
import site.zbyte.zebview.callback.CallbackObject
import site.zbyte.zebview.callback.Promise
import site.zbyte.zebview.ZebView
import site.zbyte.zebview.toStr

class MainActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        val src=findViewById<WebView>(R.id.zv)
        val zv=ZebView(src)

        zv.addJsObject("TestService", TestService)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        src.webViewClient= object :WebViewClient(){
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val zvRes=zv.shouldInterceptRequest(view,request)
                if(zvRes!=null)
                    return zvRes
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

@JavascriptClass
object WorkObject{
    @JavascriptInterface
    fun work():String{
//        throw Error("I am a error from native")
        return "workObject work done"
    }
}

@JavascriptClass
object TestService{

    @JavascriptInterface
    fun getWorker():WorkObject{
        return WorkObject
    }

    @JavascriptInterface
    fun test(
        argInt:Long,
        argFloat:Double,
        argStr:String,
        argBool:Boolean,
        argArr:Array<Any>,
        argCallback: Callback,
        argObject: CallbackObject
    ):Double{
        println(Thread.currentThread())
        println(argFloat)
        println("$argInt $argStr $argBool")
        argArr.forEach {
            println(it)
        }
        argCallback.call(123456,"test string",true, arrayOf(456798,"string in array",false))

        argObject.call("success","return")
        return 10.24
    }

    private val promise= Promise<String>{
        println("working!!!!!!!!1")
        throw Exception("some error")
        println("work done")
        it.resolve("Promise work done")
    }

    @JavascriptInterface
    fun manyWork(): Promise<String> {
        return promise
    }

    @JavascriptInterface
    fun jsonTest(obj:JSONObject):JSONObject{
        println("I got json:${obj.toString()}")
        val obj=JSONObject()
        obj.put("name","Jack")
        return obj
    }

    @JavascriptInterface
    fun testByte(bytes:ByteArray):ByteArray{
        throw Exception("I am message")
        println("testByte")
        println(bytes.toStr())
        return byteArrayOf(0x35,0x56)
    }
}