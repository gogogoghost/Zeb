package site.zbyte.zebview

import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.Keep

class Promise<T>:PromiseCallback<T>{

    constructor(processor:(PromiseCallback<T>)->Unit):this(processor, promiseHandler)

    constructor(processor:(PromiseCallback<T>)->Unit,handler:Handler) {
        handler.post{
            processor.invoke(this)
        }
    }

    companion object{
        private val handlerThread= HandlerThread("promise").also {
            it.start()
        }
        private val promiseHandler= Handler(handlerThread.looper)
    }

    enum class State{
        Pending,
        Resolved,
        Rejected
    }

    //标记promise状态
    private var state:State=State.Pending

    //then回调
    private var thenFunction:((T)->Unit)?=null

    //catch回调
    private var catchFunction:((Any?)->Unit)?=null

    //随机生成promise id
    private val id= randomString(16)

    //zebview
    private var zebView:ZebView?=null

    private var triggerFunc:(()->Unit)?=null

    //Native使用的then
    fun then(callback:(T)->Unit):Promise<T>{
        thenFunction=callback
        return this
    }

    //Native使用的catch
    fun catch(callback:(Any?)->Unit):Promise<T>{
        catchFunction=callback
        return this
    }

    //设置一个zebview
    @Synchronized
    fun setZebView(zebView: ZebView){
        this.zebView=zebView
        triggerFunc?.invoke()
    }

    //获取当前promise状态
    @Synchronized
    fun getState():State{
        return state
    }

    //获取该promiseID
    fun getId():String{
        return id
    }

    @Synchronized
    private fun trigger(success:Boolean,obj:Any?){
        val func={
            zebView!!.let {
                val arr= processArgs(it,obj)
                //调用js方法
                it.evaluateJavascript("window.finalizePromise(\"$id\",$success,\"${
                    arr.toString().replace("\"", "\\\"")
                }\")", null)
            }
        }
        if(zebView==null){
            //等于null 等待trigger
            triggerFunc=func
        }else{
            //zebview有数据，立即trigger
            func.invoke()
        }
    }

    @Synchronized
    override fun resolve(obj: T) {
        if(state==State.Pending){
            state=State.Resolved
            trigger(true,obj)
            thenFunction?.invoke(obj)
        }
    }

    @Synchronized
    override fun reject(err: Any?) {
        if(state==State.Pending){
            state=State.Rejected
            trigger(false,err)
            catchFunction?.invoke(err)
        }
    }
}