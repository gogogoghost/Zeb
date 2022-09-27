package site.zbyte.zebview

import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
import site.zbyte.zebview.callback.Callback

class BaseService {

    private var waitForCallback = JSONArray()
    private var callbackObj : Callback?=null

//    注册事件顺便返回所有服务
    @JavascriptInterface
    fun registerServiceWatcher(obj: Callback):JSONArray{
        synchronized(this) {
            callbackObj = obj
            val tmpArr=waitForCallback
//            设置为新对象 释放内存
            waitForCallback=JSONArray()
            return tmpArr
        }
    }

    fun onAdd(name:String,funcList:Set<String>){
        val data=JSONObject()
        data.put("name",name)
        val funcArray=JSONArray()
        funcList.forEach {
            funcArray.put(it)
        }
        data.put("funcList",funcArray)
        synchronized(this){
            if(callbackObj==null){
                waitForCallback.put(data)
            }else{
                callbackObj!!.call(data)
            }
        }
    }
}