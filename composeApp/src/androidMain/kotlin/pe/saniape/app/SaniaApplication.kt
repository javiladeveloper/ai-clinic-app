package pe.saniape.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import pe.saniape.app.data.Preferencias
import pe.saniape.app.data.Supabase

class SaniaApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        Preferencias.init(this)
        // Toca el cliente para inicializarlo y que restaure la sesión guardada.
        Supabase.client
    }

    // ImageLoader de Coil con fetcher de red ktor (carga las fotos evolutivas desde
    // las URLs firmadas de Supabase Storage).
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(httpClient = { HttpClient(OkHttp) })) }
            .build()
}