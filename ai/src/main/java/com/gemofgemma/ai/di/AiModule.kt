package com.gemofgemma.ai.di

import com.gemofgemma.ai.GemmaServiceConnector
import com.gemofgemma.core.AiProcessor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // No read timeout for large model downloads
            .build()
    }

    // GemmaEngine, PromptRouter, DetectionResponseParser,
    // and ModelDownloadManager all use @Inject constructor — no explicit @Provides needed.
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AiBindingModule {

    @Binds
    @Singleton
    abstract fun bindAiProcessor(connector: GemmaServiceConnector): AiProcessor
}
