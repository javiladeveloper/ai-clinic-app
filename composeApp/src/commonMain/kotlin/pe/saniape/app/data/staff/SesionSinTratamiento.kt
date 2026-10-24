package pe.saniape.app.data.staff

/**
 * Nueva cita de tipo Sesión para un paciente SIN tratamiento activo — gemelo de
 * `lib/cita-sin-tratamiento.ts` (web, rama fix/sesion-paciente-sin-tratamiento;
 * contrato en docs/sesion-paciente-sin-tratamiento.md).
 *
 * Una sesión va siempre dentro de un tratamiento: antes la app dejaba guardar una
 * cita "Sesión" huérfana (sin tratamiento, el servidor no crea la sesión). Ahora,
 * en TODAS las clínicas, el formulario lo dice y ofrece crearlo ahí mismo (queda
 * elegido y la cita sale como su Sesión #1), o a quién pedírselo. El alta rápida
 * del paciente en una Sesión, en cambio, SOLO en las clínicas que entran por
 * Sesión (psicología): con Consulta/Evaluación el paciente nuevo entra por ahí.
 */

/**
 * ¿La clínica (o la especialidad de la cita) entra SOLO por Sesión? Entre los
 * tipos que el formulario ofrece está Sesión y no hay Consulta ni Evaluación.
 */
fun entraSoloPorSesion(tiposVisibles: List<String>): Boolean =
    "Sesión" in tiposVisibles && "Consulta" !in tiposVisibles && "Evaluación" !in tiposVisibles

/** Qué se muestra bajo el paciente de una Sesión cuando no tiene tratamiento activo. */
enum class AvisoSinTratamiento {
    /** Nada que avisar (tiene tratamiento, no es Sesión, viene con uno prefijado, aún cargando…). */
    NINGUNO,

    /** "Este paciente aún no tiene tratamiento" + "＋ Crear tratamiento". */
    OFRECER_CREAR,

    /** Sin permiso de tratamientos: "pídeselo a su profesional o al Admin". */
    PEDIR_A_QUIEN_PUEDE,
}

/**
 * El aviso para el paciente elegido. [tratamientosCargados]: la lista de SUS
 * tratamientos llegó bien (si la consulta falló no se afirma que no tenga).
 * [tratamientoPrefijado]: la cita vino con su tratamiento (agendar tras crear uno).
 * [puedeSesiones]: el permiso con el que se gestionan tratamientos y sesiones.
 */
fun avisoSinTratamiento(
    tipo: String,
    pacienteElegido: Boolean,
    tratamientosCargados: Boolean,
    hayTratamientos: Boolean,
    tratamientoPrefijado: Boolean,
    puedeSesiones: Boolean,
): AvisoSinTratamiento = when {
    tipo != "Sesión" || !pacienteElegido || !tratamientosCargados || hayTratamientos || tratamientoPrefijado ->
        AvisoSinTratamiento.NINGUNO
    puedeSesiones -> AvisoSinTratamiento.OFRECER_CREAR
    else -> AvisoSinTratamiento.PEDIR_A_QUIEN_PUEDE
}

/** Título del aviso ([termino] = "paciente", "cliente"… según la clínica). */
fun tituloSinTratamiento(aviso: AvisoSinTratamiento, termino: String): String = when (aviso) {
    AvisoSinTratamiento.PEDIR_A_QUIEN_PUEDE -> "Este $termino necesita un tratamiento"
    else -> "Este $termino aún no tiene tratamiento"
}

/**
 * El detalle del aviso. [entrada]: el nombre de la otra puerta de la clínica
 * (su Consulta/Evaluación, con el nombre del flujo), o null si solo entra por Sesión.
 */
fun detalleSinTratamiento(aviso: AvisoSinTratamiento, entrada: String?, atencionDirecta: Boolean = false): String = when (aviso) {
    AvisoSinTratamiento.OFRECER_CREAR ->
        "Una sesión va siempre dentro de un tratamiento. Créalo aquí: esta cita queda como su Sesión #1, " +
            "con la fecha, hora y profesional que ya elegiste." +
            (entrada?.let {
                // Psicología: lo que casi siempre se busca es atender sin paquete.
                if (atencionDirecta) " Para atender sin tratamiento ni paquete, elige $it arriba."
                else " Si viene por algo nuevo, elige $it arriba."
            } ?: "")
    AvisoSinTratamiento.PEDIR_A_QUIEN_PUEDE ->
        "Pídeselo a su profesional o al Admin: una sesión va siempre dentro de un tratamiento." +
            (entrada?.let { " Si viene por algo nuevo, agéndale una $it." } ?: "")
    AvisoSinTratamiento.NINGUNO -> ""
}

/** Lo que dice "Guardar" si se intenta agendar la Sesión sin tratamiento (null = puede). */
fun mensajeFaltaTratamiento(aviso: AvisoSinTratamiento, termino: String): String? = when (aviso) {
    AvisoSinTratamiento.OFRECER_CREAR -> "Crea el tratamiento del $termino para agendar la sesión."
    AvisoSinTratamiento.PEDIR_A_QUIEN_PUEDE ->
        "El $termino necesita un tratamiento: pídeselo a su profesional o al Admin."
    AvisoSinTratamiento.NINGUNO -> null
}

/** Modo de cobro efectivo: el del servicio; si no fija uno válido, el de su especialidad. */
fun modoCobroServicio(p: ProcedimientoRef): String =
    p.modoCobro?.trim()?.takeIf { it == "simple" || it == "sesiones" || it == "unidades" }
        ?: if (p.usaSesiones) "sesiones" else "simple"

/**
 * Servicios que se ofrecen al crear el tratamiento DESDE una cita de Sesión:
 * TODOS los activos —por sesiones, pago único y por unidades— (dueño, 24/10/2026,
 * Centro OMER: «si yo vengo específicamente por un tratamiento, el que quiero, no
 * necesito evaluación»; antes solo salían los de sesiones). De la especialidad de
 * la cita si eligió una (los servicios sin especialidad sirven siempre). La lista
 * que llega ya es de servicios activos (`PacientesRepo.procedimientos`).
 * `serviciosParaCita` de la web; cómo queda la cita en cada modo: [costoPrimeraSesion].
 */
fun serviciosParaCita(servicios: List<ProcedimientoRef>, especialidadId: String?): List<ProcedimientoRef> =
    servicios.filter { s ->
        especialidadId.isNullOrBlank() || s.especialidadId == null || s.especialidadId == especialidadId
    }

/**
 * Costo de la PRIMERA atención de un tratamiento recién creado desde la cita (la
 * cita, tipo Sesión, es esa atención). `costoPrimeraSesion` de la web. El dinero
 * vive en el tratamiento; el costo de la cita solo dice qué vale ESA atención:
 *
 * - Paquete → null: la sesión no se cobra suelta.
 * - Sesión suelta → su precio por sesión.
 * - Pago único (modalidad "Consulta") → el precio acordado: es UN cobro por el
 *   tratamiento y lo lleva esta primera atención. Las siguientes del mismo
 *   tratamiento se agendan sin costo (solo la del creado aquí lleva precio).
 * - Unidades → null: como la intervención agendada desde la ficha; se cobra el
 *   acordado del tratamiento.
 */
fun costoPrimeraSesion(modalidad: String?, precioPorSesion: Double?, precioAcordado: Double? = null): Double? = when (modalidad) {
    "Sesión suelta" -> precioPorSesion
    "Consulta" -> precioAcordado?.takeIf { it > 0 }
    else -> null
}

/**
 * Cómo se nombra la cita del tratamiento recién creado (el aviso al guardarlo).
 * En un pago único o por unidades no hay "Sesión #1": es su atención.
 * `nombreCitaDelTratamiento` de la web.
 */
fun nombreCitaDelTratamiento(modalidad: String?): String = when (modalidad) {
    "Consulta" -> "su primera atención"
    "Unidades" -> "su intervención"
    else -> "su Sesión #1"
}

/**
 * ¿Se le puede agendar una Sesión a este tratamiento? Los activos, y además el
 * PAGO ÚNICO ya realizado y sin alta (modalidad "Consulta" en "Completado"):
 * «quizás necesite 2 o con 1 basta, es dinámico» (dueño, 24/10/2026). Completar
 * su primera atención lo deja "Realizado" pero NO cerrado —se cierra con «Dar de
 * alta»—, y la otra atención va sin volver a cobrar. Un paquete completado no:
 * ese se amplía. `tratamientoAgendableEnSesion` de la web.
 */
fun tratamientoAgendableEnSesion(estado: String?, modalidad: String?): Boolean =
    estado == "Activo" || (estado == "Completado" && modalidad == "Consulta")

/**
 * Tras crear el tratamiento desde la cita: cuál queda elegido. El del id que
 * devolvió el servidor; sin id (respuesta vieja), el único que haya.
 */
fun tratamientoCreadoEnLista(lista: List<TratamientoRef>, idCreado: String?): TratamientoRef? =
    idCreado?.let { id -> lista.firstOrNull { it.id == id } } ?: lista.singleOrNull()

// ─────────────────────────────────────────────────────────────────────────────
// «＋ Ampliar tratamiento anterior» desde «Nueva cita» (QA OMER + dueño, 23/10/2026)
// Gemelo de `lib/ampliar-desde-cita.ts` (web).
//
// La profesional atiende «por sesión», una a la vez: cada tratamiento termina con
// su sesión (queda Completado) y al agendarle la siguiente Sesión el paciente sale
// «sin tratamiento». Ampliar el último es un toque, junto a «＋ Crear tratamiento».
// La regla es la del «➕ Ampliar» de la ficha: por sesiones, Completado (sin alta),
// y no una evaluación psicológica. El ampliar en sí es el de siempre
// (`/api/staff/tratamiento/accion` · ampliar), sin reglas nuevas.
// ─────────────────────────────────────────────────────────────────────────────

/** Un tratamiento terminado candidato a ampliarse desde la cita. */
data class TratAmpliable(
    val id: String,
    val estado: String?,
    val modalidad: String?,
    val procedimientoId: String?,
    val servicio: String?,
    /** El precio del servicio en el catálogo (la lista de un paquete). */
    val precioServicio: Double?,
    val precioPorSesion: Double?,
    val totalSesiones: Int?,
    val terapeutaId: String?,
    val especialidadId: String?,
    val diagnostico: String?,
    val fechaInicio: String?,
    val creadoAt: String?,
    /** `especialidad.usa_sesiones` del servicio (solo decide si no trae modalidad). */
    val usaSesionesServicio: Boolean? = null,
    /** Fecha de su última sesión completada (null = ninguna). */
    val ultimaSesion: String? = null,
    /** `procedimiento.tipo_clinico` (la evaluación psicológica no se amplía así). */
    val tipoClinico: String? = null,
)

/** `tratamientoUsaSesiones` de la web: la modalidad manda; sin ella, la especialidad. */
fun tratamientoUsaSesiones(modalidad: String?, usaSesionesServicio: Boolean?): Boolean = when (modalidad) {
    "Consulta", "Unidades" -> false
    "Paquete", "Sesión suelta" -> true
    else -> usaSesionesServicio != false
}

/** ¿Se puede ampliar desde la cita? Completado (sin alta), por sesiones, no evaluación psicológica. */
fun esAmpliableDesdeCita(t: TratAmpliable, evalPsico: Set<String> = emptySet()): Boolean {
    if (t.estado != "Completado") return false
    if (t.tipoClinico == TIPO_CLINICO_EVALUACION_PSICO) return false
    if (t.procedimientoId != null && t.procedimientoId in evalPsico) return false
    return tratamientoUsaSesiones(t.modalidad, t.usaSesionesServicio)
}

/** Los ampliables, el MÁS RECIENTE primero: por su última sesión; sin sesiones, por inicio y alta. */
fun tratamientosAmpliables(trats: List<TratAmpliable>, evalPsico: Set<String> = emptySet()): List<TratAmpliable> {
    fun clave(t: TratAmpliable) = listOf(t.ultimaSesion.orEmpty(), t.fechaInicio.orEmpty(), t.creadoAt.orEmpty())
    return trats.filter { esAmpliableDesdeCita(it, evalPsico) }.sortedWith { a, b ->
        val ka = clave(a); val kb = clave(b)
        ka.indices.firstOrNull { ka[it] != kb[it] }?.let { i -> -ka[i].compareTo(kb[i]) } ?: 0
    }
}

/**
 * ¿Se muestra «＋ Ampliar tratamiento anterior»? Solo donde sale «＋ Crear
 * tratamiento» (Sesión, paciente sin tratamiento vigente, permiso `sesiones`),
 * con el paciente activo y al menos un ampliable. Los permisos del «➕ Ampliar» de la ficha.
 */
fun ofreceAmpliarAnterior(aviso: AvisoSinTratamiento, pacienteInactivo: Boolean, ampliables: Int): Boolean =
    aviso == AvisoSinTratamiento.OFRECER_CREAR && !pacienteInactivo && ampliables > 0

private val MESES_CORTOS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "set", "oct", "nov", "dic")

/** «Psicoterapia — última sesión 12 oct» (sin sesiones: «— terminado»). */
fun textoTratamientoAmpliable(servicio: String?, ultimaSesion: String?): String {
    val nombre = servicio?.trim()?.takeIf { it.isNotEmpty() } ?: "Tratamiento"
    val m = ultimaSesion?.let { Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(it) }
    val fecha = m?.let { r ->
        val mes = r.groupValues[2].toInt()
        "${r.groupValues[3].toInt()} ${MESES_CORTOS.getOrElse(mes - 1) { "" }}".trim()
    }
    return if (fecha != null) "$nombre — última sesión $fecha" else "$nombre — terminado"
}

/**
 * `precioListaAmpliacion` de la web: +N sesiones valen N × el precio de una sesión
 * (el pactado en una suelta; el del catálogo en un paquete). Unidades / pago único: null.
 * [otraMoneda]: el catálogo está en la moneda de la clínica → solo vale el de la suelta.
 */
fun precioListaAmpliacion(t: TratAmpliable, n: Int, otraMoneda: Boolean = false): Double? {
    if (n <= 0 || (t.modalidad != "Paquete" && t.modalidad != "Sesión suelta")) return null
    val deSuelta = if (t.modalidad == "Sesión suelta") t.precioPorSesion else null
    val unit = deSuelta?.takeIf { it > 0 } ?: (if (otraMoneda) null else t.precioServicio)
    return unit?.takeIf { it > 0 }?.let { kotlin.math.round(it * n * 100) / 100 }
}

/** Lo que se manda al ampliar desde la cita, o el error a mostrar. */
sealed class PedidoAmpliacion {
    data class Ok(val sesiones: Int, val monto: Double) : PedidoAmpliacion()
    data class Error(val error: String) : PedidoAmpliacion()
}

/**
 * `pedidoAmpliacion` de la web. El monto VACÍO vale la lista de la ampliación (con
 * un toque, una ampliación a S/ 0 sería una cortesía que nadie decidió); sin lista,
 * 0. Un 0 escrito a mano es la cortesía, a propósito.
 */
fun pedidoAmpliacion(sesiones: String, monto: String, lista: Double?, moneda: String?): PedidoAmpliacion {
    val txt = sesiones.trim()
    val n = if (Regex("^\\d+$").matches(txt)) txt.toIntOrNull() else null
    if (n == null || n < 1) return PedidoAmpliacion.Error("Indica cuántas sesiones agregar (1 o más)")
    val l = leerPrecio(monto, moneda)
    if (!l.ok) return PedidoAmpliacion.Error("Monto: ${l.error}")
    return PedidoAmpliacion.Ok(n, l.valor ?: lista ?: 0.0)
}
