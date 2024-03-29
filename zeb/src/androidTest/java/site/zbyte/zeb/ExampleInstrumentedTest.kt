package site.zbyte.zeb

import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import site.zbyte.zeb.callback.Promise
import java.nio.ByteBuffer

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val promise= Promise<String>{
            it.resolve("")
        }
        println(promise is Promise<*>)
    }

    @Test
    fun decode(){
        val str="MCwwLDAsMTcsMTAsMTAyLDUzLDUyLDEwNiw3MSw2Niw4Miw3Nyw4MCw4Miw4Miw3NSw1MCwxMTksMTAwLDEwNQ=="
        println(Base64.decode(str,0).toStr())
    }

    @Test
    fun encode(){
        val arr= byteArrayOf(0,0,0,17,10,102,53,52,106,71,66,82,77,80,82,82,75,50,119,100,105)
        val str=Base64.encodeToString(arr,Base64.NO_WRAP)
        println(str)
        println(Base64.decode(str,0).toStr())
    }

    @Test
    fun float2array(){
        val buf=ByteBuffer.allocate(8)
        buf.putDouble(10.24)
        println(buf.array().toStr())
    }
}