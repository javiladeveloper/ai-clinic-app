package pe.saniape.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

/** Android: usa el engine OkHttp (ya está como dependencia). */
actual fun crearHttpClient(): HttpClient = HttpClient(OkHttp)