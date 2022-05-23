package site.zbyte.zebview

interface PromiseCallback<T>{
    fun resolve(obj:T)
    fun reject(err:Any?)
}