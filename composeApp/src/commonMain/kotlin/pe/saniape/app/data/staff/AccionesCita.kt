package pe.saniape.app.data.staff

/**
 * Las acciones de la tarjeta de cita de la agenda: CUÁLES existen (la misma
 * lista de siempre, gemela de accionesCita de /citas web) y DÓNDE van, para
 * que la tarjeta no sea un muro de botones (pedido del dueño, 24/10/2026):
 *
 * - UNA primaria: la acción del momento (botón lleno).
 * - A lo sumo UNA secundaria.
 * - 💰 compacto junto a la píldora de pago, solo si hay deuda.
 * - El resto en «⋯ Más», agrupado (Atención · Cobro · Cita, Cancelar al final).
 *
 * Regla pura: no decide permisos (eso llega ya resuelto en [EntradaAccionesCita]);
 * solo reparte. Lo que hoy no aparece tampoco aparece en el menú.
 */
enum class AccionTarjeta {
    Confirmar, Completar, Cancelar, Revertir, Editar, PasarEvaluacion, Repetir, Odontograma, CrearTratamiento,
    /** Sala de espera: "🔔 Llegó" y "🩺 Triaje". */
    Llego, Triaje,
    /** "▶ Atender": la consulta guiada (nativa, pantalla completa). */
    Atender,
    /** "💰 Cobrar": registrar el cobro de una Consulta/Evaluación (método + fecha del pago). */
    Cobrar,
    /** "💲 Poner precio": la cita no tiene costo (importada de Google Calendar); con precio aparece el 💰. */
    PonerPrecio,
    /** "🧠 Evaluación": el espacio de trabajo de la evaluación psicológica de su tratamiento. */
    EvaluacionPsico,
    /** "↺ Anular cobro": borra el cobro dividido de caja y la cita vuelve a "por cobrar" (solo Admin). */
    AnularCobro,
    /** "🧾 Comprobante": reimprimir el recibo del cobro ya hecho (A4 o ticket 80 mm). */
    Comprobante,
    /** "📝 Dar receta / indicaciones" de la cita atendida (o "🖨 Ver" la que ya tiene). */
    Receta,
    /** Nutrición: la ficha en ⚖️ Medidas (formulario abierto, vinculado a la cita) o en 🥗 Plan alimentario. */
    Medidas, PlanAlimentario,
    /** Gineco-obstetricia: la ficha en la pestaña 🤰 Embarazo. */
    Embarazo,
    /** 🧾 Pedido para recepción: armarlo (profesional) o cobrarlo (quien cobra). */
    Pedido,
    /** 💰 Saldo del paquete en la sesión ya atendida: abono al tratamiento. */
    AbonarPaquete,
    /** Sesión que cerró el plan: «＋ Ampliar» / «✓ Dar de alta» (en la ficha del paciente). */
    Ampliar, DarAlta,
    /** ⚡ Atención directa: «▶ Atender» (evolución + completar + cobro en una pantalla). */
    AtenderDirecto,
    /** ⚡ Atención directa ya atendida: «📝 Anotar evolución» / «📝 Evolución» (no completa ni cobra). */
    Evolucion,
    /** «🚫 No vino» el mismo día: registra la falta (no cancela; la sesión no se borra). */
    NoVino,
    /** Documentos de la atención completada: 📄 informe, 🛌 descanso, 🧪 orden, 📄 constancia. */
    DocInforme, DocDescanso, DocOrden, DocConstancia,
    /** 💉 Mapa de aplicación (estética): la ficha en la pestaña con ESA cita. */
    MapaAplicacion,
    /** «✓ Se realizó en esta visita»: el servicio de una vez hecho en esta evaluación/consulta (ServicioEnVisita.kt). */
    ServicioEnVisita,
}

/** La acción de la tarjeta para cada documento (lib/agenda-dia → AgendaDia.kt). null = no hay en la app. */
fun accionDeDocumento(clave: ClaveDocumentoCita): AccionTarjeta? = when (clave) {
    ClaveDocumentoCita.INFORME -> AccionTarjeta.DocInforme
    ClaveDocumentoCita.DESCANSO -> AccionTarjeta.DocDescanso
    ClaveDocumentoCita.ORDEN -> AccionTarjeta.DocOrden
    ClaveDocumentoCita.CONSTANCIA -> AccionTarjeta.DocConstancia
    ClaveDocumentoCita.MAPA -> AccionTarjeta.MapaAplicacion
    // La emisión del consentimiento fuera de la atención guiada todavía no está en la app.
    ClaveDocumentoCita.CONSENTIMIENTO -> null
}

/** Color de la acción, sin Compose (la tarjeta lo traduce a la paleta). */
enum class TonoAccion { OK, INFO, NAVY, PEND, ERROR, PURPLE, TEAL, SUAVE }

/** Grupo del menú «⋯ Más», en este orden. */
enum class GrupoAccion(val titulo: String) {
    ATENCION("Atención"),
    COBRO("Cobro"),
    CITA("Cita"),
}

data class AccionCitaItem(val texto: String, val accion: AccionTarjeta, val tono: TonoAccion)

/** Lo que la tarjeta necesita de la sala de espera (sin Compose). */
data class SalaAcciones(
    val etapa: EtapaLlegada,
    /** La clínica toma triaje. */
    val triajeOn: Boolean,
    /** La cita va por la consulta guiada. */
    val medica: Boolean,
    /** Se está marcando la llegada ahora mismo ("…"). */
    val marcando: Boolean = false,
    /** El triaje ya se tomó (el botón dice "corregir"). */
    val triajeHecho: Boolean = false,
)

/**
 * Todo lo que decide qué acciones existen. Cada bandera llega YA con su permiso
 * y su condición resueltos por la tarjeta / la pantalla (como antes).
 */
data class EntradaAccionesCita(
    val estado: String,
    val tipo: String?,
    /** Solo en clínicas de odontología y con paciente. */
    val odontograma: Boolean = false,
    val crearTratamiento: Boolean = false,
    /**
     * «🩺 Crear tratamiento» es la acción PRINCIPAL de la cita que evalúa ya
     * completada: el plan es el paso que sigue (estética, capilar, fisio,
     * podología, psicología). No en lo dental (el plan sale de la revisión con
     * odontograma), en la consulta médica guiada (su plan vive en la atención)
     * ni en nutrición (el control sigue con medidas y plan alimentario): ahí
     * queda en «⋯ Más», como en la web.
     */
    val crearTratamientoPrincipal: Boolean = false,
    val sala: SalaAcciones? = null,
    /** Consulta guiada por especialidad (sin depender de la sala de espera). */
    val medica: Boolean = false,
    /** Nombre del paso Evaluación en el flujo de la cita; null = no hay paso al que pasar. */
    val pasarA: String? = null,
    /** Consulta/Evaluación con costo, sin cobrar, y quien mira tiene permiso 'pagos'. */
    val cobrar: Boolean = false,
    /** Cita sin costo (no Sesión, no cancelada, no pagada) y quien mira tiene 'pagos'. */
    val ponerPrecio: Boolean = false,
    val evaluacionPsico: Boolean = false,
    /** Cobro dividido ya hecho y quien mira es Admin con 'pagos'. */
    val anularCobro: Boolean = false,
    /** Cita cobrada (no Sesión, con costo, no cancelada) y quien mira tiene 'pagos'. */
    val comprobante: Boolean = false,
    /** Texto de "📝 Dar receta / indicaciones" (solo en la cita completada); null = no aplica. */
    val receta: String? = null,
    /** Nutrición: "⚖️ Medidas" y "🥗 Plan alimentario" de la atención. */
    val nutricion: Boolean = false,
    /** Gineco-obstetricia: "🤰 Embarazo" (la pestaña del carné perinatal). */
    val embarazo: Boolean = false,
    /** Texto de "🧾 Pedido para recepción" (null = no aplica). */
    val pedido: String? = null,
    /** Sesión ya atendida de un paquete con deuda: 💰 abona al paquete. */
    val abonarPaquete: Boolean = false,
    /** Sesión que cerró el plan: ＋ Ampliar / ✓ Dar de alta. */
    val terminado: AccionesTerminado? = null,
    /** Atención directa: el cierre es «▶ Atender» (una pantalla), no ✓ Completar. */
    val directa: Boolean = false,
    /** Atención directa ya atendida: el botón de su evolución. */
    val evolucion: BotonEvolucionCita? = null,
    /** «🚫 No vino» (ofreceNoVino + permiso 'citas'): lo decide la pantalla. */
    val noVino: Boolean = false,
    /** Documentos de la atención (documentosDeCita, ya con sus permisos). */
    val documentos: List<DocumentoCita> = emptyList(),
    /** «✓ Se realizó en esta visita» ([ofreceServicioEnVisita], ya con permisos): lo decide la pantalla. */
    val servicioEnVisita: Boolean = false,
)

/**
 * TODAS las acciones de la cita (la lista de siempre, mismo orden y mismas
 * condiciones que tenía la tarjeta). [planAccionesCita] solo las reparte.
 */
fun accionesCita(e: EntradaAccionesCita): List<AccionCitaItem> {
    val estado = e.estado
    val sala = e.sala
    val lista = mutableListOf<AccionCitaItem>()
    fun add(t: String, a: AccionTarjeta, tono: TonoAccion) { lista.add(AccionCitaItem(t, a, tono)) }
    // El dinero va primero, como la moneda de la web.
    if (e.cobrar) add("💰 Cobrar", AccionTarjeta.Cobrar, TonoAccion.OK)
    if (e.abonarPaquete) add("💰 Cobrar paquete", AccionTarjeta.AbonarPaquete, TonoAccion.OK)
    // La última sesión del plan ya se atendió: ¿más sesiones o alta? (los flujos de la ficha).
    if (e.terminado?.ampliar == true) add("＋ Ampliar", AccionTarjeta.Ampliar, TonoAccion.INFO)
    if (e.terminado?.alta == true) add("✓ Dar de alta", AccionTarjeta.DarAlta, TonoAccion.OK)
    if (e.ponerPrecio) add("💲 Poner precio", AccionTarjeta.PonerPrecio, TonoAccion.TEAL)
    val activa = estado == "Pendiente" || estado == "Confirmada"
    // SALA DE ESPERA (gemelo de `principalSala` de /citas): mientras el paciente
    // recorre la sala aparecen Llegó / Triaje junto al cierre.
    val principal: AccionTarjeta? = when {
        sala == null || !activa -> null
        sala.etapa == EtapaLlegada.POR_LLEGAR -> AccionTarjeta.Llego
        sala.etapa == EtapaLlegada.LLEGO && sala.triajeOn -> AccionTarjeta.Triaje
        else -> AccionTarjeta.Atender
    }
    // Médica: por la sala de hoy o, sin sala, por la especialidad (como la web).
    val esMedica = sala?.medica == true || e.medica
    val textoAtender = when {
        e.directa -> "▶ Atender"
        esMedica -> if (sala?.etapa == EtapaLlegada.EN_CONSULTA) "▶ Continuar" else "▶ Atender"
        else -> "✓ Completar"
    }
    val accionAtender = when {
        e.directa -> AccionTarjeta.AtenderDirecto
        esMedica -> AccionTarjeta.Atender
        else -> AccionTarjeta.Completar
    }
    val textoTriaje = if (sala?.triajeHecho == true) "🩺 Triaje ✓ (corregir)" else "🩺 Triaje"
    when (principal) {
        AccionTarjeta.Llego -> add(if (sala?.marcando == true) "…" else "🔔 Llegó", AccionTarjeta.Llego, TonoAccion.OK)
        AccionTarjeta.Triaje -> add("🩺 Triaje", AccionTarjeta.Triaje, TonoAccion.INFO)
        AccionTarjeta.Atender -> add(textoAtender, accionAtender, TonoAccion.NAVY)
        else -> Unit
    }
    if (principal != null) {
        if (estado == "Pendiente") add("✓ Confirmar", AccionTarjeta.Confirmar, TonoAccion.OK)
        if (sala?.triajeOn == true && principal != AccionTarjeta.Triaje) add(textoTriaje, AccionTarjeta.Triaje, TonoAccion.INFO)
        if (principal != AccionTarjeta.Atender) add(textoAtender, accionAtender, TonoAccion.NAVY)
    } else if (estado == "Pendiente") add("✓ Confirmar", AccionTarjeta.Confirmar, TonoAccion.OK)
    // Evaluación psicológica: el espacio de trabajo, en cualquier estado (como la web).
    if (e.evaluacionPsico && estado != "Cancelada") add("🧠 Evaluación", AccionTarjeta.EvaluacionPsico, TonoAccion.PURPLE)
    if (e.pasarA != null && activa) add("→ ${e.pasarA}", AccionTarjeta.PasarEvaluacion, TonoAccion.INFO)
    // Sin sala de espera: la acción de cierre según la especialidad (Atender o Completar).
    if (activa && principal == null) add(textoAtender, accionAtender, TonoAccion.NAVY)
    // Atención directa ya atendida: la evolución se anota (o corrige) desde la tarjeta.
    if (e.evolucion != null && estado == "Completada") {
        add(e.evolucion.texto, AccionTarjeta.Evolucion, if (e.evolucion.resaltado) TonoAccion.PEND else TonoAccion.SUAVE)
    }
    if (estado == "Completada" || estado == "Cancelada") add("↩ Revertir", AccionTarjeta.Revertir, TonoAccion.PEND)
    // Receta / indicaciones de la atención (después de completar, o para verla).
    if (estado == "Completada" && e.receta != null) add(e.receta, AccionTarjeta.Receta, TonoAccion.PURPLE)
    // Repetir: agendar la SIGUIENTE cita del mismo paciente en 1 toque.
    if (estado == "Completada") add("🔁 Repetir", AccionTarjeta.Repetir, TonoAccion.TEAL)
    // Odontograma desde la cita, en cualquier estado (no en las sesiones de tratamiento).
    if (e.odontograma && e.tipo != "Sesión") add("🦷 Odontograma", AccionTarjeta.Odontograma, TonoAccion.INFO)
    // Nutrición: medidas y plan alimentario de esta atención (no en la cancelada).
    if (e.nutricion && estado != "Cancelada") {
        add("⚖️ Medidas", AccionTarjeta.Medidas, TonoAccion.TEAL)
        add("🥗 Plan alimentario", AccionTarjeta.PlanAlimentario, TonoAccion.TEAL)
    }
    // Gineco-obstetricia: el carné perinatal de la paciente (no en la cancelada).
    if (e.embarazo && estado != "Cancelada") add("🤰 Embarazo", AccionTarjeta.Embarazo, TonoAccion.TEAL)
    // 📄 Informe / descanso / constancia y 💉 mapa de ESTA atención (AgendaDia.kt).
    e.documentos.forEach { d -> accionDeDocumento(d.clave)?.let { add(d.texto, it, TonoAccion.PURPLE) } }
    // 🧾 Productos que se lleva o un servicio extra de esta atención (o cobrarlo).
    if (e.pedido != null) add(e.pedido, AccionTarjeta.Pedido, TonoAccion.TEAL)
    // El servicio de una vez hecho en ESTA visita (uña encarnada, bótox): tratamiento
    // realizado + cita completada + cobro en un guardado, como la web.
    if (e.servicioEnVisita && estado != "Cancelada") add("✓ Se realizó en esta visita", AccionTarjeta.ServicioEnVisita, TonoAccion.OK)
    // Crear el plan desde la cita que evalúa (no cancelada), como la web.
    if (e.crearTratamiento && estado != "Cancelada") add("🩺 Crear tratamiento", AccionTarjeta.CrearTratamiento, TonoAccion.PURPLE)
    // 🧾 El recibo del cobro ya hecho, junto al dinero (como el menú de /citas web).
    if (e.comprobante) add("🧾 Comprobante", AccionTarjeta.Comprobante, TonoAccion.NAVY)
    // Corregir un cobro dividido es anularlo y volver a cobrar (como la web, solo Admin).
    if (e.anularCobro && estado != "Cancelada") add("↺ Anular cobro", AccionTarjeta.AnularCobro, TonoAccion.ERROR)
    if (estado != "Cancelada" && estado != "Completada") {
        add("✏ Editar o reprogramar", AccionTarjeta.Editar, TonoAccion.SUAVE)
        // La falta, no la cancelación: en una Sesión, cancelar borraba la sesión.
        if (e.noVino) add("🚫 No vino", AccionTarjeta.NoVino, TonoAccion.PEND)
        add("✕ Cancelar", AccionTarjeta.Cancelar, TonoAccion.ERROR)
    }
    return lista
}

/** Dónde va cada acción de la tarjeta. */
data class PlanAccionesCita(
    /** La acción del momento (botón lleno). */
    val primaria: AccionCitaItem?,
    /** A lo sumo una más, visible. */
    val secundaria: AccionCitaItem?,
    /** 💰 compacto junto a la píldora de pago (solo con deuda). */
    val cobro: AccionCitaItem?,
    /** «⋯ Más»: el resto, por grupo y en orden. Vacío = sin «⋯». */
    val menu: List<Pair<GrupoAccion, List<AccionCitaItem>>>,
) {
    val visibles: List<AccionCitaItem> get() = listOfNotNull(primaria, secundaria)
    val enMenu: List<AccionCitaItem> get() = menu.flatMap { it.second }
}

/** Las acciones que cierran la atención (la principal "natural" de una cita activa). */
private val CIERRE = setOf(AccionTarjeta.Completar, AccionTarjeta.Atender, AccionTarjeta.AtenderDirecto)

/** A qué grupo del menú va cada acción. */
fun grupoDe(a: AccionTarjeta): GrupoAccion = when (a) {
    AccionTarjeta.Cobrar, AccionTarjeta.AbonarPaquete, AccionTarjeta.PonerPrecio,
    AccionTarjeta.Comprobante, AccionTarjeta.AnularCobro -> GrupoAccion.COBRO
    AccionTarjeta.Confirmar, AccionTarjeta.Editar, AccionTarjeta.Repetir,
    AccionTarjeta.Revertir, AccionTarjeta.NoVino, AccionTarjeta.Cancelar -> GrupoAccion.CITA
    else -> GrupoAccion.ATENCION
}

/** Orden dentro del grupo Cita: Cancelar (rojo) siempre al final. */
private fun ordenCita(a: AccionTarjeta): Int = when (a) {
    AccionTarjeta.Confirmar -> 0
    AccionTarjeta.Editar -> 1
    AccionTarjeta.Repetir -> 2
    AccionTarjeta.Revertir -> 3
    AccionTarjeta.NoVino -> 8
    AccionTarjeta.Cancelar -> 9
    else -> 5
}

/**
 * Reparte [accionesCita] entre primaria, secundaria, 💰 y «⋯ Más».
 *
 * Primaria según la etapa:
 * - Pendiente/Confirmada → el cierre (▶ Atender / ✓ Completar). «🔔 Llegó» casi
 *   no se usa: nunca es primaria, va al menú. «🩺 Triaje» es primaria solo si el
 *   paciente ya llegó, la clínica toma triaje y aún falta tomarlo.
 * - Completada → «📝 Anotar evolución» si falta; si la sesión cerró el plan,
 *   «✓ Dar de alta» (o «＋ Ampliar»); la cita que evalúa, «🩺 Crear tratamiento»
 *   (si [EntradaAccionesCita.crearTratamientoPrincipal]). Si no, nada.
 * - Cancelada u otro estado → nada (todo en el menú).
 *
 * Secundaria (una): ✓ Confirmar (Pendiente) › el cierre si la primaria es
 * Triaje › → Evaluación › 🧠 Evaluación; en la Completada, la otra opción del
 * plan terminado o 🔁 Repetir (agendar la siguiente, muy usado).
 */
fun planAccionesCita(e: EntradaAccionesCita): PlanAccionesCita {
    val todas = accionesCita(e)
    fun de(a: AccionTarjeta) = todas.firstOrNull { it.accion == a }
    val activa = e.estado == "Pendiente" || e.estado == "Confirmada"
    val cierre = todas.firstOrNull { it.accion in CIERRE }

    val primaria: AccionCitaItem? = when {
        activa -> {
            val sala = e.sala
            val faltaTriaje = sala != null && sala.etapa == EtapaLlegada.LLEGO && sala.triajeOn
            if (faltaTriaje) de(AccionTarjeta.Triaje) ?: cierre else cierre
        }
        e.estado == "Completada" ->
            de(AccionTarjeta.Evolucion)?.takeIf { e.evolucion?.resaltado == true }
                ?: de(AccionTarjeta.DarAlta) ?: de(AccionTarjeta.Ampliar)
                ?: de(AccionTarjeta.CrearTratamiento)?.takeIf { e.crearTratamientoPrincipal }
        else -> null
    }

    val candidatasSecundaria: List<AccionCitaItem?> = when {
        activa -> listOf(
            de(AccionTarjeta.Confirmar),
            cierre?.takeIf { primaria?.accion == AccionTarjeta.Triaje },
            de(AccionTarjeta.PasarEvaluacion),
            // Podología y todo servicio de una vez: a un toque en la evaluación del día.
            de(AccionTarjeta.ServicioEnVisita),
            de(AccionTarjeta.EvaluacionPsico),
        )
        e.estado == "Completada" -> listOf(
            de(AccionTarjeta.Ampliar).takeIf { primaria?.accion == AccionTarjeta.DarAlta },
            de(AccionTarjeta.Repetir),
        )
        else -> emptyList()
    }
    val secundaria = candidatasSecundaria.filterNotNull().firstOrNull { it != primaria }

    // 💰 compacto: solo con deuda (cobro de la cita o saldo del paquete).
    val cobro = de(AccionTarjeta.Cobrar) ?: de(AccionTarjeta.AbonarPaquete)

    val fuera = setOfNotNull(primaria, secundaria, cobro)
    val resto = todas.filter { it !in fuera }
    val menu = GrupoAccion.entries.mapNotNull { g ->
        val items = resto.filter { grupoDe(it.accion) == g }
            .let { l -> if (g == GrupoAccion.CITA) l.sortedBy { ordenCita(it.accion) } else l }
        if (items.isEmpty()) null else g to items
    }
    return PlanAccionesCita(primaria, secundaria, cobro, menu)
}
