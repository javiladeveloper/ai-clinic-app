package pe.saniape.app.ui.clinica

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import pe.saniape.app.data.staff.AgendaRepo
import pe.saniape.app.data.staff.CitaStaff
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.ui.theme.Sania

/**
 * Agenda del staff: tira de días + citas del día con acciones (confirmar, completar,
 * cancelar). Respeta el scope (si es profesional vinculado, solo sus citas).
 */
@Composable
fun PantallaAgenda(ctx: ContextoStaff) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()

    val hoy = remember { hoyIso() }
    var fechaSel by remember { mutableStateOf(hoy) }
    var cargando by remember { mutableStateOf(true) }
    var citas by remember { mutableStateOf<List<CitaStaff>>(emptyList()) }
    var accionando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }

    suspend fun recargar() {
        cargando = true
        try { citas = AgendaRepo.citasDelDia(fechaSel, ctx.miTerapeutaId) }
        catch (_: Exception) { citas = emptyList() }
        cargando = false
    }

    LaunchedEffect(fechaSel) { recargar() }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Barra
            Box(Modifier.fillMaxWidth().background(c.navyDark)
                .padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg)) {
                Text("Agenda", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
            }

            // Tira de días (7 días desde hoy)
            LazyRow(
                Modifier.fillMaxWidth().padding(vertical = Sania.dim.sm),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Sania.dim.lg),
            ) {
                items(diasDesde(hoy, 14)) { dia ->
                    val activo = dia.iso == fechaSel
                    Column(
                        Modifier.size(width = 52.dp, height = 64.dp)
                            .clip(RoundedCornerShape(Sania.shape.md.dp))
                            .background(if (activo) c.navy else c.superficie)
                            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.md.dp))
                            .clickable { fechaSel = dia.iso }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(dia.diaSemana, color = if (activo) c.sobreNavy else c.textoSuave, fontSize = 11.sp)
                        Text(dia.diaMes, color = if (activo) c.sobreNavy else c.texto,
                            fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            mensaje?.let {
                Text(it, color = c.navy, fontSize = Sania.txt.pequeno,
                    modifier = Modifier.padding(horizontal = Sania.dim.lg, vertical = 4.dp))
            }

            when {
                cargando -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = c.navy) }
                citas.isEmpty() -> Box(Modifier.fillMaxSize().padding(Sania.dim.xxl), Alignment.Center) {
                    Text("No hay citas para este día.", color = c.textoSuave, fontSize = Sania.txt.cuerpo)
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = Sania.dim.lg),
                    verticalArrangement = Arrangement.spacedBy(Sania.dim.md),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Sania.dim.md),
                ) {
                    items(citas) { cita ->
                        TarjetaCitaStaff(
                            cita = cita,
                            accionando = accionando,
                            onAccion = { accion ->
                                if (accionando) return@TarjetaCitaStaff
                                accionando = true; mensaje = null
                                scope.launch {
                                    val ok = when (accion) {
                                        "confirmar" -> AgendaRepo.confirmar(cita.id)
                                        "completar" -> AgendaRepo.completar(cita.id)
                                        "revertir" -> AgendaRepo.revertir(cita.id)
                                        "cancelar" -> AgendaRepo.cancelar(cita.id)
                                        else -> false
                                    }
                                    mensaje = if (ok) "✓ Listo" else "⚠ No se pudo, intenta de nuevo"
                                    recargar()
                                    accionando = false
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaCitaStaff(cita: CitaStaff, accionando: Boolean, onAccion: (String) -> Unit) {
    val c = Sania.colors
    val colorTipo = when (cita.tipo) {
        "Evaluación" -> c.info
        "Sesión" -> c.ok
        else -> c.navy
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(Sania.dim.tarjeta),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(colorTipo))
            Spacer(Modifier.width(8.dp))
            Text(cita.hora.take(5), color = c.navy, fontSize = Sania.txt.seccion, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            BadgeEstadoCita(cita.estado)
        }
        Spacer(Modifier.height(6.dp))
        Text(cita.pacienteNombre ?: "Paciente", color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.SemiBold)
        Text(
            listOfNotNull(cita.tipo, cita.procedimiento, cita.terapeutaNombre?.let { "con $it" })
                .joinToString(" · "),
            color = c.textoSuave, fontSize = 12.sp,
        )

        // Acciones según estado
        val acciones = accionesPara(cita.estado)
        if (acciones.isNotEmpty()) {
            Spacer(Modifier.height(Sania.dim.md))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                acciones.forEach { (label, accion, color) ->
                    Box(
                        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .background(color.copy(alpha = 0.12f))
                            .border(1.dp, color, RoundedCornerShape(Sania.shape.sm.dp))
                            .clickable(enabled = !accionando) { onAccion(accion) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Acciones disponibles según el estado de la cita: (label, accion, color). */
@Composable
private fun accionesPara(estado: String): List<Triple<String, String, Color>> {
    val c = Sania.colors
    return when (estado) {
        "Pendiente" -> listOf(
            Triple("✓ Confirmar", "confirmar", c.ok),
            Triple("✓ Completar", "completar", c.navy),
            Triple("✕ Cancelar", "cancelar", c.error),
        )
        "Confirmada" -> listOf(
            Triple("✓ Completar", "completar", c.navy),
            Triple("✕ Cancelar", "cancelar", c.error),
        )
        "Completada" -> listOf(
            Triple("↩ Revertir", "revertir", c.pend),
        )
        else -> emptyList()
    }
}

// ── Helpers de fecha ──
private data class DiaTira(val iso: String, val diaSemana: String, val diaMes: String)

// Indexado por dayOfWeek.ordinal: MONDAY=0 .. SUNDAY=6
private val DIAS_ES = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

private fun hoyIso(): String {
    val d = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${d.year}-${d.monthNumber.toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}"
}

private fun diasDesde(isoInicio: String, n: Int): List<DiaTira> {
    val partes = isoInicio.split("-")
    var fecha = kotlinx.datetime.LocalDate(partes[0].toInt(), partes[1].toInt(), partes[2].toInt())
    val lista = mutableListOf<DiaTira>()
    repeat(n) {
        lista.add(DiaTira(
            iso = "${fecha.year}-${fecha.monthNumber.toString().padStart(2, '0')}-${fecha.dayOfMonth.toString().padStart(2, '0')}",
            diaSemana = DIAS_ES[fecha.dayOfWeek.ordinal],
            diaMes = fecha.dayOfMonth.toString(),
        ))
        fecha = fecha.plus(DatePeriod(days = 1))
    }
    return lista
}