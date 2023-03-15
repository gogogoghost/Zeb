package site.zbyte.zeb.callback

import site.zbyte.zeb.Zeb
import site.zbyte.zeb.toByteArray
import java.io.ByteArrayOutputStream

class CallbackObject(
    private val zeb: Zeb,
    private val objectToken:String) {

    /**
     * 调用该对象内的方法
     */
    fun call(funcName:String, vararg args:Any?):Promise<Any?>{
        val promise = Promise<Any?>{}
        zeb.appendResponse(object :Response{
            override fun encode(): ByteArray {
                val b=ByteArrayOutputStream()
                //1
                b.write(Response.REST.OBJECT_CALLBACK.v.toByteArray())
                //8
                b.write(objectToken.toByteArray())
                //name
                b.write(funcName.toByteArray())
                b.write(byteArrayOf(0x00))
                //args
                zeb.encodeArray(args,b)
                return b.toByteArray()
            }
        }, promise.getId())
        zeb.savePromise(promise)
        return promise
    }


    protected fun finalize(){
        zeb.appendResponse(object :Response{
            override fun encode(): ByteArray {
                val b=ByteArrayOutputStream()
                b.write(Response.REST.RELEASE_OBJECT.v.toByteArray())
                b.write(objectToken.toByteArray())
                return b.toByteArray()
            }
        })
    }
}