package site.zbyte.zebview.callback

import site.zbyte.zebview.ZebView
import site.zbyte.zebview.toByteArray

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
                return Response.REST.CALLBACK.v.toByteArray()+
                        //方法标记名称
                        functionToken.toByteArray()+
                        //0分割内容
                        0+
                        //回调内容
                        zv.encodeArray(args)
            }
        },promise.getId())
        zv.savePromise(promise)
        return promise
    }

    protected fun finalize(){
        zv.appendResponse(object :Response{
            override fun encode(): ByteArray {
                return Response.REST.RELEASE_CALLBACK.v.toByteArray()+
                            //方法名称
                            functionToken.toByteArray()
            }
        })
    }
}