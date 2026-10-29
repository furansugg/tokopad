package com.tokopad.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptLogoTest {
    @Test fun logoIsBoundedMonochromePng() {
        val input = Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.RED)
        val bytes = ByteArrayOutputStream()
        input.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        input.recycle()
        val encoded = Base64.getDecoder().decode(ReceiptLogo.normalize(bytes.toByteArray()))
        assertTrue(encoded.size <= 24 * 1024)
        val logo = requireNotNull(BitmapFactory.decodeByteArray(encoded, 0, encoded.size))
        assertTrue(logo.width <= 384 && logo.height <= 192)
        assertEquals(Color.BLACK, logo.getPixel(0, 0))
        logo.recycle()
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidLogoIsRejected() { ReceiptLogo.normalize(byteArrayOf(1, 2, 3)) }
}
