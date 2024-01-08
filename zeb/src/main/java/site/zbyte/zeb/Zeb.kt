package site.zbyte.zeb

import android.annotation.SuppressLint
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Log
import android.webkit.*
import org.json.JSONObject
import site.zbyte.zeb.callback.Callback
import site.zbyte.zeb.callback.CallbackObject
import site.zbyte.zeb.callback.Promise
import site.zbyte.zeb.common.readBytes
import site.zbyte.zeb.common.readString
import site.zbyte.zeb.common.toByteArray
import site.zbyte.zeb.common.toStr
import site.zbyte.zeb.data.Blob
import site.zbyte.zeb.data.JsObject
import site.zbyte.zeb.ws.IFrameSender
import site.zbyte.zeb.ws.WsListener
import site.zbyte.zeb.ws.WsServer
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.random.Random

@SuppressLint("SetJavaScriptEnabled")
class Zeb(private val src:WebView):WsListener {

    companion object{
        const val TAG="Zeb"
    }

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
    private val acrossObj=HashMap<Int,JsObject>()
    //    native侧的promise存储
    private val promiseMap=HashMap<Int,Promise<Any>>()

    //_base对象
    private val baseService=BaseService()

    private val zebAuth= if(BuildConfig.DEBUG)
        "zebAuth"
    else
        Base64.encodeToString(Random.nextBytes(12),Base64.NO_WRAP.or(Base64.URL_SAFE))
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
    fun addJsObject(name:String, obj: JsObject): Zeb {
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
    fun sendFrame(frame:ByteArray){
        frameSender?.send(frame)
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
                b.write(arg.length.toByteArray())
                b.write(arg.toByteArray())
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
            is JsObject ->{
//                val id = IdGenerator.nextId()
                acrossObj[arg.id]=arg
                val fields=arg.getFields()
                val methods=arg.getMethods()
                //head 1
                b.write(DataType.OBJECT.toInt())
                //id 8
                b.write(arg.id.toByteArray())
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
                arg.toStr().let {
                    b.write(it.length.toByteArray())
                    b.write(it.toByteArray())
                }
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
        return when(val type=buffer.get()){
            DataType.FUNCTION->{
                //方法回调
                val id=buffer.getInt()
                Callback(this@Zeb,id)
            }
            DataType.OBJECT->{
                //带有回调的对象
                val id=buffer.getInt()
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
                buffer.readString(buffer.getInt())
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
                return Exception(buffer.readString(buffer.getInt()))
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
        Log.w(TAG,"Connection closed")
        frameSender=null
        //清空存储的对象
        acrossObj.clear()
        //释放promise
        promiseMap.values.forEach {
            it.reject(Exception("Connection closed"))
        }
        promiseMap.clear()
    }

    private fun sendPromiseFinalize(promiseId:Int,success:Boolean,arg:Any?){
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
    private fun callObject(promiseId:Int,id:Int,func:String,buffer:ByteBuffer){
        if(id==0){
            callObjectInternal(promiseId,baseService,func, buffer)
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

    private fun callObjectInternal(promiseId: Int, obj:JsObject, func:String, buffer:ByteBuffer){
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

    private fun readObject(promiseId:Int,id:Int,name:String){
        if(id==0){
            readObjectInternal(promiseId,baseService,name)
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

    private fun readObjectInternal(promiseId:Int, obj:JsObject, name:String){
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

    @Synchronized
    private fun releaseObject(id:Int){
        acrossObj.remove(id)
    }

    @Synchronized
    private fun finalizePromise(id:Int,buffer: ByteBuffer){
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
                val promiseId=buffer.getInt()
                val objId=buffer.getInt()
                val funcName=buffer.readString()
                handler.post{
                    callObject(promiseId,objId,funcName,buffer)
                }
            }
            MsgType.READ_OBJECT->{
                //readObject
                val promiseId=buffer.getInt()
                val objectId=buffer.getInt()
                val fieldName=buffer.readString()
                handler.post{
                    readObject(promiseId,objectId,fieldName)
                }
            }
            MsgType.RELEASE_OBJECT->{
                //releaseObject
                val id=buffer.getInt()
                releaseObject(id)
            }
            MsgType.PROMISE_FINISH->{
                //finalizePromise
                val id=buffer.getInt()
                finalizePromise(id,buffer)
            }
        }
    }

    override fun onGetBlob(path: String): Blob? {
        //<serviceName>/<blobId>
        val index=path.indexOf('/')
        if(index<0){
            return null
        }
        val id=path.substring(0 until index).toInt()
        val name=path.substring(index+1)

        val obj=acrossObj[id]?:return null
        return obj.onGetBlob(name)
    }

    override fun onPostBlob(id:Int,data: ByteArray): String {
        val obj=acrossObj[id]?:throw Exception("Object not found")
        return obj.onPostBlob(data)
    }
}