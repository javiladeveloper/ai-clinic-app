package pe.saniape.app.ui.clinica.sani

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.DetalleLeadSani
import pe.saniape.app.data.staff.ETAPAS_SANI_DEFAULT
import pe.saniape.app.data.staff.EtapaSani
import pe.saniape.app.data.staff.LeadSani
import pe.saniape.app.data.staff.MOTIVO_NO_RESPONDER
import pe.saniape.app.data.staff.MensajeSani
import pe.saniape.app.data.staff.MiembroSani
import pe.saniape.app.data.staff.RespuestaBandeja
import pe.saniape.app.data.staff.ResultadosSaniMes
import pe.saniape.app.data.staff.SaniBandejaRepo
import pe.saniape.app.data.staff.SedeActiva
import pe.saniape.app.data.staff.TramoBandeja
import pe.saniape.app.data.staff.agregarPaginaVieja
import pe.saniape.app.data.staff.bloqueaRespuestaPorCanal
import pe.saniape.app.data.staff.enlaceWhatsAppSede
import pe.saniape.app.data.staff.esDelBot
import pe.saniape.app.data.staff.esHistorialSani
import pe.saniape.app.data.staff.etapaVisibleSani
import pe.saniape.app.data.staff.haceTexto
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.iconoCanalSani
import pe.saniape.app.data.staff.mesAnteriorSani
import pe.saniape.app.data.staff.mezclarPaginaReciente
import pe.saniape.app.data.staff.minutosDesde
import pe.saniape.app.data.staff.mostrarAvisoHistorial
import pe.saniape.app.data.staff.nombreCanalSani
import pe.saniape.app.data.staff.nombreEtapaSani
import pe.saniape.app.data.staff.nombreMesSani
import pe.saniape.app.data.staff.textoRestante
import pe.saniape.app.data.staff.ventanaSani
import pe.saniape.app.ui.DialogoConTeclado
import pe.saniape.app.ui.ManejarAtras
import pe.saniape.app.ui.Reanudacion
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.ui.theme.tocable

// LA BANDEJA DE SANI EN LA APP (gemelo de app/(app)/mensajes/page.tsx). En el
// teléfono, como la web en móvil: la lista, y al tocar una conversación se abre
// el chat; la ficha del contacto (columna 4 en escritorio) va en una hoja aparte.

private const val FILTRO_HISTORIAL = "historial"
private const val SONDEO_LISTA_MS = 20_000L
private const val SONDEO_HILO_MS = 8_000L
private const val MENSAJES_VISIBLES = 60

private enum class Puerta { CARGANDO, OK, INACTIVO, SIN_PERMISO, ERROR }

/** Color del punto de cada etapa con los tokens de Sania (PUNTO de la web, incluidos los de LeadAI). */
@Composable
private fun colorEtapa(color: String): Color {
    val c = Sania.colors
    return when (color) {
        "navy", "brasa" -> c.navy
        "amber", "tibio" -> c.pend
        "red", "calor" -> c.error
        "green", "ok" -> c.ok
        else -> c.textoSuave
    }
}

@Composable
private fun colorNivel(nivel: String): Color {
    val c = Sania.colors
    return when (nivel) { "caliente" -> c.error; "tibio" -> c.pend; else -> c.textoSuave }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaBandejaSani(
    ctx: ContextoStaff,
    onSalir: () -> Unit,
    /** Abre la ficha del paciente vinculado (null = quien mira no ve pacientes). */
    onAbrirPaciente: ((String) -> Unit)? = null,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val sedeEstado by SedeActiva.estado.collectAsState()
    // Multisede: las conversaciones de la sede activa (la de cada una sale del número
    // por el que escribió). Sin multisede, la petición de siempre.
    val sede = sedeEstado.sedeId.takeIf { sedeEstado.multiSede && it.isNotBlank() }

    var puerta by remember { mutableStateOf(Puerta.CARGANDO) }
    var mensajeError by remember { mutableStateOf("") }
    var leads by remember { mutableStateOf<List<LeadSani>>(emptyList()) }
    var etapas by remember { mutableStateOf(ETAPAS_SANI_DEFAULT) }
    var miembros by remember { mutableStateOf<List<MiembroSani>>(emptyList()) }
    var cupo by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var busqueda by remember { mutableStateOf("") }
    var filtro by remember { mutableStateOf("") }
    var selId by remember { mutableStateOf<String?>(null) }
    var cargandoHistorial by remember { mutableStateOf(false) }
    var cargado by remember { mutableStateOf(false) }
    var intentoCarga by remember { mutableIntStateOf(0) }
    // Cada carga lleva su número: si cambia la sede a mitad, la vieja se descarta.
    val gen = remember { intArrayOf(0) }
    val separa = remember { arrayOfNulls<Boolean>(1) }
    val historialListo = remember { booleanArrayOf(false) }
    // Lo ya abierto se pinta al instante (y se refresca por detrás), como la web.
    val cacheDetalles = remember { mutableStateMapOf<String, DetalleLeadSani>() }

    fun aplicarPuerta(r: RespuestaBandeja<*>): Boolean = when (r) {
        is RespuestaBandeja.Ok<*> -> { puerta = Puerta.OK; true }
        RespuestaBandeja.Inactivo -> { puerta = Puerta.INACTIVO; false }
        is RespuestaBandeja.SinPermiso -> { puerta = Puerta.SIN_PERMISO; false }
        is RespuestaBandeja.Error -> { if (leads.isEmpty() && puerta == Puerta.CARGANDO) { puerta = Puerta.ERROR; mensajeError = r.mensaje }; false }
    }

    // El resto de un tramo, de a 500 (menos páginas = mucho menos tiempo). false = se cortó.
    suspend fun seguirTramo(primero: String?, tramo: TramoBandeja, g: Int): Boolean {
        var cursor = primero
        var i = 1
        while (cursor != null && i < 31) {
            val r = SaniBandejaRepo.pagina(tramo, cursor, 500, sede)
            if (r !is RespuestaBandeja.Ok || g != gen[0]) return false
            leads = agregarPaginaVieja(leads, r.dato.items)
            cursor = r.dato.siguienteCursor
            i++
        }
        return true
    }

    suspend fun cargarHistorial(g: Int) {
        if (historialListo[0] || cargandoHistorial) return
        cargandoHistorial = true
        try {
            val r = SaniBandejaRepo.pagina(TramoBandeja.HISTORIAL, null, 500, sede)
            if (r !is RespuestaBandeja.Ok || g != gen[0]) return
            leads = agregarPaginaVieja(leads, r.dato.items)
            if (seguirTramo(r.dato.siguienteCursor, TramoBandeja.HISTORIAL, g) && g == gen[0]) historialListo[0] = true
        } finally {
            if (g == gen[0]) cargandoHistorial = false
        }
    }

    // SONDEO: solo la primera página (lo que se movió) mezclada con lo que ya hay.
    suspend fun refrescarRecientes() {
        val g = gen[0]
        val r = SaniBandejaRepo.pagina(if (separa[0] == false) TramoBandeja.TODO else TramoBandeja.VIVO, null, 100, sede)
        if (g != gen[0] || !aplicarPuerta(r)) return
        val p = (r as RespuestaBandeja.Ok).dato
        leads = mezclarPaginaReciente(leads, p.items)
        if (separa[0] == null) separa[0] = p.separaHistorial
        if (separa[0] == true && !historialListo[0]) scope.launch { cargarHistorial(g) }
    }

    // Etapas del negocio + equipo + cupo del plan: una vez al abrir.
    LaunchedEffect(Unit) {
        SaniBandejaRepo.contexto()?.let { (e, m) -> if (e.isNotEmpty()) etapas = e; miembros = m }
        cupo = SaniBandejaRepo.cupo()
    }

    // CARGA PROGRESIVA POR TRAMOS: lo vivo primero (se muestra apenas llega), el
    // resto detrás, y después el historial importado por coexistencia.
    LaunchedEffect(sede, intentoCarga) {
        val g = ++gen[0]
        historialListo[0] = false
        cargandoHistorial = false
        val r = SaniBandejaRepo.pagina(TramoBandeja.VIVO, null, 100, sede)
        if (g != gen[0] || !aplicarPuerta(r)) { cargado = true; return@LaunchedEffect }
        val p = (r as RespuestaBandeja.Ok).dato
        leads = p.items
        cargado = true
        separa[0] = p.separaHistorial
        if (seguirTramo(p.siguienteCursor, if (p.separaHistorial) TramoBandeja.VIVO else TramoBandeja.TODO, g) && p.separaHistorial) {
            cargarHistorial(g)
        }
    }

    // El sondeo corre solo con la app a la vista; al volver al frente, primero se pone al día.
    LaunchedEffect(sede, Reanudacion.enFrente, cargado) {
        if (!Reanudacion.enFrente || !cargado) return@LaunchedEffect
        while (true) {
            delay(SONDEO_LISTA_MS)
            refrescarRecientes()
        }
    }
    LaunchedEffect(Reanudacion.contador) { if (cargado) refrescarRecientes() }

    // ── Filtrado (el historial y lo descartado van aparte) ──
    val historial = leads.filter { esHistorialSani(it) }
    val deSani = leads.filter { !esHistorialSani(it) }
    val activos = deSani.filter { it.estado != "perdido" }
    val q = busqueda.trim().lowercase()
    val base = when { filtro == FILTRO_HISTORIAL -> historial; filtro.isNotEmpty() -> deSani; else -> activos }
    val visibles = base
        .filter { filtro.isEmpty() || filtro == FILTRO_HISTORIAL || etapaVisibleSani(it, etapas).id == filtro }
        .filter { q.isEmpty() || (it.nombre ?: "").lowercase().contains(q) || it.contactoExterno.lowercase().contains(q) }

    ManejarAtras(activo = selId == null && filtro.isNotEmpty()) { filtro = "" }

    Box(Modifier.fillMaxSize()) {
    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "← Más", color = c.sobreNavy, fontSize = Sania.txt.pequeno,
                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).tocable { onSalir() }.padding(vertical = 2.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text("🤖 Sani", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
                    Text("Tu asistente de WhatsApp — interviene cuando quieras", color = c.sobreNavy.copy(alpha = 0.75f), fontSize = Sania.txt.mini)
                }
            }
            when (puerta) {
                Puerta.INACTIVO -> Box(Modifier.fillMaxSize().padding(Sania.dim.xl), Alignment.TopCenter) {
                    pe.saniape.app.ui.clinica.EstadoVacio(
                        emoji = "🤖", titulo = "Sani no está activo",
                        subtitulo = "Sani responde el WhatsApp de tu clínica y agenda citas. Actívalo desde la web en Mensajes.",
                    )
                }
                Puerta.SIN_PERMISO -> Box(Modifier.fillMaxSize().padding(Sania.dim.xl), Alignment.TopCenter) {
                    pe.saniape.app.ui.clinica.EstadoVacio(emoji = "🔒", titulo = "No tienes acceso al módulo Mensajes.")
                }
                Puerta.ERROR -> Box(Modifier.fillMaxSize().padding(Sania.dim.xl), Alignment.TopCenter) {
                    pe.saniape.app.ui.clinica.EstadoVacio(
                        emoji = "⚠", titulo = "No se pudo cargar la bandeja", subtitulo = mensajeError,
                        textoAccion = "Reintentar", onAccion = { puerta = Puerta.CARGANDO; cargado = false; intentoCarga++ },
                    )
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item(key = "cabecera") {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Contadores sobre lo cargado (cuadran con la lista de abajo).
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Contador("Conversaciones", activos.size.toString(), c.texto, Modifier.weight(1f))
                                Contador("Necesitan tu ayuda", leads.count { it.estado == "escalado" }.toString(), c.error, Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Contador("Agendaron", leads.count { it.estado == "ganado" }.toString(), c.ok, Modifier.weight(1f))
                                cupo?.let { (u, l) -> Contador("Personas nuevas este mes", "$u de $l", if (u >= l) c.error else if (u >= l * 0.8) c.pend else c.navy, Modifier.weight(1f)) }
                                    ?: Spacer(Modifier.weight(1f))
                            }
                            FranjaResultados()
                            OutlinedTextField(
                                value = busqueda, onValueChange = { busqueda = it }, singleLine = true,
                                placeholder = { Text("Buscar conversación…", color = c.textoSuave) },
                                colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
                            )
                            // Buzones: Todas, cada etapa del recorrido e Historial (antes de Sani).
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ChipFiltro("Todas", activos.size, filtro.isEmpty(), null) { filtro = "" }
                                etapas.forEach { e ->
                                    ChipFiltro(nombreEtapaSani(e), deSani.count { etapaVisibleSani(it, etapas).id == e.id }, filtro == e.id, colorEtapa(e.color)) {
                                        filtro = if (filtro == e.id) "" else e.id
                                    }
                                }
                                if (historial.isNotEmpty()) {
                                    ChipFiltro("📜 Historial", historial.size, filtro == FILTRO_HISTORIAL, null) {
                                        filtro = if (filtro == FILTRO_HISTORIAL) "" else FILTRO_HISTORIAL
                                    }
                                }
                            }
                        }
                    }
                    if (puerta == Puerta.CARGANDO) {
                        item(key = "cargando") {
                            Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator(color = c.navy) }
                        }
                    } else if (visibles.isEmpty()) {
                        item(key = "vacio") {
                            Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                                when {
                                    filtro == FILTRO_HISTORIAL && cargandoHistorial ->
                                        Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator(color = c.navy) }
                                    filtro.isEmpty() && busqueda.isBlank() -> pe.saniape.app.ui.clinica.EstadoVacio(
                                        emoji = "🤖", titulo = "Sani está atento",
                                        subtitulo = "Cuando alguien escriba al WhatsApp de la clínica, la conversación aparecerá aquí.",
                                    )
                                    else -> pe.saniape.app.ui.clinica.EstadoVacio(emoji = "🤔", titulo = "Nada con esos filtros.")
                                }
                            }
                        }
                    }
                    items(visibles, key = { it.id }) { l ->
                        FilaConversacion(l, etapaVisibleSani(l, etapas)) { selId = l.id }
                    }
                    if (puerta == Puerta.OK && mostrarAvisoHistorial(historial.size, activos.size, filtro, busqueda)) {
                        item(key = "aviso-historial") {
                            Box(
                                Modifier.padding(14.dp).fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
                                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
                                    .tocable { filtro = FILTRO_HISTORIAL }.padding(12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "🗂 ${historial.size}${if (cargandoHistorial) "+" else ""} ${if (historial.size == 1) "conversación anterior" else "conversaciones anteriores"} en Historial",
                                    color = c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    item(key = "pie") { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    // El chat (pantalla completa encima de la lista, como la web en móvil).
    AnimatedVisibility(visible = selId != null, enter = pe.saniape.app.ui.theme.entrarDetalle(), exit = pe.saniape.app.ui.theme.salirDetalle()) {
        val id = remember(selId) { selId } ?: return@AnimatedVisibility
        ChatSani(
            leadId = id,
            filaLista = leads.firstOrNull { it.id == id },
            etapas = etapas, miembros = miembros,
            cache = cacheDetalles,
            onCerrar = { selId = null },
            // La conversación abierta manda sobre su fila (una acción se ve al instante).
            onLeadActualizado = { nuevo -> leads = leads.map { if (it.id == nuevo.id) nuevo else it } },
            onRefrescarLista = { scope.launch { refrescarRecientes() } },
            onAbrirPaciente = onAbrirPaciente,
        )
    }
    }
}

@Composable
private fun Contador(titulo: String, valor: String, color: Color, modifier: Modifier) {
    val c = Sania.colors
    Column(
        modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(titulo.uppercase(), color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(valor, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * 📈 Lo que Sani le trajo a la clínica en el mes (GET /api/staff/mensajes/resultados).
 * Igual que la franja de la web: sin citas del bot en el mes actual no ocupa lugar;
 * «por atender» y «canceladas» solo si hay; el monto solo si el servidor lo manda.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FranjaResultados() {
    val c = Sania.colors
    val actual = remember { hoyClinicaIso().take(7) }
    var mes by remember { mutableStateOf(actual) }
    var datos by remember { mutableStateOf<ResultadosSaniMes?>(null) }
    LaunchedEffect(mes) { datos = SaniBandejaRepo.resultados(mes) }
    val d = datos ?: return
    if (d.agendadas == 0 && mes == actual && d.pacientesNuevos == 0) return
    val titulo = if (mes == actual) "Este mes" else "En ${nombreMesSani(mes)}"
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text("📈 $titulo, Sani te trajo:", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Dato(d.agendadas.toString(), if (d.agendadas == 1) "cita agendada" else "citas agendadas", c.texto)
            Dato(d.atendidas.toString(), if (d.atendidas == 1) "atendida" else "atendidas", c.ok)
            if (d.porAtender > 0) Dato(d.porAtender.toString(), "por atender", c.texto)
            if (d.perdidas > 0) Dato(d.perdidas.toString(), if (d.perdidas == 1) "cancelada o no vino" else "canceladas o no vinieron", c.texto)
            Dato(d.pacientesNuevos.toString(), if (d.pacientesNuevos == 1) "paciente nuevo" else "pacientes nuevos", c.texto)
            d.cobrado?.let { Dato("S/ ${pe.saniape.app.data.staff.textoMonto(it)}", "cobrados", c.ok) }
        }
        Text(
            if (mes == actual) "Ver mes anterior" else "Ver este mes", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.End).clip(RoundedCornerShape(Sania.shape.sm.dp))
                .tocable { mes = if (mes == actual) mesAnteriorSani(actual) else actual }.padding(4.dp),
        )
    }
}

@Composable
private fun Dato(valor: String, etiqueta: String, color: Color) {
    Row {
        Text(valor, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(" $etiqueta", color = Sania.colors.textoSuave, fontSize = 13.sp)
    }
}

@Composable
private fun ChipFiltro(texto: String, n: Int, activo: Boolean, punto: Color?, onClick: () -> Unit) {
    val c = Sania.colors
    Row(
        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(if (activo) c.navy else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
            .tocable { onClick() }.padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (punto != null) { Box(Modifier.size(7.dp).clip(CircleShape).background(punto)); Spacer(Modifier.width(6.dp)) }
        Text(texto, color = if (activo) c.sobreNavy else c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text(n.toString(), color = if (activo) c.sobreNavy.copy(alpha = 0.8f) else c.textoSuave, fontSize = 12.sp)
    }
}

@Composable
private fun Avatar(l: LeadSani, tam: Int = 40) {
    val c = Sania.colors
    Box {
        Box(Modifier.size(tam.dp).clip(CircleShape).background(colorNivel(l.nivelInteres)), contentAlignment = Alignment.Center) {
            Text(l.titulo.trim().take(1).uppercase().ifBlank { "?" }, color = Color.White, fontSize = (tam / 2.6).sp, fontWeight = FontWeight.Bold)
        }
        // El canal sobre el avatar: se reconoce de un vistazo y decide si responder cuesta o no.
        Box(
            Modifier.align(Alignment.BottomEnd).size(17.dp).clip(CircleShape).background(c.superficie),
            contentAlignment = Alignment.Center,
        ) { Text(iconoCanalSani(l.canalOrigen), fontSize = 9.sp) }
    }
}

@Composable
private fun FilaConversacion(l: LeadSani, etapa: EtapaSani, onClick: () -> Unit) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().background(c.superficie).tocable(escala = 0.99f) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { contentDescription = "Conversación con ${l.titulo}" },
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(l)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(l.titulo, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                Text(haceTexto(minutosDesde(l.actualizadoEn)), color = c.textoSuave, fontSize = 11.sp)
            }
            Text(l.resumenIA ?: "Sin resumen todavía", color = c.textoSuave, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(colorEtapa(etapa.color)))
                Spacer(Modifier.width(5.dp))
                Text(nombreEtapaSani(etapa), color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                // Sin esto no se distingue cuál espera a una persona (y el reloj de la ventana corre).
                if (l.botPausado) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.pendBg).padding(horizontal = 6.dp, vertical = 1.dp)) {
                        Text("la atiendes tú", color = c.pend, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
}

// ── El chat ────────────────────────────────────────────────────────────────

private val TONOS = listOf("" to "Tono del negocio", "formal" to "Formal", "cercano" to "Cercano", "directo" to "Directo", "alegre" to "Alegre")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatSani(
    leadId: String,
    filaLista: LeadSani?,
    etapas: List<EtapaSani>,
    miembros: List<MiembroSani>,
    cache: MutableMap<String, DetalleLeadSani>,
    onCerrar: () -> Unit,
    onLeadActualizado: (LeadSani) -> Unit,
    onRefrescarLista: () -> Unit,
    onAbrirPaciente: ((String) -> Unit)?,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = recordarAcciones()
    // La ficha del paciente de esta conversación, si Sania guardó el vínculo.
    var paciente by remember(leadId) { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(leadId, onAbrirPaciente != null) {
        if (onAbrirPaciente != null) paciente = SaniBandejaRepo.pacienteVinculado(leadId)
    }
    var detalle by remember(leadId) { mutableStateOf(cache[leadId]) }
    var completo by remember(leadId) { mutableStateOf(false) }
    var enviando by remember { mutableStateOf(false) }
    var texto by remember(leadId) { mutableStateOf("") }
    var tono by remember { mutableStateOf("") }
    var verFicha by remember { mutableStateOf(false) }
    var editandoFicha by remember { mutableStateOf(false) }
    var mostrarUltimos by remember(leadId) { mutableIntStateOf(MENSAJES_VISIBLES) }
    var fallo by remember(leadId) { mutableStateOf<String?>(null) }

    ManejarAtras(activo = true) { if (verFicha) verFicha = false else onCerrar() }

    suspend fun cargar() {
        when (val r = SaniBandejaRepo.detalle(leadId, if (completo) null else SaniBandejaRepo.ULTIMOS_AL_ABRIR)) {
            is RespuestaBandeja.Ok -> { detalle = r.dato; cache[leadId] = r.dato; onLeadActualizado(r.dato.lead); fallo = null }
            is RespuestaBandeja.Error -> if (detalle == null) fallo = r.mensaje
            else -> if (detalle == null) fallo = "No se pudo abrir la conversación"
        }
    }

    LaunchedEffect(leadId, completo) { cargar() }
    // Hilo abierto: cada 8 s, solo a la vista, y nunca mientras se envía o se edita la ficha.
    LaunchedEffect(leadId, Reanudacion.enFrente, enviando, editandoFicha) {
        if (!Reanudacion.enFrente || enviando || editandoFicha) return@LaunchedEffect
        while (true) { delay(SONDEO_HILO_MS); cargar() }
    }

    /** Una acción sobre la conversación; devuelve el cuerpo (o null si falló, ya avisado). */
    suspend fun accion(tipo: String, extra: Map<String, JsonElement> = emptyMap()): kotlinx.serialization.json.JsonObject? {
        enviando = true
        try {
            return when (val r = SaniBandejaRepo.accion(leadId, tipo, extra)) {
                is RespuestaBandeja.Ok -> {
                    if (tipo == "responder" || tipo == "aprobar_borrador") texto = ""
                    cargar(); onRefrescarLista()
                    r.dato
                }
                is RespuestaBandeja.Error -> { Toaster.error(r.mensaje); null }
                is RespuestaBandeja.SinPermiso -> { Toaster.error(r.mensaje); null }
                RespuestaBandeja.Inactivo -> { Toaster.error("Sani no está activo en esta clínica"); null }
            }
        } finally { enviando = false }
    }

    fun toggleBot() {
        val d = detalle ?: return
        val pausar = !d.lead.botPausado
        detalle = d.copy(lead = d.lead.copy(botPausado = pausar))   // al instante; si falla, se revierte
        scope.launch {
            val r = accion(if (pausar) "pausar_bot" else "activar_bot")
            if (r == null) detalle = detalle?.let { it.copy(lead = it.lead.copy(botPausado = !pausar)) }
            else Toaster.exito(if (pausar) "Ahora atiendes tú esta conversación" else "Sani vuelve a responder")
        }
    }

    val lead = detalle?.lead ?: filaLista
    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            // Cabecera
            Row(
                Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("←", color = c.sobreNavy, fontSize = 20.sp, modifier = Modifier.clip(CircleShape).tocable { onCerrar() }.padding(6.dp)
                    .semantics { contentDescription = "Volver a la bandeja" })
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f).tocable(escala = 0.99f) { if (detalle != null) verFicha = true }) {
                    Text(lead?.titulo ?: "Conversación", color = c.sobreNavy, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    lead?.let { l ->
                        val et = etapaVisibleSani(l, etapas)
                        Text(
                            "${nombreEtapaSani(et)} · ${iconoCanalSani(l.canalOrigen)} ${nombreCanalSani(l.canalOrigen)}${if (l.adsClickId != null) " · 📣 De un anuncio" else ""} · ver ficha",
                            color = c.sobreNavy.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                lead?.takeIf { detalle != null }?.let { l ->
                    // La acción, no un estado técnico: tomar la conversación o devolvérsela a Sani.
                    Text(
                        if (l.botPausado) "↩ Que siga Sani" else "● Lo tomo yo",
                        color = if (l.botPausado) c.pend else c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                            .background(if (l.botPausado) c.pendBg else c.sobreNavy)
                            .tocable(habilitado = !enviando) { toggleBot() }.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            val d = detalle
            if (d == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    if (fallo != null) Text(fallo!!, color = c.error, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
                    else Text("Abriendo la conversación…", color = c.textoSuave, fontSize = 13.sp)
                }
                return@Column
            }
            val l = d.lead
            val ventana = ventanaSani(d.mensajes, l.adsClickId != null)
            val canalCobra = l.canalOrigen == "whatsapp"
            val bloqueado = bloqueaRespuestaPorCanal(l.canalOrigen)
            if (l.botPausado) {
                Text(
                    "Estás atendiendo tú. Sani no responde aquí hasta que se lo devuelvas" +
                        if (ventana.abierta && ventana.restanMin < 180) " · te quedan ${textoRestante(ventana.restanMin)} de ventana" else "",
                    color = c.pend, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().background(c.pendBg).padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }

            // Mensajes
            val total = d.totalMensajes ?: d.mensajes.size
            val visibles = d.mensajes.takeLast(mostrarUltimos)
            val lista = rememberLazyListState()
            LaunchedEffect(leadId, d.mensajes.size) {
                val ultimo = visibles.size + 1   // + el borrador / el botón de anteriores
                if (ultimo > 0) lista.scrollToItem(ultimo)
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = lista, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                item(key = "anteriores") {
                    if (total > mostrarUltimos) {
                        Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), Alignment.Center) {
                            Text(
                                "↑ Ver mensajes anteriores (${total - mostrarUltimos} más)", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.superficie)
                                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.pill.dp))
                                    .tocable {
                                        // Lo anterior a lo bajado se pide recién ahora (una vez).
                                        if (mostrarUltimos + 100 > d.mensajes.size && total > d.mensajes.size) completo = true
                                        mostrarUltimos += 100
                                    }.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    } else if (d.mensajes.isEmpty()) {
                        Text("Todavía no hay mensajes.", color = c.textoSuave, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
                items(visibles, key = { it.id }) { m -> Burbuja(m) }
                item(key = "borrador") {
                    l.borradorIA?.takeIf { it.isNotBlank() }?.let { b ->
                        Column(
                            Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
                                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(12.dp),
                        ) {
                            Text("🤖 SANI PREPARÓ ESTA RESPUESTA", color = c.navy, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                b, color = c.texto, fontSize = 13.sp,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg)
                                    .tocable(escala = 0.99f) { texto = b }.padding(10.dp),
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (bloqueado) "Toca el texto para ajustarlo y copiarlo a WhatsApp" else "Toca el texto para editarlo antes de enviar",
                                    color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.weight(1f),
                                )
                                if (!bloqueado) BotonChico("Enviar tal cual", c.navy, lleno = true, habilitado = !enviando) { scope.launch { accion("aprobar_borrador") } }
                            }
                        }
                    }
                }
            }

            // Compositor: herramientas arriba, campo abajo
            Column(Modifier.fillMaxWidth().background(c.superficie).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BotonChico("✨ Sugerir respuesta", c.navy, habilitado = !enviando) {
                        scope.launch {
                            val b = accion("sugerir_respuesta", if (tono.isNotEmpty()) mapOf("tono" to JsonPrimitive(tono)) else emptyMap())
                            val s = b?.let { SaniBandejaRepo.textoDe(it, "borrador", "borradorIA") }.orEmpty()
                            if (s.isNotBlank()) texto = s
                        }
                    }
                    if (texto.isNotBlank()) BotonChico("✓ Corregir", c.navy, habilitado = !enviando) {
                        val actual = texto.trim()
                        scope.launch {
                            val b = accion("corregir_texto", buildMap { put("texto", JsonPrimitive(actual)); if (tono.isNotEmpty()) put("tono", JsonPrimitive(tono)) })
                            val s = b?.let { SaniBandejaRepo.textoDe(it, "texto", "corregido") }.orEmpty()
                            if (s.isNotBlank() && s != actual) texto = s
                        }
                    }
                    TONOS.forEach { (v, t) ->
                        Text(
                            t, color = if (tono == v) c.sobreNavy else c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(if (tono == v) c.navy else c.chipBg)
                                .tocable { tono = v }.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                when {
                    bloqueado -> {
                        // Responder por WhatsApp desde Sania se cobra: se redacta aquí y se envía
                        // desde el WhatsApp Business del celular de la clínica, que es gratis.
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg).padding(10.dp)) {
                            Text("📱 Respóndele desde el WhatsApp de la clínica", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(MOTIVO_NO_RESPONDER, color = c.textoSuave, fontSize = 11.sp)
                        }
                        OutlinedTextField(
                            value = texto, onValueChange = { texto = it }, minLines = 2, maxLines = 5,
                            placeholder = { Text("Redacta aquí o pide una sugerencia; después cópialo al chat de WhatsApp", color = c.textoSuave, fontSize = 13.sp) },
                            colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
                        )
                        val url = enlaceWhatsAppSede(l.contactoExterno, texto.trim().ifBlank { null })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (texto.isNotBlank()) BotonChico("📋 Copiar", c.navy) {
                                acciones.copiarTexto(texto.trim(), "Respuesta"); Toaster.exito("Copiado. Pégalo en WhatsApp.")
                            }
                            if (url != null) BotonChico("📱 Abrir en WhatsApp${if (texto.isNotBlank()) " con este texto" else ""}", c.ok, lleno = true) { acciones.abrirUrl(url) }
                            else Text("Este contacto no tiene un número de WhatsApp.", color = c.textoSuave, fontSize = 11.sp)
                        }
                    }
                    !ventana.abierta -> Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg).padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Pasaron ${ventana.horas} horas sin que te escriba", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (canalCobra) "WhatsApp ya no permite responder por aquí. Para retomarla hay que enviarle un mensaje de los aprobados, que sí tiene costo."
                            else "${nombreCanalSani(l.canalOrigen)} ya no permite responder por aquí. Hay que esperar a que la persona vuelva a escribir.",
                            color = c.textoSuave, fontSize = 12.sp, textAlign = TextAlign.Center,
                        )
                    }
                    else -> {
                        if (ventana.restanMin < 180) Text(
                            "⏳ Te quedan ${textoRestante(ventana.restanMin)} para responderle${if (canalCobra) " sin costo" else " por aquí"}",
                            color = c.pend, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        )
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = texto, onValueChange = { texto = it }, minLines = 1, maxLines = 5,
                                placeholder = { Text("Escribe como la clínica…", color = c.textoSuave, fontSize = 13.sp) },
                                colors = coloresCampoForm(), modifier = Modifier.weight(1f),
                            )
                            BotonChico(if (enviando) "…" else "Enviar", c.navy, lleno = true, habilitado = !enviando && texto.isNotBlank()) {
                                val t = texto.trim()
                                scope.launch { accion("responder", mapOf("texto" to JsonPrimitive(t))) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (verFicha) detalle?.let { d ->
        FichaContacto(
            lead = d.lead, etapas = etapas, miembros = miembros, enviando = enviando,
            onCerrar = { verFicha = false },
            onEditando = { editandoFicha = it },
            onAccion = { tipo, extra -> scope.launch { accion(tipo, extra) } },
            onGuardarFicha = { etiquetas, nota ->
                val previo = detalle
                detalle = d.copy(lead = d.lead.copy(etiquetas = etiquetas ?: d.lead.etiquetas, nota = nota ?: d.lead.nota))
                scope.launch {
                    val r = SaniBandejaRepo.ficha(leadId, etiquetas, nota)
                    if (r !is RespuestaBandeja.Ok) { detalle = previo; Toaster.error("No se pudo guardar. Inténtalo de nuevo.") }
                }
            },
            onCopiar = { acciones.copiarTexto(d.lead.contactoExterno, "Contacto"); Toaster.exito("Copiado") },
            paciente = paciente?.takeIf { onAbrirPaciente != null },
            onAbrirPaciente = { id -> verFicha = false; onAbrirPaciente?.invoke(id) },
        )
    }
}

@Composable
private fun Burbuja(m: MensajeSani) {
    val c = Sania.colors
    val saliente = m.direccion == "saliente"
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = if (saliente) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (saliente) 16.dp else 4.dp, bottomEnd = if (saliente) 4.dp else 16.dp))
                .background(if (saliente) c.navy else c.superficie)
                .then(if (saliente) Modifier else Modifier.border(1.dp, c.borde, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // ¿Sani o una persona de la clínica? (esDelBot de la web)
            if (saliente) Text(if (esDelBot(m.origen)) "🤖 Sani" else "🧑 Tú", color = c.sobreNavy.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(m.contenido, color = if (saliente) c.sobreNavy else c.texto, fontSize = 14.sp)
            Text(horaCortaSani(m.creadoEn), color = if (saliente) c.sobreNavy.copy(alpha = 0.6f) else c.textoSuave, fontSize = 10.sp, modifier = Modifier.align(Alignment.End))
        }
    }
}

/** Hoy → "14:05"; otro día → "02/11 14:05" (en la zona de la clínica). */
private fun horaCortaSani(iso: String): String {
    val t = pe.saniape.app.data.staff.parsearInstante(iso) ?: return ""
    val ldt = t.toLocalDateTime(pe.saniape.app.data.staff.ZONA_CLINICA)
    val hhmm = "${ldt.hour.toString().padStart(2, '0')}:${ldt.minute.toString().padStart(2, '0')}"
    val hoy = hoyClinicaIso()
    return if (ldt.date.toString() == hoy) hhmm else "${ldt.dayOfMonth.toString().padStart(2, '0')}/${ldt.monthNumber.toString().padStart(2, '0')} $hhmm"
}

@Composable
private fun BotonChico(texto: String, color: Color, lleno: Boolean = false, habilitado: Boolean = true, onClick: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    Box(
        Modifier.clip(forma).background(if (lleno) (if (habilitado) color else c.borde) else c.superficie)
            .then(if (lleno) Modifier else Modifier.border(1.dp, if (habilitado) color else c.borde, forma))
            .tocable(habilitado = habilitado) { onClick() }.padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = if (lleno) c.sobreNavy else if (habilitado) color else c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
}

/**
 * La ficha del contacto (columna 4 de la web): contacto, etapa del recorrido,
 * quién la atiende, etiquetas, lo que entendió Sani, nota privada y el cierre.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FichaContacto(
    lead: LeadSani,
    etapas: List<EtapaSani>,
    miembros: List<MiembroSani>,
    enviando: Boolean,
    onCerrar: () -> Unit,
    onEditando: (Boolean) -> Unit,
    onAccion: (String, Map<String, JsonElement>) -> Unit,
    onGuardarFicha: (List<String>?, String?) -> Unit,
    onCopiar: () -> Unit,
    paciente: Pair<String, String>?,
    onAbrirPaciente: (String) -> Unit,
) {
    val c = Sania.colors
    var etiquetaNueva by remember { mutableStateOf("") }
    var notaEdit by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notaEdit != null) { onEditando(notaEdit != null) }
    val etapaLead = etapaVisibleSani(lead, etapas)
    DialogoConTeclado(onDismissRequest = { onEditando(false); onCerrar() }) {
        Surface(
            shape = RoundedCornerShape(Sania.shape.md.dp), color = c.superficie,
            modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 560.dp).fillMaxWidth(),
        ) {
            Column {
                Row(Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Ficha del contacto", color = c.sobreNavy, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("✕", color = c.sobreNavy, fontSize = 16.sp, modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .tocable { onEditando(false); onCerrar() }.padding(horizontal = 8.dp, vertical = 2.dp))
                }
                Column(
                    Modifier.weight(1f, fill = false).heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column {
                        Text(lead.titulo, color = c.texto, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(lead.contactoExterno, color = c.textoSuave, fontSize = 13.sp)
                            Spacer(Modifier.width(8.dp))
                            BotonChico("Copiar", c.navy, onClick = onCopiar)
                        }
                        Text("${iconoCanalSani(lead.canalOrigen)} ${nombreCanalSani(lead.canalOrigen)}", color = c.textoSuave, fontSize = 12.sp)
                    }
                    paciente?.let { (id, nombre) ->
                        BotonChico("👤 Ver ficha de $nombre", c.navy, lleno = true) { onAbrirPaciente(id) }
                    }
                    Seccion("Etapa del recorrido") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            etapas.forEach { e ->
                                ChipOpcion(nombreEtapaSani(e), e.id == etapaLead.id, !enviando, colorEtapa(e.color)) {
                                    if (e.id != etapaLead.id) onAccion("mover_etapa", mapOf("etapaId" to JsonPrimitive(e.id)))
                                }
                            }
                        }
                    }
                    Seccion("Quién la atiende") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ChipOpcion("Sin asignar", lead.asignadoA == null, !enviando, null) {
                                if (lead.asignadoA != null) onAccion("asignar", mapOf("usuarioId" to JsonNull))
                            }
                            miembros.forEach { m ->
                                ChipOpcion(m.nombre ?: m.email, lead.asignadoA == m.usuarioId, !enviando, null) {
                                    if (lead.asignadoA != m.usuarioId) onAccion("asignar", mapOf("usuarioId" to JsonPrimitive(m.usuarioId)))
                                }
                            }
                            if (lead.asignadoA != null && miembros.none { it.usuarioId == lead.asignadoA }) ChipOpcion("Miembro anterior", true, false, null) {}
                        }
                    }
                    Seccion("Etiquetas") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            lead.etiquetas.forEach { et ->
                                Row(
                                    Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.chipBg).padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(et, color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("×", color = c.navy, fontSize = 14.sp, modifier = Modifier.clip(CircleShape)
                                        .tocable { onGuardarFicha(lead.etiquetas.filter { it != et }, null) }.padding(horizontal = 6.dp)
                                        .semantics { contentDescription = "Quitar $et" })
                                }
                            }
                        }
                        if (lead.etiquetas.size < 10) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = etiquetaNueva, onValueChange = { etiquetaNueva = it.take(20) }, singleLine = true,
                                    placeholder = { Text("Nueva etiqueta", color = c.textoSuave) }, colors = coloresCampoForm(), modifier = Modifier.weight(1f),
                                )
                                BotonChico("Agregar", c.navy, habilitado = etiquetaNueva.isNotBlank()) {
                                    val t = etiquetaNueva.trim()
                                    if (t.isNotEmpty() && lead.etiquetas.none { it.equals(t, ignoreCase = true) }) onGuardarFicha(lead.etiquetas + t, null)
                                    etiquetaNueva = ""
                                }
                            }
                        }
                    }
                    Seccion("Lo que el asistente entendió", c.pend) {
                        Text(lead.resumenIA ?: "Todavía no hay resumen de esta conversación.", color = c.texto, fontSize = 13.sp)
                        Text("Actualizado ${haceTexto(minutosDesde(lead.actualizadoEn))}", color = c.textoSuave, fontSize = 11.sp)
                    }
                    Seccion("Tu nota (privada)") {
                        val n = notaEdit
                        if (n == null) {
                            Text(lead.nota?.takeIf { it.isNotBlank() } ?: "Sin nota. El paciente nunca la ve.", color = c.textoSuave, fontSize = 13.sp)
                            BotonChico(if (lead.nota.isNullOrBlank()) "Agregar" else "Editar", c.navy) { notaEdit = lead.nota.orEmpty() }
                        } else {
                            OutlinedTextField(value = n, onValueChange = { notaEdit = it.take(2000) }, minLines = 3, colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BotonChico("Guardar", c.navy, lleno = true) { onGuardarFicha(null, n); notaEdit = null }
                                BotonChico("Cancelar", c.textoSuave) { notaEdit = null }
                            }
                        }
                    }
                    // Cierre del caso
                    when {
                        lead.estado == "ganado" -> Text(
                            "✓ Agendó su cita", color = c.ok, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.okBg).padding(12.dp),
                        )
                        lead.estado != "perdido" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) { BotonChico("✓ Agendó su cita", c.ok, lleno = true, habilitado = !enviando) { onAccion("marcar_ganado", emptyMap()) } }
                            Box(Modifier.weight(1f)) { BotonChico("No agendó", c.textoSuave, habilitado = !enviando) { onAccion("descartar", emptyMap()) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Seccion(titulo: String, color: Color = Sania.colors.textoSuave, contenido: @Composable () -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(titulo.uppercase(), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        contenido()
    }
}

@Composable
private fun ChipOpcion(texto: String, activo: Boolean, habilitado: Boolean, punto: Color?, onClick: () -> Unit) {
    val c = Sania.colors
    Row(
        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(if (activo) c.navy else c.chipBg)
            .tocable(habilitado = habilitado) { onClick() }.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (punto != null) { Box(Modifier.size(7.dp).clip(CircleShape).background(punto)); Spacer(Modifier.width(5.dp)) }
        Text(texto, color = if (activo) c.sobreNavy else c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
