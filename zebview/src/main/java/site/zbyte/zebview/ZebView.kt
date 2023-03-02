package site.zbyte.zebview

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.webkit.*
import org.json.JSONObject
import site.zbyte.zebview.callback.Callback
import site.zbyte.zebview.callback.CallbackObject
import site.zbyte.zebview.callback.Promise
import site.zbyte.zebview.callback.Response
import java.io.ByteArrayInputStream
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue

@SuppressLint("SetJavaScriptEnabled")
class ZebView(private val src:WebView) {

    private val handler = Handler(Looper.getMainLooper())

    /**
     * JS->Native 标志
     */
    enum class REQT(val v:Byte){
        NULL(1),
        //基本类型
        STRING(2),
        INT(4),
        FLOAT(5),
        BOOLEAN(6),
        //特殊类型
        FUNCTION(10),
        OBJECT(11),
        BYTEARRAY(12),
        ARRAY(13),
        ERROR(15),
        JSON(16)
    }
    /**
     * Native->JS 标志
     */
    enum class REST(val v:Byte){
        //null
        NULL(1),
        //基本类型
        STRING(2),
        INT(4),
        FLOAT(5),
        BOOLEAN(6),
        //特殊类型
        OBJECT(11),
        BYTEARRAY(12),
        ARRAY(13),
        PROMISE(14),
        ERROR(15),
        JSON(16)
    }

//    普通的js可调用对象
    private val jsCallableObjectMap=HashMap<String,JsCallableObject>()
//    回调队列
//    private val callbackQueue=LinkedBlockingQueue<Response>()

    //_base对象
    private val baseService=BaseService(this)
    private val baseJsObject=JsCallableObject(baseService)

//    native侧的promise存储
    private val promiseMap=HashMap<String,Promise<Any?>>()

    init {
        src.settings.javaScriptEnabled=true
        src.addJavascriptInterface(this,"zebview")
    }

    private fun encodeBase64(bytes:ByteArray):String{
        return Base64.encodeToString(bytes,Base64.NO_WRAP)
    }

    private fun decodeBase64(str:String):ByteArray{
        return Base64.decode(str,Base64.NO_WRAP)
    }

//    fun shouldInterceptRequest(
//        view: View,
//        request: WebResourceRequest
//    ): WebResourceResponse?{
//        if(request.method=="GET" &&
//            request.url.scheme=="https"&&
//            request.url.host=="zv"){
//            if(request.url.path=="/receive"){
//                val data=callbackQueue.take().toByteArray()
//                val input=ByteArrayInputStream(data)
//                return WebResourceResponse(null,null,200,"OK", hashMapOf(
//                    "Access-Control-Allow-Origin" to "*"
//                ),input)
//            }
//        }
//        return null
//    }

    //添加一个 命名对象供js调用
    fun addJsObject(name:String, obj:Any): ZebView {
        //直接把他发送会JS,解析参数时，会自动生成匿名jsObject
        baseService.onAdd(name,obj)
        return this
    }

    //添加一个 匿名对象供js调用
    private fun addJsObjectInterval(id:String,obj:Any):JsCallableObject{
        return JsCallableObject(obj).also {
            jsCallableObjectMap[id]=it
        }
    }

    /**
     * 获取版本
     */
    @JavascriptInterface
    fun getVersion():String{
        return BuildConfig.ZV_VERSION
    }

    /**
     * 构造错误
     */
    @JavascriptInterface
    fun encodeError(err:Exception):ByteArray{
        return byteArrayOf(REST.ERROR.v)+
                err.toStr().toByteArray()
    }

    /**
     * 调用基础base对象
     */
    @JavascriptInterface
    fun callBaseObject(func:String,args:String):String{
        return if(baseJsObject.hasFunc(func)){
            try{
                encodeBase64(baseJsObject.call(
                    func,
                    decodeBase64(args)
                ))
            }catch (e:Exception){
                e.printStackTrace()
                encodeBase64(encodeError(e))
            }
        }else{
            encodeBase64(encodeError(Exception("Function:${func} in BaseObject is not found")))
        }
    }

    /**
     * 调用匿名对象
     */
    @JavascriptInterface
    fun callObject(id:String,func:String,args:String):String{
        return if(jsCallableObjectMap.containsKey(id)){
            val api=jsCallableObjectMap[id]!!
            if(api.hasFunc(func)){
                try{
                    encodeBase64(api.call(
                        func,
                        decodeBase64(args)
                    ))
                }catch (e:Exception){
                    e.printStackTrace()
                    encodeBase64(encodeError(e))
                }
            }else{
                encodeBase64(encodeError(Exception("Function:${func} in Object:${id} is not found")))
            }
        }else{
            encodeBase64(encodeError(Exception("Object:${id} is not found")))
        }
    }

    /**
     * 释放匿名对象
     */
    @JavascriptInterface
    fun releaseObject(id:String){
        jsCallableObjectMap.remove(id)
    }

    /**
     * 调用js代码并且获取回调
     */
    fun appendResponse(res:Response,promiseId:String?=null){
        handler.post{
            val str=Base64.encodeToString(res.toByteArray(),Base64.NO_WRAP)
            if(promiseId==null){
                src.evaluateJavascript("window.zvReceive('${str}')",null)
            }else{
                src.evaluateJavascript("window.zvReceive('${str}','${promiseId}')",null)
            }
        }
    }

    fun savePromise(promise: Promise<Any?>){
        promiseMap[promise.getId()]=promise
    }

    /**
     * js测完成回调后将数据响应
     */
    fun finalizeFromJs(token:String,args:String){
        promiseMap[token]?.run {
            val bytes=decodeBase64(args)
            val res = decodeArg(bytes)
            if(res is Exception){
                reject(res)
            }else{
                resolve(res)
            }
            promiseMap.remove(token)
        }
    }

    /**
     * 编码一个对象返回(类型)+(body)
     */
    fun encodeArg(arg:Any?):ByteArray{
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
            //boolean
            is Boolean->{
                return REST.BOOLEAN.v.toByteArray()+if(arg) 0x01 else 0x00
            }
            //array
            is Array<*> ->{
                return REST.ARRAY.v.toByteArray()+encodeArray(arg)
            }
            //promise
            is Promise<*> ->{
                arg.then {
                    appendResponse(object :Response{
                        override fun toByteArray(): ByteArray {
                            return byteArrayOf(Response.REST.PROMISE_FINISH.v)+
                                    arg.getId().toByteArray()+
                                    //停止符号
                                    0+
                                    //resolve符号
                                    1+
                                    encodeArg(it)
                        }
                    })
                }.catch {
                    appendResponse(object :Response{
                        override fun toByteArray(): ByteArray {
                            return byteArrayOf(Response.REST.PROMISE_FINISH.v)+
                                    arg.getId().toByteArray()+
                                    //停止符号
                                    0+
                                    //reject符号
                                    0+
                                    encodeArg(it?.toStr())
                        }
                    })
                }
                return REST.PROMISE.v.toByteArray()+arg.getId().toByteArray()
            }
            //bytearray
            is ByteArray->{
                return REST.BYTEARRAY.v.toByteArray()+arg
            }
            //json
            is JSONObject->{
                return REST.JSON.v.toByteArray()+arg.toString().toByteArray()
            }
            else->{
                //判断是不是Object
                if(arg::class.java.isAnnotationPresent(JavascriptClass::class.java)){
                    //为对象生成name
                    val objectId= randomString(16)
                    val jsObject=addJsObjectInterval(
                        objectId,
                        arg
                    )
                    return REST.OBJECT.v.toByteArray()+
                            //对象名称
                            objectId.toByteArray()+
                            //结尾
                            0+
                            //方法名称
                            jsObject.funcList().joinToString(",").toByteArray()
                }else{
                    throw Exception("Not support type to encode：$arg")
                }
            }
        }
    }

    /**
     * 编码一个数组返回(body)
     */
    fun encodeArray(arg:Array<*>):ByteArray{
        val arr=ArrayList<Byte>()
        arg.forEach {
            val data=encodeArg(it)
            //length
            arr.addAll(ByteBuffer.allocate(4).putInt(data.size).array().asIterable())
            //body
            arr.addAll(data.asIterable())
        }
        return arr.toByteArray()
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
            REQT.INT.v->{
                return when(body.size){
                    8->{
                        ByteBuffer.wrap(body).long
                    }
                    4->{
                        ByteBuffer.wrap(body).int
                    }
                    else->{
                        throw Exception("Not valid integer bytes size:${body.size}")
                    }
                }
            }
            REQT.FLOAT.v->{
                return ByteBuffer.wrap(body).double
            }
            REQT.STRING.v->{
                return String(body)
            }
            REQT.NULL.v->{
                return null
            }
            REQT.BOOLEAN.v->{
                return body[0]==0x01.toByte()
            }
            REQT.JSON.v->{
                return JSONObject(String(body))
            }
            REQT.ERROR.v->{
                return Exception(String(body))
            }
            else->{
                //其他情况 非法
                throw Exception("Not support type to decode:$type")
            }
        }
    }

    /**
     * 解码数组
     * 数组为：[(长度)+(body)]
     */
    private fun decodeArray(byteArray: ByteArray):Array<Any?>{
        val buffer=ByteBuffer.wrap(byteArray)
        val out=ArrayList<Any?>()
        while(buffer.remaining()>0){
            //获取长度
            val size=buffer.int
            //获取数据
            val data=ByteArray(size)
            buffer.get(data)
            //解析数据
            out.add(decodeArg(data))
        }
        return out.toArray()
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

        fun call(name: String, bytes: ByteArray): ByteArray {
            //js调用java方法，将js对象转为java对象
            val method = funcMap[name]!!
            val argsArray=decodeArray(bytes)
            //执行函数
            val res=method.invoke(obj, *argsArray)
            //返回参数
            return encodeArg(res)
        }
    }
}