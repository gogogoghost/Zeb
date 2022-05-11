package site.zbyte.zebview

import org.json.JSONArray

abstract class CallbackObject {

    fun call(funcName:String, vararg args:Any){
        call(funcName,processArgs(*args))
    }

    abstract fun call(funcName: String,args:JSONArray)

    abstract fun release()
}