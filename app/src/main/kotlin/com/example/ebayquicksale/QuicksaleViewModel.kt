package com.example.ebayquicksale

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class ItemAspect(val name: String, val value: String)

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
    val mpn: String = "Nicht zutreffend",
    val imagePaths: List<String> = emptyList(),
    val quantity: Int = 1,
    val aspects: Map<String, String> = emptyMap(),
    val bestOfferEnabled: Boolean = false,
    val localizedLegalNotice: String = ""
)

sealed interface QuicksaleUiState {
    object Idle : QuicksaleUiState
    object Loading : QuicksaleUiState
    data class Success(val draft: EbayDraft) : QuicksaleUiState
    data class Error(val message: String) : QuicksaleUiState
}

sealed interface UploadUiState {
    object Idle : UploadUiState
    data class Loading(val message: String, val progress: Float = 0f) : UploadUiState
    data class Success(val listingId: String) : UploadUiState
    data class Error(val message: String) : UploadUiState
}

class QuicksaleViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<QuicksaleUiState>(QuicksaleUiState.Idle)
    val uiState: StateFlow<QuicksaleUiState> = _uiState.asStateFlow()

    private val _uploadState = MutableStateFlow<UploadUiState>(UploadUiState.Idle)
    val uploadState: StateFlow<UploadUiState> = _uploadState.asStateFlow()

    private val _isFetchingSettings = MutableStateFlow(false)
    val isFetchingSettings: StateFlow<Boolean> = _isFetchingSettings.asStateFlow()

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

    private val _imagePaths = MutableStateFlow<List<String>>(emptyList())
    val imagePaths: StateFlow<List<String>> = _imagePaths.asStateFlow()

    private val gson = Gson()

    fun updateNotes(text: String) {
        _notes.value = text
    }

    fun addImage(context: Context, bitmap: Bitmap) {
        viewModelScope.launch {
            val path = ImageUtils.saveBitmapToInternalStorage(context, bitmap)
            if (path != null) {
                _imagePaths.value = _imagePaths.value + path
            }
        }
    }

    fun removeImage(path: String) {
        _imagePaths.value = _imagePaths.value - path
        try {
            File(path).delete()
        } catch (e: Exception) {}
    }

    fun moveImageToFront(path: String) {
        val current = _imagePaths.value.toMutableList()
        if (current.remove(path)) {
            current.add(0, path)
            _imagePaths.value = current
        }
    }

    fun loadDraftFromStorage(settingsManager: SettingsManager) {
        viewModelScope.launch {
            settingsManager.currentDraftJson.collect { json ->
                if (json != null && _uiState.value is QuicksaleUiState.Idle) {
                    try {
                        val draft = gson.fromJson(json, EbayDraft::class.java)
                        _uiState.value = QuicksaleUiState.Success(draft)
                        _imagePaths.value = draft.imagePaths
                    } catch (e: Exception) {}
                }
            }
        }
    }

    fun generateDraft(apiKey: String, ebayAccessToken: String?, defaultListingFormat: String, settingsManager: SettingsManager) {
        val currentPaths = _imagePaths.value
        val currentNotes = _notes.value

        if (apiKey.isBlank()) {
            _uiState.value = QuicksaleUiState.Error("API Key fehlt. Bitte in den Einstellungen eintragen.")
            return
        }

        if (currentPaths.isEmpty()) {
            _uiState.value = QuicksaleUiState.Error("Bitte nimm mindestens ein Foto auf.")
            return
        }

        _uiState.value = QuicksaleUiState.Loading

        viewModelScope.launch {
            try {
                val bitmaps = currentPaths.mapNotNull { path ->
                    try { BitmapFactory.decodeFile(path) } catch (e: Exception) { null }
                }

                if (bitmaps.isEmpty()) {
                    _uiState.value = QuicksaleUiState.Error("Fehler beim Laden der Bilder.")
                    return@launch
                }

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
                    "mpn" (Herstellernummer, falls unbekannt 'Nicht zutreffend'),
                    "quantity" (Integer, immer '1', außer es sind im Bild eindeutig mehrere gleiche Artikel zu sehen),
                    "aspects" (Ein JSON-Objekt/Map mit String-Keys und String-Werten. Identifiziere unbedingt Farbe, Material und Besonderheiten als Aspects, falls erkennbar).
                """.trimIndent()

                val inputContent = content {
                    bitmaps.forEach { image(it) }
                    text(prompt)
                }

                val response = generativeModel.generateContent(inputContent)
                val responseText = response.text

                if (responseText != null) {
                    try {
                        val cleanJson = responseText.replace("```json", "").replace("```", "").trim()
                        val json = JSONObject(cleanJson)
                        var htmlDesc = json.optString("description_html", "")
                            .replace("```html", "")
                            .replace("```", "")
                            .trim()
                            .replace(Regex("^\\s*[*\\-]\\s+"), "")

                        val legalNotice = settingsManager.defaultLegalNotice.first()
                        if (legalNotice.isNotBlank()) {
                            htmlDesc += "<br><br><b>Rechtlicher Hinweis:</b> $legalNotice"
                        } else {
                            htmlDesc += RECHTLICHER_HINWEIS
                        }

                        val timeStamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                        val shortUuid = UUID.randomUUID().toString().take(4)
                        
                        val quantity = json.optInt("quantity", 1)
                        
                        val aspectsMap = mutableMapOf<String, String>()
                        val aspectsJson = json.optJSONObject("aspects")
                        if (aspectsJson != null) {
                            val keys = aspectsJson.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                val value = aspectsJson.optString(key)
                                if (key.isNotBlank() && value.isNotBlank()) {
                                    aspectsMap[key] = value
                                }
                            }
                        }

                        var draft = EbayDraft(
                            title = json.optString("title", "Kein Titel").take(80),
                            descriptionHtml = htmlDesc,
                            suggestedPrice = json.optString("suggested_price", "1.00"),
                            categoryKeywords = json.optString("category_keywords", ""),
                            condition = json.optString("condition", "USED_GOOD"),
                            sku = "QS-$timeStamp-$shortUuid",
                            listingFormat = defaultListingFormat,
                            brand = json.optString("brand", "Markenlos"),
                            mpn = json.optString("mpn", "Nicht zutreffend"),
                            imagePaths = currentPaths,
                            quantity = quantity,
                            aspects = aspectsMap,
                            localizedLegalNotice = legalNotice
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
                            } catch (e: Exception) {}
                        }

                        _uiState.value = QuicksaleUiState.Success(draft)
                        settingsManager.saveCurrentDraft(gson.toJson(draft))
                    } catch (e: Exception) {
                        _uiState.value = QuicksaleUiState.Error("KI-Daten konnten nicht gelesen werden. Bitte erneut versuchen.")
                    }
                } else {
                    _uiState.value = QuicksaleUiState.Error("Keine Antwort von der KI erhalten.")
                }
            } catch (e: Exception) {
                _uiState.value = QuicksaleUiState.Error("Fehler: ${e.localizedMessage ?: "Unbekannter Fehler"}")
            }
        }
    }

    fun uploadToEbay(
        draft: EbayDraft,
        token: String,
        defaultPrice: String,
        merchantLocation: String,
        paymentId: String,
        fulfillmentId: String,
        returnId: String,
        startTimeText: String,
        settingsManager: SettingsManager
    ) {
        val paths = _imagePaths.value
        if (paths.size > 12) {
            _uploadState.value = UploadUiState.Error("eBay erlaubt maximal 12 Bilder. Bitte entferne einige Fotos.")
            return
        }

        viewModelScope.launch {
            try {
                val totalImages = paths.size
                val imageUrls = mutableListOf<String>()
                
                for (i in paths.indices) {
                    val currentNum = i + 1
                    _uploadState.value = UploadUiState.Loading(
                        message = "Bild $currentNum von $totalImages wird zu eBay übertragen...",
                        progress = i.toFloat() / totalImages
                    )
                    
                    val bitmap = try { BitmapFactory.decodeFile(paths[i]) } catch (e: Exception) { null }
                    if (bitmap != null) {
                        val url = uploadSingleImage(bitmap, token)
                        imageUrls.add(url)
                    }
                }

                if (imageUrls.isEmpty()) {
                    _uploadState.value = UploadUiState.Error("Fehler beim Bilder-Upload.")
                    return@launch
                }

                _uploadState.value = UploadUiState.Loading("Inventar-Artikel wird erstellt...", 0.9f)
                
                val finalAspects = mutableMapOf<String, List<String>>()
                finalAspects["Brand"] = listOf(if (draft.brand.isNotBlank()) draft.brand else "Markenlos")
                finalAspects["MPN"] = listOf(if (draft.mpn.isNotBlank()) draft.mpn else "Nicht zutreffend")
                draft.aspects.forEach { (k, v) ->
                    if (k != "Brand" && k != "MPN") {
                        finalAspects[k] = listOf(v)
                    }
                }

                val inventoryRequest = InventoryItemRequest(
                    product = Product(
                        title = draft.title.take(80),
                        description = draft.descriptionHtml,
                        imageUris = imageUrls.take(12),
                        aspects = finalAspects
                    ),
                    condition = draft.condition,
                    availability = Availability(
                        shipToLocationAvailability = ShipToLocationAvailability(draft.quantity),
                        merchantLocationKey = if (merchantLocation.isNotBlank()) merchantLocation else null
                    )
                )

                val inventoryResponse = EbayRetrofitClient.ebayApiService.createOrReplaceInventoryItem(
                    sku = draft.sku,
                    authorization = "Bearer $token",
                    body = inventoryRequest
                )

                if (inventoryResponse.isSuccessful) {
                    _uploadState.value = UploadUiState.Loading("Angebot wird generiert...", 0.95f)
                    val priceValue = sanitizePrice(if (draft.suggestedPrice.isNotBlank()) draft.suggestedPrice else defaultPrice)
                    val duration = if (draft.listingFormat == "FIXED_PRICE") "GTC" else "DAYS_7"
                    val finalStartTime = if (draft.listingFormat == "AUCTION") formatStartTime(startTimeText) else null

                    val offerRequest = OfferRequest(
                        sku = draft.sku,
                        categoryId = draft.categoryId,
                        format = draft.listingFormat,
                        listingDuration = duration,
                        pricingSummary = PricingSummary(
                            price = Price(value = priceValue),
                            bestOfferDetails = if (draft.bestOfferEnabled) BestOfferDetails(true) else null
                        ),
                        availableQuantity = draft.quantity,
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
                                settingsManager.saveCurrentDraft(null)
                                
                                // Lösche nur Bilder dieses Drafts
                                draft.imagePaths.forEach { path ->
                                    try { File(path).delete() } catch(e:Exception){}
                                }
                                
                                // Nach 5 Sekunden wieder Idle
                                kotlinx.coroutines.delay(5000)
                                if (_uploadState.value is UploadUiState.Success) {
                                    _uploadState.value = UploadUiState.Idle
                                }
                            } else {
                                val errorMsg = parseEbayError(publishResponse.errorBody()?.string())
                                _uploadState.value = UploadUiState.Error("eBay Fehler (Publish): $errorMsg")
                            }
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
                _uploadState.value = UploadUiState.Error("Upload-Fehler: ${e.message ?: e.localizedMessage}")
            }
        }
    }

    private suspend fun uploadSingleImage(bitmap: Bitmap, token: String): String {
        var attempt = 0
        while (attempt < 3) {
            try {
                return withContext(Dispatchers.IO) {
                    val resized = ImageUtils.resizeBitmap(bitmap) // Resize to max 1600 inside ImageUtils
                    val stream = ByteArrayOutputStream()
                    resized.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                    val imageByteArray = stream.toByteArray()

                    val xmlPayload = """
                        <?xml version="1.0" encoding="utf-8"?>
                        <UploadSiteHostedPicturesRequest xmlns="urn:ebay:apis:eBLBaseComponents">
                            <PictureSet>Supersize</PictureSet>
                            <ExtensionIn>Binary</ExtensionIn>
                        </UploadSiteHostedPicturesRequest>
                    """.trimIndent()

                    val xmlPart = MultipartBody.Part.createFormData(
                        "XMLPayload", 
                        null, 
                        xmlPayload.toRequestBody("text/xml".toMediaTypeOrNull())
                    )

                    val imagePart = MultipartBody.Part.createFormData(
                        "file", 
                        "image.jpg", 
                        imageByteArray.toRequestBody("image/jpeg".toMediaTypeOrNull())
                    )

                    val responseBody = EbayRetrofitClient.ebayApiService.uploadPicture(
                        xmlRequest = xmlPart,
                        picture = imagePart
                    ).string()

                    val urlRegex = Regex("<FullSizeInternalURL>(.*?)</FullSizeInternalURL>")
                    val match = urlRegex.find(responseBody)
                    match?.groupValues?.get(1) ?: throw Exception("URL nicht in eBay-Antwort gefunden.")
                }
            } catch (e: Exception) {
                attempt++
                if (attempt >= 3) {
                    throw Exception("Bild-Upload zu eBay fehlgeschlagen. Bitte Internetverbindung prüfen.")
                }
                kotlinx.coroutines.delay(1000)
            }
        }
        throw Exception("Bild-Upload zu eBay fehlgeschlagen. Bitte Internetverbindung prüfen.")
    }

    fun fetchEbaySettings(token: String, settingsManager: SettingsManager, onResult: (String) -> Unit) {
        viewModelScope.launch {
            _isFetchingSettings.value = true
            try {
                val authHeader = "Bearer $token"
                val locationsResponse = EbayRetrofitClient.ebayApiService.getLocations(authHeader)
                val locList = locationsResponse.locations ?: emptyList()
                _locations.value = locList
                locList.firstOrNull()?.let { settingsManager.saveEbayMerchantLocation(it.merchantLocationKey) }

                val fulfillmentResponse = EbayRetrofitClient.ebayApiService.getFulfillmentPolicies(authHeader)
                val fullList = fulfillmentResponse.fulfillmentPolicies ?: emptyList()
                _fulfillmentPolicies.value = fullList
                fullList.firstOrNull()?.let { settingsManager.saveEbayFulfillmentPolicy(it.policyId) }

                val paymentResponse = EbayRetrofitClient.ebayApiService.getPaymentPolicies(authHeader)
                val payList = paymentResponse.paymentPolicies ?: emptyList()
                _paymentPolicies.value = payList
                payList.firstOrNull()?.let { settingsManager.saveEbayPaymentPolicy(it.policyId) }

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
            val inputSdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.GERMANY)
            val date = inputSdf.parse(text)
            val outputSdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            outputSdf.timeZone = TimeZone.getTimeZone("UTC")
            if (date != null) outputSdf.format(date) else null
        } catch (e: Exception) {
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
        val clean = input.replace(",", ".").replace(Regex("[^0-9.]"), "")
        return try {
            val price = clean.toDouble()
            if (price <= 0) throw Exception("Preis muss größer als 0 sein.")
            String.format(Locale.US, "%.2f", price)
        } catch (e: Exception) {
            throw Exception("Ungültiger Preis")
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

    fun resetAll(settingsManager: SettingsManager, context: Context) {
        _uiState.value = QuicksaleUiState.Idle
        _uploadState.value = UploadUiState.Idle
        _notes.value = ""
        _imagePaths.value = emptyList()
        ImageUtils.clearInternalImageStorage(context)
        viewModelScope.launch {
            settingsManager.saveCurrentDraft(null)
        }
    }

    fun updateDraft(newDraft: EbayDraft, settingsManager: SettingsManager) {
        val currentState = _uiState.value
        if (currentState is QuicksaleUiState.Success) {
            _uiState.value = QuicksaleUiState.Success(newDraft)
            _imagePaths.value = newDraft.imagePaths
            viewModelScope.launch {
                settingsManager.saveCurrentDraft(gson.toJson(newDraft))
            }
        }
    }

    companion object {
        private const val RECHTLICHER_HINWEIS = "<br><br><b>Rechtlicher Hinweis:</b> Es handelt sich um einen Privatverkauf. Der Verkauf erfolgt unter Ausschluss jeglicher Sachmängelhaftung. Die Haftung auf Schadenersatz wegen Verletzungen von Gesundheit, Körper oder Leben und grob fahrlässiger und/oder vorsätzlicher Verletzungen meiner Pflichten als Verkäufer bleibt davon unberührt. Keine Rücknahme."
    }
}