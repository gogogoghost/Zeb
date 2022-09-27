package site.zbyte.zebview.callback

class CallbackResponse(open val token:String, open val args:String):Response{
    override fun stringify(): String {
        TODO("Not yet implemented")
    }
}