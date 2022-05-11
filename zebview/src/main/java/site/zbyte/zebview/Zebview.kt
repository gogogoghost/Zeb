package site.zbyte.zebview

import android.content.Context
import android.util.AttributeSet
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Method

class Zebview: WebView {

    companion object{
        private const val TAG="Zebview"

        const val FUNCTION_PREFIX = "_func#"
        const val OBJECT_PREFIX = "_object#"
        const val BYTEARRAY_PREFIX = "_bytes#"
    }

    constructor(context: Context):super(context)

    constructor(context: Context,attrs: AttributeSet):super(context,attrs)

    constructor(context: Context,attrs: AttributeSet,defStyle:Int):super(context, attrs, defStyle)

    init {
        addJavascriptInterface(this,"bridge")
    }

    private val apiMap=HashMap<String, Api>()

    //添加一个api服务
    fun addApi(name:String,obj:Any): Zebview {
        Api(obj).also {
            apiMap[name]=it
            //向js注册该方法
            val list= JSONArray()
            it.funcList().forEach { func->
                list.put(func)
            }
        }
        return this
    }

    @JavascriptInterface
    fun init():String{
        val map= JSONObject()
        apiMap.forEach {
            val list= JSONArray()
            it.value.funcList().forEach { func->
                list.put(func)
            }
            map.put(it.key,list)
        }
        return map.toString(0)
    }

    @JavascriptInterface
    fun call(module:String,func:String,args:String):Any?{
        if(apiMap.containsKey(module)){
            val api=apiMap[module]!!
            if(api.hasFunc(func)){
                return api.call(func,args)
            }else{
                Log.w(TAG,"Function:${func} in module:${module} is not found")
            }
        }else{
            Log.w(TAG,"Module:${module} is not found")
        }
        return null
    }

    /**
     * Api class
     */
    private inner class Api(private val obj: Any) {

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

        fun call(name: String, jsonString: String): Any? {
            //js调用java方法，将js对象转为java对象
            val method = funcMap[name]!!
            val argsList = JSONArray(jsonString)
            val argsArray = ArrayList<Any>()
            for (i in 0 until argsList.length()) {
                val args = argsList.get(i)
                val argsString = args.toString()
                if (argsString.startsWith(FUNCTION_PREFIX)) {
                    //方法回调
                    argsArray.add(object : Callback() {
                        override fun call(array:JSONArray) {
                            runMain {
                                this@Zebview.evaluateJavascript(
                                    "window.invokeCallback(\"$argsString\",\"${array}\")",
                                    null
                                )
                            }
                        }

                        override fun release() {
                            runMain {
                                this@Zebview.evaluateJavascript("window.releaseCallback(\"$argsString\")", null)
                            }
                        }
                    })
                } else if (argsString.startsWith(OBJECT_PREFIX)) {
                    //带有回调的对象
                    argsArray.add(object : CallbackObject() {
                        override fun call(funcName: String, args:JSONArray) {
                            val str="window.invokeObjectCallback(\"$argsString\",\"${funcName}\",\"${args.toString().replace("\"","\\\"")}\")"
                            runMain {
                                this@Zebview.evaluateJavascript(
                                    str,
                                    null
                                )
                            }
                        }
                        override fun release() {
                            runMain {
                                this@Zebview.evaluateJavascript("window.releaseObject(\"$argsString\")", null)
                            }
                        }
                    })
                } else if (argsString.startsWith(BYTEARRAY_PREFIX)) {
                    //字节数组
                    val bytes = Base64.decode(argsString.substring(BYTEARRAY_PREFIX.length), Base64.NO_WRAP)
                    argsArray.add(bytes)
                } else {
                    //普通数据
                    argsArray.add(args)
                }
            }
            val res=method.invoke(obj, *argsArray.toTypedArray())
            return processArg(res)
        }
    }
}