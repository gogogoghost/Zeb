import { send } from "./ws"
import { nextId } from "./utils"
import ByteBuffer from "bytebuffer"
import { MsgType } from "./constant"
import { encodeArray } from "./data"
import { promiseMap } from "./store"

/**
 * 创建一个proxy代理的api
 */
export function createObject (id, fieldList = [], funcList = []) {
  let src = {}
  //给field设置一个null占位置，后面会proxy
  fieldList.forEach((name) => {
    src[name] = null
  })
  funcList.forEach((name) => {
    src[name] = function () {
      //调用该方法
      return new Promise((resolve, reject) => {
        const callId = nextId()
        const buffer = new ByteBuffer()
        buffer.writeByte(MsgType.CALL_OBJECT)
        buffer.writeLong(callId)
        buffer.writeLong(id)
        buffer.writeCString(name)
        encodeArray(arguments, buffer)
        buffer.flip()
        send(buffer.toArrayBuffer())
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
    }
  })
}