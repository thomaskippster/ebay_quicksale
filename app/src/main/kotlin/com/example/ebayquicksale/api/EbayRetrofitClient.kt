package com.example.ebayquicksale.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object EbayRetrofitClient {
    private const val PRODUCTION_BASE_URL = "https://api.ebay.com/"
    private const val SANDBOX_BASE_URL = "https://api.sandbox.ebay.com/"

    private var productionService: EbayApiService? = null
    private var sandboxService: EbayApiService? = null

    fun getApiService(useSandbox: Boolean): EbayApiService {
        return if (useSandbox) {
            if (sandboxService == null) {
                sandboxService = createService(SANDBOX_BASE_URL)
            }
            sandboxService!!
        } else {
            if (productionService == null) {
                productionService = createService(PRODUCTION_BASE_URL)
            }
            productionService!!
        }
    }

    private fun createService(baseUrl: String): EbayApiService {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(EbayApiService::class.java)
    }

    // Für Abwärtskompatibilität, falls benötigt, aber wir sollten getApiService nutzen
    val ebayApiService: EbayApiService
        get() = getApiService(false)
}
