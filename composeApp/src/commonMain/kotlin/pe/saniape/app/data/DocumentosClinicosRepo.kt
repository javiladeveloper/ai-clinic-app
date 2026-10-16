package pe.saniape.app.data

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import pe.saniape.app.data.offline.CacheLectura
import pe.saniape.app.data.staff.DOC_CERTIFICADO
import pe.saniape.app.data.staff.DOC_DESCANSO
import pe.saniape.app.data.staff.DOC_INFORME
import pe.saniape.app.data.staff.DOC_ORDEN
import pe.saniape.app.data.staff.ExamenOrden
import pe.saniape.app.data.staff.diaLimaDe
import pe.saniape.app.data.staff.esTipoDocMedico
import pe.saniape.app.data.staff.formatearDinero
import pe.saniape.app.data.staff.nombreTipoDoc
import pe.saniape.app.data.staff.normalizarMoneda

// ─────────────────────────────────────────────────────────────────────────────
// 🩺 DOCUMENTOS MÉDICOS del portal del paciente: informes, descansos, órdenes de
// exámenes, presupuestos dentales y cronograma de cuotas. Gemelo de
// app/(portal)/MisDocumentosClinicos.tsx; datos de GET
// /api/paciente/mis-documentos-clinicos (contrato: docs/app-contrato-portal-
// paciente.md §5 de la web). Aquí: modelos, parser tolerante y reglas puras
// (testeables sin red); la vista vive en ui/SeccionDocumentosMedicos.kt.
// ─────────────────────────────────────────────────────────────────────────────

/** Clínica que emitió el documento (cabecera; puede faltar). */
data class ClinicaDocPortal(val nombre: String, val logoUrl: String?, val colorPrincipal: String?)

data class DescansoPortal(val desde: String?, val hasta: String?, val dias: Int?)

data class DiagnosticoPortal(val codigo: String?, val descripcion: String)

/** Un informe, descanso u orden de exámenes EMITIDO y visible para el paciente. */
data class DocMedicoPortal(
    val id: String,
    /** informe · descanso · orden_examenes */
    val tipo: String,
    /** 'YYYY-MM-DD' */
    val fecha: String,
    /** "IM N° 000012" */
    val numeroTexto: String,
    val titulo: String,
    /** Línea lista para mostrar bajo el título (o null). */
    val detalle: String?,
    val clinica: ClinicaDocPortal?,
    /** "Dra. Rosa Quispe" o null. */
    val profesional: String?,
    val diagnosticos: List<DiagnosticoPortal>,
    val diagnosticoReservado: Boolean,
    val descanso: DescansoPortal?,
    val examenes: List<ExamenOrden>,
    val indicaciones: String?,
    /** Ruta RELATIVA de la hoja HTML (siempre bajo [RUTA_DOCS]: el Bearer nunca sale a otro lado). */
    val urlHtml: String,
)

data class ItemPresupuestoPortal(val nombre: String, val detalle: String?, val fase: Int?, val precio: Double)

/** Estados que manda el servidor (contrato §5). */
const val PRESUPUESTO_ENVIADO = "Enviado"
const val PRESUPUESTO_ACEPTADO = "Aceptado"
const val PRESUPUESTO_RECHAZADO = "Rechazado"
const val PRESUPUESTO_VENCIDO = "Vencido"
const val PRESUPUESTO_REEMPLAZADO = "Reemplazado"

data class PresupuestoPortal(
    val id: String,
    /** 'YYYY-MM-DD' en la zona de la clínica (el servidor manda un instante). */
    val fecha: String,
    val titulo: String,
    val detalle: String?,
    val estado: String,
    /** Solo el Enviado más reciente de cada clínica (y nunca sin [enlace]). */
    val puedeAceptar: Boolean,
    val total: Double,
    /** ISO 4217 ya normalizado (sin dato → PEN). */
    val moneda: String,
    val vence: String?,
    val aceptadoAt: String?,
    val items: List<ItemPresupuestoPortal>,
    val clinica: ClinicaDocPortal?,
    /** Ruta relativa al sitio de su página pública ("/presupuesto/<token>"), o null (Reemplazado). */
    val enlace: String?,
)

/** Estados de una cuota (lib/cuotas.ts de la web). */
const val CUOTA_PAGADA = "pagada"
const val CUOTA_ATRASADA = "atrasada"
const val CUOTA_VENCE_HOY = "vence_hoy"
const val CUOTA_POR_VENCER = "por_vencer"
const val CUOTA_PROGRAMADA = "programada"

data class CuotaPortal(
    /** 0 = inicial. */
    val numero: Int,
    val monto: Double,
    val vence: String,
    val estado: String,
    val pagado: Double,
    val saldo: Double,
    val diasAtraso: Int,
    val diasParaVencer: Int,
    /** "Inicial", "1 de 3". */
    val etiqueta: String,
    /** "Atrasada 14 días · parcial". */
    val estadoTexto: String,
)

data class ResumenCuotasPortal(
    val totalPlan: Double,
    val pagado: Double,
    /** "Te falta pagar". */
    val saldo: Double,
    val pagadas: Int,
    val nCuotas: Int,
    val atrasadas: Int,
    val montoAtrasado: Double,
    val alDia: Boolean,
)

/** El cronograma de cuotas de UN tratamiento, en la moneda de ese tratamiento. */
data class PlanCuotasPortal(
    val id: String,
    val tratamientoId: String?,
    val titulo: String,
    val clinica: ClinicaDocPortal?,
    val profesional: String?,
    val moneda: String,
    val cuotas: List<CuotaPortal>,
    val resumen: ResumenCuotasPortal,
)

data class DocumentosClinicos(
    val informes: List<DocMedicoPortal>,
    val descansos: List<DocMedicoPortal>,
    val ordenes: List<DocMedicoPortal>,
    val presupuestos: List<PresupuestoPortal>,
    val cuotas: List<PlanCuotasPortal>,
    /**
     * 🧠 Certificados de salud mental (clave `certificados`, contrato §14). Un
     * servidor viejo no la manda: queda vacía y nada cambia.
     */
    val certificados: List<DocMedicoPortal> = emptyList(),
) {
    val tieneDatos: Boolean
        get() = informes.isNotEmpty() || descansos.isNotEmpty() || ordenes.isNotEmpty() ||
            presupuestos.isNotEmpty() || cuotas.isNotEmpty() || certificados.isNotEmpty()

    companion object {
        val VACIOS = DocumentosClinicos(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}

/** Raíz de la lista y de las hojas: lo único a lo que se le manda el Bearer del paciente. */
const val RUTA_DOCS = "/api/paciente/mis-documentos-clinicos"

// ── Parser (tolerante) ───────────────────────────────────────────────────────

private fun JsonObject.txtDoc(k: String): String? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

/** Número que puede llegar como texto (numeric de Postgres), null o basura → null. */
private fun JsonObject.numDoc(k: String): Double? = txtDoc(k)?.toDoubleOrNull()?.takeIf { it.isFinite() }

private fun JsonObject.enteroDoc(k: String): Int? = numDoc(k)?.toInt()

private fun JsonObject.boolDoc(k: String): Boolean = txtDoc(k)?.lowercase() == "true"

private fun JsonObject.arr(k: String): List<JsonObject> = (this[k] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

private fun clinicaDe(o: JsonObject?): ClinicaDocPortal? {
    val nombre = o?.txtDoc("nombre") ?: return null
    return ClinicaDocPortal(nombre, o.txtDoc("logo_url"), o.txtDoc("color_principal"))
}

private val RE_UUIDISH = Regex("^[A-Za-z0-9-]{1,64}$")

/**
 * La ruta de la hoja: la del servidor SOLO si es relativa y cuelga de
 * [RUTA_DOCS] (la app le manda el Bearer: nunca a otro host ni a otra ruta);
 * si no, se arma con el tipo y el id.
 */
fun rutaHojaSegura(urlHtml: String?, tipo: String, id: String): String {
    val u = urlHtml?.trim().orEmpty()
    val valida = u.startsWith("$RUTA_DOCS/") && !u.contains("..") && !u.contains("://") && !u.contains('\\')
    return if (valida) u else "$RUTA_DOCS/$tipo/$id"
}

/** El enlace del presupuesto solo si es una ruta del propio sitio ("/…", nunca "//host" ni "https://"). */
fun enlaceSeguro(enlace: String?): String? {
    val e = enlace?.trim().orEmpty()
    return e.takeIf { it.startsWith("/") && !it.startsWith("//") && !it.contains("://") && !it.contains('\\') }
}

private fun docDe(o: JsonObject, tipoLista: String): DocMedicoPortal? {
    val id = o.txtDoc("id")?.takeIf { RE_UUIDISH.matches(it) } ?: return null
    // El tipo del propio documento; sin él (o desconocido), el de la lista donde vino.
    val tipo = o.txtDoc("tipo")?.takeIf { esTipoDocMedico(it) || it == tipoLista } ?: tipoLista
    val prof = o["profesional"]
    val profesional = (prof as? JsonObject)?.txtDoc("nombre")
        ?: (prof as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
    val reservado = o.boolDoc("diagnosticoReservado")
    val desc = o["descanso"] as? JsonObject
    return DocMedicoPortal(
        id = id,
        tipo = tipo,
        fecha = o.txtDoc("fecha")?.take(10).orEmpty(),
        numeroTexto = o.txtDoc("numeroTexto").orEmpty(),
        titulo = o.txtDoc("titulo") ?: nombreTipoDoc(tipo),
        detalle = o.txtDoc("detalle"),
        clinica = clinicaDe(o["clinica"] as? JsonObject),
        profesional = profesional,
        // Reservado = no viaja (y si viajara por error, no se muestra).
        diagnosticos = if (reservado) emptyList() else o.arr("diagnosticos").mapNotNull { d ->
            val descripcion = d.txtDoc("descripcion") ?: d.txtDoc("codigo") ?: return@mapNotNull null
            DiagnosticoPortal(d.txtDoc("codigo"), descripcion)
        },
        diagnosticoReservado = reservado,
        descanso = if (tipo == DOC_DESCANSO && desc != null) {
            DescansoPortal(desc.txtDoc("desde")?.take(10), desc.txtDoc("hasta")?.take(10), desc.enteroDoc("dias"))
        } else null,
        examenes = (o["examenes"] as? JsonArray).orEmpty().mapNotNull { e ->
            val x = e as? JsonObject
            val nombre = x?.txtDoc("nombre")
                ?: (e as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() }
            nombre?.let { ExamenOrden(it, x?.txtDoc("indicacion")) }
        },
        indicaciones = o.txtDoc("indicaciones"),
        urlHtml = rutaHojaSegura(o.txtDoc("urlHtml"), tipo, id),
    )
}

private val ESTADOS_PRESUPUESTO = setOf(
    PRESUPUESTO_ENVIADO, PRESUPUESTO_ACEPTADO, PRESUPUESTO_RECHAZADO, PRESUPUESTO_VENCIDO, PRESUPUESTO_REEMPLAZADO,
)

private fun presupuestoDe(o: JsonObject): PresupuestoPortal? {
    val id = o.txtDoc("id") ?: return null
    // Un estado que la app no conoce se muestra como vencido (sin acción), como la web.
    val estado = o.txtDoc("estado")?.takeIf { it in ESTADOS_PRESUPUESTO } ?: PRESUPUESTO_VENCIDO
    val enlace = enlaceSeguro(o.txtDoc("enlace"))
    val items = o.arr("items").mapNotNull { i ->
        val nombre = i.txtDoc("nombre") ?: return@mapNotNull null
        ItemPresupuestoPortal(nombre, i.txtDoc("detalle"), i.enteroDoc("fase"), i.numDoc("precio") ?: 0.0)
    }
    return PresupuestoPortal(
        id = id,
        fecha = diaLimaDe(o.txtDoc("fecha")),
        titulo = o.txtDoc("titulo") ?: "Presupuesto",
        detalle = o.txtDoc("detalle"),
        estado = estado,
        // Aceptar exige enlace y el estado Enviado (un servidor que se equivoque no abre un botón vacío).
        puedeAceptar = o.boolDoc("puedeAceptar") && enlace != null && estado == PRESUPUESTO_ENVIADO,
        total = o.numDoc("total") ?: items.sumOf { it.precio },
        moneda = normalizarMoneda(o.txtDoc("moneda")),
        vence = o.txtDoc("vence")?.let { diaLimaDe(it) },
        aceptadoAt = o.txtDoc("aceptadoAt")?.let { diaLimaDe(it) },
        items = items,
        clinica = clinicaDe(o["clinica"] as? JsonObject),
        // Reemplazado: nunca se ofrece (ni siquiera "ver") el viejo.
        enlace = enlace.takeIf { estado != PRESUPUESTO_REEMPLAZADO },
    )
}

private val ESTADOS_CUOTA = setOf(CUOTA_PAGADA, CUOTA_ATRASADA, CUOTA_VENCE_HOY, CUOTA_POR_VENCER, CUOTA_PROGRAMADA)

/** Texto por defecto de un estado (si el servidor no manda `estadoTexto`). */
fun textoEstadoCuota(estado: String): String = when (estado) {
    CUOTA_PAGADA -> "Pagada"
    CUOTA_ATRASADA -> "Atrasada"
    CUOTA_VENCE_HOY -> "Vence hoy"
    CUOTA_POR_VENCER -> "Por vencer"
    else -> "Programada"
}

/** "Inicial" / "N de M" (etiquetaCuota de la web) si el servidor no la manda. */
fun etiquetaCuota(numero: Int, nCuotas: Int): String =
    if (numero == 0) "Inicial" else if (nCuotas > 0) "$numero de $nCuotas" else "Cuota $numero"

private fun cuotaDe(o: JsonObject, nCuotas: Int): CuotaPortal? {
    val numero = o.enteroDoc("numero") ?: return null
    val monto = o.numDoc("monto") ?: 0.0
    val estado = o.txtDoc("estado")?.takeIf { it in ESTADOS_CUOTA } ?: CUOTA_PROGRAMADA
    val pagado = o.numDoc("pagado") ?: if (estado == CUOTA_PAGADA) monto else 0.0
    return CuotaPortal(
        numero = numero,
        monto = monto,
        vence = o.txtDoc("vence")?.take(10).orEmpty(),
        estado = estado,
        pagado = pagado,
        saldo = o.numDoc("saldo") ?: (monto - pagado).coerceAtLeast(0.0),
        diasAtraso = o.enteroDoc("diasAtraso") ?: 0,
        diasParaVencer = o.enteroDoc("diasParaVencer") ?: 0,
        etiqueta = o.txtDoc("etiqueta") ?: etiquetaCuota(numero, nCuotas),
        estadoTexto = o.txtDoc("estadoTexto") ?: textoEstadoCuota(estado),
    )
}

/** Resumen calculado con las propias cuotas (si el servidor no lo manda o viene incompleto). */
fun resumenDeCuotas(cuotas: List<CuotaPortal>): ResumenCuotasPortal {
    val atrasadas = cuotas.filter { it.estado == CUOTA_ATRASADA }
    return ResumenCuotasPortal(
        totalPlan = cuotas.sumOf { it.monto },
        pagado = cuotas.sumOf { it.pagado },
        saldo = cuotas.sumOf { it.saldo },
        pagadas = cuotas.count { it.estado == CUOTA_PAGADA },
        nCuotas = cuotas.count { it.numero > 0 },
        atrasadas = atrasadas.size,
        montoAtrasado = atrasadas.sumOf { it.saldo },
        alDia = atrasadas.isEmpty(),
    )
}

private fun planCuotasDe(o: JsonObject): PlanCuotasPortal? {
    val id = o.txtDoc("id") ?: o.txtDoc("tratamientoId") ?: return null
    val r = o["resumen"] as? JsonObject
    val crudas = o.arr("cuotas")
    val nCuotasServidor = r?.enteroDoc("nCuotas") ?: crudas.count { (it.enteroDoc("numero") ?: 0) > 0 }
    val cuotas = crudas.mapNotNull { cuotaDe(it, nCuotasServidor) }.sortedBy { it.numero }
    if (cuotas.isEmpty()) return null
    val calc = resumenDeCuotas(cuotas)
    val resumen = if (r == null) calc else ResumenCuotasPortal(
        totalPlan = r.numDoc("totalPlan") ?: calc.totalPlan,
        pagado = r.numDoc("pagado") ?: calc.pagado,
        saldo = r.numDoc("saldo") ?: calc.saldo,
        pagadas = r.enteroDoc("pagadas") ?: calc.pagadas,
        nCuotas = r.enteroDoc("nCuotas") ?: calc.nCuotas,
        atrasadas = r.enteroDoc("atrasadas") ?: calc.atrasadas,
        montoAtrasado = r.numDoc("montoAtrasado") ?: calc.montoAtrasado,
        alDia = r.txtDoc("alDia")?.let { it.lowercase() == "true" } ?: calc.alDia,
    )
    val prof = o["profesional"]
    return PlanCuotasPortal(
        id = id,
        tratamientoId = o.txtDoc("tratamientoId"),
        titulo = o.txtDoc("titulo") ?: "Mis cuotas",
        clinica = clinicaDe(o["clinica"] as? JsonObject),
        profesional = (prof as? JsonObject)?.txtDoc("nombre")
            ?: (prof as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" },
        moneda = normalizarMoneda(o.txtDoc("moneda")),
        cuotas = cuotas,
        resumen = resumen,
    )
}

/**
 * JSON de /api/paciente/mis-documentos-clinicos → [DocumentosClinicos].
 * Tolerante: números como texto o null, campos ausentes, listas que faltan (un
 * servidor viejo o una parte que no se pudo leer llega vacía); lo que no tiene
 * id se salta en vez de romper la lista. Null = el texto no es JSON válido.
 */
fun parsearDocumentosClinicos(texto: String): DocumentosClinicos? {
    val raiz = runCatching { Json.parseToJsonElement(texto).jsonObject }.getOrNull() ?: return null
    fun docs(k: String, tipo: String) = raiz.arr(k).mapNotNull { docDe(it, tipo) }.distinctBy { it.id }
    return DocumentosClinicos(
        informes = docs("informes", DOC_INFORME),
        descansos = docs("descansos", DOC_DESCANSO),
        ordenes = docs("ordenes", DOC_ORDEN),
        presupuestos = raiz.arr("presupuestos").mapNotNull { presupuestoDe(it) }.distinctBy { it.id },
        cuotas = raiz.arr("cuotas").mapNotNull { planCuotasDe(it) }.distinctBy { it.id },
        // Clave nueva (§14): si falta, vacía.
        certificados = docs("certificados", DOC_CERTIFICADO),
    )
}

// ── Reglas de presentación (puras) ───────────────────────────────────────────

/** Tono semántico de un estado (la vista lo traduce a colores del tema). */
enum class TonoDoc { OK, ERROR, PEND, NEUTRO }

/** Colores de las cuotas, los del portal web (CronogramaCuotas): verde · rojo · ámbar · ámbar · gris. */
fun tonoCuota(estado: String): TonoDoc = when (estado) {
    CUOTA_PAGADA -> TonoDoc.OK
    CUOTA_ATRASADA -> TonoDoc.ERROR
    CUOTA_VENCE_HOY, CUOTA_POR_VENCER -> TonoDoc.PEND
    else -> TonoDoc.NEUTRO
}

/** Etiqueta y tono del estado de un presupuesto (ESTADO_PRESUPUESTO de la web). */
fun estadoPresupuestoUi(estado: String): Pair<String, TonoDoc> = when (estado) {
    PRESUPUESTO_ENVIADO -> "Por revisar" to TonoDoc.PEND
    PRESUPUESTO_ACEPTADO -> "Aceptado" to TonoDoc.OK
    PRESUPUESTO_RECHAZADO -> "No aceptado" to TonoDoc.ERROR
    PRESUPUESTO_REEMPLAZADO -> "Reemplazado" to TonoDoc.NEUTRO
    else -> "Vencido" to TonoDoc.NEUTRO
}

/** Monto en la moneda DE ESE presupuesto o plan (nunca se suman monedas distintas). */
fun dineroDoc(monto: Double, moneda: String): String = formatearDinero(monto, moneda)

/**
 * Las cuotas que se ven sin abrir el cronograma: las que faltan pagar (las
 * atrasadas primero, como vienen en orden), hasta [max]. Con todo pagado,
 * ninguna ("✓ Plan completo"). [completo] = todas.
 */
fun cuotasVisibles(plan: PlanCuotasPortal, completo: Boolean, max: Int = 4): List<CuotaPortal> =
    if (completo) plan.cuotas else plan.cuotas.filter { it.estado != CUOTA_PAGADA }.take(max)

/** Línea "Dra. Rosa Quispe · 🏥 Clínica Andina" (lineaOrigen de la web). */
fun lineaOrigenDoc(profesional: String?, clinica: ClinicaDocPortal?): String =
    listOfNotNull(profesional, clinica?.let { "🏥 ${it.nombre}" }).joinToString(" · ")

/** Nombres de los ítems: "Resina · Profilaxis y 2 más". */
fun resumenItemsPresupuesto(p: PresupuestoPortal): String {
    val n = p.items.map { it.nombre }
    return n.take(3).joinToString(" · ") + if (n.size > 3) " y ${n.size - 3} más" else ""
}

// ── Red ──────────────────────────────────────────────────────────────────────

/**
 * Documentos médicos del paciente (portal). Mismo camino seguro que recetas /
 * nutrición: API web con el Bearer del paciente; el servidor decide qué fichas
 * son suyas y qué documentos están visibles.
 *
 * Tolerante: un servidor viejo sin el endpoint (404) o una cuenta sin ficha
 * (403) → "sin datos" y la sección no aparece; 500/sin red → null (la UI
 * conserva lo que tenga). Caché por USUARIO: el descanso se muestra en el
 * trabajo o el colegio, donde la señal puede fallar.
 */
object DocumentosClinicosRepo {

    private val http = crearHttpClient()

    private fun clave(): String? =
        Supabase.client.auth.currentUserOrNull()?.id?.let { "portal:documentos-clinicos:$it" }

    /** Lo último guardado para este usuario, o null si no hay nada. */
    fun guardados(): DocumentosClinicos? {
        val k = clave() ?: return null
        val crudo = CacheLectura.leer(k) ?: return null
        return parsearDocumentosClinicos(crudo)
    }

    /** Desde el servidor. Null = no se pudo saber (la UI conserva lo que tenga). */
    suspend fun cargar(): DocumentosClinicos? {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken ?: return null
        val resp = withTimeoutOrNull(12_000) {
            runCatching {
                http.get("${Supabase.SITE_URL}$RUTA_DOCS") { header("Authorization", "Bearer $tk") }
            }.getOrNull()
        } ?: return null
        // 403 = cuenta sin ficha vinculada; 404 = servidor sin esta sección todavía.
        if (resp.status == HttpStatusCode.Forbidden || resp.status == HttpStatusCode.NotFound) {
            clave()?.let { CacheLectura.borrar(it) }
            return DocumentosClinicos.VACIOS
        }
        if (resp.status != HttpStatusCode.OK) return null
        val texto = runCatching { resp.bodyAsText() }.getOrNull() ?: return null
        val datos = parsearDocumentosClinicos(texto) ?: return null
        clave()?.let { k ->
            if (datos.tieneDatos) CacheLectura.guardar(k, texto) else CacheLectura.borrar(k)
        }
        return datos
    }

    /**
     * La hoja de un documento como HTML autocontenido (el mismo papel que imprime
     * la clínica). [urlHtml] ya pasó por [rutaHojaSegura]. null = no se pudo
     * (sin red, anulado u ocultado desde que se cargó la lista: 404).
     */
    suspend fun hoja(urlHtml: String): String? {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken ?: return null
        if (!urlHtml.startsWith("$RUTA_DOCS/")) return null
        val resp = withTimeoutOrNull(15_000) {
            runCatching {
                http.get("${Supabase.SITE_URL}$urlHtml") { header("Authorization", "Bearer $tk") }
            }.getOrNull()
        } ?: return null
        if (resp.status != HttpStatusCode.OK) return null
        return runCatching { resp.bodyAsText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
