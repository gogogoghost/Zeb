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
    }

    /**
     * JS->Native 标志
     */
    enum class REQT(val v:Byte){
        NULL(1),
        //基本类型
        STRING(2),
        NUMBER(3),
        //特殊类型
        FUNCTION(10),
        OBJECT(11),
        BYTEARRAY(12),
        ARRAY(13)
    }
    /**
     * Native->JS 标志
     */
    enum class REST(val v:Byte){
        NULL(1),
        //基本类型
        STRING(2),
        INT(4),
        FLOAT(5),
        //特殊类型
        OBJECT(11),
        BYTEARRAY(12),
        ARRAY(13),
        PROMISE(14)
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

    //添加一个 命名对象供js调用
    fun addNamedJsObject(name:String, obj:Any): ZebView {
        JsCallableObject(obj).also {
            namedJsCallableObjectMap[name]=it
            baseService.onAdd(obj)
        }
        return this
    }

    //添加一个 匿名对象供js调用
    private fun addJsObject(id:String,obj:Any):JsCallableObject{
        return JsCallableObject(obj).also {
            jsCallableObjectMap[id]=it
        }
    }

    /**
     * 调用有名字的对象
     */
    @JavascriptInterface
    fun callNamedObject(module:String,func:String,args:String):String{
        if(namedJsCallableObjectMap.containsKey(module)){
            val api=namedJsCallableObjectMap[module]!!
            if(api.hasFunc(func)){
                val res= String(api.call(func,args))
                println("end==============")
                println(res)
                return res
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
                return String(api.call(func,args))
            }else{
                Log.w(TAG,"Function:${func} in Object:${id} is not found")
            }
        }else{
            Log.w(TAG,"Object:${id} is not found")
        }
        return "[]"
    }

    fun appendResponse(res:Response){
        callbackQueue.put(res)
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
    fun encodeArg(arg:Any?):ByteArray{
        println(arg)
        when(arg){
            null->{
                return REST.NULL.v.toByteArray()
            }
            //number
            is Int->{
                return REST.INT.v.toByteArray()+arg.toByteArray()
            }
            is Long->{
                return REST.INT.v.toByteArray()+arg.toByteArray()
            }
            is Float->{
                return REST.FLOAT.v.toByteArray()+arg.toByteArray()
            }
            is Double->{
                return REST.FLOAT.v.toByteArray()+arg.toByteArray()
            }
            //string
            is String->{
                return REST.STRING.v.toByteArray()+arg.toByteArray()
            }
            //array
            is Array<*> ->{
                return REST.ARRAY.v.toByteArray()+encodeArray(arg)
            }
            //promise
            is Promise<*>->{
                return REST.PROMISE.v.toByteArray()+arg.getId().toByteArray()
            }
            //bytearray
            is ByteArray->{
                return REST.BYTEARRAY.v.toByteArray()+arg
            }
            else->{
                //判断是不是Object
                if(arg::class.java.isAnnotationPresent(JavascriptClass::class.java)){
                    //为对象生成name
                    val objectId= randomString(16)
                    val jsObject=addJsObject(
                        objectId,
                        arg
                    )
                    println("object!!!!!!!")
                    return REST.OBJECT.v.toByteArray()+
                            //对象名称
                            objectId.toByteArray()+
                            //结尾
                            0+
                            //方法名称
                            jsObject.funcList().joinToString(",").toByteArray()
                }else{
                    throw Exception("Not support type to encode")
                }
            }
        }
    }

    /**
     * 编码一个数组返回(body)
     */
    fun encodeArray(arg:Array<*>):ByteArray{
        val arr=ArrayList<Byte>()
        println("start array")
        println(arg)
        println(arg.size)
        arg.forEach {
            println("exec")
            println(it)
            val data=encodeArg(it)
            println(data.toStr())
            //length
            arr.addAll(ByteBuffer.allocate(4).putInt(data.size).array().asIterable())
            //body
            arr.addAll(data.asIterable())
        }
        return arr.toByteArray()
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
         * 输入：(类型)+(原始数据)
         * 输出：对象
         */
        private fun decodeArg(byteArray: ByteArray):Any?{
            val type=byteArray[0]
            val body=byteArray.sliceArray(1 until byteArray.size)
            when(type){
                REQT.FUNCTION.v->{
                    //方法回调
                    val token=String(body)
                    return Callback(this@ZebView,token)
                }
                REQT.OBJECT.v->{
                    //带有回调的对象
                    val token=String(body)
                    return CallbackObject(this@ZebView,token)
                }
                REQT.BYTEARRAY.v->{
                    //字节数组
                    return body
                }
                REQT.ARRAY.v->{
                    //数组
                    return decodeArray(body)
                }
                REQT.NUMBER.v->{
                    return JsNumber(body)
                }
                REQT.STRING.v->{
                    return String(body)
                }
                REQT.NULL.v->{
                    return null
                }
                else->{
                    //其他情况 非法
                    throw Exception("Not support type to decode")
                }
            }
        }

        /**
         * 解码数组
         * 数组为：[(长度)+(body)]
         */
        private fun decodeArray(byteArray: ByteArray):ArrayList<Any?>{
            println(byteArray.toStr())
            val buffer=ByteBuffer.wrap(byteArray)
            val out=ArrayList<Any?>()
            while(buffer.remaining()>0){
                //获取长度
                val size=buffer.int
                println("size:$size")
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
            val r=encodeArg(res)
            println(r.toStr())
            return r
        }
    }
}