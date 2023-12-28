package site.zbyte.zeb.callback

import site.zbyte.zeb.Zeb
import site.zbyte.zeb.common.toByteArray
import java.io.ByteArrayOutputStream

class CallbackObject(
    private val zeb: Zeb,
    private val id:Long):ICallback {

    /**
     * 调用该对象内的方法
     */
    fun call(funcName:String, vararg args:Any?):Promise<Any>{
        val promise = Promise<Any>{}
        zeb.appendResponse(object :Response{
            override fun encode(): ByteArray {
                val b=ByteArrayOutputStream()
                //1
                b.write(Zeb.MsgType.OBJECT_CALLBACK.toInt())
                //8
                b.write(id.toByteArray())
                //name
                b.write(funcName.toByteArray())
                b.write(0)
                //promise
                b.write(promise.getId().toByteArray())
                //args
                zeb.encodeArray(args,b)
                return b.toByteArray()
            }
        })
        zeb.savePromise(promise)
        return promise
    }


    protected fun finalize(){
        zeb.appendResponse(object :Response{
            override fun encode(): ByteArray {
                val b=ByteArrayOutputStream()
                b.write(Zeb.MsgType.RELEASE_OBJECT.toInt())
                b.write(id.toByteArray())
                return b.toByteArray()
            }
        })
    }

    override fun isObject(): Boolean {
        return true
    }

    override fun getId(): Long {
        return id
    }
}