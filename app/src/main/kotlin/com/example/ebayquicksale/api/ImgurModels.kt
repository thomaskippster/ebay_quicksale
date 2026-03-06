package com.example.ebayquicksale.api

import com.google.gson.annotations.SerializedName

data class ImgurResponse(
    @SerializedName("data")
    val data: ImgurData,
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("status")
    val status: Int
)

data class ImgurData(
    @SerializedName("link")
    val link: String
)
