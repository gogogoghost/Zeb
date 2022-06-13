package site.zbyte.zebview

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.AttributeSet
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Method

class ZebView: WebView {

    companion object{
        private const val TAG="Zebview"

        const val FUNCTION_PREFIX = "_func#"
        const val OBJECT_PREFIX = "_object#"
        const val BYTEARRAY_PREFIX = "_bytes#"
        const val PROMISE_PREFIX = "_promise#"
    }

    constructor(context: Context):super(context)

    constructor(context: Context,attrs: AttributeSet):super(context,attrs)

    constructor(context: Context,attrs: AttributeSet,defStyle:Int):super(context, attrs, defStyle)

    private val serviceMap=HashMap<String, Service>()

    private var callbackHandler:Handler?=null

    init {
        addJavascriptInterface(this,"zebview")
    }

    //调用java script
    override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
        if(callbackHandler==null){
            super.evaluateJavascript(script, resultCallback)
        }else{
            handler.post{
                super.evaluateJavascript(script, resultCallback)
            }
        }
    }

    //设置回调handler
    fun setCallbackHandler(handler: Handler){
        callbackHandler=handler
    }

    //添加一个api服务
    fun addService(name:String, obj:Any): ZebView {
        Service(obj).also {
            serviceMap[name]=it
            //向js注册该方法
            val list= JSONArray()
            it.funcList().forEach { func->
                list.put(func)
            }
        }
        return this
    }

    /**
     * js调用init获取所有服务
     */
    @JavascriptInterface
    fun getServices():String{
        val map= JSONObject()
        serviceMap.forEach {
            val list= JSONArray()
            it.value.funcList().forEach { func->
                list.put(func)
            }
            map.put(it.key,list)
        }
        return map.toString(0)
    }

    /**
     * js主动调用服务api
     */
    @JavascriptInterface
    fun callService(module:String,func:String,args:String):String{
        if(serviceMap.containsKey(module)){
            val api=serviceMap[module]!!
            if(api.hasFunc(func)){
                return api.call(func,args).toString()
            }else{
                Log.w(TAG,"Function:${func} in module:${module} is not found")
            }
        }else{
            Log.w(TAG,"Module:${module} is not found")
        }
        return "[]"
    }

    /**
     * Service class
     */
    private inner class Service(private val obj: Any) {

        private val funcMap = HashMap<String, Method>()

        init {
            //扫描所有方法
            obj.javaClass.methods.forEach { method ->
                //是否被修饰
                if (method.isAnnotationPresent(JavascriptInterface::class.java)) {
                    funcMap[method.name] = method
                }
            }
        }

        fun funcList(): Set<String> {
            return funcMap.keys
        }

        fun hasFunc(name: String): Boolean {
            return funcMap.containsKey(name)
        }

        fun call(name: String, jsonString: String): JSONArray {
            //js调用java方法，将js对象转为java对象
            val method = funcMap[name]!!
            //接受到的参数转为json array
            val argsList = JSONArray(jsonString)
            //实际传递给api的参数
            val argsArray = ArrayList<Any>()
            for (i in 0 until argsList.length()) {
                //参数对象
                val args = argsList.get(i)
                //参数对象名称 用于先判断是否为特殊对象
                val argsString = args.toString()
                if (argsString.startsWith(FUNCTION_PREFIX)) {
                    //方法回调
                    argsArray.add(Callback(this@ZebView,argsString))
                } else if (argsString.startsWith(OBJECT_PREFIX)) {
                    //带有回调的对象
                    argsArray.add( CallbackObject(this@ZebView,argsString))
                } else if (argsString.startsWith(BYTEARRAY_PREFIX)) {
                    //字节数组
                    val bytes = Base64.decode(argsString.substring(BYTEARRAY_PREFIX.length), Base64.NO_WRAP)
                    argsArray.add(bytes)
                } else if (args is JSONArray){
                    //数组 转换为Array<Any>
                    argsArray.add(Array(args.length()){
                        args.get(it)
                    })
                } else {
                    //普通数据 直接传递
                    argsArray.add(args)
                }
            }
            val res=method.invoke(obj, *argsArray.toTypedArray())
            return processArgs(this@ZebView,res)
        }
    }
}