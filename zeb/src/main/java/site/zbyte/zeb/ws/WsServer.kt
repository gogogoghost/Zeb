package site.zbyte.zeb.ws

import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Base64
import android.util.Log
import site.zbyte.zeb.BuildConfig
import site.zbyte.zeb.common.toByteArray
import site.zbyte.zeb.data.Blob
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.StringBuilder
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.experimental.and
import kotlin.experimental.xor

class WsServer(private val auth:String,private val listener: WsListener) {

    companion object{
        private const val TAG="WsServer"
        private const val magic="258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    }

    private var runningThread:Thread?=null
    private var serverRunning=false

    @Synchronized
    fun start():Int{
        if(serverRunning||runningThread?.isAlive==true){
            throw Exception("Please stop first")
        }
        serverRunning=true
        val serverAddress = if(BuildConfig.DEBUG){
            InetSocketAddress(
                "0.0.0.0",
                5000
            )
        }else{
            InetSocketAddress(
                "127.0.0.1",
                0
            )
        }
        val socket=ServerSocket()
        socket.bind(serverAddress)
        runningThread=Thread{
            socket.soTimeout=3
            while(serverRunning){
                val conn=try{
                    socket.accept()
                }catch (e:SocketTimeoutException){
                    continue
                }
                processConn(conn)
            }
            //清理现场
            try {
                socket.close()
            }catch (_:Exception){}
        }.also {
            it.start()
        }
        return socket.localPort
    }

    @Synchronized
    fun stop(){
        serverRunning=false
        runningThread?.join()
        runningThread=null
    }

    private fun processConn(conn:Socket){
        Thread{
            var wsConnection=false
            try{
                val input=BufferedInputStream(conn.getInputStream())
                val output=BufferedOutputStream(conn.getOutputStream())
                //连接完成 接收请求行
                val reqLine=readLine(input)
                val reqArr=reqLine.split(' ')
                if(reqArr.size!=3){
                    throw Exception("Bad request")
                }
                reqArr[0].let { method->
                    if(method=="GET"){
                        reqArr[1].let {uri->
                            val path=checkUri(uri)
                            if(path=="/zebChannel") {
                                //处理ws
                                wsConnection=true
                                processWs(input,output)
                            }else if(path.startsWith("/blob/")){
                                //处理get blob
                                processGetBlob(path.substring(6),input, output)
                            }
                        }
                    }else if(method=="POST"){
                        reqArr[1].let { uri->
                            val path=checkUri(uri)
                            if(path.startsWith("/blob/")){
                                //处理put blob
                                processPostBlob(path.substring(6),input,output)
                            }
                        }
                    }
                }
            }catch (e:Exception){
                Log.w(TAG,e)
            }finally {
                //关闭连接
                try{
                    conn.close()
                }catch (_:Exception){}
                if(wsConnection){
                    listener.onDisconnect()
                }
            }
        }.start()
    }

    private fun checkUri(path:String):String{
        val uri=Uri.parse(path)
        if(uri.getQueryParameter("auth")!=auth){
            throw Exception("Auth fail")
        }
        return uri.path!!
    }

    private fun processPostBlob(path:String,input: InputStream,output: OutputStream){
        val header=readHeaders(input)
        val length=header["Content-Length"]!!.toInt()
        val buf=ByteArray(length)
        input.read(buf)
        val name=listener.onPostBlob(path.toLong(),buf)
        sendBlob(Blob(name.toByteArray(),"text/plain"),output)
    }

    private fun processGetBlob(path:String,input: InputStream,output: OutputStream){
        readHeaders(input)
        val blob=listener.onGetBlob(path)
        if(blob==null){
            //404
            send404(output)
        }else{
            //200
            sendBlob(blob, output)
        }
    }

    private fun sendBlob(blob: Blob,output: OutputStream){
        output.write("HTTP/1.1 200 OK\r\n".toByteArray())
        output.write("Content-Type: ${blob.mimeType?:"application/octet-stream"}\r\n".toByteArray())
        output.write("Content-Length: ${blob.data.size}\r\n".toByteArray())
        output.write("Connection: close\r\n".toByteArray())
        writeCrossHeaders(output)
        output.write("\r\n".toByteArray())
        output.write(blob.data)
        output.flush()
    }

    private fun send404(output: OutputStream){
        output.write("HTTP/1.1 404 Not Found\r\n".toByteArray())
        output.write("Connection: close\r\n".toByteArray())
        writeCrossHeaders(output)
        output.write("\r\n".toByteArray())
        output.flush()
    }

    private fun writeCrossHeaders(output: OutputStream){
        output.write("Access-Control-Allow-Origin: *\r\n".toByteArray())
    }

    private fun processWs(input:InputStream,output: OutputStream){
        //读取头部
        val headers=readHeaders(input)
        //验证头部
        val sec=checkHeader(headers)
        val acceptStr=genAccept(sec)
        //发送接受请求
        sendAccept(acceptStr,output)
        //连接完成
        val sender=object :IFrameSender{
            override fun send(msg: ByteArray) {
                try{
                    sendFrame(msg,output)
                }catch (e:Exception){
                    Log.w(TAG,e)
                }
            }
        }
        listener.onConnect(sender)
        //启动接收数据循环
        while (true){
            val frame=readFrame(input)
            listener.onMessage(frame)
        }
    }

    private fun sendFrame(frame:ByteArray,output: OutputStream){
        //1 字符
        //2 字节
        output.write(0x82)
        if(frame.size<=125){
            //单字节表示
            output.write(frame.size)
        }else if(frame.size<=0xffff){
            //2字节表示
            output.write(126)
            output.write(frame.size.toByteArray().sliceArray(2..3))
        }else{
            //8字节
            output.write(127)
            output.write(frame.size.toLong().toByteArray())
        }
        output.write(frame)
        output.flush()
    }

    private fun readFrame(input:InputStream):ByteArray{
        val buffer=ByteArrayOutputStream()
        while(true){
            val b1=input.readByte()
            //是否是结束帧
            val endFrame=b1.and(0x80.toByte())!=0.toByte()
            //后面三个必须为0
            if(b1.and(0b01110000.toByte())!=0.toByte()){
                throw Exception("Bad Protocol:1")
            }
            when(b1.and(0x0f)){
                0x00.toByte()->{
                    //分片帧
                }
                0x01.toByte()->{
                    //文本帧 测试专用
                }
                0x02.toByte(),->{
                    //二进制帧
                }
                0x08.toByte()->{
                    //关闭连接
                    throw Exception("Client close")
                }
                else->{
                    throw Exception("Bad Protocol:2")
                }
            }
            val b2=input.readByte()
            val mask=b2.and(0x80.toByte())!=0.toByte()
            var length=b2.and(0x7f.toByte()).toInt().and(0xff)
            if(length==126){
                length= ByteBuffer.wrap(input.readBytes(2)).short.toInt().and(0xffff)
            }else if(length==127){
                val tmp=ByteBuffer.wrap(input.readBytes(8)).long
                if(tmp>Int.MAX_VALUE){
                    throw Exception("Data too long")
                }
                length= tmp.toInt()
            }
            val data=if(mask){
                val maskBytes=input.readBytes(4)
                val data=input.readBytes(length)
                for(i in data.indices){
                    data[i]=data[i].xor(maskBytes[i%4])
                }
                data
            }else{
                input.readBytes(length)
            }
            buffer.write(data)
            if(endFrame){
                return buffer.toByteArray()
            }
        }

    }

    private fun sendAccept(str:String,output:OutputStream){
        output.write("HTTP/1.1 101 Switching Protocols\r\n".toByteArray())
        output.write("Upgrade: websocket\r\n".toByteArray())
        output.write("Connection: Upgrade\r\n".toByteArray())
        output.write("Sec-WebSocket-Accept: $str\r\n".toByteArray())
        output.write("\r\n".toByteArray())
        output.flush()
    }

    private fun genAccept(sec:String):String{
        val digest = MessageDigest.getInstance("SHA-1")
        val hashedBytes = digest.digest((sec+magic).toByteArray())
        return Base64.encodeToString(hashedBytes,Base64.NO_WRAP)
    }

    private fun checkHeader(map:Map<String,String>):String{
        if(map["Upgrade"] !="websocket"||map["Connection"]!="Upgrade"||!map.containsKey("Sec-WebSocket-Key")){
            throw Exception("Bad request")
        }
        return map["Sec-WebSocket-Key"]!!
    }

    private fun readHeaders(input: InputStream):Map<String,String>{
        val header=HashMap<String,String>()
        while(true){
            val line=readLine(input)
            if(line.isNotEmpty()){
                val index=line.indexOf(':')
                if(index<0){
                    throw Exception("Bad headers:$line")
                }
                header[line.substring(0,index).trim()]=line.substring(index+1).trim()
            }else{
                return header
            }
        }
    }

    private fun readLine(input:InputStream):String{
        val str=StringBuilder()
        // \r has appear
        var flag=false
        while(true){
            val b=input.readByte().toInt().toChar()
            if(flag){
                // must be \n
                if(b=='\n'){
                    return str.toString()
                }else{
                    throw IOException("Unexpected char after \\r:$b")
                }
            }else{
                if(b=='\r'){
                    flag=true
                }else{
                    str.append(b)
                }
            }
        }
    }

    private fun InputStream.readByte():Byte{
        val b=this.read()
        if(b<0){
            throw IOException("Unexpected End")
        }
        return b.toByte()
    }

    private fun InputStream.readBytes(len:Int):ByteArray{
        val arr=ByteArray(len)
        var count=0
        while(count<len){
            val c=this.read(arr,count,len-count)
            if(c<=0){
                throw IOException("Unexpected End")
            }
            count+=c
        }
        return arr
    }
}