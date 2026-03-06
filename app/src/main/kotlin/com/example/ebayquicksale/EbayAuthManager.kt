package com.example.ebayquicksale

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
                    val refreshToken = tokenResponse.refreshToken
                    
                    val authState = AuthState(response, tokenResponse, tokenException)
                    
                    CoroutineScope(Dispatchers.IO).launch {
                        settingsManager.saveEbayAccessToken(accessToken)
                        settingsManager.saveEbayRefreshToken(refreshToken)
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

    fun getValidAccessToken(clientId: String, clientSecret: String, callback: (String?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val accessToken = settingsManager.ebayAccessToken.first()
            val refreshToken = settingsManager.ebayRefreshToken.first()
            
            if (accessToken == null || refreshToken == null) {
                callback(null)
                return@launch
            }

            // Wir bauen ein minimales AuthState Objekt aus den gespeicherten Daten
            // (In einer echten App würde man das AuthState JSON serialisiert speichern)
            val authState = AuthState(serviceConfig)
            authState.update(TokenResponse.Builder(
                TokenRequest.Builder(serviceConfig, clientId).setRefreshToken(refreshToken).build()
            ).setAccessToken(accessToken).setRefreshToken(refreshToken).build(), null)

            val clientAuth: ClientAuthentication = ClientSecretBasic(clientSecret)
            
            authState.performActionWithFreshTokens(authService, clientAuth) { freshAccessToken, _, ex ->
                if (freshAccessToken != null) {
                    if (freshAccessToken != accessToken) {
                        CoroutineScope(Dispatchers.IO).launch {
                            settingsManager.saveEbayAccessToken(freshAccessToken)
                        }
                    }
                    callback(freshAccessToken)
                } else {
                    callback(null)
                }
            }
        }
    }
}
