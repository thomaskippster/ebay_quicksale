package com.example.ebayquicksale

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ebayquicksale.api.*
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.gson.Gson
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
    val sku: String = "",
    val listingFormat: String = "AUCTION",
    val brand: String = "Markenlos",
    val mpn: String = "Nicht zutreffend"
)

sealed interface QuiksaleUiState {
    object Idle : QuiksaleUiState
    object Loading : QuiksaleUiState
    data class Success(val draft: EbayDraft) : QuiksaleUiState
    data class Error(val message: String) : QuiksaleUiState
}

sealed interface UploadUiState {
    object Idle : UploadUiState
    data class Loading(val message: String, val progress: Float = 0f) : UploadUiState
    data class Success(val listingId: String) : UploadUiState
    data class Error(val message: String) : UploadUiState
}

class QuiksaleViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<QuiksaleUiState>(QuiksaleUiState.Idle)
    val uiState: StateFlow<QuiksaleUiState> = _uiState.asStateFlow()

    private val _uploadState = MutableStateFlow<UploadUiState>(UploadUiState.Idle)
    val uploadState: StateFlow<UploadUiState> = _uploadState.asStateFlow()

    private val _isFetchingSettings = MutableStateFlow(false)
    val isFetchingSettings: StateFlow<Boolean> = _isFetchingSettings.asStateFlow()

    // Listen für Dropdowns in den Einstellungen
    private val _fulfillmentPolicies = MutableStateFlow<List<PolicyInfo>>(emptyList())
    val fulfillmentPolicies: StateFlow<List<PolicyInfo>> = _fulfillmentPolicies.asStateFlow()

    private val _paymentPolicies = MutableStateFlow<List<PolicyInfo>>(emptyList())
    val paymentPolicies: StateFlow<List<PolicyInfo>> = _paymentPolicies.asStateFlow()

    private val _returnPolicies = MutableStateFlow<List<PolicyInfo>>(emptyList())
    val returnPolicies: StateFlow<List<PolicyInfo>> = _returnPolicies.asStateFlow()

    private val _locations = MutableStateFlow<List<LocationInfo>>(emptyList())
    val locations: StateFlow<List<LocationInfo>> = _locations.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _bitmaps = MutableStateFlow<List<Bitmap>>(emptyList())
    val bitmaps: StateFlow<List<Bitmap>> = _bitmaps.asStateFlow()

    private val gson = Gson()

    fun updateNotes(text: String) {
        _notes.value = text
    }

    fun addBitmap(bitmap: Bitmap) {
        _bitmaps.value = _bitmaps.value + bitmap
    }

    fun removeBitmap(bitmap: Bitmap) {
        _bitmaps.value = _bitmaps.value - bitmap
    }

    fun loadDraftFromStorage(settingsManager: SettingsManager) {
        viewModelScope.launch {
            settingsManager.currentDraftJson.collect { json ->
                if (json != null && _uiState.value is QuiksaleUiState.Idle) {
                    try {
                        val draft = gson.fromJson(json, EbayDraft::class.java)
                        _uiState.value = QuiksaleUiState.Success(draft)
                    } catch (e: Exception) {
                        // Ignorieren falls ungültig
                    }
                }
            }
        }
    }

    fun generateDraft(apiKey: String, ebayAccessToken: String?, defaultListingFormat: String, settingsManager: SettingsManager) {
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
                    "category_keywords" (2-3 Suchbegriffe, um die eBay-Kategorie zu finden),
                    "condition" (MUSS exakt einer dieser Werte sein: NEW, LIKE_NEW, USED_EXCELLENT, USED_GOOD, USED_ACCEPTABLE, FOR_PARTS_OR_NOT_WORKING),
                    "brand" (die Marke des Artikels, falls unbekannt 'Markenlos'),
                    "mpn" (Herstellernummer, falls unbekannt 'Nicht zutreffend').
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
                    
                    // HTML-Sicherheit: Bereinigung der htmlDesc
                    var htmlDesc = json.optString("description_html", "")
                        .replace("```html", "")
                        .replace("```", "")
                        .trim()
                        .replace(Regex("^\\s*[*\\-]\\s+"), "") // Entfernt führende Markdown-Bullets falls vorhanden
                    
                    // Rechtlichen Hinweis anhängen
                    htmlDesc += RECHTLICHER_HINWEIS

                    // SKU-Generierung: Präfix + Datum/Zeit + Kurze UUID für garantierte Eindeutigkeit
                    val timeStamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                    val shortUuid = UUID.randomUUID().toString().take(4)
                    
                    var draft = EbayDraft(
                        title = json.optString("title", "Kein Titel"),
                        descriptionHtml = htmlDesc,
                        suggestedPrice = json.optString("suggested_price", "1.00"),
                        categoryKeywords = json.optString("category_keywords", ""),
                        condition = json.optString("condition", "USED_GOOD"),
                        sku = "QS-$timeStamp-$shortUuid",
                        listingFormat = defaultListingFormat,
                        brand = json.optString("brand", "Markenlos"),
                        mpn = json.optString("mpn", "Nicht zutreffend")
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
                    settingsManager.saveCurrentDraft(gson.toJson(draft))
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
        defaultPrice: String,
        merchantLocation: String,
        paymentId: String,
        fulfillmentId: String,
        returnId: String,
        startTimeText: String,
        settingsManager: SettingsManager
    ) {
        if (bitmaps.size > 12) {
            _uploadState.value = UploadUiState.Error("eBay erlaubt maximal 12 Bilder. Bitte entferne einige Fotos.")
            return
        }

        viewModelScope.launch {
            try {
                // 1. Bilder zum eBay Picture Service (EPS) hochladen mit Fortschritt
                val totalImages = bitmaps.size
                val imageUrls = mutableListOf<String>()
                
                for (i in bitmaps.indices) {
                    val currentNum = i + 1
                    _uploadState.value = UploadUiState.Loading(
                        message = "Bild $currentNum von $totalImages wird zu eBay übertragen...",
                        progress = i.toFloat() / totalImages
                    )
                    
                    val url = uploadSingleImage(bitmaps[i], token)
                    if (url != null) {
                        imageUrls.add(url)
                    }
                }

                if (imageUrls.isEmpty()) {
                    _uploadState.value = UploadUiState.Error("Fehler beim Bilder-Upload zu eBay. Bitte prüfe deine Verbindung.")
                    return@launch
                }

                // 2. Inventory Item erstellen mit Aspects (Marke & MPN)
                _uploadState.value = UploadUiState.Loading("Inventar-Artikel wird erstellt...", 0.9f)
                val inventoryRequest = InventoryItemRequest(
                    product = Product(
                        title = draft.title.take(80),
                        description = draft.descriptionHtml,
                        imageUris = imageUrls,
                        aspects = mapOf(
                            "Brand" to listOf(if (draft.brand.isNotBlank()) draft.brand else "Markenlos"),
                            "MPN" to listOf(if (draft.mpn.isNotBlank()) draft.mpn else "Nicht zutreffend")
                        )
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
                    _uploadState.value = UploadUiState.Loading("Angebot wird generiert...", 0.95f)
                    
                    val priceValue = sanitizePrice(if (draft.suggestedPrice.isNotBlank()) draft.suggestedPrice else defaultPrice)

                    // Dynamische Dauer bestimmen
                    val duration = if (draft.listingFormat == "FIXED_PRICE") "GTC" else "DAYS_7"
                    
                    // Startzeit-Logik
                    val finalStartTime = if (draft.listingFormat == "AUCTION") formatStartTime(startTimeText) else null

                    val offerRequest = OfferRequest(
                        sku = draft.sku,
                        categoryId = draft.categoryId,
                        format = draft.listingFormat,
                        listingDuration = duration,
                        pricingSummary = PricingSummary(
                            price = Price(value = priceValue)
                        ),
                        merchantLocationKey = if (merchantLocation.isNotBlank()) merchantLocation else null,
                        listingPolicies = ListingPolicies(
                            fulfillmentPolicyId = fulfillmentId,
                            paymentPolicyId = paymentId,
                            returnPolicyId = returnId
                        ),
                        scheduledStartTime = finalStartTime
                    )

                    val offerResponse = EbayRetrofitClient.ebayApiService.createOffer(
                        authorization = "Bearer $token",
                        body = offerRequest
                    )

                    if (offerResponse.isSuccessful) {
                        val offerId = offerResponse.body()?.offerId ?: ""
                        if (offerId.isNotBlank()) {
                            _uploadState.value = UploadUiState.Loading("Angebot wird veröffentlicht...", 0.99f)
                            val publishResponse = EbayRetrofitClient.ebayApiService.publishOffer(
                                offerId = offerId,
                                authorization = "Bearer $token"
                            )
                            
                            if (publishResponse.isSuccessful) {
                                val listingId = publishResponse.body()?.listingId ?: "Unbekannt"
                                _uploadState.value = UploadUiState.Success(listingId)
                                settingsManager.saveCurrentDraft(null) // Erfolg -> Entwurf löschen
                            } else {
                                val errorMsg = parseEbayError(publishResponse.errorBody()?.string())
                                _uploadState.value = UploadUiState.Error("eBay Fehler (Publish): $errorMsg")
                            }
                        } else {
                            _uploadState.value = UploadUiState.Error("Keine OfferId erhalten.")
                        }
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

    /**
     * Ruft eBay-Einstellungen (Standort, Versand, Zahlung, Rückgabe) automatisch ab.
     */
    fun fetchEbaySettings(token: String, settingsManager: SettingsManager, onResult: (String) -> Unit) {
        viewModelScope.launch {
            _isFetchingSettings.value = true
            try {
                val authHeader = "Bearer $token"

                // 1. Merchant Locations laden
                val locationsResponse = EbayRetrofitClient.ebayApiService.getLocations(authHeader)
                val locList = locationsResponse.locations ?: emptyList()
                _locations.value = locList
                locList.firstOrNull()?.let { settingsManager.saveEbayMerchantLocation(it.merchantLocationKey) }

                // 2. Fulfillment Policies laden
                val fulfillmentResponse = EbayRetrofitClient.ebayApiService.getFulfillmentPolicies(authHeader)
                val fullList = fulfillmentResponse.fulfillmentPolicies ?: emptyList()
                _fulfillmentPolicies.value = fullList
                fullList.firstOrNull()?.let { settingsManager.saveEbayFulfillmentPolicy(it.policyId) }

                // 3. Payment Policies laden
                val paymentResponse = EbayRetrofitClient.ebayApiService.getPaymentPolicies(authHeader)
                val payList = paymentResponse.paymentPolicies ?: emptyList()
                _paymentPolicies.value = payList
                payList.firstOrNull()?.let { settingsManager.saveEbayPaymentPolicy(it.policyId) }

                // 4. Return Policies laden
                val returnResponse = EbayRetrofitClient.ebayApiService.getReturnPolicies(authHeader)
                val retList = returnResponse.returnPolicies ?: emptyList()
                _returnPolicies.value = retList
                retList.firstOrNull()?.let { settingsManager.saveEbayReturnPolicy(it.policyId) }

                val summary = "Gefunden: ${locList.size} Standorte, ${fullList.size} Versand, ${payList.size} Zahlung, ${retList.size} Rückgabe."
                onResult("Erfolgreich! $summary")
            } catch (e: Exception) {
                onResult("Fehler beim Abrufen der eBay-Einstellungen: ${e.localizedMessage}")
            } finally {
                _isFetchingSettings.value = false
            }
        }
    }

    private fun formatStartTime(text: String): String? {
        if (text.isBlank() || text.uppercase() == "SOFORT") return null
        
        return try {
            // Versuche das Format yyyy-MM-dd HH:mm zu parsen
            val inputSdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.GERMANY)
            val date = inputSdf.parse(text)
            
            val outputSdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            outputSdf.timeZone = TimeZone.getTimeZone("UTC")
            if (date != null) outputSdf.format(date) else null
        } catch (e: Exception) {
            // Fallback: Nächster Donnerstag 18:00 Uhr UTC (Best-Practice für Auktionen)
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.THURSDAY) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            calendar.set(Calendar.HOUR_OF_DAY, 18)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 7)
            }
            
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.format(calendar.time)
        }
    }

    private fun sanitizePrice(input: String): String {
        // Entferne alles außer Ziffern, Komma und Punkt
        val clean = input.replace(",", ".").replace(Regex("[^0-9.]"), "")
        return try {
            val price = clean.toDouble()
            String.format(Locale.US, "%.2f", price)
        } catch (e: Exception) {
            "1.00" // Fallback
        }
    }

    private suspend fun uploadSingleImage(bitmap: Bitmap, token: String): String? {
        return try {
            val resizedBitmap = ImageUtils.resizeBitmap(bitmap)
            val stream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            val byteArray = stream.toByteArray()
            
            val requestBody = byteArray.toRequestBody("image/jpeg".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", "image.jpg", requestBody)

            val response = EbayRetrofitClient.ebayApiService.uploadPicture(
                authorization = "Bearer $token",
                picture = body
            )

            response.FullSizeInternalURL
        } catch (e: Exception) {
            null
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
                
                // Parameter extrahieren (z.B. Min-Preis, fehlende Felder)
                val parameters = firstError.optJSONArray("parameters")
                val paramsStr = if (parameters != null && parameters.length() > 0) {
                    val pList = mutableListOf<String>()
                    for (i in 0 until parameters.length()) {
                        val p = parameters.getJSONObject(i)
                        val name = p.optString("name")
                        val value = p.optString("value")
                        if (name.isNotBlank() && value.isNotBlank()) pList.add("$name: $value")
                    }
                    " [" + pList.joinToString(", ") + "]"
                } else ""

                val baseMsg = if (longMessage.isNotBlank()) longMessage else message
                baseMsg + paramsStr
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

    fun resetAll(settingsManager: SettingsManager) {
        _uiState.value = QuiksaleUiState.Idle
        _uploadState.value = UploadUiState.Idle
        _notes.value = ""
        _bitmaps.value = emptyList()
        viewModelScope.launch {
            settingsManager.saveCurrentDraft(null)
        }
    }

    fun updateDraft(newDraft: EbayDraft, settingsManager: SettingsManager) {
        val currentState = _uiState.value
        if (currentState is QuiksaleUiState.Success) {
            _uiState.value = QuiksaleUiState.Success(newDraft)
            viewModelScope.launch {
                settingsManager.saveCurrentDraft(gson.toJson(newDraft))
            }
        }
    }

    companion object {
        private const val RECHTLICHER_HINWEIS = "<br><br><b>Rechtlicher Hinweis:</b> Es handelt sich um einen Privatverkauf. Der Verkauf erfolgt unter Ausschluss jeglicher Sachmängelhaftung. Die Haftung auf Schadenersatz wegen Verletzungen von Gesundheit, Körper oder Leben und grob fahrlässiger und/oder vorsätzlicher Verletzungen meiner Pflichten als Verkäufer bleibt davon unberührt. Keine Rücknahme."
    }
}
