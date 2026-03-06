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
data class FulfillmentPolicyResponse(
    val fulfillmentPolicies: List<FulfillmentPolicy>? = null,
    val total: Int? = null
)

data class FulfillmentPolicy(
    val name: String,
    val fulfillmentPolicyId: String,
    val marketplaceId: String? = null,
    val default: Boolean? = null
)

data class PaymentPolicyResponse(
    val paymentPolicies: List<PaymentPolicy>? = null,
    val total: Int? = null
)

data class PaymentPolicy(
    val name: String,
    val paymentPolicyId: String,
    val marketplaceId: String? = null,
    val default: Boolean? = null
)

data class ReturnPolicyResponse(
    val returnPolicies: List<ReturnPolicy>? = null,
    val total: Int? = null
)

data class ReturnPolicy(
    val name: String,
    val returnPolicyId: String,
    val marketplaceId: String? = null,
    val default: Boolean? = null
)

data class LocationResponse(
    val locations: List<MerchantLocation>? = null,
    val total: Int? = null
)

data class MerchantLocation(
    val name: String,
    val merchantLocationKey: String
)
