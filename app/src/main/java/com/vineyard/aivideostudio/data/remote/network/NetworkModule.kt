package com.vineyard.aivideostudio.data.remote.network

import com.vineyard.aivideostudio.core.common.AppConstants
import com.vineyard.aivideostudio.core.result.AppError
import com.vineyard.aivideostudio.core.util.JsonUtils
import com.vineyard.aivideostudio.data.remote.gemini.GeminiApiService
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class AuthInterceptor(private val apiKeyProvider: () -> String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val apiKey = apiKeyProvider()
        val requestBuilder = originalRequest.newBuilder()
        if (apiKey.isNotBlank() && originalRequest.header("x-goog-api-key") == null) {
            requestBuilder.addHeader("x-goog-api-key", apiKey)
        }
        return chain.proceed(requestBuilder.build())
    }
}

class NetworkLoggingInterceptor(
    private val onLog: (message: String, isError: Boolean, details: String?) -> Unit
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val method = request.method
        val startNs = System.nanoTime()

        onLog("HTTP Request: $method $path", false, null)

        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
            onLog("HTTP Failed ($tookMs ms): $method $path - ${e.message}", true, e.stackTraceToString())
            throw e
        }

        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
        val code = response.code
        val isError = !response.isSuccessful

        if (isError) {
            onLog("HTTP Error $code ($tookMs ms): $method $path", true, "Status: $code ${response.message}")
        } else {
            onLog("HTTP Success $code ($tookMs ms): $method $path", false, null)
        }

        return response
    }
}

object ApiErrorMapper {
    fun mapHttpError(code: Int, errorBody: String?): AppError {
        val cleanError = errorBody?.take(500) ?: "HTTP $code"
        return when (code) {
            400 -> AppError.ValidationError("Invalid API request format: $cleanError")
            401, 403 -> AppError.ApiKeyError("Invalid or unauthorized Gemini API key: $cleanError")
            404 -> AppError.ModelUnavailableError("Requested model not found: $cleanError")
            429 -> AppError.QuotaExceededError("Gemini API quota exceeded or rate limited: $cleanError")
            in 500..599 -> AppError.NetworkError("Gemini server error ($code): $cleanError")
            else -> AppError.NetworkError("Gemini API error ($code): $cleanError")
        }
    }
}

object NetworkModule {
    fun createOkHttpClient(
        apiKeyProvider: () -> String,
        networkLogger: NetworkLoggingInterceptor? = null
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC // Never log full body to avoid leaking keys/data
        }

        // Configure OkHttp Dispatcher to allow high-concurrency parallel requests without host choking
        val dispatcher = Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 32 // Raised from default 5 to allow parallel cue batches (e.g. 10 at a time)
        }

        val builder = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(apiKeyProvider))
            .addInterceptor(logging)

        if (networkLogger != null) {
            builder.addInterceptor(networkLogger)
        }

        return builder.build()
    }

    fun createGeminiApiService(okHttpClient: OkHttpClient): GeminiApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl(AppConstants.GEMINI_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(JsonUtils.moshi))
            .build()

        return retrofit.create(GeminiApiService::class.java)
    }
}