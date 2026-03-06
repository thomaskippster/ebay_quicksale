package com.example.ebayquicksale.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ImgurRetrofitClient {
    private const val BASE_URL = "https://api.imgur.com/"

    val imgurApiService: ImgurApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ImgurApiService::class.java)
    }
}
