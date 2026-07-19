package pe.saniape.app.data.offline

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.ui.Toaster

/**
 * Vacía la cola local contra los endpoints de staff.
 *
 * Serial (un mutex): procesa de a una operación, en orden de encolado, para
 * respetar las dependencias (el paciente antes que su cita). Antes de enviar
 * traduce los ids temporales; si falta un mapeo, salta la operación y la deja
 * para cuando su dependencia sincronice.
 *
 * Cada operación lleva su `idempotency_key`, que el servidor honra: si un
 * reintento llega dos veces, no se duplica (crítico en pagos → kardex).
 */
object Sincronizador {
    private val http = crearHttpClient()
    private val mutex = Mutex()

    /** Lanza un ciclo de sincronización sin bloquear a quien llama. */
    fun disparar(scope: CoroutineScope) {
        scope.launch { sincronizar() }
    }

    suspend fun sincronizar() {
        if (mutex.isLocked) return // ya hay un ciclo en curso
        mutex.withLock {
            val ops = ColaRepo.pendientes()
            if (ops.isEmpty()) {
                EstadoSync.actualizar(0)
                return
            }
            val mapa = ColaRepo.mapa().toMutableMap()
            val hechas = mutableSetOf<Long>()
            var avisadoSinRed = false
            var sincronizoAlgo = false

            for (op in ops) {
                if (!puedeEnviarse(op, hechas)) continue // su dependencia aún no está lista

                val payload = runCatching { Json.parseToJsonElement(op.payload).jsonObject }.getOrNull()
                if (payload == null) {
                    ColaRepo.marcarFallida(op.id, "payload inválido")
                    continue
                }
                // Traducir ids temporales; si falta alguno, esperar (no enviar huérfana).
                val traducido = traducir(payload, mapa) ?: continue

                val cuerpo = JsonObject(traducido + ("idempotency_key" to JsonPrimitive(op.idemKey)))
                val r = enviar(op.endpoint, cuerpo)
                when {
                    r == null -> {
                        // Fallo de RED: sigue pendiente, se reintenta en el próximo ciclo
                        // (al volver la señal o al reabrir la app).
                        ColaRepo.volverAPendiente(op.id, "sin conexión")
                        if (!avisadoSinRed) {
                            avisadoSinRed = true
                            Toaster.error("Sin conexión — se guardó y se registrará al volver la señal")
                        }
                    }
                    r.exito -> {
                        ColaRepo.marcarHecha(op.id)
                        hechas += op.id
                        sincronizoAlgo = true
                        // Reconciliar: si la operación creó algo que la UI mostraba con
                        // id temporal, guardar su id real para las que dependen de él.
                        if (op.idTemporal != null && r.id != null) {
                            ColaRepo.guardarMapa(op.idTemporal, r.id)
                            mapa[op.idTemporal] = r.id
                        }
                    }
                    else -> ColaRepo.marcarFallida(op.id, r.error ?: "error del servidor")
                }
            }

            val quedan = ColaRepo.contarPendientes()
            EstadoSync.actualizar(quedan)
            if (quedan == 0L) {
                ColaRepo.limpiarHechas()
                if (sincronizoAlgo) Toaster.exito("Todo sincronizado")
            }
        }
    }

    private data class Respuesta(val exito: Boolean, val id: String?, val error: String?)

    /**
     * Envía una operación. Devuelve null si el fallo es de RED o de concurrencia
     * (reintentable); una Respuesta con exito=false si el servidor la rechazó.
     */
    private suspend fun enviar(endpoint: String, cuerpo: JsonObject): Respuesta? = try {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken
        if (tk == null) {
            null // sin sesión: reintentar cuando vuelva a haberla
        } else {
            val resp = http.post("${Supabase.SITE_URL}$endpoint") {
                header("Authorization", "Bearer $tk")
                contentType(ContentType.Application.Json)
                setBody(cuerpo.toString())
            }
            val json = runCatching { Json.parseToJsonElement(resp.bodyAsText()).jsonObject }.getOrNull()
            when (resp.status) {
                HttpStatusCode.OK -> Respuesta(true, json?.get("id")?.jsonPrimitive?.contentOrNull, null)
                // 409: otra ejecución con la misma clave está en curso → reintentar luego.
                HttpStatusCode.Conflict -> null
                else -> Respuesta(
                    exito = false,
                    id = null,
                    error = json?.get("error")?.jsonPrimitive?.contentOrNull ?: "HTTP ${resp.status.value}",
                )
            }
        }
    } catch (e: Exception) {
        null // timeout / sin señal → reintentable
    }
}

/**
 * ¿Se puede enviar ya esta operación? Solo si no depende de nadie o su
 * dependencia ya se completó en este ciclo. Pura, para poder testear el orden
 * sin base de datos ni red.
 */
fun puedeEnviarse(op: OpPendiente, hechas: Set<Long>): Boolean =
    op.dependeDe == null || op.dependeDe in hechas
