// import { encode, decode } from 'js-base64';
import { num2arr,concatArr,arr2num,arr2float } from "./utils";
/**
 * 基本类型 不管直接序列化
 * function 用_func#重命名然后传递字符串
 * 带有function的object 用_object#重命名后传递字符串
 */
// const FUNCTION_PREFIX="_func#"
// const OBJECT_PREFIX="_object#"
// const BYTEARRAY_PREFIX="_bytes#"
// const PROMISE_PREFIX="_promise#"

// js请求native的数据类型
const REQT={
    NULL:1,
    //基本类型
    STRING:2,
    NUMBER:3,
    //特殊类型
    FUNCTION:10,
    OBJECT:11,
    BYTEARRAY:12,
    ARRAY:13
}
// native响应的数据类型
const REST={
    NULL:1,
    //基本类型
    STRING:2,
    INT:4,
    FLOAT:5,
    //特殊类型
    OBJECT:11,
    BYTEARRAY:12,
    ARRAY:13,
    PROMISE:14
}


//存储回调的map
const functionMap={}
//存储带有回调function的map
const objectMap={}
//存储待pending的promise
const promiseMap={}

//Native传回来的object
const nativeObject={}

//TextEncoder
const textEncoder=new TextEncoder()
const textDecoder=new TextDecoder()

//生成随机字符串
function randomString(e) {
    e = e || 32;
    let t = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678",
        a = t.length,
        n = "";
    for (let i = 0; i < e; i++) n += t.charAt(Math.floor(Math.random() * a));
    return n
}

//编码参数
function encodeArg(arg){
    const c=arg.constructor
    if(arg==null||arg==undefined){
        return num2arr(REQT.NULL,1)
    }else if(c==Number){
        if(arg%1==0){
            //int or long
            if(arg>0xffffffff){
                //long
                return concatArr(
                    num2arr(REQT.NUMBER,1),
                    num2arr(arg,8)
                )
            }else{
                //int
                return concatArr(
                    num2arr(REQT.NUMBER,1),
                    num2arr(arg,4)
                )
            }
        }else{
            //float or double
            //not support float only
            let buf=new Float32Array([arg])
            return concatArr(
                num2arr(REQT.NUMBER,1),
                new Int8Array(buf.buffer)
            )
        }
    }else if(c==String){
        return concatArr(
            num2arr(REQT.STRING,1),
            textEncoder.encode(arg)
        )
    }else if(c==Function){
        const token=randomString(16)
        functionMap[token]=arg
        return concatArr(
            num2arr(REQT.FUNCTION,1),
            textEncoder.encode(token)
        )
    }else if(c==Object){
        const token=randomString(16)
        objectMap[token]=arg
        return concatArr(
            num2arr(REQT.OBJECT,1),
            textEncoder.encode(token)
        )
    }else if(c==Uint8Array){
        return concatArr(
            num2arr(REQT.BYTEARRAY,1),
            c
        )
    }else if(c==Array){
        return concatArr(
            num2arr(REQT.ARRAY,1),
            encodeArray(arg)
        )
    }else{
        throw new Error("Not support type to encode")
    }
}

//编码参数列表
function encodeArray(args){
    let res=new Uint8Array(0)
    for(const a of args){
        console.log(a)
        const buf=encodeArg(a)
        console.log(buf)
        res=concatArr(
            res,
            num2arr(buf.length,4),
            buf
        )
    }
    return res
}

//解码参数
function decodeArg(bytes){
    console.log(bytes)
    //先读取一个标志位
    const t=bytes[0]
    console.log("type",t)
    const body=bytes.slice(1)
    switch(t){
        case REST.NULL:
            return null
        case REST.STRING:
            return textDecoder.decode(body)
        case REST.INT:
            return arr2num(body)
        case REST.FLOAT:
            return arr2float(body)
        case REST.OBJECT:
            const zeroIndex=body.indexOf(0)
            const token=textDecoder.decode(
                body.slice(0,zeroIndex)
            )
            const funcListStr=textDecoder.decode(
                body.size(zeroIndex+1)
            )
            funcList=funcListStr.split(',')
            const obj=createApi(token,funcList,false)
            nativeObject[token]=obj
            return obj
        case REST.BYTEARRAY:
            return body
        case REST.PROMISE:
            const id=textDecoder.decode(body)
            return new Promise((resolve,reject)=>{
                promiseMap[id]={
                    resolve,
                    reject
                }
            })
        case REST.ARRAY:
            return decodeArray(body)
    }
}

//解码参数数组
function decodeArray(bytes){
    //arraybuffer
    let buffer=bytes.buffer
    //返回参数
    let res=[]
    let index=0
    while(index<bytes.length){
        //size
        const size=new Uint32Array(buffer,index)[0]
        index+=4
        //body
        const data=new Uint8Array(buffer,index,size)
        res.push(decodeArg(data))
    }
    return res
}

/**
 * 调用Native指定模块指定代码
 */
function invokeNative(moduleName,funcName,args,named){
    let res=null
    if(named){
        res=window.zebview.callNamedObject(
            moduleName,
            funcName,
            args
        )
    }else{
        res=window.zebview.callObject(
            moduleName,
            funcName,
            args
        )
    }
    return decodeArg(
        textEncoder.encode(res)
    )
}

/**
 * 创建一个proxy代理的api
 */
function createApi(name,funcList=[],named){
    // console.log("log:createApi:"+arguments)
    return new Proxy({},{
        get(_,key){
            for(const func of funcList){
                if(func===key){
                    return function(){
                        //调用该方法
                        console.log(arguments)
                        const bytes=encodeArray(arguments)
                        console.log(bytes)
                        const args=textDecoder.decode(bytes)
                        return invokeNative(name,func,args,named)
                    }
                }
            }
            return undefined
        }
    })
}

//服务存储
let api={}

function addApi(name,funcList){
    api[name]=createApi(name,funcList,true)
}

let exportObject=null

function stringToUint8Array(str){
    var arr = [];
    for (var i = 0, j = str.length; i < j; ++i) {
      arr.push(str.charCodeAt(i));
    }
   
    var tmpUint8Array = new Uint8Array(arr);
    return tmpUint8Array
  }

if(window.zebview){
    const baseApi=createApi("_base",["registerServiceWatcher"],true)
    const objList=baseApi.registerServiceWatcher((info)=>{
        addApi(info['name'],info['funcList'])
    })
    console.log("list",objList)
    for(const item of objList){
        addApi(item['name'],item['funcList'])
    }
    exportObject={
        api:api
    }
}

export default exportObject
