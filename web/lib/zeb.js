import { connectZeb, send, onMessage } from "./ws";
import ByteBuffer from "bytebuffer";
import { encodeArg, encodeArray, decodeArg, decodeArray } from "./data";
import { createObject } from "./proxy";
import { MsgType } from "./constant";
import { promiseMap, functionMap, objectMap, props } from "./store";
import { Mutex } from 'async-mutex';

const mutex = new Mutex();
let apiInternal = null

/**
 * 处理队列下来的消息
 */
async function processMessage (buffer) {
    const t = buffer.readByte()
    switch (t) {
        case MsgType.CALLBACK:
            {
                // 调用回调
                const callbackId = buffer.readInt()
                const promiseId = buffer.readInt()
                const args = decodeArray(buffer)
                const func = functionMap[callbackId]
                if (func) {
                    const res = await func.apply(func, args)
                    finalizePromise(promiseId, res)
                }
            }
            break
        case MsgType.OBJECT_CALLBACK:
            {
                //调用对象内的回调
                const objId = buffer.readInt()
                const funcName = buffer.readCString()
                const promiseId = buffer.readInt()
                const args = decodeArray(buffer)
                const obj = objectMap[objId]
                if (obj) {
                    const func = obj[funcName]
                    if (func) {
                        const res = await func.apply(func, args)
                        finalizePromise(promiseId, res)
                    }
                }
            }
            break
        case MsgType.RELEASE_CALLBACK:
            {
                //释放方法
                const id = buffer.readInt()
                delete functionMap[id]
            }
            break
        case MsgType.RELEASE_OBJECT:
            {
                //释放对象
                const id = buffer.readInt()
                delete objectMap[id]
            }
            break
        case MsgType.PROMISE_FINISH:
            {
                //promise finalize
                const id = buffer.readInt()
                const success = buffer.readByte() == 1
                const arg = decodeArg(buffer)

                const promise = promiseMap[id]
                if (promise) {
                    if (success) {
                        promise.resolve(arg)
                    } else {
                        promise.reject(new Error(naviveErrorPrefix + arg))
                    }
                    delete promiseMap[id]
                }
            }
            break
    }
}

function finalizePromise (id, res) {
    const buffer = new ByteBuffer()
    buffer.writeByte(MsgType.PROMISE_FINISH)
    buffer.writeInt(id)
    encodeArg(res, buffer)
    buffer.flip()
    send(buffer.toArrayBuffer())
}

export async function connect () {
    if (!window.zeb) {
        throw new Error("Zeb not found")
    }
    const release = await mutex.acquire()
    try {
        if (apiInternal != null) {
            return apiInternal
        }
        props.zebAuth = window.zeb.getAuth()
        props.zebPort = window.zeb.getPort()
        await connectZeb(props.zebAuth, props.zebPort)
        onMessage((data) => {
            const buffer = ByteBuffer.wrap(data)
            processMessage(buffer)
        })
        const baseApi = createObject(0, [], ["getService"])
        apiInternal = {}
        const objList = await baseApi.getService((name, obj) => {
            apiInternal[name] = obj
        })
        for (const item of objList) {
            apiInternal[item[0]] = item[1]
        }
        return apiInternal
    } finally {
        release()
    }
}