package site.zbyte.zeb.common

import java.nio.ByteBuffer
import java.util.*


/**
 * 随机字符串
 */
//fun randomString(length:Int):String{
//    val str="ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"
//    return (1..length).map { str.random() }.joinToString("")
//}

fun Byte.toByteArray():ByteArray{
    return byteArrayOf(this)
}

fun Int.toByteArray():ByteArray{
    return ByteBuffer.allocate(4)
        .putInt(this)
        .array()
}

fun Long.toByteArray():ByteArray {
    return ByteBuffer.allocate(8)
        .putLong(this)
        .array()
}

fun Double.toByteArray():ByteArray{
    return ByteBuffer.allocate(8)
        .putDouble(this)
        .array()
}

fun ByteArray.toStr(): String {
    val sb = StringBuffer(this.size)
    var sTemp: String
    for (i in this.indices) {
        sTemp = Integer.toHexString(0xFF and this[i].toInt())
        if (sTemp.length < 2) sb.append(0)
        sb.append(sTemp.uppercase(Locale.ROOT))
    }
    return sb.toString()
}

fun ByteBuffer.readBytes(len:Int):ByteArray{
    val buf=ByteArray(len)
    this.get(buf)
    return buf
}

fun ByteBuffer.readString(len:Int):String{
    return String(this.readBytes(len))
}

fun ByteBuffer.readString():String {
    var count = 1
    while (this.get(this.position() + count) != 0x00.toByte()) {
        count++
    }
    val res = if (count == 1) "" else {
        this.readString(count)
    }
    this.position(this.position() + 1)
    return res
}

fun Exception.toStr():String{
    var throwable:Throwable?=this
    var msg = ""
    while(throwable!=null){
        if (msg.isNotEmpty()){
            msg += " -> "
        }
        msg += throwable.javaClass.name
        throwable.message?.also {
            msg+= ":$it"
        }
        throwable=throwable.cause
    }
    return msg
}