package pe.saniape.app.ui.clinica

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.ContextoCaso
import pe.saniape.app.data.staff.ContextoCasoRepo
import pe.saniape.app.ui.theme.Sania

// Lo que se LEE mientras se escribe la evolución de una sesión: «📋 Contexto del
// caso» y «Sesión anterior». Gemelo de components/fisio/PanelCasoCierre.tsx (web)
// en su versión angosta: arriba de los campos y plegable. Solo lectura.

/** El contexto del caso pedido y si todavía está cargando. */
data class EstadoContextoCaso(val contexto: ContextoCaso?, val cargando: Boolean)

/**
 * Pide el contexto del caso de la sesión que se cierra. [activo] = quien completa
 * ve la historia clínica (sin el permiso no se pide nada) y hay paciente.
 */
@Composable
fun recordarContextoCaso(
    activo: Boolean, pacienteId: String?, tratamientoId: String?, especialidadId: String?,
): EstadoContextoCaso {
    val pedir = activo && !pacienteId.isNullOrBlank()
    val estado by produceState(EstadoContextoCaso(null, pedir), pedir, pacienteId, tratamientoId, especialidadId) {
        value = if (!pedir) EstadoContextoCaso(null, false)
        else EstadoContextoCaso(ContextoCasoRepo.leer(pacienteId!!, tratamientoId, especialidadId), false)
    }
    return estado
}

/** Bloque plegable: el título se toca para abrir/cerrar (con ▾). */
@Composable
private fun BloquePlegable(
    titulo: String, detalle: String?, abiertoInicial: Boolean, fondo: Color, borde: Color,
    contenido: @Composable () -> Unit,
) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(abiertoInicial) }
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    Column(
        Modifier.fillMaxWidth().clip(forma).background(fondo).border(1.dp, borde, forma)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button) { abierto = !abierto }
                .semantics { contentDescription = "$titulo, ${if (abierto) "abierto" else "plegado"}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(titulo, color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            detalle?.let {
                Spacer(Modifier.width(6.dp))
                Text(it, color = c.textoSuave, fontSize = 11.sp, maxLines = 1)
            }
            Spacer(Modifier.width(6.dp))
            Text(if (abierto) "▴" else "▾", color = c.textoSuave, fontSize = 13.sp)
        }
        if (abierto) {
            Spacer(Modifier.height(6.dp))
            contenido()
        }
    }
}

/** Si es largo, 4 líneas y «Ver más». */
@Composable
private fun TextoPlegable(texto: String, color: Color = Sania.colors.texto) {
    val c = Sania.colors
    var abierto by remember(texto) { mutableStateOf(false) }
    val largo = pe.saniape.app.data.staff.esTextoLargo(texto)
    Text(
        texto, color = color, fontSize = 12.sp, lineHeight = 17.sp,
        maxLines = if (largo && !abierto) 4 else Int.MAX_VALUE,
        overflow = TextOverflow.Ellipsis,
    )
    if (largo) {
        Text(
            if (abierto) "Ver menos" else "Ver más", color = c.navy, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(role = Role.Button) { abierto = !abierto }.padding(vertical = 3.dp),
        )
    }
}

@Composable
private fun EtiquetaCaso(texto: String, color: Color = Sania.colors.textoSuave) {
    Text(texto.uppercase(), color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
}

/**
 * «📋 Contexto del caso». Cargando: un recuadro tenue. Sin datos: nada. [abiertoInicial]:
 * [pe.saniape.app.data.staff.contextoAbiertoInicial].
 */
@Composable
fun BloqueContextoCaso(estado: EstadoContextoCaso, abiertoInicial: Boolean, modifier: Modifier = Modifier) {
    val c = Sania.colors
    val ctx = estado.contexto
    if (estado.cargando && ctx?.tieneDatos != true) {
        Box(
            modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg)
                .semantics { contentDescription = "Cargando el contexto del caso" },
        )
        return
    }
    if (ctx == null || !ctx.tieneDatos) return
    Column(modifier) {
        BloquePlegable("📋 Contexto del caso", ctx.detalleFuente(), abiertoInicial, c.chipBg, c.borde) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ctx.secciones.forEach { s ->
                    Column {
                        EtiquetaCaso(s.etiqueta)
                        TextoPlegable(s.texto)
                    }
                }
            }
        }
    }
}

/**
 * «🗒 Sesión anterior (#N — dd/mm)»: lo que se hizo y su evolución. En
 * psicología la evolución se lee campo por campo. [eva]: el dolor al entrar/salir
 * (fisio), en la cabecera.
 */
@Composable
fun BloqueSesionAnterior(
    numero: Int, fecha: String, notas: String?, mejorias: String?,
    psicologia: Boolean, eva: String?, abiertoInicial: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = Sania.colors
    val notasT = notas?.takeIf { it.isNotBlank() }
    val mejT = mejorias?.takeIf { it.isNotBlank() }
    val camposPsico = if (psicologia) pe.saniape.app.data.staff.camposEvolucionPsicoAnterior(mejT) else emptyList()
    Column(modifier) {
        BloquePlegable(
            "🗒 Sesión anterior (#$numero — ${pe.saniape.app.data.staff.fechaDiaMes(fecha)})",
            eva?.takeIf { it.isNotBlank() }, abiertoInicial, c.superficie, c.borde,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                notasT?.let { TextoPlegable(it) }
                if (camposPsico.isNotEmpty()) {
                    camposPsico.forEach { (etiqueta, texto) ->
                        Column {
                            EtiquetaCaso(etiqueta)
                            TextoPlegable(texto)
                        }
                    }
                } else if (mejT != null) {
                    Column {
                        EtiquetaCaso(if (psicologia) "Evolución" else "Mejorías", c.ok)
                        TextoPlegable(mejT)
                    }
                }
                if (notasT == null && mejT == null) {
                    Text("Sin observaciones registradas", color = c.textoSuave, fontSize = 11.sp, fontStyle = FontStyle.Italic)
                }
            }
        }
    }
}
