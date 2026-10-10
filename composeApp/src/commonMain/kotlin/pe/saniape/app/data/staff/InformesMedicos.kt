package pe.saniape.app.data.staff

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// ─────────────────────────────────────────────────────────────────────────────
// DOCUMENTOS MÉDICOS IMPRESOS — informe médico, descanso médico (certificado) y
// orden de exámenes. GEMELO de lib/informes-medicos.ts de la web (si cambia uno,
// cambia el otro). Contrato: docs/psiquiatria-documentos-medicos.md §4.
//
// Todos los planes: es papel que el médico firma y sella. EMITIR y ANULAR van por
// POST /api/staff/informe/{emitir,anular} (el servidor y la RLS validan quién
// firma); LEER va directo a `informes_medicos` (la RLS acota). Aquí solo lo puro:
// validaciones (las mismas que la web, para avisar antes de mandar), regla de
// firmante, visibilidad, prellenado desde la atención y armado del JSON.
// ─────────────────────────────────────────────────────────────────────────────

const val DOC_INFORME = "informe"
const val DOC_DESCANSO = "descanso"
const val DOC_ORDEN = "orden_examenes"

val TIPOS_DOC_MEDICO = listOf(DOC_INFORME, DOC_DESCANSO, DOC_ORDEN)

fun esTipoDocMedico(t: String?): Boolean = t in TIPOS_DOC_MEDICO

fun nombreTipoDoc(t: String): String = when (t) {
    DOC_INFORME -> "Informe médico"
    DOC_DESCANSO -> "Descanso médico"
    DOC_ORDEN -> "Orden de exámenes"
    else -> "Documento"
}

fun iconoTipoDoc(t: String): String = when (t) {
    DOC_INFORME -> "📄"
    DOC_DESCANSO -> "🛌"
    DOC_ORDEN -> "🧪"
    else -> "📄"
}

/** Prefijo del número impreso: cada tipo lleva su propio correlativo. */
private fun prefijoTipoDoc(t: String): String = when (t) {
    DOC_INFORME -> "IM"
    DOC_DESCANSO -> "DM"
    DOC_ORDEN -> "OE"
    else -> ""
}

/** "IM N° 000012" (formatearNumeroInforme). */
fun formatearNumeroInforme(tipo: String, n: Int?): String {
    val num = if (n == null || n < 1) "—" else n.toString().padStart(6, '0')
    return "${prefijoTipoDoc(tipo)} N° $num".trim()
}

/** Títulos que se ofrecen (el médico puede escribir otro). */
fun titulosSugeridos(tipo: String, psiq: Boolean = false): List<String> = when (tipo) {
    DOC_DESCANSO -> listOf("Certificado de descanso médico", "Certificado médico", "Constancia de atención")
    DOC_ORDEN -> listOf("Orden de exámenes auxiliares", "Orden de laboratorio")
    else -> if (psiq) listOf("Informe psiquiátrico", "Informe médico", "Certificado de salud mental")
    else listOf("Informe médico", "Certificado médico", "Constancia de atención")
}

data class ExamenOrden(val nombre: String, val indicacion: String? = null)

const val MAX_EXAMENES_ORDEN = 40
const val MAX_DIAS_DESCANSO = 366
/** Días hacia atrás que se acepta fechar un documento (MAX_DIAS_ATRAS_DOC). */
const val MAX_DIAS_ATRAS_DOC = 30

/** Lo que el formulario manda para emitir (BorradorInforme de la web). */
data class BorradorInforme(
    val tipo: String,
    val pacienteId: String,
    val terapeutaId: String?,
    /** 'YYYY-MM-DD' en la zona de la sede. */
    val fecha: String,
    val titulo: String? = null,
    val dirigidoA: String? = null,
    val motivo: String? = null,
    val antecedentes: String? = null,
    val examen: String? = null,
    val examenMental: String? = null,
    val escalas: String? = null,
    val tratamiento: String? = null,
    val recomendaciones: String? = null,
    val diagnosticos: List<DiagnosticoCie> = emptyList(),
    val ocultarDiagnostico: Boolean = false,
    val descansoDesde: String? = null,
    val descansoHasta: String? = null,
    val examenes: List<ExamenOrden> = emptyList(),
    val indicaciones: String? = null,
    val citaId: String? = null,
    val atencionId: String? = null,
    val tratamientoId: String? = null,
)

/** Prellenado del formulario (PrefillInforme de la web). */
data class PrefillInforme(
    val motivo: String? = null,
    val antecedentes: String? = null,
    val examen: String? = null,
    val examenMental: String? = null,
    val escalas: String? = null,
    val diagnosticos: List<DiagnosticoCie> = emptyList(),
    val tratamiento: String? = null,
    val recomendaciones: String? = null,
    val examenes: List<ExamenOrden> = emptyList(),
    val citaId: String? = null,
    val atencionId: String? = null,
    val tratamientoId: String? = null,
    val terapeutaId: String? = null,
    /** Psiquiatría: títulos, exámenes y CIE-10 propios. */
    val psiq: Boolean = false,
)

private val RE_FECHA = Regex("^\\d{4}-\\d{2}-\\d{2}$")

private fun txt(v: String?): String? = v?.trim()?.takeIf { it.isNotEmpty() }

private fun fechaONull(iso: String?): LocalDate? =
    if (iso == null || !RE_FECHA.matches(iso)) null else runCatching { LocalDate.parse(iso) }.getOrNull()

/** Días de descanso contando los dos extremos (del 9 al 15 = 7). null si las fechas no sirven. */
fun diasDescanso(desde: String?, hasta: String?): Int? {
    val d = fechaONull(desde) ?: return null
    val h = fechaONull(hasta) ?: return null
    return d.daysUntil(h) + 1
}

/** El "hasta" que corresponde a N días desde [desde] (inclusive). */
fun hastaPorDias(desde: String, dias: Int): String {
    val d = fechaONull(desde.take(10)) ?: return desde
    return d.plus(DatePeriod(days = maxOf(1, dias) - 1)).toString()
}

/** [fecha] menos N días. */
fun restarDias(fecha: String, dias: Int): String {
    val d = fechaONull(fecha.take(10)) ?: return fecha
    return d.plus(DatePeriod(days = -dias)).toString()
}

private val TILDES_EX = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'à' to 'a', 'è' to 'e', 'ì' to 'i', 'ò' to 'o', 'ù' to 'u')

/** Clave para no duplicar exámenes: GEMELA de la del trigger informe_medico_aprender_examenes. */
fun claveExamen(s: String): String =
    s.lowercase().map { TILDES_EX[it] ?: it }.joinToString("")
        .replace(Regex("\\s+"), " ").trim().replace(Regex("[\\s.,;:]+$"), "")

/** Sin vacíos ni repetidos (por nombre), con tope (normalizarExamenesOrden). */
fun normalizarExamenesOrden(lista: List<ExamenOrden>): List<ExamenOrden> {
    val vistos = mutableSetOf<String>()
    val out = mutableListOf<ExamenOrden>()
    for (x in lista) {
        val nombre = x.nombre.replace(Regex("\\s+"), " ").trim().take(160)
        if (nombre.length < 2) continue
        val k = claveExamen(nombre)
        if (!vistos.add(k)) continue
        out += ExamenOrden(nombre, txt(x.indicacion)?.take(200))
        if (out.size >= MAX_EXAMENES_ORDEN) break
    }
    return out
}

/**
 * Diagnósticos sin vacíos ni repetidos (por código, o por texto si no tiene),
 * hasta 8, con tipo P/D/R: normalizarDiagnosticos de lib/cie10.ts.
 */
fun diagnosticosParaDocumento(l: List<DiagnosticoCie>): List<DiagnosticoCie> {
    val vistos = mutableSetOf<String>()
    val out = mutableListOf<DiagnosticoCie>()
    for (d in l) {
        val descripcion = d.descripcion.replace(Regex("\\s+"), " ").trim()
        val codigo = d.codigo?.trim()?.uppercase()?.ifBlank { null }
        if (descripcion.isEmpty() && codigo == null) continue
        val clave = if (codigo != null) "c:" + codigo.replace(Regex("[^A-Z0-9]"), "") else "t:" + claveExamen(descripcion)
        if (!vistos.add(clave)) continue
        out += DiagnosticoCie(codigo, descripcion.ifEmpty { codigo.orEmpty() }, if (d.tipo == "D" || d.tipo == "R") d.tipo else "P")
        if (out.size >= 8) break
    }
    return out
}

/**
 * Valida ANTES de mandar (validarInforme de la web; la base vuelve a validar).
 * [hoy] = 'YYYY-MM-DD' en la zona de la sede: la fecha entre hoy − 30 y hoy.
 */
fun validarInforme(b: BorradorInforme, hoy: String?): List<String> {
    val e = mutableListOf<String>()
    if (!esTipoDocMedico(b.tipo)) e += "Tipo de documento inválido."
    if (b.pacienteId.isBlank()) e += "Falta el paciente."
    if (b.terapeutaId.isNullOrBlank()) e += "Elige el profesional que firma."
    val f = fechaONull(b.fecha)
    if (f == null) e += "Fecha inválida."
    else {
        val h = fechaONull(hoy)
        if (h != null) {
            val dif = f.daysUntil(h)
            if (dif < 0) e += "La fecha del documento no puede ser futura."
            else if (dif > MAX_DIAS_ATRAS_DOC) e += "La fecha del documento no puede ser de hace más de $MAX_DIAS_ATRAS_DOC días."
        }
    }
    val dx = diagnosticosParaDocumento(b.diagnosticos)
    if (b.tipo == DOC_INFORME) {
        val algo = listOf(b.motivo, b.antecedentes, b.examen, b.examenMental, b.escalas, b.tratamiento, b.recomendaciones)
            .any { txt(it) != null } || dx.isNotEmpty()
        if (!algo) e += "El informe está vacío: escribe al menos el motivo, el examen o el diagnóstico."
    }
    if (b.tipo == DOC_DESCANSO) {
        val dias = diasDescanso(b.descansoDesde, b.descansoHasta)
        when {
            dias == null -> e += "Indica desde y hasta cuándo es el descanso."
            dias < 1 -> e += "La fecha \"hasta\" no puede ser anterior a \"desde\"."
            dias > MAX_DIAS_DESCANSO -> e += "El descanso no puede pasar de un año."
        }
    }
    if (b.tipo == DOC_ORDEN && normalizarExamenesOrden(b.examenes).isEmpty()) {
        e += "Agrega al menos un examen a la orden."
    }
    return e
}

/**
 * El cuerpo de `POST /api/staff/informe/emitir` (mismo contrato que la web:
 * BorradorInforme + claveCliente + seguimiento). Textos recortados (vacío → null);
 * el servidor descarta el cuerpo del informe en un descanso o una orden.
 */
fun cuerpoEmitirInforme(b: BorradorInforme, claveCliente: String, seguimiento: Boolean): JsonObject {
    fun t(s: String?): JsonPrimitive = txt(s)?.let { JsonPrimitive(it) } ?: JsonNull
    val desc = b.tipo == DOC_DESCANSO
    return buildJsonObject {
        put("tipo", b.tipo)
        put("pacienteId", b.pacienteId)
        put("terapeutaId", t(b.terapeutaId))
        put("fecha", b.fecha)
        put("titulo", t(b.titulo?.take(120)))
        put("dirigidoA", t(b.dirigidoA?.take(200)))
        // El cuerpo solo va en el INFORME: un descanso o una orden no mandan texto
        // que no se imprime (gemelo de filaInforme y del CHECK de la base).
        fun cuerpo(s: String?): JsonPrimitive = if (b.tipo == DOC_INFORME) t(s) else JsonNull
        put("motivo", cuerpo(b.motivo))
        put("antecedentes", cuerpo(b.antecedentes))
        put("examen", cuerpo(b.examen))
        put("examenMental", cuerpo(b.examenMental))
        put("escalas", cuerpo(b.escalas))
        put("tratamiento", cuerpo(b.tratamiento))
        put("recomendaciones", cuerpo(b.recomendaciones))
        put("diagnosticos", JsonArray(diagnosticosParaDocumento(b.diagnosticos).map { d ->
            buildJsonObject {
                put("codigo", d.codigo?.let { JsonPrimitive(it) } ?: JsonNull)
                put("descripcion", d.descripcion)
                put("tipo", d.tipo.ifBlank { "P" })
            }
        }))
        put("ocultarDiagnostico", b.ocultarDiagnostico)
        put("descansoDesde", if (desc) t(b.descansoDesde) else JsonNull)
        put("descansoHasta", if (desc) t(b.descansoHasta) else JsonNull)
        put("examenes", JsonArray(if (b.tipo == DOC_ORDEN) normalizarExamenesOrden(b.examenes).map { x ->
            buildJsonObject {
                put("nombre", x.nombre)
                put("indicacion", x.indicacion?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        } else emptyList()))
        put("indicaciones", t(b.indicaciones))
        put("citaId", t(b.citaId))
        put("atencionId", t(b.atencionId))
        put("tratamientoId", t(b.tratamientoId))
        put("claveCliente", claveCliente)
        put("seguimiento", seguimiento && b.tipo == DOC_ORDEN)
    }
}

// ── Quién emite, quién firma, quién ve ───────────────────────────────────────

/**
 * ¿Este profesional es médico u odontólogo? GEMELO de `esEmisorMedico` (y del
 * chequeo INFORME_PROFESIONAL_NO_MEDICO de la base): alguna especialidad que
 * receta, o ninguna especialidad de otro rubro.
 */
fun esEmisorMedico(rubros: List<String?>): Boolean {
    val lista = rubros.filterNotNull().filter { it.isNotBlank() }
    return lista.any { it in RUBROS_QUE_RECETAN } || lista.none { it !in RUBROS_QUE_RECETAN }
}

/** ¿Quién puede EMITIR? Admin o el profesional vinculado, siempre con permiso `sesiones`. */
fun puedeEmitirDocumentos(rol: String?, miTerapeutaId: String?, permisoSesiones: Boolean): Boolean =
    permisoSesiones && (rol == "Admin" || !miTerapeutaId.isNullOrBlank())

/** ¿Puede firmar a nombre de [terapeutaId]? (el Admin, a nombre de cualquiera). */
fun puedeFirmarComo(rol: String?, miTerapeutaId: String?, terapeutaId: String?): Boolean {
    if (terapeutaId.isNullOrBlank()) return false
    return rol == "Admin" || miTerapeutaId == terapeutaId
}

/** Un posible firmante: lo que el selector necesita. */
data class FirmanteDoc(
    val id: String,
    val nombre: String,
    val cmp: String?,
    val estado: String,
    val rubros: List<String?>,
    val nombresEspecialidades: List<String> = emptyList(),
) {
    val psiquiatra: Boolean get() = nombresEspecialidades.any { esPsiquiatria(it) }
}

/**
 * Los que firman (el filtro de InformeMedicoForm.tsx): activos, con colegiatura
 * (≥ 3), médicos u odontólogos; el profesional (no Admin) solo a su nombre.
 */
fun firmantesDocumento(equipo: List<FirmanteDoc>, esAdmin: Boolean, miTerapeutaId: String?): List<FirmanteDoc> =
    equipo.filter { it.estado != "Inactivo" && (it.cmp?.trim()?.length ?: 0) >= 3 }
        .filter { esEmisorMedico(it.rubros) }
        .filter { esAdmin || it.id == miTerapeutaId }

/**
 * El firmante elegido al abrir (como el useEffect de la web): el Admin → el del
 * prellenado → él mismo → el único; el profesional → él mismo (o nadie).
 */
fun firmanteInicial(firmantes: List<FirmanteDoc>, esAdmin: Boolean, miTerapeutaId: String?, sugerido: String?): String {
    val propio = miTerapeutaId?.takeIf { id -> firmantes.any { it.id == id } }
    if (!esAdmin) return propio.orEmpty()
    val delPrefill = sugerido?.takeIf { id -> firmantes.any { it.id == id } }
    return delPrefill ?: propio ?: firmantes.singleOrNull()?.id.orEmpty()
}

/**
 * ¿La clínica tiene los documentos médicos? (documentosMedicosActivos): flujo
 * médico, o recetas de una clínica MÉDICA. DALU (recetas como hoja de
 * indicaciones) y RENOVA no ven nada. Si el servidor manda el dato ya resuelto
 * (`documentosMedicos`), manda ese; un servidor anterior no lo trae y se deduce:
 * recetas encendidas en una clínica NO médica llegan como `recetasOptIn`.
 */
fun documentosMedicosActivos(m: ModulosClinicos): Boolean =
    m.documentosMedicos ?: (m.flujoMedico || (m.recetas && !m.recetasOptIn && m.mapaReceta.activo))

/**
 * ¿La ficha de este paciente muestra los documentos médicos? (esPacienteDocs de
 * la ficha web): clínica con documentos + paciente médico (flujo) o de recetas
 * en una clínica médica.
 */
fun pacienteRecibeDocumentos(m: ModulosClinicos, esPacienteMedico: Boolean, esPacienteReceta: Boolean): Boolean =
    documentosMedicosActivos(m) && (esPacienteMedico || (esPacienteReceta && !m.recetasOptIn))

/** El nombre de la pestaña, como la web. */
fun etiquetaPestanaRecetas(esPacienteReceta: Boolean, esPacienteDocs: Boolean): String = when {
    esPacienteReceta && esPacienteDocs -> "💊 Recetas y documentos"
    esPacienteDocs -> "📄 Documentos médicos"
    else -> "💊 Recetas"
}

// ── Exámenes sugeridos ───────────────────────────────────────────────────────

/** EXAMENES_FRECUENTES_MEDICINA de lib/atencion-medica.ts. */
val EXAMENES_FRECUENTES_MEDICINA = listOf(
    "Hemograma completo", "Glucosa en ayunas", "Perfil lipídico", "Creatinina", "Urea",
    "Examen completo de orina", "Urocultivo", "Perfil hepático", "Hemoglobina glicosilada (HbA1c)",
    "TSH", "Proteína C reactiva", "Grupo sanguíneo y factor Rh", "Prueba rápida VIH / sífilis",
    "Radiografía de tórax", "Ecografía abdominal", "Electrocardiograma",
)

/** EXAMENES_FRECUENTES_PSIQUIATRIA de lib/atencion-medica.ts. */
val EXAMENES_FRECUENTES_PSIQUIATRIA = listOf(
    "Hemograma completo", "Perfil tiroideo (TSH, T4 libre)", "Perfil hepático", "Perfil renal (urea, creatinina)",
    "Glucosa en ayunas", "Perfil lipídico", "Electrolitos séricos (Na, K, Cl)", "Litemia (nivel de litio en sangre)",
    "Ácido valproico sérico", "Prolactina", "Electrocardiograma", "Prueba de embarazo (β-HCG)",
    "Toxicológico en orina (drogas de abuso)", "Vitamina B12 y ácido fólico", "Hemoglobina glicosilada (HbA1c)",
    "Carbamazepina sérica",
)

data class ExamenAprendido(val nombre: String, val usos: Int = 0)

/** Lo aprendido por la clínica primero (más usado arriba), luego la base del rubro, sin duplicar. */
fun sugerenciasExamenes(aprendidos: List<ExamenAprendido>, psiq: Boolean = false): List<String> {
    val base = if (psiq) EXAMENES_FRECUENTES_PSIQUIATRIA + EXAMENES_FRECUENTES_MEDICINA else EXAMENES_FRECUENTES_MEDICINA
    val vistos = mutableSetOf<String>()
    val out = mutableListOf<String>()
    for (n in aprendidos.sortedByDescending { it.usos }.map { it.nombre } + base) {
        val k = claveExamen(n)
        if (k.isEmpty() || !vistos.add(k)) continue
        out += n
    }
    return out
}

/** Typeahead: coincide al inicio de cualquier palabra, sin tildes (filtrarExamenes). */
fun filtrarExamenes(lista: List<String>, texto: String, max: Int = 8): List<String> {
    val q = claveExamen(texto)
    if (q.length < 2) return emptyList()
    val sep = Regex("[\\s(/,-]+")
    return lista.filter { n ->
        val k = claveExamen(n)
        k.startsWith(q) || k.split(sep).any { it.startsWith(q) }
    }.take(max)
}

// ── Prellenado desde la atención ─────────────────────────────────────────────

/** Lo que trae una atención médica para prellenar (FuenteAtencion de la web). */
data class FuenteAtencion(
    val motivoConsulta: String? = null,
    val tiempoEnfermedad: String? = null,
    val relato: String? = null,
    val examenFisico: String? = null,
    val examenMental: String? = null,
    /** Ya en texto ("PHQ-9: 14 / 27 — Depresión moderada", una por línea). */
    val escalasTexto: String? = null,
    val diagnosticos: List<DiagnosticoCie> = emptyList(),
    val tratamiento: String? = null,
    val planTrabajo: String? = null,
    val examenes: List<ExamenOrden> = emptyList(),
)

/**
 * Prellenado desde la atención y los antecedentes de la ficha (prefillDesdeAtencion).
 * El médico lo EDITA antes de emitir: nada sale impreso sin pasar por sus ojos.
 */
fun prefillDesdeAtencion(
    a: FuenteAtencion?, antecedentes: String?, alergias: String?, medicacionActual: String?,
): PrefillInforme {
    val motivo = listOfNotNull(
        txt(a?.motivoConsulta),
        txt(a?.tiempoEnfermedad)?.let { "Tiempo de enfermedad: $it" },
        txt(a?.relato),
    ).joinToString("\n")
    val ant = listOfNotNull(
        txt(antecedentes),
        txt(alergias)?.let { "Alergias: $it" },
        txt(medicacionActual)?.let { "Medicación habitual: $it" },
    ).joinToString("\n")
    return PrefillInforme(
        motivo = motivo.ifBlank { null },
        antecedentes = ant.ifBlank { null },
        examen = txt(a?.examenFisico),
        examenMental = txt(a?.examenMental),
        escalas = txt(a?.escalasTexto),
        diagnosticos = diagnosticosParaDocumento(a?.diagnosticos.orEmpty()),
        tratamiento = txt(a?.tratamiento),
        recomendaciones = txt(a?.planTrabajo),
        examenes = normalizarExamenesOrden(a?.examenes.orEmpty()),
    )
}

// ── Lo emitido (lista de la ficha) ───────────────────────────────────────────

/** Una fila de `informes_medicos` vista por el staff (solo lo que la lista usa). */
data class InformeMedicoApp(
    val id: String,
    val tipo: String,
    val numero: Int?,
    val fecha: String,
    val titulo: String?,
    val motivo: String?,
    val terapeutaId: String?,
    val estado: String,
    val motivoAnulacion: String?,
    val ocultarDiagnostico: Boolean,
    val descansoDesde: String?,
    val descansoHasta: String?,
    val descansoDias: Int?,
    val examenes: List<ExamenOrden>,
    val profesionalNombre: String?,
    val profesionalColegiatura: String?,
) {
    val anulado: Boolean get() = estado == "Anulado"
    val numeroTexto: String get() = formatearNumeroInforme(tipo, numero)
}

/** Columnas que la lista necesita (no el `*`: sin el cuerpo ni los datos congelados del paciente). */
const val SELECT_INFORMES_STAFF =
    "id, tipo, numero, fecha, titulo, motivo, terapeuta_id, estado, motivo_anulacion, ocultar_diagnostico, " +
        "descanso_desde, descanso_hasta, descanso_dias, examenes, profesional, created_at"

private fun JsonObject.s(k: String): String? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() }

/** Fila → InformeMedicoApp. Tolerante: sin id o con un tipo desconocido se descarta. */
fun aInformeMedico(o: JsonObject): InformeMedicoApp? {
    val id = o.s("id") ?: return null
    val tipo = o.s("tipo")?.takeIf { esTipoDocMedico(it) } ?: return null
    val ex = (o["examenes"] as? JsonArray).orEmpty().mapNotNull { e ->
        val x = e as? JsonObject
        val nombre = x?.s("nombre") ?: (e as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
        nombre?.let { ExamenOrden(it, x?.s("indicacion")) }
    }
    val prof = o["profesional"] as? JsonObject
    return InformeMedicoApp(
        id = id,
        tipo = tipo,
        numero = o.s("numero")?.toDoubleOrNull()?.toInt(),
        fecha = o.s("fecha")?.take(10).orEmpty(),
        titulo = o.s("titulo"),
        motivo = o.s("motivo"),
        terapeutaId = o.s("terapeuta_id"),
        estado = o.s("estado") ?: "Emitido",
        motivoAnulacion = o.s("motivo_anulacion"),
        ocultarDiagnostico = o.s("ocultar_diagnostico") == "true",
        descansoDesde = o.s("descanso_desde")?.take(10),
        descansoHasta = o.s("descanso_hasta")?.take(10),
        descansoDias = o.s("descanso_dias")?.toDoubleOrNull()?.toInt(),
        examenes = normalizarExamenesOrden(ex),
        profesionalNombre = prof?.s("nombre"),
        profesionalColegiatura = prof?.s("colegiatura"),
    )
}

/** "dd/mm/aaaa" desde un ISO (fechaCorta de la web). */
private fun fechaCortaDoc(iso: String?): String {
    val p = iso?.take(10)?.split("-") ?: return "—"
    return if (p.size == 3) "${p[2]}/${p[1]}/${p[0]}" else iso
}

/** La línea de resumen de la lista (resumen() de DocumentosMedicosPaciente.tsx). */
fun resumenInforme(inf: InformeMedicoApp): String = when (inf.tipo) {
    DOC_DESCANSO -> "${inf.descansoDias ?: "?"} día(s): ${fechaCortaDoc(inf.descansoDesde)} → ${fechaCortaDoc(inf.descansoHasta)}"
    DOC_ORDEN -> inf.examenes.joinToString(" · ") { it.nombre }
    else -> inf.titulo ?: inf.motivo?.lineSequence()?.firstOrNull().orEmpty()
}

/** ¿La especialidad es de psiquiatría? Por el NOMBRE (en la base es medicina_general). */
fun esPsiquiatria(nombre: String?): Boolean =
    nombre.orEmpty().lowercase().map { TILDES_EX[it] ?: it }.joinToString("").contains("psiquiatr")

/** Especialidad de la clínica con su nombre y estado (para decidir psiquiatría). */
data class EspecialidadNombre(val id: String, val nombre: String, val estado: String? = null)

/**
 * ¿La atención es de psiquiatría? (esPsiq de ConsultaGuiada.tsx): por la
 * especialidad de la cita; sin ella, si todas las activas lo son. Nunca dental.
 */
fun atencionEsPsiquiatria(esps: List<EspecialidadNombre>, especialidadId: String?, dental: Boolean): Boolean {
    if (dental) return false
    if (!especialidadId.isNullOrBlank()) return esPsiquiatria(esps.firstOrNull { it.id == especialidadId }?.nombre)
    val activas = esps.filter { (it.estado ?: "Activa") == "Activa" }
    return activas.isNotEmpty() && activas.all { esPsiquiatria(it.nombre) }
}
