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
        val EBAY_CLIENT_ID = stringPreferencesKey("ebay_client_id")
        val EBAY_CLIENT_SECRET = stringPreferencesKey("ebay_client_secret")
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

    val ebayClientId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[EBAY_CLIENT_ID] ?: ""
    }

    val ebayClientSecret: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[EBAY_CLIENT_SECRET] ?: ""
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
}
