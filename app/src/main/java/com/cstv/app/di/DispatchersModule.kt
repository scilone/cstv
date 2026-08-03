package com.cstv.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

/**
 * Dispatcher de calcul, injecté plutôt que référencé en dur : les dérivations
 * catalogue (`CatalogFacetsBuilder`) doivent quitter le thread principal en
 * production, mais rester sous le contrôle de l'ordonnanceur de test en JVM —
 * `Dispatchers.Default` échappe à `runTest`, qui rendrait alors la main avant
 * la fin du calcul.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
