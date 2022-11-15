package site.zbyte.zebview.callback

interface PromiseCallback<T>{
    fun resolve(obj:T)
    fun reject(err:Exception?)
}