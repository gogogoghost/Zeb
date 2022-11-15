package site.zbyte.zebview.callback

import site.zbyte.zebview.ZebView
import site.zbyte.zebview.toByteArray

class CallbackObject(
    private val zv: ZebView,
    private val objectToken:String) {

    /**
     * 调用该对象内的方法
     */
    fun call(funcName:String, vararg args:Any?):Promise<Any?>{
        val promise = Promise<Any?>{}
        zv.appendResponse(object :Response{
            override fun toByteArray(): ByteArray {
                return Response.REST.OBJECT_CALLBACK.v.toByteArray()+
                        //对象token
                        objectToken.toByteArray()+
                        //0分割
                        0+
                        //方法标记名称
                        funcName.toByteArray()+
                        //0分割
                        0+
                        //回调内容
                        zv.encodeArray(args)
            }
        }, promise.getId())
        zv.savePromise(promise)
        return promise
    }


    protected fun finalize(){
        zv.appendResponse(object :Response{
            override fun toByteArray(): ByteArray {
                return Response.REST.RELEASE_OBJECT.v.toByteArray()+
                        //方法名称
                        objectToken.toByteArray()
            }
        })
    }
}