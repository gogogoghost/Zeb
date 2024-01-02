package site.zbyte.zeb.callback

import site.zbyte.zeb.Zeb
import site.zbyte.zeb.common.toByteArray
import java.io.ByteArrayOutputStream

/**
 * 描述一个js的回调方法
 */
class Callback(
    private val zeb: Zeb,
    private val id: Long
):ICallback {

    /**
     * 调用该回调方法
     */
    fun call(vararg args:Any?):Promise<Any>{
        val promise= Promise<Any>()

        zeb.savePromise(promise)
        val b=ByteArrayOutputStream()
        //1
        b.write(Zeb.MsgType.CALLBACK.toInt())
        //8 function
        b.write(id.toByteArray())
        //8 promise id
        b.write(promise.getId().toByteArray())
        //args
        zeb.encodeArray(args,b)
        zeb.sendFrame(b.toByteArray())

        return promise
    }

    protected fun finalize(){
        val b=ByteArrayOutputStream()
        b.write(Zeb.MsgType.RELEASE_CALLBACK.toInt())
        b.write(id.toByteArray())
        zeb.sendFrame(b.toByteArray())
    }

    override fun getId():Long {
        return id
    }
}