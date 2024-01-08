import ByteBuffer from "bytebuffer"
import { callbackIdGen, objIdGen } from "./utils"
import { functionMap, objectMap } from './store'
import { createObject } from "./proxy"
import { DataType, MsgType } from "./constant"
import { send } from "./ws"

// Native error prefix
const naviveErrorPrefix = "Native exception:"
// 内存释放注册器
// chrome >= 84
let register = null
if (window.FinalizationRegistry) {
  register = new FinalizationRegistry((id) => {
    const buffer = new ByteBuffer()
    buffer.writeByte(MsgType.RELEASE_OBJECT)
    buffer.writeLong(id)
    buffer.flip()
    send(buffer.toArrayBuffer())
  })
} else {
  console.warn("Not support FinalizationRegistry. Js object receive from native will be not released.")
}

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

function isObjHasFunction (obj) {
  for (const key in obj) {
    if (obj[key] instanceof Function) {
      return true
    }
  }
  return false
}


//obj -> bytes
export function encodeArg (arg, buffer = new ByteBuffer()) {
  if (arg == null || arg == undefined) {
    buffer.writeByte(DataType.NULL)
    return
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
    buffer.writeByte(DataType.STRING)
    buffer.writeInt(arg.length)
    buffer.writeString(arg)
  } else if (c == Function) {
    const id = callbackIdGen.nextId()
    functionMap[id] = arg
    buffer.writeByte(DataType.FUNCTION)
    buffer.writeInt(id)
  } else if (c == Object) {
    //判断对象内有没有方法
    if (isObjHasFunction(arg)) {
      const id = objIdGen.nextId()
      objectMap[id] = arg
      buffer.writeByte(DataType.OBJECT)
      buffer.writeInt(id)
    } else {
      //当成数据对象传递
      buffer.writeByte(DataType.JSON)
      buffer.writeCString(JSON.stringify(arg))
    }
  } else if (c == Uint8Array) {
    buffer.writeByte(DataType.BYTEARRAY)
    buffer.writeInt(arg.length)
    buffer.append(arg)
  } else if (c == ArrayBuffer) {
    buffer.writeByte(DataType.BYTEARRAY)
    buffer.writeInt(arg.byteLength)
    buffer.append(arg)
  } else if (c == Array) {
    buffer.writeByte(DataType.ARRAY)
    encodeArray(arg, buffer)
  } else if (c == Boolean) {
    buffer.writeByte(DataType.BOOLEAN)
    buffer.writeByte(arg ? 1 : 0)
  } else if (c == Error) {
    buffer.writeByte(DataType.ERROR)
    const str = arg.toStr()
    buffer.writeInt(str.length)
    buffer.writeString(str)
  } else {
    throw new Error("Not support type to encode:" + arg)
  }
  return buffer
}

//编码参数列表
export function encodeArray (args, buffer = new ByteBuffer()) {
  // let res = new Uint8Array(0)
  buffer.writeInt(args.length)
  for (let i = 0; i < args.length; i++) {
    encodeArg(args[i], buffer)
  }
  return buffer
}

//解码参数
export function decodeArg (buffer, throwErr = true) {
  //先读取一个标志位
  const t = buffer.readByte()
  switch (t) {
    case DataType.ERROR:
      const err = new Error(naviveErrorPrefix + buffer.readString(buffer.readInt()))
      if (throwErr) {
        throw err
      } else {
        return err
      }
    case DataType.NULL:
      return null
    case DataType.STRING:
      return buffer.readString(buffer.readInt())
    case DataType.INT:
      return buffer.readInt()
    case DataType.LONG:
      return buffer.readLong()
    case DataType.FLOAT:
      return buffer.readDouble()
    case DataType.OBJECT:
      //8字节长的id
      const objectId = buffer.readInt()
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
      const len = buffer.readInt()
      const data = buffer.slice(buffer.offset, buffer.offset + len)
      buffer.skip(len)
      return data.toArrayBuffer()
    case DataType.ARRAY:
      return decodeArray(buffer)
    case DataType.BOOLEAN:
      return buffer.readByte() == 1
    case DataType.JSON:
      return JSON.parse(buffer.readCString())
    default:
      throw new Error("Not support type to decode:" + t)
  }
}

//解码参数数组
export function decodeArray (buffer) {
  const len = buffer.readInt()
  const res = []
  for (let i = 0; i < len; i++) {
    res.push(decodeArg(buffer))
  }
  return res
}