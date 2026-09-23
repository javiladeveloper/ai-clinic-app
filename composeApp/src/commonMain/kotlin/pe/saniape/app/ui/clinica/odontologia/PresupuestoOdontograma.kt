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
import kotlinx.coroutines.launch
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
) {
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

    LaunchedEffect(Unit) { todos = OdontogramaRepo.procedimientos() }

    // Solo lo que todavía no tiene tratamiento: lo ya presupuestado no se
    // vuelve a ofrecer (crearía un segundo tratamiento por la misma caries).
    val libres = remember(hallazgos) { hallazgos.filter { it.tratamientoId == null } }
    val (lineasBase, sinServicio) = remember(libres, catalogo, todos) {
        agruparPresupuesto(libres, catalogo, todos)
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
    var marcadas by remember(idsServicios) { mutableStateOf(idsServicios.toSet()) }
    val procPorId = remember(todos) { todos.associateBy { it.id } }

    val total = lineas.filter { it.procedimientoId in marcadas }.sumOf { it.subtotal } +
        extras.sumOf { precioExtra(it) }

    Column(
        Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .padding(12.dp),
    ) {
        Text("💰 Presupuesto", color = c.texto, fontWeight = FontWeight.Bold, fontSize = 15.sp)

        if (lineas.isEmpty() && sinServicio.isEmpty() && extras.isEmpty()) {
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
                    "S/ ${dinero(l.subtotal)}",
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
                    "S/ ${dinero(precioExtra(e))}",
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
        if (sinServicio.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Sin servicio asignado", color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            val porId = catalogo.associateBy { it.id }
            sinServicio.map { it.hallazgoId }.distinct().mapNotNull { porId[it] }.forEach { hal ->
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
                Text("S/ ${dinero(total)}", color = c.texto, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            val cuantos = marcadas.size + extras.size
            if (!soloLectura) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
                        .background(if (cuantos > 0 && !creando) c.navy else c.borde)
                        .clickable(enabled = cuantos > 0 && !creando) {
                            creando = true
                            val planes: List<PlanTratamiento> =
                                lineas.filter { it.procedimientoId in marcadas }
                                    .map { planTratamiento(it, procPorId[it.procedimientoId]) }
                            scope.launch {
                                val fallidos = crearTodo(
                                    pacienteId, citaId, planes,
                                    extras.map { it.copy(precio = precioExtra(it)) },
                                )
                                creando = false
                                if (fallidos == 0) {
                                    Toaster.exito("Tratamiento(s) creados")
                                    extras = emptyList()
                                    precios = emptyMap()
                                    onCambio()
                                } else {
                                    Toaster.error("$fallidos no se pudieron crear. Revisa y reintenta.")
                                    onCambio()
                                }
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
 * fallaron: si fallan algunos, los que sí se crearon quedan (no se deshacen),
 * y como los hallazgos ya atados dejan de aparecer, reintentar solo toca lo que
 * faltó.
 */
private suspend fun crearTodo(
    pacienteId: String,
    citaId: String?,
    planes: List<PlanTratamiento>,
    extras: List<ProcedimientoRef>,
): Int {
    var fallidos = 0
    for (p in planes) {
        val ok = PacientesRepo.crearTratamiento(
            pacienteId = pacienteId, procedimientoId = p.procedimientoId, terapeutaId = null,
            modalidad = p.modalidad, totalSesiones = p.totalSesiones, precioPaquete = p.precioPaquete,
            precioPorSesion = null, precioAcordado = p.precioAcordado, diagnostico = p.diagnostico,
            citaOrigenId = citaId, cantidadUnidades = p.cantidadUnidades, precioUnitario = p.precioUnitario,
            hallazgoIds = p.hallazgoIds,
        )
        if (!ok) fallidos++
    }
    for (e in extras) {
        val porSesiones = e.modoCobro == "sesiones"
        val ok = PacientesRepo.crearTratamiento(
            pacienteId = pacienteId, procedimientoId = e.id, terapeutaId = null,
            modalidad = if (porSesiones) "Sesiones" else "Sesión suelta",
            totalSesiones = if (porSesiones) (e.tarifarios.firstOrNull()?.cantidadSesiones ?: 1) else 1,
            precioPaquete = if (porSesiones) e.precio else null,
            precioPorSesion = null, precioAcordado = e.precio,
            diagnostico = "${e.nombre} (Tratamiento complementario)", citaOrigenId = citaId,
        )
        if (!ok) fallidos++
    }
    return fallidos
}

@Composable
private fun ElegirServicio(
    titulo: String,
    subtitulo: String,
    servicios: List<ProcedimientoRef>,
    onCerrar: () -> Unit,
    onElegir: (ProcedimientoRef) -> Unit,
) {
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
                    Text("S/ ${dinero(s.precio)}", color = c.textoSuave, fontSize = 12.sp)
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
            prefix = { Text("S/ ") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
            ),
            colors = pe.saniape.app.ui.clinica.pacientes.coloresCampoForm(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Precio de lista: S/ ${dinero(base)}",
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
