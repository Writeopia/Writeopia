package io.writeopia.features.search.di

import androidx.compose.runtime.Composable
import io.writeopia.features.search.ui.SearchViewModel
import kotlinx.coroutines.CoroutineScope

interface SearchInjection {

    fun provideViewModel(coroutineScope: CoroutineScope? = null): SearchViewModel

    @Composable
    fun provideViewModelMobile(coroutineScope: CoroutineScope?): SearchViewModel
}
