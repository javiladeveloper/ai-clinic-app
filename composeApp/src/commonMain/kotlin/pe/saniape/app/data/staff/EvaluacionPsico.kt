package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

// ─────────────────────────────────────────────────────────────────────────────
// EVALUACIÓN PSICOLÓGICA — modelos y lecturas puras del contrato
// (docs/app-contrato-evaluacion-psico.md en la web).
//
// La app NO reimplementa reglas: los estados de los 6 chips, la edad en meses,
// el armado del informe, el PDF y quién ve qué los resuelve el servidor. Aquí
// solo hay: los modelos, el parseo TOLERANTE (un campo null o con otra forma no
// tumba la pantalla) y los catálogos de textos de la UI, copiados tal cual de
// lib/evaluacion-psicologica.ts (son textos, no reglas).
//
// Convención del contrato: el sobre y los cuerpos van en camelCase; las
// entidades (evaluación, test aplicado, informe, catálogo) llegan como filas de
// la base, en snake_case.
// ─────────────────────────────────────────────────────────────────────────────

/** `procedimientos.tipo_clinico` que enciende todo esto. */
const val TIPO_CLINICO_EVALUACION_PSICO = "evaluacion_psicologica"

/** Categoría de las fotos de tests (material protegido: nunca en Documentos ni en el portal). */
const val CATEGORIA_TEST_PSICOLOGICO = "Test psicológico"

/** Categoría del informe emitido en los documentos del paciente (lo ve en "Mi salud"). */
const val CATEGORIA_INFORME_PSICOLOGICO = "Informe psicológico"

// ── Catálogos de la UI (copia de lib/evaluacion-psicologica.ts) ──────────────

data class OpcionPsico(val valor: String, val nombre: String)

/** Los 6 componentes, en orden. `titulo` corto para el chip; `tituloLargo` para la sección. */
data class ComponentePsico(val clave: String, val titulo: String, val tituloLargo: String, val icono: String)

val COMPONENTES_PSICO = listOf(
    ComponentePsico("entrevista", "Entrevista", "Entrevista", "🗣️"),
    ComponentePsico("fuentes", "Recopilación", "Recopilación de información", "📂"),
    ComponentePsico("observacion", "Observación", "Observación de la conducta", "👁️"),
    ComponentePsico("tests", "Tests", "Aplicación de tests", "📝"),
    ComponentePsico("analisis", "Análisis", "Análisis completo", "🧩"),
    ComponentePsico("plan", "Plan", "Plan de intervención", "🎯"),
)

data class SeccionEntrevista(val clave: String, val titulo: String, val ayuda: String, val corta: Boolean = false)

val SECCIONES_ENTREVISTA = listOf(
    SeccionEntrevista("motivo", "Motivo de consulta", "Qué preocupa y para qué se pide la evaluación."),
    SeccionEntrevista("solicitante", "Quién lo solicita", "Padres, colegio, el propio paciente, médico…", corta = true),
    SeccionEntrevista("informante", "Informante", "Quién da la información (madre, padre, el paciente…).", corta = true),
    SeccionEntrevista("derivadoPor", "Derivado por", "Profesional o institución que deriva, si hay.", corta = true),
    SeccionEntrevista("historiaPersonal", "Historia personal", "Datos relevantes de su historia."),
    SeccionEntrevista("desarrollo", "Desarrollo", "Embarazo, parto, hitos del desarrollo (sobre todo en niños)."),
    SeccionEntrevista("escolaridad", "Escolaridad / laboral", "Rendimiento, conducta en el colegio o historia laboral."),
    SeccionEntrevista("salud", "Salud", "Enfermedades, medicación, sueño, alimentación."),
    SeccionEntrevista("familia", "Familia", "Composición, dinámica y relaciones familiares."),
    SeccionEntrevista("antecedentes", "Antecedentes", "Antecedentes psicológicos, psiquiátricos o familiares relevantes."),
)

val TIPOS_FUENTE = listOf(
    OpcionPsico("padres", "Padres / familia"),
    OpcionPsico("colegio", "Colegio"),
    OpcionPsico("medico", "Médico derivador"),
    OpcionPsico("informe_previo", "Informe previo"),
    OpcionPsico("otro", "Otra fuente"),
)

data class GuiaObservacion(val clave: String, val titulo: String, val opciones: List<String>)

val GUIA_OBSERVACION = listOf(
    GuiaObservacion("apariencia", "Apariencia", listOf("Adecuada a la edad", "Aseo adecuado", "Descuidada", "Vestimenta acorde al contexto")),
    GuiaObservacion("actitud", "Actitud", listOf("Colaboradora", "Tímida", "Desconfiada", "Oposicionista", "Ansiosa", "Indiferente")),
    GuiaObservacion("contactoVisual", "Contacto visual", listOf("Adecuado", "Escaso", "Evitativo", "Fijo")),
    GuiaObservacion("lenguaje", "Lenguaje", listOf("Fluido", "Coherente", "Pobre", "Dificultades articulatorias", "Verborreico", "Ecolalia")),
    GuiaObservacion("atencion", "Atención", listOf("Sostenida", "Dispersa", "Fluctuante", "Requiere redirección")),
    GuiaObservacion("psicomotricidad", "Psicomotricidad", listOf("Adecuada", "Inquietud motora", "Enlentecida", "Torpeza motora", "Estereotipias")),
    GuiaObservacion("afecto", "Afecto", listOf("Eutímico", "Ansioso", "Triste", "Plano", "Lábil", "Irritable")),
)

val AREAS_ANALISIS = listOf(
    OpcionPsico("intelectual", "Área intelectual / cognitiva"),
    OpcionPsico("visomotora", "Área visomotora"),
    OpcionPsico("emocional", "Área emocional"),
    OpcionPsico("personalidad", "Personalidad"),
    OpcionPsico("familiar_social", "Área familiar y social"),
    OpcionPsico("vocacional", "Área vocacional"),
)

val ENFOQUES_PSICO = listOf(
    OpcionPsico("tcc", "Cognitivo-conductual (TCC)"),
    OpcionPsico("act", "Aceptación y compromiso (ACT)"),
    OpcionPsico("dbt", "Dialéctico-conductual (DBT)"),
    OpcionPsico("mindfulness", "Mindfulness / tercera generación"),
    OpcionPsico("emdr", "EMDR"),
    OpcionPsico("tcc_trauma", "TCC centrada en el trauma"),
    OpcionPsico("sistemica", "Sistémica / familiar"),
    OpcionPsico("eft", "Focalizada en las emociones (EFT)"),
    OpcionPsico("gottman", "Método Gottman (pareja)"),
    OpcionPsico("psicodinamica", "Psicodinámica / psicoanalítica"),
    OpcionPsico("humanista", "Humanista / centrada en la persona"),
    OpcionPsico("gestalt", "Gestalt"),
    OpcionPsico("breve_soluciones", "Breve centrada en soluciones"),
    OpcionPsico("interpersonal", "Interpersonal (TIP)"),
    OpcionPsico("juego", "Terapia de juego"),
    OpcionPsico("parental", "Entrenamiento parental / modificación de conducta"),
    OpcionPsico("aba", "Análisis conductual aplicado (ABA)"),
    OpcionPsico("cognitiva", "Rehabilitación / estimulación cognitiva"),
    OpcionPsico("psicoeducacion", "Psicoeducación"),
    OpcionPsico("crisis", "Intervención en crisis"),
    OpcionPsico("integrativa", "Integrativa / ecléctica"),
)

val FRECUENCIAS_PSICO = listOf(
    OpcionPsico("semanal", "Semanal"),
    OpcionPsico("quincenal", "Quincenal"),
    OpcionPsico("dos_por_semana", "2 por semana"),
    OpcionPsico("mensual", "Mensual"),
)

val ESTADOS_TEST_PSICO = listOf(
    OpcionPsico("aplicado", "Aplicado"),
    OpcionPsico("calificado", "Calificado"),
    OpcionPsico("interpretado", "Interpretado"),
)

val VALIDEZ_PSICO = listOf(
    OpcionPsico("valido", "Válido"),
    OpcionPsico("dudoso", "Dudoso"),
    OpcionPsico("invalido", "Inválido"),
)

val POBLACIONES_PSICO = listOf(
    OpcionPsico("ninos", "Niños"),
    OpcionPsico("adolescentes", "Adolescentes"),
    OpcionPsico("adultos", "Adultos"),
)

/** Las 8 secciones de texto del informe (la 1 es la filiación y la 9 la firma). */
data class SeccionInforme(val clave: String, val numero: Int, val titulo: String)

val SECCIONES_INFORME = listOf(
    SeccionInforme("motivo", 2, "Motivo de evaluación"),
    SeccionInforme("antecedentes", 3, "Antecedentes relevantes"),
    SeccionInforme("observacion", 4, "Observación de conducta"),
    SeccionInforme("tecnicas", 5, "Técnicas e instrumentos aplicados"),
    SeccionInforme("resultados", 6, "Resultados, análisis e interpretación"),
    SeccionInforme("conclusiones", 7, "Conclusiones"),
    SeccionInforme("impresionDiagnostica", 7, "Impresión diagnóstica"),
    SeccionInforme("recomendaciones", 8, "Recomendaciones"),
)

/** El subtítulo que el informe llevó siempre (fase 1): el encabezado de la plantilla estándar. */
const val ENCABEZADO_INFORME_POR_DEFECTO =
    "Documento confidencial. Su contenido es de uso exclusivo del evaluado o su representante."

/** Sin motivo ni conclusiones no hay informe: la plantilla no los puede ocultar. */
val SECCIONES_OBLIGATORIAS_INFORME = setOf("motivo", "conclusiones")

internal const val MAX_TITULO_SECCION_INFORME = 120

/** La plantilla estándar: reproduce exactamente el informe de la fase 1. */
fun plantillaInformePorDefecto(): PlantillaInformePsico = PlantillaInformePsico(
    secciones = SECCIONES_INFORME.map { SeccionPlantillaPsico(it.clave, it.titulo, true) },
    encabezado = ENCABEZADO_INFORME_POR_DEFECTO,
    pie = "",
)

/**
 * Por qué no se borra el PDF de un informe emitido (MOTIVO_INFORME_NO_SE_BORRA
 * de la web, contrato §13.4).
 */
const val MOTIVO_INFORME_NO_SE_BORRA =
    "El PDF de un informe psicológico emitido no se puede borrar: es un registro clínico y el Código de Ética del CPsP (art. 18) pide conservarlo sin enmendaduras. Para corregirlo, emite una nueva versión desde 🧠 Evaluación → Informe: esta queda en el historial y el paciente verá solo la nueva."

val CAMPOS_FILIACION = listOf(
    OpcionPsico("nombre", "Nombre"),
    OpcionPsico("edad", "Edad"),
    OpcionPsico("dni", "DNI"),
    OpcionPsico("instruccion", "Grado de instrucción"),
    OpcionPsico("ocupacion", "Ocupación"),
    OpcionPsico("informante", "Informante"),
    OpcionPsico("derivadoPor", "Derivado por"),
    OpcionPsico("fechasEvaluacion", "Fechas de evaluación"),
    OpcionPsico("psicologo", "Psicólogo(a)"),
    OpcionPsico("colegiatura", "C.Ps.P."),
)

// ── Modelos ──────────────────────────────────────────────────────────────────

/** Componente 1. Los textos por clave de [SECCIONES_ENTREVISTA]; el genograma es una foto. */
data class EntrevistaPsico(
    val textos: Map<String, String> = emptyMap(),
    val genogramaDocumentoId: String? = null,
) {
    fun texto(clave: String): String = textos[clave].orEmpty()
    fun con(clave: String, valor: String): EntrevistaPsico = copy(textos = textos + (clave to valor))
}

data class FuentePsico(
    val id: String,
    val tipo: String = "padres",
    val nombre: String = "",
    val fecha: String? = null,
    val resumen: String = "",
    val documentoIds: List<String> = emptyList(),
)

data class ObservacionPsico(
    val seleccion: Map<String, List<String>> = emptyMap(),
    val notas: String = "",
) {
    fun elegidas(clave: String): List<String> = seleccion[clave].orEmpty()
    /** Marca o desmarca [opcion] en el aspecto [clave] (chip). */
    fun alternar(clave: String, opcion: String): ObservacionPsico {
        val actual = elegidas(clave)
        val nuevo = if (opcion in actual) actual - opcion else actual + opcion
        return copy(seleccion = seleccion + (clave to nuevo))
    }
}

data class AnalisisPsico(
    val areas: Map<String, String> = emptyMap(),
    val conclusiones: String = "",
)

data class PlanPsico(
    val objetivos: List<String> = emptyList(),
    val enfoques: List<String> = emptyList(),
    val enfoqueOtro: String = "",
    val numeroSesiones: Int? = null,
    val frecuencia: String? = null,
    val sesionesFamilia: String = "",
    val reevaluarAlCerrar: Boolean = false,
    val procedimientoId: String? = null,
    val precio: Double? = null,
    val recomendaciones: String = "",
    val incluirEnInforme: Boolean = false,
    /** Lo pone /plan-tratamiento ("✓ Tratamiento creado"). `guardar` lo ignora. */
    val tratamientoCreadoId: String? = null,
)

data class EvaluacionPsico(
    val id: String,
    val pacienteId: String = "",
    val tratamientoId: String? = null,
    val terapeutaId: String? = null,
    /** 'abierta' | 'cerrada' (se cierra al emitir el informe). */
    val estado: String = "abierta",
    val entrevista: EntrevistaPsico = EntrevistaPsico(),
    val fuentes: List<FuentePsico> = emptyList(),
    val observacion: ObservacionPsico = ObservacionPsico(),
    val analisis: AnalisisPsico = AnalisisPsico(),
    val diagnosticos: List<DiagnosticoCie> = emptyList(),
    val plan: PlanPsico = PlanPsico(),
)

/** Estado de un chip: 'vacio' | 'en_curso' | 'completo' (lo calcula el servidor). */
data class EstadosPsico(val porComponente: Map<String, String> = emptyMap()) {
    fun de(clave: String): String = porComponente[clave]?.takeIf { it in ESTADOS_CHIP } ?: "vacio"
}

private val ESTADOS_CHIP = setOf("vacio", "en_curso", "completo")

data class PuntajeEscalaPsico(
    val escala: String = "",
    val directo: String = "",
    val transformado: String = "",
    val percentil: String = "",
    val categoria: String = "",
)

data class PuntajeGlobalPsico(val puntaje: String = "", val categoria: String = "", val descripcion: String = "")

/** El test del catálogo embebido en un test aplicado. */
data class TestRefPsico(
    val id: String = "",
    val nombreCorto: String = "",
    val nombre: String = "",
    val categoria: String = "",
    val tipoPuntaje: String = "",
    val generaImagen: Boolean = false,
    /** Fase 3: null = catálogo global de Sania (solo esos se autocalculan). */
    val clinicaId: String? = null,
)

data class TestAplicadoPsico(
    val id: String,
    val evaluacionId: String = "",
    val testId: String = "",
    val fecha: String = "",
    val edadMeses: Int? = null,
    val informante: String = "",
    /** 'presencial' | 'virtual' */
    val modalidad: String = "presencial",
    val forma: String = "",
    val baremo: String = "",
    /** 'valido' | 'dudoso' | 'invalido' | null */
    val validez: String? = null,
    val puntajes: List<PuntajeEscalaPsico> = emptyList(),
    val global: PuntajeGlobalPsico = PuntajeGlobalPsico(),
    val interpretacion: String = "",
    val observaciones: String = "",
    /** 'aplicado' → 'calificado' → 'interpretado' */
    val estado: String = "aplicado",
    val enInforme: Boolean = true,
    val test: TestRefPsico? = null,
    /** Fase 3: ítems respondidos y lo que calculó el servidor (null = puntuado a mano). */
    val respuestas: RespuestasTestPsico? = null,
    val createdAt: String? = null,
) {
    val nombreCorto: String get() = test?.nombreCorto?.ifBlank { null } ?: "Test"
}

data class EscalaCatalogoPsico(val nombre: String, val grupo: String? = null)

data class TestCatalogoPsico(
    val id: String,
    /** null = catálogo global de Sania; uuid = test propio de la clínica. */
    val clinicaId: String? = null,
    val nombreCorto: String,
    val nombre: String = "",
    val categoria: String = "",
    val poblacion: List<String> = emptyList(),
    val tipoPuntaje: String = "",
    val generaImagen: Boolean = false,
    val escalas: List<EscalaCatalogoPsico> = emptyList(),
    /** Los 35 más usados en Perú van primero (orden bajo). */
    val orden: Int = 9999,
)

/**
 * Foto o adjunto protegido de la evaluación (nunca lo ve el paciente). Vive bajo
 * `protegido/…` en Storage: la app NUNCA lo firma ni lo lee directo; se ve por
 * id con [EvaluacionPsicoRepo.urlDeDocumento]. [path] es solo informativo.
 */
data class FotoPsico(
    val id: String,
    val nombre: String = "Foto",
    val path: String,
    val tipo: String? = null,
    val testAplicadoId: String? = null,
    /** 'test' | 'genograma' | 'fuente' */
    val uso: String = "test",
    val createdAt: String? = null,
)

/** Una sección (2–8) de la plantilla del informe de la clínica (fase 2, contrato §13.2). */
data class SeccionPlantillaPsico(val clave: String, val titulo: String, val visible: Boolean = true)

/**
 * Plantilla del informe (copia de lib/informe-psicologico-plantilla.ts): las 8
 * secciones en el orden del informe, con su título y si se muestran, más los
 * textos fijos de encabezado (bajo el título) y pie (después de la firma).
 * `armar` la COPIA dentro del contenido: lo armado no cambia si la clínica la edita.
 */
data class PlantillaInformePsico(
    val secciones: List<SeccionPlantillaPsico>,
    val encabezado: String = ENCABEZADO_INFORME_POR_DEFECTO,
    val pie: String = "",
)

/** "Reemplaza a la versión N emitida el …" (solo en informes v ≥ 2). */
data class ReemplazaAPsico(val version: Int, val emitido: String = "")

data class ContenidoInformePsico(
    val filiacion: Map<String, String> = emptyMap(),
    val secciones: Map<String, String> = emptyMap(),
    val lugar: String = "",
    val fecha: String = "",
    /** Fase 2: la plantilla con que se armó (null = informe de la fase 1 → la estándar). La pone el servidor. */
    val plantilla: PlantillaInformePsico? = null,
    /** Fase 2: a qué versión reemplaza (solo v ≥ 2). La pone el servidor. */
    val reemplazaA: ReemplazaAPsico? = null,
    /**
     * Fase 3: anexo "Perfil de puntajes". La app solo decide `incluir`; los
     * perfiles los calcula el servidor (al guardar, armar y emitir). null = el
     * contenido no lo trae (servidor sin fase 3 o nunca se tocó).
     */
    val anexoPerfiles: AnexoPerfilesPsico? = null,
    /** Fase 3: secciones redactadas con IA y aceptadas (la marca solo crece). */
    val asistidoIa: AsistidoIaPsico? = null,
)

data class AnexoPerfilesPsico(val incluir: Boolean, val perfiles: List<PerfilTestPsico> = emptyList())

data class AsistidoIaPsico(
    val secciones: List<String> = emptyList(),
    val modelo: String = "",
    val fecha: String = "",
    val sinConsentimientoConfirmado: Boolean = false,
)

data class InformePsico(
    val id: String,
    val evaluacionId: String = "",
    val version: Int = 1,
    /** 'borrador' | 'emitido' (emitido = congelado). */
    val estado: String = "borrador",
    val contenido: ContenidoInformePsico = ContenidoInformePsico(),
    val documentoId: String? = null,
    val emitidoAt: String? = null,
    /** Fase 2: la versión posterior emitida que lo reemplazó (null en el vigente y en borradores). */
    val reemplazadoPor: String? = null,
    val reemplazadoAt: String? = null,
) {
    val emitido: Boolean get() = estado == "emitido"
    /** Borrador o emitido de una versión ≥ 2 (corrige a una anterior). */
    val esVersionNueva: Boolean get() = version > 1
}

data class DocumentoInformePsico(val id: String, val path: String, val nombre: String)

/** Una fila del historial de versiones del informe (`informes` del GET, fase 2). */
data class VersionInformePsico(
    val id: String,
    val version: Int,
    /** 'borrador' | 'emitido' */
    val estado: String = "borrador",
    val emitidoAt: String? = null,
    val documentoId: String? = null,
    val reemplazadoPor: String? = null,
    /** Número de la versión que la reemplazó ("Reemplazado por v2"). */
    val reemplazadoPorVersion: Int? = null,
    /** El emitido vigente: el que ve el paciente. */
    val vigente: Boolean = false,
    /** Su PDF (se ve por GET foto?documentoId=; solo Admin y tratante). */
    val pdf: DocumentoInformePsico? = null,
) {
    val tienePdf: Boolean get() = pdf != null || documentoId != null
    /** Emitido pero su PDF no se generó: se completa con "Generar PDF pendiente" (emitir de nuevo). */
    val pdfPendiente: Boolean get() = estado == "emitido" && !tienePdf
    val idPdf: String? get() = pdf?.id ?: documentoId
}

/** Lo que precarga el formulario de tratamiento de siempre ("Crear tratamiento con este plan"). */
data class PrefillPlanPsico(
    val procedimientoId: String? = null,
    val terapeutaId: String? = null,
    val modalidad: String = "Paquete",
    val totalSesiones: Int? = null,
    val precioPaquete: Double? = null,
    val diagnostico: String? = null,
)

data class SugerenciaSesionesPsico(val clave: String, val nombre: String, val sesiones: Int)

data class ServicioPsico(val id: String, val nombre: String, val precio: Double? = null)

data class TratamientoEvalPsico(
    val id: String,
    val pacienteId: String = "",
    val terapeutaId: String? = null,
    val estado: String? = null,
    val totalSesiones: Int = 0,
    val sesionesCompletadas: Int = 0,
    val procedimientoNombre: String? = null,
    /** 'incluida' | 'adicional' | null */
    val devolucion: String? = null,
)

data class PacienteEvalPsico(
    val id: String,
    val nombre: String = "Paciente",
    val dni: String? = null,
    val edadTexto: String = "",
)

/** Todo el espacio de trabajo (GET / abrir). */
data class EspacioEvalPsico(
    val esEvaluacionPsico: Boolean,
    val soloLectura: Boolean = false,
    val tratamiento: TratamientoEvalPsico? = null,
    val paciente: PacienteEvalPsico? = null,
    val evaluacion: EvaluacionPsico? = null,
    val estados: EstadosPsico = EstadosPsico(),
    val tests: List<TestAplicadoPsico> = emptyList(),
    val fotos: List<FotoPsico> = emptyList(),
    /** El ÚLTIMO informe (puede ser el borrador de una versión nueva). */
    val informe: InformePsico? = null,
    /** El PDF del VIGENTE (el último emitido, el que ve el paciente), aunque haya un borrador nuevo. */
    val informePdf: DocumentoInformePsico? = null,
    /** Fase 2: historial de versiones, de la más nueva a la más vieja (vacío en un servidor sin fase 2). */
    val informes: List<VersionInformePsico> = emptyList(),
    val planPrefill: PrefillPlanPsico? = null,
    val sugerenciaSesiones: SugerenciaSesionesPsico? = null,
    val servicios: List<ServicioPsico> = emptyList(),
)

/** Respuesta de `guardar`: lo que la pantalla refresca (lo escrito NO se pisa). */
data class GuardadoPsico(
    val estados: EstadosPsico?,
    val sugerencia: SugerenciaSesionesPsico?,
)

// ── Lectura tolerante ────────────────────────────────────────────────────────
// Un campo null, ausente o con otra forma toma su valor por defecto y la
// pantalla sigue: nada de crashes por un JSONB viejo o a medio llenar.

private fun JsonElement?.obj(): JsonObject? = when (this) {
    is JsonObject -> this
    is JsonArray -> firstOrNull() as? JsonObject   // los joins de PostgREST pueden venir como arreglo
    else -> null
}

private fun JsonObject?.txt(k: String): String? =
    (this?.get(k) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it != "null" }

private fun JsonObject?.texto(k: String): String = txt(k).orEmpty()

private fun JsonObject?.entero(k: String): Int? = txt(k)?.trim()?.toDoubleOrNull()?.toInt()

private fun JsonObject?.decimal(k: String): Double? = txt(k)?.trim()?.replace(',', '.')?.toDoubleOrNull()

private fun JsonObject?.si(k: String, defecto: Boolean): Boolean = when (txt(k)?.lowercase()) {
    "true" -> true
    "false" -> false
    else -> defecto
}

private fun JsonObject?.lista(k: String): List<JsonElement> = (this?.get(k) as? JsonArray).orEmpty()

private fun JsonObject?.textos(k: String): List<String> = lista(k).mapNotNull { e ->
    (e as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
}

private fun JsonObject?.mapaTextos(k: String): Map<String, String> =
    (this?.get(k) as? JsonObject)?.mapNotNull { (clave, v) ->
        (v as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.let { clave to it }
    }?.toMap().orEmpty()

internal fun leerEntrevista(o: JsonObject?): EntrevistaPsico = EntrevistaPsico(
    textos = SECCIONES_ENTREVISTA.associate { it.clave to o.texto(it.clave) }.filterValues { it.isNotEmpty() },
    genogramaDocumentoId = o.txt("genogramaDocumentoId")?.ifBlank { null },
)

internal fun leerFuentes(e: JsonElement?): List<FuentePsico> = (e as? JsonArray).orEmpty().mapIndexedNotNull { i, x ->
    val o = x as? JsonObject ?: return@mapIndexedNotNull null
    FuentePsico(
        // Sin id (dato viejo): uno estable por posición, para poder editarla.
        id = o.txt("id")?.ifBlank { null } ?: "f$i",
        tipo = o.txt("tipo")?.takeIf { t -> TIPOS_FUENTE.any { it.valor == t } } ?: "otro",
        nombre = o.texto("nombre"),
        fecha = o.txt("fecha")?.ifBlank { null },
        resumen = o.texto("resumen"),
        documentoIds = o.textos("documentoIds"),
    )
}

internal fun leerObservacion(o: JsonObject?): ObservacionPsico = ObservacionPsico(
    seleccion = (o?.get("seleccion") as? JsonObject)?.mapValues { (_, v) ->
        (v as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.contentOrNull }
    }.orEmpty(),
    notas = o.texto("notas"),
)

internal fun leerAnalisis(o: JsonObject?): AnalisisPsico = AnalisisPsico(
    areas = o.mapaTextos("areas"),
    conclusiones = o.texto("conclusiones"),
)

internal fun leerDiagnosticos(e: JsonElement?): List<DiagnosticoCie> = (e as? JsonArray).orEmpty().mapNotNull { x ->
    val o = x as? JsonObject ?: return@mapNotNull null
    val descripcion = o.texto("descripcion")
    val codigo = o.txt("codigo")?.ifBlank { null }
    if (descripcion.isBlank() && codigo == null) return@mapNotNull null
    DiagnosticoCie(codigo = codigo, descripcion = descripcion, tipo = o.txt("tipo")?.takeIf { it in setOf("P", "D", "R") } ?: "P")
}

internal fun leerPlan(o: JsonObject?): PlanPsico = PlanPsico(
    objetivos = o.textos("objetivos"),
    enfoques = o.textos("enfoques"),
    enfoqueOtro = o.texto("enfoqueOtro"),
    numeroSesiones = o.entero("numeroSesiones")?.takeIf { it > 0 },
    frecuencia = o.txt("frecuencia")?.takeIf { f -> FRECUENCIAS_PSICO.any { it.valor == f } },
    sesionesFamilia = o.texto("sesionesFamilia"),
    reevaluarAlCerrar = o.si("reevaluarAlCerrar", false),
    procedimientoId = o.txt("procedimientoId")?.ifBlank { null },
    precio = o.decimal("precio"),
    recomendaciones = o.texto("recomendaciones"),
    incluirEnInforme = o.si("incluirEnInforme", false),
    tratamientoCreadoId = o.txt("tratamientoCreadoId")?.ifBlank { null },
)

internal fun leerEvaluacion(o: JsonObject?): EvaluacionPsico? {
    val id = o.txt("id") ?: return null
    return EvaluacionPsico(
        id = id,
        pacienteId = o.texto("paciente_id"),
        tratamientoId = o.txt("tratamiento_id"),
        terapeutaId = o.txt("terapeuta_id"),
        estado = o.txt("estado") ?: "abierta",
        entrevista = leerEntrevista(o?.get("entrevista").obj()),
        fuentes = leerFuentes(o?.get("fuentes")),
        observacion = leerObservacion(o?.get("observacion").obj()),
        analisis = leerAnalisis(o?.get("analisis").obj()),
        diagnosticos = leerDiagnosticos(o?.get("diagnosticos")),
        plan = leerPlan(o?.get("plan").obj()),
    )
}

internal fun leerEstados(o: JsonObject?): EstadosPsico? =
    o?.let { EstadosPsico(COMPONENTES_PSICO.associate { c -> c.clave to (it.txt(c.clave) ?: "vacio") }) }

internal fun leerTestAplicado(o: JsonObject?): TestAplicadoPsico? {
    val id = o.txt("id") ?: return null
    val t = o?.get("test").obj()
    val g = o?.get("global").obj()
    return TestAplicadoPsico(
        id = id,
        evaluacionId = o.texto("evaluacion_id"),
        testId = o.texto("test_id"),
        fecha = o.texto("fecha").take(10),
        edadMeses = o.entero("edad_meses"),
        informante = o.texto("informante"),
        modalidad = if (o.txt("modalidad") == "virtual") "virtual" else "presencial",
        forma = o.texto("forma"),
        baremo = o.texto("baremo"),
        validez = o.txt("validez")?.takeIf { v -> VALIDEZ_PSICO.any { it.valor == v } },
        puntajes = o.lista("puntajes").mapNotNull { x ->
            val p = x as? JsonObject ?: return@mapNotNull null
            PuntajeEscalaPsico(p.texto("escala"), p.texto("directo"), p.texto("transformado"), p.texto("percentil"), p.texto("categoria"))
        },
        global = PuntajeGlobalPsico(g.texto("puntaje"), g.texto("categoria"), g.texto("descripcion")),
        interpretacion = o.texto("interpretacion"),
        observaciones = o.texto("observaciones"),
        estado = o.txt("estado")?.takeIf { e -> ESTADOS_TEST_PSICO.any { it.valor == e } } ?: "aplicado",
        enInforme = o.si("en_informe", true),
        test = t?.let {
            TestRefPsico(
                id = it.texto("id"), nombreCorto = it.texto("nombre_corto"), nombre = it.texto("nombre"),
                categoria = it.texto("categoria"), tipoPuntaje = it.texto("tipo_puntaje"),
                generaImagen = it.si("genera_imagen", false),
                clinicaId = it.txt("clinica_id")?.ifBlank { null },
            )
        },
        respuestas = leerRespuestasTest(o?.get("respuestas") as? JsonObject),
        createdAt = o.txt("created_at"),
    )
}

internal fun leerTestCatalogo(o: JsonObject?): TestCatalogoPsico? {
    val id = o.txt("id") ?: return null
    val corto = o.txt("nombre_corto")?.ifBlank { null } ?: o.txt("nombre")?.ifBlank { null } ?: return null
    return TestCatalogoPsico(
        id = id,
        clinicaId = o.txt("clinica_id")?.ifBlank { null },
        nombreCorto = corto,
        nombre = o.texto("nombre"),
        categoria = o.txt("categoria")?.ifBlank { null } ?: "Otros",
        poblacion = o.textos("poblacion"),
        tipoPuntaje = o.texto("tipo_puntaje"),
        generaImagen = o.si("genera_imagen", false),
        escalas = o.lista("estructura_escalas").mapNotNull { x ->
            val e = x as? JsonObject
            val nombre = e.txt("nombre")?.trim()?.ifBlank { null } ?: (x as? JsonPrimitive)?.contentOrNull?.trim()?.ifBlank { null }
            nombre?.let { EscalaCatalogoPsico(it, e.txt("grupo")) }
        },
        orden = o.entero("orden") ?: 9999,
    )
}

internal fun leerFoto(o: JsonObject?): FotoPsico? {
    val id = o.txt("id") ?: return null
    // Se ve por id (GET foto?documentoId=): el path es solo informativo.
    val path = o.txt("path") ?: o.txt("archivo_url") ?: ""
    return FotoPsico(
        id = id,
        nombre = o.txt("nombre")?.ifBlank { null } ?: "Foto",
        path = path,
        tipo = o.txt("tipo") ?: o.txt("tipo_archivo"),
        testAplicadoId = o.txt("testAplicadoId") ?: o.txt("test_aplicado_id"),
        uso = o.txt("uso")?.takeIf { it in setOf("test", "genograma", "fuente") } ?: "test",
        createdAt = o.txt("created_at"),
    )
}

internal fun leerInforme(o: JsonObject?): InformePsico? {
    val id = o.txt("id") ?: return null
    val c = o?.get("contenido").obj()
    return InformePsico(
        id = id,
        evaluacionId = o.texto("evaluacion_id"),
        version = o.entero("version") ?: 1,
        estado = if (o.txt("estado") == "emitido") "emitido" else "borrador",
        contenido = ContenidoInformePsico(
            filiacion = c.mapaTextos("filiacion"),
            secciones = c.mapaTextos("secciones"),
            lugar = c.texto("lugar"),
            fecha = c.texto("fecha"),
            plantilla = (c?.get("plantilla") as? JsonObject)?.let { leerPlantillaInforme(it) },
            reemplazaA = leerReemplazaA(c?.get("reemplazaA").obj()),
            anexoPerfiles = (c?.get("anexoPerfiles") as? JsonObject)?.let { a ->
                AnexoPerfilesPsico(a.si("incluir", false), a.lista("perfiles").mapNotNull { leerPerfilTest(it as? JsonObject) })
            },
            asistidoIa = (c?.get("asistido_ia") as? JsonObject)?.let { a ->
                AsistidoIaPsico(a.textos("secciones"), a.texto("modelo"), a.texto("fecha"), a.si("sinConsentimientoConfirmado", false))
            }?.takeIf { it.secciones.isNotEmpty() },
        ),
        documentoId = o.txt("documento_id"),
        emitidoAt = o.txt("emitido_at"),
        reemplazadoPor = o.txt("reemplazado_por")?.ifBlank { null },
        reemplazadoAt = o.txt("reemplazado_at")?.ifBlank { null },
    )
}

internal fun leerReemplazaA(o: JsonObject?): ReemplazaAPsico? {
    val v = o.entero("version")?.takeIf { it >= 1 } ?: return null
    return ReemplazaAPsico(v, o.texto("emitido").take(10))
}

internal fun leerDocumentoInforme(o: JsonObject?): DocumentoInformePsico? {
    val id = o.txt("id") ?: return null
    return DocumentoInformePsico(id, o.txt("path").orEmpty(), o.txt("nombre") ?: "Informe psicológico.pdf")
}

/** Una fila del historial (`informes`). Sin id ni versión válida se descarta. */
internal fun leerVersionInforme(o: JsonObject?): VersionInformePsico? {
    val id = o.txt("id") ?: return null
    val version = o.entero("version")?.takeIf { it >= 1 } ?: return null
    val reemplazadoPor = o.txt("reemplazado_por")?.ifBlank { null }
    return VersionInformePsico(
        id = id,
        version = version,
        estado = if (o.txt("estado") == "emitido") "emitido" else "borrador",
        emitidoAt = o.txt("emitido_at")?.ifBlank { null },
        documentoId = o.txt("documento_id")?.ifBlank { null },
        reemplazadoPor = reemplazadoPor,
        reemplazadoPorVersion = o.entero("reemplazadoPorVersion"),
        vigente = o.si("vigente", false),
        pdf = leerDocumentoInforme(o?.get("pdf").obj()),
    ).let { v ->
        // Vigente solo un emitido CON su PDF y sin reemplazar (si el PDF de la vN
        // falló, el paciente sigue viendo la anterior): diga lo que diga el campo.
        v.copy(vigente = v.vigente && v.estado == "emitido" && v.reemplazadoPor == null && v.tienePdf)
    }
}

/**
 * La plantilla tal como llega (contenido.plantilla o GET /plantilla), SANEADA
 * como sanearPlantilla de la web: cada una de las 8 secciones exactamente una
 * vez (desconocidas fuera, repetidas cuenta la primera, faltantes al final en su
 * orden estándar), título vacío = el estándar y motivo/conclusiones siempre
 * visibles. Encabezado/pie ausentes = los de la estándar.
 */
internal fun leerPlantillaInforme(o: JsonObject?): PlantillaInformePsico {
    val base = plantillaInformePorDefecto()
    if (o == null) return base
    val vistas = mutableSetOf<String>()
    val secciones = mutableListOf<SeccionPlantillaPsico>()
    o.lista("secciones").forEach { x ->
        val s = x as? JsonObject ?: return@forEach
        val clave = s.txt("clave") ?: return@forEach
        val estandar = SECCIONES_INFORME.firstOrNull { it.clave == clave } ?: return@forEach
        if (!vistas.add(clave)) return@forEach
        val titulo = s.texto("titulo").take(MAX_TITULO_SECCION_INFORME).replace(Regex("\\s+"), " ").trim()
        secciones += SeccionPlantillaPsico(
            clave = clave,
            titulo = titulo.ifEmpty { estandar.titulo },
            visible = clave in SECCIONES_OBLIGATORIAS_INFORME || s.si("visible", true),
        )
    }
    base.secciones.filter { it.clave !in vistas }.forEach { secciones += it }
    return PlantillaInformePsico(
        secciones = secciones,
        encabezado = o.txt("encabezado")?.trim() ?: base.encabezado,
        pie = o.txt("pie")?.trim() ?: base.pie,
    )
}

internal fun leerPrefill(o: JsonObject?): PrefillPlanPsico? = o?.let {
    PrefillPlanPsico(
        procedimientoId = it.txt("procedimiento_id")?.ifBlank { null },
        terapeutaId = it.txt("terapeuta_id")?.ifBlank { null },
        modalidad = it.txt("modalidad") ?: "Paquete",
        totalSesiones = it.entero("total_sesiones")?.takeIf { n -> n > 0 },
        precioPaquete = it.decimal("precio_paquete"),
        diagnostico = it.txt("diagnostico")?.ifBlank { null },
    )
}

internal fun leerSugerencia(o: JsonObject?): SugerenciaSesionesPsico? {
    val n = o.entero("sesiones")?.takeIf { it > 0 } ?: return null
    return SugerenciaSesionesPsico(o.texto("clave"), o.texto("nombre"), n)
}

/** El sobre de GET / abrir → espacio de trabajo (puro, testeable). null = no se pudo leer. */
internal fun parsearEspacioPsico(o: JsonObject?): EspacioEvalPsico? {
    if (o == null) return null
    if (!o.si("esEvaluacionPsico", false)) return EspacioEvalPsico(esEvaluacionPsico = false)
    val t = o["tratamiento"].obj()
    val proc = t?.get("procedimiento").obj()
    val p = o["paciente"].obj()
    return EspacioEvalPsico(
        esEvaluacionPsico = true,
        soloLectura = o.si("soloLectura", false),
        tratamiento = t.txt("id")?.let { id ->
            TratamientoEvalPsico(
                id = id,
                pacienteId = t.texto("pacienteId"),
                terapeutaId = t.txt("terapeutaId"),
                estado = t.txt("estado"),
                totalSesiones = t.entero("totalSesiones") ?: 0,
                sesionesCompletadas = t.entero("sesionesCompletadas") ?: 0,
                procedimientoNombre = proc.txt("nombre"),
                devolucion = proc.txt("devolucion"),
            )
        },
        paciente = p.txt("id")?.let { id ->
            PacienteEvalPsico(id, p.txt("nombre") ?: "Paciente", p.txt("dni")?.ifBlank { null }, p.texto("edadTexto"))
        },
        evaluacion = leerEvaluacion(o["evaluacion"].obj()),
        estados = leerEstados(o["estados"].obj()) ?: EstadosPsico(),
        tests = o.lista("tests").mapNotNull { leerTestAplicado(it.obj()) },
        fotos = o.lista("fotos").mapNotNull { leerFoto(it.obj()) },
        informe = leerInforme(o["informe"].obj()),
        informePdf = leerDocumentoInforme(o["informePdf"].obj()),
        informes = o.lista("informes").mapNotNull { leerVersionInforme(it as? JsonObject) }.sortedByDescending { it.version },
        planPrefill = leerPrefill(o["planPrefill"].obj()),
        sugerenciaSesiones = leerSugerencia(o["sugerenciaSesiones"].obj()),
        servicios = o.lista("servicios").mapNotNull { x ->
            val s = x.obj()
            val id = s.txt("id") ?: return@mapNotNull null
            ServicioPsico(id, s.txt("nombre") ?: "Servicio", s.decimal("precio"))
        },
    )
}

/** Respuesta de `guardar` (estados + sugerencia). */
internal fun parsearGuardadoPsico(o: JsonObject?): GuardadoPsico = GuardadoPsico(
    estados = leerEstados(o?.get("estados").obj()),
    sugerencia = leerSugerencia(o?.get("sugerenciaSesiones").obj()),
)

/** `{ ok, tests: [...] }` del catálogo, ordenado como lo manda el servidor (orden, nombre). */
internal fun parsearCatalogoPsico(o: JsonObject?): List<TestCatalogoPsico> =
    o.lista("tests").mapNotNull { leerTestCatalogo(it.obj()) }

internal fun testDeRespuesta(o: JsonObject?): TestAplicadoPsico? = leerTestAplicado(o?.get("test").obj())
internal fun testCatalogoDeRespuesta(o: JsonObject?): TestCatalogoPsico? = leerTestCatalogo(o?.get("test").obj())
internal fun fotoDeRespuesta(o: JsonObject?): FotoPsico? = leerFoto(o?.get("foto").obj())
internal fun informeDeRespuesta(o: JsonObject?): InformePsico? = leerInforme(o?.get("informe").obj())
internal fun totalSesionesDeRespuesta(o: JsonObject?): Int? = o.entero("totalSesiones")
internal fun idDeRespuesta(o: JsonObject?): String? = o.txt("id")?.ifBlank { null }

// ── Cuerpos (lo que va al servidor: el componente COMPLETO, camelCase) ───────

internal fun jsonEntrevista(e: EntrevistaPsico): JsonObject = buildJsonObject {
    SECCIONES_ENTREVISTA.forEach { put(it.clave, e.texto(it.clave)) }
    put("genogramaDocumentoId", e.genogramaDocumentoId)
}

internal fun jsonFuentes(l: List<FuentePsico>): JsonArray = JsonArray(l.map { f ->
    buildJsonObject {
        put("id", f.id)
        put("tipo", f.tipo)
        put("nombre", f.nombre)
        put("fecha", f.fecha)
        put("resumen", f.resumen)
        putJsonArray("documentoIds") { f.documentoIds.forEach { add(JsonPrimitive(it)) } }
    }
})

internal fun jsonObservacion(o: ObservacionPsico): JsonObject = buildJsonObject {
    putJsonObject("seleccion") {
        o.seleccion.filterValues { it.isNotEmpty() }.forEach { (k, v) -> put(k, JsonArray(v.map { JsonPrimitive(it) })) }
    }
    put("notas", o.notas)
}

internal fun jsonAnalisis(a: AnalisisPsico): JsonObject = buildJsonObject {
    putJsonObject("areas") { a.areas.filterValues { it.isNotBlank() }.forEach { (k, v) -> put(k, v) } }
    put("conclusiones", a.conclusiones)
}

internal fun jsonDiagnosticos(l: List<DiagnosticoCie>): JsonArray = JsonArray(l.map { d ->
    buildJsonObject {
        put("codigo", d.codigo)
        put("descripcion", d.descripcion)
        put("tipo", d.tipo)
    }
})

internal fun jsonPlan(p: PlanPsico): JsonObject = buildJsonObject {
    // Los objetivos vacíos (la fila nueva que aún no se escribió) no viajan.
    putJsonArray("objetivos") { p.objetivos.map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(JsonPrimitive(it)) } }
    putJsonArray("enfoques") { p.enfoques.forEach { add(JsonPrimitive(it)) } }
    put("enfoqueOtro", p.enfoqueOtro)
    put("numeroSesiones", p.numeroSesiones)
    put("frecuencia", p.frecuencia)
    put("sesionesFamilia", p.sesionesFamilia)
    put("reevaluarAlCerrar", p.reevaluarAlCerrar)
    put("procedimientoId", p.procedimientoId)
    put("precio", p.precio)
    put("recomendaciones", p.recomendaciones)
    put("incluirEnInforme", p.incluirEnInforme)
}

/** `datos` de test/editar: todos los campos editables, en snake_case (como la fila). */
internal fun jsonDatosTest(t: TestAplicadoPsico): JsonObject = buildJsonObject {
    if (t.fecha.isNotBlank()) put("fecha", t.fecha)
    put("informante", t.informante)
    put("modalidad", t.modalidad)
    put("forma", t.forma)
    put("baremo", t.baremo)
    put("validez", t.validez)
    putJsonArray("puntajes") {
        t.puntajes.forEach { p ->
            add(buildJsonObject {
                put("escala", p.escala); put("directo", p.directo); put("transformado", p.transformado)
                put("percentil", p.percentil); put("categoria", p.categoria)
            })
        }
    }
    putJsonObject("global") {
        put("puntaje", t.global.puntaje); put("categoria", t.global.categoria); put("descripcion", t.global.descripcion)
    }
    put("interpretacion", t.interpretacion)
    put("observaciones", t.observaciones)
    put("estado", t.estado)
    put("en_informe", t.enInforme)
}

internal fun jsonContenidoInforme(c: ContenidoInformePsico): JsonObject = buildJsonObject {
    putJsonObject("filiacion") { CAMPOS_FILIACION.forEach { put(it.valor, c.filiacion[it.valor].orEmpty()) } }
    putJsonObject("secciones") { SECCIONES_INFORME.forEach { put(it.clave, c.secciones[it.clave].orEmpty()) } }
    put("lugar", c.lugar)
    put("fecha", c.fecha)
    // Fase 3: solo `incluir` (los perfiles los calcula el servidor). Sin tocarlo no viaja.
    c.anexoPerfiles?.let { a -> putJsonObject("anexoPerfiles") { put("incluir", a.incluir) } }
    // La marca de IA solo crece: el servidor une la que llega con la guardada.
    c.asistidoIa?.let { a ->
        putJsonObject("asistido_ia") {
            putJsonArray("secciones") { a.secciones.forEach { add(JsonPrimitive(it)) } }
            put("modelo", a.modelo)
            put("fecha", a.fecha)
            put("sinConsentimientoConfirmado", a.sinConsentimientoConfirmado)
        }
    }
}

// ── Reglas de presentación (puras) ───────────────────────────────────────────

/**
 * ¿Mostrar "🧠 Evaluación"? (puedeVerEvaluacionPsico de la web). La base decide
 * igual con RLS; esto es para no ofrecer un botón que respondería "sin acceso".
 * Admin, o el profesional del tratamiento, o quien atiende alguna de sus citas o
 * sesiones (o está en su `cita_equipo`).
 */
fun puedeVerEvaluacionPsico(
    rol: String?,
    miTerapeutaId: String?,
    tratamientoTerapeutaId: String?,
    terapeutasCitas: List<String?> = emptyList(),
): Boolean {
    if (rol == "Admin") return true
    val yo = miTerapeutaId ?: return false
    return tratamientoTerapeutaId == yo || yo in terapeutasCitas
}

/**
 * "+ Agregar cita de evaluación": es agenda (no contenido clínico) → cualquier
 * staff con permiso de citas o sesiones, con el tratamiento vivo y la ficha activa.
 */
fun puedeAgregarCitaEvaluacion(puedeCitas: Boolean, puedeSesiones: Boolean, estadoTratamiento: String?, fichaInactiva: Boolean): Boolean =
    !fichaInactiva && (puedeCitas || puedeSesiones) && (estadoTratamiento == "Activo" || estadoTratamiento == "Completado")

/** "8 años 3 meses" a partir de meses cumplidos (textoEdad de la web). */
fun textoEdadMeses(meses: Int?): String {
    if (meses == null || meses < 0) return ""
    val a = meses / 12
    val m = meses % 12
    val anos = if (a == 1) "1 año" else "$a años"
    val mes = if (m == 1) "1 mes" else "$m meses"
    return when {
        a == 0 -> mes
        m == 0 -> anos
        else -> "$anos $mes"
    }
}

/** dd/mm/aaaa sin pasar por Date (no corre el día por zona horaria). */
fun fechaDmyPsico(iso: String?): String {
    val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(iso.orEmpty()) ?: return ""
    val (a, mes, d) = m.destructured
    return "$d/$mes/$a"
}

/** "cita 2 de 3" del encabezado (la siguiente por hacer, sin pasarse del total). */
fun textoCitaDeEvaluacion(t: TratamientoEvalPsico?): String {
    if (t == null) return ""
    val total = t.totalSesiones
    val actual = minOf(t.sesionesCompletadas + 1, maxOf(total, 1))
    val devolucion = when (t.devolucion) {
        "incluida" -> " · la última es la devolución"
        "adicional" -> " · devolución aparte"
        else -> ""
    }
    return "cita $actual de ${if (total > 0) total.toString() else "—"}$devolucion"
}

private fun sinTildesPsico(s: String): String = s.lowercase()
    .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u').replace('ñ', 'n')

/**
 * Catálogo filtrado (texto en nombre corto, nombre o categoría; y población) y
 * agrupado por categoría. Los grupos van en el orden del servidor: la primera
 * aparición manda, así el ranking de los más usados queda arriba.
 */
fun agruparCatalogoPsico(
    tests: List<TestCatalogoPsico>,
    busqueda: String,
    poblacion: String? = null,
): List<Pair<String, List<TestCatalogoPsico>>> {
    val q = sinTildesPsico(busqueda.trim())
    val filtrados = tests.filter { t ->
        (poblacion == null || poblacion in t.poblacion) &&
            (q.isEmpty() || listOf(t.nombreCorto, t.nombre, t.categoria).any { sinTildesPsico(it).contains(q) })
    }
    return filtrados.groupBy { it.categoria }.toList()
}

/** Filas de puntaje vacías con las escalas del catálogo (por si el servidor no las precargó). */
fun puntajesDesdeEscalas(escalas: List<EscalaCatalogoPsico>): List<PuntajeEscalaPsico> =
    escalas.map { it.nombre.trim() }.filter { it.isNotEmpty() }.map { PuntajeEscalaPsico(escala = it) }

/** Id local estable de una fuente nueva (lo genera el cliente, como la web). */
fun nuevoIdFuente(existentes: List<FuentePsico>, semilla: Long): String {
    var n = semilla
    var id = "f${n.toString(36)}"
    while (existentes.any { it.id == id }) { n++; id = "f${n.toString(36)}" }
    return id
}

/** Al quitar una foto: deja de estar en el genograma y en los adjuntos de las fuentes. */
fun sinFoto(ev: EvaluacionPsico, fotoId: String): EvaluacionPsico = ev.copy(
    entrevista = if (ev.entrevista.genogramaDocumentoId == fotoId) ev.entrevista.copy(genogramaDocumentoId = null) else ev.entrevista,
    fuentes = ev.fuentes.map { f -> if (fotoId in f.documentoIds) f.copy(documentoIds = f.documentoIds - fotoId) else f },
)

/**
 * Precio propuesto al elegir el servicio del plan: si no había precio, servicio
 * × sesiones (con 2 decimales). Si ya había uno, se respeta (lo pudo negociar).
 */
fun precioPropuestoPlan(precioActual: Double?, precioServicio: Double?, sesiones: Int?): Double? {
    if (precioActual != null) return precioActual
    if (precioServicio == null || sesiones == null || sesiones <= 0) return null
    return kotlin.math.round(precioServicio * sesiones * 100) / 100
}

/** La sugerencia se ofrece solo si cambia algo ("Sugerido: N sesiones — usar"). */
fun ofrecerSugerencia(sugerencia: SugerenciaSesionesPsico?, numeroSesiones: Int?): Boolean =
    sugerencia != null && sugerencia.sesiones != numeroSesiones

// ── Informe: plantilla y versiones (fase 2) ──────────────────────────────────

/** La plantilla con que se muestra UN informe: la copiada al armarlo o, en los de la fase 1, la estándar. */
fun plantillaDelInforme(c: ContenidoInformePsico?): PlantillaInformePsico = c?.plantilla ?: plantillaInformePorDefecto()

/**
 * Secciones VISIBLES con su número, en el orden de la plantilla
 * (seccionesNumeradas de la web). La 1 es la filiación, así que empiezan en 2;
 * la impresión diagnóstica que va JUSTO después de las conclusiones comparte su
 * número. Con la estándar: 2, 3, 4, 5, 6, 7, 7, 8.
 */
fun seccionesNumeradasInforme(p: PlantillaInformePsico?): List<SeccionInforme> {
    val pl = p ?: plantillaInformePorDefecto()
    val out = mutableListOf<SeccionInforme>()
    var n = 1
    pl.secciones.forEach { s ->
        // Las obligatorias nunca se ocultan (aunque llegue una plantilla rara).
        if (!s.visible && s.clave !in SECCIONES_OBLIGATORIAS_INFORME) return@forEach
        if (!(s.clave == "impresionDiagnostica" && out.lastOrNull()?.clave == "conclusiones")) n += 1
        out += SeccionInforme(s.clave, n, s.titulo)
    }
    return out
}

/**
 * "Versión 2. Reemplaza a la versión 1 emitida el 05/10/2026." (textoReemplazo
 * de la web; la nueva versión siempre es la anterior + 1). "" sin reemplazo.
 */
fun textoReemplazoInforme(r: ReemplazaAPsico?): String {
    if (r == null || r.version < 1) return ""
    val fecha = fechaDmyPsico(r.emitido)
    return "Versión ${r.version + 1}. Reemplaza a la versión ${r.version}${if (fecha.isNotEmpty()) " emitida el $fecha" else ""}."
}

/** Estado de una fila del historial: (texto, tono) con tono 'vigente' | 'reemplazado' | 'borrador' | 'emitido'. */
fun estadoVersionInforme(v: VersionInformePsico): Pair<String, String> = when {
    v.estado == "borrador" -> "Borrador" to "borrador"
    v.pdfPendiente -> "PDF pendiente" to "pendiente"
    v.vigente -> "Vigente · lo ve el paciente" to "vigente"
    v.reemplazadoPorVersion != null -> "Reemplazado por v${v.reemplazadoPorVersion}" to "reemplazado"
    v.reemplazadoPor != null -> "Reemplazado" to "reemplazado"
    else -> "Emitido" to "emitido"
}

/**
 * Id del PDF del informe que se está MOSTRANDO (no el vigente): con un borrador
 * v2 abierto, `informePdf` es el de la v1 y no se mezcla. null = sin PDF propio
 * (borrador, o emitido cuyo PDF falló → "Generar PDF pendiente").
 */
fun idPdfDeInforme(informe: InformePsico?, versiones: List<VersionInformePsico>): String? {
    if (informe == null || !informe.emitido) return null
    return informe.documentoId ?: versiones.firstOrNull { it.id == informe.id }?.idPdf
}

/** Emitido sin su PDF (falló al generarlo): se ofrece "Generar PDF pendiente". */
fun pdfPendienteDeInforme(informe: InformePsico?, versiones: List<VersionInformePsico>): Boolean =
    informe != null && informe.emitido && idPdfDeInforme(informe, versiones) == null

/** "Descartar borrador": solo el de una versión nueva (v >= 2); la v1 se edita y emite. */
fun puedeDescartarBorrador(informe: InformePsico?): Boolean =
    informe != null && !informe.emitido && informe.version > 1

/** El historial se muestra cuando hay más de una versión (como la web). */
fun mostrarHistorialInforme(versiones: List<VersionInformePsico>): Boolean = versiones.size > 1

/**
 * Error de "Emitir nueva versión". Un servidor sin la fase 2 responde
 * `400 DATOS_INVALIDOS` ("Acción no válida"): se dice que aún no está disponible.
 */
fun mensajeErrorNuevaVersion(status: Int, codigo: String?, error: String?): String =
    mensajeErrorAccionFase2("Emitir una nueva versión del informe", status, codigo, error)

/** Igual, para una acción nueva de la fase 2 ("Descartar borrador"…) en un servidor que aún no la tiene. */
fun mensajeErrorAccionFase2(que: String, status: Int, codigo: String?, error: String?): String =
    if (codigo == "DATOS_INVALIDOS" || (status == 404 && codigo == null))
        "$que aún no está disponible. Inténtalo más tarde."
    else mensajeErrorPsico(status, codigo, error)

/**
 * Frase propia de cada `codigo` del contrato (§2 y los puntuales). Los que no
 * están aquí muestran el texto del servidor. null = sin frase propia.
 */
internal fun fraseDeCodigoPsico(codigo: String?): String? = when (codigo) {
    "SIN_ACCESO_EVALUACION" -> "Solo el Admin y el profesional tratante pueden ver esta evaluación (es confidencial)."
    "SIN_PERMISO" -> "No tienes permiso para agregar citas a la evaluación (se necesita el permiso de citas o de sesiones)."
    "NO_ES_STAFF" -> "Tu usuario no es del equipo de esta clínica. Revisa la clínica activa."
    "EVALUACION_CERRADA" -> "El informe ya se emitió: la evaluación quedó en solo lectura."
    "INFORME_YA_EMITIDO" -> "El informe ya se emitió: queda congelado y no se puede cambiar. Para corregirlo, emite una nueva versión."
    "INFORME_EMITIDO_CONGELADO" -> "Un informe emitido no se edita. Para corregirlo, emite una nueva versión."
    "INFORME_EMITIDO_NO_SE_BORRA" -> MOTIVO_INFORME_NO_SE_BORRA
    "INFORME_NO_EMITIDO" -> "El informe todavía es un borrador: edítalo y emítelo."
    "INFORME_BORRADOR_EXISTENTE" -> "Ya hay un borrador de una versión nueva. Recarga la evaluación."
    "INFORME_VERSION_INVALIDA", "INFORME_REEMPLAZO_INVALIDO", "INFORME_NACE_BORRADOR" ->
        "Otra persona cambió el informe al mismo tiempo. Recarga la evaluación."
    "TRATAMIENTO_CON_INFORME_EMITIDO" -> "Este tratamiento tiene un informe psicológico emitido: no se puede borrar."
    "NO_ES_EVALUACION_PSICOLOGICA" -> "Este tratamiento no es una evaluación psicológica."
    "TRATAMIENTO_NO_ENCONTRADO" -> "No se encontró el tratamiento (o es de otra clínica)."
    "EVALUACION_NO_ENCONTRADA" -> "No se encontró la evaluación o no tienes acceso."
    "EVALUACION_SIN_TRATAMIENTO" -> "El tratamiento de esta evaluación se borró."
    "CITA_NO_ENCONTRADA" -> "No se encontró la cita."
    "SESION_NO_ENCONTRADA" -> "No se encontró la sesión."
    "DOCUMENTO_NO_ENCONTRADO", "FOTO_NO_ENCONTRADA" -> "No se encontró el archivo (o no tienes acceso)."
    "TEST_NO_ENCONTRADO" -> "No se encontró el test aplicado. Recarga la evaluación."
    "TEST_CATALOGO_NO_ENCONTRADO" -> "Ese test ya no está en el catálogo."
    "TEST_DUPLICADO" -> "La clínica ya tiene un test propio con ese nombre corto."
    "TRATAMIENTO_CAMBIO" -> "Otra persona cambió las citas de esta evaluación al mismo tiempo. Recarga e inténtalo de nuevo."
    "TRATAMIENTO_DE_OTRO_PACIENTE" -> "Ese tratamiento es de otro paciente."
    // Fase 3 (§14)
    "INSTRUMENTO_DESCONOCIDO" -> "Ese instrumento no tiene autocálculo. Actualiza la app."
    "INSTRUMENTO_NO_CORRESPONDE" -> "Ese instrumento no corresponde a este test (solo se autocalculan los del catálogo de Sania)."
    "AUTOCALCULO_NO_DISPONIBLE" -> "Responder ítems aún no está disponible. Inténtalo más tarde."
    "APLICACION_ANTERIOR_NO_ENCONTRADA" -> "No se encontró esa aplicación anterior (o no tienes acceso)."
    "PLAN_SIN_IA" -> "El borrador con IA no está incluido en el plan de la clínica."
    "SIN_SECCIONES_VACIAS" -> "No hay secciones vacías para proponer: la IA solo redacta las que están en blanco."
    "IA_SIN_PROPUESTA" -> "La IA no devolvió una propuesta útil. Inténtalo de nuevo."
    "DEMASIADAS_SOLICITUDES" -> "Demasiadas solicitudes seguidas. Espera un minuto e inténtalo de nuevo."
    "INFORME_NO_ENCONTRADO" -> "No se encontró el informe. Recarga la evaluación."
    else -> null
}

/** Texto de un error del servidor para el usuario (algunos códigos con frase propia). */
fun mensajeErrorPsico(status: Int, codigo: String?, error: String?): String = fraseDeCodigoPsico(codigo) ?: when {
    codigo == "NO_AUTENTICADO" || status == 401 -> "Tu sesión expiró. Vuelve a entrar."
    // 404 sin `codigo` ni texto propio (la página 404 de Next): el endpoint no
    // existe todavía en el servidor.
    status == 404 && codigo == null && (error.isNullOrBlank() || error.startsWith("No se pudo completar (HTTP")) ->
        "La evaluación psicológica aún no está disponible. Actualiza la app o inténtalo más tarde."
    // INFORME_INCOMPLETO, DATOS_INVALIDOS, LIMITE_PLAN…: el texto del servidor dice qué falta.
    !error.isNullOrBlank() -> error
    else -> "No se pudo completar (HTTP $status)."
}

/**
 * Un error de la base (trigger) dentro del mensaje de una excepción de
 * PostgREST → su frase. Para borrados directos (documentos) que la base frena.
 */
fun fraseDeErrorBasePsico(mensaje: String?): String? {
    val m = mensaje ?: return null
    return listOf("INFORME_EMITIDO_NO_SE_BORRA", "TRATAMIENTO_CON_INFORME_EMITIDO", "EVALUACION_CERRADA", "INFORME_EMITIDO_CONGELADO")
        .firstOrNull { m.contains(it) }?.let { fraseDeCodigoPsico(it) }
}
