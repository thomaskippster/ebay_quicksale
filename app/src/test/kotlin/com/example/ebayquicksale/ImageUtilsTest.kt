package com.example.ebayquicksale

import android.graphics.Bitmap
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageUtilsTest {

    @Test
    fun `resizeBitmap returns original if smaller than maxSize`() {
        val bitmap = mockk<Bitmap>()
        every { bitmap.width } returns 500
        every { bitmap.height } returns 400
        
        val result = ImageUtils.resizeBitmap(bitmap, 1024)
        
        assertEquals(bitmap, result)
    }

    @Test
    fun `resizeBitmap scales down if width exceeds maxSize`() {
        val bitmap = mockk<Bitmap>()
        val scaledBitmap = mockk<Bitmap>()
        
        every { bitmap.width } returns 2000
        every { bitmap.height } returns 1000
        
        mockkStatic(Bitmap::class)
        every { Bitmap.createScaledBitmap(any(), any(), any(), any()) } returns scaledBitmap
        
        val result = ImageUtils.resizeBitmap(bitmap, 1000)
        
        assertEquals(scaledBitmap, result)
        verify { Bitmap.createScaledBitmap(bitmap, 1000, 500, true) }
    }

    @Test
    fun `resizeBitmap scales down if height exceeds maxSize`() {
        val bitmap = mockk<Bitmap>()
        val scaledBitmap = mockk<Bitmap>()
        
        every { bitmap.width } returns 1000
        every { bitmap.height } returns 2000
        
        mockkStatic(Bitmap::class)
        every { Bitmap.createScaledBitmap(any(), any(), any(), any()) } returns scaledBitmap
        
        val result = ImageUtils.resizeBitmap(bitmap, 1000)
        
        assertEquals(scaledBitmap, result)
        verify { Bitmap.createScaledBitmap(bitmap, 500, 1000, true) }
    }
}
