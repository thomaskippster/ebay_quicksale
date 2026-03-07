package com.example.ebayquicksale.api

import okhttp3.MultipartBody
import retrofit2.http.*

interface EbayApiService {
    
    /**
     * Lädt ein Bild zum eBay Picture Service (EPS) hoch.
     */
    @Multipart
    @POST("ws/api.dll") // Trading API endpoint often used for EPS uploads
    suspend fun uploadPicture(
        @Header("X-EBAY-API-IAF-TOKEN") authorization: String,
        @Header("X-EBAY-API-CALL-NAME") callName: String = "UploadSiteHostedPictures",
        @Header("X-EBAY-API-SITEID") siteId: String = "77", // 77 = Germany
        @Header("X-EBAY-API-COMPATIBILITY-LEVEL") compatibilityLevel: String = "1191",
        @Header("X-EBAY-API-RESPONSE-ENCODING") responseEncoding: String = "XML",
        @Part xmlRequest: MultipartBody.Part,
        @Part picture: MultipartBody.Part
    ): okhttp3.ResponseBody

    /**
     * Ruft eBay-Kategorie-Vorschläge basierend auf einem Such-String ab.
     */
    @GET("commerce/taxonomy/v1/category_tree/{treeId}/get_category_suggestions")
    suspend fun getCategorySuggestions(
        @Path("treeId") treeId: String,
        @Query("q") query: String,
        @Header("Authorization") authorization: String
    ): CategorySuggestionResponse

    /**
     * Erstellt oder ersetzt ein Inventory Item (Lagerartikel) bei eBay.
     */
    @PUT("sell/inventory/v1/inventory_item/{sku}")
    suspend fun createOrReplaceInventoryItem(
        @Path("sku") sku: String,
        @Header("Authorization") authorization: String,
        @Header("Content-Language") contentLanguage: String = "de-DE",
        @Body body: InventoryItemRequest
    ): retrofit2.Response<Unit>

    /**
     * Erstellt ein Angebot (Offer) für ein Inventory Item.
     */
    @POST("sell/inventory/v1/offer")
    suspend fun createOffer(
        @Header("Authorization") authorization: String,
        @Header("Content-Language") contentLanguage: String = "de-DE",
        @Body body: OfferRequest
    ): retrofit2.Response<OfferResponse>

    /**
     * Veröffentlicht ein erstelltes Angebot (macht es live).
     */
    @POST("sell/inventory/v1/offer/{offerId}/publish")
    suspend fun publishOffer(
        @Path("offerId") offerId: String,
        @Header("Authorization") authorization: String
    ): retrofit2.Response<PublishResponse>

    /**
     * Ruft Versand-Policies (Fulfillment Policies) ab.
     */
    @GET("sell/account/v1/fulfillment_policy")
    suspend fun getFulfillmentPolicies(
        @Header("Authorization") authorization: String,
        @Query("marketplace_id") marketplaceId: String
    ): PolicyResponse

    /**
     * Ruft Zahlungs-Policies (Payment Policies) ab.
     */
    @GET("sell/account/v1/payment_policy")
    suspend fun getPaymentPolicies(
        @Header("Authorization") authorization: String,
        @Query("marketplace_id") marketplaceId: String
    ): PolicyResponse

    /**
     * Ruft Rückgabe-Policies (Return Policies) ab.
     */
    @GET("sell/account/v1/return_policy")
    suspend fun getReturnPolicies(
        @Header("Authorization") authorization: String,
        @Query("marketplace_id") marketplaceId: String
    ): PolicyResponse

    /**
     * Ruft Standorte (Merchant Locations) ab.
     */
    @GET("sell/inventory/v1/location")
    suspend fun getLocations(
        @Header("Authorization") authorization: String
    ): LocationResponse

    /**
     * Platzhalter für getMarketplaceId
     */
    @GET("sell/account/v1/privilege")
    suspend fun getMarketplaceId(
        @Header("Authorization") authorization: String
    ): okhttp3.ResponseBody
}
