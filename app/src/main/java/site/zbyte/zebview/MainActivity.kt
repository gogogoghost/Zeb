package site.zbyte.zebview

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView

class MainActivity : AppCompatActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        val zv=findViewById<Zebview>(R.id.zv)

        zv.addApi("A",ServiceA)
        zv.addApi("B",ServiceB)

        zv.clearCache(true)

        zv.settings.javaScriptEnabled=true

        zv.loadUrl("http://192.168.0.137:3000")
    }
}

object ServiceA{
    @JavascriptInterface
    fun actionA(){

    }
}

object ServiceB{
    @JavascriptInterface
    fun actionB(){

    }
}