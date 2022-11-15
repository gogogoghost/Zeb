package site.zbyte.zebview.callback

import android.os.Handler
import android.os.HandlerThread
import site.zbyte.zebview.randomString
import site.zbyte.zebview.toStr

class Promise<T>: PromiseCallback<T> {

    companion object{
        //默认handler
        private val handlerThread= HandlerThread("promise").also {
            it.start()
        }
        private val promiseHandler= Handler(handlerThread.looper)
    }

    private val handler:Handler

    constructor(processor:(PromiseCallback<T>)->Unit):this(processor, promiseHandler)

    constructor(processor:(PromiseCallback<T>)->Unit, handler:Handler) {
        //初始化handler 并且执行promise
        this.handler=handler
        handler.post{
            try{
                processor.invoke(this)
            }catch (e:Exception){
                reject(e)
            }
        }
    }


    enum class State{
        Pending,
        Resolved,
        Rejected
    }

    //标记promise状态
    private var state: State = State.Pending

    //then回调
    private var thenFunctionList:ArrayList<((T)->Unit)> = arrayListOf()

    private var thenResult:T?=null

    //catch回调
    private var catchFunctionList:ArrayList<((Exception?)->Unit)> = arrayListOf()

    private var catchResult:Exception?=null

    //随机生成promise id
    private val id= randomString(16)

    //锁
    private val lock=Object()

    //Native使用的then
    fun then(callback:(T)->Unit): Promise<T> {
        synchronized(lock){
            if(state== State.Pending){
                thenFunctionList.add(callback)
            }else if(state== State.Resolved){
                handler.post{
                    callback.invoke(thenResult!!)
                }
            }
            return this
        }
    }

    //Native使用的catch
    fun catch(callback:(Throwable?)->Unit): Promise<T> {
        synchronized(lock){
            if(state== State.Pending){
                catchFunctionList.add(callback)
            }else if(state== State.Rejected){
                handler.post{
                    callback.invoke(catchResult!!)
                }
            }
            return this
        }
    }

    //获取当前promise状态
    fun getState(): State {
        synchronized(lock){
            return state
        }
    }

    //获取该promiseID
    fun getId():String{
        return id
    }

    override fun resolve(obj: T) {
        synchronized(lock){
            if(state== State.Pending){
                state= State.Resolved
                thenResult=obj
                lock.notifyAll()
                while(thenFunctionList.size>0){
                    val item=thenFunctionList.first()
                    item.invoke(obj)
                    thenFunctionList.removeAt(0)
                }
            }
        }
    }

    override fun reject(err: Exception?) {
        synchronized(lock){
            if(state== State.Pending){
                state= State.Rejected
                catchResult=err
                lock.notifyAll()
                while(catchFunctionList.size>0){
                    val item=catchFunctionList.first()
                    item.invoke(err)
                    catchFunctionList.removeAt(0)
                }
            }
        }
    }

    fun await():T{
        synchronized(lock){
            if(state== State.Pending){
                lock.wait()
            }
            if(state== State.Resolved){
                return thenResult!!
            }else{
                throw catchResult?:Exception("")
            }
        }
    }
}