import { num2arr, concatArr, arr2num, arr2float, makeBuffer, randomString, nextId } from "./utils";
import { suspend, unsuspend, isSuspend } from "./suspend";
import { connectZeb } from "./ws";
import ByteBuffer from "bytebuffer";

// 消息处理
const MsgType = {
    CALLBACK: 1,
    OBJECT_CALLBACK: 2,
    RELEASE_CALLBACK: 3,
    RELEASE_OBJECT: 4,
    PROMISE_FINISH: 5,
    CALL_OBJECT:6,
    READ_OBJECT:7
}

// 数据类型
const DataType={
    NULL: 1,
    //基本类型
    STRING: 2,
    LONG: 3,
    INT: 4,
    FLOAT: 5,
    BOOLEAN: 6,
    //特殊类型
    FUNCTION: 10,
    OBJECT: 11,
    BYTEARRAY: 12,
    ARRAY: 13,
    // PROMISE: 14,
    ERROR: 15,
    JSON: 16
}


//存储回调的map
const functionMap = {}
//存储带有回调function的map
const objectMap = {}
//存储待pending的promise
const promiseMap = {}

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
    register = new FinalizationRegistry((id) => {
        const buffer=new ByteBuffer()
        buffer.writeByte(MsgType.RELEASE_OBJECT)
        buffer.writeLong(id)
        buffer.flip()
        client.send(buffer.toArrayBuffer())
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

//obj -> bytes
function encodeArg (arg,buffer=new ByteBuffer()) {
    if (arg == null || arg == undefined) {
        buffer.writeByte(DataType.NULL)
    }
    const c = arg.constructor
    if (c == Number) {
        if (arg % 1 == 0) {
            if (arg > 0xffffffff) {
                //use 8 bytes
                buffer.writeByte(DataType.LONG)
                buffer.writeLong(arg)
            } else {
                //use 4 bytes
                buffer.writeByte(DataType.INT)
                buffer.writeInt(arg)
            }
        } else {
            //use 8 bytes
            buffer.writeByte(DataType.FLOAT)
            buffer.writeDouble(arg)
        }
    } else if (c == String) {
        buffer.writeCString(arg)
    } else if (c == Function) {
        const id=nextId()
        functionMap[id] = arg
        buffer.writeByte(DataType.FUNCTION)
        buffer.writeLong(id)
    } else if (c == Object) {
        //判断对象内有没有方法
        if (isObjHasFunction(arg)) {
            const id=nextId()
            objectMap[id] = arg
            buffer.writeByte(DataType.OBJECT)
            buffer.writeLong(id)
        } else {
            //当成数据对象传递
            buffer.writeByte(DataType.JSON)
            buffer.writeCString(JSON.stringify(arg))
        }
    } else if (c == Uint8Array) {
        buffer.writeByte(DataType.BYTEARRAY)
        buffer.writeInt(arg.size)
        buffer.append(arg)
    } else if (c == Array) {
        buffer.writeByte(DataType.ARRAY)
        encodeArray(arg,buffer)
    } else if (c == Boolean) {
        buffer.writeByte(DataType.BOOLEAN)
        buffer.writeByte(arg?1:0)
    } else if (c == Error) {
        buffer.writeByte(DataType.ERROR)
        buffer.writeCString(arg.toStr())
    } else {
        throw new Error("Not support type to encode:" + arg)
    }
    return buffer
}

//编码参数列表
function encodeArray (args,buffer=new ByteBuffer()) {
    // let res = new Uint8Array(0)
    buffer.writeInt(args.length)
    for (let i=0;i<args.length;i++) {
        encodeArg(args[i],buffer)
    }
    return buffer
}

//解码参数
function decodeArg (buffer, throwErr = true) {
    //先读取一个标志位
    const t = buffer.readByte()
    switch (t) {
        case DataType.ERROR:
            const err = new Error(naviveErrorPrefix + buffer.readCString())
            if (throwErr) {
                throw err
            } else {
                return err
            }
        case DataType.NULL:
            return null
        case DataType.STRING:
            return buffer.readCString()
        case DataType.INT:
            return buffer.readInt()
        case DataType.FLOAT:
            return buffer.readDouble()
        case DataType.OBJECT:
            //8字节长的id
            const objectId=buffer.readLong()
            const fieldRaw = buffer.readCString()
            const funcRaw = buffer.readCString()

            const fieldList = fieldRaw.length == 0 ? [] : fieldRaw.split(',')
            const funcList = funcRaw.length == 0 ? [] : funcRaw.split(',')

            const obj = createObject(objectId, fieldList, funcList)
            //当对象不使用的时候 通知native回收内存
            if (register) {
                register.register(obj, objectId)
            }
            return obj
        case DataType.BYTEARRAY:
            const len=buffer.readInt()
            const data=buffer.slice(buffer.offset,buffer.offset+len)
            buffer.skip(len)
            return data.toArrayBuffer()
        // case DataType.PROMISE:
        //     const id = buffer.readLong()
        //     // let hasSuspend = false
        //     // //read more
        //     // let i = 8;
        //     // while (i < body.length) {
        //     //     const magic = body[i]
        //     //     i++
        //     //     if ((magic & 0x10) != 0) {
        //     //         //suspend
        //     //         const token = textDecoder.decode(body.slice(i, i + 8))
        //     //         i += 8
        //     //         if ((magic & 0x01) != 0) {
        //     //             //object
        //     //             suspend(true, id, token)
        //     //             hasSuspend = true
        //     //         } else if ((magic & 0x02) != 0) {
        //     //             //callback
        //     //             suspend(false, id, token)
        //     //             hasSuspend = true
        //     //         }
        //     //     } else {
        //     //         break
        //     //     }
        //     // }
        //     return new Promise((resolve, reject) => {
        //         promiseMap[id] = {
        //             resolve,
        //             reject,
        //         }
        //     })
        case DataType.ARRAY:
            return decodeArray(buffer)
        case DataType.BOOLEAN:
            return buffer.readByte==1
        case DataType.JSON:
            return JSON.parse(buffer.readCString())
        default:
            throw new Error("Not support type to decode:" + t)
    }
}

//解码参数数组
function decodeArray (buffer) {
    const len=buffer.readInt()
    const res=[]
    for(let i=0;i<len;i++){
        res.push(decodeArg(buffer))
    }
    return res
    // //arraybuffer
    // let buffer = bytes.buffer
    // //返回参数
    // let res = []
    // let index = 0
    // while (index < bytes.length) {
    //     //size
    //     const size = new DataView(buffer, index, 4).getInt32(0)
    //     index += 4
    //     //body
    //     const data = new Uint8Array(buffer, index, size)
    //     res.push(decodeArg(data))
    //     index += size
    // }
    // return res
}

/**
 * 处理队列下来的消息
 */
async function processMessage(buffer) {
    const t = buffer.readByte()
    switch (t) {
        case MsgType.CALLBACK:
            {
                // 调用回调
                const callbackId=buffer.readLong()
                const promiseId=buffer.readLong()
                const args = decodeArray(buffer)
                // if (isSuspend(false, token)) {
                //     return
                // }
                const func = functionMap[callbackId]
                if (func) {
                    const res = await func.apply(func, args)
                    finalizePromise(promiseId,res)
                }
            }
            break
        case MsgType.OBJECT_CALLBACK:
            {
                //调用对象内的回调
                const objId=buffer.readLong()
                const funcName=buffer.readCString()
                const promiseId=buffer.readLong()
                const args = decodeArray(argsRaw)
                // if (isSuspend(true, token)) {
                //     return
                // }
                const obj = objectMap[objId]
                if (obj) {
                    const func = obj[funcName]
                    if (func) {
                        const res = await func.apply(func, args)
                        finalizePromise(promiseId,res)
                    }
                }
            }
            break
        case MsgType.RELEASE_CALLBACK:
            {
                //释放方法
                const id = buffer.readLong()
                delete functionMap[id]
            }
            break
        case MsgType.RELEASE_OBJECT:
            {
                //释放对象
                const id = buffer.readLong()
                delete objectMap[id]
            }
            break
        case MsgType.PROMISE_FINISH:
            {
                //promise finalize
                const id=buffer.readLong()
                const success=buffer.readByte()==1
                const arg=decodeArg(buffer)

                const promise = promiseMap[id]
                if (promise) {
                    if (success) {
                        promise.resolve(arg)
                    } else {
                        promise.reject(new Error(naviveErrorPrefix + arg))
                    }
                    // if (promise.hasSuspend) {
                    //     unsuspend(name)
                    // }
                    delete promiseMap[id]
                }
            }
            break
    }
}

let client=null

function finalizePromise(id,res){
    const buffer=new ByteBuffer()
    buffer.writeByte(MsgType.PROMISE_FINISH)
    buffer.writeLong(id)
    encodeArg(res,buffer)
    buffer.flip()
    client.send(buffer.toArrayBuffer())
}

/**
 * 创建一个proxy代理的api
 */
function createObject (id, fieldList = [], funcList = []) {
    let src = {}
    //给field设置一个null占位置，后面会proxy
    fieldList.forEach((name) => {
        src[name] = null
    })
    funcList.forEach((name) => {
        src[name] = function () {
            //调用该方法
            return new Promise((resolve,reject)=>{
                const callId=nextId()
                const buffer=new ByteBuffer()
                buffer.writeByte(MsgType.CALL_OBJECT)
                buffer.writeLong(callId)
                buffer.writeLong(id)
                buffer.writeCString(name)
                encodeArray(arguments,buffer)
                buffer.flip()
                client.send(buffer.toArrayBuffer())
                promiseMap[callId] = {
                    resolve,
                    reject,
                }
            })
        }
    })
    return new Proxy(src, {
        get (_, key) {
            for (const func of funcList) {
                if (func === key) {
                    return src[key]
                }
            }
            // for (const field of fieldList) {
            //     if (field === key) {
            //         const res = window.zeb.readObject(
            //             token,
            //             key
            //         )
            //         return decodeArg(Base64.toUint8Array(res))
            //     }
            // }
            // return undefined
        }
    })
}

export async function connect(){
    if(!window.zeb){
        throw new Error("Zeb not found")
    }
    const auth=window.zeb.getAuth()
    const port=window.zeb.getPort()

    client=await connectZeb(auth,port)
    client.onMessage((data)=>{
        console.log(data)
        const buffer=ByteBuffer.wrap(data)
        processMessage(buffer)
    })
    const baseApi=createObject(0,[],["registerServiceWatcher"])
    const objList=await baseApi.registerServiceWatcher((name,obj)=>{
        console.log('register',name,obj)
    })
    console.log(objList)
    console.log("done")
}

if (window.zeb) {

    

    // const baseApi = createObject(null, [], [
    //     "registerServiceWatcher",
    // ], true)
    // const objList = baseApi.registerServiceWatcher((name, obj) => {
    //     apiInternal[name] = obj
    // })
    // for (let i = 0; i < objList.length; i++) {
    //     const name = objList[i++]
    //     const obj = objList[i]
    //     apiInternal[name] = obj
    // }
    // api = apiInternal

    // // 初始化回调接受器
    // // 由于native并不能正确的等待promise 所以需要手动实现回调
    // window.zebCall = async function (str, promiseId) {
    //     const data = Base64.toUint8Array(str)
    //     let resValue = null
    //     try {
    //         await processMessage(
    //             data,
    //             (res) => {
    //                 resValue = res
    //             }
    //         )
    //     } catch (e) {
    //         console.warn(e)
    //         resValue = e
    //     }
    //     if (promiseId) {
    //         window.zeb.finalizePromise(
    //             promiseId,
    //             Base64.fromUint8Array(encodeArg(resValue))
    //         )
    //     }
    // }
}

export {
    api
}