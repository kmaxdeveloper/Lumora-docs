package uz.kmax.documents

import android.app.Application
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import uz.kmax.documents.data.ai.AiApiService
import uz.kmax.documents.data.ai.AiRepositoryImpl
import uz.kmax.documents.data.local.PreferenceManager
import uz.kmax.documents.data.local.database.AppDatabase
import uz.kmax.documents.data.repository.DocumentRepositoryImpl
import uz.kmax.documents.data.ocr.MlKitOcrDataSource
import uz.kmax.documents.data.ocr.OcrRepositoryImpl
import uz.kmax.documents.domain.repository.AiRepository
import uz.kmax.documents.domain.repository.DocumentRepository
import uz.kmax.documents.domain.repository.OcrRepository
import uz.kmax.documents.domain.repository.BillingRepository
import uz.kmax.documents.data.repository.BillingRepositoryImpl
import uz.kmax.documents.data.local.EntitlementLocalCache
import androidx.appcompat.app.AppCompatDelegate

class LumoraApplication : Application() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    
    val preferenceManager by lazy { PreferenceManager(this) }
    
    val documentRepository: DocumentRepository by lazy { 
        DocumentRepositoryImpl(this, database.documentDao(), database.documentPageDao()) 
    }

    val ocrRepository: OcrRepository by lazy {
        OcrRepositoryImpl(MlKitOcrDataSource(this), database.documentPageDao(), documentRepository)
    }

    val billingRepository: BillingRepository by lazy {
        BillingRepositoryImpl(this, EntitlementLocalCache(this))
    }

    // AI functionality frozen for V1
    /*
    private val aiApiService: AiApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.lumora.kmax.uz/") // Placeholder Backend URL
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(AiApiService::class.java)
    }

    val aiRepository: AiRepository by lazy {
        AiRepositoryImpl(aiApiService, database.documentDao())
    }
    */

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(preferenceManager.appearance)
        uz.kmax.documents.domain.ads.AdsManager.initialize(this)
    }
}
