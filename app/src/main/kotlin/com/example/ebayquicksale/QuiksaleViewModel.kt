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

data class EbayDraft(
    val title: String,
    val descriptionHtml: String,
    val suggestedPrice: Double,
    val categoryKeywords: String
)

sealed interface QuiksaleUiState {
    object Idle : QuiksaleUiState
    object Loading : QuiksaleUiState
    data class Success(val draft: EbayDraft) : QuiksaleUiState
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
                    Du bist ein professioneller eBay-Verkäufer. Analysiere das Bild und die Notizen: '$notes'. 
                    Antworte AUSSCHLIESSLICH mit einem validen JSON-Objekt, das diese exakten Keys enthält: 
                    "title" (max. 80 Zeichen), 
                    "description_html" (die ausführliche Beschreibung in HTML), 
                    "suggested_price" (ein realistischer Startpreis als Float) und 
                    "category_keywords" (2-3 Suchbegriffe, um die eBay-Kategorie zu finden).
                """.trimIndent()

                val inputContent = content {
                    image(bitmap)
                    text(prompt)
                }

                val response = generativeModel.generateContent(inputContent)
                val responseText = response.text

                if (responseText != null) {
                    val json = JSONObject(responseText)
                    val draft = EbayDraft(
                        title = json.optString("title", "Kein Titel"),
                        descriptionHtml = json.optString("description_html", ""),
                        suggestedPrice = json.optDouble("suggested_price", 1.0),
                        categoryKeywords = json.optString("category_keywords", "")
                    )
                    _uiState.value = QuiksaleUiState.Success(draft)
                } else {
                    _uiState.value = QuiksaleUiState.Error("Keine Antwort von der KI erhalten.")
                }
            } catch (e: Exception) {
                _uiState.value = QuiksaleUiState.Error("Fehler: ${e.localizedMessage ?: "Unbekannter Fehler"}")
            }
        }
    }
}
