package com.example.ebayquicksale

import android.content.Context
import android.content.Intent
import net.openid.appauth.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class EbayAuthManager(private val context: Context, private val settingsManager: SettingsManager) {

    private val authService = AuthorizationService(context)

    private fun getServiceConfig(useSandbox: Boolean): AuthorizationServiceConfiguration {
        val authBase = if (useSandbox) "https://auth.sandbox.ebay.com" else "https://auth.ebay.com"
        val apiBase = if (useSandbox) "https://api.sandbox.ebay.com" else "https://api.ebay.com"
        
        return AuthorizationServiceConfiguration(
            android.net.Uri.parse("$authBase/oauth2/authorize"),
            android.net.Uri.parse("$apiBase/identity/v1/oauth2/token")
        )
    }

    fun createAuthIntent(clientId: String, ruName: String, useSandbox: Boolean): Intent {
        val request = AuthorizationRequest.Builder(
            getServiceConfig(useSandbox), clientId, ResponseTypeValues.CODE,
            android.net.Uri.parse(ruName)
        ).setScope("https://api.ebay.com/oauth/api_scope/commerce.taxonomy.readonly https://api.ebay.com/oauth/api_scope/sell.inventory https://api.ebay.com/oauth/api_scope/sell.account https://api.ebay.com/oauth/api_scope/sell.account.readonly")
         .build()
        return authService.getAuthorizationRequestIntent(request)
    }

    fun handleAuthResponse(intent: Intent, clientSecret: String, useSandbox: Boolean, callback: (String?, String?) -> Unit) {
        val response = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)

        if (response != null) {
            val clientAuth: ClientAuthentication = ClientSecretPost(clientSecret)
            val tokenRequest = response.createTokenExchangeRequest(emptyMap())
            // Wir müssen sicherstellen, dass der TokenRequest die richtige Konfiguration (Sandbox/Production) nutzt
            val requestWithConfig = TokenRequest.Builder(getServiceConfig(useSandbox), tokenRequest.clientId)
                .setAuthorizationCode(tokenRequest.authorizationCode)
                .setRedirectUri(tokenRequest.redirectUri)
                .setGrantType(tokenRequest.grantType)
                .setScopes(tokenRequest.scope)
                .build()

            authService.performTokenRequest(requestWithConfig, clientAuth) { tokenResponse, tokenEx ->
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

    fun getValidAccessToken(clientSecret: String, useSandbox: Boolean, callback: (String?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val savedJson = settingsManager.ebayAuthState.first()
            if (savedJson == null) {
                withContext(Dispatchers.Main) { callback(null) }
                return@launch
            }

            try {
                val authState = AuthState.jsonDeserialize(savedJson)
                val clientAuth: ClientAuthentication = ClientSecretPost(clientSecret)

                // Wir aktualisieren die Konfiguration im AuthState, falls sich der Sandbox-Modus geändert hat
                val config = getServiceConfig(useSandbox)
                val updatedAuthState = AuthState(
                    authState.lastAuthorizationResponse!!,
                    authState.lastTokenResponse,
                    authState.authorizationException
                )

                updatedAuthState.performActionWithFreshTokens(authService, clientAuth) { accessToken, _, _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        settingsManager.saveEbayAuthState(updatedAuthState.jsonSerializeString())
                        settingsManager.saveEbayAccessToken(updatedAuthState.accessToken)
                        withContext(Dispatchers.Main) { callback(accessToken) }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { callback(null) }
            }
        }
    }
}
