package site.zbyte.app

import android.webkit.JavascriptInterface
import org.json.JSONObject
import site.zbyte.zeb.callback.Callback
import site.zbyte.zeb.callback.CallbackObject
import site.zbyte.zeb.data.Blob
import site.zbyte.zeb.data.JsObject

class TestService:JsObject(true,true) {

    // js can read this field
    private var age=10

    @JavascriptInterface
    fun ageAdd():Int{
        age++
        return age
    }

    // return a SharedObject
    private val innerObject = object :JsObject(){
        @JavascriptInterface
        fun innerFunction():String{
            return "inner function result"
        }
    }

    @JavascriptInterface
    fun getInnerObject():JsObject{
        return innerObject
    }

    // support type
    @JavascriptInterface
    fun testType(
        aInt:Int,
        aLong:Long,
        aFloat:Double,
        aStr:String,
        aBool:Boolean,
        bytes:ByteArray,
        nil:Any?,
        json:JSONObject,
        aArr:Array<Any?>,
        cb: Callback,
        obj: CallbackObject,
    ):Any?{
        val res=cb.call(
            aInt,
            aLong,
            aFloat,
            aStr,
            aBool,
            bytes,
            nil,
            json,
            aArr
        ).await()
        return obj.call("done",res).await()
    }

//    private var runningThread:Thread?=null
    private var thread:Thread?=null

    private var cb:CallbackObject?=null
    @JavascriptInterface
    fun startThread(interval:Long,timeout:Long,callback:CallbackObject){
        synchronized(this){
            if (thread?.isAlive==true){
                throw Exception("cannot start twice")
            }
            cb=callback
            thread=Thread{
                val st=System.currentTimeMillis()
                while(true){
                    synchronized(this@TestService){
                        if(thread==null){
                            return@Thread
                        }
                        callback.call("exec")
                    }
                    Thread.sleep(interval)
                    if(System.currentTimeMillis()-st>=timeout){
                        callback.call("timeout")
                        break
                    }
                }
            }.also {
                it.start()
            }
        }
    }

    @JavascriptInterface
    fun stopThread(){
        synchronized(this){
            thread?.also {
                thread=null
            }
        }?.join()
    }

    private var tempBlob:Blob?=null

    override fun onGetBlob(path: String): Blob? {
        return if(path=="tempBlob"){
            tempBlob
        }else{
            null
        }
    }

    override fun onPostBlob(data: ByteArray): String {
        tempBlob=Blob(data,"text/plain")
        return "tempBlob"
    }
}