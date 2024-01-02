package site.zbyte.zeb.data

import android.webkit.JavascriptInterface
import site.zbyte.zeb.common.IdGenerator
import java.lang.reflect.Field
import java.lang.reflect.Method

open class JsObject(
    //include private member
    private val internal:Boolean=false,
    //include no JavascriptInterface member
    private val unsafe:Boolean=false
) {

    private val fieldMap=HashMap<String,Field>()
    private val methodMap=HashMap<String,Method>()

    val id= IdGenerator.nextId()

    init {
        if(internal){
            this.javaClass.declaredFields.forEach {
                it.isAccessible=true
                fieldMap[it.name] = it
            }
            this.javaClass.declaredMethods.forEach {
                if(unsafe||it.isAnnotationPresent(JavascriptInterface::class.java)){
                    it.isAccessible=true
                    methodMap[it.name]=it
                }
            }
        }else{
            this.javaClass.fields.forEach {
                fieldMap[it.name] = it
            }
            this.javaClass.methods.forEach {
                if(unsafe||it.isAnnotationPresent(JavascriptInterface::class.java)){
                    methodMap[it.name]=it
                }
            }
        }
    }

    fun getFields():Array<String>{
        return fieldMap.keys.toTypedArray()
    }

    fun getMethods():Array<String>{
        return methodMap.keys.toTypedArray()
    }

    fun callMethod(name: String, args:Array<Any?>): Any? {
        //js调用java方法，将js对象转为java对象
        val method = methodMap[name]?:throw Exception("No such method: $name")
        //执行函数
        return method.invoke(this, *args)
    }

    fun getField(name:String):Any?{
        val field = fieldMap[name]?:throw Exception("No such field: $name")
        return field.get(this)
    }

    open fun onGetBlob(path:String):Blob?{
        return null
    }
    open fun onPostBlob(data:ByteArray):String{
        throw Exception("Not implement")
    }
}