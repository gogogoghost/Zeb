package site.zbyte.zebview

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

fun Any.toJson():JSONObject{
    val obj=JSONObject()
    this.javaClass.declaredFields.forEach {
        //过滤AIDL的CREATOR
        if(it.name=="CREATOR")
            return@forEach
        it.isAccessible=true
        obj.put(it.name,it.get(this))
    }
    return obj
}

/**
 * JAVA对象转js对象
 */
fun processArg(obj:Any?):Any?{
    return when(obj){
        null->{
            null
        }
        is Array<*> ->{
            //转成JSONArray
            JSONArray().also { arr->
                obj.forEach {
                    arr.put(it)
                }
            }
        }
        is ByteArray->{
            "${ZebView.BYTEARRAY_PREFIX}${Base64.encodeToString(obj, Base64.NO_WRAP)}"
        }
        //基本类型原样返回
        is Byte,is Short,is Int,is Long,is Float,is Double,is Boolean,is Char,is String->{
            obj
        }
        //其他数据 全部json序列化返回
        else -> {
            obj.toJson()
        }
    }
}

/**
 * JAVA对象数组转js对象数组
 */
fun processArgs(vararg args:Any): JSONArray {
    val array= JSONArray()
    args.forEach {
        array.put(processArg(it))
    }
    return array
}