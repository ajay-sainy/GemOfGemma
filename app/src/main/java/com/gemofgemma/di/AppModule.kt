package com.gemofgemma.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.gemofgemma.core.data.AppDatabase
import com.gemofgemma.core.data.ChatDao
import com.gemofgemma.core.data.ChatRepository
import com.gemofgemma.core.data.ToolPreferencesRepository

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideToolPreferencesRepository(@ApplicationContext context: Context): ToolPreferencesRepository {
        return ToolPreferencesRepository(context)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "gemofgemma_db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideChatDao(database: AppDatabase): ChatDao {
        return database.chatDao()
    }

    @Provides
    @Singleton
    fun provideChatRepository(chatDao: ChatDao, @ApplicationContext context: Context): ChatRepository {
        return ChatRepository(chatDao, context)
    }
}
