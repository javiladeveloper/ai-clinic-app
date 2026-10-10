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
 * primera sesión. Por sesiones, la tarjeta "Agendarla ahora" del formulario
 * arranca MARCADA (como la web); si se desmarca —o el tipo no tiene esa
 * tarjeta— se ofrece después de crear. null = no hay nada que ofrecer (ya se
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

