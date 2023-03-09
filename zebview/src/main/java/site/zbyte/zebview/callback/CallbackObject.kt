package site.zbyte.zebview.callback

import site.zbyte.zebview.ZebView
import site.zbyte.zebview.toByteArray
import java.io.ByteArrayOutputStream

class CallbackObject(
    private val zv: ZebView,
    private val objectToken:String) {

    /**
     * 调用该对象内的方法
     */
    fun call(funcName:String, vararg args:Any?):Promise<Any?>{
        val promise = Promise<Any?>{}
        zv.appendResponse(object :Response{
            override fun encode(): ByteArray {
                val b=ByteArrayOutputStream()
                b.write(Response.REST.OBJECT_CALLBACK.v.toByteArray())
                b.write(objectToken.toByteArray())
                b.write(byteArrayOf(0x00))
                b.write(funcName.toByteArray())
                b.write(byteArrayOf(0x00))
                zv.encodeArray(args,b)
                return b.toByteArray()
            }
        }, promise.getId())
        zv.savePromise(promise)
        return promise
    }


    protected fun finalize(){
        zv.appendResponse(object :Response{
            override fun encode(): ByteArray {
                val b=ByteArrayOutputStream()
                b.write(Response.REST.RELEASE_OBJECT.v.toByteArray())
                b.write(objectToken.toByteArray())
                return b.toByteArray()
            }
        })
    }
}