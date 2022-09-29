package site.zbyte.zebview.callback

class EmptyResponse:Response {
    override fun toByteArray(): ByteArray {
        return byteArrayOf(Response.REST.EMPTY.v)
    }
}