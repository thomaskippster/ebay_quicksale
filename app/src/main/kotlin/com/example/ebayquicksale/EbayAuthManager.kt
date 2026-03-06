package com.example.ebayquicksale

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import net.openid.appauth.*
import net.openid.appauth.browser.AnyBrowserMatcher
import net.openid.appauth.browser.BrowserAllowList

class EbayAuthManager(private val context: Context) {

    private val authService = AuthorizationService(context)
    
    // eBay OAuth Endpoints
    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse("https://auth.ebay.com/oauth2/authorize"), // Auth Endpoint
        Uri.parse("https://api.ebay.com/identity/v1/oauth2/token") // Token Endpoint
    )

    // eBay Credentials (Placeholders - these should be moved to a secure config later)
    private val clientId = "YOUR_EBAY_CLIENT_ID"
    private val clientSecret = "YOUR_EBAY_CLIENT_SECRET"
    private val redirectUri = Uri.parse("quiksale://oauth2redirect")

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val sharedPreferences = EncryptedSharedPreferences.create(
        "ebay_auth_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun createAuthIntent(): Intent {
        val authRequest = AuthorizationRequest.Builder(
            serviceConfig,
            clientId,
            ResponseTypeValues.CODE,
            redirectUri
        ).setScope("https://api.ebay.com/oauth/api_scope/commerce.taxonomy.readonly")
         .build()

        return authService.getAuthorizationRequestIntent(authRequest)
    }

    fun handleAuthResponse(intent: Intent, callback: (String?, String?) -> Unit) {
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
                    saveAccessToken(accessToken)
                    callback(accessToken, null)
                } else {
                    callback(null, tokenException?.message ?: "Token exchange failed")
                }
            }
        } else {
            callback(null, ex?.message ?: "Auth failed")
        }
    }

    private fun saveAccessToken(token: String?) {
        sharedPreferences.edit().putString("access_token", token).apply()
    }

    fun getAccessToken(): String? {
        return sharedPreferences.getString("access_token", null)
    }
}
