package com.gemofgemma.actions.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ActionsModule {
    // All action handlers, ActionDispatcher, and SafetyValidator use constructor
    // injection via @Inject and @Singleton annotations.
    // This module serves as the Hilt entry point for the :actions module.
    // Add explicit @Provides methods here if bindings for interfaces are needed.
}
