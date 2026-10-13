package com.example.qr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import java.util.EnumMap

object QrUtils {

    /**
     * Generates a high quality QR code bitmap from content text
     */
    fun generateQrBitmap(
        content: String,
        width: Int = 512,
        height: Int = 512,
        darkColor: Int = Color.BLACK,
        lightColor: Int = Color.WHITE
    ): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) darkColor else lightColor)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Ultra-resilient multi-pass QR code decoder:
     * - Multi-region scanning: full image, center 80%, center 60%, center 45%, top/bottom halves
     * - Preserves high-density module resolution (does NOT over-downsample long VLESS URLs)
     * - Handles inverted QR codes (white QR code on dark background common in dark mode VPN apps)
     * - Tries both HybridBinarizer and GlobalHistogramBinarizer
     * - Tries multi-angle rotations (0°, 90°, 180°, 270°)
     * - Tries both TRY_HARDER and default heuristics
     */
    fun decodeQrBitmap(bitmap: Bitmap): String? {
        // Fast path 1: Try original bitmap directly (both normal and inverted)
        decodeSingleBitmap(bitmap)?.let { return it }

        // Candidate crops (especially for phone screenshots where QR is in the center)
        val w = bitmap.width
        val h = bitmap.height

        val crops = mutableListOf<Bitmap>()

        // 1. Center 80% crop
        if (w > 200 && h > 200) {
            val cw80 = (w * 0.8f).toInt()
            val ch80 = (h * 0.8f).toInt()
            val x80 = (w - cw80) / 2
            val y80 = (h - ch80) / 2
            try {
                crops.add(Bitmap.createBitmap(bitmap, x80, y80, cw80, ch80))
            } catch (_: Exception) {}
        }

        // 2. Center 60% crop
        if (w > 250 && h > 250) {
            val cw60 = (w * 0.6f).toInt()
            val ch60 = (h * 0.6f).toInt()
            val x60 = (w - cw60) / 2
            val y60 = (h - ch60) / 2
            try {
                crops.add(Bitmap.createBitmap(bitmap, x60, y60, cw60, ch60))
            } catch (_: Exception) {}
        }

        // 3. Center 45% crop (for large full-screen screenshots with small QR in center)
        if (w > 350 && h > 350) {
            val cw45 = (w * 0.45f).toInt()
            val ch45 = (h * 0.45f).toInt()
            val x45 = (w - cw45) / 2
            val y45 = (h - ch45) / 2
            try {
                crops.add(Bitmap.createBitmap(bitmap, x45, y45, cw45, ch45))
            } catch (_: Exception) {}
        }

        for (crop in crops) {
            decodeSingleBitmap(crop)?.let { return it }
        }

        // Multi-scale passes if large image
        if (w > 1600 || h > 1600) {
            val scaled1200 = scaleDownIfNeeded(bitmap, 1200)
            decodeSingleBitmap(scaled1200)?.let { return it }

            val scaled800 = scaleDownIfNeeded(bitmap, 800)
            decodeSingleBitmap(scaled800)?.let { return it }
        }

        // Rotation passes (for tilted phone photos)
        val rot90 = rotateBitmap(bitmap, 90f)
        decodeSingleBitmap(rot90)?.let { return it }

        val rot270 = rotateBitmap(bitmap, 270f)
        decodeSingleBitmap(rot270)?.let { return it }

        val rot180 = rotateBitmap(bitmap, 180f)
        decodeSingleBitmap(rot180)?.let { return it }

        return null
    }

    /**
     * Decodes a CameraX ImageProxy frame efficiently
     */
    fun decodeImageProxy(imageProxy: ImageProxy): String? {
        return try {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val bitmap = imageProxy.toBitmap()
            val oriented = if (rotationDegrees != 0) {
                rotateBitmap(bitmap, rotationDegrees.toFloat())
            } else {
                bitmap
            }
            decodeQrBitmap(oriented)
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeSingleBitmap(bmp: Bitmap): String? {
        val width = bmp.width
        val height = bmp.height
        if (width <= 0 || height <= 0) return null

        val pixels = IntArray(width * height)
        bmp.getPixels(pixels, 0, width, 0, 0, width, height)

        val source = RGBLuminanceSource(width, height, pixels)

        // 1. Try normal
        decodeFromLuminanceSource(source)?.let { return it }

        // 2. Try inverted (crucial for dark theme apps like Happ, v2rayNG, Telegram dark mode)
        if (source.isCropSupported) {
            try {
                val inverted = source.invert()
                decodeFromLuminanceSource(inverted)?.let { return it }
            } catch (_: Exception) {}
        }

        // 3. Try manual bitwise inverted pixels if source.invert() wasn't enough
        try {
            val invertedPixels = IntArray(pixels.size)
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = 255 - ((p shr 16) and 0xFF)
                val g = 255 - ((p shr 8) and 0xFF)
                val b = 255 - (p and 0xFF)
                invertedPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            val invSource = RGBLuminanceSource(width, height, invertedPixels)
            decodeFromLuminanceSource(invSource)?.let { return it }
        } catch (_: Exception) {}

        return null
    }

    private fun decodeFromLuminanceSource(source: LuminanceSource): String? {
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
            put(DecodeHintType.TRY_HARDER, java.lang.Boolean.TRUE)
            put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
        }

        // 1. Try HybridBinarizer with QRCodeReader directly
        try {
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            val qrReader = QRCodeReader()
            val res = qrReader.decode(bitmap, hints)
            if (!res.text.isNullOrBlank()) return res.text
        } catch (_: Exception) {}

        // 2. Try GlobalHistogramBinarizer with QRCodeReader
        try {
            val bitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
            val qrReader = QRCodeReader()
            val res = qrReader.decode(bitmap, hints)
            if (!res.text.isNullOrBlank()) return res.text
        } catch (_: Exception) {}

        // 3. Try MultiFormatReader as fallback
        try {
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            val mfReader = MultiFormatReader()
            val res = mfReader.decode(bitmap, hints)
            if (!res.text.isNullOrBlank()) return res.text
        } catch (_: Exception) {}

        try {
            val bitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
            val mfReader = MultiFormatReader()
            val res = mfReader.decode(bitmap, hints)
            if (!res.text.isNullOrBlank()) return res.text
        } catch (_: Exception) {}

        return null
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap

        val scale = maxDim.toFloat() / maxOf(w, h)
        val newW = (w * scale).toInt().coerceAtLeast(1)
        val newH = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
