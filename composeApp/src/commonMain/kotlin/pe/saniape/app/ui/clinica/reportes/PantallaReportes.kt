package pe.saniape.app.ui.clinica.reportes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.staff.AgendaRepo
import pe.saniape.app.data.staff.BalanceMes
import pe.saniape.app.data.staff.CatalogosCobroRepo
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.HitoMes
import pe.saniape.app.data.staff.PuntoMes
import pe.saniape.app.data.staff.ReportesRepo
import pe.saniape.app.data.staff.ResultadoSeries
import pe.saniape.app.data.staff.SedeActiva
import pe.saniape.app.data.staff.SeriesReporte
import pe.saniape.app.data.staff.TIPOS_HITO
import pe.saniape.app.data.staff.TerapeutaRef
import pe.saniape.app.data.staff.balanceUltimoCerrado
import pe.saniape.app.data.staff.csvReporteMensual
import pe.saniape.app.data.staff.emojiHito
import pe.saniape.app.data.staff.entero
import pe.saniape.app.data.staff.etiquetaMes
import pe.saniape.app.data.staff.mesConAnio
import pe.saniape.app.data.staff.nombreCsvReporte
import pe.saniape.app.data.staff.solesGrafico
import pe.saniape.app.data.staff.titularDe
import pe.saniape.app.ui.AlertaConTeclado
import pe.saniape.app.ui.CargandoLista
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.ChipSede
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania

/**
 * 📈 Reportes (nativo). Gemelo de app/(app)/reportes/page.tsx de la web:
 * "cómo viene el negocio mes a mes" — pacientes nuevos, citas atendidas,
 * sesiones e ingresos/egresos de los últimos 6/12/24 meses, con los filtros de
 * la web (sede del selector global, método de pago y profesional), titulares
 * del mes con su comparación honesta, "qué pasó cada mes" y exportar a CSV.
 *
 * Las otras dos vistas de la web (Rendimiento y Pacientes nuevos) ya son
 * nativas: aquí se abren con [onAbrirPacientesPeriodo] / [onAbrirPacientesNuevos].
 *
 * Permiso `reportes` (lo decide el padre) + plan con reportes (Premium): sin
 * plan, el mismo aviso 💎 que muestran los otros reportes de la app.
 */
@Composable
fun PantallaReportes(
    ctx: ContextoStaff,
    onSalir: () -> Unit,
    onAbrirPacientesPeriodo: (() -> Unit)? = null,
    onAbrirPacientesNuevos: (() -> Unit)? = null,
) = pe.saniape.app.tutoriales.PantallaTutorial("Reportes") {
    val c = Sania.colors
    val acciones = recordarAcciones()

    var meses by remember { mutableIntStateOf(12) }
    // Filtros de la web: por dónde entró el dinero y quién lo generó. La sede
    // sale sola del selector global (SedeActiva).
    var metodo by remember { mutableStateOf("") }
    var terapeutaId by remember { mutableStateOf("") }
    var metodos by remember { mutableStateOf<List<String>>(emptyList()) }
    var terapeutas by remember { mutableStateOf<List<TerapeutaRef>>(emptyList()) }

    var series by remember { mutableStateOf<SeriesReporte?>(null) }
    var error by remember { mutableStateOf<ResultadoSeries.Error?>(null) }
    var cargando by remember { mutableStateOf(true) }
    var intento by remember { mutableIntStateOf(0) }

    val sedeEstado by SedeActiva.estado.collectAsState()
    val sede = sedeEstado.filtro?.sedeId
    // Multisede: mientras el diálogo obligatorio de sede esté abierto (o el usuario
    // no tenga sedes activas) no se pide nada — si no, primero saldría el
    // consolidado y una consulta pesada de más. Abrir "cambiar de sede" no cuenta:
    // la sede no cambia hasta elegir, y entonces cambia `sede`.
    val esperandoSede = sedeEstado.multiSede && (sedeEstado.obligatorio || sedeEstado.sinSedes)
    val conPlan = ctx.can("reportes")
    // El dinero (titular de ingresos y columnas del CSV) solo con permiso de finanzas,
    // como los gráficos de ingresos/egresos.
    val conDinero = ctx.puede("finanzas")

    LaunchedEffect(ctx.clinicaId) {
        if (!conPlan) return@LaunchedEffect
        metodos = runCatching { CatalogosCobroRepo.nombresMetodos() }.getOrDefault(emptyList())
        terapeutas = runCatching { AgendaRepo.terapeutasActivos() }.getOrDefault(emptyList())
    }

    // Cambiar un filtro cancela la carga anterior (LaunchedEffect): una
    // respuesta vieja nunca pisa a la nueva.
    LaunchedEffect(ctx.clinicaId, meses, sede, metodo, terapeutaId, intento, esperandoSede) {
        if (!conPlan || esperandoSede) { cargando = false; return@LaunchedEffect }
        cargando = true
        when (val r = ReportesRepo.series(meses, sede, metodo, terapeutaId)) {
            is ResultadoSeries.Ok -> { series = r.series; error = null }
            // Los números viejos NO quedan bajo los filtros nuevos: se ve el error con "Reintentar".
            is ResultadoSeries.Error -> { error = r; series = null }
        }
        cargando = false
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Cabecera
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
                    Text("📈 Reportes", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
                    Text("Cómo viene el negocio mes a mes", color = c.sobreNavy.copy(alpha = 0.8f), fontSize = Sania.txt.mini)
                    ChipSede(Modifier.padding(top = 4.dp))
                }
                val s = series
                if (conPlan && s != null) {
                    Box(
                        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                            .background(c.sobreNavy.copy(alpha = 0.15f))
                            .clickable {
                                acciones.compartirArchivo(
                                    nombre = nombreCsvReporte(s.hoy),
                                    contenido = csvReporteMensual(s, conDinero = conDinero),
                                    mime = "text/csv",
                                    titulo = "Compartir reporte mensual",
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text("⬆ Compartir CSV", color = c.sobreNavy, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = Sania.dim.lg, vertical = Sania.dim.md),
            ) {
                // Las tres vistas de la web: Mes a mes (esta), Rendimiento y Pacientes nuevos.
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ChipFiltro("📈 Mes a mes", activo = true) {}
                    onAbrirPacientesPeriodo?.let { ChipFiltro("🎯 Pacientes del período", activo = false, onClick = it) }
                    onAbrirPacientesNuevos?.let { ChipFiltro("🌱 Pacientes nuevos", activo = false, onClick = it) }
                }
                Spacer(Modifier.height(Sania.dim.md))

                if (!conPlan) {
                    MensajeReportes("💎", "Los reportes mensuales están disponibles desde el plan Premium.")
                    return@Column
                }

                // Filtros
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(6, 12, 24).forEach { m ->
                        ChipFiltro("$m meses", activo = meses == m) { meses = m }
                    }
                }
                Spacer(Modifier.height(Sania.dim.sm))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectorFiltro(
                        etiquetaVacia = "Todos los métodos",
                        valor = metodo,
                        opciones = metodos.map { it to it },
                        onElegir = { metodo = it },
                        modifier = Modifier.weight(1f),
                    )
                    SelectorFiltro(
                        etiquetaVacia = "Todo el equipo",
                        valor = terapeutaId,
                        opciones = terapeutas.map { it.id to it.nombre },
                        onElegir = { terapeutaId = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (metodo.isNotBlank()) {
                    // Igual que el servidor: con método elegido solo cuentan ingresos.
                    Text(
                        "Con un método de pago elegido, los egresos no se cuentan (no se “cobran” por un método).",
                        color = c.textoSuave, fontSize = Sania.txt.mini, modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.height(Sania.dim.md))

                val s = series
                val e = error
                when {
                    esperandoSede -> MensajeReportes("🏥", "Elige una sede para ver sus reportes.")
                    cargando && s == null -> CargandoLista(filas = 4, conAvatar = false, conMargen = false)
                    e != null && s == null -> MensajeReportes(
                        emoji = if (e.porPlan) "💎" else "⚠",
                        texto = e.mensaje,
                        textoAccion = if (e.porPlan) null else "Reintentar",
                        onAccion = { intento++ },
                    )
                    s != null -> {
                        if (cargando) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                                CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Actualizando…", color = c.textoSuave, fontSize = Sania.txt.mini)
                            }
                        } else if (e != null) {
                            Text("⚠ ${e.mensaje}", color = c.error, fontSize = Sania.txt.mini, modifier = Modifier.padding(bottom = 6.dp))
                        }
                        ContenidoSeries(
                            ctx = ctx,
                            s = s,
                            onCambioHitos = { intento++ },
                            onAbrirFinanzas = { acciones.abrirUrl("${Supabase.SITE_URL}/finanzas") },
                        )
                    }
                }
                Spacer(Modifier.height(Sania.dim.xl))
            }
        }
    }
}

@Composable
private fun ContenidoSeries(
    ctx: ContextoStaff,
    s: SeriesReporte,
    onCambioHitos: () -> Unit,
    onAbrirFinanzas: () -> Unit,
) {
    val c = Sania.colors
    // Titulares del mes en curso (2 por fila a 360 dp).
    val tarjetas = buildList {
        add(Triple("Pacientes nuevos", "👥", s.pacientes) to false)
        add(Triple("Citas atendidas", "📅", s.citas) to false)
        if (ctx.usaSesiones) add(Triple("Sesiones", "🏃", s.sesiones) to false)
        // El dinero solo con permiso de finanzas (como los gráficos de ingresos/egresos).
        if (ctx.puede("finanzas")) add(Triple("Ingresos", "💸", s.ingresos) to true)
    }
    tarjetas.chunked(2).forEach { fila ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Sania.dim.sm)) {
            fila.forEach { (t, dinero) ->
                TarjetaMetrica(t.first, t.second, t.third, dinero, Modifier.weight(1f))
            }
            if (fila.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(Sania.dim.sm))
    }
    Spacer(Modifier.height(Sania.dim.sm))

    TarjetaGrafico(
        "👥 Pacientes nuevos por mes",
        "Cuándo entró cada paciente a la clínica. El mes en curso va en gris: todavía no termina.",
    ) {
        GraficoMensual(s.pacientes, forma = FormaGrafico.LINEA, serie = 1, hitos = s.hitos, unidad = "pacientes")
    }
    TarjetaGrafico("📅 Citas atendidas", "Solo las completadas.") {
        GraficoMensual(s.citas, serie = 1, hitos = s.hitos, unidad = "citas")
    }
    if (ctx.usaSesiones) {
        TarjetaGrafico("🏃 Sesiones realizadas", "Sesiones de tratamiento completadas.") {
            GraficoMensual(s.sesiones, serie = 4, hitos = s.hitos, unidad = "sesiones")
        }
    }
    // Ingresos y egresos: solo con permiso de finanzas (como la web).
    if (ctx.puede("finanzas")) {
        TarjetaGrafico("💸 Ingresos y egresos", "Lo que entró y salió de caja cada mes.") {
            val p = paletaGrafico()
            Leyenda("Ingresos", p.serie2)
            GraficoMensual(s.ingresos, serie = 2, dinero = true, hitos = s.hitos, altura = 170.dp)
            Spacer(Modifier.height(Sania.dim.md))
            Leyenda("Egresos", p.serie3)
            GraficoMensual(s.egresos, serie = 3, dinero = true, hitos = s.hitos, altura = 170.dp)
            balanceUltimoCerrado(s.ingresos, s.egresos)?.let {
                Spacer(Modifier.height(Sania.dim.md))
                ResumenBalance(it)
            }
            Text(
                "Ver Finanzas y caja ↗", color = c.navy, fontSize = Sania.txt.pequeno, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .clickable { onAbrirFinanzas() }.padding(vertical = 4.dp),
            )
        }
    }

    HitosDelMes(hitos = s.hitos, meses = s.pacientes.map { it.mes }, onCambio = onCambioHitos)
}

/**
 * Titular de una métrica del mes en curso (TarjetaMetrica.tsx). La comparación
 * es contra el MISMO TRAMO del mes pasado, no contra su total.
 */
@Composable
private fun TarjetaMetrica(titulo: String, icono: String, serie: List<PuntoMes>, dinero: Boolean, modifier: Modifier) {
    val c = Sania.colors
    val t = titularDe(serie) ?: return
    val fmt: (Double) -> String = { if (dinero) solesGrafico(it) else entero(it) }
    Box(
        modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(12.dp),
    ) {
        Text(icono, fontSize = 22.sp, modifier = Modifier.align(Alignment.TopEnd).alpha(0.08f))
        Column {
            Text(titulo.uppercase(), color = c.lav, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(fmt(t.valor), color = c.texto, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (t.parcial) {
                Text(
                    "${t.diasTranscurridos ?: "?"} de ${t.diasDelMes ?: "?"} días" +
                        (t.proyectado?.let { " · va camino de ${fmt(it)}" } ?: ""),
                    color = c.textoSuave, fontSize = Sania.txt.mini,
                )
                val v = t.variacion
                if (v != null) {
                    Text(
                        "${if (v > 0) "▲" else if (v < 0) "▼" else "="} ${kotlin.math.abs(v)}% vs. mismo tramo del mes pasado",
                        color = if (v > 0) c.ok else if (v < 0) c.pend else c.textoSuave,
                        fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold,
                    )
                } else if (t.mismoTramoAnterior != null) {
                    // Sin base suficiente se dice el número pelado, que no engaña.
                    Text("A estas alturas del mes pasado: ${fmt(t.mismoTramoAnterior)}", color = c.textoSuave, fontSize = Sania.txt.mini)
                }
            } else {
                Text("este mes", color = c.lav, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TarjetaGrafico(titulo: String, ayuda: String, contenido: @Composable ColumnScope.() -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().padding(bottom = Sania.dim.md).clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .padding(Sania.dim.lg),
    ) {
        Text(titulo, color = c.texto, fontSize = Sania.txt.seccion, fontWeight = FontWeight.Bold)
        Text(ayuda, color = c.textoSuave, fontSize = Sania.txt.mini, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
        contenido()
    }
}

@Composable
private fun Leyenda(texto: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(texto, color = Sania.colors.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
    }
}

/** Balance del último mes cerrado (el en curso aún no es comparable). */
@Composable
private fun ResumenBalance(b: BalanceMes) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text("Último mes cerrado (${b.etiqueta})", color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Entró ${solesGrafico(b.ingresos)}", color = c.texto, fontSize = Sania.txt.pequeno)
            Text("Salió ${solesGrafico(b.egresos)}", color = c.texto, fontSize = Sania.txt.pequeno)
        }
        Text(
            "Balance ${solesGrafico(b.balance)}",
            color = if (b.balance >= 0) c.ok else c.error, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * "Qué pasó cada mes" (HitosDelMes.tsx): anotar una promoción o un feriado y
 * queda marcado en los gráficos. Así, meses después, se sigue sabiendo por qué
 * ese mes fue distinto.
 */
@Composable
private fun HitosDelMes(hitos: List<HitoMes>, meses: List<String>, onCambio: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var anotando by remember { mutableStateOf(false) }
    var aBorrar by remember { mutableStateOf<HitoMes?>(null) }
    val anio = meses.lastOrNull()?.take(4)?.toIntOrNull()
    val ordenados = remember(hitos) { hitos.sortedByDescending { it.mes } }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .padding(Sania.dim.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🏷 Qué pasó cada mes", color = c.texto, fontSize = Sania.txt.seccion,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.navy)
                    .clickable { anotando = true }.padding(horizontal = 14.dp, vertical = 7.dp),
            ) { Text("+ Anotar", color = c.sobreNavy, fontSize = Sania.txt.pequeno, fontWeight = FontWeight.Bold) }
        }
        Text(
            "Anota una promoción o un feriado y queda marcado en los gráficos. Así, meses después, " +
                "sigues sabiendo por qué ese mes fue distinto.",
            color = c.textoSuave, fontSize = Sania.txt.mini, modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(Sania.dim.md))
        if (ordenados.isEmpty()) {
            Text(
                "Todavía no anotaste nada.\nPor ejemplo: «Promos de Fiestas Patrias» en julio.",
                color = c.textoSuave, fontSize = Sania.txt.pequeno, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(vertical = 18.dp, horizontal = 12.dp),
            )
        } else {
            ordenados.forEach { h ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                        .padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(etiquetaMes(h.mes, anio), color = c.lav, fontSize = Sania.txt.mini,
                        fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
                    Column(Modifier.weight(1f)) {
                        Text(h.titulo, color = c.texto, fontSize = Sania.txt.pequeno, fontWeight = FontWeight.Bold)
                        h.detalle?.let { Text(it, color = c.textoSuave, fontSize = Sania.txt.mini) }
                    }
                    Text(emojiHito(h.tipo), fontSize = 14.sp)
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).clickable { aBorrar = h },
                        contentAlignment = Alignment.Center,
                    ) { Text("✕", color = c.textoSuave, fontSize = Sania.txt.pequeno) }
                }
            }
        }
    }

    if (anotando) {
        DialogoAnotarHito(meses = meses, onCerrar = { anotando = false }, onAnotado = { anotando = false; onCambio() })
    }
    aBorrar?.let { h ->
        var borrando by remember(h.id) { mutableStateOf(false) }
        AlertaConTeclado(
            onDismissRequest = { if (!borrando) aBorrar = null },
            title = { Text("¿Borrar esta nota?") },
            text = { Text("«${h.titulo}» (${mesConAnio(h.mes)}) deja de marcarse en los gráficos.") },
            confirmButton = {
                TextButton(enabled = !borrando, onClick = {
                    borrando = true
                    scope.launch {
                        val error = ReportesRepo.borrarHito(h.id)
                        borrando = false
                        aBorrar = null
                        if (error == null) { Toaster.exito("Borrado"); onCambio() } else Toaster.error(error)
                    }
                }) { Text(if (borrando) "Borrando…" else "Borrar", color = c.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { aBorrar = null }) { Text("Cancelar", color = c.textoSuave) } },
        )
    }
}

@Composable
private fun DialogoAnotarHito(meses: List<String>, onCerrar: () -> Unit, onAnotado: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var mes by remember { mutableStateOf(meses.lastOrNull().orEmpty()) }
    var tipo by remember { mutableStateOf("promocion") }
    var titulo by remember { mutableStateOf("") }
    var detalle by remember { mutableStateOf("") }
    var guardando by remember { mutableStateOf(false) }
    var menuMes by remember { mutableStateOf(false) }

    fun guardar() {
        if (guardando) return
        if (titulo.isBlank()) { Toaster.error("Ponle un nombre"); return }
        guardando = true
        scope.launch {
            val error = ReportesRepo.anotarHito(mes, titulo, detalle, tipo)
            guardando = false
            if (error == null) { Toaster.exito("Anotado"); onAnotado() } else Toaster.error(error)
        }
    }

    DialogoForm(
        titulo = "Anotar qué pasó",
        subtitulo = "Queda marcado con 🏷 en los gráficos de ese mes",
        textoAccion = if (guardando) "Guardando…" else "Anotar",
        accionHabilitada = !guardando && titulo.isNotBlank() && mes.isNotBlank(),
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = { guardar() },
    ) {
        EtqForm("Mes")
        Box {
            pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm(if (mes.isBlank()) "Elegir mes" else mesConAnio(mes)) { menuMes = true }
            DropdownMenu(expanded = menuMes, onDismissRequest = { menuMes = false }) {
                meses.reversed().forEach { m ->
                    DropdownMenuItem(text = { Text(mesConAnio(m)) }, onClick = { mes = m; menuMes = false })
                }
            }
        }
        Spacer(Modifier.height(Sania.dim.lg))
        EtqForm("Qué fue")
        TIPOS_HITO.chunked(2).forEach { fila ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                fila.forEach { t ->
                    val sel = tipo == t.valor
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .background(if (sel) c.chipBg else c.superficie)
                            .border(if (sel) 1.5.dp else 1.dp, if (sel) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                            .clickable { tipo = t.valor }.padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text("${t.emoji} ${t.etiqueta}", color = if (sel) c.navy else c.texto,
                            fontSize = Sania.txt.pequeno, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                        Text(t.ayuda, color = c.textoSuave, fontSize = 10.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(Sania.dim.md))
        EtqForm("Nombre")
        OutlinedTextField(
            value = titulo, onValueChange = { titulo = it },
            placeholder = { Text("Promos de Fiestas Patrias") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Sania.dim.md))
        EtqForm("Detalle (opcional)")
        OutlinedTextField(
            value = detalle, onValueChange = { detalle = it },
            placeholder = { Text("2x1 en terapias, del 20 al 31") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Selector compacto de un filtro ("Todos los métodos", "Todo el equipo"). "" = sin filtro. */
@Composable
private fun SelectorFiltro(
    etiquetaVacia: String,
    valor: String,
    opciones: List<Pair<String, String>>,
    onElegir: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(false) }
    val activo = valor.isNotBlank()
    val texto = opciones.firstOrNull { it.first == valor }?.second ?: etiquetaVacia
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                .background(if (activo) c.chipBg else c.superficie)
                .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                .clickable { abierto = true }.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(texto, color = if (activo) c.navy else c.texto, fontSize = Sania.txt.pequeno,
                fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("▾", color = c.textoSuave, fontSize = Sania.txt.pequeno)
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            DropdownMenuItem(text = { Text(etiquetaVacia) }, onClick = { onElegir(""); abierto = false })
            opciones.forEach { (id, nombre) ->
                DropdownMenuItem(text = { Text(nombre) }, onClick = { onElegir(id); abierto = false })
            }
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
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(texto, color = if (activo) c.sobreNavy else c.texto, fontSize = 12.sp,
            fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun MensajeReportes(emoji: String, texto: String, textoAccion: String? = null, onAccion: () -> Unit = {}) {
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
