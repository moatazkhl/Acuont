package com.example.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import com.example.BuildConfig

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@Serializable
data class Content(
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null
)

@Serializable
data class GenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate>
)

@Serializable
data class Candidate(
    val content: Content
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-1.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: String
    ): String
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

object GeminiService {
    suspend fun generateAiFinancialReport(prompt: String): String {
        // Safe access to injected GEMINI_API_KEY from BuildConfig
        val apiKey = try {
            BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String ?: ""
        } catch (e: Exception) {
            ""
        }
        
        if (apiKey.isBlank()) {
            return "عذراً، لم يتم العثور على مفتاح API الخاص بـ Gemini في Secrets للـ AI Studio. يرجى تهيئته لتنشيط المستشار المالي والتحليل الذكي بالأرقام."
        }

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(Part(text = prompt))
                )
            ),
            generationConfig = GenerationConfig(temperature = 0.7f),
            systemInstruction = Content(
                parts = listOf(Part(text = "أنت مستشار مالي وخبير محاسبة ذكي ومحترف. تقوم بتقديم تحليلات مالية دقيقة، تحديد الثغرات، إعطاء نصائح استراتيجية لنمو الأرباح وتقليل التكاليف بناء على الأرقام والبيانات المعطاة لك باللغة العربية، بأسلوب مرتب واحترافي ورصين."))
            )
        )

        return try {
            val json = Json { ignoreUnknownKeys = true }
            val requestString = json.encodeToString(GenerateContentRequest.serializer(), request)
            val responseString = RetrofitClient.service.generateContent(apiKey, requestString)
            val response = json.decodeFromString(GenerateContentResponse.serializer(), responseString)
            response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "عذراً، استجابة الذكاء الاصطناعي فارغة حالياً."
        } catch (e: Exception) {
            "فشل تحليل المستشار المالي الذكي: ${e.localizedMessage ?: e.message}"
        }
    }
}
