package site.zbyte.zebview

import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
import site.zbyte.zebview.callback.Callback

@JavascriptClass
class BaseService {

    private var waitForCallback = ArrayList<Any>()
    private var callbackObj : Callback?=null

//    注册事件顺便返回所有服务
    @JavascriptInterface
    fun registerServiceWatcher(obj: Callback):Array<Any>{
        synchronized(this) {
            callbackObj = obj
            val res=waitForCallback.toArray()
            waitForCallback.clear()
            return res
        }
    }

    fun onAdd(obj:Any){
        if(!obj::class.java.isAnnotationPresent(JavascriptClass::class.java))
            return
        synchronized(this){
            if(callbackObj==null){
                waitForCallback.add(obj)
            }else{
                callbackObj!!.call(obj)
            }
        }
    }
}