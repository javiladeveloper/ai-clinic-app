package pe.saniape.app.ui.reservar

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import pe.saniape.app.data.ClinicaDir
import pe.saniape.app.data.ClinicaPaciente
import pe.saniape.app.data.SaludRepo
import pe.saniape.app.ui.Blanco
import pe.saniape.app.ui.BorderColor
import pe.saniape.app.ui.ManejarAtras
import pe.saniape.app.ui.Muted
import pe.saniape.app.ui.Navy
import pe.saniape.app.ui.Sand
import pe.saniape.app.ui.TextoPrincipal

/**
 * "Mis clínicas": SOLO las clínicas donde el paciente tiene historial (por su DNI).
 * Reemplaza al directorio con mapa. En las clínicas Plus (`puedeReservar`) muestra
 * "Reservar cita"; en las demás, solo puede ver su historial (tab Salud). Si no tiene
 * historial en ninguna, un mensaje amable. Sin mapa, sin listar clínicas ajenas.
 */
@Composable
fun PantallaMisClinicas() {
    // Paso interno: lista de clínicas ↔ formulario de reserva de una clínica Plus.
    var reservando by remember { mutableStateOf<ClinicaDir?>(null) }

    var cargando by remember { mutableStateOf(true) }
    var clinicas by remember { mutableStateOf<List<ClinicaPaciente>>(emptyList()) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cargando = true; error = false
        val r = runCatching { SaludRepo.misClinicas() }.getOrNull()
        if (r == null) error = true else clinicas = r
        cargando = false
    }

    // Si está reservando, muestra el formulario (con su propio Atrás).
    val enReserva = reservando
    if (enReserva != null) {
        ManejarAtras(activo = true) { reservando = null }
        PantallaFormularioReserva(clinica = enReserva, onAtras = { reservando = null })
        return
    }

    Column(Modifier.fillMaxSize().background(Sand).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Mis clínicas", color = TextoPrincipal, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Donde te has atendido. En las que lo permiten, puedes reservar tu próxima cita.",
            color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp))

        when {
            cargando -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Navy)
            }
            error -> MensajeVacio("😕", "No se pudo cargar", "Revisa tu conexión e inténtalo de nuevo.")
            clinicas.isEmpty() -> MensajeVacio(
                "📋", "Aún no tienes historial",
                "Cuando te atiendas en una clínica que use Sania, aparecerá aquí.",
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                clinicas.forEach { cl ->
                    TarjetaClinicaPaciente(cl, onReservar = {
                        reservando = ClinicaDir(
                            nombre = cl.nombre, slug = cl.slug ?: "", logoUrl = cl.logoUrl,
                            colorPrincipal = cl.colorPrincipal, ciudad = null, distrito = null,
                            direccion = null, referencia = null, lat = null, lng = null,
                            descripcion = null, especialidades = emptyList(),
                            permiteReserva = true, whatsappContacto = null,
                        )
                    })
                }
            }
        }
    }
}

@Composable
private fun TarjetaClinicaPaciente(cl: ClinicaPaciente, onReservar: () -> Unit) {
    val color = runCatching { Color(("ff" + (cl.colorPrincipal ?: "#2c3e7a").removePrefix("#")).toLong(16)) }
        .getOrDefault(Navy)
    Column(
        Modifier.fillMaxWidth().background(Blanco, RoundedCornerShape(16.dp))
            .border(1.dp, BorderColor, RoundedCornerShape(16.dp)).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Logo o inicial
            Box(Modifier.size(46.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center) {
                if (cl.logoUrl != null) {
                    AsyncImage(model = cl.logoUrl, contentDescription = null, modifier = Modifier.size(46.dp).clip(CircleShape))
                } else {
                    Text(cl.nombre.take(1).uppercase(), color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(cl.nombre, color = TextoPrincipal, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (cl.puedeReservar) "Puedes reservar tu cita" else "Ver tu historial en Salud",
                    color = if (cl.puedeReservar) color else Muted, fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (cl.puedeReservar) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color)
                    .clickable { onReservar() }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("📅  Reservar cita", color = Blanco, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        } else {
            // No-Plus: no se reserva online. Solo se indica dónde ver su historial.
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .border(1.dp, BorderColor, RoundedCornerShape(12.dp)).padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Tu historial está en la pestaña Salud", color = Muted, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun MensajeVacio(emoji: String, titulo: String, detalle: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(emoji, fontSize = 44.sp)
        Text(titulo, color = TextoPrincipal, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(detalle, color = Muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
    }
}
