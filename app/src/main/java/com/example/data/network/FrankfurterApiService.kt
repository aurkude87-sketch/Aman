package com.example.data.network

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class FrankfurterLatestResponse(
    val amount: Double = 1.0,
    val base: String = "USD",
    val date: String = "",
    val rates: Map<String, Double> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class FrankfurterTimeSeriesResponse(
    val amount: Double = 1.0,
    val base: String = "USD",
    val start_date: String = "",
    val end_date: String = "",
    val rates: Map<String, Map<String, Double>> = emptyMap()
)

interface FrankfurterApiService {
    @GET("latest")
    suspend fun getLatestRates(
        @Query("from") base: String = "USD",
        @Query("to") symbols: String = "EUR,GBP,JPY,AUD,CHF,CAD"
    ): FrankfurterLatestResponse

    @GET("{dateRange}")
    suspend fun getTimeSeries(
        @Path("dateRange") dateRange: String,
        @Query("from") base: String = "USD",
        @Query("to") symbols: String = "EUR,GBP,JPY,AUD,CHF,CAD"
    ): FrankfurterTimeSeriesResponse

    companion object {
        private const val BASE_URL = "https://api.frankfurter.app/"

        fun create(): FrankfurterApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(FrankfurterApiService::class.java)
        }
    }
}
