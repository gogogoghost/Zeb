package site.zbyte.zebview

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.*
import org.json.JSONObject
import site.zbyte.zebview.callback.Callback
import site.zbyte.zebview.callback.CallbackObject
import site.zbyte.zebview.callback.Promise
import site.zbyte.zebview.callback.Response
import site.zbyte.zebview.data.SharedObject
import java.nio.ByteBuffer

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
    private val acrossObj=HashMap<String,SharedObject>()

    //_base对象
    private val baseService=BaseService(this)
    private val baseJsObject=SharedObject(baseService)

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

    //添加一个 命名对象供js调用
    fun addJsObject(name:String, obj: SharedObject): ZebView {
        //发送给js
        baseService.onAdd(name,obj)
        return this
    }

    /**
     * 获取版本
     */
    @JavascriptInterface
    fun getVersion():String{
        return BuildConfig.version
    }

    /**
     * 调用对象，id为null则为调用baseObject
     */
    @JavascriptInterface
    fun callObject(id:String?,func:String,args:String):String{
        return if(id==null){
            callObjectInternal(baseJsObject,func, args)
        }else if(acrossObj.containsKey(id)){
            callObjectInternal(acrossObj[id]!!,func, args)
        }else{
            eArg(Exception("Object:${id} is not found"))
        }
    }

    private fun callObjectInternal(obj:SharedObject, func:String, args:String):String{
        return try{
            eArg(obj.callMethod(
                func,
                dArr(args)
            ))
        }catch (e:Exception){
            e.printStackTrace()
            eArg(e)
        }
    }

    @JavascriptInterface
    fun readObject(id:String?,name:String):String{
        return if(id==null){
            readObjectInternal(baseJsObject,name)
        }else if(acrossObj.containsKey(id)){
            readObjectInternal(acrossObj[id]!!,name)
        }else{
            eArg(Exception("Object:${id} is not found"))
        }
    }

    private fun readObjectInternal(obj:SharedObject, name:String):String{
        return try{
            eArg(obj.getField(name))
        }catch (e:Exception){
            e.printStackTrace()
            eArg(e)
        }
    }

    /**
     * 释放匿名对象
     */
    @JavascriptInterface
    fun releaseObject(id:String){
        acrossObj.remove(id)
    }

    /**
     * 调用js代码并且获取回调
     */
    fun appendResponse(res:Response,promiseId:String?=null){
        handler.post{
            val str=encodeBase64(res.encode())
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
    @JavascriptInterface
    fun finalizePromise(token:String,args:String){
        promiseMap[token]?.run {
            val res = dArg(args)
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
                        override fun encode(): ByteArray {
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
                        override fun encode(): ByteArray {
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
            //send object
            is SharedObject ->{
                val id = randomString(8)
                acrossObj[id]=arg
                val fields=arg.getFields()
                val methods=arg.getMethods()
                val buf=ArrayList<Byte>()
                //head 1
                buf.add(REST.OBJECT.v)
                //id 16
                buf.addAll(id.toByteArray().asIterable())
                //field
                buf.addAll(
                    fields.joinToString(",").toByteArray().asIterable()
                )
                //split
                buf.add(0x00)
                //method
                buf.addAll(
                    methods.joinToString(",").toByteArray().asIterable()
                )
                return buf.toByteArray()
            }
            //exception
            is Exception->{
                return byteArrayOf(REST.ERROR.v)+
                        arg.toStr().toByteArray()
            }
            else->{
                throw Exception("Not support type to encode：$arg. If you need transfer a object, please use AcrossObject")
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
     * encode and decode helper
     */
    //===========
    private fun eArg(arg:Any?):String{
        return encodeBase64(encodeArg(arg))
    }
    private fun eArr(arr:Array<*>):String{
        return encodeBase64(encodeArray(arr))
    }
    private fun dArg(str:String):Any?{
        return decodeArg(decodeBase64(str))
    }
    private fun dArr(str:String):Array<Any?>{
        return decodeArray(decodeBase64(str))
    }
}