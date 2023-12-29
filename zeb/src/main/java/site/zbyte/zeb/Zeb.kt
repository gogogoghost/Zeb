package site.zbyte.zeb

import android.annotation.SuppressLint
import android.os.Handler
import android.os.HandlerThread
import android.webkit.*
import org.json.JSONObject
import site.zbyte.zeb.callback.Callback
import site.zbyte.zeb.callback.CallbackObject
import site.zbyte.zeb.callback.Promise
import site.zbyte.zeb.callback.Response
import site.zbyte.zeb.common.IdGenerator
import site.zbyte.zeb.common.toByteArray
import site.zbyte.zeb.common.toStr
import site.zbyte.zeb.data.SharedObject
import site.zbyte.zeb.ws.IFrameSender
import site.zbyte.zeb.ws.WsListener
import site.zbyte.zeb.ws.WsServer
import java.io.ByteArrayOutputStream
import java.lang.StringBuilder
import java.nio.ByteBuffer
import kotlin.experimental.and

@SuppressLint("SetJavaScriptEnabled")
class Zeb(private val src:WebView):WsListener {

    object MsgType{
        const val CALLBACK:Byte=1
        const val OBJECT_CALLBACK:Byte=2
        const val RELEASE_CALLBACK:Byte=3
        const val RELEASE_OBJECT:Byte=4
        const val PROMISE_FINISH:Byte=5
        const val CALL_OBJECT:Byte=6
        const val READ_OBJECT:Byte=7
    }

    /**
     * JS->Native 标志
     */
    object DataType{
        const val NULL:Byte=1
        //基本类型
        const val STRING:Byte=2
        const val LONG:Byte=3
        const val INT:Byte=4
        const val FLOAT:Byte=5
        const val BOOLEAN:Byte=6
        //特殊类型
        const val FUNCTION:Byte=10
        const val OBJECT:Byte=11
        const val BYTEARRAY:Byte=12
        const val ARRAY:Byte=13
//        const val PROMISE:Byte=14
        const val ERROR:Byte=15
        const val JSON:Byte=16
    }

//    普通的js可调用对象
    private val acrossObj=HashMap<Long,SharedObject>()

    //_base对象
    private val baseService=BaseService()
    private val baseJsObject=SharedObject(baseService)

//    native侧的promise存储
    private val promiseMap=HashMap<Long,Promise<Any>>()

    private val zebAuth= if(BuildConfig.DEBUG)
        "zebAuth"
    else
        IdGenerator.nextId().toByteArray().toStr()
    private val wsServer=WsServer(zebAuth,this)
    private val zebPort=wsServer.start()

    private val handlerThread= HandlerThread("ws").also {
        it.start()
    }
    private val handler= Handler(handlerThread.looper)

    init {
        src.settings.javaScriptEnabled=true
        src.addJavascriptInterface(this,"zeb")
    }

    //添加一个 命名对象供js调用
    fun addJsObject(name:String, obj: SharedObject): Zeb {
        //发送给js
        baseService.onAdd(name,obj)
        return this
    }

    @JavascriptInterface
    fun getAuth():String{
        return zebAuth
    }

    @JavascriptInterface
    fun getPort():String{
        return "$zebPort"
    }

    /**
     * 获取版本
     */
    @JavascriptInterface
    fun getVersion():String{
        return BuildConfig.version
    }

    /**
     * 调用js代码并且获取回调
     */
    fun appendResponse(res:Response){
        frameSender?.send(
            res.encode()
        )
    }

    fun savePromise(promise: Promise<Any>){
        promiseMap[promise.getId()]=promise
    }

    /**
     * 编码一个对象返回(类型)+(body)
     */
    private fun encodeArg(arg:Any?, b:ByteArrayOutputStream=ByteArrayOutputStream()):ByteArrayOutputStream{
        when(arg){
            null->{
                b.write(DataType.NULL.toInt())
            }
            //number
            is Int->{
                b.write(DataType.INT.toInt())
                b.write(arg.toByteArray())
            }
            is Long->{
                b.write(DataType.LONG.toInt())
                b.write(arg.toByteArray())
            }
            is Float->{
                b.write(DataType.FLOAT.toInt())
                b.write(arg.toDouble().toByteArray())
            }
            is Double->{
                b.write(DataType.FLOAT.toInt())
                b.write(arg.toByteArray())
            }
            //string
            is String->{
                b.write(DataType.STRING.toInt())
                b.write(arg.toByteArray())
                b.write(0)
            }
            //boolean
            is Boolean->{
                b.write(DataType.BOOLEAN.toInt())
                b.write(if(arg) 0x01 else 0x00)
            }
            //array
            is Array<*> ->{
                b.write(DataType.ARRAY.toInt())
                encodeArray(arg,b)
            }
            //promise
//            is Promise<*> ->{
//                arg.then {
//                    appendResponse(object :Response{
//                        override fun encode(): ByteArray {
//                            val b2=ByteArrayOutputStream()
//                            //1
//                            b2.write(MsgType.PROMISE_FINISH.toByteArray())
//                            //8
//                            b2.write(arg.getId().toByteArray())
//                            //success
//                            b2.write(byteArrayOf(0x01))
//                            encodeArg(it,b2)
//                            return b2.toByteArray()
//                        }
//                    })
//                }.catch {
//                    appendResponse(object :Response{
//                        override fun encode(): ByteArray {
//                            val b2=ByteArrayOutputStream()
//                            //1
//                            b2.write(MsgType.PROMISE_FINISH.toByteArray())
//                            //8
//                            b2.write(arg.getId().toByteArray())
//                            //fail
//                            b2.write(byteArrayOf(0x00))
//                            encodeArg(it,b2)
//                            return b2.toByteArray()
//                        }
//                    })
//                }
//                b.write(DataType.PROMISE.toInt())
//                b.write(arg.getId().toByteArray())
//                //添加suspend
////                arg.getSuspendCallback().forEach {
////                    b.write(if(it.isObject()){
////                        0x10 or 0x01
////                    }else{
////                        0x10 or 0x02
////                    })
////                    b.write(it.getToken().toByteArray())
////                }
//            }
            //bytearray
            is ByteArray->{
                b.write(DataType.BYTEARRAY.toInt())
                b.write(arg.size.toByteArray())
                b.write(arg)
            }
            //json
            is JSONObject->{
                b.write(DataType.JSON.toInt())
                b.write(arg.toString().toByteArray())
                b.write(0)
            }
            //send object
            is SharedObject ->{
                val id = IdGenerator.nextId()
                acrossObj[id]=arg
                val fields=arg.getFields()
                val methods=arg.getMethods()
                //head 1
                b.write(DataType.OBJECT.toInt())
                //id 8
                b.write(id.toByteArray())
                //field
                b.write(
                    fields.joinToString(",").toByteArray()
                )
                b.write(0)
                //method
                b.write(
                    methods.joinToString(",").toByteArray()
                )
                b.write(0)
            }
            //exception
            is Exception->{
                b.write(DataType.ERROR.toInt())
                b.write(arg.toStr().toByteArray())
                b.write(0)
            }
            else->{
                throw Exception("Not support type to encode：$arg. If you need transfer a object, please use SharedObject")
            }
        }
        return b
    }

    /**
     * 编码一个数组返回(body)
     */
    fun encodeArray(arg:Array<*>,b:ByteArrayOutputStream= ByteArrayOutputStream()):ByteArrayOutputStream{
        b.write(arg.size.toByteArray())
        arg.forEach {
            encodeArg(it,b)
        }
        return b
    }

    /**
     * 解码对象
     * 输入：(类型)+(原始数据)
     * 输出：对象
     */
    private fun decodeArg(buffer: ByteBuffer):Any?{
        val type=buffer.get()
        return when(type){
            DataType.FUNCTION->{
                //方法回调
                val id=buffer.long
                Callback(this@Zeb,id)
            }
            DataType.OBJECT->{
                //带有回调的对象
                val id=buffer.long
                CallbackObject(this@Zeb,id)
            }
            DataType.BYTEARRAY->{
                //字节数组
                val len=buffer.int
                buffer.readBytes(len)
            }
            DataType.ARRAY->{
                //数组
                decodeArray(buffer)
            }
            DataType.INT->{
                buffer.int
            }
            DataType.LONG->{
                buffer.long
            }
            DataType.FLOAT->{
                buffer.double
            }
            DataType.STRING->{
                buffer.readString()
            }
            DataType.NULL->{
                null
            }
            DataType.BOOLEAN->{
                buffer.get()==0x01.toByte()
            }
            DataType.JSON->{
                return JSONObject(buffer.readString())
            }
            DataType.ERROR->{
                return Exception(buffer.readString())
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
    private fun decodeArray(buffer: ByteBuffer):Array<Any?>{
        val len=buffer.int
        val out=ArrayList<Any?>()
        for(i in 0 until len){
            out.add(decodeArg(buffer))
        }
        return out.toArray()
    }


    private var frameSender:IFrameSender?=null

    override fun onConnect(sender: IFrameSender) {
        frameSender=sender
    }

    override fun onDisconnect() {
        frameSender=null
    }

    private fun sendPromiseFinalize(promiseId:Long,success:Boolean,arg:Any?){
        val b2=ByteArrayOutputStream()
        //1
        b2.write(MsgType.PROMISE_FINISH.toByteArray())
        //8
        b2.write(promiseId.toByteArray())
        //success
        b2.write(if(success)1 else 0)
        encodeArg(arg,b2)
        frameSender?.send(b2.toByteArray())
    }

    /**
     * 调用对象，id为空则为调用baseObject
     */
    private fun callObject(promiseId:Long,id:Long,func:String,buffer:ByteBuffer){
        if(id==0L){
            callObjectInternal(promiseId,baseJsObject,func, buffer)
        }else if(acrossObj.containsKey(id)){
            callObjectInternal(promiseId,acrossObj[id]!!,func, buffer)
        }else{
            sendPromiseFinalize(
                promiseId,
                false,
                Exception("Object:${id} is not found")
            )
        }
    }

    private fun callObjectInternal(promiseId: Long,obj:SharedObject, func:String, buffer:ByteBuffer){
        try{
            sendPromiseFinalize(
                promiseId,
                true,
                obj.callMethod(
                    func,
                    decodeArray(buffer)
                ))
        }catch (e:Exception){
            e.printStackTrace()
            sendPromiseFinalize(promiseId,false,e)
        }
    }

    private fun readObject(promiseId:Long,id:Long,name:String){
        if(id==0L){
            readObjectInternal(promiseId,baseJsObject,name)
        }else if(acrossObj.containsKey(id)){
            readObjectInternal(promiseId,acrossObj[id]!!,name)
        }else{
            sendPromiseFinalize(
                promiseId,
                false,
                Exception("Object:${id} is not found")
            )
        }
    }

    private fun readObjectInternal(promiseId:Long,obj:SharedObject, name:String){
        try{
            sendPromiseFinalize(
                promiseId,
                true,
                obj.getField(name)
            )
        }catch (e:Exception){
            e.printStackTrace()
            sendPromiseFinalize(promiseId,false,e)
        }
    }

    private fun releaseObject(id:Long){
        acrossObj.remove(id)
    }

    private fun finalizePromise(id:Long,buffer: ByteBuffer){
        promiseMap[id]?.run {
            val res = decodeArg(buffer)
            if(res is Exception){
                reject(res)
            }else{
                resolve(res)
            }
            promiseMap.remove(id)
        }
    }

    override fun onMessage(data: ByteArray) {
        val buffer=ByteBuffer.wrap(data)
        when(buffer.get()){
            MsgType.CALL_OBJECT->{
                //callObject
                val promiseId=buffer.long
                val objId=buffer.long
                val funcName=buffer.readString()
                handler.post{
                    callObject(promiseId,objId,funcName,buffer)
                }
            }
            MsgType.READ_OBJECT->{
                //readObject
                val promiseId=buffer.long
                val objectId=buffer.long
                val fieldName=buffer.readString()
                handler.post{
                    readObject(promiseId,objectId,fieldName)
                }
            }
            MsgType.RELEASE_OBJECT->{
                //releaseObject
                val id=buffer.long
                releaseObject(id)
            }
            MsgType.PROMISE_FINISH->{
                //finalizePromise
                val id=buffer.long
                finalizePromise(id,buffer)
            }
        }
    }

    private fun ByteBuffer.readBytes(len:Int):ByteArray{
        val buf=ByteArray(len)
        this.get(buf)
        return buf
    }

    private fun ByteBuffer.readString(len:Int):String{
        return String(this.readBytes(len))
    }

    private fun ByteBuffer.readString():String{
        var count=1
        while(this.get(this.position()+count)!=0x00.toByte()){
            count++
        }
        val res=if(count==1) "" else{
            this.readString(count)
        }
        this.position(this.position()+1)
        return res
    }
}