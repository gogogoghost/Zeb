## ZebView

[![](https://jitpack.io/v/site.zbyte/zebview.svg)](https://jitpack.io/#site.zbyte/zebview)

Bridge between javascript and java on Android WebView

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

The following tow versions are compatible

- A.B.x
- A.B.y