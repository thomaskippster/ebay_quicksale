package com.example.ebayquicksale.api

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface EbayApiService {
    
    /**
     * Ruft eBay-Kategorie-Vorschläge basierend auf einem Such-String ab.
     * @param query Der Such-String (Keywords).
     * @param authorization Der OAuth 2.0 Access Token ("Bearer <token>").
     */
    @GET("commerce/taxonomy/v1/category_tree/77/get_category_suggestions")
    suspend fun getCategorySuggestions(
        @Query("q") query: String,
        @Header("Authorization") authorization: String
    ): CategorySuggestionResponse
}
