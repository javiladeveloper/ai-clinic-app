package pe.saniape.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

/** iOS: usa el engine Darwin (NSURLSession) — el mismo que emplea Supabase en iOS. */
actual fun crearHttpClient(): HttpClient = HttpClient(Darwin)
