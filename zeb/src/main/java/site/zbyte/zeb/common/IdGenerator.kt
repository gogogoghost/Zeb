package site.zbyte.zeb.common

class IdGenerator {
    private var current= 0
    @Synchronized
    fun nextId():Int{
        current++
        if(current>= Int.MAX_VALUE){
            current= 1
        }
        return current
    }
}