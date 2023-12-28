package site.zbyte.zeb.ws

import java.io.OutputStream

interface WsListener {
    fun onConnect(sender:IFrameSender)
    fun onDisconnect()

    fun onMessage(data:ByteArray)
}