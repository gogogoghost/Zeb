package site.zbyte.zebview.callback

import site.zbyte.zebview.ZebView
import site.zbyte.zebview.toByteArray
import java.io.ByteArrayOutputStream

/**
 * 描述一个js的回调方法
 */
class Callback(
    private val zv: ZebView,
    private val functionToken: String
) {

    /**
     * 调用该回调方法
     */
    fun call(vararg args:Any?):Promise<Any?>{
        val promise= Promise<Any?>{}
        zv.appendResponse(object :Response{
            override fun encode():ByteArray {
                val b=ByteArrayOutputStream()
                b.write(Response.REST.CALLBACK.v.toByteArray())
                b.write(functionToken.toByteArray())
                b.write(byteArrayOf(0x00))
                zv.encodeArray(args,b)
                return b.toByteArray()
            }
        },promise.getId())
        zv.savePromise(promise)
        return promise
    }

    protected fun finalize(){
        zv.appendResponse(object :Response{
            override fun encode(): ByteArray {
                val b=ByteArrayOutputStream()
                b.write(Response.REST.RELEASE_CALLBACK.v.toByteArray())
                b.write(functionToken.toByteArray())
                return b.toByteArray()
            }
        })
    }
}