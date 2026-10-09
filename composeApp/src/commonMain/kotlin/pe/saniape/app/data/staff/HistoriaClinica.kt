package pe.saniape.app.data.staff

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * HISTORIA CLÍNICA por especialidad — el contrato del servidor
 * (`GET /api/staff/historia/{id}?formato=json`, tipos en `lib/historia-tipos.ts`
 * de la web). El servidor decide QUÉ va y en qué orden (norma, plan, permisos,
 * personalización de la clínica); la app dibuja lo que llega, en ese orden.
 *
 * Reglas que respeta este parseo:
 *  · `tipo` de sección desconocido → se ignora (compatibilidad hacia adelante).
 *  · Una sección que no se puede leer se salta: nunca tumba la historia entera.
 *  · Campos ausentes / null → su valor por defecto.
 *
 * Sin Compose y sin red: se prueba entero (HistoriaClinicaParseoTest).
 */

@Serializable
data class ParHc(val etiqueta: String = "", val valor: String = "", val alerta: Boolean = false)

@Serializable
data class FirmaDigitalHc(val firmante: String = "", val firmadoAt: String = "", val certificado: String? = null)

@Serializable
data class FirmaHc(
    val titulo: String = "",
    val nombre: String? = null,
    val colegiatura: String? = null,
    val digital: FirmaDigitalHc? = null,
)

/** Los datos de una sección; cada `tipo` tiene su clase. */
sealed interface DatosHc

@Serializable
data class DatosEncabezadoHc(val nombre: String = "", val iniciales: String = "", val filas: List<ParHc> = emptyList()) : DatosHc

@Serializable
data class DatosFiliacionHc(
    val numeroHC: String = "",
    val apertura: String? = null,
    val ipress: String? = null,
    /** Valor "" = falta (en papel se imprime la raya para llenar). */
    val filas: List<ParHc> = emptyList(),
    val alergias: String? = null,
) : DatosHc

@Serializable
data class DatosContactoHc(val email: String = "") : DatosHc

@Serializable
data class DiagnosticoRecienteHc(val texto: String = "", val fecha: String = "")

@Serializable
data class DatosDiagnosticoHc(val reciente: DiagnosticoRecienteHc? = null, val inicial: String? = null) : DatosHc

/** `antecedentes` y `campos_propios`: etiqueta → valor (alerta = alergias en rojo). */
@Serializable
data class DatosParesHc(val items: List<ParHc> = emptyList()) : DatosHc

@Serializable
data class DiagnosticoHc(
    val descripcion: String = "",
    val codigo: String? = null,
    /** 'P' presuntivo · 'D' definitivo · 'R' repetitivo */
    val tipo: String = "",
    val tipoNombre: String = "",
)

@Serializable
data class RecetaHc(
    val id: String = "",
    val numero: String = "",
    val fecha: String = "",
    val anulada: Boolean = false,
    val items: List<String> = emptyList(),
    val profesional: String? = null,
    val citaId: String? = null,
)

@Serializable
data class ConsentimientoHc(
    val id: String = "",
    val citaId: String? = null,
    val procedimiento: String = "",
    val estado: String = "",
    val emitido: String? = null,
    val firmado: String? = null,
    val profesional: String? = null,
)

@Serializable
data class AtencionHc(
    val id: String = "",
    val citaId: String? = null,
    val fecha: String = "",
    val hora: String? = null,
    val titulo: String = "",
    val profesional: String? = null,
    val dental: Boolean = false,
    val triaje: String? = null,
    val campos: List<ParHc> = emptyList(),
    val diagnosticos: List<DiagnosticoHc> = emptyList(),
    val diagnosticoSinCodificar: String? = null,
    val recetas: List<RecetaHc> = emptyList(),
    val consentimientos: List<ConsentimientoHc> = emptyList(),
    /** Lo que la norma espera y falta: aviso EN PANTALLA, no se imprime. */
    val faltan: List<String> = emptyList(),
    val firma: FirmaHc = FirmaHc(),
)

@Serializable
data class DatosAtencionesHc(val items: List<AtencionHc> = emptyList()) : DatosHc

@Serializable
data class DatosConsentimientosHc(val items: List<ConsentimientoHc> = emptyList(), val nota: String = "") : DatosHc

@Serializable
data class PuntoEvaHc(val numero: Int = 0, val fecha: String = "", val inicio: Int? = null, val fin: Int? = null)

@Serializable
data class SesionHc(
    val id: String = "",
    val numero: Int = 0,
    val fecha: String = "",
    val hora: String? = null,
    val procedimientos: String? = null,
    val piezas: List<String> = emptyList(),
    val motivo: String? = null,
    val mejorias: String? = null,
    /** "entra 7 → sale 3" */
    val eva: String? = null,
    val profesional: String? = null,
    val duracion: Int? = null,
    val costo: Double? = null,
)

@Serializable
data class CuadroSesionesHc(
    /** "Profesional: X · 45 min por sesión" cuando no cambian entre sesiones. */
    val comunes: List<String> = emptyList(),
    val verEvolucion: Boolean = false,
    val verProfesional: Boolean = false,
    val verDuracion: Boolean = false,
    val verCosto: Boolean = false,
    val filas: List<SesionHc> = emptyList(),
)

@Serializable
data class PagoFilaHc(
    val clave: String = "",
    val fecha: String = "",
    val metodo: String = "",
    val nota: String? = null,
    val monto: Double = 0.0,
)

@Serializable
data class PagosTratamientoHc(
    val filas: List<PagoFilaHc> = emptyList(),
    val acordado: Double = 0.0,
    val pagado: Double = 0.0,
    val saldo: Double = 0.0,
)

@Serializable
data class TratamientoHc(
    val id: String = "",
    val rubro: String = "general",
    val nombre: String = "",
    val modalidad: String? = null,
    val estado: String? = null,
    val estadoPago: String? = null,
    val profesional: String? = null,
    val fechaInicio: String? = null,
    /** "3/10" o "3" */
    val sesiones: String = "",
    val precios: List<ParHc> = emptyList(),
    val diagnostico: String? = null,
    val notas: String? = null,
    val curvaEva: List<PuntoEvaHc> = emptyList(),
    val cuadro: CuadroSesionesHc = CuadroSesionesHc(),
    /** Solo con permiso de pagos. */
    val pagos: PagosTratamientoHc? = null,
    /** Moneda de los montos de este tratamiento (multipaís). null = la de [RegionalHc]. */
    val moneda: String? = null,
)

@Serializable
data class DatosTratamientosHc(val items: List<TratamientoHc> = emptyList()) : DatosHc

@Serializable
data class ConsultaHc(
    val id: String = "",
    val fecha: String = "",
    val hora: String? = null,
    val tipo: String? = null,
    val profesional: String? = null,
    val estado: String? = null,
    val diagnostico: String? = null,
    val notas: String? = null,
)

/** `consultas` y `controles`. */
@Serializable
data class DatosConsultasHc(val items: List<ConsultaHc> = emptyList()) : DatosHc

@Serializable
data class SolicitudHc(
    val id: String = "",
    val tipo: String = "",
    val titulo: String = "",
    val fecha: String = "",
    val estado: String = "",
    val resultado: String? = null,
    val resultadoUrl: String? = null,
)

@Serializable
data class DatosExamenesHc(val items: List<SolicitudHc> = emptyList()) : DatosHc

@Serializable
data class FilaComparativoHc(
    val clave: String = "",
    val etiqueta: String = "",
    val nota: String? = null,
    val inicial: String = "",
    val fechaInicial: String = "",
    val ultima: String? = null,
    val fechaUltima: String? = null,
    val cambio: String = "",
    /** true mejoró (verde) · false empeoró (rojo) · null neutro. */
    val mejora: Boolean? = null,
)

@Serializable
data class ObjetivoHc(
    val id: String = "",
    val texto: String = "",
    val logrado: Boolean = false,
    val medida: String = "",
    val estado: String = "",
)

@Serializable
data class ZonasTextoHc(val inicial: List<String> = emptyList(), val ultima: List<String> = emptyList())

@Serializable
data class DatosFisioEvaluacionHc(
    val resumen: String = "",
    val comparativo: List<FilaComparativoHc> = emptyList(),
    val hayUltima: Boolean = false,
    /** id de zona del mapa corporal → 1 leve · 2 moderado · 3 severo. */
    val zonasInicial: Map<String, Int>? = null,
    val zonasUltima: Map<String, Int>? = null,
    val zonasTexto: ZonasTextoHc = ZonasTextoHc(),
    val objetivos: List<ObjetivoHc> = emptyList(),
    val objetivosLogrados: Int = 0,
) : DatosHc

@Serializable
data class SerieEvaHc(val tratamientoId: String = "", val titulo: String = "", val puntos: List<PuntoEvaHc> = emptyList())

@Serializable
data class DatosEvolucionEvaHc(val series: List<SerieEvaHc> = emptyList()) : DatosHc

/** Un hallazgo dental tal como se dibuja (mismo formato que `odontograma_snapshots.datos`). */
@Serializable
data class HallazgoDentalHc(
    val diente: String = "",
    @SerialName("diente_hasta") val dienteHasta: String? = null,
    val superficies: List<String>? = null,
    val estado: String = "Pendiente",
    @SerialName("hallazgo_id") val hallazgoId: String = "",
    val nombre: String = "",
    val color: String = "#dc2626",
    @SerialName("marca_ausente") val marcaAusente: Boolean = false,
    @SerialName("por_boca") val porBoca: Boolean = false,
    val notas: String? = null,
)

@Serializable
data class OdontogramaInicialHc(val snapshotId: String = "", val fecha: String = "", val hallazgos: List<HallazgoDentalHc> = emptyList())

@Serializable
data class OdontogramaActualHc(val fecha: String = "", val hallazgos: List<HallazgoDentalHc> = emptyList())

@Serializable
data class DatosOdontogramaHc(
    /** El inicial (inalterable por norma). null si nunca se fijó. */
    val inicial: OdontogramaInicialHc? = null,
    val actual: OdontogramaActualHc = OdontogramaActualHc(),
    val firma: FirmaHc = FirmaHc(),
) : DatosHc

@Serializable
data class DatosPeriodontogramaHc(
    val id: String = "",
    val fecha: String = "",
    /** Mismo formato que `periodontogramas.datos` (pieza → mediciones). */
    val datos: JsonObject = JsonObject(emptyMap()),
    val notas: String? = null,
    val profesional: String? = null,
) : DatosHc

@Serializable
data class PiezasSesionHc(
    val sesionId: String = "",
    val tratamiento: String = "",
    val numero: Int = 0,
    val fecha: String = "",
    val piezas: List<String> = emptyList(),
)

@Serializable
data class DatosPiezasSesionHc(val items: List<PiezasSesionHc> = emptyList()) : DatosHc

@Serializable
data class PuntajeEscalaHc(
    val escala: String = "",
    val directo: String = "",
    val transformado: String = "",
    val percentil: String = "",
    val categoria: String = "",
)

@Serializable
data class SeccionInformePsicoHc(val numero: Int = 0, val titulo: String = "", val texto: String = "")

@Serializable
data class InformePsicoHc(
    val id: String = "",
    val version: Int = 1,
    val emitido: String? = null,
    val encabezado: String? = null,
    val reemplazo: String? = null,
    val filiacion: List<ParHc> = emptyList(),
    val secciones: List<SeccionInformePsicoHc> = emptyList(),
    val psicologo: String? = null,
    val colegiatura: String? = null,
    val lugarFecha: String? = null,
    val pie: String? = null,
)

@Serializable
data class TestPsicoHc(
    val id: String = "",
    val nombre: String = "",
    val fecha: String = "",
    val edadTexto: String? = null,
    val validez: String? = null,
    val global: String? = null,
    val puntajes: List<PuntajeEscalaHc> = emptyList(),
)

@Serializable
data class EvaluacionPsicoHc(
    val evaluacionId: String = "",
    val tratamientoId: String? = null,
    val titulo: String = "",
    val informe: InformePsicoHc? = null,
    val tests: List<TestPsicoHc> = emptyList(),
)

/**
 * Psicología (privacidad): el informe solo viaja si se pidió `psico=informe`, y
 * los puntajes solo con `psico=…tests`. Sin pedirlos, `evaluaciones` llega vacía
 * y los flags dicen qué se puede pedir (para ofrecer las casillas).
 */
@Serializable
data class DatosPsicologiaHc(
    val hayInforme: Boolean = false,
    val hayEvaluaciones: Boolean = false,
    val incluyeInforme: Boolean = false,
    val incluyeTests: Boolean = false,
    val evaluaciones: List<EvaluacionPsicoHc> = emptyList(),
) : DatosHc

@Serializable
data class TomaVitalHc(
    val fecha: String = "",
    val hora: String? = null,
    val texto: String = "",
    val presionSistolica: Double? = null,
    val presionDiastolica: Double? = null,
    val frecuenciaCardiaca: Double? = null,
    val frecuenciaRespiratoria: Double? = null,
    val temperatura: Double? = null,
    val saturacionO2: Double? = null,
    val peso: Double? = null,
    val talla: Double? = null,
    val imc: Double? = null,
    val perimetroAbdominal: Double? = null,
    val tomadoPor: String? = null,
)

@Serializable
data class DatosSignosVitalesHc(val tomas: List<TomaVitalHc> = emptyList()) : DatosHc

@Serializable
data class DatosRecetasHc(val items: List<RecetaHc> = emptyList()) : DatosHc

/**
 * Gineco-obstetricia: una fila del cuadro de controles prenatales (carné
 * perinatal). AU y LCF los lee la web de lo escrito en el examen.
 */
@Serializable
data class ControlPrenatalHc(
    val id: String = "",
    val numero: Int = 0,
    val fecha: String = "",
    val hora: String? = null,
    /** "24 sem 3 d" o null (sin FUR). */
    val edadGestacional: String? = null,
    /** "110/70" */
    val presionArterial: String? = null,
    val peso: Double? = null,
    val alturaUterina: Double? = null,
    val lcf: Double? = null,
    val examen: String? = null,
    val indicaciones: String? = null,
    val proximoControl: String? = null,
    val profesional: String? = null,
)

@Serializable
data class DatosControlesPrenatalesHc(
    /** FUR, FPP, edad gestacional hoy, N° de controles. */
    val resumen: List<ParHc> = emptyList(),
    val filas: List<ControlPrenatalHc> = emptyList(),
) : DatosHc

/** "EG 24 sem 3 d · PA 110/70 · 63.5 kg · AU 24 cm · LCF 140" — lo medido en un control, en una línea. */
fun medidasControlPrenatalHc(f: ControlPrenatalHc): String = listOfNotNull(
    f.edadGestacional?.takeIf { it.isNotBlank() }?.let { "EG $it" },
    f.presionArterial?.takeIf { it.isNotBlank() }?.let { "PA $it" },
    f.peso?.let { "${numeroHc(it)} kg" },
    f.alturaUterina?.let { "AU ${numeroHc(it)} cm" },
    f.lcf?.let { "LCF ${numeroHc(it)}" },
).joinToString(" · ")

@Serializable
data class MedicionHc(
    val fecha: String = "",
    val peso: Double? = null,
    val talla: Double? = null,
    val imc: Double? = null,
    val clasificacionImc: String? = null,
    val perimetroAbdominal: Double? = null,
    val cintura: Double? = null,
    val cadera: Double? = null,
    val grasaPct: Double? = null,
    val masaMuscular: Double? = null,
    val notas: String? = null,
    /** 'nutricion' · 'triaje' · 'ficha' */
    val origen: String = "",
)

@Serializable
data class CambioPesoHc(val desde: Double = 0.0, val hasta: Double = 0.0, val diferencia: Double = 0.0)

@Serializable
data class DatosAntropometriaHc(
    /** Cronológica (la más antigua primero). */
    val mediciones: List<MedicionHc> = emptyList(),
    val cambioPeso: CambioPesoHc? = null,
) : DatosHc

@Serializable
data class FotoHc(
    val id: String = "",
    /** URL firmada (1 h): no se guarda. */
    val url: String? = null,
    val nombre: String = "",
    val momento: String? = null,
    val fecha: String = "",
    val notas: String? = null,
    val tratamientoId: String? = null,
)

@Serializable
data class ParFotosHc(val tratamientoId: String? = null, val titulo: String = "", val antes: FotoHc = FotoHc(), val despues: FotoHc = FotoHc())

@Serializable
data class DatosFotosHc(val fotos: List<FotoHc> = emptyList(), val pares: List<ParFotosHc> = emptyList()) : DatosHc

/**
 * Una sección OBLIGATORIA (de la norma) que la app no pudo leer: no se descarta
 * en silencio, se muestra el aviso "ver PDF" (el HTML del servidor sí la trae).
 */
data object DatosNoLegiblesHc : DatosHc

/** Una sección ya leída. [tipo] es el del contrato ("tratamientos", "controles"…). */
data class SeccionHc(
    val id: String,
    val tipo: String,
    val titulo: String,
    val obligatoria: Boolean,
    val datos: DatosHc,
)

data class BloqueHc(val id: String, val rubro: String, val titulo: String?, val secciones: List<SeccionHc>)

@Serializable
data class ClinicaHc(val nombre: String = "", val logoUrl: String? = null, val ipress: String? = null)

@Serializable
data class PacienteHc(val id: String = "", val nombre: String = "", val iniciales: String = "")

@Serializable
data class PermisosHc(val verContacto: Boolean = false, val verPagos: Boolean = false, val soloLoMio: Boolean = false)

/** País de la sede del paciente (multipaís). Respaldo: Perú, soles, DNI. */
@Serializable
data class RegionalHc(
    val pais: String = "PE",
    val moneda: String = "PEN",
    val zona: String = "America/Lima",
    /** "DNI" / "CI"… donde la app diría "DNI". */
    val etiquetaDocumento: String = "DNI",
)

@Serializable
data class PieHc(val izquierda: String = "", val derecha: String = "")

data class HistoriaClinicaDoc(
    val version: Int,
    /** "estandar" | "especialidad" */
    val formato: String,
    val formatoEspecialidadDisponible: Boolean,
    /** Dibujar gráficos (curva EVA, peso, fotos lado a lado). */
    val graficos: Boolean,
    val generadoEn: String,
    val fechaGeneracion: String,
    val clinica: ClinicaHc,
    val paciente: PacienteHc,
    val rubros: List<String>,
    val permisos: PermisosHc,
    val bloques: List<BloqueHc>,
    val pie: PieHc,
    val regional: RegionalHc = RegionalHc(),
) {
    val esPorEspecialidad: Boolean get() = formato == "especialidad"
    /** Todas las secciones, en orden (para buscar una en particular). */
    val secciones: List<SeccionHc> get() = bloques.flatMap { it.secciones }
}

// ── Lectura cruda (el sobre) ────────────────────────────────────────────────

@Serializable
private data class BloqueCrudoHc(
    val id: String = "",
    val rubro: String = "general",
    val titulo: String? = null,
    val secciones: List<JsonElement> = emptyList(),
)

@Serializable
private data class DocCrudoHc(
    val version: Int = 0,
    val formato: String = "estandar",
    val formatoEspecialidadDisponible: Boolean = false,
    val graficos: Boolean = false,
    val generadoEn: String = "",
    val fechaGeneracion: String = "",
    val clinica: ClinicaHc = ClinicaHc(),
    val paciente: PacienteHc = PacienteHc(),
    val rubros: List<String> = emptyList(),
    val permisos: PermisosHc = PermisosHc(),
    val bloques: List<BloqueCrudoHc>? = null,
    val pie: PieHc = PieHc(),
    val regional: RegionalHc? = null,
)

internal val jsonHc = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false; isLenient = true }

/** Versión del contrato que esta app sabe dibujar. */
const val VERSION_HC_APP = 1

/**
 * Lee la historia del JSON del servidor. null = no es una historia (p.ej. la web
 * vieja devolvió HTML, o un JSON de error): quien llama cae al HTML de siempre.
 */
fun parsearHistoriaClinica(texto: String): HistoriaClinicaDoc? {
    val raiz = runCatching { jsonHc.parseToJsonElement(texto).jsonObject }.getOrNull() ?: return null
    if (raiz["bloques"] == null) return null
    val crudo = runCatching { jsonHc.decodeFromJsonElement(DocCrudoHc.serializer(), raiz) }.getOrNull() ?: return null
    val bloques = crudo.bloques ?: return null
    return HistoriaClinicaDoc(
        version = crudo.version,
        formato = crudo.formato,
        formatoEspecialidadDisponible = crudo.formatoEspecialidadDisponible,
        graficos = crudo.graficos,
        generadoEn = crudo.generadoEn,
        fechaGeneracion = crudo.fechaGeneracion,
        clinica = crudo.clinica,
        paciente = crudo.paciente,
        rubros = crudo.rubros,
        permisos = crudo.permisos,
        bloques = bloques.map { b ->
            BloqueHc(b.id, b.rubro, b.titulo?.takeIf { it.isNotBlank() }, b.secciones.mapNotNull(::parsearSeccionHc))
        }.filter { it.secciones.isNotEmpty() },
        pie = crudo.pie,
        regional = crudo.regional ?: RegionalHc(),
    )
}

private fun JsonObject.texto(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

/**
 * Una sección: tipo desconocido o ilegible → null (se salta, no rompe la
 * historia), SALVO que sea obligatoria (de la norma): entonces llega con
 * [DatosNoLegiblesHc] para avisar "ver PDF" en vez de desaparecer.
 */
internal fun parsearSeccionHc(el: JsonElement): SeccionHc? {
    val o = el as? JsonObject ?: return null
    val tipo = o.texto("tipo") ?: return null
    val obligatoria = o.texto("obligatoria") == "true"
    val datos = o["datos"]?.takeIf { it !is JsonNull } ?: JsonObject(emptyMap())
    val d: DatosHc = runCatching {
        fun <T : DatosHc> leer(s: kotlinx.serialization.KSerializer<T>): T = jsonHc.decodeFromJsonElement(s, datos)
        when (tipo) {
            "encabezado" -> leer(DatosEncabezadoHc.serializer())
            "filiacion" -> leer(DatosFiliacionHc.serializer())
            "contacto" -> leer(DatosContactoHc.serializer())
            "diagnostico" -> leer(DatosDiagnosticoHc.serializer())
            "antecedentes", "campos_propios" -> leer(DatosParesHc.serializer())
            "atenciones" -> leer(DatosAtencionesHc.serializer())
            "consentimientos" -> leer(DatosConsentimientosHc.serializer())
            "tratamientos" -> leer(DatosTratamientosHc.serializer())
            "consultas", "controles" -> leer(DatosConsultasHc.serializer())
            "examenes" -> leer(DatosExamenesHc.serializer())
            "fisio_evaluacion" -> leer(DatosFisioEvaluacionHc.serializer())
            "evolucion_eva" -> leer(DatosEvolucionEvaHc.serializer())
            "odontograma" -> leer(DatosOdontogramaHc.serializer())
            "periodontograma" -> leer(DatosPeriodontogramaHc.serializer())
            "piezas_sesion" -> leer(DatosPiezasSesionHc.serializer())
            "psicologia" -> leer(DatosPsicologiaHc.serializer())
            "signos_vitales" -> leer(DatosSignosVitalesHc.serializer())
            "recetas" -> leer(DatosRecetasHc.serializer())
            "antropometria" -> leer(DatosAntropometriaHc.serializer())
            "fotos" -> leer(DatosFotosHc.serializer())
            "controles_prenatales" -> leer(DatosControlesPrenatalesHc.serializer())
            else -> null
        }
    }.getOrNull() ?: (if (obligatoria) DatosNoLegiblesHc else return null)
    return SeccionHc(
        id = o.texto("id") ?: tipo,
        tipo = tipo,
        titulo = o.texto("titulo") ?: "",
        obligatoria = obligatoria,
        datos = d,
    )
}

// ── Respuesta del endpoint ──────────────────────────────────────────────────

sealed class CargaHistoriaHc {
    data class Ok(val doc: HistoriaClinicaDoc) : CargaHistoriaHc()
    data class Error(val mensaje: String, val codigo: String?) : CargaHistoriaHc()
    /** El servidor no entiende `formato=json` (web vieja): se abre el HTML de siempre. */
    data object NoSoportado : CargaHistoriaHc()
}

/**
 * Decide qué hacer con lo que respondió el servidor. La web vieja ignora
 * `formato=json` y responde HTML (incluso sus errores, con 200): eso es
 * [CargaHistoriaHc.NoSoportado], no un error. Una versión del contrato más
 * nueva que la que esta app conoce también cae al HTML (que siempre está al día).
 */
fun interpretarRespuestaHistoria(status: Int, cuerpo: String): CargaHistoriaHc {
    val t = cuerpo.trimStart()
    if (t.startsWith("<")) return CargaHistoriaHc.NoSoportado
    val obj = runCatching { jsonHc.parseToJsonElement(t).jsonObject }.getOrNull()
    if (status in 200..299) {
        val doc = parsearHistoriaClinica(t) ?: return CargaHistoriaHc.NoSoportado
        if (doc.version > VERSION_HC_APP) return CargaHistoriaHc.NoSoportado
        return CargaHistoriaHc.Ok(doc)
    }
    if (obj == null) return if (status == 404 || status == 405) CargaHistoriaHc.NoSoportado
        else CargaHistoriaHc.Error("No se pudo abrir la historia clínica (error $status).", null)
    val codigo = obj.texto("codigo")
    val mensaje = obj.texto("error")?.takeIf { it.isNotBlank() } ?: when (codigo) {
        "NO_AUTENTICADO" -> "Tu sesión expiró. Vuelve a entrar."
        "SIN_ACCESO" -> "No tienes acceso a esta historia clínica."
        "NO_ENCONTRADO" -> "No se encontró el paciente."
        else -> "No se pudo abrir la historia clínica."
    }
    return CargaHistoriaHc.Error(mensaje, codigo)
}

/** Valor del parámetro `psico` según las casillas (null = no pedir nada protegido). */
fun parametroPsicoHc(informe: Boolean, tests: Boolean): String? = when {
    informe && tests -> "informe,tests"
    informe -> "informe"
    tests -> "tests"
    else -> null
}

// ── Ayudas de dibujo (puras, probadas) ──────────────────────────────────────

/** Un punto de una serie en el tiempo (peso, IMC…). */
data class PuntoSerieHc(val fecha: String, val valor: Double)

/** Peso por fecha, en orden, para el gráfico de nutrición. */
fun seriePesoHc(a: DatosAntropometriaHc): List<PuntoSerieHc> =
    a.mediciones.mapNotNull { m -> m.peso?.let { PuntoSerieHc(m.fecha, it) } }

/** 72.0 → "72"; 72.456 → "72.5". */
fun numeroHc(v: Double): String {
    val r = kotlin.math.round(v * 10) / 10
    return if (r == kotlin.math.floor(r)) r.toLong().toString() else r.toString()
}

/** Símbolo de una moneda ISO (respaldo: el código mismo). */
fun simboloMonedaHc(moneda: String?): String = when (moneda?.uppercase()) {
    null, "", "PEN" -> "S/"
    "BOB" -> "Bs"
    "USD" -> "US$"
    "EUR" -> "€"
    "CLP", "COP", "MXN", "ARS" -> "$"
    else -> moneda.uppercase()
}

/** PEN → "S/ 1,234.50", BOB → "Bs 1,234.50" (como `formatearDinero` de la web). */
fun dineroHc(v: Double, moneda: String? = "PEN"): String {
    val cent = kotlin.math.round(kotlin.math.abs(v) * 100).toLong()
    val miles = (cent / 100).toString().reversed().chunked(3).joinToString(",").reversed()
    val s = "$miles.${(cent % 100).toString().padStart(2, '0')}"
    val sim = simboloMonedaHc(moneda)
    return if (v < 0) "-$sim $s" else "$sim $s"
}

/** "S/ 120.00" (soles; ver [dineroHc] para otras monedas). */
fun solesHc(v: Double): String = dineroHc(v, "PEN")

/**
 * Los hallazgos de la HC en el formato del odontograma nativo: los registros
 * por pieza y el catálogo (nombre/color/marca de ausente) que necesita para
 * pintarlos. Solo lectura: los ids son sintéticos.
 */
fun hallazgosParaOdontograma(lista: List<HallazgoDentalHc>, pacienteId: String): Pair<List<DienteHallazgo>, List<HallazgoDental>> {
    val registros = lista.mapIndexed { i, h ->
        DienteHallazgo(
            id = "hc-$i", pacienteId = pacienteId, diente = h.diente,
            hallazgoId = h.hallazgoId.ifBlank { "hc-h-${h.nombre}" },
            superficies = h.superficies, estado = h.estado, notas = h.notas, dienteHasta = h.dienteHasta,
        )
    }
    val catalogo = lista.distinctBy { it.hallazgoId.ifBlank { "hc-h-${it.nombre}" } }.map { h ->
        HallazgoDental(
            id = h.hallazgoId.ifBlank { "hc-h-${h.nombre}" }, nombre = h.nombre, color = h.color,
            marcaAusente = h.marcaAusente, porBoca = h.porBoca,
        )
    }
    return registros to catalogo
}

/** Hay piezas de leche (cuadrantes 5 a 8) entre los hallazgos. */
fun tieneDeciduosHc(lista: List<HallazgoDentalHc>): Boolean =
    lista.any { it.diente.firstOrNull() in '5'..'8' }

/** "16 OM · Caries (Pendiente)" — la lista en texto, para quien no mira el dibujo. */
fun textoHallazgoHc(h: HallazgoDentalHc): String {
    val pieza = if (h.dienteHasta != null) "${h.diente}–${h.dienteHasta}" else h.diente
    val caras = h.superficies?.takeIf { it.isNotEmpty() }?.let { cs -> (ordenarCaras(cs) + cs.filter { it !in SUPERFICIES }).joinToString("") }?.let { " $it" } ?: ""
    return "$pieza$caras · ${h.nombre} (${h.estado})" + (h.notas?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: "")
}

/** Piezas registradas en un periodontograma (las claves de `datos`). */
fun piezasPeriodontogramaHc(d: DatosPeriodontogramaHc): List<String> = d.datos.keys.sorted()
