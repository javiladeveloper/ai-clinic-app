package pe.saniape.app.ui.clinica.agenda.modales

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.ResumenClinico
import pe.saniape.app.data.staff.ResumenClinicoRepo
import pe.saniape.app.data.staff.TratamientoResumen
import pe.saniape.app.ui.theme.Sania

/**
 * Popup de RESUMEN CLÍNICO que el fisio abre al tocar el nombre del paciente en su agenda.
 * Muestra lo que necesita para atender de un vistazo (igual que la web): motivo de consulta,
 * diagnóstico, tratamiento en curso e historial. Carga async desde el endpoint de staff.
 */
@Composable
fun ResumenClinicoSheet(
    pacienteId: String,
    onCerrar: () -> Unit,
    onVerFicha: () -> Unit,
) {
    val c = Sania.colors
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var dato by remember { mutableStateOf<ResumenClinico?>(null) }

    LaunchedEffect(pacienteId) {
        cargando = true; error = false
        val r = ResumenClinicoRepo.cargar(pacienteId)
        dato = r
        error = r == null
        cargando = false
    }

    AlertDialog(
        onDismissRequest = onCerrar,
        title = {
            Column {
                Text(dato?.nombre ?: "Resumen clínico", fontWeight = FontWeight.Bold, color = c.texto)
                // Edad · ocupación (subtítulo sutil).
                val sub = listOfNotNull(
                    dato?.edad?.let { "$it años" },
                    dato?.ocupacion?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(sub, color = c.textoSuave, fontSize = Sania.txt.pequeno,
                        modifier = Modifier.padding(top = 2.dp))
                }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                when {
                    cargando -> Row(
                        Modifier.fillMaxWidth().padding(vertical = Sania.dim.xl),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.height(0.dp))
                        Text("  Cargando…", color = c.textoSuave, fontSize = Sania.txt.cuerpo)
                    }
                    error || dato == null -> Box(
                        Modifier.fillMaxWidth().padding(vertical = Sania.dim.xl),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("No se pudo cargar el resumen.", color = c.error, fontSize = Sania.txt.cuerpo)
                    }
                    else -> Contenido(dato!!)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onVerFicha) {
                Text("Ver ficha completa →", color = c.navy, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cerrar", color = c.textoSuave) } },
        containerColor = c.superficie,
        shape = RoundedCornerShape(Sania.shape.lg.dp),
    )
}

@Composable
private fun Contenido(r: ResumenClinico) {
    val c = Sania.colors

    // MOTIVO DE CONSULTA (destacado): por qué vino el paciente.
    Seccion("MOTIVO DE CONSULTA") {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                .background(c.pendBg).padding(Sania.dim.md),
        ) {
            Text(
                r.motivo?.takeIf { it.isNotBlank() } ?: "Sin motivo registrado.",
                color = if (r.motivo.isNullOrBlank()) c.textoSuave else c.pend,
                fontSize = Sania.txt.cuerpo,
                fontWeight = if (r.motivo.isNullOrBlank()) FontWeight.Normal else FontWeight.SemiBold,
            )
        }
    }

    // DIAGNÓSTICO vigente (+ tipo de patología si hay).
    Seccion("DIAGNÓSTICO") {
        Text(
            r.diagnosticoVigente?.takeIf { it.isNotBlank() } ?: "Sin diagnóstico registrado.",
            color = if (r.diagnosticoVigente.isNullOrBlank()) c.textoSuave else c.texto,
            fontSize = Sania.txt.cuerpo,
        )
        r.tipoPatologia?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.chipBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) { Text(it, color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
    }

    // TRATAMIENTO EN CURSO (tarjeta verde) o aviso "sin tratamiento en curso".
    Seccion("TRATAMIENTO EN CURSO") {
        val activo = r.tratamientoActivo
        if (activo != null) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.okBg).border(1.dp, c.ok, RoundedCornerShape(Sania.shape.sm.dp))
                    .padding(Sania.dim.md),
            ) {
                Text(activo.servicio, color = c.ok, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text("${activo.hechas} de ${activo.total} sesiones",
                    color = c.texto, fontSize = Sania.txt.pequeno, fontWeight = FontWeight.SemiBold)
                activo.desde?.takeIf { it.isNotBlank() }?.let {
                    Text("Desde $it", color = c.textoSuave, fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp))
                }
                activo.diagnostico?.takeIf { it.isNotBlank() && it != r.diagnosticoVigente }?.let {
                    Text(it, color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        } else {
            Text("Sin tratamiento en curso.", color = c.textoSuave, fontSize = Sania.txt.cuerpo)
        }
    }

    // TRATAMIENTOS ANTERIORES: todos los que NO son el en curso (lista compacta).
    val anteriores = r.tratamientos.filter { it.id != r.tratamientoActivo?.id }
    if (anteriores.isNotEmpty()) {
        Seccion("TRATAMIENTOS ANTERIORES") {
            anteriores.forEach { t ->
                FilaTratamiento(t)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

/** Fila compacta del historial: servicio + hechas/total + badge de estado. */
@Composable
private fun FilaTratamiento(t: TratamientoResumen) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .padding(horizontal = Sania.dim.md, vertical = Sania.dim.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(t.servicio, color = c.texto, fontSize = Sania.txt.pequeno, fontWeight = FontWeight.SemiBold)
            Text("${t.hechas}/${t.total} sesiones", color = c.textoSuave, fontSize = 11.sp)
        }
        BadgeEstadoTratamiento(t.estado)
    }
}

/** Badge de estado del tratamiento (Completado=verde, Cancelado=rojo, resto=neutro). */
@Composable
private fun BadgeEstadoTratamiento(estado: String) {
    val c = Sania.colors
    val (fg, bg) = when (estado) {
        "Completado" -> c.ok to c.okBg
        "Cancelado" -> c.error to c.errorBg
        "Activo" -> c.info to c.infoBg
        else -> c.textoSuave to c.chipBg
    }
    Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg)
        .padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(estado, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

/** Título de sección (uppercase gris) + contenido. */
@Composable
private fun Seccion(titulo: String, contenido: @Composable () -> Unit) {
    val c = Sania.colors
    Column(Modifier.fillMaxWidth().padding(top = Sania.dim.md)) {
        Text(titulo, color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp))
        contenido()
    }
}
