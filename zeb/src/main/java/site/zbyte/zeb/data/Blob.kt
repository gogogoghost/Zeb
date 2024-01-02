package site.zbyte.zeb.data

class Blob(
    val data:ByteArray,
    //default: application/octet-stream
    val mimeType:String?
)