package site.zbyte.app

import android.webkit.JavascriptInterface
import org.json.JSONObject
import site.zbyte.zebview.callback.Callback
import site.zbyte.zebview.callback.CallbackObject
import site.zbyte.zebview.callback.Promise
import site.zbyte.zebview.data.SharedObject
import kotlin.concurrent.thread

class TestService {

    // js can read this field
    private var age=10

    @JavascriptInterface
    fun ageAdd():Int{
        age++
        return age
    }

    // return promise
    @JavascriptInterface
    fun getPromise():Promise<String?>{
        return Promise<String?>{
            Thread.sleep(3000)
            it.resolve("resolved!")
        }
    }

    // return a SharedObject
    private val innerObject = SharedObject(InnerCls())

    class InnerCls{
        @JavascriptInterface
        fun innerFunction():String{
            return "inner function result"
        }
    }

    @JavascriptInterface
    fun getInnerObject():SharedObject{
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
        thread {
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
            obj.call("done",res).await()
        }
        return null
    }
}