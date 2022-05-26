package site.zbyte.zebview

import androidx.annotation.Keep

class Promise<T>(private val processor:(PromiseCallback<T>)->Unit):PromiseCallback<T>{

    enum class State{
        Pending,
        Resolved,
        Rejected
    }

    @Keep
    private lateinit var zv:ZebView

    //标记promise状态
    private var state:State=State.Pending

    //随机生成promise id
    val id= randomString(16)

    //promise启动时该方法会被反射调用
    @Keep
    private fun run(){
        zv.getPromiseHandler().post{
            processor.invoke(this)
        }
    }

    fun getState():State{
        return state
    }

    private fun trigger(success:Boolean,obj:Any?){
        //更新状态
        state=if(success)
            State.Resolved
        else
            State.Rejected

        val arr= processArgs(zv,obj)
        zv.evaluateJavascript("window.finalizePromise(\"$id\",$success,\"${
            arr.toString().replace("\"", "\\\"")
        }\")", null)
    }

    override fun resolve(obj: T) {
        if(state==State.Pending)
            trigger(true,obj)
    }

    override fun reject(err: Any?) {
        if(state==State.Pending)
            trigger(false,err)
    }
}