package site.zbyte.zebview

import androidx.annotation.Keep

class Promise<T>(private val processor:(PromiseCallback<T>)->Unit):PromiseCallback<T>{

    @Keep
    private lateinit var zv:ZebView

    val id= randomString(16)

    @Keep
    private fun run(){
        zv.getPromiseHandler().post{
            processor.invoke(this)
        }
    }

    private fun trigger(success:Boolean,obj:Any?){
        val arr= processArgs(zv,obj)
        zv.evaluateJavascript("window.finalizePromise(\"$id\",$success,\"${
            arr.toString().replace("\"", "\\\"")
        }\")", null)
    }

    override fun resolve(obj: T) {
        trigger(true,obj)
    }

    override fun reject(err: Any?) {
        trigger(false,err)
    }
}