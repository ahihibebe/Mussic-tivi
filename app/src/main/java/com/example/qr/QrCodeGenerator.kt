package com.example.qr

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object QrCodeGenerator {

    suspend fun generateQrBitmap(content: String, sizePx: Int = 512): ImageBitmap? =
        withContext(Dispatchers.Default) {
            if (content.isBlank()) return@withContext null
            try {
                val hints = mapOf(
                    EncodeHintType.MARGIN to 2,
                    EncodeHintType.CHARACTER_SET to "UTF-8"
                )
                val bitMatrix = QRCodeWriter().encode(
                    content,
                    BarcodeFormat.QR_CODE,
                    sizePx,
                    sizePx,
                    hints
                )
                val width = bitMatrix.width
                val height = bitMatrix.height
                val pixels = IntArray(width * height)
                val darkColor = android.graphics.Color.parseColor("#0F172A")
                val lightColor = android.graphics.Color.WHITE

                for (y in 0 until height) {
                    val offset = y * width
                    for (x in 0 until width) {
                        pixels[offset + x] = if (bitMatrix[x, y]) darkColor else lightColor
                    }
                }

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
                bitmap.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
}
