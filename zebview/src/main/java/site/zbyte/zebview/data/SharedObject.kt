package site.zbyte.zebview.data

import android.webkit.JavascriptInterface
import java.lang.reflect.Field
import java.lang.reflect.Method

class SharedObject(
    private val obj:Any,
    private val internal:Boolean=false,
    private val unsafe:Boolean=false
) {

    private val fieldMap=HashMap<String,Field>()
    private val methodMap=HashMap<String,Method>()

    init {
        if(internal){
            obj.javaClass.declaredFields.forEach {
                it.isAccessible=true
                fieldMap[it.name] = it
            }
            obj.javaClass.declaredMethods.forEach {
                if(unsafe||it.isAnnotationPresent(JavascriptInterface::class.java)){
                    it.isAccessible=true
                    methodMap[it.name]=it
                }
            }
        }else{
            obj.javaClass.fields.forEach {
                fieldMap[it.name] = it
            }
            obj.javaClass.methods.forEach {
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
        return method.invoke(obj, *args)
    }

    fun getField(name:String):Any?{
        val field = fieldMap[name]?:throw Exception("No such field: $name")
        return field.get(obj)
    }
}