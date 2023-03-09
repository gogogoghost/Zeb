## ZebView

[![](https://jitpack.io/v/site.zbyte/zebview.svg)](https://jitpack.io/#site.zbyte/zebview)

Bridge between javascript and java/kotlin on Android WebView

- Supports multiple types
- Js call Java and get return
- Java call js callback and get return
- Support promise
- Exception/Error can throw to each other

### Get Start

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

Native

```kotlin

//Init zebview
val zv=ZebView(webview)

//Insert your object into js named TestService
zv.addJsObject("TestService", 
    SharedObject(
        //Your object
        TestService(),
        //Js can access private field/method if internal is true
        internal = true,
        //Js can invoke @JavascriptInterface method default. All method available if unsafe is true
        unsafe = true
    )
)

```

Javascript

```js
import {api} from 'zebview'
//Call function
const res = api.TestService.someFunc()
//Read field
const field = api.TestService.someField
```

### A complex usage

Function in Class TestService

```kotlin

@JavascriptInterface
fun testType(
    aInt:Int,
    aLong:Long,
    aFloat:Double,
    aStr:String,
    aBool:Boolean,
    bytes:ByteArray,
    nil:Any?,
    json:JSONObject,
    aArr:Array<Any?>,
    cb: Callback,
    obj: CallbackObject,
):Promise<Any?>{
    return Promise{
        val res=cb.call(
            aInt,
            aLong,
            aFloat,
            aStr,
            aBool,
            bytes,
            nil,
            json,
            aArr
        ).await()
        val res2=obj.call("done",res).await()
        it.resolve(res2)
    }
}
```

Use in js

```js

async()=>{
    const res=await api.TestService.testType(
        //1 byte
        0xff,
        //5 bytes
        0x5555555555,
        //double
        -10.24,
        //string
        "Hello world",
        //boolean
        false,
        //bytes array
        new Uint8Array([0x5f,0x68]),
        //null
        null,
        //json object
        {name:'Jack',age:18},
        //array of any type
        [
            "Don't worry",
            {price:100},
            [-100,200,-300],
            "Be Happy"
        ],
        //callback
        function(){
            //print all params above this function
            console.log(arguments)
            return "Yes Please"
        },
        //object with multiple callback
        {
            done(txt){
                //print 'Yes Please'
                console.log(txt)
                return 999
            }
        }
    )
    //print 999
    console.log(res)
}
```

### Supports multiple types

Javascript -> Java (function params)

- NULL -> NULL
- String -> String
- Number(integer) -> Integer/Long(>0xffffffff)
- Number(double) -> Double
- Boolean -> Boolean
- Callback function -> [Callback](https://github.com/gogogoghost/ZebView/blob/master/zebview/src/main/java/site/zbyte/zebview/callback/Callback.kt)
- Object with function -> [CallbackObject](https://github.com/gogogoghost/ZebView/blob/master/zebview/src/main/java/site/zbyte/zebview/callback/CallbackObject.kt)
- Object without function -> org.json.JSONObject
- Uint8Array -> byte[]
- Array -> Array<Any?>

---

Java -> Javascript (callback params / function result / promise resolve)

- NULL -> NULL
- String -> String
- Integer/Long/Float/Double -> Number
- Boolean -> Boolean
- [SharedObject](https://github.com/gogogoghost/ZebView/blob/master/zebview/src/main/java/site/zbyte/zebview/data/SharedObject.kt) -> Object
- byte[] -> Uint8Array
- Array<Any?> -> Array
- [Promise](https://github.com/gogogoghost/ZebView/blob/master/zebview/src/main/java/site/zbyte/zebview/callback/Promise.kt)<T?> -> Promise<T?>
- org.json.JSONObject -> Object
