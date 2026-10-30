package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// ⚡ ATENCIÓN DIRECTA (psicología, ValienteMente 23/10/2026) — gemelo de
// `lib/modo-rapido.ts` (web). «Son muchos pasos para algo más directo… no
// necesitaría crear un tratamiento para poder ingresar las evoluciones».
//
// Donde el rubro lo soporta (hoy solo psicología) la clínica atiende así,
// SIEMPRE y sin ningún interruptor:
//  - Agendar: «Nueva cita» ofrece primero «Atención directa» (se guarda como
//    Consulta: sin tratamiento, se cobra como cita). Arranca en ella salvo que
//    el paciente tenga un tratamiento vigente por sesiones (ahí, Sesión). Si
//    quien agenda tocó el tipo, no se le cambia.
//  - Atender: «▶ Atender» en la cita SIN tratamiento abre UNA pantalla con la
//    evolución, el contexto del caso y «¿El paciente pagó?». Guardar la deja en
//    la historia psicológica (ligada a la cita), completa la cita y, si pagó, la
//    cobra en caja como cualquier consulta (POST /api/staff/cita/atender-rapido).
//  - Ya atendida: «📝 Anotar evolución» (resaltado si falta) o «📝 Evolución».
//
// Otro rubro (DALU fisio, RENOVA…): ninguna de estas funciones ofrece nada y
// la app se ve exactamente igual. Las reglas las valida el servidor otra vez.

/** Tipo con que se guarda la cita de atención directa (la Consulta se atiende y se cobra sola). */
const val TIPO_ATENCION_DIRECTA = "Consulta"

/** ¿Rige la atención directa? Solo por el rubro (hoy, psicología). `atencionRapidaAplica`. */
fun atencionDirectaAplica(esPsicologia: Boolean): Boolean = esPsicologia

/**
 * ¿La cita / el formulario es de psicología? Gemelo de `atencionEsPsicologia`
 * (lib/flujo.ts): la especialidad elegida manda; si no, las del profesional (todas
 * de psicología); si no, las de la clínica (todas de psicología). Psiquiatría no.
 * [especialidades]: (id, rubro, nombre) de las especialidades activas.
 */
fun atencionEsPsicologia(
    especialidades: List<Triple<String, String?, String?>>,
    especialidadId: String?,
    especialidadesProfesional: List<String> = emptyList(),
): Boolean {
    fun es(e: Triple<String, String?, String?>?) =
        e != null && e.second == "psicologia" && !e.third.orEmpty().contains("psiquiatr", ignoreCase = true)
    if (!especialidadId.isNullOrBlank()) return es(especialidades.firstOrNull { it.first == especialidadId })
    if (especialidadesProfesional.isNotEmpty()) return especialidadesProfesional.all { id -> es(especialidades.firstOrNull { it.first == id }) }
    return especialidades.isNotEmpty() && especialidades.all { es(it) }
}

/** ¿El tratamiento va por sesiones? (pago único y unidades no). `vaPorSesiones` de la web. */
fun vaPorSesiones(modalidad: String?): Boolean = modalidad != "Consulta" && modalidad != "Unidades"

/**
 * Los tipos de «Nueva cita» donde rige la atención directa: ella PRIMERO (aunque
 * el flujo no tenga Consulta: psicología entra por Evaluación o Sesión) y los
 * demás detrás. Donde no rige, la lista tal cual. `tiposConAtencionRapida`.
 */
fun <T> tiposConAtencionDirecta(tipos: List<T>, aplica: Boolean, atencion: T, valor: (T) -> String): List<T> =
    if (!aplica) tipos else listOf(atencion) + tipos.filter { valor(it) != TIPO_ATENCION_DIRECTA }

/**
 * Con qué tipo arranca «Nueva cita» donde rige la atención directa: con un
 * tratamiento VIGENTE (activo) por sesiones → "Sesión"; sin él (o sin paciente)
 * → la atención directa. Si no se ofrece Sesión, la atención directa.
 * Quien llama no lo aplica si la persona ya tocó el tipo o vino prefijado.
 */
fun tipoInicialAtencionDirecta(tiposVisibles: List<String>, modalidadesVigentes: List<String?>): String =
    if ("Sesión" in tiposVisibles && modalidadesVigentes.any(::vaPorSesiones)) "Sesión" else TIPO_ATENCION_DIRECTA

/** Lo que ofrece una cita sin tratamiento donde rige la atención directa. */
enum class OfertaAtencionDirecta {
    /** «▶ Atender»: evolución + completar + cobro opcional, en una pantalla. */
    ATENDER,
    /** Ya se atendió: «📝 Anotar evolución» / «📝 Evolución» (no vuelve a completar ni cobrar). */
    EVOLUCION,
}

private val TIPOS_ATENDIBLES = setOf("Consulta", "Sesión")
private val ESTADOS_EN_CURSO = setOf("Pendiente", "Confirmada")

/**
 * ¿Qué ofrece esta cita? `ofertaAtencionRapida` de la web. null = lo de siempre
 * (otro rubro, cita de un tratamiento, evaluación, cancelada, o quien mira no
 * puede escribir la historia). Quién: el profesional de la cita (o el vinculado
 * que toma una cita sin profesional) o el Admin, con `sesiones` + «Historia clínica».
 */
fun ofertaAtencionDirecta(
    esPsicologia: Boolean,
    tipo: String?, estado: String?, tratamientoId: String?, terapeutaId: String?, pacienteId: String?,
    rol: String?, puedeSesiones: Boolean, historiaClinica: Boolean, miTerapeutaId: String?,
    soloLectura: Boolean = false,
): OfertaAtencionDirecta? {
    if (!atencionDirectaAplica(esPsicologia)) return null
    if (pacienteId.isNullOrBlank() || !tratamientoId.isNullOrBlank()) return null
    if (tipo !in TIPOS_ATENDIBLES) return null
    if (soloLectura || !puedeSesiones || !historiaClinica) return null
    val suya = !miTerapeutaId.isNullOrBlank() && (terapeutaId == miTerapeutaId || terapeutaId.isNullOrBlank())
    if (rol != "Admin" && !suya) return null
    // El Admin sin vínculo no puede ser el autor de una cita sin profesional.
    if (terapeutaId.isNullOrBlank() && miTerapeutaId.isNullOrBlank()) return null
    return when {
        estado in ESTADOS_EN_CURSO -> OfertaAtencionDirecta.ATENDER
        estado == "Completada" -> OfertaAtencionDirecta.EVOLUCION
        else -> null
    }
}

/** El botón de la cita ya atendida. [resaltado] = falta la evolución (ámbar). */
data class BotonEvolucionCita(val texto: String, val resaltado: Boolean)

/**
 * `botonEvolucionCita` de la web: sin evolución → «📝 Anotar evolución»
 * resaltado; con ella, o sin saber todavía (null) → «📝 Evolución» discreto
 * (nunca se pide anotar lo que quizá ya está).
 */
fun botonEvolucionCita(oferta: OfertaAtencionDirecta?, tieneEvolucion: Boolean?): BotonEvolucionCita? {
    if (oferta != OfertaAtencionDirecta.EVOLUCION) return null
    return if (tieneEvolucion == false) BotonEvolucionCita("📝 Anotar evolución", resaltado = true)
    else BotonEvolucionCita("📝 Evolución", resaltado = false)
}

/** ¿Hay algo escrito? (una evolución ya guardada no puede quedar vacía). */
fun evolucionConContenido(e: EvolucionSesionPsico): Boolean =
    e.evolucion.isNotBlank() || e.estado.isNotBlank() || e.tareas.isNotBlank() || e.plan.isNotBlank()

/** Pasos del «¿El paciente pagó?» con los endpoints de siempre (`pasosCobroAtencionRapida`). */
data class PasosCobroAtencion(
    /** La cita no tenía precio: primero /api/staff/cita/ajustar-monto con este monto. */
    val fijarPrecio: Double?,
    /** Con precio y otro monto: el precio negociado de /api/staff/cita/cobrar. */
    val costoAcordado: Double?,
)

/** null = no se cobra como cita (una Sesión abona a su tratamiento) o monto inválido. */
fun pasosCobroAtencionDirecta(tipo: String?, costoCita: Double?, monto: Double): PasosCobroAtencion? {
    if (tipo == "Sesión" || !monto.isFinite() || monto <= 0) return null
    val costo = costoCita ?: 0.0
    if (costo <= 0) return PasosCobroAtencion(fijarPrecio = monto, costoAcordado = null)
    val igual = kotlin.math.round(costo * 100) == kotlin.math.round(monto * 100)
    return PasosCobroAtencion(fijarPrecio = null, costoAcordado = if (igual) null else monto)
}

/**
 * ¿Ofrecer «💵 ¿El paciente pagó?»? Solo al atender (la ya completada se cobra con
 * 💰), nunca en una Sesión, sin cobrar todavía y con permiso de pagos.
 */
fun ofrecePagoAtencion(atender: Boolean, tipo: String?, pagada: Boolean, puedePagos: Boolean, soloLectura: Boolean): Boolean =
    atender && !pagada && tipo != "Sesión" && puedePagos && !soloLectura

/**
 * Lo que impide guardar (el texto ámbar junto al botón), o null. [montoError]:
 * el error de lectura del monto (null = se leyó); [montoValido]: > 0.
 */
fun bloqueoAtencionDirecta(
    soloLectura: Boolean, atender: Boolean, conTexto: Boolean, yaTeniaEvolucion: Boolean,
    pago: Boolean, montoValido: Boolean, montoError: String?,
): String? = when {
    soloLectura -> "Modo lectura"
    !atender && !conTexto -> "Escribe la evolución"
    yaTeniaEvolucion && !conTexto -> "La evolución no puede quedar vacía"
    pago && !montoValido -> montoError ?: "Indica cuánto pagó"
    else -> null
}

/**
 * El botón dice lo que pasa: ya atendida → «Guardar evolución»; con cobro →
 * «Guardar / Completar y cobrar S/ X»; con texto → «Guardar y completar»; sin
 * nada escrito → «✓ Completar sin evolución». [montoTexto] = "S/ 80" o null.
 */
fun textoBotonAtencionDirecta(atender: Boolean, conTexto: Boolean, montoTexto: String?): String = when {
    !atender -> "Guardar evolución"
    montoTexto != null -> "${if (conTexto) "Guardar" else "Completar"} y cobrar $montoTexto"
    conTexto -> "Guardar y completar"
    else -> "✓ Completar sin evolución"
}

/** Título de la pantalla: «▶ Atender · Ana» / «📝 Evolución · Ana» / «📝 Anotar evolución · Ana». */
fun tituloAtencionDirecta(atender: Boolean, yaTeniaEvolucion: Boolean, nombre: String?): String {
    val n = nombre?.takeIf { it.isNotBlank() } ?: "Paciente"
    return when {
        atender -> "▶ Atender · $n"
        yaTeniaEvolucion -> "📝 Evolución · $n"
        else -> "📝 Anotar evolución · $n"
    }
}

/** El aviso tras guardar sin cobro (con cobro lo dice el cobro). */
fun avisoGuardadoAtencionDirecta(atender: Boolean, conTexto: Boolean): String = when {
    !atender -> "Evolución guardada"
    conTexto -> "Listo: evolución guardada y cita completada"
    else -> "Cita completada. La evolución, cuando quieras: «📝 Anotar evolución»."
}

/** Un servicio del catálogo, lo mínimo para sugerir el precio de la atención directa. */
data class ServicioPrecioAtencion(
    val id: String,
    val precio: Double,
    val especialidadId: String?,
    /** `modo_cobro` del servicio ('sesiones' / 'simple' / 'unidades' / null). */
    val modoCobro: String?,
    /** `especialidad.usa_sesiones` (decide si el servicio no fija modo). */
    val usaSesiones: Boolean,
    /** `tipo_cita`: el servicio ES el precio de un tipo de cita (Consulta/Evaluación). */
    val tipoCita: String?,
    val tipoClinico: String?,
)

/**
 * Precio sugerido cuando la clínica no tiene precio de Consulta: el de su servicio
 * por sesiones (la sesión de psicoterapia), de la especialidad de la cita si eligió
 * una. Nunca la evaluación psicológica. null = sin sugerencia. `precioSugeridoAtencionRapida`.
 */
fun precioSugeridoAtencionDirecta(servicios: List<ServicioPrecioAtencion>, especialidadId: String?): Double? {
    val candidatos = servicios.filter { s ->
        s.tipoCita.isNullOrBlank() && s.tipoClinico != TIPO_CLINICO_EVALUACION_PSICO &&
            (especialidadId.isNullOrBlank() || s.especialidadId == null || s.especialidadId == especialidadId) &&
            modoCobroEfectivoServicio(s.modoCobro, s.usaSesiones) == "sesiones" && s.precio > 0
    }
    val elegido = (if (!especialidadId.isNullOrBlank()) candidatos.firstOrNull { it.especialidadId == especialidadId } else null)
        ?: candidatos.firstOrNull()
    return elegido?.precio
}

/** `modoCobroEfectivo` de la web con los campos sueltos. */
fun modoCobroEfectivoServicio(modoCobro: String?, usaSesiones: Boolean): String =
    modoCobro?.trim()?.takeIf { it == "simple" || it == "sesiones" || it == "unidades" }
        ?: if (usaSesiones) "sesiones" else "simple"

// ── Lo que devuelve GET /api/staff/cita/atender-rapido ──

/** La cita tal como la manda el servidor para la pantalla. */
data class CitaAtencionDirecta(
    val id: String, val tipo: String?, val estado: String?, val fecha: String, val hora: String?,
    val costo: Double?, val pagada: Boolean, val pacienteId: String, val pacienteNombre: String?,
    val terapeutaId: String?, val especialidadId: String?, val tratamientoId: String?, val sedeId: String?,
)

data class DatosAtencionDirecta(
    val cita: CitaAtencionDirecta,
    /** La evolución ya guardada de ESTA cita (null = aún no tiene). */
    val evolucion: EvolucionSesionPsico?,
    /** La última evolución anterior del paciente: (fecha, evolución). */
    val anterior: Pair<String, EvolucionSesionPsico>?,
    val soloLectura: Boolean,
    /**
     * PRIMERA CONSULTA (29/10/2026, gemelo de la web): el paciente todavía no
     * tiene historia psicológica → se pide la historia inicial. Servidor viejo
     * (sin `modo`) = evolución, como siempre.
     */
    val modo: String = "evolucion",
    /** La historia inicial ya guardada de ESTA cita (clave de [CAMPOS_PSICO_INICIAL] → texto). */
    val inicial: Map<String, String>? = null,
    /** El «Motivo» de la ficha, para arrancar escrito. */
    val motivoPaciente: String? = null,
) {
    /** Se atiende ahora (completa la cita) o ya se atendió (solo la evolución). */
    val atender: Boolean get() = cita.estado != "Completada"
    val esInicial: Boolean get() = modo == "inicial"
}

/** Los campos de la HISTORIA INICIAL: (clave, etiqueta, ayuda). Gemelo de ETIQUETAS_PSICO_INICIAL (web). */
val CAMPOS_PSICO_INICIAL: List<Triple<String, String, String>> = listOf(
    Triple("motivo_consulta", "Motivo de consulta", "Por qué viene, en sus palabras…"),
    Triple("relato", "Historia del problema", "Desde cuándo, cómo empezó, qué lo empeora o alivia…"),
    Triple("observaciones", "Antecedentes personales y familiares", "Familia, salud, estudios o trabajo… (opcional)"),
    Triple("examen_mental", "Estado emocional / examen mental", "Cómo se presenta: ánimo, discurso, riesgo… (opcional)"),
    Triple("plan_trabajo", "Plan terapéutico", "¿Sesiones? ¿Evaluación? ¿Derivación? (opcional)"),
    Triple("tratamiento", "Tareas e indicaciones", "Lo que se lleva para casa (opcional)"),
)

/** «¿Qué sigue?» al cerrar la primera consulta: (valor, texto). */
val SIGUIENTES_PRIMERA_CONSULTA: List<Pair<String, String>> = listOf(
    "terapia" to "🏃 Terapia (sesiones)",
    "evaluacion" to "🧠 Evaluación con informe",
    "derivar" to "↗️ Derivar",
    "solo" to "✅ Solo esta consulta",
)

/** Qué pasa al guardar con esa elección (quien cobra arma el paquete; si no, recepción). */
fun ayudaSiguiente(valor: String, cobra: Boolean): String = when (valor) {
    "terapia", "evaluacion" -> if (cobra)
        "Al guardar se abre ${if (valor == "terapia") "el paquete de terapia" else "el paquete de la evaluación"}, listo para cobrar y agendar."
    else "Al guardar le dejas el pedido a recepción: lo cobra y agenda las citas."
    "derivar" -> "Anota a quién y por qué en «Plan terapéutico». El documento de derivación se emite desde su ficha."
    else -> "Queda registrada la consulta, sin más pasos."
}

/** La decisión queda escrita en la historia inicial: «Siguiente paso: …» al inicio del plan. */
fun planConSiguiente(plan: String, valor: String?): String {
    val nombre = SIGUIENTES_PRIMERA_CONSULTA.firstOrNull { it.first == valor }?.second?.substringAfter(' ') ?: return plan
    if (plan.startsWith("Siguiente paso:")) return plan
    return "Siguiente paso: $nombre." + if (plan.isNotBlank()) "\n${plan.trim()}" else ""
}

private fun JsonObject.txt(k: String): String? =
    (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }

private fun evolucionDe(o: JsonObject?): EvolucionSesionPsico? = o?.let {
    EvolucionSesionPsico(it.txt("evolucion").orEmpty(), it.txt("estado").orEmpty(), it.txt("tareas").orEmpty(), it.txt("plan").orEmpty())
}

/** El JSON del GET → [DatosAtencionDirecta]; null = ilegible. */
fun parsearAtencionDirecta(raiz: JsonObject?): DatosAtencionDirecta? {
    val c = raiz?.get("cita") as? JsonObject ?: return null
    val pac = c["paciente"] as? JsonObject
    val id = c.txt("id") ?: return null
    val pacienteId = pac?.txt("id") ?: return null
    val ant = raiz["anterior"] as? JsonObject
    val antEvo = evolucionDe(ant?.get("evolucion") as? JsonObject)
    return DatosAtencionDirecta(
        cita = CitaAtencionDirecta(
            id = id, tipo = c.txt("tipo"), estado = c.txt("estado"), fecha = c.txt("fecha").orEmpty(),
            hora = c.txt("hora"), costo = c.txt("costo")?.toDoubleOrNull(),
            pagada = c.txt("pagada") == "true", pacienteId = pacienteId, pacienteNombre = pac.txt("nombre"),
            terapeutaId = c.txt("terapeuta_id"), especialidadId = c.txt("especialidad_id"),
            tratamientoId = c.txt("tratamiento_id"), sedeId = c.txt("sede_id"),
        ),
        evolucion = evolucionDe(raiz["evolucion"] as? JsonObject),
        anterior = if (ant != null && antEvo != null) (ant.txt("fecha").orEmpty() to antEvo) else null,
        soloLectura = raiz.txt("soloLectura") == "true",
        modo = raiz.txt("modo") ?: "evolucion",
        inicial = (raiz["inicial"] as? JsonObject)?.let { o -> CAMPOS_PSICO_INICIAL.associate { (k, _, _) -> k to o.txt(k).orEmpty() } },
        motivoPaciente = raiz.txt("motivoPaciente"),
    )
}

/** La última evolución, campo por campo, sin los vacíos: (etiqueta, texto). */
fun camposEvolucionAnterior(e: EvolucionSesionPsico): List<Pair<String, String>> =
    listOf(
        "Temas trabajados y evolución" to e.evolucion,
        "Estado emocional / examen mental" to e.estado,
        "Tareas e indicaciones" to e.tareas,
        "Plan para la próxima sesión" to e.plan,
    ).filter { it.second.isNotBlank() }
