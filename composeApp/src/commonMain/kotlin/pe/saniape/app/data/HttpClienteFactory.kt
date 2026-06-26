package pe.saniape.app.data

import io.ktor.client.HttpClient

/**
 * Crea el HttpClient con el engine de cada plataforma (Android: OkHttp).
 * Necesario porque Ktor en KMP no auto-detecta el engine de forma fiable.
 */
expect fun crearHttpClient(): HttpClient
