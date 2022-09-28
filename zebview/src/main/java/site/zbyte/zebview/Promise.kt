package site.zbyte.zebview

import android.os.Handler
import android.os.HandlerThread

class Promise<T>:PromiseCallback<T>{

    companion object{
        //默认handler
        private val handlerThread= HandlerThread("promise").also {
            it.start()
        }
        private val promiseHandler= Handler(handlerThread.looper)
    }

    private val handler:Handler

    constructor(processor:(PromiseCallback<T>)->Unit):this(processor, promiseHandler)

    constructor(processor:(PromiseCallback<T>)->Unit,handler:Handler) {
        //初始化handler 并且执行promise
        this.handler=handler
        handler.post{
            processor.invoke(this)
        }
    }


    enum class State{
        Pending,
        Resolved,
        Rejected
    }

    //标记promise状态
    private var state:State=State.Pending

    //then回调
    private var thenFunctionList:ArrayList<((T)->Unit)> = arrayListOf()

    private var thenResult:T?=null

    //catch回调
    private var catchFunctionList:ArrayList<((Any?)->Unit)> = arrayListOf()

    private var catchResult:Any?=null

    //随机生成promise id
    private val id=randomString(16)

    //Native使用的then
    @Synchronized
    fun then(callback:(T)->Unit):Promise<T>{
        if(state==State.Pending){
            thenFunctionList.add(callback)
        }else if(state==State.Resolved){
            handler.post{
                callback.invoke(thenResult!!)
            }
        }
        return this
    }

    //Native使用的catch
    @Synchronized
    fun catch(callback:(Any?)->Unit):Promise<T>{
        if(state==State.Pending){
            catchFunctionList.add(callback)
        }else if(state==State.Rejected){
            handler.post{
                callback.invoke(catchResult!!)
            }
        }
        return this
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
    override fun resolve(obj: T) {
        if(state==State.Pending){
            state=State.Resolved
            thenResult=obj
            while(thenFunctionList.size>0){
                val item=thenFunctionList.first()
                item.invoke(obj)
                thenFunctionList.removeAt(0)
            }
        }
    }

    @Synchronized
    override fun reject(err: Any?) {
        if(state==State.Pending){
            state=State.Rejected
            catchResult=err
            while(catchFunctionList.size>0){
                val item=catchFunctionList.first()
                item.invoke(err)
                catchFunctionList.removeAt(0)
            }
        }
    }
}