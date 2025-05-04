package io.writeopia.ui.image

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.CacheStrategy
import coil3.network.ConnectivityChecker
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import io.ktor.client.HttpClient

object ImageLoadConfig {

    @OptIn(ExperimentalCoilApi::class)
    @Composable
    fun configImageLoad() {
        setSingletonImageLoaderFactory { context ->
            ImageLoader.Builder(context)
                .components {
                    add(
                        KtorNetworkFetcherFactory(
                            httpClient = { HttpClient() },
                            cacheStrategy = { CacheStrategy.DEFAULT },
                            connectivityChecker = { ctx -> ConnectivityChecker(ctx) }
                        )
                    )
                }
                .crossfade(true)
                .build()
        }
    }
}
