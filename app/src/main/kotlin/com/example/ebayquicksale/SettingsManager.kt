package com.example.ebayquicksale

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    companion object {
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val EBAY_START_PRICE = stringPreferencesKey("ebay_start_price")
        val EBAY_START_TIME = stringPreferencesKey("ebay_start_time")
        val EBAY_ACCESS_TOKEN = stringPreferencesKey("ebay_access_token")
        val EBAY_REFRESH_TOKEN = stringPreferencesKey("ebay_refresh_token")
        val EBAY_CLIENT_ID = stringPreferencesKey("ebay_client_id")
        val EBAY_CLIENT_SECRET = stringPreferencesKey("ebay_client_secret")
        val EBAY_MERCHANT_LOCATION = stringPreferencesKey("ebay_merchant_location")
        val EBAY_PAYMENT_POLICY = stringPreferencesKey("ebay_payment_policy")
        val EBAY_FULFILLMENT_POLICY = stringPreferencesKey("ebay_fulfillment_policy")
        val EBAY_RETURN_POLICY = stringPreferencesKey("ebay_return_policy")
        val EBAY_LISTING_FORMAT = stringPreferencesKey("ebay_listing_format")
        val CURRENT_DRAFT_JSON = stringPreferencesKey("current_draft_json")
        val EBAY_AUTH_STATE = stringPreferencesKey("ebay_auth_state_json")
        val EBAY_LEGAL_NOTICE_DEFAULT = stringPreferencesKey("ebay_legal_notice_default")
        val EBAY_MARKETPLACE_ID = stringPreferencesKey("ebay_marketplace_id")
    }

    val defaultLegalNotice: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[EBAY_LEGAL_NOTICE_DEFAULT] ?: ""
    }

    suspend fun saveDefaultLegalNotice(notice: String) {
        context.dataStore.edit { preferences ->
            preferences[EBAY_LEGAL_NOTICE_DEFAULT] = notice
        }
    }

    val ebayMarketplaceId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[EBAY_MARKETPLACE_ID] ?: "EBAY_DE"
    }

    suspend fun saveEbayMarketplaceId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[EBAY_MARKETPLACE_ID] = id
        }
    }

    val ebayAuthState: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[EBAY_AUTH_STATE]
    }

    suspend fun saveEbayAuthState(json: String?) {
        context.dataStore.edit { preferences ->
            if (json == null) {
                preferences.remove(EBAY_AUTH_STATE)
            } else {
                preferences[EBAY_AUTH_STATE] = json
            }
        }
    }

    val currentDraftJson: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[CURRENT_DRAFT_JSON]
    }

    suspend fun saveCurrentDraft(json: String?) {
        context.dataStore.edit { preferences ->
            if (json == null) {
                preferences.remove(CURRENT_DRAFT_JSON)
            } else {
                preferences[CURRENT_DRAFT_JSON] = json
            }
        }
    }

    val geminiApiKey: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[GEMINI_API_KEY] ?: ""
    }

    val ebayStartPrice: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[EBAY_START_PRICE] ?: "1.00"
    }

    val ebayStartTime: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[EBAY_START_TIME] ?: ""
    }

    val ebayAccessToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[EBAY_ACCESS_TOKEN]
    }

    val ebayRefreshToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[EBAY_REFRESH_TOKEN]
    }

    val ebayClientId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[EBAY_CLIENT_ID] ?: ""
    }

    val ebayClientSecret: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[EBAY_CLIENT_SECRET] ?: ""
    }

    val ebayMerchantLocation: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[EBAY_MERCHANT_LOCATION] ?: ""
    }

    val ebayPaymentPolicy: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[EBAY_PAYMENT_POLICY] ?: ""
    }

    val ebayFulfillmentPolicy: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[EBAY_FULFILLMENT_POLICY] ?: ""
    }

    val ebayReturnPolicy: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[EBAY_RETURN_POLICY] ?: ""
    }

    val ebayListingFormat: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[EBAY_LISTING_FORMAT] ?: "AUCTION"
    }

    suspend fun saveGeminiApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[GEMINI_API_KEY] = key
        }
    }

    suspend fun saveEbayStartPrice(price: String) {
        context.dataStore.edit { preferences ->
            preferences[EBAY_START_PRICE] = price
        }
    }

    suspend fun saveEbayStartTime(time: String) {
        context.dataStore.edit { preferences ->
            preferences[EBAY_START_TIME] = time
        }
    }

    suspend fun saveEbayAccessToken(token: String?) {
        context.dataStore.edit { preferences ->
            if (token == null) {
                preferences.remove(EBAY_ACCESS_TOKEN)
            } else {
                preferences[EBAY_ACCESS_TOKEN] = token
            }
        }
    }

    suspend fun saveEbayRefreshToken(token: String?) {
        context.dataStore.edit { preferences ->
            if (token == null) {
                preferences.remove(EBAY_REFRESH_TOKEN)
            } else {
                preferences[EBAY_REFRESH_TOKEN] = token
            }
        }
    }

    suspend fun saveEbayClientId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[EBAY_CLIENT_ID] = id
        }
    }

    suspend fun saveEbayClientSecret(secret: String) {
        context.dataStore.edit { preferences ->
            preferences[EBAY_CLIENT_SECRET] = secret
        }
    }

    suspend fun saveEbayMerchantLocation(location: String) {
        context.dataStore.edit { preferences ->
            preferences[EBAY_MERCHANT_LOCATION] = location
        }
    }

    suspend fun saveEbayPaymentPolicy(id: String) {
        context.dataStore.edit { preferences ->
            preferences[EBAY_PAYMENT_POLICY] = id
        }
    }

    suspend fun saveEbayFulfillmentPolicy(id: String) {
        context.dataStore.edit { preferences ->
            preferences[EBAY_FULFILLMENT_POLICY] = id
        }
    }

    suspend fun saveEbayReturnPolicy(id: String) {
        context.dataStore.edit { preferences ->
            preferences[EBAY_RETURN_POLICY] = id
        }
    }

    suspend fun saveEbayListingFormat(format: String) {
        context.dataStore.edit { preferences ->
            preferences[EBAY_LISTING_FORMAT] = format
        }
    }
}
