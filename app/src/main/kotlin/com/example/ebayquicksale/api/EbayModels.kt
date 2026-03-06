package com.example.ebayquicksale.api

import com.google.gson.annotations.SerializedName

// Taxonomy API Models (Existing)
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

// Inventory API Models (New)
data class InventoryItemRequest(
    val product: Product,
    val condition: String = "NEW",
    val availability: Availability = Availability(ShipToLocationAvailability(1))
)

data class Product(
    val title: String,
    val description: String,
    val imageUris: List<String> = emptyList()
)

data class Availability(
    val shipToLocationAvailability: ShipToLocationAvailability,
    val merchantLocationKey: String? = null
)

data class ShipToLocationAvailability(
    val quantity: Int
)

// Offer API Models (New)
data class OfferRequest(
    val sku: String,
    val marketplaceId: String = "EBAY_DE",
    val format: String,
    val listingDuration: String,
    val availableQuantity: Int = 1,
    val pricingSummary: PricingSummary,
    val categoryId: String,
    val merchantLocationKey: String? = null,
    val listingPolicies: ListingPolicies,
    val scheduledStartTime: String? = null
)

data class PricingSummary(
    val price: Price
)

data class Price(
    val value: String,
    val currency: String = "EUR"
)

data class OfferResponse(
    val offerId: String
)

data class ListingPolicies(
    val fulfillmentPolicyId: String,
    val paymentPolicyId: String,
    val returnPolicyId: String
)

// Response Models for Policy and Location Discovery
data class PolicyResponse(
    val total: Int, 
    val fulfillmentPolicies: List<PolicyInfo>? = null, 
    val paymentPolicies: List<PolicyInfo>? = null, 
    val returnPolicies: List<PolicyInfo>? = null
)

data class PolicyInfo(val name: String, val policyId: String)

data class LocationResponse(val locations: List<LocationInfo>?)

data class LocationInfo(val merchantLocationKey: String, val name: String)
