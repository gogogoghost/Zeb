package site.zbyte.zeb

import android.webkit.JavascriptInterface
import site.zbyte.zeb.callback.Callback
import site.zbyte.zeb.data.JsObject

class BaseService: JsObject() {

    //存放初始化所用所有对象 不释放
    private val serviceMap=HashMap<String,JsObject>()
    private var callbackObj : Callback?=null

//    注册事件顺便返回所有服务
    @JavascriptInterface
    fun getService(obj: Callback):Array<Any>{
        callbackObj = obj
        val arr=ArrayList<Any>()
        for((key,value) in serviceMap){
            arr.add(arrayOf(key,value))
        }
        return arr.toTypedArray()
    }

    fun onAdd(name:String,obj: JsObject){
        //保存一下
        serviceMap[name]=obj
        //如果有回调 回调一下
        callbackObj?.call(name,obj)
    }

    fun getServiceByName(name:String):JsObject?{
        return serviceMap[name]
    }

//    fun getServiceById(id:Long):JsObject?{
//        for(service in serviceMap.values){
//    }
}