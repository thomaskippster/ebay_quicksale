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
}
