package com.example.ebayquicksale

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface EbayApiService {
    
    /**
     * Ruft eBay-Kategorie-Vorschläge basierend auf einem Such-String ab.
     * @param categoryTreeId Die ID des Kategorie-Baums (77 für Deutschland).
     * @param query Der Such-String (Keywords).
     * @param authorization Der OAuth 2.0 Access Token ("Bearer <token>").
     */
    @GET("commerce/taxonomy/v1/category_tree/{categoryTreeId}/get_category_suggestions")
    suspend fun getCategorySuggestions(
        @Path("categoryTreeId") categoryTreeId: String = "77",
        @Query("q") query: String,
        @Header("Authorization") authorization: String
    ): CategorySuggestionResponse
}
