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
object TestServiceObject{
    @JavascriptInterface
    fun test(intVal:Int,strVal:String,boolVal:Boolean){
        //This function invoke by js and can return something
        //See [Support Params Type] behind
    }
}

//Create ZebView
val zv=ZebView(context)

//Add Service
zv.addService("TestService",TestServiceObject)
    .addService("SecondService",SecondServiceObject)

//Load your page
zv.loadUrl("http://192.168.0.137:3000")
```

#### Frontend Part

See [zebview-bridge](https://github.com/gogogoghost/Zebview/tree/master/frontend)

### Support Params Type

Javascript -> Java

- Number -> Integer/Long
- String -> String
- Boolean -> Boolean
- Uint8Array -> byte[]
- Array -> Array<Any>
- Callback function -> Callback
- Object(callback only) -> CallbackObject
- Object(data only) -> JSONObject

---

Java -> Javascript(callback)

- Integer/Long -> Number
- String -> String
- Boolean -> Boolean
- byte[] -> Uint8Array
- Array<Any> -> Array
- JSONObject -> Object
- [Promise](https://github.com/gogogoghost/ZebView/blob/master/zebview/src/main/java/site/zbyte/zebview/Promise.kt)<T> -> Promise<T>

### Callback

When android receive a Callback or CallbackObject from js

```kotlin
//Callback
callback.call(args0,args1,args2)
//CallbackObject
callbackObject.call("onSuccess",args0,args1,args2)
callbackObject.call("onFail",args0,args1,args2)
```

You need to release a callback if you don't need it any more.

```kotlin
callback.release()
callbackObject.release()
```

### Version Compatible

Versions of [ZebView](https://jitpack.io/#site.zbyte/zebview) for Android and [zebview-bridge](https://www.npmjs.com/package/zebview-bridge) for web

The following tow versions are compatible

- A.B.x
- A.B.y