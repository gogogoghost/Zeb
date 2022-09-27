package site.zbyte.zebview

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebView
import org.json.JSONArray
import site.zbyte.zebview.callback.Callback
import site.zbyte.zebview.callback.CallbackObject
import site.zbyte.zebview.callback.Response
import site.zbyte.zebview.value.JsNumber
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue

class ZebView: WebView {

    companion object{
        private const val TAG="Zebview"

        /**
         * JS->Native 标志
         */
        enum class REQT{
            //基本类型
            NUMBER,
            STRING,
            //特殊类型
            FUNCTION,
            OBJECT,
            BYTEARRAY,
            ARRAY
        }
        /**
         * Native->JS 标志
         */
        enum class REST{
            //基本类型
            NUMBER,
            STRING,
            //特殊类型
            OBJECT,
            BYTEARRAY,
            ARRAY
        }


    }

    constructor(context: Context):super(context)

    constructor(context: Context,attrs: AttributeSet):super(context,attrs)

    constructor(context: Context,attrs: AttributeSet,defStyle:Int):super(context, attrs, defStyle)

//    有名字的js可调用对象
    private val namedJsCallableObjectMap=HashMap<String, JsCallableObject>()
//    普通的js可调用对象
    private val jsCallableObjectMap=HashMap<String,JsCallableObject>()
//    回调队列
    private val callbackQueue=LinkedBlockingQueue<Response>()

    private val mainHandler=Handler(Looper.getMainLooper())
    private var callbackHandler:Handler=mainHandler

    private val baseService=BaseService()

    init {
        addJavascriptInterface(this,"zebview")
//        手动添加内置服务 不触发onAdd
        JsCallableObject(baseService).also {
            namedJsCallableObjectMap["_base"]=it
        }
    }

    //覆写执行js 使js执行在主线程
    override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
        callbackHandler.post{
            super.evaluateJavascript(script, resultCallback)
        }
    }

    //设置回调handler
    fun setCallbackHandler(handler: Handler){
        callbackHandler=handler
    }

    //添加一个 对象供js调用
    fun addBaseObject(name:String, obj:Any): ZebView {
        JsCallableObject(obj).also {
            namedJsCallableObjectMap[name]=it
            baseService.onAdd(name,it.funcList())
        }
        return this
    }

    /**
     * 调用有名字的对象
     */
    @JavascriptInterface
    fun callNamedObject(module:String,func:String,args:String):String{
        if(namedJsCallableObjectMap.containsKey(module)){
            val api=namedJsCallableObjectMap[module]!!
            if(api.hasFunc(func)){
                return api.call(func,args).toString()
            }else{
                Log.w(TAG,"Function:${func} in Object:${module} is not found")
            }
        }else{
            Log.w(TAG,"Object:${module} is not found")
        }
        return "[]"
    }

    /**
     * 调用无名字的对象
     */
    @JavascriptInterface
    fun callObject(id:String,func:String,args:String):String{
        if(jsCallableObjectMap.containsKey(id)){
            val api=jsCallableObjectMap[id]!!
            if(api.hasFunc(func)){
                return api.call(func,args).toString()
            }else{
                Log.w(TAG,"Function:${func} in Object:${id} is not found")
            }
        }else{
            Log.w(TAG,"Object:${id} is not found")
        }
        return "[]"
    }

    /**
     * 阻塞接收native的消息 用于相应回调
     */
    @JavascriptInterface
    fun receive():String{
        return callbackQueue.take().stringify()
    }

    /**
     * 编码一个对象返回(类型)+(body)
     */
    fun encodeArg(arg:Any):ByteArray{
        val buf=ByteBuffer.allocate(4)

        when(arg){
            is Int->{
                return ByteBuffer.allocate(5)
                    .put()
            }
        }
    }

    /**
     * 编码一个数组返回(body)
     */
    fun encodeArray():ByteArray{

    }

    /**
     * Service class
     */
    private inner class JsCallableObject(private val obj: Any) {

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

        /**
         * 解码对象
         */
        private fun decodeArg(byteArray: ByteArray):Any{
            val type=byteArray[0]
            val body=byteArray.sliceArray(1..byteArray.size)
            when(type.toInt()){
                REQT.FUNCTION.ordinal->{
                    //方法回调
                    val token=String(body)
                    return Callback(this@ZebView,token)
                }
                REQT.OBJECT.ordinal->{
                    //带有回调的对象
                    val token=String(body)
                    return CallbackObject(this@ZebView,token)
                }
                REQT.BYTEARRAY.ordinal->{
                    //字节数组
                    return body
                }
                REQT.ARRAY.ordinal->{
                    //数组
                    return decodeArray(body)
                }
                REQT.NUMBER.ordinal->{
                    return JsNumber(body)
                }
                REQT.STRING.ordinal->{
                    return String(body)
                }
                else->{
                    //其他情况 非法
                    throw Exception("Not support value type")
                }
            }
        }

        /**
         * 解码数组
         */
        private fun decodeArray(byteArray: ByteArray):ArrayList<Any>{
            val buffer=ByteBuffer.wrap(byteArray)
            val out=ArrayList<Any>()
            while(buffer.remaining()>0){
                //获取长度
                val size=buffer.int
                //获取数据
                val data=ByteArray(size)
                buffer.get(data)
                //解析数据
                out.add(decodeArg(data))
            }
            return out
        }

        fun call(name: String, argsString: String): ByteArray {
            //js调用java方法，将js对象转为java对象
            val method = funcMap[name]!!
            //转换参数 js调用native函数，参数一定是array
            val argsArray=decodeArray(argsString.toByteArray())
            //执行函数
            val res=method.invoke(obj, *argsArray.toTypedArray())
            //返回参数
            return processArgs(this@ZebView,res)
        }
    }
}