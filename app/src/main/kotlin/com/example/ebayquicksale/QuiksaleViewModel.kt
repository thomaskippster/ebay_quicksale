package com.example.ebayquicksale

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

sealed interface QuiksaleUiState {
    object Idle : QuiksaleUiState
    object Loading : QuiksaleUiState
    data class Success(
        val title: String,
        val keywords: String,
        val htmlContent: String
    ) : QuiksaleUiState
    data class Error(val message: String) : QuiksaleUiState
}

class QuiksaleViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<QuiksaleUiState>(QuiksaleUiState.Idle)
    val uiState: StateFlow<QuiksaleUiState> = _uiState.asStateFlow()

    fun generateDraft(bitmap: Bitmap, notes: String, apiKey: String) {
        if (apiKey.isBlank()) {
            _uiState.value = QuiksaleUiState.Error("API Key fehlt. Bitte in den Einstellungen eintragen.")
            return
        }

        _uiState.value = QuiksaleUiState.Loading

        viewModelScope.launch {
            try {
                val config = generationConfig {
                    responseMimeType = "application/json"
                }

                val generativeModel = GenerativeModel(
                    modelName = "gemini-1.5-flash",
                    apiKey = apiKey,
                    generationConfig = config
                )

                val prompt = """
                    Du bist ein eBay-Experte. Analysiere das Bild und die Notizen: '$notes'. 
                    Antworte AUSSCHLIESSLICH mit einem JSON-Objekt, das exakt folgende Keys enthält: 
                    'title' (ein passender eBay-Titel, max 80 Zeichen), 
                    'category_keywords' (2-3 Suchbegriffe für die eBay-Kategoriefindung) und 
                    'html_description' (die detaillierte Artikelbeschreibung formatiert in HTML).
                """.trimIndent()

                val inputContent = content {
                    image(bitmap)
                    text(prompt)
                }

                val response = generativeModel.generateContent(inputContent)
                val responseText = response.text

                if (responseText != null) {
                    val json = JSONObject(responseText)
                    val title = json.optString("title", "Kein Titel generiert")
                    val keywords = json.optString("category_keywords", "")
                    val htmlDescription = json.optString("html_description", "Keine Beschreibung generiert")

                    _uiState.value = QuiksaleUiState.Success(
                        title = title,
                        keywords = keywords,
                        htmlContent = htmlDescription
                    )
                } else {
                    _uiState.value = QuiksaleUiState.Error("Keine Antwort von der KI erhalten.")
                }
            } catch (e: Exception) {
                _uiState.value = QuiksaleUiState.Error("Fehler: ${e.localizedMessage ?: "Unbekannter Fehler"}")
            }
        }
    }
}
