package com.tokopad.app

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceiptLayoutTest {
    private fun rasterFrames(bytes: ByteArray): List<Triple<Int, Int, ByteArray>> {
        val frames = mutableListOf<Triple<Int, Int, ByteArray>>()
        var offset = 0
        while (offset + 8 <= bytes.size) {
            if (bytes.copyOfRange(offset, offset + 4).contentEquals(byteArrayOf(0x1d, 0x76, 0x30, 0))) {
                val stride = (bytes[offset + 4].toInt() and 255) + ((bytes[offset + 5].toInt() and 255) shl 8)
                val height = (bytes[offset + 6].toInt() and 255) + ((bytes[offset + 7].toInt() and 255) shl 8)
                val end = offset + 8 + stride * height
                assertTrue("Truncated raster", end <= bytes.size)
                frames += Triple(stride, height, bytes.copyOfRange(offset + 8, end))
                offset = end
            } else offset++
        }
        return frames
    }

    @Test fun japaneseReceiptFieldsUseNonemptyRasterAndAsciiKeepsFastPath() {
        val japanese = "日本語のお店"
        val transaction = sale("").copy(shop = ShopSettings(shopName = japanese, header = "ようこそ", footer = "ありがとうございます"),
            customerName = "山田太郎", lines = listOf(CartLine(Product(name = "お茶", price = 100))))
        for ((paper, stride) in listOf(58 to 48, 80 to 72)) {
            val settings = ShopSettings(paperWidth = paper)
            val blocks = Receipt.blocks(transaction, settings).filter { block -> block.text.any { it.code > 126 } }
            val bytes = Receipt.bytes(transaction, settings)
            val frames = rasterFrames(bytes)
            assertEquals(blocks.size, frames.size)
            assertTrue(frames.all { it.first == stride && it.second in 1..2048 && it.third.any { pixel -> pixel != 0.toByte() } })
            assertTrue(Receipt.text(transaction, settings).contains(japanese))
            assertTrue(bytes.toString(Charsets.US_ASCII).contains("TOTAL"))
            val changed = transaction.copy(shop = transaction.shop.copy(shopName = "カタカナショップ"))
            assertFalse(frames.first().third.contentEquals(rasterFrames(Receipt.bytes(changed, settings)).first().third))
            assertTrue(rasterFrames(Receipt.bytes(sale("").copy(shop = ShopSettings()), settings)).isEmpty())
        }
    }

    @Test fun longJapaneseLinesWrapInsidePrinterRasterWidth() {
        val transaction = sale("").copy(shop = ShopSettings(shopName = "日".repeat(32)))
        val image = rasterFrames(Receipt.bytes(transaction, ShopSettings())).single()
        val shortImage = rasterFrames(Receipt.bytes(transaction.copy(shop = ShopSettings(shopName = "日")), ShopSettings())).single()
        assertEquals(48, image.first)
        assertTrue(image.second > shortImage.second)
        assertTrue(image.second <= 2048)
    }

    private fun sale(logo: String) = Sale(
        id = 1, number = "internal-id", createdAt = 0, zoneId = "UTC",
        lines = emptyList(), customerId = null, customerName = "", customerPhone = "",
        subtotal = 0, discount = 0, total = 0, payment = "cash", paid = 0,
        shop = ShopSettings(headerMode = "both", logo = logo),
    )

    private fun png(bitmap: Bitmap): String = try {
        val output = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        Base64.getEncoder().encodeToString(output.toByteArray())
    } finally {
        bitmap.recycle()
    }

    @Test fun logoProducesCenteredMonochromeGsV0RasterWithWhiteTransparency() {
        val bitmap = Bitmap.createBitmap(8, 2, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        bitmap.setPixel(0, 0, Color.BLACK)
        bitmap.setPixel(1, 0, Color.TRANSPARENT)
        bitmap.setPixel(7, 0, Color.BLACK)
        bitmap.setPixel(1, 1, Color.BLACK)
        bitmap.setPixel(2, 1, Color.rgb(128, 128, 128))
        bitmap.setPixel(3, 1, Color.rgb(127, 127, 127))
        val bytes = Receipt.bytes(sale(png(bitmap)), ShopSettings())
        val prefix = byteArrayOf(
            0x1b, 0x40, 0x1b, 0x74, 0x00,
            0x1b, 0x61, 0x01, 0x1b, 0x45, 0x00, 0x1d, 0x21, 0x00,
            0x1d, 0x76, 0x30, 0x00, 0x01, 0x00, 0x02, 0x00, 0x81.toByte(), 0x50, 0x0a,
        )
        assertArrayEquals(prefix, bytes.copyOfRange(0, prefix.size))
        assertTrue(bytes.toString(Charsets.US_ASCII).contains("Tokopad"))
    }

    @Test fun maximumLogoDimensionsProduceExpectedRasterLength() {
        val bitmap = Bitmap.createBitmap(384, 192, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        val bytes = Receipt.bytes(sale(png(bitmap)), ShopSettings())
        assertArrayEquals(
            byteArrayOf(0x1d, 0x76, 0x30, 0x00, 48, 0, 192.toByte(), 0),
            bytes.copyOfRange(14, 22),
        )
        assertTrue(bytes.copyOfRange(22, 22 + 48 * 192).all { it == 0xff.toByte() })
    }

    @Test fun oversizedBitmapDimensionsRejectedBeforeRasterization() {
        for ((width, height) in listOf(385 to 1, 1 to 193)) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                Receipt.bytes(sale(png(bitmap)), ShopSettings())
                error("Expected oversized logo rejection")
            } catch (error: IllegalArgumentException) {
                assertTrue(error.message!!.contains("384 x 192"))
            }
        }
    }
}
