package site.zbyte.zeb.callback

interface PromiseCallback<T>{
    fun resolve(obj:T?)
    fun reject(err:Exception?)
}