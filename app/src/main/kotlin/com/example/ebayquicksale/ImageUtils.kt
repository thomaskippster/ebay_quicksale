package com.example.ebayquicksale

import android.content.Context
import android.graphics.Bitmap
import java.io.File

object ImageUtils {
    /**
     * Skaliert ein Bitmap proportional herunter, falls Breite oder Höhe maxSize überschreiten.
     */
    fun resizeBitmap(bitmap: Bitmap): Bitmap {
        val maxSize = 1600
        val minSize = 1000
        val width = bitmap.width
        val height = bitmap.height

        val ratio = width.toFloat() / height.toFloat()

        var newWidth = width
        var newHeight = height

        if (width > maxSize || height > maxSize) {
            if (width > height) {
                newWidth = maxSize
                newHeight = (maxSize / ratio).toInt()
            } else {
                newHeight = maxSize
                newWidth = (maxSize * ratio).toInt()
            }
        } else if (width < minSize && height < minSize) {
            // Upscaling als Notlösung für zu kleine Bilder
            if (width > height) {
                newWidth = minSize
                newHeight = (minSize / ratio).toInt()
            } else {
                newHeight = minSize
                newWidth = (minSize * ratio).toInt()
            }
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    /**
     * Löscht alle temporären Bilder aus dem Cache-Verzeichnis, die mit "JPEG_" beginnen.
     */
    /**
     * Speichert ein Bitmap im internen Speicher der App und gibt den absoluten Pfad zurück.
     */
    fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap): String? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.getDefault()).format(Date())
        val fileName = "QS_IMG_$timeStamp.jpg"
        val file = File(context.filesDir, fileName)
        
        return try {
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            stream.flush()
            stream.close()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Löscht alle von der App im internen Speicher erstellten Bilder.
     */
    fun clearInternalImageStorage(context: Context) {
        try {
            val files = context.filesDir.listFiles()
            files?.forEach { file ->
                if (file.name.startsWith("QS_IMG_")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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
