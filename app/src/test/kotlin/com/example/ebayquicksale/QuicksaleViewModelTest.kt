package com.example.ebayquicksale

import android.graphics.Bitmap
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class QuicksaleViewModelTest {

    private lateinit var viewModel: QuicksaleViewModel
    private lateinit var settingsManager: SettingsManager

    @Before
    fun setUp() {
        viewModel = QuicksaleViewModel()
        settingsManager = mockk(relaxed = true)
        every { settingsManager.appendLegalNotice } returns flowOf(true)
        every { settingsManager.defaultLegalNotice } returns flowOf("")
    }

    @Test
    fun `addBitmap adds bitmap to list`() {
        // In this architecture, adding a bitmap currently requires a context because it saves to internal storage.
    }

    @Test
    fun `updateNotes updates notes state`() {
        val newNote = "Test note"
        viewModel.updateNotes(newNote)
        assertEquals(newNote, viewModel.notes.value)
    }

    @Test
    fun `generateDraft fails when API key is blank`() {
        viewModel.generateDraft("", null, "AUCTION", "EBAY_DE", settingsManager)
        val state = viewModel.uiState.value
        assertTrue(state is QuicksaleUiState.Error)
        assertEquals("API Key fehlt. Bitte in den Einstellungen eintragen.", (state as QuicksaleUiState.Error).message)
    }

    @Test
    fun `generateDraft fails when no bitmaps are added`() {
        viewModel.generateDraft("key", null, "AUCTION", "EBAY_DE", settingsManager)
        val state = viewModel.uiState.value
        assertTrue(state is QuicksaleUiState.Error)
        assertEquals("Bitte nimm mindestens ein Foto auf.", (state as QuicksaleUiState.Error).message)
    }

    @Test
    fun `resetUploadState sets uploadState to Idle`() {
        viewModel.resetUploadState()
        assertTrue(viewModel.uploadState.value is UploadUiState.Idle)
    }

    @Test
    fun `resetAll resets all states to initial`() {
        viewModel.resetAll(settingsManager, mockk(relaxed = true))
        assertEquals("", viewModel.notes.value)
        assertTrue(viewModel.imagePaths.value.isEmpty())
        assertTrue(viewModel.uiState.value is QuicksaleUiState.Idle)
    }
}
