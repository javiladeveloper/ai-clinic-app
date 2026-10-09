package pe.saniape.app.ui.clinica.ajustes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import pe.saniape.app.data.staff.AgendaRepo
import pe.saniape.app.data.staff.AsignacionEvento
import pe.saniape.app.data.staff.CalendarioRepo
import pe.saniape.app.data.staff.Cabecera
import pe.saniape.app.data.staff.ConfigFuenteCal
import pe.saniape.app.data.staff.DecisionesCal
import pe.saniape.app.data.staff.EventoVista
import pe.saniape.app.data.staff.FilaPaciente
import pe.saniape.app.data.staff.PestanaEventos
import pe.saniape.app.data.staff.RefNombre
import pe.saniape.app.data.staff.ResultadoImportacion
import pe.saniape.app.data.staff.alternarMarca
import pe.saniape.app.data.staff.avisoCupoCruces
import pe.saniape.app.data.staff.avisoPorRevisar
import pe.saniape.app.data.staff.citasAImportar
import pe.saniape.app.data.staff.conPaciente
import pe.saniape.app.data.staff.corregirPaciente
import pe.saniape.app.data.staff.direccionDe
import pe.saniape.app.data.staff.duracionTxt
import pe.saniape.app.data.staff.esCitaMarcada
import pe.saniape.app.data.staff.FalloImportar
import pe.saniape.app.data.staff.falloImportar
import pe.saniape.app.data.staff.soloCaracteresCelular
import pe.saniape.app.data.staff.esEditable
import pe.saniape.app.data.staff.estadoCabecera
import pe.saniape.app.data.staff.eventosDePestana
import pe.saniape.app.data.staff.fechaCortaCal
import pe.saniape.app.data.staff.filasPacientes
import pe.saniape.app.data.staff.hexColor
import pe.saniape.app.data.staff.marcarTodos
import pe.saniape.app.data.staff.nombreColor
import pe.saniape.app.data.staff.ofreceElegirPaciente
import pe.saniape.app.data.staff.opcionesFicha
import pe.saniape.app.data.staff.otrosPorParecido
import pe.saniape.app.data.staff.puedeImportar
import pe.saniape.app.data.staff.quePasa
import pe.saniape.app.data.staff.resultadoImportacionDe
import pe.saniape.app.data.staff.telefonoDe
import pe.saniape.app.data.staff.textoBotonImportar
import pe.saniape.app.data.staff.textoElegirPaciente
import pe.saniape.app.data.staff.textoPie
import pe.saniape.app.data.staff.textoPorRevisarResultado
import pe.saniape.app.data.staff.textoSinCupoResultado
import pe.saniape.app.data.staff.toastImportacion
import pe.saniape.app.data.staff.usarActual
import pe.saniape.app.ui.AlertaConTeclado
import pe.saniape.app.ui.Gestion
import pe.saniape.app.ui.ManejarAtras
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.conIndicador
import pe.saniape.app.ui.theme.Sania

/**
 * Lo que la persona va marcando en la vista previa, fuera de la pantalla: lo
 * guarda la sección (no se pierde al actualizar; las rotaciones no recrean la
 * actividad: ver configChanges en el AndroidManifest).
 */
@androidx.compose.runtime.Stable
internal class TrabajoVista(config: ConfigFuenteCal, decisiones: DecisionesCal) {
    var config by mutableStateOf(config)
    var dec by mutableStateOf(decisiones)
    var pestana by mutableStateOf(PestanaEventos.REVISAR)
    val lista = androidx.compose.foundation.lazy.LazyListState()
}

/**
 * Revisión ANTES de importar un calendario de Google — gemelo nativo de
 * `components/calendario/VistaPreviaCalendario.tsx`. La persona decide (qué es
 * cita, de qué paciente es cada evento dudoso, fichas nuevas o existentes,
 * uniones "misma persona", colores, pasadas, servicio) y nada se guarda hasta
 * "Importar": el servidor recalcula todo con estas decisiones.
 *
 * Una sola LazyColumn (cientos de eventos y pacientes sin trabarse); los
 * selectores largos ("Es la misma persona que…") se arman al abrirlos, por fila.
 */
@Composable
internal fun VistaPreviaCalendario(
    abierta: VistaAbierta,
    procedimientos: List<Pair<String, String>>,
    onActualizar: suspend (ConfigFuenteCal, DecisionesCal) -> Boolean,
    onImportado: () -> Unit,
    onReconectar: (String) -> Unit,
    onCerrar: () -> Unit,
    /** El plan sincroniza (Premium/Plus). Básico = una importación: cambian textos y el botón con 0 citas. */
    sincroniza: Boolean = true,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val datos = abierta.datos
    // Lo que la persona marca vive en el holder de la sección (sobrevive a
    // "Actualizar vista previa", que conserva pestaña y desplazamiento).
    val t = abierta.trabajo
    var config by t::config
    var dec by t::dec
    var pestana by t::pestana
    var trabajando by remember { mutableStateOf<String?>(null) }
    var resultado by remember { mutableStateOf<ResultadoImportacion?>(null) }
    var editando by remember { mutableStateOf<FilaPaciente?>(null) }
    var eligiendoFicha by remember { mutableStateOf<FilaPaciente?>(null) }
    var uniendo by remember { mutableStateOf<FilaPaciente?>(null) }
    var asignando by remember { mutableStateOf<EventoVista?>(null) }
    var eligiendoServicio by remember { mutableStateOf(false) }
    var eligiendoFecha by remember { mutableStateOf(false) }

    val tocado = dec != abierta.decisiones || config != abierta.config
    val r = datos.resumen
    val eventos = remember(datos.items, pestana) { eventosDePestana(datos.items, pestana) }
    val cabecera = estadoCabecera(eventos, dec)
    val filas = remember(datos.pacientes, dec.pacientes) { filasPacientes(datos.pacientes, dec.pacientes) }
    val nombresFilas = remember(filas, dec.pacientes) { filas.map { it.p.clave to (dec.pacientes[it.p.clave]?.nombre ?: it.p.nombre) } }
    val aCrear = remember(datos, dec) { citasAImportar(datos, dec) }

    fun actualizar() {
        if (trabajando != null) return
        trabajando = "actualizar"
        scope.launch { try { onActualizar(config, dec) } finally { trabajando = null } }
    }

    fun importar() {
        if (trabajando != null) return
        trabajando = "importar"
        scope.launch {
            try {
                val res = conIndicador(Gestion.GUARDANDO) { CalendarioRepo.importar(abierta.fuente.id, config, dec, abierta.soloPendientes) }
                val o = res.cuerpo
                if (res.registrada && o != null) {
                    val ri = resultadoImportacionDe(o)
                    resultado = ri
                    Toaster.exito(toastImportacion(ri))
                    onImportado()
                } else when (falloImportar(res.codigo, res.rechazo?.status ?: 0)) {
                    // Sin respuesta a tiempo u ocupado: el servidor guardó las decisiones y
                    // puede seguir importando. Sin reintento automático (daría OCUPADO).
                    FalloImportar.POSIBLE_EN_CURSO -> {
                        Toaster.info("Puede que siga importando en el servidor: revisa en unos minutos")
                        onImportado()
                        onCerrar()
                    }
                    FalloImportar.RECONECTAR -> onReconectar(res.rechazo?.error ?: "Vuelve a conectar la cuenta de Google.")
                    FalloImportar.OTRO -> Toaster.error(res.rechazo?.error ?: "No se pudo importar")
                }
            } finally { trabajando = null }
        }
    }

    resultado?.let { res ->
        PantallaResultado(res, config.recordatoriosPacientes == true, sincroniza, onCerrar)
        return
    }

    // El atrás del sistema cierra la vista previa (no mientras importa).
    ManejarAtras(activo = true) { if (trabajando != "importar") onCerrar() }

    // ── Diálogos ──
    if (eligiendoServicio) DialogoListaLarga(
        titulo = "Servicio de las citas",
        opciones = listOf("" to "Sin servicio (se elige al atender)") + procedimientos,
        onElegir = { config = config.copy(procedimientoId = it.ifEmpty { null }); eligiendoServicio = false },
        onCerrar = { eligiendoServicio = false },
    )
    if (eligiendoFecha) SelectorFechaDesde(
        inicial = config.desde, hoy = datos.hoy,
        onElegir = { config = config.copy(desde = it); eligiendoFecha = false },
        onCerrar = { eligiendoFecha = false },
    )
    eligiendoFicha?.let { f ->
        DialogoListaLarga(
            titulo = "Ficha de ${dec.pacientes[f.p.clave]?.nombre ?: f.p.nombre}",
            opciones = opcionesFicha(f.p),
            onElegir = { id -> dec = dec.conPaciente(f.p.clave) { it.copy(usar = id.ifEmpty { null }) }; eligiendoFicha = null },
            onCerrar = { eligiendoFicha = null },
        )
    }
    uniendo?.let { f ->
        // Solo al abrir: con cientos de pacientes, ordenar en cada fila sería pesado.
        val otros = remember(f.p.clave, nombresFilas) { otrosPorParecido(f.p.clave, f.p.nombre, nombresFilas) }
        DialogoListaLarga(
            titulo = "Es la misma persona que…",
            opciones = otros,
            onElegir = { k -> dec = dec.conPaciente(f.p.clave) { it.copy(unirA = k) }; uniendo = null },
            onCerrar = { uniendo = null },
        )
    }
    editando?.let { f -> DialogoCorregirPaciente(f, dec, onListo = { dec = it; editando = null }, onCancelar = { editando = null }) }
    asignando?.let { ev ->
        DialogoAsignarPaciente(ev, onElegir = { a -> dec = dec.copy(porEvento = dec.porEvento + (ev.id to a)); asignando = null }, onCancelar = { asignando = null })
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            CabeceraAjustes("← Agenda de Google Calendar", "Revisa antes de importar", { if (trabajando != "importar") onCerrar() })
            LazyColumn(
                Modifier.fillMaxSize(),
                state = t.lista,
                contentPadding = PaddingValues(Sania.dim.lg),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item("resumen") {
                    Tarjeta {
                        Ayuda(
                            (datos.nombreCalendario ?: abierta.fuente.nombre) +
                                (abierta.profesional?.let { " → $it" } ?: " → sin profesional asignado") +
                                " · nada se guarda hasta que pulses “Importar”.",
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Dato(r.crearFuturas, "citas próximas", Modifier.weight(1f))
                            Dato(r.crearPasadas, if (config.pasadas == "omitir") "pasadas" else "pasadas (atendidas)", Modifier.weight(1f))
                            Dato(r.pacientesNuevos, "fichas nuevas", Modifier.weight(1f),
                                sub = if (r.pacientesExistentes > 0) "${r.pacientesExistentes} ya registradas" else null)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Dato(r.revisar, "por revisar", Modifier.weight(1f), alerta = r.revisar > 0)
                            Dato(r.ignorar, "no son cita", Modifier.weight(1f))
                        }
                        if (r.revisar > 0) Aviso(avisoPorRevisar(r.revisar))
                        avisoCupoCruces(r.sinCupo, r.cruces)?.let { Aviso(it) }
                        if (datos.avisos.isNotEmpty()) Ayuda(datos.avisos.joinToString(" · "))
                    }
                }

                item("ajustes") {
                    Tarjeta("Ajustes de la importación") {
                        Column {
                            Selector("Traer desde", config.desde?.let { fechaCortaCal(it) } ?: "", placeholder = "Los últimos 2 años") { eligiendoFecha = true }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Ayuda("Vacío = los últimos 2 años. Hacia adelante: 1 año.", )
                                if (config.desde != null) {
                                    Spacer(Modifier.width(8.dp))
                                    Enlace("Quitar fecha") { config = config.copy(desde = null) }
                                }
                            }
                        }
                        Column {
                            EtqForm("Citas pasadas")
                            ChipsEleccion(
                                listOf("atendida" to "Importarlas como atendidas (historial, sin cobro)", "omitir" to "No importarlas"),
                                elegido = config.pasadas ?: "atendida",
                            ) { config = config.copy(pasadas = it) }
                        }
                        Selector("Servicio de las citas",
                            procedimientos.find { it.first == config.procedimientoId }?.second ?: "",
                            placeholder = "Sin servicio (se elige al atender)") { eligiendoServicio = true }
                        FilaInterruptor(
                            "Enviar recordatorios a los pacientes",
                            "WhatsApp la mañana de la cita si la clínica tiene WhatsApp, y notificaciones si usan la app del paciente. Apagado: a estos pacientes no les llega nada de Sania. Al profesional sí le llegan sus avisos de agenda.",
                            activo = config.recordatoriosPacientes == true,
                        ) { config = config.copy(recordatoriosPacientes = it) }
                        if (datos.colores.isNotEmpty()) {
                            Ayuda("¿Qué significa cada color de tu calendario?")
                            datos.colores.forEach { col ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Punto(hexColor(col.clave), 12.dp)
                                        Spacer(Modifier.width(6.dp))
                                        Text("${nombreColor(col.clave)} (${col.eventos})", color = c.texto, fontSize = 13.sp)
                                    }
                                    ChipsEleccion(
                                        listOf("Confirmada" to "Confirmada", "Pendiente" to "Sin confirmar", "ignorar" to "No importar"),
                                        elegido = config.mapaColores[col.clave] ?: "Confirmada",
                                    ) { config = config.copy(mapaColores = config.mapaColores + (col.clave to it)) }
                                }
                            }
                        }
                        Boton(if (trabajando == "actualizar") "Recalculando…" else "↻ Actualizar vista previa",
                            primario = false, habilitado = trabajando == null) { actualizar() }
                        if (tocado) Ayuda("Cambiaste algo: actualiza para ver los números (al importar se aplica igual).", c.pend)
                    }
                }

                if (datos.pacientes.isNotEmpty()) {
                    item("pac-cab") {
                        Column(Modifier.padding(top = 6.dp)) {
                            Text("Pacientes detectados (${filas.size})", color = c.texto, fontSize = Sania.txt.seccion, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Ayuda("Se juntan los eventos con el mismo nombre completo (y el mismo celular si lo traen). Se usa una ficha que ya existe solo si hay UNA con ese nombre; si no, tú decides. En las fichas nuevas puedes corregir el nombre, el celular y la dirección. Si una persona aparece dos veces escrita distinto (“Jenny” y “Jeny”), únela con “Es la misma persona que…”.")
                        }
                    }
                    items(filas, key = { "p:" + it.p.clave }) { f ->
                        FilaPacienteDetectado(
                            f = f, dec = dec, hayOtros = filas.size > 1,
                            onEditar = { editando = f },
                            onFicha = { eligiendoFicha = f },
                            onUnir = { uniendo = f },
                            onSeparar = { clave -> dec = dec.conPaciente(clave) { it.copy(unirA = null) } },
                        )
                    }
                }

                item("ev-cab") {
                    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Eventos", color = c.texto, fontSize = Sania.txt.seccion, fontWeight = FontWeight.Bold)
                        ChipsEleccion(PestanaEventos.entries.map { it.name to it.titulo }, elegido = pestana.name) { pestana = PestanaEventos.valueOf(it) }
                        Ayuda("Muestra hasta ${r.mostrados.revisar} que conviene mirar (dudosos, no son cita o fuera de horario), ${r.mostrados.futuras} próximas y ${r.mostrados.pasadas} pasadas de ${r.eventos} eventos. Los totales de arriba son de todos.")
                        if (eventos.isNotEmpty()) Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                                .clickable(enabled = cabecera != Cabecera.VACIO) { dec = marcarTodos(eventos, cabecera != Cabecera.TODOS, dec) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TriStateCheckbox(
                                state = when (cabecera) { Cabecera.TODOS -> ToggleableState.On; Cabecera.MEZCLA -> ToggleableState.Indeterminate; else -> ToggleableState.Off },
                                enabled = cabecera != Cabecera.VACIO,
                                onClick = { dec = marcarTodos(eventos, cabecera != Cabecera.TODOS, dec) },
                                colors = CheckboxDefaults.colors(checkedColor = c.navy),
                            )
                            Text(
                                if (cabecera == Cabecera.TODOS) "Desmarcar todos los de esta pestaña" else "Marcar todos los de esta pestaña como cita",
                                color = if (cabecera == Cabecera.VACIO) c.textoSuave else c.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                if (eventos.isEmpty()) item("ev-vacio") {
                    Ayuda(if (pestana == PestanaEventos.REVISAR) "Nada que revisar: todo se ve como cita normal." else "Sin eventos.")
                } else items(eventos, key = { "e:" + it.id }) { ev ->
                    FilaEvento(
                        ev = ev, dec = dec, config = config,
                        onAlternar = { dec = alternarMarca(ev, dec) },
                        onElegir = { asignando = ev },
                        onQuitarAsignacion = { dec = dec.copy(porEvento = dec.porEvento - ev.id) },
                    )
                }

                // Al final de la lista, no flotando: tapaba los eventos (como la web).
                item("pie") {
                    Tarjeta {
                        Text(textoPie(aCrear, r.revisar, sincroniza, abierta.soloPendientes), color = c.texto, fontSize = 13.5.sp, lineHeight = 18.sp)
                        Ayuda("Sin aviso de “nueva cita” por cada una. El profesional recibe sus avisos de agenda; los pacientes, solo si encendiste los recordatorios.")
                        if (tocado) Ayuda("Cambiaste algo: actualiza para ver los números (al importar se aplica igual).", c.pend)
                        Boton(
                            textoBotonImportar(aCrear, esGoogle = true, importando = trabajando == "importar", sincroniza = sincroniza, reintento = abierta.soloPendientes),
                            habilitado = puedeImportar(aCrear, esGoogle = true, trabajando = trabajando != null, sincroniza = sincroniza, reintento = abierta.soloPendientes),
                        ) { importar() }
                    }
                    Spacer(Modifier.height(Sania.dim.xxl))
                }
            }
        }
    }
}

// ── Piezas ───────────────────────────────────────────────────────────────────

@Composable
private fun Dato(n: Int, t: String, modifier: Modifier, sub: String? = null, alerta: Boolean = false) {
    val c = Sania.colors
    Column(
        modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(if (alerta) c.pendBg else c.chipBg).padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$n", color = c.navy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(t, color = c.textoSuave, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 13.sp)
        sub?.let { Text(it, color = c.textoSuave, fontSize = 10.sp, textAlign = TextAlign.Center) }
    }
}

/** Punto de color (dibujado: sin layouts intrínsecos). */
@Composable
private fun Punto(hex: String, tam: androidx.compose.ui.unit.Dp) {
    val color = colorDeHex(hex) ?: Color(0xFF9AA3C7)
    Spacer(Modifier.size(tam).drawBehind { drawCircle(color) })
}

@Composable
private fun Enlace(texto: String, color: Color = Sania.colors.navy, onClick: () -> Unit) {
    Text(
        texto, color = color, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(vertical = 4.dp, horizontal = 2.dp),
    )
}

@Composable
private fun TarjetaFila(contenido: @Composable ColumnScope.() -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.md.dp)
    Column(
        Modifier.fillMaxWidth().clip(forma).background(c.superficie).border(1.dp, c.borde, forma).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp), content = contenido,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaPacienteDetectado(
    f: FilaPaciente, dec: DecisionesCal, hayOtros: Boolean,
    onEditar: () -> Unit, onFicha: () -> Unit, onUnir: () -> Unit, onSeparar: (String) -> Unit,
) {
    val c = Sania.colors
    val p = f.p
    val d = dec.pacientes[p.clave]
    val usar = usarActual(p, d)
    val usaExistente = usar.isNotEmpty() && usar != "nuevo"
    val opciones = remember(p) { opcionesFicha(p) }
    TarjetaFila {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(d?.nombre ?: p.nombre, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${f.eventos} ${if (f.eventos == 1) "cita" else "citas"}", color = c.textoSuave, fontSize = 12.sp)
        }
        if (p.revisar && d?.usar.isNullOrEmpty()) p.motivoRevisar?.let { Text(it, color = c.pend, fontSize = 12.sp) }
        if (p.existente?.inactivo == true && d?.usar.isNullOrEmpty()) {
            Text("Hay una ficha con este nombre dada de baja: se crea una nueva salvo que elijas esa.", color = c.pend, fontSize = 12.sp)
        }
        f.unidos.forEach { u ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("+ ${u.nombre} (${u.eventos} ${if (u.eventos == 1) "cita" else "citas"}): misma persona",
                    color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                Enlace("Separar") { onSeparar(u.clave) }
            }
        }
        if (usaExistente) Text("Ficha que ya existe: su celular y dirección no se cambian aquí.", color = c.textoSuave, fontSize = 12.sp)
        else {
            val tel = telefonoDe(p, d)
            val dir = direccionDe(p, d)
            Text("📱 ${tel.ifEmpty { "Sin celular" }}  ·  🏠 ${dir.ifEmpty { "Sin dirección" }}", color = c.textoSuave, fontSize = 12.sp)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (opciones.isNotEmpty()) {
                val etq = opciones.find { it.first == usar }?.second ?: "Elegir…"
                Enlace("Ficha: $etq ▾", if (usar.isEmpty()) c.pend else c.navy) { onFicha() }
            } else Text("Nueva", color = c.ok, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
            Enlace("✎ Corregir datos") { onEditar() }
            if (hayOtros) Enlace("Es la misma persona que…", c.textoSuave) { onUnir() }
        }
    }
}

@Composable
private fun FilaEvento(
    ev: EventoVista, dec: DecisionesCal, config: ConfigFuenteCal,
    onAlternar: () -> Unit, onElegir: () -> Unit, onQuitarAsignacion: () -> Unit,
) {
    val c = Sania.colors
    val esCita = esCitaMarcada(ev, dec)
    val asignado = dec.porEvento[ev.id]
    TarjetaFila {
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(
                checked = esCita, enabled = esEditable(ev), onCheckedChange = { onAlternar() },
                colors = CheckboxDefaults.colors(checkedColor = c.navy),
            )
            Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Punto(hexColor(ev.color), 10.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(ev.titulo.ifBlank { "(sin título)" }, color = c.texto, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                }
                if (ev.nombre.isNotBlank() && ev.nombre != ev.titulo) Text("→ ${ev.nombre}", color = c.textoSuave, fontSize = 12.sp)
                Text(
                    fechaCortaCal(ev.fecha) + " · " + if (ev.todoElDia) "Todo el día" else "${ev.hora} · ${duracionTxt(ev.duracion)}",
                    color = c.textoSuave, fontSize = 12.sp,
                )
                val extras = listOfNotNull(
                    ev.telefono?.let { "📱 $it" },
                    if (ev.domicilio) "🏠 ${ev.direccion ?: "a domicilio"}" else null,
                    if (ev.recurrente) "🔁 se repite" else null,
                )
                if (extras.isNotEmpty()) Text(extras.joinToString("  "), color = c.textoSuave, fontSize = 11.5.sp)
                if (asignado != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✓ Paciente: ${if (asignado.pacienteId != null) asignado.etiqueta ?: "ficha elegida" else asignado.nombre ?: ""}",
                        color = c.ok, fontSize = 12.sp, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                    Enlace("quitar", c.textoSuave) { onQuitarAsignacion() }
                }
                if (ofreceElegirPaciente(ev, dec)) Enlace(textoElegirPaciente(ev, dec)) { onElegir() }
                Text(quePasa(ev, dec, config), color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (ev.sinCupo && esCita) Text("⛔ fuera de horario o sin cupo", color = c.error, fontSize = 12.sp)
                if (ev.cruce && esCita) Text("⚠ se cruza con otra", color = c.pend, fontSize = 12.sp)
                ev.motivo?.let { Text(it, color = c.textoSuave, fontSize = 12.sp) }
            }
        }
    }
}

/**
 * Lista larga para elegir (profesional, servicio, ficha, "misma persona"):
 * LazyColumn con altura acotada y buscador si es larga. Con el teclado abierto
 * el botón sube a la cabecera (AlertaConTeclado).
 */
@Composable
internal fun DialogoListaLarga(
    titulo: String,
    opciones: List<Pair<String, String>>,
    onElegir: (String) -> Unit,
    onCerrar: () -> Unit,
) {
    val c = Sania.colors
    var filtro by remember { mutableStateOf("") }
    val visibles = remember(filtro, opciones) {
        val f = pe.saniape.app.data.staff.normalizarNombre(filtro)
        if (f.isEmpty()) opciones else opciones.filter { pe.saniape.app.data.staff.normalizarNombre(it.second).contains(f) }
    }
    AlertaConTeclado(
        onDismissRequest = onCerrar,
        title = { Text(titulo, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column {
                if (opciones.size > 8) {
                    Campo(null, filtro, { filtro = it }, placeholder = "Buscar…")
                    Spacer(Modifier.height(8.dp))
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    items(visibles) { (valor, etiqueta) ->
                        Text(etiqueta, color = c.texto, fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().clickable { onElegir(valor) }.padding(vertical = 11.dp))
                    }
                    if (visibles.isEmpty()) item("vacio") { Text("Sin resultados", color = c.textoSuave, fontSize = 13.sp) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}

/** Corregir nombre, celular y dirección (solo fichas nuevas; el nombre siempre). */
@Composable
private fun DialogoCorregirPaciente(f: FilaPaciente, dec: DecisionesCal, onListo: (DecisionesCal) -> Unit, onCancelar: () -> Unit) {
    val c = Sania.colors
    val p = f.p
    val d = dec.pacientes[p.clave]
    val usar = usarActual(p, d)
    val usaExistente = usar.isNotEmpty() && usar != "nuevo"
    var nombre by remember { mutableStateOf(d?.nombre ?: p.nombre) }
    var tel by remember { mutableStateOf(telefonoDe(p, d)) }
    var dir by remember { mutableStateOf(direccionDe(p, d)) }
    var ficha by remember { mutableStateOf<Pair<String?, String?>?>(null) }
    LaunchedEffect(usar) { if (usaExistente) ficha = CalendarioRepo.datosFicha(usar) }
    DialogoForm(
        titulo = "Paciente detectado",
        subtitulo = "${f.eventos} ${if (f.eventos == 1) "cita" else "citas"}",
        textoAccion = "Listo",
        accionHabilitada = nombre.isNotBlank(),
        onCancelar = onCancelar,
        onAccion = {
            onListo(
                if (usaExistente) corregirPaciente(p, dec, nombre, telefonoDe(p, d), direccionDe(p, d))
                else corregirPaciente(p, dec, nombre, tel, dir),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Campo("Nombre", nombre, { nombre = it }, max = 120)
            if (usaExistente) {
                Ayuda("Es una ficha que ya existe: su celular y su dirección no se cambian aquí.")
                Campo("Celular", ficha?.first ?: "", {}, placeholder = "—", habilitado = false)
                Campo("Dirección", ficha?.second ?: "", {}, placeholder = "—", habilitado = false)
            } else {
                Campo("Celular", tel, { tel = soloCaracteresCelular(it) }, placeholder = "Sin celular", teclado = KeyboardType.Phone, max = 20)
                Campo("Dirección", dir, { dir = it }, placeholder = "Sin dirección", max = 200)
                Ayuda("Dirección para la atención a domicilio. Vacío = sin dato.", c.textoSuave)
            }
        }
    }
}

/** Elegir la ficha de un evento, o escribir el nombre completo (ficha nueva). */
@Composable
private fun DialogoAsignarPaciente(ev: EventoVista, onElegir: (AsignacionEvento) -> Unit, onCancelar: () -> Unit) {
    val c = Sania.colors
    var query by remember { mutableStateOf("") }
    var elegido by remember { mutableStateOf<RefNombre?>(null) }
    var nombre by remember { mutableStateOf("") }
    var resultados by remember { mutableStateOf<List<RefNombre>>(emptyList()) }
    var buscando by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { resultados = emptyList(); buscando = false; return@LaunchedEffect }
        kotlinx.coroutines.delay(300)   // pausa: no una consulta por tecla
        buscando = true
        resultados = runCatching { AgendaRepo.buscarPacientes(q) }.getOrDefault(emptyList()).take(40)
        buscando = false
    }
    val palabras = nombre.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
    DialogoForm(
        titulo = "Elegir paciente",
        subtitulo = "${ev.titulo.ifBlank { "(sin título)" }} · ${fechaCortaCal(ev.fecha)} ${if (ev.todoElDia) "" else ev.hora}".trim(),
        textoAccion = "Usar",
        accionHabilitada = elegido != null || palabras >= 2,
        onCancelar = onCancelar,
        onAccion = {
            val e = elegido
            onElegir(if (e != null) AsignacionEvento(pacienteId = e.id, etiqueta = e.nombre) else AsignacionEvento(nombre = nombre.trim().replace(Regex("\\s+"), " ")))
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            elegido?.let { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✓ ${e.nombre}", color = c.ok, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Enlace("cambiar", c.textoSuave) { elegido = null }
                }
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Buscar ficha por nombre o DNI", color = c.textoSuave, fontSize = 13.sp) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = pe.saniape.app.ui.clinica.pacientes.coloresCampoForm(),
            )
            when {
                query.trim().length < 2 -> {}
                buscando && resultados.isEmpty() -> Ayuda("Buscando…")
                resultados.isEmpty() -> Ayuda("Sin coincidencias.")
                else -> resultados.forEach { item ->
                    val sel = elegido?.id == item.id
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .background(if (sel) c.chipBg else Color.Transparent)
                            .clickable { elegido = item; query = "" }.padding(horizontal = 8.dp, vertical = 9.dp),
                    ) {
                        Text(item.nombre, color = c.texto, fontSize = 14.sp)
                        item.dni?.takeIf { it.isNotBlank() }?.let { Text("DNI $it", color = c.textoSuave, fontSize = 11.sp) }
                    }
                }
            }
            Campo(null, nombre, { nombre = it }, placeholder = "…o escribe el nombre completo (ficha nueva)", max = 120)
        }
    }
}

/** "Traer desde": una fecha hasta hoy (de la clínica). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorFechaDesde(inicial: String?, hoy: String, onElegir: (String) -> Unit, onCerrar: () -> Unit) {
    val c = Sania.colors
    fun aMs(iso: String?): Long? = iso?.let { runCatching { LocalDate.parse(it).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() }.getOrNull() }
    fun aIso(ms: Long): String = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date.toString()
    val tope = hoy.ifBlank { null }
    val estado = rememberDatePickerState(
        initialSelectedDateMillis = aMs(inicial) ?: aMs(tope),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = tope == null || aIso(utcTimeMillis) <= tope
        },
    )
    DatePickerDialog(
        onDismissRequest = onCerrar,
        confirmButton = {
            TextButton(onClick = { estado.selectedDateMillis?.let { onElegir(aIso(it)) } ?: onCerrar() }) { Text("Aceptar", color = c.navy) }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar", color = c.textoSuave) } },
    ) { DatePicker(state = estado) }
}

/** Lo que pasó al importar (como la tarjeta de resultado de la web). */
@Composable
private fun PantallaResultado(res: ResultadoImportacion, recordatorios: Boolean, sincroniza: Boolean, onCerrar: () -> Unit) {
    val c = Sania.colors
    SubPantalla("Agenda de Google Calendar", onCerrar, volver = "← Agenda de Google Calendar") {
        Tarjeta(if (res.enCurso) "⏳ Importación en curso" else "✓ Importación terminada") {
            Text(
                "${res.creadas} citas creadas" + (if (res.actualizadas > 0) " · ${res.actualizadas} actualizadas" else "") +
                    (if (res.canceladas > 0) " · ${res.canceladas} canceladas" else ""),
                color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            )
            Text("${res.pacientesCreados} fichas nuevas de paciente", color = c.texto, fontSize = 14.sp)
            if (res.porRevisar > 0) Text(textoPorRevisarResultado(res.porRevisar, sincroniza), color = c.texto, fontSize = 13.sp)
            if (res.enCurso) Text("El calendario es grande: el resto se importa solo en los próximos minutos (cada 10 min avanza).", color = c.texto, fontSize = 13.sp)
            Ayuda(
                "Al profesional le llegan sus avisos normales de estas citas (1 h antes y el resumen del día), pero no el de “nueva cita” por cada una." +
                    if (recordatorios) " Los pacientes reciben sus recordatorios normales: WhatsApp la mañana de la cita (si la clínica tiene WhatsApp) y, si usan la app del paciente, sus notificaciones."
                    else " A los pacientes no se les envía ningún recordatorio (ni WhatsApp ni notificaciones).",
            )
            if (res.sinCupo.isNotEmpty()) {
                val lista = res.sinCupo.take(12).joinToString("\n") { "· ${fechaCortaCal(it.fecha)} ${it.hora} — ${it.titulo}" } +
                    if (res.sinCupo.size > 12) "\n… y ${res.sinCupo.size - 12} más" else ""
                Aviso(
                    "${res.sinCupo.size} no entraron por cupo u horario. Revisa el horario del profesional (Equipo → Horario). " +
                        textoSinCupoResultado(sincroniza) + "\n\n$lista",
                )
            }
            if (res.errores.isNotEmpty()) Text("Algunas no se pudieron guardar: ${res.errores.take(2).joinToString(" · ")}", color = c.error, fontSize = 12.5.sp)
            Boton("Cerrar") { onCerrar() }
        }
    }
}
