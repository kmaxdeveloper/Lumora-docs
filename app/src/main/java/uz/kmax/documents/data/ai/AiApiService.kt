package uz.kmax.documents.data.ai

import retrofit2.http.Body
import retrofit2.http.POST
import uz.kmax.documents.data.ai.dto.SummarizeRequest
import uz.kmax.documents.data.ai.dto.SummarizeResponse

interface AiApiService {
    @POST("v1/ai/summarize")
    suspend fun summarize(@Body request: SummarizeRequest): SummarizeResponse
}
