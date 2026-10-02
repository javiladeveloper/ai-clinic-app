package pe.saniape.app.ui.clinica

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.EmbudoNuevos
import pe.saniape.app.data.staff.EtapaNuevo
import pe.saniape.app.data.staff.FilaPacienteNuevo
import pe.saniape.app.data.staff.PacienteStaff
import pe.saniape.app.data.staff.PacientesNuevosRepo
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.RefNombre
import pe.saniape.app.data.staff.ResultadoPacientesNuevos
import pe.saniape.app.data.staff.SedeActiva
import pe.saniape.app.data.staff.enlaceWhatsApp
import pe.saniape.app.data.staff.fechaLegibleCorta
import pe.saniape.app.data.staff.filtrarPorEtapa
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.mesesRecientes
import pe.saniape.app.data.staff.porcentajeEscalon
import pe.saniape.app.ui.ManejarAtras
import pe.saniape.app.ui.clinica.agenda.modales.textoSoles
import pe.saniape.app.ui.hora12
import pe.saniape.app.ui.nombreDeSaludo
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Paleta
import pe.saniape.app.ui.theme.Sania

/** Un escalón del embudo y la etapa en la que "se quedan" los que no pasan al siguiente. */
private data class Escalon(val etiqueta: String, val valor: Int, val etapa: String, val deQue: String?)

private fun escalones(e: EmbudoNuevos) = listOf(
    Escalon("Se registraron", e.nuevos, EtapaNuevo.SIN_EVALUACION, null),
    Escalon("Vinieron a evaluación", e.evaluados, EtapaNuevo.EVALUADO, "de los registrados"),
    Escalon("Empezaron tratamiento", e.enTratamiento, EtapaNuevo.EN_TRATAMIENTO, "de los evaluados"),
    Escalon("Pagaron", e.pagaron, EtapaNuevo.PAGO, "de los que empezaron"),
)

/** Qué se ve al filtrar por una etapa ("se quedaron en…"). */
private fun textoFiltro(etapa: String): String = when (etapa) {
    EtapaNuevo.SIN_EVALUACION -> "Se registraron y aún no vienen a evaluación"
    EtapaNuevo.EVALUADO -> "Se evaluaron y aún no empiezan tratamiento"
    EtapaNuevo.EN_TRATAMIENTO -> "Empezaron tratamiento y aún no pagan"
    else -> "Ya pagaron"
}

/** Mensaje de WhatsApp de seguimiento según hasta dónde llegó el paciente. */
internal fun mensajeSeguimientoNuevo(f: FilaPacienteNuevo, clinica: String): String {
    val hola = "Hola" + (nombreDeSaludo(f.nombre)?.let { " $it" } ?: "") + " 👋 Te escribimos de $clinica."
    return when (f.etapa) {
        EtapaNuevo.SIN_EVALUACION -> "$hola ¿Te ayudamos a agendar tu evaluación?"
        EtapaNuevo.EVALUADO -> "$hola ¿Cómo te sientes después de tu evaluación? Si quieres, coordinamos el inicio de tu tratamiento."
        else -> {
            val servicio = f.tratamiento?.servicio?.takeIf { it.isNotBlank() }?.let { " de $it" } ?: ""
            val deuda = f.pago?.deuda?.takeIf { it > 0 }?.let { " Queda un saldo de ${textoSoles(it)}." } ?: ""
            "$hola Te contactamos por tu tratamiento$servicio.$deuda ¿Coordinamos el pago?"
        }
    }
}

/**
 * 🌱 "Pacientes nuevos" (nativo): los pacientes que se registraron en un mes y
 * hasta dónde llegaron — evaluación → tratamiento → pago — con sus sesiones,
 * paciente por paciente. Gemelo de /api/staff/pacientes-nuevos (la web arma el
 * embudo y las filas; aquí solo se pintan y filtran). Solo con permiso
 * `pacientes` (lo decide el padre); el plan lo valida el servidor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaPacientesNuevos(ctx: ContextoStaff, onSalir: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = recordarAcciones()
    val meses = remember { mesesRecientes(hoyClinicaIso()) }
    var mes by remember { mutableStateOf(meses.firstOrNull()?.id ?: hoyClinicaIso().take(7)) }
    var terapeuta by remember { mutableStateOf<String?>(null) }
    var etapa by remember { mutableStateOf<String?>(null) }
    var resultado by remember { mutableStateOf<ResultadoPacientesNuevos?>(null) }
    var cargando by remember { mutableStateOf(true) }
    var refrescando by remember { mutableStateOf(false) }
    var intento by remember { mutableIntStateOf(0) }
    var terapeutas by remember { mutableStateOf<List<RefNombre>>(emptyList()) }
    var ficha by remember { mutableStateOf<PacienteStaff?>(null) }
    var cargandoFicha by remember { mutableStateOf(false) }

    ManejarAtras(activo = ficha == null, onAtras = onSalir)

    // Multisede: el reporte es de la sede activa (en "Todas las sedes", de toda la clínica).
    val sedeEstado by SedeActiva.estado.collectAsState()
    val sede = sedeEstado.filtro?.sedeId

    // Filtro por profesional: solo para quien ve a toda la clínica (el profesional
    // vinculado ya ve lo suyo). Si no se pueden leer, simplemente no aparece.
    LaunchedEffect(ctx.clinicaId) {
        if (ctx.miTerapeutaId == null || ctx.esAdmin) {
            terapeutas = runCatching { PacientesRepo.terapeutasActivos() }.getOrDefault(emptyList())
        }
    }

    LaunchedEffect(ctx.clinicaId, mes, sede, terapeuta, intento) {
        cargando = true
        if (resultado is ResultadoPacientesNuevos.Error) resultado = null
        resultado = PacientesNuevosRepo.cargar(mes, sede, terapeuta)
        cargando = false
        refrescando = false
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(c.navyDark)
                        .padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "← Más", color = c.sobreNavy, fontSize = Sania.txt.pequeno,
                            modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
                                .clickable { onSalir() }.padding(vertical = 2.dp),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text("🌱 Pacientes nuevos", color = c.sobreNavy,
                            fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
                        ChipSede(Modifier.padding(top = 4.dp))
                    }
                }

                PullToRefreshBox(
                    isRefreshing = refrescando,
                    onRefresh = { refrescando = true; intento++ },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = Sania.dim.lg, vertical = Sania.dim.md,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Sania.dim.sm),
                    ) {
                        item(key = "meses") {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                meses.forEach { m ->
                                    ChipFiltro(m.etiqueta, activo = m.id == mes) {
                                        if (m.id != mes) { mes = m.id; etapa = null }
                                    }
                                }
                            }
                        }
                        if (terapeutas.size > 1) item(key = "profesionales") {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                ChipFiltro("Todos los profesionales", activo = terapeuta == null) {
                                    terapeuta = null; etapa = null
                                }
                                terapeutas.forEach { t ->
                                    ChipFiltro(t.nombre, activo = t.id == terapeuta) {
                                        terapeuta = t.id; etapa = null
                                    }
                                }
                            }
                        }

                        val r = resultado
                        when {
                            cargando && r == null -> item(key = "cargando") {
                                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), Alignment.Center) {
                                    CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp)
                                }
                            }
                            r is ResultadoPacientesNuevos.Error -> item(key = "error") {
                                MensajeNuevos(
                                    emoji = if (r.porPlan) "💎" else "⚠",
                                    texto = r.mensaje,
                                    textoAccion = if (r.porPlan) null else "Reintentar",
                                    onAccion = { intento++ },
                                )
                            }
                            r is ResultadoPacientesNuevos.Ok -> {
                                val rep = r.reporte
                                if (cargando && !refrescando) item(key = "actualizando") {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Actualizando…", color = c.textoSuave, fontSize = Sania.txt.mini)
                                    }
                                }
                                if (rep.embudo.nuevos == 0 && rep.filas.isEmpty()) {
                                    item(key = "vacio") {
                                        MensajeNuevos(
                                            emoji = "🌱",
                                            texto = "No se registraron pacientes nuevos" +
                                                (rep.periodo.etiqueta.takeIf { it.isNotBlank() }?.let { " en $it" } ?: " este mes") + ".",
                                        )
                                    }
                                } else {
                                    item(key = "embudo") {
                                        TarjetaEmbudoNuevos(
                                            embudo = rep.embudo,
                                            etiqueta = rep.periodo.etiqueta,
                                            filas = rep.filas,
                                            etapa = etapa,
                                            onEtapa = { etapa = if (etapa == it) null else it },
                                        )
                                    }
                                    val visibles = filtrarPorEtapa(rep.filas, etapa)
                                    item(key = "filtro") {
                                        Row(
                                            Modifier.fillMaxWidth().padding(top = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                (etapa?.let { textoFiltro(it) } ?: "Todos los pacientes") + " · ${visibles.size}",
                                                color = c.textoSuave, fontSize = Sania.txt.pequeno,
                                                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                                            )
                                            if (etapa != null) ChipFiltro("Todos", activo = false) { etapa = null }
                                        }
                                    }
                                    if (visibles.isEmpty()) item(key = "sin-filtro") {
                                        MensajeNuevos(emoji = "🙌", texto = "Nadie se quedó en este paso.")
                                    }
                                    items(visibles, key = { it.pacienteId }) { f ->
                                        TarjetaPacienteNuevo(
                                            f = f,
                                            onAbrir = {
                                                if (!cargandoFicha) {
                                                    cargandoFicha = true
                                                    scope.launch {
                                                        val p = runCatching { PacientesRepo.porId(f.pacienteId) }.getOrNull()
                                                        cargandoFicha = false
                                                        if (p != null) ficha = p
                                                        else pe.saniape.app.ui.Toaster.error("No se pudo abrir la ficha del paciente.")
                                                    }
                                                }
                                            },
                                            onWhatsApp = f.telefono?.takeIf { !f.pagado }
                                                ?.let { tel -> enlaceWhatsApp(tel, mensajeSeguimientoNuevo(f, ctx.clinicaNombre)) }
                                                ?.let { url -> { acciones.abrirUrl(url) } },
                                        )
                                    }
                                }
                            }
                        }
                        item(key = "fin") { Spacer(Modifier.height(Sania.dim.xl)) }
                    }
                }
            }

            if (cargandoFicha) {
                Box(Modifier.fillMaxSize().background(c.fondo.copy(alpha = 0.6f)), Alignment.Center) {
                    CircularProgressIndicator(color = c.navy)
                }
            }
            ficha?.let { pac ->
                Box(Modifier.fillMaxSize().background(c.fondo)) {
                    pe.saniape.app.ui.clinica.pacientes.PantallaFichaPaciente(
                        ctx = ctx, pacienteInicial = pac,
                        // Al volver, el reporte se recarga: lo hecho en la ficha (agendar, cobrar) cuenta.
                        onCerrar = { ficha = null; intento++ },
                    )
                }
            }
        }
    }
}

@Composable
private fun TarjetaEmbudoNuevos(
    embudo: EmbudoNuevos,
    etiqueta: String,
    filas: List<FilaPacienteNuevo>,
    etapa: String?,
    onEtapa: (String) -> Unit,
) {
    val c = Sania.colors
    val pasos = escalones(embudo)
    val quedados = filas.groupingBy { it.etapa }.eachCount()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .padding(Sania.dim.lg),
    ) {
        if (etiqueta.isNotBlank()) {
            Text(etiqueta.uppercase(), color = c.textoSuave, fontSize = Sania.txt.mini,
                fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(6.dp))
        }
        pasos.forEachIndexed { i, p ->
            val activo = etapa == p.etapa
            val pct = if (i == 0) null else porcentajeEscalon(p.valor, pasos[i - 1].valor)
            val color = when (i) { 0 -> c.navy; 1 -> c.info; 2 -> c.purple; else -> c.ok }
            Column(
                Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(if (activo) c.chipBg else Color.Transparent)
                    .border(1.dp, if (activo) c.navy else Color.Transparent, RoundedCornerShape(Sania.shape.sm.dp))
                    .clickable { onEtapa(p.etapa) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.etiqueta, color = c.texto, fontSize = Sania.txt.pequeno,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (pct != null && p.deQue != null) {
                        Text("$pct% ${p.deQue}", color = c.textoSuave, fontSize = Sania.txt.mini)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("${p.valor}", color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth().height(8.dp)
                        .clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.chipBg),
                ) {
                    val total = embudo.nuevos.coerceAtLeast(1)
                    val v = p.valor.coerceIn(0, total)
                    if (v > 0) Box(Modifier.weight(v.toFloat()).fillMaxHeight().background(color))
                    if (total - v > 0) Spacer(Modifier.weight((total - v).toFloat()))
                }
                val n = quedados[p.etapa] ?: 0
                if (n > 0) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (p.etapa == EtapaNuevo.PAGO) "Ver los $n que pagaron"
                        else "$n se ${if (n == 1) "quedó" else "quedaron"} aquí",
                        color = if (activo) c.navy else c.textoSuave, fontSize = Sania.txt.mini,
                        fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Toca un paso para ver a quiénes se quedaron ahí.",
            color = c.textoSuave, fontSize = Sania.txt.mini,
        )
    }
}

@Composable
private fun TarjetaPacienteNuevo(f: FilaPacienteNuevo, onAbrir: () -> Unit, onWhatsApp: (() -> Unit)?) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .clickable { onAbrir() }.padding(Sania.dim.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(f.nombre.ifBlank { "Paciente" }, color = c.texto, fontSize = Sania.txt.cuerpo,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (f.fechaRegistro.isNotBlank()) {
                    Text("Registrado ${fechaLegibleCorta(f.fechaRegistro)}", color = c.textoSuave, fontSize = Sania.txt.mini)
                }
            }
            f.pago?.let { BadgePago(it.estado, it.deuda) }
        }
        Spacer(Modifier.height(8.dp))

        // Evaluación
        val ev = f.evaluacion
        val (txtEv, colEv) = when (ev?.estado) {
            "atendida" -> "✓ Evaluado ${fechaLegibleCorta(ev.fecha)}" +
                (ev.profesional?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") to c.ok
            "agendada" -> "📅 Evaluación agendada ${fechaLegibleCorta(ev.fecha)}" +
                (ev.profesional?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") to c.info
            "no_asistio" -> "✕ No asistió a su evaluación (${fechaLegibleCorta(ev.fecha)})" to c.error
            else -> "Sin evaluación" to c.textoSuave
        }
        Text(txtEv, color = colEv, fontSize = Sania.txt.pequeno)

        // Tratamiento + sesiones
        f.tratamiento?.let { t ->
            Spacer(Modifier.height(4.dp))
            val esPaquete = t.modalidad?.lowercase()?.contains("paquete") == true
            val detalle = when {
                esPaquete && t.totalSesiones != null -> " · Paquete de ${t.totalSesiones}"
                !t.modalidad.isNullOrBlank() -> " · ${t.modalidad}"
                else -> ""
            }
            Text("🩺 ${t.servicio.ifBlank { "Tratamiento" }}$detalle", color = c.texto, fontSize = Sania.txt.pequeno,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "Sesiones ${t.sesionesCompletadas}" + (t.totalSesiones?.let { "/$it" } ?: ""),
                color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold,
            )
        }

        // Próxima cita
        f.proximaCita?.takeIf { it.fecha.isNotBlank() }?.let { pc ->
            Spacer(Modifier.height(4.dp))
            Text(
                "Próxima cita: ${fechaLegibleCorta(pc.fecha)}" + (pc.hora?.takeIf { it.isNotBlank() }?.let { " · ${hora12(it)}" } ?: ""),
                color = c.navy, fontSize = Sania.txt.pequeno,
            )
        }

        if (onWhatsApp != null) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                    .border(1.dp, Paleta.WhatsApp, RoundedCornerShape(Sania.shape.pill.dp))
                    .clickable { onWhatsApp() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("💬 WhatsApp", color = c.texto, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BadgePago(estado: String, deuda: Double?) {
    val c = Sania.colors
    val (etq, fg, bg) = when (estado) {
        "pagado" -> Triple("Pagado", c.ok, c.okBg)
        "parcial" -> Triple("Parcial", c.info, c.infoBg)
        else -> Triple("Sin pagar", c.pend, c.pendBg)
    }
    Column(horizontalAlignment = Alignment.End) {
        Box(
            Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg)
                .padding(horizontal = 10.dp, vertical = 3.dp),
        ) { Text(etq, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        if (estado != "pagado" && deuda != null && deuda > 0) {
            Text("debe ${textoSoles(deuda)}", color = fg, fontSize = Sania.txt.mini,
                modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun ChipFiltro(texto: String, activo: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
            .background(if (activo) c.navy else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(texto, color = if (activo) c.sobreNavy else c.texto, fontSize = 12.sp,
            fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun MensajeNuevos(emoji: String, texto: String, textoAccion: String? = null, onAccion: () -> Unit = {}) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = Sania.dim.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 36.sp)
        Spacer(Modifier.height(Sania.dim.sm))
        Text(texto, color = c.textoSuave, fontSize = Sania.txt.cuerpo, textAlign = TextAlign.Center)
        if (textoAccion != null) {
            Spacer(Modifier.height(Sania.dim.md))
            Box(
                Modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.navy)
                    .clickable { onAccion() }.padding(horizontal = 20.dp, vertical = 10.dp),
            ) { Text(textoAccion, color = c.sobreNavy, fontWeight = FontWeight.Bold) }
        }
    }
}
