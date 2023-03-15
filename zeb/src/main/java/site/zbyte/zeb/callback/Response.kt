package site.zbyte.zeb.callback

interface Response {

    enum class REST(val v:Byte){
        CALLBACK(1),
        OBJECT_CALLBACK(2),
        RELEASE_CALLBACK(3),
        RELEASE_OBJECT(4),
        PROMISE_FINISH(5)
    }

    fun encode():ByteArray
}