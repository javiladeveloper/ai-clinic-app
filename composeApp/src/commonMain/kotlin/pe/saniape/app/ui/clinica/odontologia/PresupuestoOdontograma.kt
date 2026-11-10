package pe.saniape.app.ui.clinica.odontologia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import pe.saniape.app.data.offline.ColaRepo
import pe.saniape.app.data.staff.CODIGO_PIEZAS_YA_PRESUPUESTADAS
import pe.saniape.app.data.staff.MENSAJE_PIEZAS_YA_PRESUPUESTADAS
import pe.saniape.app.data.staff.TratamientoEnCola
import pe.saniape.app.data.staff.esCreacionParcial
import pe.saniape.app.data.staff.marcasTrasCambio
import pe.saniape.app.data.staff.libresParaPresupuesto
import pe.saniape.app.data.staff.nombresSinPrecio
import pe.saniape.app.data.staff.presupuestoDesactualizado
import pe.saniape.app.data.staff.tratamientosEnCola
import pe.saniape.app.data.staff.DienteHallazgo
import pe.saniape.app.data.staff.HallazgoDental
import pe.saniape.app.data.staff.OdontogramaRepo
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.PlanTratamiento
import pe.saniape.app.data.staff.ProcedimientoRef
import pe.saniape.app.data.staff.agruparPresupuesto
import pe.saniape.app.data.staff.esLineaDeBoca
import pe.saniape.app.data.staff.planTratamiento
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.data.staff.simboloMoneda

/**
 * El presupuesto que sale del odontograma, y el botón que lo vuelve tratamiento.
 *
 * Cada servicio es una línea (tres caries → "Resina ×3"). Se marcan las que el
 * paciente acepta y se crean los tratamientos. El DINERO va por el endpoint de
 * siempre (/api/staff/tratamiento/accion), que también ata los hallazgos al
 * tratamiento en el servidor: así funciona igual desde la cola sin señal.
 *
 * Gemelo de `components/odontologia/PresupuestoPanel.tsx` en la web.
 */
@Composable
internal fun PresupuestoOdontograma(
    pacienteId: String,
    citaId: String?,
    hallazgos: List<DienteHallazgo>,
    catalogo: List<HallazgoDental>,
    soloLectura: Boolean,
    /** Tras crear tratamientos o vincular un servicio: recargar lo de arriba. */
    onCambio: () -> Unit,
    /** Qué especialidades son dentales (de /api/staff/contexto). */
    mapaDental: pe.saniape.app.data.staff.MapaDental = pe.saniape.app.data.staff.MapaDental(),
    /**
     * Dentro de la revisión de una evaluación dental: aquí se registra "crear lo
     * marcado", que "✓ Completar evaluación" llama si el odontólogo no apretó
     * "Crear N tratamiento(s)". null = fuera de la revisión (ficha, sesiones).
     */
    registro: RegistroPresupuesto? = null,
    /** Tras crear con el botón del presupuesto: ofrecer agendar la primera cita. */
    onOfrecerAgendar: ((pe.saniape.app.ui.clinica.pacientes.OfertaPrimeraCita) -> Unit)? = null,
) {
    val moneda = pe.saniape.app.ui.monedaUI()
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    // `todos` arma las líneas (un hallazgo ya vinculado nunca se pierde);
    // `servicios` son los que se OFRECEN para elegir: solo los dentales.
    var todos by remember { mutableStateOf<List<ProcedimientoRef>>(emptyList()) }
    val servicios = remember(todos, mapaDental) {
        todos.filter { pe.saniape.app.data.staff.esServicioDental(it.especialidadId, mapaDental) }
    }
    var creando by remember { mutableStateOf(false) }
    var vinculando by remember { mutableStateOf<HallazgoDental?>(null) }
    var eligiendoExtra by remember { mutableStateOf(false) }
    var extras by remember { mutableStateOf<List<ProcedimientoRef>>(emptyList()) }
    // Precio ajustado por el médico (descuento, caso especial), por servicio.
    // Sin ajuste manda el del tarifario. Igual que la web.
    var precios by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var editandoPrecio by remember { mutableStateOf<Pair<String, String>?>(null) }  // id → nombre

    // Hasta que cargan los servicios las líneas no existen, y "Completar
    // evaluación" creería que no hay nada que crear: no se registra antes.
    var serviciosCargados by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { todos = OdontogramaRepo.procedimientos(); serviciosCargados = true }

    // ── Lo que ya va en camino (auditoría app C1) ───────────────────────────
    // Un «crear tratamiento» que quedó en la cola sin señal NO está hecho: sus
    // piezas siguen sin tratamiento en lo que se ve. Se leen de la cola (sobrevive
    // a cerrar la app) y salen del presupuesto como «pendiente de sincronizar»:
    // un segundo toque no puede encolar otro tratamiento por las mismas piezas.
    val onCambioActual by androidx.compose.runtime.rememberUpdatedState(onCambio)
    var enCola by remember(pacienteId) { mutableStateOf<List<TratamientoEnCola>>(emptyList()) }
    val pendientesCola by pe.saniape.app.data.offline.EstadoSync.pendientes.collectAsState()
    LaunchedEffect(pacienteId, pendientesCola) {
        val antes = enCola.flatMap { it.hallazgoIds }.toSet()
        val ahora = runCatching { tratamientosEnCola(ColaRepo.pendientes(), pacienteId) }.getOrDefault(enCola)
        enCola = ahora
        // Algo de lo encolado ya subió (o el servidor lo rechazó): se relee el
        // odontograma para ver esas piezas atadas, o libres otra vez.
        if ((antes - ahora.flatMap { it.hallazgoIds }.toSet()).isNotEmpty()) onCambioActual()
    }
    // Lo recién creado, hasta que la recarga lo traiga atado a su tratamiento.
    var creadosLocal by remember(pacienteId) { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(hallazgos) {
        if (creadosLocal.isNotEmpty()) {
            creadosLocal = creadosLocal.filterTo(mutableSetOf()) { id -> hallazgos.any { it.id == id && it.tratamientoId == null } }
        }
    }
    val idsEnCola = remember(enCola) { enCola.flatMap { it.hallazgoIds }.toSet() }

    // Solo lo que todavía no tiene tratamiento: lo ya presupuestado no se
    // vuelve a ofrecer (crearía un segundo tratamiento por la misma caries).
    val libres = remember(hallazgos, idsEnCola, creadosLocal) { libresParaPresupuesto(hallazgos, idsEnCola + creadosLocal) }
    val (lineasBase, sinServicio, sinPieza) = remember(libres, catalogo, todos, servicios) {
        // El servicio SUGERIDO por nombre sale solo de los dentales (igual que la web).
        agruparPresupuesto(libres, catalogo, todos, servicios)
    }
    // Se aplica el precio ajustado ANTES de planificar, así el tratamiento se
    // crea con lo que el médico acordó con el paciente, no con el de lista.
    val lineas = remember(lineasBase, precios) {
        lineasBase.map { l ->
            val pu = precios[l.procedimientoId] ?: return@map l
            l.copy(precioUnitario = pu, subtotal = if (esLineaDeBoca(l)) pu else pu * l.piezas.size)
        }
    }
    fun precioExtra(e: ProcedimientoRef) = precios[e.id] ?: e.precio
    // Todas marcadas por defecto: lo normal es presupuestar todo lo encontrado.
    // La clave son los SERVICIOS, no `lineas`: `lineas` cambia al editar un
    // precio, y con esa clave editar un precio volvía a marcar lo desmarcado.
    val idsServicios = lineasBase.map { it.procedimientoId }
    // Al desaparecer lo creado (R5) NO se re-marca lo que el odontólogo desmarcó:
    // solo las líneas nuevas entran marcadas.
    var marcadas by remember { mutableStateOf(idsServicios.toSet()) }
    var vistas by remember { mutableStateOf(idsServicios.toSet()) }
    if (idsServicios.toSet() != vistas) {
        val (m, v) = marcasTrasCambio(marcadas, vistas, idsServicios)
        marcadas = m
        vistas = v
    }
    val procPorId = remember(todos) { todos.associateBy { it.id } }

    val total = lineas.filter { it.procedimientoId in marcadas }.sumOf { it.subtotal } +
        extras.sumOf { precioExtra(it) }

    /**
     * Lo creado sale de la lista EN CUANTO se crea, aunque otra línea falle
     * (auditoría C2, igual que `onCreado` de la web): reintentar no vuelve a
     * crear la ortodoncia que ya se cargó. Las piezas creadas se ocultan hasta
     * que la recarga las traiga atadas; las encoladas, por la cola.
     */
    fun aplicar(r: ResultadoCrear) {
        if (r.extrasCreados.isNotEmpty()) extras = extras.filter { it.id !in r.extrasCreados }
        val usados = r.lineasCreadas + r.extrasCreados
        if (usados.isNotEmpty()) precios = precios - usados
        if (r.hallazgosCreados.isNotEmpty()) creadosLocal = creadosLocal + r.hallazgosCreados
        r.cola?.let { enCola = it }
    }

    // ── Completar evaluación en un paso (29/09/2026, gemelo de PresupuestoPanel web) ──
    // Lo que se crea es lo que está A LA VISTA: líneas marcadas, precios
    // ajustados y servicios añadidos. Se vuelve a registrar en cada composición
    // (SideEffect), así siempre ve lo último que tocó el odontólogo.
    if (registro != null) {
        val crearMarcados: suspend () -> ResultadoTratamientoEvaluacion = crear@{
            if (creando) throw ErrorEvaluacionDental("se está creando el tratamiento; espera un momento.")
            val elegidas = lineas.filter { it.procedimientoId in marcadas }
            val extrasAhora = extras.map { it.copy(precio = precioExtra(it)) }
            if (elegidas.isEmpty() && extrasAhora.isEmpty()) {
                return@crear when {
                    lineas.isNotEmpty() -> ResultadoTratamientoEvaluacion.Desmarcado
                    (sinServicio - sinPieza.toSet()).isNotEmpty() -> ResultadoTratamientoEvaluacion.SinServicio
                    else -> ResultadoTratamientoEvaluacion.SinHallazgos
                }
            }
            // Sin precio no se crea NADA: un tratamiento en S/ 0 queda "pago
            // pendiente" para siempre y no se cobra nunca (regla de la web).
            val sinPrecio = nombresSinPrecio(elegidas, extrasAhora)
            if (sinPrecio.isNotEmpty()) {
                throw ErrorEvaluacionDental("pon el precio de ${sinPrecio.joinToString(", ")} en el presupuesto (o desmárcalo).")
            }
            val planes = elegidas.map { planTratamiento(it, procPorId[it.procedimientoId]) }
            val cuantos = planes.size + extrasAhora.size
            creando = true
            val r = try {
                crearTodo(pacienteId, citaId, planes, extrasAhora, procPorId)
            } finally {
                creando = false
            }
            aplicar(r)
            onCambio()
            if (r.desactualizado != null) throw ErrorEvaluacionDental(r.desactualizado.replaceFirstChar { it.lowercase() } + ".")
            val fallidos = r.fallidos
            val oferta = r.oferta
            if (fallidos > 0) {
                // Los que sí se crearon quedan (y atan sus piezas): al reintentar,
                // la evaluación ya "tiene tratamiento" y no se duplica, así que lo
                // que faltó se crea con el botón del presupuesto.
                throw ErrorEvaluacionDental(
                    if (fallidos == cuantos) "no se pudo crear el tratamiento."
                    else "se crearon ${cuantos - fallidos} de $cuantos tratamientos. Crea lo que falta con \"Crear tratamiento(s)\" del presupuesto.",
                )
            }
            extras = emptyList()
            precios = emptyMap()
            ResultadoTratamientoEvaluacion.Creado(cuantos, oferta)
        }
        // «Lo pensará»: el mismo enlace para aceptar de la web (sin crear nada).
        val enviarEnlace: suspend () -> pe.saniape.app.data.staff.ResultadoEnlacePresupuesto = {
            val items = pe.saniape.app.data.staff.itemsPresupuestoDental(
                lineas.filter { it.procedimientoId in marcadas },
                extras.map { it.copy(precio = precioExtra(it)) },
                preciosManuales = precios.keys,
            )
            pe.saniape.app.data.staff.PresupuestoDentalRepo.enlaceWhatsApp(pacienteId, items, hallazgos, catalogo, moneda)
        }
        val listo = serviciosCargados && !soloLectura
        androidx.compose.runtime.SideEffect {
            registro.crear = if (listo) crearMarcados else null
            registro.enviarEnlace = if (listo) enviarEnlace else null
        }
        androidx.compose.runtime.DisposableEffect(registro) { onDispose { registro.crear = null; registro.enviarEnlace = null } }
    }

    Column(
        Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .padding(12.dp),
    ) {
        Text("💰 Presupuesto", color = c.texto, fontWeight = FontWeight.Bold, fontSize = 15.sp)

        if (lineas.isEmpty() && sinServicio.isEmpty() && extras.isEmpty() && enCola.isEmpty()) {
            Text(
                "Marca hallazgos en el odontograma y aquí aparece lo que cuesta tratarlos.",
                color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
            )
        }

        // ── Una línea por servicio ────────────────────────────────────────
        lineas.forEach { l ->
            val sel = l.procedimientoId in marcadas
            val deBoca = esLineaDeBoca(l)
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp)
                    .clickable(enabled = !soloLectura) {
                        marcadas = if (sel) marcadas - l.procedimientoId else marcadas + l.procedimientoId
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (sel) "☑" else "☐", color = c.navy, fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (deBoca) l.nombre else "${l.nombre} ×${l.piezas.size}",
                        color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (deBoca) "${l.hallazgoNombre} · boca completa (se cobra una vez)"
                        else "${l.hallazgoNombre} · pieza(s) ${l.piezas.joinToString(", ")}",
                        color = c.textoSuave, fontSize = 11.sp,
                    )
                }
                // Tocar el precio lo edita (precio por pieza, o total si es de boca).
                Text(
                    "${simboloMoneda(moneda)} ${dinero(l.subtotal)}",
                    color = if (l.procedimientoId in precios) c.navy else c.texto,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.clickable(enabled = !soloLectura) {
                        editandoPrecio = l.procedimientoId to l.nombre
                    }.padding(4.dp),
                )
            }
        }

        // ── Servicios agregados a mano (ortodoncia, brackets, blanqueamiento) ──
        extras.forEach { e ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("☑", color = c.navy, fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.nombre, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Tratamiento complementario", color = c.textoSuave, fontSize = 11.sp)
                }
                Text(
                    "${simboloMoneda(moneda)} ${dinero(precioExtra(e))}",
                    color = if (e.id in precios) c.navy else c.texto,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.clickable(enabled = !soloLectura) { editandoPrecio = e.id to e.nombre }.padding(4.dp),
                )
                if (!soloLectura) Text(
                    "✕", color = c.error, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { extras = extras - e }.padding(start = 8.dp),
                )
            }
        }

        // ── Hallazgos que no tienen servicio ──────────────────────────────
        // No se pueden cobrar hasta que se les asigne uno. Se muestran para que
        // no se olviden: una fractura sin servicio no entra al presupuesto.
        // ── Tratamientos en la cola sin señal: ya van, no se ofrecen otra vez ──
        enCola.forEach { t ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("⏳", fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        t.diagnostico ?: procPorId[t.procedimientoId]?.nombre ?: "Tratamiento",
                        color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    )
                    Text("Pendiente de sincronizar: se creará al volver la señal", color = c.textoSuave, fontSize = 11.sp)
                }
                t.precioAcordado?.let {
                    Text("${simboloMoneda(moneda)} ${dinero(it)}", color = c.textoSuave, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        val porIdCat = remember(catalogo) { catalogo.associateBy { it.id } }
        // Los «sin pieza» también vienen en sinServicio (como la web): no se les
        // ofrece «Asignar servicio», el arreglo es marcarlos en su pieza.
        val sinServicioReal = remember(sinServicio, sinPieza) { sinServicio - sinPieza.toSet() }
        // ── Hallazgos de PIEZA guardados en «Boca completa» (R2) ──────────
        // No se cobran: no tienen pieza. Antes volvían «de boca» toda la línea
        // y se cobraba una resina por varias caries.
        if (sinPieza.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Sin pieza (no se cobra)", color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            sinPieza.map { it.hallazgoId }.distinct().mapNotNull { porIdCat[it] }.forEach { hal ->
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(colorDe(hal.color)))
                    Spacer(Modifier.width(6.dp))
                    Text(hal.nombre, color = c.texto, fontSize = 12.sp)
                }
            }
            Text(
                "Quedó en «Boca completa» y es de una pieza: márcalo en su pieza del odontograma y bórralo de «Boca completa».",
                color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (sinServicioReal.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Sin servicio asignado", color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            val porId = porIdCat
            sinServicioReal.map { it.hallazgoId }.distinct().mapNotNull { porId[it] }.forEach { hal ->
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(colorDe(hal.color)))
                    Spacer(Modifier.width(6.dp))
                    Text(hal.nombre, color = c.texto, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    if (!soloLectura) Text(
                        "Asignar servicio", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { vinculando = hal }.padding(4.dp),
                    )
                }
            }
        }

        // ── Total y acción ────────────────────────────────────────────────
        if (!soloLectura) {
            Spacer(Modifier.height(10.dp))
            Text(
                "+ Añadir servicio (ortodoncia, brackets…)",
                color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { eligiendoExtra = true }.padding(vertical = 4.dp),
            )
        }
        if (lineas.isNotEmpty() || extras.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Total", color = c.textoSuave, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("${simboloMoneda(moneda)} ${dinero(total)}", color = c.texto, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            val cuantos = marcadas.size + extras.size
            if (!soloLectura) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
                        .background(if (cuantos > 0 && !creando) c.navy else c.borde)
                        .clickable(enabled = cuantos > 0 && !creando) {
                            val elegidas = lineas.filter { it.procedimientoId in marcadas }
                            val extrasAhora = extras.map { it.copy(precio = precioExtra(it)) }
                            // La misma guarda que la evaluación (auditoría C3): sin
                            // precio no se crea NADA, ni la línea ni el resto.
                            val sinPrecio = nombresSinPrecio(elegidas, extrasAhora)
                            if (sinPrecio.isNotEmpty()) {
                                Toaster.error("Pon el precio de ${sinPrecio.joinToString(", ")} (o desmárcalo)")
                                return@clickable
                            }
                            creando = true
                            val planes: List<PlanTratamiento> =
                                elegidas.map { planTratamiento(it, procPorId[it.procedimientoId]) }
                            scope.launch {
                                val r = try {
                                    crearTodo(pacienteId, citaId, planes, extrasAhora, procPorId)
                                } finally {
                                    creando = false
                                }
                                aplicar(r)
                                onCambio()
                                when {
                                    r.desactualizado != null -> Toaster.error(r.desactualizado)
                                    r.fallidos > 0 -> Toaster.error("${r.fallidos} no se pudieron crear. Revisa y reintenta.")
                                    // Lo encolado ya lo avisó la cola («se registrará al volver la señal»).
                                    r.enCola > 0 && r.creadosEnServidor > 0 ->
                                        Toaster.exito("${r.creadosEnServidor} creado(s); ${r.enCola} se creará(n) al volver la señal")
                                    r.enCola == 0 -> Toaster.exito("Tratamiento(s) creados")
                                }
                                // Siempre se ofrece agendar la primera cita (requisito del dueño).
                                if (r.fallidos == 0 && r.desactualizado == null) r.oferta?.let { of -> onOfrecerAgendar?.invoke(of) }
                            }
                        }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when {
                            creando -> "Creando…"
                            cuantos == 0 -> "Marca lo que el paciente acepta"
                            else -> "Crear $cuantos tratamiento(s)"
                        },
                        color = if (cuantos > 0 && !creando) c.sobreNavy else c.textoSuave,
                        fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    )
                }
            }
        }
    }

    // ── Asignar un servicio a un hallazgo del catálogo ────────────────────
    vinculando?.let { hal ->
        ElegirServicio(
            titulo = "Servicio para \"${hal.nombre}\"",
            subtitulo = "Se usará para todos los pacientes: es del catálogo de la clínica",
            servicios = servicios,
            onCerrar = { vinculando = null },
            onElegir = { proc ->
                vinculando = null
                scope.launch {
                    if (OdontogramaRepo.vincularServicio(hal.id, proc.id)) {
                        Toaster.exito("Servicio asignado")
                        onCambio()
                    } else Toaster.error("No se pudo asignar")
                }
            },
        )
    }

    editandoPrecio?.let { (id, nombre) ->
        val linea = lineasBase.firstOrNull { it.procedimientoId == id }
        val base = linea?.precioUnitario ?: extras.firstOrNull { it.id == id }?.precio ?: 0.0
        EditarPrecio(
            nombre = nombre,
            // En una línea por piezas se edita el precio POR PIEZA: es lo que
            // el médico negocia ("la resina te la dejo en 50").
            porPieza = linea != null && !esLineaDeBoca(linea) && linea.piezas.size > 1,
            actual = precios[id] ?: base,
            base = base,
            onCerrar = { editandoPrecio = null },
            onGuardar = { nuevo ->
                precios = if (nuevo == null) precios - id else precios + (id to nuevo)
                editandoPrecio = null
            },
        )
    }

    if (eligiendoExtra) {
        ElegirServicio(
            titulo = "Añadir al presupuesto",
            subtitulo = "Un servicio que no sale de un hallazgo",
            servicios = servicios.filter { s -> extras.none { it.id == s.id } },
            onCerrar = { eligiendoExtra = false },
            onElegir = { proc -> extras = extras + proc; eligiendoExtra = false },
        )
    }
}

/**
 * Crea un tratamiento por plan y uno por cada servicio extra. Devuelve cuántos
 * fallaron (si fallan algunos, los que sí se crearon quedan —no se deshacen— y
 * como los hallazgos ya atados dejan de aparecer, reintentar solo toca lo que
 * faltó) y la oferta de agendar la primera cita del PRIMER tratamiento creado
 * (null si quedó en la cola offline: aún no hay id).
 *
 * Modalidad: "Paquete" (no "Sesiones", que la base no acepta: el servidor la
 * normalizaba) y la sesión suelta lleva su precio (= lo presupuestado), como
 * `tratamientoDeItem` de la web: sin él, completar la sesión cobraba el precio
 * de lista y quedaba saldo fantasma.
 */
private class ResultadoCrear(
    val fallidos: Int = 0,
    val oferta: pe.saniape.app.ui.clinica.pacientes.OfertaPrimeraCita? = null,
    /** Servicios de las líneas que quedaron registradas (en el servidor o en la cola). */
    val lineasCreadas: Set<String> = emptySet(),
    /** Extras registrados: salen de la lista aunque otro falle. */
    val extrasCreados: Set<String> = emptySet(),
    /** Piezas atadas YA en el servidor (se ocultan hasta que la recarga las traiga). */
    val hallazgosCreados: Set<String> = emptySet(),
    val enCola: Int = 0,
    val creadosEnServidor: Int = 0,
    /** La cola releída tras crear (null = no se pudo leer). */
    val cola: List<TratamientoEnCola>? = null,
    /** El presupuesto estaba viejo: no se creó lo que faltaba. El texto a mostrar. */
    val desactualizado: String? = null,
)

private const val MENSAJE_YA_EN_COLA = "Estas piezas ya van en la cola: se crearán al volver la señal"

private suspend fun colaDe(pacienteId: String): List<TratamientoEnCola>? =
    runCatching { tratamientosEnCola(ColaRepo.pendientes(), pacienteId) }.getOrNull()

private suspend fun crearTodo(
    pacienteId: String,
    citaId: String?,
    planes: List<PlanTratamiento>,
    extras: List<ProcedimientoRef>,
    procPorId: Map<String, ProcedimientoRef>,
): ResultadoCrear {
    // Antes de crear se relee lo de las piezas (auditoría C1): si la web, otro
    // equipo o un toque anterior ya las presupuestó, el presupuesto que se ve
    // está viejo y crear daría un segundo tratamiento por las mismas piezas.
    val ids = planes.flatMap { it.hallazgoIds }
    if (ids.isNotEmpty()) {
        val colaAntes = colaDe(pacienteId)
        val idsCola = colaAntes.orEmpty().flatMap { it.hallazgoIds }.toSet()
        val frescos = OdontogramaRepo.hallazgosONull(pacienteId)
        if (presupuestoDesactualizado(ids, frescos, idsCola)) {
            return ResultadoCrear(
                cola = colaAntes,
                desactualizado = if (ids.any { it in idsCola }) MENSAJE_YA_EN_COLA else MENSAJE_PIEZAS_YA_PRESUPUESTADAS,
            )
        }
    }

    var fallidos = 0
    var enCola = 0
    var enServidor = 0
    var desactualizado: String? = null
    val lineasCreadas = mutableSetOf<String>()
    val extrasCreados = mutableSetOf<String>()
    val hallazgosCreados = mutableSetOf<String>()
    // id creado + su servicio + la respuesta (trae su sede)
    var primero: Triple<String, ProcedimientoRef?, kotlinx.serialization.json.JsonObject?>? = null
    /** true = quedó registrada (en el servidor o en la cola). */
    suspend fun crear(cuerpo: kotlinx.serialization.json.JsonObject, proc: ProcedimientoRef?): Boolean {
        val r = PacientesRepo.crearTratamientoDetalle(cuerpo)
        if (!r.registrada) {
            if (r.codigo == CODIGO_PIEZAS_YA_PRESUPUESTADAS) {
                // R4 (servidor): otro ya ató esas piezas y no se creó nada. Se
                // para aquí: con el presupuesto viejo, lo que sigue (extras
                // incluidos) también puede estar ya cargado.
                desactualizado = MENSAJE_PIEZAS_YA_PRESUPUESTADAS
                return false
            }
            val cuerpoRechazo = r.rechazo?.datos ?: r.cuerpo
            if (esCreacionParcial(r.rechazo?.status ?: 0, cuerpoRechazo)) {
                // 409 parcial: el tratamiento SÍ existe; reintentar lo duplicaría.
                // Cuenta como creado (sale de la lista) y se muestra el mensaje del servidor.
                r.rechazo?.let { Toaster.error(it.error) }
                enServidor++
                return true
            }
            fallidos++
            r.rechazo?.let { Toaster.error(it.error) }
            return false
        }
        if (r.encolada) enCola++ else enServidor++
        val id = pe.saniape.app.data.staff.idDeRespuesta(r.cuerpo)
        if (primero == null && id != null && !r.encolada) primero = Triple(id, proc, r.cuerpo)
        return true
    }
    for (p in planes) {
        if (desactualizado != null) break
        val modalidad = if (p.modalidad == "Sesiones") "Paquete" else p.modalidad
        val antesCola = enCola
        val ok = crear(PacientesRepo.cuerpoCrearTratamiento(
            pacienteId = pacienteId, procedimientoId = p.procedimientoId, terapeutaId = null,
            modalidad = modalidad, totalSesiones = p.totalSesiones, precioPaquete = p.precioPaquete,
            precioPorSesion = if (modalidad == "Sesión suelta") p.precioAcordado else null,
            precioAcordado = p.precioAcordado, diagnostico = p.diagnostico,
            citaOrigenId = citaId, cantidadUnidades = p.cantidadUnidades, precioUnitario = p.precioUnitario,
            hallazgoIds = p.hallazgoIds,
        ), procPorId[p.procedimientoId])
        if (ok) {
            lineasCreadas += p.procedimientoId
            // Las encoladas las oculta la cola; estas ya están atadas en el servidor.
            if (enCola == antesCola) hallazgosCreados += p.hallazgoIds
        }
    }
    for (e in extras) {
        if (desactualizado != null) break
        val porSesiones = e.modoCobro == "sesiones"
        val ok = crear(PacientesRepo.cuerpoCrearTratamiento(
            pacienteId = pacienteId, procedimientoId = e.id, terapeutaId = null,
            modalidad = if (porSesiones) "Paquete" else "Sesión suelta",
            totalSesiones = if (porSesiones) (e.tarifarios.firstOrNull()?.cantidadSesiones ?: 1) else 1,
            precioPaquete = if (porSesiones) e.precio else null,
            precioPorSesion = if (porSesiones) null else e.precio, precioAcordado = e.precio,
            diagnostico = "${e.nombre} (Tratamiento complementario)", citaOrigenId = citaId,
        ), e)
        if (ok) extrasCreados += e.id
    }
    val oferta = primero?.let { (id, proc, resp) ->
        pe.saniape.app.ui.clinica.pacientes.ofertaPrimeraCitaDe(
            tratamientoId = id, pacienteId = pacienteId, pacienteNombre = null,
            tipo = proc?.let { pe.saniape.app.data.staff.tipoTratamientoDe(it) }
                ?: pe.saniape.app.data.staff.TipoTratamientoNuevo.SESIONES,
            terapeutaId = null, especialidadId = proc?.especialidadId, respuesta = resp,
        )
    }
    return ResultadoCrear(
        fallidos = fallidos, oferta = oferta,
        lineasCreadas = lineasCreadas, extrasCreados = extrasCreados, hallazgosCreados = hallazgosCreados,
        enCola = enCola, creadosEnServidor = enServidor,
        cola = if (enCola > 0) colaDe(pacienteId) else null,
        desactualizado = desactualizado,
    )
}

@Composable
private fun ElegirServicio(
    titulo: String,
    subtitulo: String,
    servicios: List<ProcedimientoRef>,
    onCerrar: () -> Unit,
    onElegir: (ProcedimientoRef) -> Unit,
) {
    val moneda = pe.saniape.app.ui.monedaUI()
    val c = Sania.colors
    DialogoForm(titulo = titulo, subtitulo = subtitulo, textoAccion = "Cerrar", onCancelar = onCerrar, onAccion = onCerrar) {
        if (servicios.isEmpty()) {
            Text("No hay servicios de odontología activos.", color = c.textoSuave, fontSize = 13.sp)
        }
        EtqForm("Servicios")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            servicios.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                        .clickable { onElegir(s) }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(s.nombre, color = c.texto, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("${simboloMoneda(moneda)} ${dinero(s.precio)}", color = c.textoSuave, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun EditarPrecio(
    nombre: String,
    porPieza: Boolean,
    actual: Double,
    base: Double,
    onCerrar: () -> Unit,
    /** null = volver al precio de lista. */
    onGuardar: (Double?) -> Unit,
) {
    val moneda = pe.saniape.app.ui.monedaUI()
    val c = Sania.colors
    var texto by remember { mutableStateOf(dinero(actual)) }
    val valor = texto.replace(',', '.').toDoubleOrNull()
    DialogoForm(
        titulo = nombre,
        subtitulo = if (porPieza) "Precio por pieza" else "Precio",
        textoAccion = "Guardar",
        accionHabilitada = valor != null && valor >= 0,
        onCancelar = onCerrar,
        onAccion = { onGuardar(valor) },
    ) {
        androidx.compose.material3.OutlinedTextField(
            value = texto,
            onValueChange = { texto = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
            prefix = { Text("${simboloMoneda(moneda)} ") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
            ),
            colors = pe.saniape.app.ui.clinica.pacientes.coloresCampoForm(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Precio de lista: ${simboloMoneda(moneda)} ${dinero(base)}",
            color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
        )
        if (actual != base) {
            Text(
                "Volver al precio de lista", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onGuardar(null) }.padding(top = 8.dp),
            )
        }
    }
}

/** 180.0 → "180.00". Sin depender de la configuración regional del teléfono. */
private fun dinero(v: Double): String {
    val cent = kotlin.math.round(v * 100).toLong()
    val ent = cent / 100
    val dec = (cent % 100).toString().padStart(2, '0')
    return "$ent.$dec"
}
