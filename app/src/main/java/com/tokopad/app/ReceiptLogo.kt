package com.tokopad.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object ReceiptLogo {
    suspend fun load(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open logo image" }
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 4 * 1024 * 1024) { "Logo image exceeds 4 MB" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        normalize(bytes)
    }

    internal fun normalize(bytes: ByteArray): String {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outWidth in 1..32768 && options.outHeight in 1..32768) { "Invalid logo image" }
        options.inJustDecodeBounds = false
        options.inSampleSize = 1
        while (options.outWidth / options.inSampleSize > 768 || options.outHeight / options.inSampleSize > 384) {
            options.inSampleSize *= 2
        }
        val source = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)) { "Cannot decode logo image" }
        try {
            val scale = minOf(384f / source.width, 192f / source.height, 1f)
            val width = maxOf(1, (source.width * scale).toInt())
            val height = maxOf(1, (source.height * scale).toInt())
            val logo = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(logo)
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(source, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
                val pixels = IntArray(width * height)
                logo.getPixels(pixels, 0, width, 0, 0, width, height)
                for (i in pixels.indices) {
                    val pixel = pixels[i]
                    val luminance = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
                    pixels[i] = if (luminance < 160) Color.BLACK else Color.WHITE
                }
                logo.setPixels(pixels, 0, width, 0, 0, width, height)
                val output = ByteArrayOutputStream()
                check(logo.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Cannot prepare logo" }
                require(output.size() <= 24 * 1024) { "Logo is too complex; choose a simpler image" }
                return Base64.getEncoder().encodeToString(output.toByteArray())
            } finally { logo.recycle() }
        } finally { source.recycle() }
    }
}
