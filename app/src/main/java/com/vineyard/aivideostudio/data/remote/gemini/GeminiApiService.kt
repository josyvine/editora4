package com.vineyard.aivideostudio.data.remote.gemini

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GeminiApiService {

    @GET("v1beta/models")
    suspend fun listModels(
        @Header("x-goog-api-key") apiKey: String,
        @Query("pageSize") pageSize: Int? = 50,
        @Query("pageToken") pageToken: String? = null
    ): Response<ListModelsResponse>

    @POST("v1beta/{model}:generateContent")
    suspend fun generateContent(
        @Path(value = "model", encoded = true) model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GenerateContentRequest
    ): Response<GenerateContentResponse>
}
