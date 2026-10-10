package pe.saniape.app.data.staff

/**
 * Reglas PURAS del formulario "Nuevo tratamiento" (ModalCrearTratamiento) que
 * tocan plantillas, modalidad y la primera sesión. Sin red ni Compose, para
 * poder testearlas. Gemelas de TratamientoForm.tsx (web): el tipo sale del
 * modo de cobro EFECTIVO del servicio y la modalidad nunca lo contradice.
 */

/**
 * RPC atómica del contador de usos (`usos = usos + 1`, parámetro `p_id`), de la
 * migración web 20261018100000_plantillas_permiso_y_uso. Si la base aún no la
 * tiene, la app cae al leer-y-escribir de antes.
 */
const val RPC_USO_PLANTILLA = "marcar_uso_plantilla"

/** Tipo del tratamiento que se crea (mismo criterio que `tipoDe` / modoEfectivo de la web). */
enum class TipoTratamientoNuevo { SESIONES, UNIDADES, SERVICIO_UNICO, CONSULTA }

/**
 * Modo de cobro EFECTIVO del servicio: el del procedimiento; si no fija uno, se
 * hereda de la especialidad (usa_sesiones=true → sesiones, false → simple).
 * 'simple' con precio > 0 es SERVICIO ÚNICO (blanqueamiento); con precio 0,
 * CONSULTA (medicina, nutrición). Antes la app decidía "consulta" por la
 * especialidad aunque el servicio fuera por sesiones (y al revés: un servicio
 * 'simple' gratis en una especialidad con sesiones nacía como Paquete).
 */
fun tipoTratamientoDe(p: ProcedimientoRef, precioSede: Double? = null): TipoTratamientoNuevo {
    val modo = p.modoCobro?.trim()?.takeIf { it == "simple" || it == "sesiones" || it == "unidades" }
        ?: if (p.usaSesiones) "sesiones" else "simple"
    return when (modo) {
        "unidades" -> TipoTratamientoNuevo.UNIDADES
        "simple" -> if ((precioSede ?: p.precio) > 0.0) TipoTratamientoNuevo.SERVICIO_UNICO else TipoTratamientoNuevo.CONSULTA
        else -> TipoTratamientoNuevo.SESIONES
    }
}

/** Modalidades que admite cada tipo (la primera es la de por defecto). */
fun modalidadesDe(tipo: TipoTratamientoNuevo): List<String> = when (tipo) {
    TipoTratamientoNuevo.SESIONES -> listOf("Paquete", "Sesión suelta")
    TipoTratamientoNuevo.UNIDADES -> listOf("Unidades")
    TipoTratamientoNuevo.SERVICIO_UNICO, TipoTratamientoNuevo.CONSULTA -> listOf("Consulta")
}

/**
 * La modalidad que se GUARDA: la elegida si el tipo la admite; si no, la de
 * por defecto del tipo. Así nunca nace un tratamiento con una modalidad
 * incompatible con el modo de cobro de su servicio (p. ej. una plantilla o un
 * "nuevo paquete" que traía "Unidades" sobre un servicio por sesiones).
 */
fun modalidadAGuardar(tipo: TipoTratamientoNuevo, elegida: String?): String {
    val validas = modalidadesDe(tipo)
    return elegida?.takeIf { it in validas } ?: validas.first()
}

/** Campos comerciales del formulario, como texto (lo que muestran los campos). */
data class CamposComerciales(
    val modalidad: String = "Paquete",
    val totalSesiones: String = "10",
    val precioPaquete: String = "",
    val precioPorSesion: String = "",
    val cantidadUnidades: String = "",
    val precioUnitario: String = "",
    /** Vacío = precio de lista (servicio único / unidades) o el base. */
    val precioAcordado: String = "",
)

/**
 * Lo que el servicio pone al elegirlo (gemelo del autollenado web): precio por
 * sesión, paquete del tarifario de 10 (o el primero), precio por unidad
 * sugerido. [precioSede] = precio propio de la sede (sede_procedimientos):
 * reemplaza al precio base del servicio. [modalidad] se conserva si el tipo la
 * admite (cambiar de servicio no deshace la elección Paquete/Suelta).
 */
fun camposDeServicio(p: ProcedimientoRef, precioSede: Double? = null, modalidad: String = "Paquete"): CamposComerciales {
    val precio = precioSede ?: p.precio
    val tar = p.tarifarios.firstOrNull { it.cantidadSesiones == 10 } ?: p.tarifarios.firstOrNull()
    return CamposComerciales(
        modalidad = modalidadAGuardar(tipoTratamientoDe(p, precioSede), modalidad),
        totalSesiones = tar?.cantidadSesiones?.toString() ?: "10",
        precioPaquete = tar?.precioTotal?.toString() ?: p.precioPaquete?.toString() ?: "",
        precioPorSesion = precio.toString(),
        cantidadUnidades = "",
        // Con precio de sede, el sugerido por unidad del servicio no aplica a esa sede.
        precioUnitario = (if (precioSede != null) precioSede else p.precioUnitarioSugerido ?: p.precio).toString(),
        // Servicio único: el acordado NO se prellena (vacío = se cobra el base).
        precioAcordado = "",
    )
}

/** Resultado de aplicar una plantilla sobre los campos del servicio. */
data class PlantillaAplicada(
    val campos: CamposComerciales,
    /** La plantilla traía precios y NO se copiaron (moneda o precio propio de la sede). */
    val preciosOmitidos: Boolean,
)

/**
 * Aplica una plantilla SOBRE los campos frescos del servicio ([base]): lo que la
 * plantilla no trae queda como lo pone el servicio (así cambiar de plantilla no
 * arrastra valores de la anterior). Gemelo de `estadoDesdePlantilla`
 * (lib/plantillas-tratamiento.ts, contrato en docs/plantillas-tratamiento.md):
 *  - la modalidad, solo si cuadra con el modo de cobro del servicio;
 *  - pago único: la plantilla guarda su precio en `precio_por_sesion` → va al
 *    precio ACORDADO (vacío = se cobra el precio base);
 *  - [mismaMoneda] = false (sede de otro país): sus precios —escritos en la
 *    moneda de la clínica— NO se copian; se usa el precio del servicio en esa
 *    sede ([precioSede]) o queda vacío para escribirlo. Nunca se mezclan monedas.
 *  - Con un N° de sesiones propio y sin precio de paquete que lo acompañe, el
 *    paquete sale del tarifario de ese tamaño o queda vacío (nunca el de 10 para 6).
 */
fun aplicarPlantilla(
    base: CamposComerciales,
    pl: PlantillaRef,
    tipo: TipoTratamientoNuevo,
    tarifarios: List<TarifarioRef>,
    mismaMoneda: Boolean,
    precioSede: Double? = null,
): PlantillaAplicada {
    val simple = tipo == TipoTratamientoNuevo.SERVICIO_UNICO || tipo == TipoTratamientoNuevo.CONSULTA
    val total = pl.totalSesiones?.takeIf { it > 0 }
    var c = base.copy(
        modalidad = modalidadAGuardar(tipo, pl.modalidad?.takeIf { it in modalidadesDe(tipo) } ?: base.modalidad),
        totalSesiones = total?.toString() ?: base.totalSesiones,
        cantidadUnidades = pl.cantidadUnidades?.takeIf { it > 0 }?.toString() ?: "",
        precioAcordado = "",
    )
    val traePrecios = pl.precioPaquete != null || pl.precioPorSesion != null || pl.precioUnitario != null
    if (mismaMoneda) {
        pl.precioPaquete?.let { c = c.copy(precioPaquete = it.toString()) }
        pl.precioUnitario?.let { c = c.copy(precioUnitario = it.toString()) }
        pl.precioPorSesion?.let { c = if (simple) c.copy(precioAcordado = it.toString()) else c.copy(precioPorSesion = it.toString()) }
        if (pl.precioPaquete == null && total != null && total.toString() != base.totalSesiones) {
            c = c.copy(precioPaquete = tarifarios.firstOrNull { it.cantidadSesiones == total }?.precioTotal?.toString() ?: "")
        }
    } else {
        // Los del servicio también están en la moneda de la clínica: solo el de la sede sirve.
        val sede = precioSede?.toString() ?: ""
        c = c.copy(
            precioPaquete = "",
            precioPorSesion = sede,
            precioUnitario = if (tipo == TipoTratamientoNuevo.UNIDADES) sede else "",
        )
    }
    return PlantillaAplicada(c, preciosOmitidos = !mismaMoneda && (traePrecios || precioSede == null))
}

/**
 * ¿La plantilla se cobra en la moneda en que está escrita? Una plantilla es de
 * la clínica: sus montos están en la moneda de la clínica. Si el tratamiento se
 * cobra en otra (la sede del paciente o la de destino son de otro país), no.
 */
fun mismaMonedaQueLaClinica(monedaClinica: String, monedasDestino: Collection<String>): Boolean =
    monedasDestino.all { normalizarMoneda(it) == normalizarMoneda(monedaClinica) }

/** Aviso cuando los precios de la plantilla no se copiaron (otra moneda). null = nada que avisar. */
fun avisoPreciosPlantilla(
    preciosOmitidos: Boolean, monedaClinica: String, monedaDestino: String, precioSede: Double?,
): String? {
    if (!preciosOmitidos) return null
    val base = normalizarMoneda(monedaClinica)
    val dest = normalizarMoneda(monedaDestino)
    return "Los precios de la plantilla están en $base y esta sede cobra en $dest: " +
        (if (precioSede != null) "se usó el precio del servicio en esta sede" else "escribe el precio en $dest") +
        ". Revísalo antes de crear."
}

/**
 * ¿Este profesional atiende este servicio? La MISMA regla que filtra los
 * servicios del formulario: sin especialidades o servicio sin especialidad → sí.
 */
fun profesionalAtiende(especialidadServicio: String?, especialidadesProfesional: List<String>): Boolean =
    especialidadServicio == null || especialidadesProfesional.isEmpty() || especialidadServicio in especialidadesProfesional

/**
 * Plantillas que se ofrecen en el selector: solo las que tienen servicio
 * (las copiadas de la biblioteca nacen sin él) y cuyo servicio está activo
 * (y no apagado en la sede de destino).
 */
fun plantillasOfrecibles(
    plantillas: List<PlantillaRef>,
    procedimientosActivos: List<ProcedimientoRef>,
    apagadosEnSede: Set<String> = emptySet(),
): List<PlantillaRef> {
    val activos = procedimientosActivos.mapTo(HashSet()) { it.id }
    return plantillas.filter { pl ->
        val pid = pl.procedimientoId?.takeIf { it.isNotBlank() }
        pid != null && pid in activos && pid !in apagadosEnSede
    }
}

/**
 * "Control en X días" de la plantilla → fecha (AAAA-MM-DD) desde [hoyIso] (hoy de
 * la sede). Si el servicio tiene PROTOCOLO de controles, las fechas las programa
 * él al crear: una puesta a mano lo adelantaría un control → null.
 */
fun proximoControlDePlantilla(controlDias: Int?, hoyIso: String, servicioConProtocolo: Boolean = false): String? =
    if (servicioConProtocolo) null else controlDias?.takeIf { it >= 0 }?.let { sumarDiasIso(hoyIso, it) }

/** Indicaciones de la plantilla → medicación del tratamiento (vacías = nada). */
fun medicacionDePlantilla(indicaciones: String?): String? = indicaciones?.trim()?.takeIf { it.isNotEmpty() }

// ── Primera sesión / cita ──────────────────────────────────────────────────

/**
 * Requisito del dueño: al crear un tratamiento SIEMPRE se ofrece agendar la
 * primera sesión. En la app la casilla "Agendarla ahora" arranca DESMARCADA
 * (nunca se crea una cita sin que alguien elija fecha y hora): si no se marca
 * —o el tipo no tiene esa casilla— se ofrece después de crear. null = no hay nada que ofrecer (ya se
 * agendó, o no hay id del tratamiento: quedó en la cola offline).
 */
fun ofrecerAgendarTrasCrear(tratamientoId: String?, primeraAgendada: Boolean): Boolean =
    !tratamientoId.isNullOrBlank() && !primeraAgendada

/**
 * Tipo de la cita que agenda la primera atención: SIEMPRE una Sesión del
 * tratamiento (gemelo de `ofertaAgendaTrasCrear` de la web): por sesiones, la
 * #1; por unidades, la intervención; pago único / consulta, "su cita" (sesión
 * gemela sin cobro propio: al completarla el servicio único queda realizado).
 */
@Suppress("UNUSED_PARAMETER")
fun tipoCitaPrimera(tipo: TipoTratamientoNuevo): String = "Sesión"

/** Texto del botón/diálogo de la oferta (gemelo de `etiquetaOfertaAgenda`). */
fun textoAgendarPrimera(tipo: TipoTratamientoNuevo): String = when (tipo) {
    TipoTratamientoNuevo.SESIONES -> "📅 Agendar la primera sesión"
    TipoTratamientoNuevo.UNIDADES -> "📅 Agendar intervención"
    TipoTratamientoNuevo.SERVICIO_UNICO, TipoTratamientoNuevo.CONSULTA -> "📅 Agendar la cita"
}


// ── Precio en la moneda de la sede ─────────────────────────────────────────

/**
 * ¿Falta escribir el precio en la moneda de la sede? Con una sede de OTRA moneda
 * y sin precio propio del servicio en ella, el precio del servicio está en la
 * moneda de la clínica: no sirve de respaldo. Una sesión suelta o un servicio
 * único con el campo vacío NO se crean (se cobrarían en la moneda equivocada);
 * el paquete ya lo exige su propio aviso y unidades exige el precio por unidad.
 */
fun faltaPrecioEnMonedaSede(
    sinPrecioEnMoneda: Boolean, tipo: TipoTratamientoNuevo?, modalidad: String,
    precioPorSesion: String, precioAcordado: String,
): Boolean {
    if (!sinPrecioEnMoneda || tipo == null) return false
    return when (tipo) {
        TipoTratamientoNuevo.SESIONES -> modalidad == "Sesión suelta" && precioPorSesion.toDoubleOrNull() == null
        TipoTratamientoNuevo.SERVICIO_UNICO -> precioAcordado.toDoubleOrNull() == null
        else -> false
    }
}

/**
 * Campos del servicio en una sede de OTRA moneda: los montos del servicio y sus
 * paquetes están en la moneda de la clínica, así que no se prellenan; solo el
 * precio propio de la sede (si lo tiene) como precio por sesión / por unidad.
 */
fun camposEnOtraMoneda(c: CamposComerciales, tipo: TipoTratamientoNuevo, precioSede: Double?): CamposComerciales {
    val sede = precioSede?.toString() ?: ""
    return c.copy(
        precioPaquete = "",
        precioPorSesion = sede,
        precioUnitario = if (tipo == TipoTratamientoNuevo.UNIDADES) sede else "",
    )
}

// ── Dónde se venderá ─────────────────────────────────────────────────────────

/**
 * La sede que el SERVIDOR le pondrá al tratamiento (`sede_por_defecto_tratamiento`):
 * la de la cita que lo origina → la sede activa del usuario → la principal. Sin
 * multisede, null (un solo local: nada que decidir).
 */
fun sedeDestinoTratamiento(multiSede: Boolean, sedeCitaOrigen: String?, sedeActiva: String?, sedePrincipal: String?): String? =
    if (!multiSede) null
    else sedeCitaOrigen?.takeIf { it.isNotBlank() } ?: sedeActiva?.takeIf { it.isNotBlank() } ?: sedePrincipal?.takeIf { it.isNotBlank() }

// ── Fecha y hora propuestas para la primera sesión ──────────────────────────

/** Un día del horario de atención (`configuracion.horarios_atencion` / `sedes.horarios_atencion`). */
data class DiaAtencion(val dia: String, val activo: Boolean, val apertura: String, val cierre: String)

/** Gemelo de HORARIO_ATENCION_DEFAULT (lib/horario-atencion.ts): rige si la clínica no guardó el suyo. */
val HORARIO_ATENCION_DEFAULT: List<DiaAtencion> = listOf(
    DiaAtencion("Lunes", true, "08:00", "18:00"),
    DiaAtencion("Martes", true, "08:00", "18:00"),
    DiaAtencion("Miércoles", true, "08:00", "18:00"),
    DiaAtencion("Jueves", true, "08:00", "18:00"),
    DiaAtencion("Viernes", true, "08:00", "18:00"),
    DiaAtencion("Sábado", true, "09:00", "13:00"),
    DiaAtencion("Domingo", false, "09:00", "13:00"),
)

/**
 * El horario guardado (JSON `[{dia, activo, apertura, cierre}]`). Vacío o
 * ilegible → el de por defecto (la web además entiende texto libre; aquí, ante
 * la duda, el default, que solo sirve para PROPONER una fecha editable).
 */
fun parsearHorariosAtencion(crudo: String?): List<DiaAtencion> {
    val txt = crudo?.trim().orEmpty()
    if (txt.isEmpty()) return HORARIO_ATENCION_DEFAULT
    return runCatching {
        kotlinx.serialization.json.Json.parseToJsonElement(txt) as kotlinx.serialization.json.JsonArray
    }.getOrNull()?.mapNotNull { e ->
        val o = e as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
        fun t(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it != "null" }
        val dia = t("dia") ?: return@mapNotNull null
        DiaAtencion(dia, t("activo") == "true", t("apertura").orEmpty(), t("cierre").orEmpty())
    }?.takeIf { it.isNotEmpty() } ?: HORARIO_ATENCION_DEFAULT
}

private fun minutosDe(hhmm: String): Int? {
    val p = hhmm.trim().split(":")
    val h = p.getOrNull(0)?.toIntOrNull() ?: return null
    val m = p.getOrNull(1)?.take(2)?.toIntOrNull() ?: 0
    return if (h in 0..23 && m in 0..59) h * 60 + m else null
}

private fun hhmm(min: Int): String = "${(min / 60).toString().padStart(2, '0')}:${(min % 60).toString().padStart(2, '0')}"

private fun sinTildes(s: String): String = s.lowercase()
    .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u')

/** Nombre del día (0 = domingo, como `AhoraEnZona.diaIdx`). */
private val DIAS_ATENCION_IDX = listOf("domingo", "lunes", "martes", "miercoles", "jueves", "viernes", "sabado")

/**
 * Fecha y hora propuestas para la primera sesión: la próxima hora en punto de
 * HOY si todavía cabe en el horario de atención (antes de abrir: la apertura);
 * si no (p. ej. ya cerró, o pasadas las 20:00), el próximo día que atiende, a
 * su hora de apertura. Nunca una hora pasada. Sin ningún día activo: mañana 09:00.
 */
fun primeraSesionPropuesta(hoyIso: String, minutosAhora: Int, diaIdxHoy: Int, horario: List<DiaAtencion>): Pair<String, String> {
    fun diaDe(idx: Int): DiaAtencion? = horario.firstOrNull { sinTildes(it.dia) == DIAS_ATENCION_IDX[((idx % 7) + 7) % 7] }
    val proximaEnPunto = (minutosAhora / 60 + 1) * 60
    diaDe(diaIdxHoy)?.takeIf { it.activo }?.let { d ->
        val abre = minutosDe(d.apertura)
        val cierra = minutosDe(d.cierre)
        if (abre != null && cierra != null) {
            val propuesta = maxOf(proximaEnPunto, abre)
            if (propuesta < cierra && propuesta < 24 * 60) return hoyIso to hhmm(propuesta)
        }
    }
    for (n in 1..7) {
        val d = diaDe(diaIdxHoy + n)?.takeIf { it.activo } ?: continue
        val abre = minutosDe(d.apertura) ?: continue
        return sumarDiasIso(hoyIso, n) to hhmm(abre)
    }
    return sumarDiasIso(hoyIso, 1) to "09:00"
}
