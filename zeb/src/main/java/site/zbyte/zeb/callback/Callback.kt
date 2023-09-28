package site.zbyte.zeb.callback

import site.zbyte.zeb.Zeb
import site.zbyte.zeb.toByteArray
import java.io.ByteArrayOutputStream

/**
 * 描述一个js的回调方法
 */
class Callback(
    private val zeb: Zeb,
    private val functionToken: String
):ICallback {

    /**
     * 调用该回调方法
     */
    fun call(vararg args:Any?):Promise<Any>{
        val promise= Promise<Any>{}
        zeb.appendResponse(object :Response{
            override fun encode():ByteArray {
                val b=ByteArrayOutputStream()
                //1
                b.write(Response.REST.CALLBACK.v.toByteArray())
                //8
                b.write(functionToken.toByteArray())
                //args
                zeb.encodeArray(args,b)
                return b.toByteArray()
            }
        },promise.getId())
        zeb.savePromise(promise)
        return promise
    }

    protected fun finalize(){
        zeb.appendResponse(object :Response{
            override fun encode(): ByteArray {
                val b=ByteArrayOutputStream()
                b.write(Response.REST.RELEASE_CALLBACK.v.toByteArray())
                b.write(functionToken.toByteArray())
                return b.toByteArray()
            }
        })
    }

    override fun isObject(): Boolean {
        return false
    }

    override fun getToken(): String {
        return functionToken
    }
}