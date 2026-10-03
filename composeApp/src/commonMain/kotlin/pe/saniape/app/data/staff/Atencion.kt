package pe.saniape.app.data.staff

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// ─────────────────────────────────────────────────────────────────────────────
// CONSULTA GUIADA — modelos de `GET /api/staff/atencion/consulta` y reglas puras.
//
// GEMELO de la web (si cambia una, cambia la otra):
//   · lib/atencion-medica.ts → pasosDeAtencion, avisosCierre
//   · docs/app-contrato-atencion.md (§2) → forma del JSON
//
// El servidor manda `flags.pasos` y `flags.avisosCierre` ya resueltos; las
// funciones de abajo son el respaldo para recalcular en pantalla (p. ej. al
// cambiar la firma de un consentimiento sin volver a pedir todo).
// Los nombres de campo siguen el JSON tal cual (snake_case de las filas de la
// base, camelCase de lo que arma el endpoint). Se requiere un Json con
// ignoreUnknownKeys: solo se modela lo que la pantalla usa.
// ─────────────────────────────────────────────────────────────────────────────

/** Diagnóstico CIE-10. `tipo`: P presuntivo, D definitivo, R repetido. `codigo` nulo = sin codificar. */
@Serializable
data class DiagnosticoCie(
    val codigo: String? = null,
    val descripcion: String = "",
    val tipo: String = "P",
)

/** Examen auxiliar solicitado; `documento_id` liga el resultado ya subido. */
@Serializable
data class ExamenSolicitado(
    val nombre: String,
    val indicacion: String? = null,
    val documento_id: String? = null,
    val resultado: String? = null,
    val fecha_resultado: String? = null,
)

@Serializable
data class PasoAtencion(val clave: String, val titulo: String)

@Serializable
data class ControlAtencion(
    val id: String? = null,
    val fecha: String? = null,
    val hora: String? = null,
    val estado: String? = null,
)

/** Fila de `atenciones_clinicas` (solo lo que la app lee o edita). */
@Serializable
data class AtencionClinicaApp(
    val id: String? = null,
    val terapeuta_id: String? = null,
    val tratamiento_id: String? = null,
    val llegada_at: String? = null,
    val triaje_at: String? = null,
    val consulta_inicio_at: String? = null,
    val atendida_at: String? = null,
    val motivo_consulta: String? = null,
    val tiempo_enfermedad: String? = null,
    val relato: String? = null,
    val funciones_biologicas: String? = null,
    val presion_sistolica: Double? = null,
    val presion_diastolica: Double? = null,
    val frecuencia_cardiaca: Double? = null,
    val frecuencia_respiratoria: Double? = null,
    val temperatura: Double? = null,
    val saturacion_o2: Double? = null,
    val peso: Double? = null,
    val talla: Double? = null,
    val imc: Double? = null,
    val perimetro_abdominal: Double? = null,
    val examen_fisico: String? = null,
    val diagnosticos: List<DiagnosticoCie> = emptyList(),
    val examenes: List<ExamenSolicitado> = emptyList(),
    val plan_trabajo: String? = null,
    val tratamiento: String? = null,
    val observaciones: String? = null,
    val nota_procedimiento: String? = null,
    val control_cita_id: String? = null,
    val control: ControlAtencion? = null,
)

@Serializable
data class PacienteConsultaApp(
    val id: String,
    val nombre: String = "",
    val dni: String? = null,
    val estado: String = "Activo",
    val fecha_nacimiento: String? = null,
    val telefono: String? = null,
    val ocupacion: String? = null,
    val alergias: String? = null,
    val antecedentes: String? = null,
    val medicacion_actual: String? = null,
)

@Serializable
data class NombreRef(val id: String? = null, val nombre: String = "")

@Serializable
data class CitaConsultaApp(
    val id: String,
    val fecha: String = "",
    val hora: String? = null,
    val tipo: String = "",
    val estado: String = "",
    val no_asistio: Boolean = false,
    val paciente_id: String? = null,
    val terapeuta_id: String? = null,
    val especialidad_id: String? = null,
    val tratamiento_id: String? = null,
    val procedimiento_id: String? = null,
    val costo: Double = 0.0,
    val pagada_at: String? = null,
    val duracion: Int? = null,
    val notas: String? = null,
    val sede_id: String? = null,
    val diagnostico: String? = null,
    val paciente: PacienteConsultaApp? = null,
    val terapeuta: NombreRef? = null,
    val procedimiento: NombreRef? = null,
    val tratamiento: TratamientoConsultaApp? = null,
)

/**
 * El servicio del tratamiento de la cita, con sus plantillas de consentimiento
 * (`tratamiento.procedimiento` del contrato §2). Una plantilla activa = el
 * procedimiento REQUIERE consentimiento (misma regla que el endpoint).
 */
@Serializable
data class ProcedimientoTratamientoApp(
    val id: String? = null,
    val nombre: String = "",
    val especialidad_id: String? = null,
    val plantillas: List<PlantillaServicioApp> = emptyList(),
)

/** `cita.tratamiento`: solo lo que la pantalla usa. */
@Serializable
data class TratamientoConsultaApp(
    val id: String? = null,
    val diagnostico: String? = null,
    /** Para el cobro del cierre de un procedimiento: "Se cobra en su tratamiento (…)". */
    val estado_pago: String? = null,
    val precio_acordado: Double? = null,
    val procedimiento: ProcedimientoTratamientoApp? = null,
)

/** ¿El servicio de la cita pide consentimiento? (plantillas activas, como la web). */
fun requiereConsentimiento(cita: CitaConsultaApp): Boolean =
    cita.tratamiento?.procedimiento?.plantillas?.any { it.activo } == true

@Serializable
data class RecetaBreve(
    val id: String,
    val numero: Int? = null,
    val fecha: String = "",
    val valida_hasta: String = "",
    val estado: String = "",
)

@Serializable
data class ConsentimientoApp(
    val id: String,
    val procedimiento: String = "",
    val estado: String = "",
    val firmado_at: String? = null,
)

/** Cita de un procedimiento indicado (`indicados[].citas`). */
@Serializable
data class CitaIndicadaApp(
    val id: String,
    val fecha: String = "",
    val hora: String? = null,
    val estado: String = "",
)

/** Consentimiento de un procedimiento indicado (`indicados[].consentimientos_informados`). */
@Serializable
data class ConsentimientoIndicadoApp(
    val id: String,
    val estado: String = "",
    val procedimiento: String? = null,
)

@Serializable
data class ProcedimientoIndicadoApp(
    val id: String,
    val procedimiento_id: String? = null,
    val estado: String = "",
    val estado_pago: String? = null,
    val precio_acordado: Double? = null,
    val procedimiento: NombreRef? = null,
    val citas: List<CitaIndicadaApp> = emptyList(),
    val consentimientos_informados: List<ConsentimientoIndicadoApp> = emptyList(),
)

@Serializable
data class PlantillaServicioApp(val id: String, val activo: Boolean = true)

@Serializable
data class ServicioPlan(
    val id: String,
    val nombre: String = "",
    val precio: Double? = null,
    val modo_cobro: String? = null,
    val precio_unitario_sugerido: Double? = null,
    val especialidad_id: String? = null,
    val tipo_cita: String? = null,
    val categoria: String? = null,
    val plantillas: List<PlantillaServicioApp> = emptyList(),
)

@Serializable
data class EspecialidadProfesionalApp(val id: String, val rubro: String? = null)

@Serializable
data class ProfesionalPlan(
    val id: String,
    val nombre: String = "",
    val cmp: String? = null,
    val estado: String = "",
    /**
     * Puede figurar como prescriptor de una receta: lo calcula el SERVIDOR con la
     * regla de la web (activo + colegiatura + especialidad que receta según el
     * mapa de la clínica). La app no lo recalcula.
     */
    val puedePrescribir: Boolean = false,
    val especialidades: List<EspecialidadProfesionalApp> = emptyList(),
)

@Serializable
data class FraseFrecuenteApp(val campo: String, val texto: String, val usos: Int = 0)

@Serializable
data class FlagsConsulta(
    val dental: Boolean = false,
    val esProcedimiento: Boolean = false,
    val recetasAplica: Boolean = false,
    val especialidadId: String? = null,
    val puedeAtender: Boolean = false,
    val soloLectura: Boolean = true,
    val completada: Boolean = false,
    val cobrable: Boolean = false,
    val edad: Int? = null,
    val numeroHc: String = "",
    val estadoCola: String = "por_llegar",
    val pasos: List<PasoAtencion> = emptyList(),
    val faltantesFiliacion: List<String> = emptyList(),
    val faltantesAtencion: List<String> = emptyList(),
    val avisosCierre: List<String> = emptyList(),
    val consentimientosEmitidos: Int = 0,
)

@Serializable
data class ModulosConsulta(
    val camposTriaje: List<String> = emptyList(),
    val recetasOptIn: Boolean = false,
)

/**
 * Triaje que OTRA cita del paciente registró hoy (campo opcional `triajeDeHoy` de
 * GET consulta: solo si esta cita no tiene triaje propio, es de hoy y no está
 * Completada/Cancelada). Es PRESTADO: no se aplica solo. El médico toca "Usar
 * triaje de hoy" y recién entonces se copia al borrador (y se guarda con él).
 * [vitales]: columna → número (o texto numérico). [motivo]: el del triaje.
 */
@Serializable
data class TriajeDeHoyApp(
    val citaId: String? = null,
    val hora: String? = null,
    val registradoPor: String? = null,
    val motivo: String? = null,
    val vitales: JsonObject? = null,
)

/** Vitales que se aceptan del triaje de hoy (los 8 de norma + el perímetro). */
private val CLAVES_VITALES_TRIAJE = listOf(
    "presion_sistolica", "presion_diastolica", "frecuencia_cardiaca",
    "frecuencia_respiratoria", "temperatura", "saturacion_o2", "peso", "talla",
    "perimetro_abdominal",
)

/**
 * Los vitales del triaje de hoy como texto editable ("70", "36.5"), solo los que
 * traen un número. Un valor no numérico o vacío se ignora.
 */
fun vitalesDeTriajeHoy(t: TriajeDeHoyApp?): Map<String, String> {
    val v = t?.vitales ?: return emptyMap()
    return CLAVES_VITALES_TRIAJE.mapNotNull { k ->
        val n = (v[k] as? JsonPrimitive)?.content?.trim()?.replace(',', '.')?.toDoubleOrNull() ?: return@mapNotNull null
        k to (if (n % 1.0 == 0.0 && kotlin.math.abs(n) < 1e15) n.toLong().toString() else n.toString())
    }.toMap()
}

/** "09:15" desde "09:15:00" o un ISO "2026-10-03T09:15:00". null si no vino. */
fun horaTriajeHoy(t: TriajeDeHoyApp): String? =
    t.hora?.trim()?.takeIf { it.isNotEmpty() }?.let { h -> if ('T' in h) h.substringAfter('T') else h }?.take(5)

/** " (09:15 · Lic. Ana Ruiz)" o "" si no vino ni hora ni quién. */
private fun detalleTriajeHoy(t: TriajeDeHoyApp): String {
    val detalle = listOfNotNull(horaTriajeHoy(t), t.registradoPor?.trim()?.takeIf { it.isNotEmpty() })
    return if (detalle.isNotEmpty()) " (${detalle.joinToString(" · ")})" else ""
}

/** Aviso de origen: "Del triaje de hoy (09:15 · Lic. Ana Ruiz)". */
fun avisoTriajeHoy(t: TriajeDeHoyApp): String = "Del triaje de hoy" + detalleTriajeHoy(t)

/** Botón: "Usar triaje de hoy (09:15 · Lic. Ana Ruiz)". */
fun botonUsarTriajeHoy(t: TriajeDeHoyApp): String = "Usar triaje de hoy" + detalleTriajeHoy(t)

/**
 * Copia el triaje prestado al borrador (solo cuando el médico tocó el botón),
 * SOLO en los campos vacíos: lo que el médico ya escribió no se pisa. La presión
 * va solo si vienen las DOS cifras y las dos están vacías aquí: media presión
 * haría rechazar el guardado ("Presión incompleta") y no se bloquea la consulta
 * por algo prestado. El motivo se copia solo si el de la consulta está vacío.
 */
fun aplicarTriajeDeHoy(b: BorradorAtencion, t: TriajeDeHoyApp): BorradorAtencion {
    val v = vitalesDeTriajeHoy(t).filterKeys { b.vitales[it].isNullOrBlank() }.toMutableMap()
    if (v["presion_sistolica"] == null || v["presion_diastolica"] == null) {
        v.remove("presion_sistolica"); v.remove("presion_diastolica")
    }
    val motivo = t.motivo?.trim()?.takeIf { it.isNotEmpty() }
    val textos = if (motivo != null && b.textos["motivo_consulta"].isNullOrBlank())
        b.textos + ("motivo_consulta" to motivo) else b.textos
    return b.copy(vitales = b.vitales + v, textos = textos)
}

/**
 * El triaje de hoy SOLO aplica si esta cita no tiene vitales propios y el de hoy
 * trae al menos uno. Así nunca pisa lo que ya se midió en esta consulta.
 */
fun triajeDeHoyAplicable(d: DatosConsultaApp): TriajeDeHoyApp? {
    val t = d.triajeDeHoy ?: return null
    val a = d.atencion
    val propios = listOf(
        a?.presion_sistolica, a?.presion_diastolica, a?.frecuencia_cardiaca, a?.frecuencia_respiratoria,
        a?.temperatura, a?.saturacion_o2, a?.peso, a?.talla, a?.perimetro_abdominal,
    )
    if (propios.any { it != null }) return null
    return t.takeIf { vitalesDeTriajeHoy(it).isNotEmpty() }
}

/** Respuesta completa de `GET /api/staff/atencion/consulta?cita=<id>`. */
@Serializable
data class DatosConsultaApp(
    val ok: Boolean = true,
    val cita: CitaConsultaApp,
    val atencion: AtencionClinicaApp? = null,
    val recetas: List<RecetaBreve> = emptyList(),
    val consentimientos: List<ConsentimientoApp> = emptyList(),
    val indicados: List<ProcedimientoIndicadoApp> = emptyList(),
    val servicios: List<ServicioPlan> = emptyList(),
    val profesionales: List<ProfesionalPlan> = emptyList(),
    val frases: List<FraseFrecuenteApp> = emptyList(),
    val motivoSugerido: String = "",
    val diagnosticosSugeridos: List<DiagnosticoCie> = emptyList(),
    val flags: FlagsConsulta = FlagsConsulta(),
    val modulos: ModulosConsulta = ModulosConsulta(),
    /** Triaje de otra cita de hoy (opcional; ausente en servidores viejos). */
    val triajeDeHoy: TriajeDeHoyApp? = null,
)

/**
 * Estado editable de la atención en pantalla (lo que se manda a `guardar`).
 * `guardar` REEMPLAZA todo: siempre se manda el borrador completo.
 */
data class BorradorAtencion(
    val terapeutaId: String? = null,
    val tratamientoId: String? = null,
    /** motivo_consulta, tiempo_enfermedad, relato, funciones_biologicas, examen_fisico, plan_trabajo, tratamiento, observaciones, nota_procedimiento */
    val textos: Map<String, String> = emptyMap(),
    /** presion_sistolica, presion_diastolica, frecuencia_cardiaca, frecuencia_respiratoria, temperatura, saturacion_o2, peso, talla, perimetro_abdominal (texto tal cual se escribe) */
    val vitales: Map<String, String> = emptyMap(),
    val diagnosticos: List<DiagnosticoCie> = emptyList(),
    val examenes: List<ExamenSolicitado> = emptyList(),
)

// ── Reglas puras (gemelas de lib/atencion-medica.ts) ─────────────────────────

/** Pasos según el tipo de atención: una consulta o la cita de un procedimiento. */
fun pasosDeAtencion(esProcedimiento: Boolean, dental: Boolean): List<PasoAtencion> {
    if (esProcedimiento) {
        return listOf(
            PasoAtencion("procedimiento", "Consentimiento y procedimiento"),
            PasoAtencion("vitales", "Funciones vitales"),
            PasoAtencion("diagnostico", "Diagnóstico"),
            PasoAtencion("plan", "Indicaciones y control"),
            PasoAtencion("cierre", "Cierre"),
        )
    }
    return listOf(
        PasoAtencion("motivo", "Motivo y anamnesis"),
        PasoAtencion("vitales", if (dental) "Funciones vitales (opcional)" else "Funciones vitales"),
        PasoAtencion("examen", if (dental) "Examen estomatológico" else "Examen físico"),
        PasoAtencion("diagnostico", "Diagnóstico CIE-10"),
        PasoAtencion("plan", "Plan"),
        PasoAtencion("cierre", "Cierre"),
    )
}

/**
 * Avisos del cierre (no bloquean): lo que conviene resolver antes de terminar.
 * El consentimiento sin firmar es AVISO y no bloqueo (Ley 26842 art. 4).
 */
fun avisosCierre(
    esProcedimiento: Boolean,
    consentimientos: List<ConsentimientoApp>,
    requiereConsentimiento: Boolean,
    faltantesHc: List<String>,
): List<String> {
    val a = mutableListOf<String>()
    if (esProcedimiento && requiereConsentimiento) {
        val firmado = consentimientos.any { it.estado == "Firmado" }
        val rechazado = consentimientos.any { it.estado == "Rechazado" }
        if (rechazado && !firmado) {
            a.add("El paciente NO aceptó el procedimiento (consentimiento rechazado). No debería realizarse.")
        } else if (!firmado) {
            a.add("El consentimiento informado de este procedimiento no está registrado como firmado. Imprímelo y regístralo antes de realizarlo (Ley 26842, art. 15.4).")
        }
    }
    if (faltantesHc.isNotEmpty()) a.add("Para una HC completa (NTS 139) falta: ${faltantesHc.joinToString(" · ")}.")
    return a
}
