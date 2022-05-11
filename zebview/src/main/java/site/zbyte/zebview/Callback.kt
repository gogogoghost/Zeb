package site.zbyte.zebview

import org.json.JSONArray

abstract class Callback {

    fun call(vararg args:Any){
        call(processArgs(*args))
    }

    abstract fun call(array:JSONArray)

    abstract fun release()
}