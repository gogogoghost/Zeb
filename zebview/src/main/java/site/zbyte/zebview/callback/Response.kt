package site.zbyte.zebview.callback

interface Response {

    enum class REST(val v:Byte){
        CALLBACK(0x01),
        OBJECT_CALLBACK(0x02),
        RELEASE_CALLBACK(0x03),
        RELEASE_OBJECT(0x04)
    }

    fun stringify():String
}