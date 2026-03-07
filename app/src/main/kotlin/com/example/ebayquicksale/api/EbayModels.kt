package com.example.ebayquicksale.api

import com.google.gson.annotations.SerializedName

// Taxonomy API Models (Existing)
data class CategorySuggestionResponse(
    @SerializedName("categorySuggestions")
    val categorySuggestions: List<CategorySuggestion>?
)

data class CategorySuggestion(
    val category: CategoryInfo
)

data class CategoryInfo(
    val categoryId: String,
    val categoryName: String
)

// Inventory API Models
data class InventoryItemRequest(
    val product: Product,
    val condition: String,
    val availability: Availability
)

data class Product(
    val title: String,
    val description: String,
    val imageUris: List<String>,
    val aspects: Map<String, List<String>>? = null
)

data class Availability(
    val shipToLocationAvailability: ShipToLocationAvailability,
    val merchantLocationKey: String? = null
)

data class ShipToLocationAvailability(
    val quantity: Int
)

data class OfferRequest(
    val sku: String,
    val categoryId: String,
    val format: String,
    val listingDuration: String,
    val pricingSummary: PricingSummary,
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

data class PublishResponse(
    val listingId: String
)

data class ListingPolicies(
    val fulfillmentPolicyId: String,
    val paymentPolicyId: String,
    val returnPolicyId: String
)

// Settings / Discovery Models
data class PolicyResponse(
    @SerializedName("fulfillmentPolicies") val fulfillmentPolicies: List<PolicyInfo>?,
    @SerializedName("paymentPolicies") val paymentPolicies: List<PolicyInfo>?,
    @SerializedName("returnPolicies") val returnPolicies: List<PolicyInfo>?
)

data class PolicyInfo(val name: String, val policyId: String)

data class LocationResponse(val locations: List<LocationInfo>?)

data class LocationInfo(val merchantLocationKey: String, val name: String)

data class EbayPictureResponse(
    val FullSizeInternalURL: String? = null
)
