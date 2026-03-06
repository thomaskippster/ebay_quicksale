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
}
