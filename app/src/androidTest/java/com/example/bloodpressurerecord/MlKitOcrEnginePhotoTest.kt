package com.example.bloodpressurerecord.mlkit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloodpressurerecord.domain.ocr.BpReadingParser
import java.nio.ByteBuffer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MlKitOcrEnginePhotoTest {

    @Test
    fun recognizesSuppliedBloodPressureMonitorPhotos() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().context
        val engine = MlKitOcrEngine()
        val failures = mutableListOf<String>()

        expectedReadings.forEach { (fileName, expected) ->
            val bitmap = decodeUprightAsset(context, fileName)
            val actual = try {
                BpReadingParser.parse(engine.recognize(bitmap))
            } finally {
                bitmap.recycle()
            }
            val actualValues = actual?.let { Triple(it.systolic, it.diastolic, it.pulse) }
            if (actualValues != expected) {
                failures += "$fileName expected=$expected actual=$actualValues"
            }
        }

        assertTrue(
            "OCR mismatches (${failures.size}/${expectedReadings.size}):\n${failures.joinToString("\n")}",
            failures.isEmpty()
        )
    }

    private fun decodeUprightAsset(context: Context, fileName: String): Bitmap {
        val bytes = context.assets.open(fileName).use { it.readBytes() }
        val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
        return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
    }

    companion object {
        private val expectedReadings = linkedMapOf(
            "mmexport1786163394429.jpg" to Triple(116, 83, 62),
            "mmexport1786163395634.jpg" to Triple(112, 81, 82),
            "mmexport1786163396784.jpg" to Triple(116, 82, 76),
            "mmexport1786163397945.jpg" to Triple(115, 82, 76),
            "mmexport1786163399069.jpg" to Triple(110, 78, 75),
            "mmexport1786163400126.jpg" to Triple(118, 81, 82),
            "mmexport1786163401185.jpg" to Triple(116, 81, 71),
            "mmexport1786163402229.jpg" to Triple(116, 84, 68),
            "mmexport1786163403235.jpg" to Triple(113, 81, 76),
            "mmexport1786163404275.jpg" to Triple(113, 81, 76),
            "mmexport1786163405241.jpg" to Triple(112, 83, 75),
            "mmexport1786163406244.jpg" to Triple(110, 78, 71),
            "mmexport1786163407226.jpg" to Triple(115, 87, 86),
            "mmexport1786163408147.jpg" to Triple(119, 84, 73),
            "mmexport1786163409169.jpg" to Triple(119, 83, 72),
            "mmexport1786163410282.jpg" to Triple(112, 81, 71),
            "mmexport1786163411282.jpg" to Triple(118, 84, 65),
            "mmexport1786163433704.jpg" to Triple(109, 73, 75),
            "mmexport1786163434680.jpg" to Triple(111, 80, 78),
            "mmexport1786163435647.jpg" to Triple(118, 81, 71),
            "mmexport1786163436611.jpg" to Triple(111, 80, 71),
            "mmexport1786163437490.jpg" to Triple(115, 83, 66),
            "mmexport1786163438433.jpg" to Triple(111, 74, 77),
            "mmexport1786163439501.jpg" to Triple(106, 76, 73),
            "mmexport1786163440542.jpg" to Triple(108, 75, 73)
        )
    }
}
