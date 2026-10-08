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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.EmbudoPacientes
import pe.saniape.app.data.staff.PeriodoReporte
import pe.saniape.app.data.staff.ReportesRepo
import pe.saniape.app.data.staff.ResultadoReporte
import pe.saniape.app.data.staff.SedeActiva
import pe.saniape.app.ui.theme.Sania

/**
 * 📊 "Pacientes del período" (nativo). Gemelo del bloque BloqueEmbudo de la web
 * (Reportes → Rendimiento): cuántos pacientes se atendieron, cuántos vinieron a
 * evaluación y qué pasó con ellos (compraron paquete / sin pagar / ya tenían /
 * sin paquete). Los números vienen calculados del servidor; aquí solo se pintan.
 * Solo con permiso `reportes` (lo decide el padre); el plan lo valida el servidor.
 */
@Composable
fun PantallaPacientesPeriodo(ctx: ContextoStaff, onSalir: () -> Unit) {
    val c = Sania.colors
    var periodo by remember { mutableStateOf(PeriodoReporte.MES) }
    var resultado by remember { mutableStateOf<ResultadoReporte?>(null) }
    var cargando by remember { mutableStateOf(true) }
    var intento by remember { mutableIntStateOf(0) }

    // Multisede: el reporte es de la sede activa (en "Todas las sedes", de toda la clínica).
    val sedeEstado by SedeActiva.estado.collectAsState()
    val sede = sedeEstado.filtro?.sedeId

    LaunchedEffect(ctx.clinicaId, periodo, sede, intento) {
        cargando = true
        // Tras un error se muestra el spinner; con datos, se mantienen visibles mientras se actualiza.
        if (resultado is ResultadoReporte.Error) resultado = null
        resultado = ReportesRepo.pacientesDelPeriodo(periodo, sede)
        cargando = false
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
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
                    Text("📊 ${ctx.terminologiaPaciente.Pacientes} del período", color = c.sobreNavy,
                        fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
                    ChipSede(Modifier.padding(top = 4.dp))
                }
            }

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = Sania.dim.lg, vertical = Sania.dim.md),
            ) {
                // Período (los mismos cuatro que la web).
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PeriodoReporte.entries.forEach { p ->
                        ChipPeriodo(p.etiqueta, activo = p == periodo) { periodo = p }
                    }
                }
                Spacer(Modifier.height(Sania.dim.md))

                val r = resultado
                when {
                    cargando && r == null -> Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), Alignment.Center) {
                        CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp)
                    }
                    r is ResultadoReporte.Error -> MensajeReporte(
                        emoji = if (r.porPlan) "💎" else "⚠",
                        texto = r.mensaje,
                        textoAccion = if (r.porPlan) null else "Reintentar",
                        onAccion = { intento++ },
                    )
                    r is ResultadoReporte.Ok -> {
                        if (cargando) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                                CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Actualizando…", color = c.textoSuave, fontSize = Sania.txt.mini)
                            }
                        }
                        val e = r.reporte.embudo
                        val etiqueta = r.reporte.etiquetaPeriodo
                        when {
                            e == null -> MensajeReporte(
                                emoji = "📊",
                                texto = "Este reporte todavía no está disponible para tu clínica. Inténtalo más tarde.",
                            )
                            e.atendidos == 0 -> MensajeReporte(
                                emoji = "🗓",
                                texto = "No hubo ${ctx.terminologiaPaciente.pacientes} atendidos" + (etiqueta?.let { " en $it" } ?: " en este período") + ".",
                            )
                            else -> TarjetaEmbudo(e, etiqueta, ctx.terminologiaPaciente.pacientes)
                        }
                    }
                }
                Spacer(Modifier.height(Sania.dim.xl))
            }
        }
    }
}

@Composable
private fun TarjetaEmbudo(e: EmbudoPacientes, etiqueta: String?, pacientes: String) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .padding(Sania.dim.lg),
    ) {
        if (etiqueta != null) {
            Text(etiqueta.uppercase(), color = c.textoSuave, fontSize = Sania.txt.mini,
                fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(4.dp))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${e.atendidos}", color = c.texto, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Text(
                "$pacientes atendidos · ${e.evaluados} vinieron a evaluación",
                color = c.textoSuave, fontSize = Sania.txt.pequeno,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(Sania.dim.md))

        if (e.evaluados > 0) {
            val partes = listOf(
                ParteBarra("Compraron paquete", e.compraron, c.ok),
                ParteBarra("Paquete sin pagar", e.pendientes, c.info),
                ParteBarra("Ya tenían paquete", e.yaTenian, c.purple),
                ParteBarra("Sin paquete", e.sinPaquete, c.pend),
            )
            BarraApilada(partes)
            Spacer(Modifier.height(10.dp))
            partes.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(p.color))
                    Spacer(Modifier.width(8.dp))
                    Text(p.label, color = c.textoSuave, fontSize = Sania.txt.pequeno, modifier = Modifier.weight(1f))
                    Text("${p.valor}", color = c.texto, fontSize = Sania.txt.pequeno, fontWeight = FontWeight.Bold)
                }
            }

            // Cierre: % de los evaluados que compró paquete.
            Spacer(Modifier.height(Sania.dim.md))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.chipBg).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    "${e.tasaCompra?.let { "$it%" } ?: "—"} de los evaluados compró paquete",
                    color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold,
                )
                Text("${e.compraron} de ${e.evaluados} evaluados", color = c.textoSuave, fontSize = Sania.txt.pequeno)
                notaNoEvaluados(e)?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = c.textoSuave, fontSize = Sania.txt.pequeno)
                }
            }
        } else {
            Text("Nadie vino a evaluación en este período.", color = c.textoSuave, fontSize = Sania.txt.pequeno)
            notaNoEvaluados(e)?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = c.textoSuave, fontSize = Sania.txt.pequeno)
            }
        }

        // Evaluaciones sin cerrar: no cuentan como evaluados hasta cerrarlas (mismo aviso que la web).
        if (e.evalSinCerrar > 0) {
            Spacer(Modifier.height(Sania.dim.md))
            Text(
                "⚠ ${e.evalSinCerrar} " +
                    (if (e.evalSinCerrar == 1) "evaluación quedó" else "evaluaciones quedaron") +
                    " sin cerrar (ni atendida ni “no asistió”) y no se cuentan acá. " +
                    "Ciérralas en la agenda para que el reporte esté completo.",
                color = c.pend, fontSize = Sania.txt.pequeno, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.pendBg).padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }

        Spacer(Modifier.height(Sania.dim.md))
        Text(
            "Evaluados: los que vinieron a una cita de Evaluación (las consultas no cuentan, salvo que en " +
                "tu clínica la consulta sea la evaluación). Compraron: pagaron (aunque sea una parte de) un " +
                "paquete dentro del período.",
            color = c.textoSuave, fontSize = Sania.txt.mini,
        )
    }
}

/** "Aparte, no se evaluaron: N vinieron solo a consulta · M solo a sesiones…" (null si no hay). */
internal fun notaNoEvaluados(e: EmbudoPacientes): String? {
    if (e.soloConsulta <= 0 && e.soloSesiones <= 0) return null
    val partes = buildList {
        if (e.soloConsulta > 0) add("${e.soloConsulta} vinieron solo a consulta")
        if (e.soloSesiones > 0) add("${e.soloSesiones} solo a sesiones de un paquete anterior")
    }
    return "Aparte, no se evaluaron: " + partes.joinToString(" · ")
}

private data class ParteBarra(val label: String, val valor: Int, val color: Color)

@Composable
private fun BarraApilada(partes: List<ParteBarra>) {
    val c = Sania.colors
    val total = partes.sumOf { it.valor }
    Row(
        Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.chipBg),
    ) {
        if (total > 0) partes.filter { it.valor > 0 }.forEach { p ->
            Box(Modifier.weight(p.valor.toFloat()).fillMaxHeight().background(p.color))
        }
    }
}

@Composable
private fun ChipPeriodo(texto: String, activo: Boolean, onClick: () -> Unit) {
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
private fun MensajeReporte(emoji: String, texto: String, textoAccion: String? = null, onAccion: () -> Unit = {}) {
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
