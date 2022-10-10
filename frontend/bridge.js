import { num2arr, concatArr, arr2num, arr2float,makeBuffer } from "./utils";
import {Base64} from 'js-base64'

// js请求native的数据类型
const REQT = {
    NULL: 1,
    //基本类型
    STRING: 2,
    INT: 4,
    FLOAT: 5,
    BOOLEAN: 6,
    //特殊类型
    FUNCTION: 10,
    OBJECT: 11,
    BYTEARRAY: 12,
    ARRAY: 13,
    JSON:16
}
// native响应的数据类型
const REST = {
    NULL: 1,
    //基本类型
    STRING: 2,
    INT: 4,
    FLOAT: 5,
    BOOLEAN: 6,
    //特殊类型
    OBJECT: 11,
    BYTEARRAY: 12,
    ARRAY: 13,
    PROMISE: 14,
    ERROR: 15,
    JSON:16
}

// 异步回调消息处理
const AREST = {
    EMPTY:0,
    CALLBACK: 1,
    OBJECT_CALLBACK: 2,
    RELEASE_CALLBACK: 3,
    RELEASE_OBJECT: 4,
    PROMISE_FINISH: 5
}


//存储回调的map
const functionMap = {}
//存储带有回调function的map
const objectMap = {}
//存储待pending的promise
const promiseMap = {}

const textEncoder=new TextEncoder()
const textDecoder=new TextDecoder()

//服务存储
let apiInternal = {}

// 没有获取到zebview 默认导出null
let api = null

// 内存释放注册器
const register=new FinalizationRegistry((token)=>{
    window.zebview.releaseObject(token)
})

//生成随机字符串
function randomString(e) {
    e = e || 32;
    let t = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678",
        a = t.length,
        n = "";
    for (let i = 0; i < e; i++) n += t.charAt(Math.floor(Math.random() * a));
    return n
}

function isObjHasFunction(obj){
    for(const key in obj){
        if(obj[key].constructor==Function){
            return true
        }
    }
    return false
}

//编码参数
function encodeArg(arg) {
    if (arg == null || arg == undefined){
        return num2arr(REQT.NULL, 1)
    }
    const c = arg.constructor
    if (c == Number) {
        if (arg % 1 == 0) {
            if(arg > 0xffffffff){
                //use 8 bytes
                return concatArr(
                    num2arr(REQT.INT, 1),
                    num2arr(arg, 8)
                )
            }else{
                //use 4 bytes
                return concatArr(
                    num2arr(REQT.INT, 1),
                    num2arr(arg, 4)
                )
            }
        } else {
            //use 8 bytes
            const {buf,view}=makeBuffer(REQT.FLOAT,8)
            view.setFloat64(1,arg)
            return buf
        }
    } else if (c == String) {
        return concatArr(
            num2arr(REQT.STRING, 1),
            textEncoder.encode(arg)
        )
    } else if (c == Function) {
        const token = randomString(16)
        functionMap[token] = arg
        return concatArr(
            num2arr(REQT.FUNCTION, 1),
            textEncoder.encode(token)
        )
    } else if (c == Object) {
        //判断对象内有没有方法
        if(isObjHasFunction(arg)){
            const token = randomString(16)
            objectMap[token] = arg
            return concatArr(
                num2arr(REQT.OBJECT, 1),
                textEncoder.encode(token)
            )
        }else{
            //当成数据对象传递
            const body=JSON.stringify(arg)
            return concatArr(
                num2arr(REQT.JSON,1),
                textEncoder.encode(body)
            )
        }
    } else if (c == Uint8Array) {
        return concatArr(
            num2arr(REQT.BYTEARRAY, 1),
            c
        )
    } else if (c == Array) {
        return concatArr(
            num2arr(REQT.ARRAY, 1),
            encodeArray(arg)
        )
    } else if (c == Boolean) {
        return concatArr(
            num2arr(REQT.BOOLEAN, 1),
            new Uint8Array([arg ? 1 : 0])
        )
    } else {
        throw new Error("Not support type to encode")
    }
}

//编码参数列表
function encodeArray(args) {
    let res = new Uint8Array(0)
    for (const a of args) {
        const buf = encodeArg(a)
        res = concatArr(
            res,
            num2arr(buf.length, 4),
            buf
        )
    }
    return res
}

//解码参数
function decodeArg(bytes) {
    //先读取一个标志位
    const t = bytes[0]
    const body = bytes.slice(1)
    switch (t) {
        case REST.ERROR:
            throw new Error("Native exception:"+textDecoder.decode(body))
        case REST.NULL:
            return null
        case REST.STRING:
            return textDecoder.decode(body)
        case REST.INT:
            return arr2num(body)
        case REST.FLOAT:
            return arr2float(body)
        case REST.OBJECT:
            const zeroIndex = body.indexOf(0)
            const token = textDecoder.decode(
                body.slice(0, zeroIndex)
            )
            const funcListStr = textDecoder.decode(
                body.slice(zeroIndex + 1)
            )
            const funcList = funcListStr.split(',')
            const obj = createObject(token, funcList)
            //当对象不使用的时候 通知native回收内存
            register.register(obj,token)
            return obj
        case REST.BYTEARRAY:
            return body
        case REST.PROMISE:
            const id = textDecoder.decode(body)
            return new Promise((resolve, reject) => {
                promiseMap[id] = {
                    resolve,
                    reject
                }
            })
        case REST.ARRAY:
            return decodeArray(body)
        case REST.BOOLEAN:
            return body[0] == 1
        case REST.JSON:
            return JSON.parse(textDecoder.decode(body))
        default:
            throw new Error("Not support type to decode")
    }
}

//解码参数数组
function decodeArray(bytes) {
    //arraybuffer
    let buffer = bytes.buffer
    //返回参数
    let res = []
    let index = 0
    while (index < bytes.length) {
        //size
        const size = new DataView(buffer, index, 4).getInt32(0)
        index += 4
        //body
        const data = new Uint8Array(buffer, index, size)
        res.push(decodeArg(data))
        index += size
    }
    return res
}

/**
 * 创建一个proxy代理的api
 */
function createObject(name, funcList = []) {
    return new Proxy({}, {
        get(_, key) {
            for (const func of funcList) {
                if (func === key) {
                    return function () {
                        //调用该方法
                        const bytes = encodeArray(arguments)
                        const args = Base64.fromUint8Array(bytes)
                        const res = window.zebview.callObject(
                            name,
                            func,
                            args
                        )
                        return decodeArg(
                            Base64.toUint8Array(res)
                        )
                    }
                }
            }
            return undefined
        }
    })
}

/**
 * 创建baseObject
 */
function createBaseObject(funcList = []) {
    return new Proxy({}, {
        get(_, key) {
            for (const func of funcList) {
                if (func === key) {
                    return function () {
                        //调用该方法
                        const bytes = encodeArray(arguments)
                        const args = Base64.fromUint8Array(bytes)
                        const res = window.zebview.callBaseObject(
                            func,
                            args
                        )
                        return decodeArg(
                            Base64.toUint8Array(res)
                        )
                    }
                }
            }
            return undefined
        }
    })
}

/**
 * 处理队列下来的消息
 */
function processMessage(bytes) {
    const t = bytes[0]
    const body = bytes.slice(1)
    switch (t) {
        case AREST.EMPTY:
            //空消息
            return
        case AREST.CALLBACK:
            {
                // 调用回调
                const i = body.indexOf(0)
                const token = textDecoder.decode(body.slice(0, i))
                const func = functionMap[token]
                if (func) {
                    const argsRaw = body.slice(i + 1)
                    const args = decodeArray(argsRaw)
                    func.apply(func, args)
                }
            }
            break
        case AREST.OBJECT_CALLBACK:
            {
                //调用对象内的回调
                const i = body.indexOf(0)
                const token=textDecoder.decode(body.slice(0,i))
                const obj=objectMap[token]
                if(obj){
                    const i2=body.indexOf(0,i+1)
                    const funcName=textDecoder.decode(body.slice(i+1,i2))
                    const func=obj[funcName]
                    if(func){
                        const argsRaw=body.slice(i2+1)
                        const args=decodeArray(argsRaw)
                        func.apply(func,args)
                    }
                }
            }
            break
        case AREST.RELEASE_CALLBACK:
            {
                //释放方法
                const name=textDecoder.decode(body)
                delete functionMap[name]
            }
            break
        case AREST.RELEASE_OBJECT:
            {
                //释放对象
                const name=textDecoder.decode(body)
                delete objectMap[name]
            }
            break
        case AREST.PROMISE_FINISH:
            {
                //promise finalize
                const i=body.indexOf(0)
                const name=textDecoder.decode(body.slice(0,i))
                const promise=promiseMap[name]
                if(promise){
                    const success=body[i+1]==1
                    const arg=decodeArg(body.slice(i+2))
                    if(success){
                        promise.resolve(arg)
                    }else{
                        promise.reject(arg)
                    }
                    delete promiseMap[name]
                }
            }
            break
    }
}

/**
 * 消息循环
 */
async function messageLoop() {
    while (true) {
        const res = await fetch('http://zv/receive')
        const data = await res.arrayBuffer()
        processMessage(new Uint8Array(data))
    }
}

if (window.zebview) {
    const baseApi = createBaseObject(["registerServiceWatcher"], true)
    const objList = baseApi.registerServiceWatcher((name, obj) => {
        apiInternal[name] = obj
    })
    for (let i = 0; i < objList.length; i++) {
        const name = objList[i++]
        const obj = objList[i]
        apiInternal[name] = obj
    }
    // 启动消息循环 接收消息
    messageLoop()
    api=apiInternal
}

export {
    api
}