package com.example.ebayquicksale

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ebayquicksale.api.*
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

data class EbayDraft(
    val title: String,
    val descriptionHtml: String,
    val suggestedPrice: String,
    val categoryKeywords: String,
    val categoryId: String = "",
    val condition: String = "USED_GOOD"
)

sealed interface QuiksaleUiState {
    object Idle : QuiksaleUiState
    object Loading : QuiksaleUiState
    data class Success(val draft: EbayDraft) : QuiksaleUiState
    data class Error(val message: String) : QuiksaleUiState
}

sealed interface UploadUiState {
    object Idle : UploadUiState
    object Loading : UploadUiState
    object Success : UploadUiState
    data class Error(val message: String) : UploadUiState
}

class QuiksaleViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<QuiksaleUiState>(QuiksaleUiState.Idle)
    val uiState: StateFlow<QuiksaleUiState> = _uiState.asStateFlow()

    private val _uploadState = MutableStateFlow<UploadUiState>(UploadUiState.Idle)
    val uploadState: StateFlow<UploadUiState> = _uploadState.asStateFlow()

    fun generateDraft(bitmap: Bitmap, notes: String, apiKey: String, ebayAccessToken: String?) {
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
                    Antworte AUSSCHLIESSLICH mit einem validen JSON-Objekt. 
                    Das JSON muss exakt diese Keys enthalten: 
                    "title" (max. 80 Zeichen), 
                    "description_html" (die ausführliche Beschreibung in HTML formatiert), 
                    "suggested_price" (ein realistischer Startpreis als String), 
                    "category_keywords" (2-3 Suchbegriffe, um die eBay-Kategorie zu finden) und
                    "condition" (MUSS exakt einer dieser Werte sein: NEW, LIKE_NEW, USED_EXCELLENT, USED_GOOD, USED_ACCEPTABLE, FOR_PARTS_OR_NOT_WORKING).
                """.trimIndent()

                val inputContent = content {
                    image(bitmap)
                    text(prompt)
                }

                val response = generativeModel.generateContent(inputContent)
                val responseText = response.text

                if (responseText != null) {
                    val json = JSONObject(responseText)
                    var draft = EbayDraft(
                        title = json.optString("title", "Kein Titel"),
                        descriptionHtml = json.optString("description_html", ""),
                        suggestedPrice = json.optString("suggested_price", "1.00"),
                        categoryKeywords = json.optString("category_keywords", ""),
                        condition = json.optString("condition", "USED_GOOD")
                    )

                    // eBay Kategorie-Vorschläge abrufen, falls Token vorhanden
                    if (!ebayAccessToken.isNullOrBlank() && draft.categoryKeywords.isNotBlank()) {
                        try {
                            val ebayResponse = EbayRetrofitClient.ebayApiService.getCategorySuggestions(
                                query = draft.categoryKeywords,
                                authorization = "Bearer $ebayAccessToken"
                            )
                            val firstCategory = ebayResponse.categorySuggestions?.firstOrNull()?.category
                            if (firstCategory != null) {
                                draft = draft.copy(categoryId = firstCategory.categoryId)
                            }
                        } catch (e: Exception) {
                            if (e.message?.contains("401") == true) {
                                _uiState.value = QuiksaleUiState.Error("eBay Token abgelaufen. Bitte neu einloggen.")
                                return@launch
                            }
                        }
                    }

                    _uiState.value = QuiksaleUiState.Success(draft)
                } else {
                    _uiState.value = QuiksaleUiState.Error("Keine Antwort von der KI erhalten.")
                }
            } catch (e: Exception) {
                _uiState.value = QuiksaleUiState.Error("Fehler: ${e.localizedMessage ?: "Unbekannter Fehler"}")
            }
        }
    }

    fun uploadToEbay(
        draft: EbayDraft,
        token: String,
        defaultPrice: String,
        merchantLocation: String,
        paymentPolicy: String,
        fulfillmentPolicy: String,
        returnPolicy: String
    ) {
        _uploadState.value = UploadUiState.Loading

        viewModelScope.launch {
            try {
                val sku = "QUIKSALE-" + UUID.randomUUID().toString().take(8)
                
                // 1. Inventory Item erstellen
                val inventoryRequest = InventoryItemRequest(
                    product = Product(
                        title = draft.title,
                        description = draft.descriptionHtml
                    ),
                    condition = draft.condition
                )

                val inventoryResponse = EbayRetrofitClient.ebayApiService.createOrReplaceInventoryItem(
                    sku = sku,
                    authorization = "Bearer $token",
                    body = inventoryRequest
                )

                if (inventoryResponse.isSuccessful) {
                    // 2. Offer erstellen
                    val priceValue = if (draft.suggestedPrice.isNotBlank()) {
                        draft.suggestedPrice.replace(Regex("[^0-9.]"), "")
                    } else {
                        defaultPrice
                    }.replace(",", ".")

                    val offerRequest = OfferRequest(
                        sku = sku,
                        categoryId = draft.categoryId,
                        pricingSummary = PricingSummary(
                            price = Price(value = priceValue)
                        ),
                        merchantLocationKey = if (merchantLocation.isNotBlank()) merchantLocation else null,
                        listingPolicies = ListingPolicies(
                            fulfillmentPolicyId = fulfillmentPolicy,
                            paymentPolicyId = paymentPolicy,
                            returnPolicyId = returnPolicy
                        )
                    )

                    val offerResponse = EbayRetrofitClient.ebayApiService.createOffer(
                        authorization = "Bearer $token",
                        body = offerRequest
                    )

                    if (offerResponse.isSuccessful) {
                        _uploadState.value = UploadUiState.Success
                    } else {
                        val errorMsg = parseEbayError(offerResponse.errorBody()?.string())
                        _uploadState.value = UploadUiState.Error("eBay Fehler (Offer): $errorMsg")
                    }
                } else {
                    val errorMsg = parseEbayError(inventoryResponse.errorBody()?.string())
                    _uploadState.value = UploadUiState.Error("eBay Fehler (Inventory): $errorMsg")
                }
            } catch (e: Exception) {
                _uploadState.value = UploadUiState.Error("Upload-Fehler: ${e.localizedMessage ?: "Unbekannter Fehler"}")
            }
        }
    }

    private fun parseEbayError(errorJson: String?): String {
        if (errorJson.isNullOrBlank()) return "Unbekannter API-Fehler"
        return try {
            val json = JSONObject(errorJson)
            val errors = json.optJSONArray("errors")
            if (errors != null && errors.length() > 0) {
                val firstError = errors.getJSONObject(0)
                val message = firstError.optString("message")
                val longMessage = firstError.optString("longMessage")
                if (longMessage.isNotBlank()) longMessage else message
            } else {
                errorJson
            }
        } catch (e: Exception) {
            errorJson
        }
    }
    
    fun resetUploadState() {
        _uploadState.value = UploadUiState.Idle
    }
}
