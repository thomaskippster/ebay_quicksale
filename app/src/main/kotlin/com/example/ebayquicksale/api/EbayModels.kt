package com.example.ebayquicksale.api

import com.google.gson.annotations.SerializedName

data class CategorySuggestionResponse(
    @SerializedName("categorySuggestions")
    val categorySuggestions: List<CategorySuggestion>?
)

data class CategorySuggestion(
    @SerializedName("category")
    val category: Category?,
    @SerializedName("relevancy")
    val relevancy: String?
)

data class Category(
    @SerializedName("categoryId")
    val categoryId: String,
    @SerializedName("categoryName")
    val categoryName: String
)
