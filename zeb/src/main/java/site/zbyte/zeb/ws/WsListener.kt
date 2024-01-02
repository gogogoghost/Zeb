package site.zbyte.zeb.ws

import site.zbyte.zeb.data.Blob
import java.io.OutputStream

interface WsListener {
    fun onConnect(sender:IFrameSender)
    fun onDisconnect()
    fun onMessage(data:ByteArray)
    fun onGetBlob(path:String):Blob?
    fun onPostBlob(id:Long,data: ByteArray):String
}