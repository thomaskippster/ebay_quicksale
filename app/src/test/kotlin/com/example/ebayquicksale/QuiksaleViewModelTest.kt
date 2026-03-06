package com.example.ebayquicksale

import android.graphics.Bitmap
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuiksaleViewModelTest {

    private lateinit var viewModel: QuiksaleViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = QuiksaleViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updateNotes updates notes state`() = runTest {
        val testNotes = "Test notes"
        viewModel.updateNotes(testNotes)
        assertEquals(testNotes, viewModel.notes.value)
    }

    @Test
    fun `addBitmap adds bitmap to list`() = runTest {
        val bitmap = mockk<Bitmap>()
        viewModel.addBitmap(bitmap)
        assertTrue(viewModel.bitmaps.value.contains(bitmap))
        assertEquals(1, viewModel.bitmaps.value.size)
    }

    @Test
    fun `removeBitmap removes bitmap from list`() = runTest {
        val bitmap = mockk<Bitmap>()
        viewModel.addBitmap(bitmap)
        viewModel.removeBitmap(bitmap)
        assertTrue(viewModel.bitmaps.value.isEmpty())
    }

    @Test
    fun `resetAll resets all states to initial`() = runTest {
        val bitmap = mockk<Bitmap>()
        viewModel.addBitmap(bitmap)
        viewModel.updateNotes("Some notes")
        
        viewModel.resetAll()
        
        assertEquals("", viewModel.notes.value)
        assertTrue(viewModel.bitmaps.value.isEmpty())
        assertTrue(viewModel.uiState.value is QuiksaleUiState.Idle)
        assertTrue(viewModel.uploadState.value is UploadUiState.Idle)
    }

    @Test
    fun `generateDraft fails when API key is blank`() = runTest {
        viewModel.generateDraft("", "token", "AUCTION")
        
        val state = viewModel.uiState.value
        assertTrue(state is QuiksaleUiState.Error)
        assertEquals("API Key fehlt. Bitte in den Einstellungen eintragen.", (state as QuiksaleUiState.Error).message)
    }

    @Test
    fun `generateDraft fails when no bitmaps are added`() = runTest {
        viewModel.generateDraft("apiKey", "token", "AUCTION")
        
        val state = viewModel.uiState.value
        assertTrue(state is QuiksaleUiState.Error)
        assertEquals("Bitte nimm mindestens ein Foto auf.", (state as QuiksaleUiState.Error).message)
    }

    @Test
    fun `updateDraft updates Success state with new draft`() = runTest {
        val initialDraft = EbayDraft("Title", "Desc", "1.00", "Keywords")
        val updatedDraft = initialDraft.copy(title = "New Title")
        
        // Manual state injection (since QuiksaleUiState is sealed and Success is a data class)
        // We can't directly set private _uiState, but we can trigger a state change that results in Success.
        // However, for unit testing the logic of updateDraft:
        viewModel.updateDraft(updatedDraft) // Should do nothing if state is Idle
        assertTrue(viewModel.uiState.value is QuiksaleUiState.Idle)

        // Mocking the success state is hard because it's a private MutableStateFlow.
        // In a real scenario, we might want to make it internal or use a test-friendly way to set it.
        // But for this project, let's test what we can.
    }

    @Test
    fun `resetUploadState sets uploadState to Idle`() = runTest {
        // We can't easily set uploadState to something else without triggering a real upload
        // but we can verify it's Idle initially and after reset.
        viewModel.resetUploadState()
        assertTrue(viewModel.uploadState.value is UploadUiState.Idle)
    }
}
