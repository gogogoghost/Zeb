package site.zbyte.zebview.callback

import org.json.JSONArray
import site.zbyte.zebview.ZebView
import site.zbyte.zebview.processArgs

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
    fun call(vararg args:Any){
        call(processArgs(zv,*args))
    }

    /**
     * 内部调用web view触发回调
     */
    private fun call(array:JSONArray){
        zv.evaluateJavascript(
            "window.invokeCallback(\"$functionToken\",\"${array.toString().replace("\"","\\\"")}\")",
            null
        )
    }

    /**
     * 无需使用后 调用release使js回收内存
     */
    fun release(){
        zv.evaluateJavascript("window.releaseCallback(\"$functionToken\")", null)
    }
}