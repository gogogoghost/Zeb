import { num2arr, concatArr, arr2num, arr2float, makeBuffer, randomString } from "./utils";
import { Base64 } from 'js-base64'
import { suspend, unsuspend, isSuspend } from "./suspend";

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
    ERROR: 15,
    JSON: 16
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
    JSON: 16
}

// 异步回调消息处理
const AREST = {
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

const textEncoder = new TextEncoder()
const textDecoder = new TextDecoder()

//服务存储
let apiInternal = {}

// 没有获取到zeb 默认导出null
let api = null

// Native error prefix
const naviveErrorPrefix = "Native exception:"

// 扩展error方法
// chrome>=93 支持cause
Error.prototype.toStr = function () {
    let msg = ""
    let cause = this
    while (cause) {
        if (msg) {
            msg += " -> "
        }
        // 非error类型，转成字符串
        if (cause.constructor != Error) {
            msg += JSON.stringify(cause)
            break
        }
        msg += cause.name + ":" + cause.message
        cause = cause.cause
    }
    return msg
}

// 内存释放注册器
// chrome >= 84
let register = null
if (window.FinalizationRegistry) {
    register = new FinalizationRegistry((token) => {
        window.zeb.releaseObject(token)
    })
} else {
    console.warn("Not support FinalizationRegistry. Js object receive from native will be not released.")
}

function isObjHasFunction (obj) {
    for (const key in obj) {
        if (obj[key] instanceof Function) {
            return true
        }
    }
    return false
}

//obj -> bytes -> base64
function encodeArg (arg) {
    if (arg == null || arg == undefined) {
        return num2arr(REQT.NULL, 1)
    }
    const c = arg.constructor
    if (c == Number) {
        if (arg % 1 == 0) {
            if (arg > 0xffffffff) {
                //use 8 bytes
                return concatArr(
                    num2arr(REQT.INT, 1),
                    num2arr(arg, 8)
                )
            } else {
                //use 4 bytes
                return concatArr(
                    num2arr(REQT.INT, 1),
                    num2arr(arg, 4)
                )
            }
        } else {
            //use 8 bytes
            const { buf, view } = makeBuffer(REQT.FLOAT, 8)
            view.setFloat64(1, arg)
            return buf
        }
    } else if (c == String) {
        return concatArr(
            num2arr(REQT.STRING, 1),
            textEncoder.encode(arg)
        )
    } else if (c == Function) {
        const token = randomString(8)
        functionMap[token] = arg
        return concatArr(
            num2arr(REQT.FUNCTION, 1),
            textEncoder.encode(token)
        )
    } else if (c == Object) {
        //判断对象内有没有方法
        if (isObjHasFunction(arg)) {
            const token = randomString(8)
            objectMap[token] = arg
            return concatArr(
                num2arr(REQT.OBJECT, 1),
                textEncoder.encode(token)
            )
        } else {
            //当成数据对象传递
            const body = JSON.stringify(arg)
            return concatArr(
                num2arr(REQT.JSON, 1),
                textEncoder.encode(body)
            )
        }
    } else if (c == Uint8Array) {
        return concatArr(
            num2arr(REQT.BYTEARRAY, 1),
            arg
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
    } else if (c == Error) {
        return concatArr(
            num2arr(REQT.ERROR, 1),
            textEncoder.encode(arg.toStr())
        )
    } else {
        throw new Error("Not support type to encode:" + arg)
    }
}

//编码参数列表
function encodeArray (args) {
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
function decodeArg (bytes, throwErr = true) {
    //先读取一个标志位
    const t = bytes[0]
    const body = bytes.slice(1)
    switch (t) {
        case REST.ERROR:
            const err = new Error(naviveErrorPrefix + textDecoder.decode(body))
            if (throwErr) {
                throw err
            } else {
                return err
            }
        case REST.NULL:
            return null
        case REST.STRING:
            return textDecoder.decode(body)
        case REST.INT:
            return arr2num(body)
        case REST.FLOAT:
            return arr2float(body)
        case REST.OBJECT:
            //8字节长的id
            const token = textDecoder.decode(
                body.slice(0, 8)
            )
            //field以，分隔以0结尾
            const index = body.indexOf(0, 8)
            const fieldArr = body.slice(8, index)
            const methodArr = body.slice(index + 1, body.length)

            const fieldRaw = textDecoder.decode(fieldArr)
            const funcRaw = textDecoder.decode(methodArr)

            const fieldList = fieldRaw.length == 0 ? [] : fieldRaw.split(',')
            const funcList = funcRaw.length == 0 ? [] : funcRaw.split(',')

            const obj = createObject(token, fieldList, funcList)
            //当对象不使用的时候 通知native回收内存
            if (register) {
                register.register(obj, token)
            }
            return obj
        case REST.BYTEARRAY:
            return body
        case REST.PROMISE:
            const id = textDecoder.decode(body.slice(0, 8))
            let hasSuspend = false
            //read more
            let i = 8;
            while (i < body.length) {
                const magic = body[i]
                i++
                if ((magic & 0x10) != 0) {
                    //suspend
                    const token = textDecoder.decode(body.slice(i, i + 8))
                    i += 8
                    if ((magic & 0x01) != 0) {
                        //object
                        suspend(true, id, token)
                        hasSuspend = true
                    } else if ((magic & 0x02) != 0) {
                        //callback
                        suspend(false, id, token)
                        hasSuspend = true
                    }
                } else {
                    break
                }
            }
            return new Promise((resolve, reject) => {
                promiseMap[id] = {
                    resolve,
                    reject,
                    hasSuspend
                }
            })
        case REST.ARRAY:
            return decodeArray(body)
        case REST.BOOLEAN:
            return body[0] == 1
        case REST.JSON:
            return JSON.parse(textDecoder.decode(body))
        default:
            throw new Error("Not support type to decode:" + t)
    }
}

//解码参数数组
function decodeArray (bytes) {
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
function createObject (token, fieldList = [], funcList = []) {
    let src = {}
    fieldList.forEach((name) => {
        src[name] = null
    })
    funcList.forEach((name) => {
        src[name] = function () {
            //调用该方法
            const bytes = encodeArray(arguments)
            const args = Base64.fromUint8Array(bytes)
            const res = window.zeb.callObject(
                token,
                name,
                args
            )
            return decodeArg(
                Base64.toUint8Array(res)
            )
        }
    })
    return new Proxy(src, {
        get (_, key) {
            for (const func of funcList) {
                if (func === key) {
                    return src[key]
                }
            }
            for (const field of fieldList) {
                if (field === key) {
                    const res = window.zeb.readObject(
                        token,
                        key
                    )
                    return decodeArg(Base64.toUint8Array(res))
                }
            }
            return undefined
        }
    })
}

/**
 * 处理队列下来的消息
 */
async function processMessage (bytes, resCallback) {
    const t = bytes[0]
    const body = bytes.slice(1)
    switch (t) {
        case AREST.CALLBACK:
            {
                // 调用回调
                const token = textDecoder.decode(body.slice(0, 8))
                if (isSuspend(false, token)) {
                    return
                }
                const func = functionMap[token]
                if (func) {
                    const argsRaw = body.slice(8)
                    const args = decodeArray(argsRaw)
                    const res = await func.apply(func, args)
                    resCallback(res)
                }
            }
            break
        case AREST.OBJECT_CALLBACK:
            {
                //调用对象内的回调
                const token = textDecoder.decode(body.slice(0, 8))
                if (isSuspend(true, token)) {
                    return
                }
                const obj = objectMap[token]
                if (obj) {
                    const nameEnd = body.indexOf(0, 8)
                    const funcName = textDecoder.decode(body.slice(8, nameEnd))
                    const func = obj[funcName]
                    if (func) {
                        const argsRaw = body.slice(nameEnd + 1)
                        const args = decodeArray(argsRaw)
                        const res = await func.apply(func, args)
                        resCallback(res)
                    }
                }
            }
            break
        case AREST.RELEASE_CALLBACK:
            {
                //释放方法
                const name = textDecoder.decode(body)
                delete functionMap[name]
            }
            break
        case AREST.RELEASE_OBJECT:
            {
                //释放对象
                const name = textDecoder.decode(body)
                delete objectMap[name]
            }
            break
        case AREST.PROMISE_FINISH:
            {
                //promise finalize
                const name = textDecoder.decode(body.slice(0, 8))
                const promise = promiseMap[name]
                if (promise) {
                    const success = body[8] == 1
                    const arg = decodeArg(body.slice(9), false)
                    if (success) {
                        promise.resolve(arg)
                    } else {
                        promise.reject(new Error(naviveErrorPrefix + arg))
                    }
                    if (promise.hasSuspend) {
                        unsuspend(name)
                    }
                    delete promiseMap[name]
                }
            }
            break
    }
}

if (window.zeb) {
    const baseApi = createObject(null, [], [
        "registerServiceWatcher",
    ], true)
    const objList = baseApi.registerServiceWatcher((name, obj) => {
        apiInternal[name] = obj
    })
    for (let i = 0; i < objList.length; i++) {
        const name = objList[i++]
        const obj = objList[i]
        apiInternal[name] = obj
    }
    api = apiInternal

    // 初始化回调接受器
    // 由于native并不能正确的等待promise 所以需要手动实现回调
    window.zebCall = async function (str, promiseId) {
        const data = Base64.toUint8Array(str)
        let resValue = null
        try {
            await processMessage(
                data,
                (res) => {
                    resValue = res
                }
            )
        } catch (e) {
            console.warn(e)
            resValue = e
        }
        if (promiseId) {
            window.zeb.finalizePromise(
                promiseId,
                Base64.fromUint8Array(encodeArg(resValue))
            )
        }
    }
}

export {
    api
}