package site.zbyte.zeb.ws

import android.util.Base64
import android.util.Log
import site.zbyte.zeb.BuildConfig
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

class WsServer(private val port:Int,private val auth:String) {

    private var runningThread:Thread?=null
    private var serverRunning=false

    private val magic="258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

    private val TAG="WsServer"

    @Synchronized
    fun start(){
        if(serverRunning||runningThread?.isAlive==true){
            throw Exception("Please stop first")
        }
        serverRunning=true
        runningThread=Thread{
            val serverAddress = InetSocketAddress(
                if(BuildConfig.DEBUG) "0.0.0.0" else "127.0.0.1",
                port
            )
            val socket=ServerSocket()
            socket.bind(serverAddress)
            socket.soTimeout=3
            while(serverRunning){
                val conn=try{
                    socket.accept()
                }catch (e:SocketTimeoutException){
                    continue
                }
                try{
                    processConn(conn)
                }catch (e:Exception){
                    e.printStackTrace()
                }finally {
                    //关闭连接
                    try{
                        conn.close()
                    }catch (_:Exception){}
                }
            }
        }.also {
            it.start()
        }
    }

    private fun processConn(conn:Socket){
        val input=conn.getInputStream()
        val output=conn.getOutputStream()
        //连接完成 接收第一行数据
        val reqLine=readLine(input)
        checkReqLine(reqLine)
        //读取头部
        val headers=readHeaders(input)
        //验证头部
        val sec=checkHeader(headers)
        val acceptStr=genAccept(sec)
        //发送接受请求
        sendAccept(acceptStr,output)
        //开始畅快通信
        while (true){
            val frame=readFrame(input)
            println(frame.size)
            println(String(frame))
        }
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
                //不允许分片
                0x00.toByte()->{
                    //分片帧
                    println("分片")
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
            Log.d(TAG,"length $length")
            if(length==126){
                length= ByteBuffer.wrap(input.readBytes(2)).short.toInt().and(0xffff)
            }else if(length==127){
                val tmp=ByteBuffer.wrap(input.readBytes(8)).long
                if(tmp>Int.MAX_VALUE){
                    throw Exception("Data too long")
                }
                length= tmp.toInt()
            }
            Log.d(TAG,"mask $mask length $length")
            val data=if(mask){
                val maskBytes=input.readBytes(4)
                val data=input.readBytes(length)
                processMask(maskBytes,data)
            }else{
                input.readBytes(length)
            }
            buffer.write(data)
            if(endFrame){
                return buffer.toByteArray()
            }
        }

    }

    private fun processMask(mask:ByteArray,data:ByteArray):ByteArray{
        for(i in data.indices){
            data[i]=data[i].xor(mask[i%4])
        }
        return data
    }

    private fun sendAccept(str:String,output:OutputStream){
        output.write("HTTP/1.1 101 Switching Protocols\r\n".toByteArray())
        output.write("Upgrade: websocket\r\n".toByteArray())
        output.write("Connection: Upgrade\r\n".toByteArray())
        output.write("Sec-WebSocket-Accept: $str\r\n".toByteArray())
        output.write("\r\n".toByteArray())
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

    private fun checkReqLine(reqLine:String){
        val reqArr=reqLine.split(' ')
        if(reqArr.size!=3){
            throw Exception("Bad request")
        }
        reqArr[1].let {
            if(it!="/$auth"){
                throw Exception("Auth fail")
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