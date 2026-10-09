package pe.saniape.app.data

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Resultado de una carga del portal: distingue "cargó bien" (aunque sea vacío) de
 * "falló" (red/sesión). Sin esto, un fallo se confundía con "no tienes datos" y — peor —
 * disparaba un fallback directo por email que no valida identidad (riesgo de fuga entre
 * clínicas). Con esto, el portal SOLO usa el camino API (seguro) y ante error avisa.
 */
sealed interface ResultadoPortal<out T> {
    data class Ok<T>(val datos: T) : ResultadoPortal<T>
    data object Error : ResultadoPortal<Nothing>
}

/**
 * Un pago registrado del tratamiento (informativo para el paciente). [detalle]
 * (desde 2026-10-06): un pago con saldo a favor llega como UNA entrada
 * ("Saldo a favor S/ 205.97 + Yape S/ 284.03") y el saldo que salió hacia otro
 * tratamiento con monto NEGATIVO ("Saldo aplicado a Ortodoncia"). Se muestra
 * [etiqueta] = detalle ?: metodo.
 */
data class PagoInfo(val fecha: String, val monto: Double, val metodo: String?, val detalle: String? = null) {
    val etiqueta: String? get() = detalle?.takeIf { it.isNotBlank() } ?: metodo
}

/** Cuenta del tratamiento: costo, pagado, saldo y detalle de pagos (informativo). */
data class Saldo(
    val acordado: Double,
    val pagado: Double,
    val saldo: Double,
    val estado: String,
    val pagos: List<PagoInfo> = emptyList(),
    /**
     * La clínica conectó su cuenta de Mercado Pago y puede recibir el pago.
     * Si es false NO se ofrece pagar: un botón que al tocarlo falla es peor que
     * no tenerlo.
     */
    val puedePagarOnline: Boolean = false,
    /** Lo pagado de más en ESTE tratamiento, tal como lo calcula el servidor (no se recalcula). */
    val aFavor: Double = 0.0,
    /** Clínica del tratamiento (el crédito nunca se suma entre clínicas). */
    val clinicaId: String? = null,
    /** Multipaís: moneda de la sede del tratamiento (la manda el servidor; sin ella, soles). */
    val moneda: String = pe.saniape.app.data.staff.MONEDA_POR_DEFECTO,
)

/**
 * Documento del paciente visible en su portal. [tipo] = extensión ("pdf", "jpg"…)
 * cuando el servidor la manda; [tratamientoNombre] = el tratamiento al que se ancló
 * (campo opcional nuevo; sin él la lista va plana, como antes).
 */
data class Documento(
    val id: String,
    val nombre: String,
    val categoria: String,
    val path: String,
    val fecha: String,
    val tipo: String? = null,
    val tratamientoNombre: String? = null,
    /** Id del tratamiento (agrupa: dos paquetes con el mismo nombre NO se funden). */
    val tratamientoId: String? = null,
)

/**
 * Foto evolutiva que la clínica marcó visible para el paciente. [url] = URL firmada
 * si el servidor ya la manda en la lista; si no, se pide por `?path=` al mostrarla.
 */
data class FotoPortal(
    val id: String,
    val path: String,
    val momento: String?,
    val fecha: String,
    val url: String? = null,
    val tratamientoNombre: String? = null,
    val tratamientoId: String? = null,
)

/** Lo que devuelve GET /api/paciente/mis-documentos: documentos + fotos evolutivas. */
data class DocumentosPortal(
    val documentos: List<Documento> = emptyList(),
    val fotos: List<FotoPortal> = emptyList(),
)

/**
 * Cuentas del paciente (GET /api/paciente/mis-pagos): saldo por tratamiento y lo
 * que tiene A FAVOR en total (`saldoAFavor`, campo opcional: ausente = 0).
 */
data class SaldosPortal(
    val porTratamiento: Map<String, Saldo> = emptyMap(),
    /** El crédito de la ÚNICA clínica que lo tiene; 0 si no hay o si hay en 2+ clínicas. */
    val saldoAFavor: Double = 0.0,
    /** clinicaId → crédito (> 0). Nunca se suma entre clínicas. */
    val saldoAFavorPorClinica: Map<String, Double> = emptyMap(),
)

/** Clase de archivo para el ícono y para decidir si se ve DENTRO de la app. */
enum class ClaseArchivo { IMAGEN, PDF, OTRO }

/**
 * Clase del archivo por su tipo (extensión o mime) o, si no vino, por la
 * extensión del path. Solo jpg/png/webp se ven en el visor de la app; el resto
 * (PDF, HEIC, Word…) se abre afuera como siempre.
 */
fun claseArchivo(tipo: String?, path: String?): ClaseArchivo {
    val t = tipo?.lowercase()?.substringAfterLast('/')?.substringAfterLast('.')?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: path?.substringBefore('?')?.substringAfterLast('.', "")?.lowercase()
    return when (t) {
        "jpg", "jpeg", "png", "webp" -> ClaseArchivo.IMAGEN
        "pdf" -> ClaseArchivo.PDF
        else -> ClaseArchivo.OTRO
    }
}

/** Ícono del archivo según su clase (📄 PDF, 🖼 imagen, 📎 el resto). */
fun iconoArchivo(clase: ClaseArchivo): String = when (clase) {
    ClaseArchivo.IMAGEN -> "🖼"
    ClaseArchivo.PDF -> "📄"
    ClaseArchivo.OTRO -> "📎"
}

/** Un grupo del portal: [clave] estable y única (para las listas), [titulo] null = sin título. */
data class GrupoPortal<T>(val clave: String, val titulo: String?, val items: List<T>)

/**
 * Agrupa por ID de tratamiento (no por nombre: dos paquetes que se llaman igual
 * no se funden, y un procedimiento llamado "General" no choca con el grupo de
 * los sueltos), en el orden en que aparece cada uno. Si NINGUNO trae tratamiento
 * (servidor viejo) → un solo grupo sin título (lista plana, como antes). Los que
 * no traen tratamiento van al final, en "General" (clave fija).
 */
fun <T> agruparPorTratamiento(items: List<T>, idDe: (T) -> String?, nombreDe: (T) -> String?): List<GrupoPortal<T>> {
    if (items.isEmpty()) return emptyList()
    if (items.all { idDe(it).isNullOrBlank() }) return listOf(GrupoPortal("todos", null, items))
    val con = items.filter { !idDe(it).isNullOrBlank() }.groupBy { idDe(it)!! }.map { (id, lista) ->
        GrupoPortal("t:$id", lista.firstNotNullOfOrNull { nombreDe(it)?.trim()?.takeIf { n -> n.isNotEmpty() } } ?: "Tratamiento", lista)
    }
    val sin = items.filter { idDe(it).isNullOrBlank() }
    return con + (if (sin.isNotEmpty()) listOf(GrupoPortal("general", "General", sin)) else emptyList())
}

/**
 * Los tratamientos del portal, separados (QA 2026-10-05): los VIGENTES van en la
 * lista principal (en curso primero) y los CANCELADOS en un apartado propio, sin
 * deuda ni progreso. Los Eliminados no se muestran nunca.
 */
data class TratamientosPortal(
    val vigentes: List<Tratamiento> = emptyList(),
    val cancelados: List<Tratamiento> = emptyList(),
)

fun separarTratamientosPortal(lista: List<Tratamiento>): TratamientosPortal {
    val visibles = lista.filter { it.estado != "Eliminado" }
    return TratamientosPortal(
        vigentes = visibles.filter { it.estado != "Cancelado" }.sortedByDescending { it.estado == "Activo" },
        cancelados = visibles.filter { it.estado == "Cancelado" },
    )
}

/** Una clínica donde el paciente tiene historial. `puedeReservar` = plan Plus + reservas on. */
data class ClinicaPaciente(
    val clinicaId: String,
    val nombre: String,
    val slug: String?,
    val logoUrl: String?,
    val colorPrincipal: String?,
    val puedeReservar: Boolean,
    /** Match solo por DNI, sin confirmar por la clinica: se ve que existe, sin
     *  historial ni reserva, hasta que recepcion confirme el vinculo. */
    val pendiente: Boolean = false,
)

/**
 * Datos de la pestaña Salud que NO se pueden leer con anon key (usan service_role
 * en la web: firmar URLs de storage, leer config de saldo). Se piden al API web
 * con el Bearer token del paciente.
 */
/** Resultado de pedir el link de pago. */
sealed class ResultadoPago {
    /** URL de Mercado Pago donde el paciente completa el pago. */
    data class Ok(val url: String) : ResultadoPago()
    data class Error(val mensaje: String) : ResultadoPago()
}

/** El portal del paciente exige entrar con Google/Apple (la sesión actual es por clave). */
class PortalRequiereGoogle : Exception("El portal del paciente requiere entrar con Google")

object SaludRepo {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = crearHttpClient()

    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
    private fun JsonObject.dbl(k: String): Double =
        (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
    private fun JsonObject.intp(k: String): Int =
        (this[k] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
    private fun JsonObject.bool(k: String): Boolean =
        (this[k] as? JsonPrimitive)?.content == "true"

    /**
     * Tratamientos del paciente (progreso + timeline) desde el API web con Bearer.
     * La web los lee con service_role (la RLS de tratamientos/sesiones es solo de
     * staff), así que aquí NO se pueden leer directo de Supabase con anon key.
     */
    suspend fun tratamientos(): ResultadoPortal<List<Tratamiento>> {
        val tk = token() ?: return ResultadoPortal.Error
        val resp = runCatching {
            http.get("${Supabase.SITE_URL}/api/paciente/mi-tratamiento") {
                header("Authorization", "Bearer $tk")
            }
        }.getOrNull() ?: return ResultadoPortal.Error
        // 403 = la cuenta aún NO está vinculada a ninguna ficha de paciente (cuenta nueva
        // de Google que todavía no reservó ni canjeó código). NO es un fallo → vacío, para
        // que el portal muestre "vincúlate / reserva" en vez de un error rojo alarmante.
        if (resp.status == HttpStatusCode.Forbidden) return ResultadoPortal.Ok(emptyList())
        if (resp.status != HttpStatusCode.OK) return ResultadoPortal.Error
        val arr = json.parseToJsonElement(resp.bodyAsText()).jsonObject["tratamientos"] as? JsonArray
            ?: JsonArray(emptyList())   // API respondió OK pero sin tratamientos = vacío legítimo
        val lista = arr.mapNotNull { el ->
            val o = el.jsonObject
            val ses = (o["sesiones"] as? JsonArray ?: JsonArray(emptyList())).mapNotNull {
                val s = it.jsonObject
                SesionMin(
                    numero = s.intp("numero"),
                    fecha = s.str("fecha") ?: "",
                    estado = s.str("estado") ?: "",
                )
            }
            Tratamiento(
                id = o.str("id") ?: return@mapNotNull null,
                procedimiento = o.str("procedimiento") ?: "Tratamiento",
                clinica = o.str("clinica"),
                clinicaLogo = o.str("clinicaLogo"),
                estado = o.str("estado") ?: "",
                usaSesiones = o.bool("usaSesiones"),
                totalSesiones = (o["totalSesiones"] as? JsonPrimitive)?.content?.toIntOrNull(),
                sesionesCompletadas = o.intp("sesionesCompletadas"),
                fechaInicio = o.str("fechaInicio"),
                sesiones = ses,
                modalidad = o.str("modalidad"),
            )
        }
        return ResultadoPortal.Ok(lista)
    }

    /**
     * Clínicas donde el paciente tiene historial (fichas). El flujo del portal muestra
     * estas y solo en las `puedeReservar` (Plus) permite agendar. Error → null (la UI
     * distingue "sin historial" de "falló la carga").
     */
    suspend fun misClinicas(): List<ClinicaPaciente>? {
        val tk = token() ?: return null
        val resp = runCatching {
            http.get("${Supabase.SITE_URL}/api/paciente/mis-clinicas") {
                header("Authorization", "Bearer $tk")
            }
        }.getOrNull() ?: return null
        // 403 = la cuenta todavía no está vinculada a ninguna ficha (cuenta nueva de Google
        // que aún no reservó ni canjeó código). NO es un fallo de red → lista vacía, para
        // que la pantalla invite a vincularse en vez de gritar "revisa tu conexión".
        // Mismo criterio que mi-tratamiento y mis-citas; esta era la única que faltaba.
        if (resp.status == HttpStatusCode.Forbidden) return emptyList()
        if (resp.status != HttpStatusCode.OK) return null
        val arr = json.parseToJsonElement(resp.bodyAsText()).jsonObject["clinicas"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull {
            val o = it.jsonObject
            ClinicaPaciente(
                clinicaId = o.str("clinica_id") ?: return@mapNotNull null,
                nombre = o.str("nombre") ?: "Clínica",
                slug = o.str("slug"),
                logoUrl = o.str("logo_url"),
                colorPrincipal = o.str("color_principal"),
                puedeReservar = o.bool("puedeReservar"),
                pendiente = o.bool("pendiente"),
            )
        }
    }

    /** Saldos por tratamiento_id (solo de clínicas que lo habilitaron) + lo que tiene a favor. */
    suspend fun saldos(): SaldosPortal {
        val tk = token() ?: return SaldosPortal()
        val resp = http.get("${Supabase.SITE_URL}/api/paciente/mis-pagos") {
            header("Authorization", "Bearer $tk")
        }
        if (resp.status != HttpStatusCode.OK) return SaldosPortal()
        return parsearSaldos(resp.bodyAsText())
    }

    /** El JSON de mis-pagos → saldos (puro, testeable). `saldoAFavor` ausente = 0. */
    internal fun parsearSaldos(cuerpo: String): SaldosPortal {
        val raiz = runCatching { json.parseToJsonElement(cuerpo).jsonObject }.getOrNull() ?: return SaldosPortal()
        val obj = raiz["saldos"] as? JsonObject
        val porTrat = obj?.mapNotNull { (id, v) ->
            val o = v as? JsonObject ?: return@mapNotNull null
            val pagos = (o["pagos"] as? JsonArray ?: JsonArray(emptyList())).mapNotNull {
                val p = it as? JsonObject ?: return@mapNotNull null
                PagoInfo(
                    fecha = p.str("fecha") ?: return@mapNotNull null,
                    monto = p.dbl("monto"),
                    metodo = p.str("metodo"),
                    detalle = p.str("detalle"),
                )
            }
            id to Saldo(o.dbl("acordado"), o.dbl("pagado"), o.dbl("saldo"), o.str("estado") ?: "", pagos,
                puedePagarOnline = o.bool("puedePagarOnline"),
                aFavor = o.dbl("aFavor").coerceAtLeast(0.0),
                clinicaId = o.str("clinicaId"),
                moneda = o.str("moneda")?.takeIf { it.isNotBlank() }?.let { pe.saniape.app.data.staff.normalizarMoneda(it) }
                    ?: pe.saniape.app.data.staff.MONEDA_POR_DEFECTO)
        }?.toMap().orEmpty()
        val porClinica = (raiz["saldoAFavorPorClinica"] as? JsonObject)?.mapNotNull { (id, v) ->
            val monto = (v as? JsonPrimitive)?.content?.toDoubleOrNull() ?: return@mapNotNull null
            if (monto > 0.005) id to monto else null
        }?.toMap().orEmpty()
        return SaldosPortal(porTrat, raiz.dbl("saldoAFavor").coerceAtLeast(0.0), porClinica)
    }

    /** Documentos del paciente + sus fotos evolutivas visibles. */
    suspend fun documentos(): DocumentosPortal {
        val tk = token() ?: return DocumentosPortal()
        val resp = http.get("${Supabase.SITE_URL}/api/paciente/mis-documentos") {
            header("Authorization", "Bearer $tk")
        }
        if (resp.status != HttpStatusCode.OK) return DocumentosPortal()
        return parsearDocumentos(resp.bodyAsText())
    }

    /**
     * El JSON de mis-documentos → documentos y fotos (puro, testeable). Antes se
     * leía solo `documentos` y las fotos que la clínica marcó visibles se perdían.
     * `tipo`, `tratamientoNombre` y `fotos[].url` son opcionales.
     */
    internal fun parsearDocumentos(cuerpo: String): DocumentosPortal {
        val raiz = runCatching { json.parseToJsonElement(cuerpo).jsonObject }.getOrNull() ?: return DocumentosPortal()
        val docs = ((raiz["documentos"] as? JsonArray) ?: JsonArray(emptyList())).mapNotNull {
            val o = it as? JsonObject ?: return@mapNotNull null
            Documento(
                id = o.str("id") ?: return@mapNotNull null,
                nombre = o.str("nombre") ?: "Documento",
                categoria = o.str("categoria") ?: "",
                path = o.str("path") ?: return@mapNotNull null,
                fecha = o.str("fecha") ?: "",
                tipo = o.str("tipo"),
                tratamientoNombre = o.str("tratamientoNombre")?.takeIf { n -> n.isNotBlank() },
                tratamientoId = o.str("tratamientoId")?.takeIf { n -> n.isNotBlank() },
            )
        }
        val fotos = ((raiz["fotos"] as? JsonArray) ?: JsonArray(emptyList())).mapNotNull {
            val o = it as? JsonObject ?: return@mapNotNull null
            FotoPortal(
                id = o.str("id") ?: return@mapNotNull null,
                path = o.str("path") ?: return@mapNotNull null,
                momento = o.str("momento"),
                fecha = o.str("fecha") ?: "",
                url = o.str("url")?.takeIf { u -> u.isNotBlank() },
                tratamientoNombre = o.str("tratamientoNombre")?.takeIf { n -> n.isNotBlank() },
                tratamientoId = o.str("tratamientoId")?.takeIf { n -> n.isNotBlank() },
            )
        }
        return DocumentosPortal(docs, fotos)
    }

    /** URL firmada temporal para abrir un documento. */
    suspend fun urlDocumento(path: String): String? {
        val tk = token() ?: return null
        val resp = http.get("${Supabase.SITE_URL}/api/paciente/mis-documentos") {
            header("Authorization", "Bearer $tk")
            url { parameters.append("path", path) }
        }
        if (resp.status != HttpStatusCode.OK) return null
        return json.parseToJsonElement(resp.bodyAsText()).jsonObject.str("url")
    }

    // ── DNI de la cuenta: la llave que enlaza el portal con sus fichas ──

    /** DNI reclamado por la cuenta, o null si aún no tiene (→ pedirlo). */
    suspend fun dniCuenta(): String? {
        val tk = token() ?: return null
        val resp = http.get("${Supabase.SITE_URL}/api/paciente/dni") {
            header("Authorization", "Bearer $tk")
        }
        // 403 con requiereGoogle: la sesión entró con usuario+clave (staff en modo
        // paciente). El portal solo confía en Google/Apple → la UI lo explica en vez
        // de pedir un DNI que el servidor igual va a rechazar.
        if (resp.status == HttpStatusCode.Forbidden) {
            val requiere = runCatching {
                json.parseToJsonElement(resp.bodyAsText()).jsonObject.bool("requiereGoogle")
            }.getOrDefault(false)
            if (requiere) throw PortalRequiereGoogle()
        }
        if (resp.status != HttpStatusCode.OK) return null
        return json.parseToJsonElement(resp.bodyAsText()).jsonObject.str("dni")
    }

    /**
     * Reclama el DNI para la cuenta (el server valida RENIEC + coincidencia de
     * nombre + único + fijo 30 días). Devuelve null si ok; si no, el mensaje de error.
     */
    suspend fun reclamarDni(dni: String): String? {
        val tk = token() ?: return "Sesión expirada"
        val resp = http.post("${Supabase.SITE_URL}/api/paciente/dni") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody("""{"dni":"$dni"}""")
        }
        if (resp.status == HttpStatusCode.OK) return null
        return runCatching {
            json.parseToJsonElement(resp.bodyAsText()).jsonObject.str("error")
        }.getOrNull() ?: "No se pudo guardar tu DNI"
    }

    /**
     * Canjea el código de 6 dígitos que le dio su clínica: vincula la cuenta con su
     * ficha aunque no tenga email ni DNI registrados. Devuelve null si ok; si no,
     * el mensaje de error del servidor (código usado, vencido, inválido...).
     */
    suspend fun vincularCodigo(codigo: String): String? {
        val tk = token() ?: return "Sesión expirada"
        val resp = http.post("${Supabase.SITE_URL}/api/paciente/vincular") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody("""{"codigo":"$codigo"}""")
        }
        if (resp.status == HttpStatusCode.OK) return null
        return runCatching {
            json.parseToJsonElement(resp.bodyAsText()).jsonObject.str("error")
        }.getOrNull() ?: "No se pudo vincular"
    }

    /** Citas del portal desde el API web (email O DNI — no solo email). */
    suspend fun misCitas(): ResultadoPortal<Pair<List<CitaPortal>, List<CitaPortal>>> {
        val tk = token() ?: return ResultadoPortal.Error
        val resp = runCatching {
            http.get("${Supabase.SITE_URL}/api/paciente/mis-citas") {
                header("Authorization", "Bearer $tk")
            }
        }.getOrNull() ?: return ResultadoPortal.Error
        // 403 = cuenta aún no vinculada a ninguna ficha → vacío (no error). Ver tratamientos().
        if (resp.status == HttpStatusCode.Forbidden) return ResultadoPortal.Ok(emptyList<CitaPortal>() to emptyList())
        if (resp.status != HttpStatusCode.OK) return ResultadoPortal.Error
        val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
        fun mapear(k: String): List<CitaPortal> =
            (root[k] as? JsonArray ?: JsonArray(emptyList())).mapNotNull {
                val o = it.jsonObject
                CitaPortal(
                    id = o.str("id") ?: return@mapNotNull null,
                    fecha = o.str("fecha") ?: "",
                    hora = o.str("hora") ?: "",
                    estado = o.str("estado") ?: "",
                    tipo = o.str("tipo"),
                    profesional = o.str("profesional"),
                    clinica = o.str("clinica"),
                    clinicaSlug = o.str("clinicaSlug"),
                    // El servidor ya mandaba todo esto; el parser lo descartaba y por eso
                    // el paciente no podía gestionar su cita ni saber a qué local ir.
                    token = o.str("token"),
                    sede = o.str("sede"),
                    direccion = o.str("direccion"),
                    lat = o["lat"]?.jsonPrimitive?.doubleOrNull,
                    lng = o["lng"]?.jsonPrimitive?.doubleOrNull,
                    whatsapp = o.str("whatsapp"),
                )
            }
        return ResultadoPortal.Ok(mapear("proximas") to mapear("pasadas"))
    }

    /**
     * Confirmar / cancelar / reprogramar / reseñar la cita.
     *
     * Va contra /api/cita/[token], el MISMO endpoint que usa el portal web: el
     * paciente no necesita sesión, le basta el token de su cita. Devuelve null
     * si salió bien, o el motivo en español para mostrarlo tal cual.
     *
     * El servidor tiene la última palabra y por buenas razones: no deja
     * reprogramar a menos de 3 horas de la cita, ni tocar una ya completada o
     * cancelada. Esos mensajes se muestran sin reescribir.
     */
    private suspend fun accionCita(token: String, cuerpo: String): String? {
        val resp = runCatching {
            http.post("${Supabase.SITE_URL}/api/cita/$token") {
                contentType(ContentType.Application.Json)
                setBody(cuerpo)
            }
        }.getOrNull() ?: return "Sin conexión. Revisa tu internet."
        if (resp.status == HttpStatusCode.OK) return null
        return runCatching {
            json.parseToJsonElement(resp.bodyAsText()).jsonObject.str("error")
        }.getOrNull() ?: "No se pudo completar la acción"
    }

    suspend fun confirmarCita(token: String): String? =
        accionCita(token, """{"accion":"confirmar"}""")

    suspend fun cancelarCita(token: String): String? =
        accionCita(token, """{"accion":"cancelar"}""")

    /** fecha AAAA-MM-DD, hora HH:MM. La clínica recibe un aviso del cambio. */
    suspend fun reprogramarCita(token: String, fecha: String, hora: String): String? =
        accionCita(token, """{"accion":"reprogramar","fecha":"$fecha","hora":"$hora"}""")

    /**
     * Reseña de la atención (1 a 5 estrellas). El comentario se escapa porque
     * el paciente escribe libre: una comilla rompería el JSON armado a mano.
     */
    suspend fun resenarCita(token: String, calificacion: Int, comentario: String): String? {
        val limpio = JsonPrimitive(comentario.trim()).toString()
        return accionCita(token, """{"accion":"resenar","calificacion":$calificacion,"comentario":$limpio}""")
    }

    /**
     * Pedir el link para pagar un tratamiento.
     *
     * El monto lo decide el SERVIDOR (saldo pendiente): aquí solo se dice cuánto
     * quiere abonar el paciente, y allá se acota al saldo. Un cliente no debería
     * poder fijar cuánto paga.
     *
     * Si la clínica no conectó su cuenta de Mercado Pago, el servidor responde con
     * un mensaje claro ("puedes pagar en recepción") en vez de un error técnico.
     *
     * @param monto null = pagar todo lo que debe.
     */
    suspend fun pagarTratamiento(tratamientoId: String, monto: Double? = null): ResultadoPago {
    val tk = token() ?: return ResultadoPago.Error("Sesión expirada")
    val cuerpo = buildString {
        append("""{"tratamientoId":"$tratamientoId"""")
        if (monto != null) append(""","monto":$monto""")
        append("}")
    }
    val resp = runCatching {
        http.post("${Supabase.SITE_URL}/api/paciente/pagar") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(cuerpo)
        }
    }.getOrNull() ?: return ResultadoPago.Error("Sin conexión. Revisa tu internet.")

    val body = runCatching { json.parseToJsonElement(resp.bodyAsText()).jsonObject }.getOrNull()
    val url = body?.str("url")
    return if (resp.status == HttpStatusCode.OK && !url.isNullOrBlank()) {
        ResultadoPago.Ok(url)
    } else {
        ResultadoPago.Error(body?.str("error") ?: "No se pudo abrir el pago")
    }
    }

}
