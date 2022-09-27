package site.zbyte.zebview.callback

class CallbackObjectResponse(val token:String, val funcName:String,val args:String):Response{
    override fun stringify(): String {
        return ""
    }
}