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
  let obj = {}
  //给field设置一个null占位置，后面会proxy
  fieldList.forEach((name) => {
    obj[name] = {
      get () {
        return new Promise((resolve, reject) => {
          const promiseId = nextId()
          const buffer = new ByteBuffer()
          buffer.writeByte(MsgType.READ_OBJECT)
          buffer.writeLong(promiseId)
          buffer.writeLong(id)
          buffer.writeCString(name)
          buffer.flip()
          send(buffer.toArrayBuffer())
          promiseMap[promiseId] = {
            resolve,
            reject,
          }
        })
      },
      set (value) {

      }
    }
  })
  funcList.forEach((name) => {
    obj[name] = function () {
      //调用该方法
      return new Promise((resolve, reject) => {
        const promiseId = nextId()
        const buffer = new ByteBuffer()
        buffer.writeByte(MsgType.CALL_OBJECT)
        buffer.writeLong(promiseId)
        buffer.writeLong(id)
        buffer.writeCString(name)
        encodeArray(arguments, buffer)
        buffer.flip()
        send(buffer.toArrayBuffer())
        promiseMap[promiseId] = {
          resolve,
          reject,
        }
      })
    }
  })
  return obj
}