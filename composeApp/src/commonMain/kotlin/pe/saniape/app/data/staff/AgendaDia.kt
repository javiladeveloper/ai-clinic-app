package pe.saniape.app.data.staff

// ─────────────────────────────────────────────────────────────────────────────
// LO QUE SE RESUELVE EN EL DÍA, DESDE LA AGENDA (auditoría de flujos
// 2026-10-24, T2). GEMELO de lib/agenda-dia.ts (si cambia una, cambia la otra).
//
//  · «🚫 No vino» el MISMO día en «⋯ Más» (antes solo en el banner de citas
//    vencidas; cancelar una Sesión la borraba y la falta se perdía). Usa el
//    endpoint de siempre (/api/staff/cita/no-asistio).
//  · Documentos de la atención en «⋯ Más»: informe / descanso / constancia
//    (los del rubro, gemelo de perfilConsulta de lib/consulta-guiada-perfil.ts)
//    y 💉 mapa de aplicación (estética), abiertos con ESA cita.
//  · «💰 N atendidas sin cobrar · S/ X» de HOY y «🧮 Cerrar mi caja».
// ─────────────────────────────────────────────────────────────────────────────

private val FECHA_ISO = Regex("""^\d{4}-\d{2}-\d{2}$""")
private val HORA_HM = Regex("""^\d{2}:\d{2}$""")

/**
 * ¿«⋯ Más» ofrece «🚫 No vino»? Solo una cita abierta (Pendiente / Confirmada)
 * que ya llegó: de un día anterior, o de HOY con su hora ya empezada (sin hora:
 * el día ya llegó). Una futura no (eso es reprogramar o cancelar); la
 * completada tampoco (el servidor la rechaza: 409). [hoy] y [ahoraHora] son
 * los de la sede de la cita. Gemelo de `ofreceNoVino`.
 */
fun ofreceNoVino(estado: String?, fecha: String?, hora: String?, hoy: String, ahoraHora: String): Boolean {
    if (estado != "Pendiente" && estado != "Confirmada") return false
    val f = fecha.orEmpty().take(10)
    if (!FECHA_ISO.matches(f) || !FECHA_ISO.matches(hoy)) return false
    if (f < hoy) return true
    if (f > hoy) return false
    val h = hora.orEmpty().take(5)
    val ya = ahoraHora.take(5)
    if (!HORA_HM.matches(h)) return true
    if (!HORA_HM.matches(ya)) return false
    return h <= ya
}

// ── Documentos de la atención ────────────────────────────────────────────────

/** Un documento del rubro: tipo (informe / descanso / orden_examenes), el título que se imprime y el botón. */
data class DocumentoRubro(val tipo: String, val titulo: String?, val boton: String)

/** Gemelo de NOMBRE_CONSULTA (lib/consulta-guiada-perfil.ts). */
private val NOMBRE_CONSULTA_RUBRO = mapOf(
    "fisioterapia" to "Consulta de fisioterapia",
    "nutricion" to "Consulta de nutrición",
    "estetica" to "Consulta de estética",
    "capilar" to "Consulta capilar",
    "podologia" to "Consulta de podología",
)

private val DOCS_MEDICOS_RUBRO = listOf(
    DocumentoRubro(DOC_INFORME, null, "Informe médico"),
    DocumentoRubro(DOC_DESCANSO, null, "Descanso médico"),
    DocumentoRubro(DOC_ORDEN, null, "Orden de exámenes"),
)

const val TITULO_INFORME_PSICOLOGICO = "Informe psicológico"
const val TITULO_CONSTANCIA_PSICOLOGICA = "Constancia de atención psicológica"

/**
 * El apartado de una especialidad para sus documentos (gemelo de
 * `apartadoDeEspecialidad`, solo lo que decide la familia): podología por el
 * nombre; si no, el rubro guardado o el que delata el nombre. Médicos
 * (medicina general, psiquiatría, dermatología, odontología, gineco) →
 * "medica"; null = no se sabe (médica de siempre).
 */
fun apartadoDocumentos(rubro: String?, nombre: String?): String? {
    if (esPodologia(nombre)) return "podologia"
    return when (val r = rubro?.trim()?.takeIf { it.isNotEmpty() } ?: rubroPorNombre(nombre)) {
        "fisioterapia", "psicologia", "estetica", "nutricion", "capilar" -> r
        "medicina_general", "odontologia", "ginecologia" -> "medica"
        else -> null
    }
}

/**
 * El apartado de la atención (gemelo de `apartadoDeAtencion`): el de la
 * especialidad de la cita; sin ella, el de las del profesional si todas
 * coinciden; si no, el de las especialidades de la clínica si todas coinciden.
 */
fun apartadoDeAtencionDocs(
    especialidades: List<EspecialidadRef>,
    especialidadId: String?,
    especialidadesProfesional: List<String>?,
): String? {
    fun ap(id: String?) = especialidades.firstOrNull { it.id == id }?.let { apartadoDocumentos(it.rubro, it.nombre) }
    fun unico(xs: List<String?>): String? = xs.firstOrNull()?.takeIf { p -> xs.isNotEmpty() && xs.all { it != null && it == p } }
    if (!especialidadId.isNullOrBlank()) return ap(especialidadId)
    val prof = especialidadesProfesional.orEmpty().filter { it.isNotBlank() }
    if (prof.isNotEmpty()) return unico(prof.map { ap(it) })
    return unico(especialidades.map { apartadoDocumentos(it.rubro, it.nombre) })
}

/** Los documentos del rubro (gemelo de `perfilConsulta(apartado).documentos`). */
fun documentosDelRubro(apartado: String?): List<DocumentoRubro> {
    if (apartado == null || apartado == "medica") return DOCS_MEDICOS_RUBRO
    if (apartado == "psicologia") return listOf(
        DocumentoRubro(DOC_INFORME, TITULO_INFORME_PSICOLOGICO, TITULO_INFORME_PSICOLOGICO),
        DocumentoRubro(DOC_INFORME, TITULO_CONSTANCIA_PSICOLOGICA, "Constancia de atención"),
    )
    val titulo = NOMBRE_CONSULTA_RUBRO[apartado] ?: "Consulta"
    val de = titulo.replace(Regex("^Consulta (de )?"), "").lowercase().ifEmpty { "la atención" }
    return listOf(
        DocumentoRubro(DOC_INFORME, "Informe de $de", "Informe"),
        DocumentoRubro(DOC_INFORME, "Constancia de atención", "Constancia de atención"),
    )
}

/** Qué abre la acción (la tarjeta lo traduce a su AccionTarjeta). */
enum class ClaveDocumentoCita { INFORME, DESCANSO, ORDEN, CONSTANCIA, CONSENTIMIENTO, MAPA }

data class DocumentoCita(
    val clave: ClaveDocumentoCita,
    val texto: String,
    /** Informe / descanso / orden / constancia: el tipo y el título que se imprime. */
    val tipo: String? = null,
    val titulo: String? = null,
    /** Atención psicológica: títulos psicológicos en el formulario. */
    val psico: Boolean = false,
)

const val TEXTO_CONSENTIMIENTO_CITA = "✍️ Consentimiento informado"
const val TEXTO_MAPA_CITA = "💉 Mapa de aplicación"

/**
 * Los documentos que ofrece «⋯ Más» (gemelo de `documentosDeCita`). Cada
 * bandera llega YA resuelta con su permiso:
 *  · [docsRubro] solo en la cita COMPLETADA y con [puedeEmitirDocs]
 *    (documentosMedicosActivos + puedeEmitirDocumentos + historia clínica).
 *  · [consentimiento] y [mapaAplicacion]: en cualquier estado menos la cancelada.
 * Sin paciente: nada.
 */
fun documentosDeCita(
    estado: String?,
    pacienteId: String?,
    docsRubro: List<DocumentoRubro>?,
    puedeEmitirDocs: Boolean,
    consentimiento: Boolean,
    mapaAplicacion: Boolean,
    psico: Boolean = false,
): List<DocumentoCita> {
    if (pacienteId.isNullOrBlank()) return emptyList()
    val out = mutableListOf<DocumentoCita>()
    val viva = estado != "Cancelada"
    if (estado == "Completada" && puedeEmitirDocs) {
        docsRubro.orEmpty().forEach { d ->
            val constancia = d.tipo == DOC_INFORME && d.titulo.orEmpty().startsWith("Constancia", ignoreCase = true)
            val clave = when {
                constancia -> ClaveDocumentoCita.CONSTANCIA
                d.tipo == DOC_DESCANSO -> ClaveDocumentoCita.DESCANSO
                d.tipo == DOC_ORDEN -> ClaveDocumentoCita.ORDEN
                else -> ClaveDocumentoCita.INFORME
            }
            out.add(DocumentoCita(clave, "${iconoTipoDoc(d.tipo)} ${d.boton}", d.tipo, d.titulo, psico))
        }
    }
    if (viva && consentimiento) out.add(DocumentoCita(ClaveDocumentoCita.CONSENTIMIENTO, TEXTO_CONSENTIMIENTO_CITA))
    if (viva && mapaAplicacion) out.add(DocumentoCita(ClaveDocumentoCita.MAPA, TEXTO_MAPA_CITA))
    return out
}

// ── Lo atendido hoy sin cobrar ───────────────────────────────────────────────

/**
 * Las atenciones de HOY ya atendidas que nadie cobró: Consulta / Evaluación
 * completadas, con precio y sin `pagadaAt` (el mismo criterio que «💰 Por
 * cobrar» de Finanzas, acotado al día). La Sesión no entra: su dinero vive en
 * el tratamiento. Total en céntimos. Gemelo de `atendidasSinCobrar`.
 */
fun atendidasSinCobrar(citas: List<CitaStaff>, hoy: String): Pair<List<CitaStaff>, Double> {
    val filas = citas.filter {
        it.fecha.take(10) == hoy && it.estado == "Completada" &&
            (it.tipo == "Consulta" || it.tipo == "Evaluación") &&
            (it.costo ?: 0.0) > 0 && it.pagadaAt == null
    }
    val centimos = filas.sumOf { kotlin.math.round((it.costo ?: 0.0) * 100).toLong() }
    return filas to centimos / 100.0
}

/**
 * ¿Quién ve «🧮 Cerrar mi caja»? Quien cobra (`pagos`) o quien ve las finanzas,
 * con el cierre en su plan (Premium). El servidor (alcanceCierreCaja) lo vuelve
 * a exigir: con solo `pagos`, la caja de HOY y sin el listado.
 */
fun ofreceCerrarMiCaja(pagos: Boolean, finanzas: Boolean, planConCierre: Boolean): Boolean =
    (pagos || finanzas) && planConCierre
