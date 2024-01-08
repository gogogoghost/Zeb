package site.zbyte.zeb.callback

import android.os.Handler
import android.os.HandlerThread
import site.zbyte.zeb.common.IdGenerator

class Promise<T>: PromiseCallback<T?> {

    companion object{
        private val idGenerator=IdGenerator()
    }

    enum class State{
        Pending,
        Resolved,
        Rejected
    }

    //标记promise状态
    private var state: State = State.Pending

    //then回调
    private var thenFunctionList:ArrayList<((T?)->Unit)> = arrayListOf()

    private var thenResult:T?=null

    //catch回调
    private var catchFunctionList:ArrayList<((Exception?)->Unit)> = arrayListOf()

    private var catchResult:Exception?=null

    //随机生成promise id
    private val id = idGenerator.nextId()

    //锁
    private val lock = Object()

    //Native使用的then
//    fun then(callback:(T?)->Unit): Promise<T> {
//        synchronized(lock){
//            if(state== State.Pending){
//                thenFunctionList.add(callback)
//            }else if(state== State.Resolved){
//                handler.post{
//                    callback.invoke(thenResult)
//                }
//            }
//            return this
//        }
//    }

    //Native使用的catch
//    fun catch(callback:(Exception?)->Unit): Promise<T> {
//        synchronized(lock){
//            if(state== State.Pending){
//                catchFunctionList.add(callback)
//            }else if(state== State.Rejected){
//                handler.post{
//                    callback.invoke(catchResult!!)
//                }
//            }
//            return this
//        }
//    }

    //获取当前promise状态
    fun getState(): State {
        synchronized(lock){
            return state
        }
    }

    //获取该promiseID
    fun getId():Int{
        return id
    }

    override fun resolve(obj: T?) {
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

    /**
     * 在当前线程等待结果返回，请注意不要在js回调线程中等待，会阻塞线程
     * js层抛出的异常在await时会在native层抛出
     */
    fun await():T?{
        synchronized(lock){
            if(state== State.Pending){
                lock.wait()
            }
            if(state== State.Resolved){
                return thenResult
            }else{
                throw catchResult?:Exception("")
            }
        }
    }
}