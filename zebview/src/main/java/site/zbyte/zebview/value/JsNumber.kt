package site.zbyte.zebview.value

import java.nio.ByteBuffer

class JsNumber(private val bytes:ByteArray) {

    private val buf=ByteBuffer.wrap(bytes)

    fun toByte():Byte{
        return buf.get()
    }
    fun toInt():Int{
        return buf.int
    }
    fun toLong():Long{
        return buf.long
    }
    fun toFloat():Float{
        return buf.float
    }
    fun toDouble():Double{
        return buf.double
    }
}