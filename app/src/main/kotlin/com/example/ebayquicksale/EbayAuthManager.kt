package com.example.ebayquicksale

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.openid.appauth.*

class EbayAuthManager(
    private val context: Context,
    private val settingsManager: SettingsManager
) {

    private val authService = AuthorizationService(context)
    
    // eBay OAuth Endpoints
    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse("https://auth.ebay.com/oauth2/authorize"), // Auth Endpoint
        Uri.parse("https://api.ebay.com/identity/v1/oauth2/token") // Token Endpoint
    )

    private val redirectUri = Uri.parse("quiksale://oauth2redirect")

    fun createAuthIntent(clientId: String): Intent {
        val authRequest = AuthorizationRequest.Builder(
            serviceConfig,
            clientId,
            ResponseTypeValues.CODE,
            redirectUri
        ).setScope("https://api.ebay.com/oauth/api_scope/commerce.taxonomy.readonly https://api.ebay.com/oauth/api_scope/sell.inventory")
         .build()

        return authService.getAuthorizationRequestIntent(authRequest)
    }

    fun handleAuthResponse(intent: Intent, clientSecret: String, callback: (String?, String?) -> Unit) {
        val response = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)

        if (response != null) {
            val clientAuth: ClientAuthentication = ClientSecretBasic(clientSecret)
            authService.performTokenRequest(
                response.createTokenExchangeRequest(),
                clientAuth
            ) { tokenResponse, tokenException ->
                if (tokenResponse != null) {
                    val accessToken = tokenResponse.accessToken
                    CoroutineScope(Dispatchers.IO).launch {
                        settingsManager.saveEbayAccessToken(accessToken)
                    }
                    callback(accessToken, null)
                } else {
                    callback(null, tokenException?.message ?: "Token exchange failed")
                }
            }
        } else {
            callback(null, ex?.message ?: "Auth failed")
        }
    }
}
