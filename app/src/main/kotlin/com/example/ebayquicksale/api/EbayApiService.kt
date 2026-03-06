package com.example.ebayquicksale.api

import retrofit2.http.*

interface EbayApiService {
    
    /**
     * Ruft eBay-Kategorie-Vorschläge basierend auf einem Such-String ab.
     */
    @GET("commerce/taxonomy/v1/category_tree/77/get_category_suggestions")
    suspend fun getCategorySuggestions(
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
     * Ruft Versand-Policies (Fulfillment Policies) ab.
     */
    @GET("sell/account/v1/fulfillment_policy")
    suspend fun getFulfillmentPolicies(
        @Header("Authorization") authorization: String,
        @Query("marketplace_id") marketplaceId: String = "EBAY_DE"
    ): PolicyResponse

    /**
     * Ruft Zahlungs-Policies (Payment Policies) ab.
     */
    @GET("sell/account/v1/payment_policy")
    suspend fun getPaymentPolicies(
        @Header("Authorization") authorization: String,
        @Query("marketplace_id") marketplaceId: String = "EBAY_DE"
    ): PolicyResponse

    /**
     * Ruft Rückgabe-Policies (Return Policies) ab.
     */
    @GET("sell/account/v1/return_policy")
    suspend fun getReturnPolicies(
        @Header("Authorization") authorization: String,
        @Query("marketplace_id") marketplaceId: String = "EBAY_DE"
    ): PolicyResponse

    /**
     * Ruft Standorte (Merchant Locations) ab.
     */
    @GET("sell/inventory/v1/location")
    suspend fun getLocations(
        @Header("Authorization") authorization: String
    ): LocationResponse
}
