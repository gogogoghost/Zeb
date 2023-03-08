package site.zbyte.zebview

import android.webkit.JavascriptInterface
import site.zbyte.zebview.callback.Callback
import site.zbyte.zebview.data.SharedObject

class BaseService(private val zv:ZebView) {

    //存放初始化所用所有对象 不释放
    private var globalServiceCallback = ArrayList<Any>()
    private var callbackObj : Callback?=null

//    注册事件顺便返回所有服务
    @JavascriptInterface
    fun registerServiceWatcher(obj: Callback):Array<Any>{
        synchronized(this) {
            callbackObj = obj
            return globalServiceCallback.toArray()
        }
    }

    fun onAdd(name:String,obj: SharedObject){
        synchronized(this){
            //保存一下
            globalServiceCallback.add(name)
            globalServiceCallback.add(obj)
            //如果有回调 回调一下
            if(callbackObj!=null){
                callbackObj!!.call(name,obj)
            }
        }
    }
}