package com.example.ebayquicksale

import android.content.Context
import android.graphics.Bitmap
import java.io.File

object ImageUtils {
    /**
     * Skaliert ein Bitmap proportional herunter, falls Breite oder Höhe maxSize überschreiten.
     */
    fun resizeBitmap(bitmap: Bitmap, maxSize: Int = 1024): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxSize && height <= maxSize) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int

        if (width > height) {
            newWidth = maxSize
            newHeight = (maxSize / ratio).toInt()
        } else {
            newHeight = maxSize
            newWidth = (maxSize * ratio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Löscht alle temporären Bilder aus dem Cache-Verzeichnis, die mit "JPEG_" beginnen.
     */
    fun clearImageCache(context: Context) {
        try {
            val cacheDir = context.cacheDir
            val files = cacheDir.listFiles()
            files?.forEach { file ->
                if (file.name.startsWith("JPEG_")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
