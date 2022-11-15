## ZebView

[![](https://jitpack.io/v/site.zbyte/zebview.svg)](https://jitpack.io/#site.zbyte/zebview)

Bridge between javascript and java/kotlin on Android WebView

### Get Start

There are two parts for getting start

#### Android Part

Add jitpack repository

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}
```

Add dependency

```groovy
dependencies {
    implementation "site.zbyte:zebview:${version}"
}
```

Create ZebView in layout xml file or by java code(kotlin)

```kotlin
//Make a service
@JavascriptClass
object TestServiceObject{

    /**
     * in js:
     * api.TestService.test(100,"Hello World",false)
     */
    @JavascriptInterface
    fun test(intVal:Int,strVal:String,boolVal:Boolean){
        //This function invoke by js and can return something
        //See [Support Params Type] behind
    }

    /**
     * in js:
     * api.TestService.manyWork().then((res)=>{
     *      //print "result"
     *      console.log(res)
     * })
     */
    @JavascriptInterface
    fun manyWork():Promise<String>{
        return Promise<String>{
            //do many work
            it.resolve("result")
        }
    }

    /**
     * in js:
     * api.TestService.notifyJs((name)=>{
     *      return "my name is "+name
     * })
     */
    @JavascriptInterface
    fun notifyJs(callback:Callback){
        callback.call("Jack").then{it->
            //print "my name is Jack"
            println(it)
        }
    }
}

//Create ZebView
val webView=new WebView(context)
val zv=ZebView(webView)

//Add Service
zv.addJsObject("TestService",TestServiceObject)
    .addJsObject("SecondService",SecondServiceObject)

//Load your page
zv.loadUrl("http://192.168.0.137:3000")
```

#### Frontend Part

See [zebview-bridge](https://github.com/gogogoghost/Zebview/tree/master/frontend)

### Support Params Type

Javascript -> Java (function params)

- NULL -> NULL
- String -> String
- Number(integer) -> Integer/Long(>0xffffffff)
- Number(double) -> Double
- Boolean -> Boolean
- Callback function -> [Callback](https://github.com/gogogoghost/ZebView/blob/master/zebview/src/main/java/site/zbyte/zebview/callback/Callback.kt)
- Object with function -> [CallbackObject](https://github.com/gogogoghost/ZebView/blob/master/zebview/src/main/java/site/zbyte/zebview/callback/CallbackObject.kt)
- Uint8Array -> byte[]
- Array -> Array<Any>
- Object without function -> org.json.JSONObject

---

Java -> Javascript (callback params or function result)

- NULL -> NULL
- String -> String
- Integer/Long -> Number
- Float/Double -> Number
- Boolean -> Boolean
- Object with [JavascriptClass](https://github.com/gogogoghost/ZebView/blob/master/zebview/src/main/java/site/zbyte/zebview/JavascriptClass.kt) annotation -> Object with function callable (function need **JavascriptInterface** annotation)
- byte[] -> Uint8Array
- Array<Any> -> Array
- [Promise](https://github.com/gogogoghost/ZebView/blob/master/zebview/src/main/java/site/zbyte/zebview/Promise.kt)<T> -> Promise<T>
- org.json.JSONObject -> Object