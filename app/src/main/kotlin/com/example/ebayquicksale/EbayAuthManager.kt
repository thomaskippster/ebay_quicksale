package com.example.ebayquicksale

import android.content.Context
import android.content.Intent
import net.openid.appauth.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class EbayAuthManager(private val context: Context, private val settingsManager: SettingsManager) {

    private val authService = AuthorizationService(context)
    private val serviceConfig = AuthorizationServiceConfiguration(
        android.net.Uri.parse("https://auth.ebay.com/oauth2/authorize"),
        android.net.Uri.parse("https://api.ebay.com/identity/v1/oauth2/token")
    )

    fun createAuthIntent(clientId: String): Intent {
        val request = AuthorizationRequest.Builder(
            serviceConfig, clientId, ResponseTypeValues.CODE,
            android.net.Uri.parse("quicksale://oauth")
        ).setScope("https://api.ebay.com/oauth/api_scope/commerce.taxonomy.readonly https://api.ebay.com/oauth/api_scope/sell.inventory https://api.ebay.com/oauth/api_scope/sell.account https://api.ebay.com/oauth/api_scope/sell.account.readonly https://api.ebay.com/oauth/api_scope/ebay_api_all")
         .build()
        return authService.getAuthorizationRequestIntent(request)
    }

    fun handleAuthResponse(intent: Intent, clientSecret: String, callback: (String?, String?) -> Unit) {
        val response = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)

        if (response != null) {
            val clientAuth: ClientAuthentication = ClientSecretPost(clientSecret)
            authService.performTokenRequest(response.createTokenExchangeRequest(), clientAuth) { tokenResponse, tokenEx ->
                val state = AuthState(response, tokenResponse, tokenEx)
                if (tokenResponse != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        settingsManager.saveEbayAuthState(state.jsonSerializeString())
                        settingsManager.saveEbayAccessToken(state.accessToken)
                        settingsManager.saveEbayRefreshToken(state.refreshToken)
                        withContext(Dispatchers.Main) { callback(state.accessToken, null) }
                    }
                } else {
                    callback(null, tokenEx?.localizedMessage)
                }
            }
        } else {
            callback(null, ex?.localizedMessage)
        }
    }

    fun getValidAccessToken(clientId: String, clientSecret: String, callback: (String?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val savedJson = settingsManager.ebayAuthState.first()
            if (savedJson == null) {
                withContext(Dispatchers.Main) { callback(null) }
                return@launch
            }

            try {
                val authState = AuthState.jsonDeserialize(savedJson)
                val clientAuth: ClientAuthentication = ClientSecretPost(clientSecret)

                // performActionWithFreshTokens übernimmt das Refreshing automatisch
                authState.performActionWithFreshTokens(authService, clientAuth) { accessToken, _, ex ->
                    CoroutineScope(Dispatchers.IO).launch {
                        settingsManager.saveEbayAuthState(authState.jsonSerializeString())
                        settingsManager.saveEbayAccessToken(authState.accessToken)
                        withContext(Dispatchers.Main) { callback(accessToken) }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { callback(null) }
            }
        }
    }
}
