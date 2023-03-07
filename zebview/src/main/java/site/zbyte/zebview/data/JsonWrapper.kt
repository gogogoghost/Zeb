package site.zbyte.zebview.data

import org.json.JSONObject

class JsonWrapper(private val obj:Any): JSONObject() {
    init {
        obj.javaClass.declaredFields.forEach {
            it.isAccessible=true
            this.put(it.name,it.get(obj))
        }
    }
}