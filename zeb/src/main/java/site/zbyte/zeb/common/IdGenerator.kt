package site.zbyte.zeb.common

object IdGenerator {
    private const val MIN=1L
    private const val MAX=0x1fffffffffffff

    private var current= 0L

    @Synchronized
    fun nextId():Long{
        current++
        if(current>= MAX){
            current= MIN
        }
        return current
    }
}