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

fun finalizePromise(zebView:ZebView,id:String,success:Boolean,result:Any?){
    val arr= processArgs(zebView,result)
    //调用js方法
    zebView.evaluateJavascript("window.finalizePromise(\"$id\",$success,\"${
        arr.toString().replace("\"", "\\\"")
    }\")", null)
}

/**
 * JAVA对象转js对象
 */
fun processArg(zv:ZebView,obj:Any?):Any?{
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
        //Promise
        is Promise<*>->{
            //调用同一个Promise也会有不同的ID
            val callHash= randomString(8)
            //完整的promiseID
            val hash="${obj.getId()}${callHash}"
            obj.then {
                finalizePromise(zv,hash,true,it)
            }.catch {
                finalizePromise(zv,hash,false,it)
            }
            "${ZebView.PROMISE_PREFIX}${hash}"
        }
        is JSONObject, is JSONArray ->{
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
fun processArgs(zv:ZebView,vararg args:Any?): JSONArray {
    val array= JSONArray()
    args.forEach {
        array.put(processArg(zv,it))
    }
    return array
}

/**
 * 随机字符串
 */
fun randomString(length:Int):String{
    val str="ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"
    return (1..length).map { str.random() }.joinToString("")
}

fun ByteArray.toInt():Int{
    var result = 0
    for (i in indices) {
        result = result or (this[i].toInt() shl 8 * i)
    }
    return result
}

fun ByteArray.toLong():Long{
    var result = 0L
    for (i in indices) {
        result = result or (this[i].toLong() shl 8 * i)
    }
    return result
}