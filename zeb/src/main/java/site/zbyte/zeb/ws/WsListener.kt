package site.zbyte.zeb.ws

import java.io.OutputStream

interface WsListener {
    fun onConnect(output:OutputStream)
    fun onDisconnect()

    fun onMessage(data:ByteArray)
}