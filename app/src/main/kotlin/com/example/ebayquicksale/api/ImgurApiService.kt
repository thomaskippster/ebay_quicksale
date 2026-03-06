package com.example.ebayquicksale.api

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ImgurApiService {
    @Multipart
    @POST("3/image")
    suspend fun uploadImage(
        @Header("Authorization") authorization: String, // "Client-ID <YOUR_CLIENT_ID>"
        @Part image: MultipartBody.Part
    ): Response<ImgurResponse>
}
