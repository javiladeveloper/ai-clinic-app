package pe.saniape.app.ui.clinica.actividad

import pe.saniape.app.data.staff.LocalTerminologiaPaciente
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.ActividadRepo
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.DatosActividad
import pe.saniape.app.data.staff.MovimientoActividad
import pe.saniape.app.data.staff.PersonaActividad
import pe.saniape.app.data.staff.TipoActividad
import pe.saniape.app.data.staff.diaDeMovimiento
import pe.saniape.app.data.staff.filtrarMovimientos
import pe.saniape.app.data.staff.horaDeMovimiento
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.inicialesActividad
import pe.saniape.app.data.staff.sumarDiasIso
import pe.saniape.app.ui.CargandoLista
import pe.saniape.app.ui.clinica.EstadoVacio
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoFecha
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.fechaDMA
import pe.saniape.app.ui.theme.Sania

/**
 * 📈 Actividad del equipo (Más → Administración, solo Admin). Gemelo de
 * /actividad en la web: qué se REGISTRÓ en un rango de fechas (no lo agendado
 * para ellas) y quién lo hizo. Resumen en 4 tarjetas tocables, tarjetas por
 * persona y timeline de movimientos con búsqueda por paciente o DNI. Es de
 * lectura: pide a /api/actividad (el servidor agrega y acota el detalle a 500).
 * Feature del plan Plus (candado como en la web).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaActividad(ctx: ContextoStaff, onSalir: () -> Unit) {
    val c = Sania.colors
    val hoy = remember { hoyClinicaIso() }
    var desde by remember { mutableStateOf(hoy) }
    var hasta by remember { mutableStateOf(hoy) }
    var datos by remember { mutableStateOf<DatosActividad?>(null) }
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var recarga by remember { mutableIntStateOf(0) }
    var personaSel by remember { mutableStateOf<String?>(null) }
    var tipoSel by remember { mutableStateOf<TipoActividad?>(null) }
    var busqueda by remember { mutableStateOf("") }
    var eligiendoDesde by remember { mutableStateOf(false) }
    var eligiendoHasta by remember { mutableStateOf(false) }

    val conPlan = ctx.can("actividadEquipo")

    LaunchedEffect(ctx.clinicaId, desde, hasta, recarga, conPlan) {
        if (!conPlan) return@LaunchedEffect
        cargando = true; error = null
        val (d, e) = ActividadRepo.cargar(desde, hasta)
        if (d != null) { datos = d; personaSel = null; tipoSel = null } else error = e
        cargando = false
    }

    if (eligiendoDesde) DialogoFecha(inicial = desde, onElegir = { n -> val dn = if (n > hoy) hoy else n; desde = dn; if (hasta < dn) hasta = dn }, onCerrar = { eligiendoDesde = false })
    if (eligiendoHasta) DialogoFecha(inicial = hasta, onElegir = { n -> hasta = if (n > hoy) hoy else n; if (desde > hasta) desde = hasta }, onCerrar = { eligiendoHasta = false })

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "← Más", color = c.sobreNavy, fontSize = Sania.txt.pequeno,
                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable { onSalir() }.padding(vertical = 2.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text("Actividad del equipo", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
                    Text("Qué se registró y quién lo hizo", color = c.sobreNavy.copy(alpha = 0.75f), fontSize = Sania.txt.mini)
                }
            }

            if (!conPlan) {
                Box(Modifier.fillMaxSize().padding(Sania.dim.xl), Alignment.TopCenter) {
                    EstadoVacio(
                        emoji = "🔒", titulo = "Actividad del equipo",
                        subtitulo = "Mira qué registró cada persona de tu equipo y con qué ritmo trabaja. Disponible en el plan Plus.",
                    )
                }
                return@Column
            }

            val d = datos
            val movimientos = remember(d, personaSel, tipoSel, busqueda) {
                if (d == null) emptyList() else filtrarMovimientos(d.detalle, personaSel, tipoSel, busqueda)
            }
            val hayFiltro = personaSel != null || tipoSel != null || busqueda.isNotBlank()

            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = Sania.dim.lg),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ── Filtro de fechas ──
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(top = Sania.dim.md).clip(RoundedCornerShape(Sania.shape.md.dp))
                            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
                    ) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Hoy" to 0, "7 días" to 6, "30 días" to 29).forEach { (t, dias) ->
                                val activo = desde == sumarDiasIso(hoy, -dias) && hasta == hoy
                                Chip(t, activo) { desde = sumarDiasIso(hoy, -dias); hasta = hoy }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f)) {
                                Etiqueta("Desde"); CajaSelectorForm(fechaDMA(desde)) { eligiendoDesde = true }
                            }
                            Column(Modifier.weight(1f)) {
                                Etiqueta("Hasta"); CajaSelectorForm(fechaDMA(hasta)) { eligiendoHasta = true }
                            }
                        }
                        Text(
                            "Cuenta lo que se registró en estas fechas, no lo agendado para ellas. Una cita creada hoy para el próximo mes cuenta hoy.",
                            color = c.textoSuave, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }

                if (cargando && d != null) {
                    item {
                        Text("Actualizando…", color = c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg).padding(8.dp), textAlign = TextAlign.Center)
                    }
                }
                if (error != null) {
                    item {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie).padding(14.dp)) {
                            Text(error.orEmpty(), color = c.error, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Reintentar", color = c.navy, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { recarga++ }.padding(vertical = 8.dp),
                            )
                        }
                    }
                }
                if (cargando && d == null && error == null) item { CargandoLista(filas = 4, conAvatar = false, conMargen = false) }

                if (d != null) {
                    // ── Resumen: 4 tarjetas tocables que filtran el detalle ──
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TarjetaResumen(
                                    "📝", "Registros creados", d.creados.total, Modifier.weight(1f), tipoSel == TipoActividad.CREADOS, c.teal,
                                    sub = if (d.creados.total > 0) "${d.creados.citas} citas · ${d.creados.sesiones} sesiones" else null,
                                ) { tipoSel = if (tipoSel == TipoActividad.CREADOS) null else TipoActividad.CREADOS }
                                TarjetaResumen(
                                    "🧑", if (d.creados.pacientes == 1) "${LocalTerminologiaPaciente.current.Paciente} nuevo" else "${LocalTerminologiaPaciente.current.Pacientes} nuevos", d.creados.pacientes,
                                    Modifier.weight(1f), tipoSel == TipoActividad.PACIENTES, c.info,
                                ) { tipoSel = if (tipoSel == TipoActividad.PACIENTES) null else TipoActividad.PACIENTES }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TarjetaResumen(
                                    "✅", "Sesiones completadas", d.sesionesCompletadas, Modifier.weight(1f), tipoSel == TipoActividad.COMPLETADAS, c.ok,
                                ) { tipoSel = if (tipoSel == TipoActividad.COMPLETADAS) null else TipoActividad.COMPLETADAS }
                                TarjetaResumen(
                                    "🗑", "Eliminaciones", d.eliminados, Modifier.weight(1f), tipoSel == TipoActividad.ELIMINADOS, c.pend,
                                    sub = if (d.eliminados > 0) "Revisar" else null,
                                ) { tipoSel = if (tipoSel == TipoActividad.ELIMINADOS) null else TipoActividad.ELIMINADOS }
                            }
                        }
                    }

                    // ── Por persona ──
                    item { Titulo("Por persona") }
                    if (d.equipo.isEmpty()) {
                        item {
                            Text(
                                "Nadie registró nada ${if (d.esHoy) "hoy" else "en estas fechas"}.",
                                color = c.textoSuave, fontSize = 13.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie).padding(20.dp),
                            )
                        }
                    } else {
                        itemsIndexed(d.equipo, key = { i, p -> "p$i-${p.quien}" }) { _, p ->
                            TarjetaPersona(p, activo = personaSel == p.quien) { personaSel = if (personaSel == p.quien) null else p.quien }
                        }
                    }

                    // ── Detalle ──
                    if (d.detalle.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Titulo(
                                    (tipoSel?.etiqueta ?: personaSel?.let { "Qué hizo $it" } ?: "Detalle de movimientos") +
                                        (if (tipoSel != null && personaSel != null) " · $personaSel" else "") +
                                        (if (hayFiltro && movimientos.isNotEmpty()) " · ${movimientos.size} resultado${if (movimientos.size == 1) "" else "s"}" else ""),
                                )
                                OutlinedTextField(
                                    value = busqueda, onValueChange = { busqueda = it }, singleLine = true,
                                    placeholder = { Text("🔍 Buscar ${LocalTerminologiaPaciente.current.paciente} o DNI…") }, colors = coloresCampoForm(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (hayFiltro) {
                                    Text(
                                        "✕ Ver todos", color = c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                            .clickable { personaSel = null; tipoSel = null; busqueda = "" }.padding(vertical = 6.dp, horizontal = 4.dp),
                                    )
                                }
                            }
                        }
                        if (movimientos.isEmpty()) {
                            item {
                                Text(
                                    if (hayFiltro) "Nada coincide con los filtros aplicados." else "Sin movimientos.",
                                    color = c.textoSuave, fontSize = 13.sp, textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie).padding(20.dp),
                                )
                            }
                        } else {
                            itemsIndexed(movimientos, key = { i, m -> "m$i-${m.cuando}" }) { _, m ->
                                FilaMovimiento(m, mostrarDia = !d.esHoy, mostrarQuien = personaSel == null)
                            }
                        }
                        if (d.detalle.size >= 500) {
                            item {
                                Text(
                                    "Se muestran los 500 movimientos más recientes. Acota las fechas para ver el resto.",
                                    color = c.pend, fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(Sania.dim.xl)) }
            }
        }
    }
}

@Composable
private fun Etiqueta(t: String) {
    Text(t.uppercase(), color = Sania.colors.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, modifier = Modifier.padding(bottom = 5.dp))
}

@Composable
private fun Titulo(t: String) {
    Text(t.uppercase(), color = Sania.colors.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Chip(texto: String, activo: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Text(
        texto, color = if (activo) c.sobreNavy else c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
            .background(if (activo) c.navy else c.fondo)
            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun TarjetaResumen(
    icono: String, etiqueta: String, valor: Int, modifier: Modifier, activa: Boolean, color: Color,
    sub: String? = null, onClick: () -> Unit,
) {
    val c = Sania.colors
    // En 0 no es tocable: llevar a una lista vacía frustra sin aportar nada.
    val tocable = valor > 0
    Column(
        modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(if (activa) 2.dp else 1.dp, if (activa) c.navy else c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .clickable(enabled = tocable) { onClick() }.padding(12.dp),
    ) {
        Text("$icono  $etiqueta", color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(valor.toString(), color = if (tocable) color else c.textoSuave, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        if (sub != null) Text(sub, color = c.textoSuave, fontSize = 11.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaPersona(p: PersonaActividad, activo: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(if (activo) c.chipBg else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .clickable { onClick() }.padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(c.chipBg), Alignment.Center) {
                Text(inicialesActividad(p.quien), color = c.navy, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.quien, color = c.texto, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                Text(if (activo) "Viendo su detalle" else "Toca para ver el detalle", color = c.textoSuave, fontSize = 11.sp)
            }
            Text(if (activo) "▾" else "›", color = c.textoSuave, fontSize = 18.sp)
        }
        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Pildora(p.creados, "creados", c.teal)
            Pildora(p.completadas, "completadas", c.ok)
            Pildora(p.editados, "editados", c.info)
            Pildora(p.eliminados, "eliminados", c.error)
        }
    }
}

/** Contador con color semántico; en 0 se ve apagado para que destaque lo que sí pasó. */
@Composable
private fun Pildora(n: Int, label: String, color: Color) {
    val c = Sania.colors
    val vacio = n == 0
    Text(
        "$n $label", color = if (vacio) c.textoSuave else color, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
            .background(if (vacio) c.fondo else color.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/** Color e icono por tipo de movimiento: se distingue de un vistazo sin leer. */
@Composable
private fun estiloDe(accion: String): Pair<Color, String> {
    val c = Sania.colors
    return when (accion) {
        "DELETE" -> c.error to "🗑"
        "COMPLETAR" -> c.ok to "✅"
        "INSERT" -> c.teal to "＋"
        else -> c.info to "✎"
    }
}

@Composable
private fun FilaMovimiento(m: MovimientoActividad, mostrarDia: Boolean, mostrarQuien: Boolean) {
    val c = Sania.colors
    val (color, icono) = estiloDe(m.accion)
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(52.dp).padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(horaDeMovimiento(m.cuando), color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (mostrarDia) Text(diaDeMovimiento(m.cuando).drop(5), color = c.textoSuave, fontSize = 10.sp)
        }
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
                .drawBehind { drawRect(color, size = Size(4.dp.toPx(), size.height)) }
                .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Row {
                Text(icono, color = color, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text(m.que, color = c.texto, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            }
            if (!m.paciente.isNullOrBlank()) {
                Text(
                    m.paciente + (m.dni?.let { "  ·  DNI $it" } ?: ""),
                    color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp),
                )
            }
            Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (mostrarQuien) Text(m.quien, color = c.textoSuave, fontSize = 11.sp)
                if (m.agendadaPara != null) {
                    Text(
                        "📅 ${m.agendadaPara}", color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}
