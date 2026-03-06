package com.example.ebayquicksale

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ebayquicksale.api.*
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class EbayDraft(
    val title: String,
    val descriptionHtml: String,
    val suggestedPrice: String,
    val categoryKeywords: String,
    val categoryId: String = "",
    val condition: String = "USED_GOOD",
    val sku: String = ""
)

sealed interface QuiksaleUiState {
    object Idle : QuiksaleUiState
    object Loading : QuiksaleUiState
    data class Success(val draft: EbayDraft) : QuiksaleUiState
    data class Error(val message: String) : QuiksaleUiState
}

sealed interface UploadUiState {
    object Idle : UploadUiState
    data class Loading(val message: String) : UploadUiState
    data class Success(val offerId: String) : UploadUiState
    data class Error(val message: String) : UploadUiState
}

class QuiksaleViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<QuiksaleUiState>(QuiksaleUiState.Idle)
    val uiState: StateFlow<QuiksaleUiState> = _uiState.asStateFlow()

    private val _uploadState = MutableStateFlow<UploadUiState>(UploadUiState.Idle)
    val uploadState: StateFlow<UploadUiState> = _uploadState.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _bitmaps = MutableStateFlow<List<Bitmap>>(emptyList())
    val bitmaps: StateFlow<List<Bitmap>> = _bitmaps.asStateFlow()

    fun updateNotes(text: String) {
        _notes.value = text
    }

    fun addBitmap(bitmap: Bitmap) {
        _bitmaps.value = _bitmaps.value + bitmap
    }

    fun removeBitmap(bitmap: Bitmap) {
        _bitmaps.value = _bitmaps.value - bitmap
    }

    fun generateDraft(apiKey: String, ebayAccessToken: String?) {
        val currentBitmaps = _bitmaps.value
        val currentNotes = _notes.value

        if (apiKey.isBlank()) {
            _uiState.value = QuiksaleUiState.Error("API Key fehlt. Bitte in den Einstellungen eintragen.")
            return
        }

        if (currentBitmaps.isEmpty()) {
            _uiState.value = QuiksaleUiState.Error("Bitte nimm mindestens ein Foto auf.")
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
                    Du bist ein professioneller eBay-Verkäufer. Analysiere diese Bilder und die Notizen: '$currentNotes'. 
                    Antworte AUSSCHLIESSLICH mit einem validen JSON-Objekt. 
                    Das JSON muss exakt diese Keys enthalten: 
                    "title" (max. 80 Zeichen), 
                    "description_html" (die ausführliche Beschreibung in HTML formatiert), 
                    "suggested_price" (ein realistischer Startpreis als String), 
                    "category_keywords" (2-3 Suchbegriffe, um die eBay-Kategorie zu finden) und
                    "condition" (MUSS exakt einer dieser Werte sein: NEW, LIKE_NEW, USED_EXCELLENT, USED_GOOD, USED_ACCEPTABLE, FOR_PARTS_OR_NOT_WORKING).
                """.trimIndent()

                val inputContent = content {
                    currentBitmaps.forEach { image(it) }
                    text(prompt)
                }

                val response = generativeModel.generateContent(inputContent)
                val responseText = response.text

                if (responseText != null) {
                    val cleanJson = responseText.replace("```json", "").replace("```", "").trim()
                    
                    val json = JSONObject(cleanJson)
                    var htmlDesc = json.optString("description_html", "")
                    // Rechtlichen Hinweis anhängen
                    htmlDesc += RECHTLICHER_HINWEIS

                    var draft = EbayDraft(
                        title = json.optString("title", "Kein Titel").take(80),
                        descriptionHtml = htmlDesc,
                        suggestedPrice = json.optString("suggested_price", "1.00"),
                        categoryKeywords = json.optString("category_keywords", ""),
                        condition = json.optString("condition", "USED_GOOD"),
                        sku = "QUIKSALE-" + UUID.randomUUID().toString().take(8)
                    )

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
        bitmaps: List<Bitmap>,
        token: String,
        imgurId: String,
        defaultPrice: String,
        merchantLocation: String,
        paymentId: String,
        fulfillmentId: String,
        returnId: String,
        startTimeText: String
    ) {
        viewModelScope.launch {
            try {
                // 1. Bilder zu Imgur hochladen
                _uploadState.value = UploadUiState.Loading("Bilder werden zu Imgur hochgeladen...")
                val imageUrls = uploadImagesToImgur(bitmaps, imgurId)
                if (imageUrls.isEmpty()) {
                    _uploadState.value = UploadUiState.Error("Fehler beim Bilder-Upload zu Imgur. Prüfe die Imgur Client ID.")
                    return@launch
                }

                // 2. Inventory Item erstellen
                _uploadState.value = UploadUiState.Loading("Inventar-Artikel wird bei eBay erstellt...")
                val inventoryRequest = InventoryItemRequest(
                    product = Product(
                        title = draft.title,
                        description = draft.descriptionHtml,
                        imageUris = imageUrls
                    ),
                    condition = draft.condition,
                    availability = Availability(
                        shipToLocationAvailability = ShipToLocationAvailability(1),
                        merchantLocationKey = if (merchantLocation.isNotBlank()) merchantLocation else null
                    )
                )

                val inventoryResponse = EbayRetrofitClient.ebayApiService.createOrReplaceInventoryItem(
                    sku = draft.sku,
                    authorization = "Bearer $token",
                    body = inventoryRequest
                )

                if (inventoryResponse.isSuccessful) {
                    // 3. Offer erstellen
                    _uploadState.value = UploadUiState.Loading("Auktion wird bei eBay geplant...")
                    
                    val priceValue = if (draft.suggestedPrice.isNotBlank()) {
                        draft.suggestedPrice.replace(",", ".").replace(Regex("[^0-9.]"), "")
                    } else {
                        defaultPrice.replace(",", ".").replace(Regex("[^0-9.]"), "")
                    }

                    val scheduledTime = formatStartTime(startTimeText)

                    val offerRequest = OfferRequest(
                        sku = draft.sku,
                        categoryId = draft.categoryId,
                        pricingSummary = PricingSummary(
                            price = Price(value = priceValue)
                        ),
                        merchantLocationKey = if (merchantLocation.isNotBlank()) merchantLocation else null,
                        listingPolicies = ListingPolicies(
                            fulfillmentPolicyId = fulfillmentId,
                            paymentPolicyId = paymentId,
                            returnPolicyId = returnId
                        ),
                        scheduledStartTime = scheduledTime
                    )

                    val offerResponse = EbayRetrofitClient.ebayApiService.createOffer(
                        authorization = "Bearer $token",
                        body = offerRequest
                    )

                    if (offerResponse.isSuccessful) {
                        val offerId = offerResponse.body()?.offerId ?: "Unbekannt"
                        _uploadState.value = UploadUiState.Success(offerId)
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

    private fun formatStartTime(text: String): String? {
        if (text.isBlank()) return null
        
        // Einfache Logik: Nächster Donnerstag 18:00 Uhr UTC
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.THURSDAY) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        calendar.set(Calendar.HOUR_OF_DAY, 18)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // Sicherheitscheck: Wenn der Zeitpunkt in der Vergangenheit liegt, nimm den nächsten Donnerstag
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 7)
        }
        
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(calendar.time)
    }

    private suspend fun uploadImagesToImgur(bitmaps: List<Bitmap>, imgurId: String): List<String> {
        if (imgurId.isBlank()) return emptyList()
        
        return bitmaps.map { bitmap ->
            viewModelScope.async {
                try {
                    val resizedBitmap = ImageUtils.resizeBitmap(bitmap)
                    val stream = ByteArrayOutputStream()
                    resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                    val byteArray = stream.toByteArray()
                    val requestBody = byteArray.toRequestBody("image/jpeg".toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("image", "upload.jpg", requestBody)

                    val response = ImgurRetrofitClient.imgurApiService.uploadImage(
                        authorization = "Client-ID $imgurId",
                        image = body
                    )

                    if (response.isSuccessful) {
                        response.body()?.data?.link
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull()
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

    fun resetAll() {
        _uiState.value = QuiksaleUiState.Idle
        _uploadState.value = UploadUiState.Idle
        _notes.value = ""
        _bitmaps.value = emptyList()
    }

    fun updateDraft(newDraft: EbayDraft) {
        val currentState = _uiState.value
        if (currentState is QuiksaleUiState.Success) {
            _uiState.value = QuiksaleUiState.Success(newDraft)
        }
    }

    companion object {
        private const val RECHTLICHER_HINWEIS = "<br><br><b>Rechtlicher Hinweis:</b> Es handelt sich um einen Privatverkauf. Der Verkauf erfolgt unter Ausschluss jeglicher Sachmängelhaftung. Die Haftung auf Schadenersatz wegen Verletzungen von Gesundheit, Körper oder Leben und grob fahrlässiger und/oder vorsätzlicher Verletzungen meiner Pflichten als Verkäufer bleibt davon unberührt. Keine Rücknahme."
    }
}
