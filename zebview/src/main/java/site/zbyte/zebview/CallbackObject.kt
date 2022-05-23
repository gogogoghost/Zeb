package site.zbyte.zebview

import org.json.JSONArray

class CallbackObject(
    private val zv: ZebView,
    private val objectToken:String) {

    /**
     * 调用该对象内的方法
     */
    fun call(funcName:String, vararg args:Any){
        //将参数转换为JSONArray
        call(funcName,processArgs(zv,*args))
    }

    /**
     * 真正调用js的call
     */
    private fun call(funcName: String, args:JSONArray){
        zv.evaluateJavascript(
            "window.invokeObjectCallback(\"$objectToken\",\"${funcName}\",\"${args.toString().replace("\"","\\\"")}\")",
            null
        )
    }

    /**
     * 无需使用的回调需要使用release以便释放js内存
     */
    fun release(){
        zv.evaluateJavascript("window.releaseObject(\"$objectToken\")", null)
    }
}