package pe.saniape.app.ui.clinica.odontologia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.DienteHallazgo
import pe.saniape.app.data.staff.HallazgoDental
import pe.saniape.app.data.staff.OdontogramaRepo
import pe.saniape.app.data.staff.piezasPendientes
import pe.saniape.app.data.staff.piezasSugeridas
import pe.saniape.app.data.staff.simboloMoneda
import pe.saniape.app.data.staff.totalPorPiezas
import pe.saniape.app.ui.theme.Sania

/**
 * 🦷 «Piezas de este tratamiento» al crear un tratamiento dental DIRECTO (sin pasar por
 * el diagnóstico, 24/11/2026). Gemelo de components/odontologia/PiezasDeTratamiento.tsx:
 * las piezas pendientes del odontograma, elegir cuáles cubre y el odontograma ahí mismo.
 * Las elegidas viajan como `hallazgoIds` y las ata el servidor al crear.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PiezasDeTratamiento(
    pacienteId: String,
    pacienteNombre: String?,
    procedimientoId: String?,
    precioLista: Double?,
    moneda: String?,
    elegidas: List<String>,
    onCambio: (List<String>) -> Unit,
    /** «Usar este precio»: el total por piezas como precio del tratamiento (null = no se ofrece). */
    onUsarPrecio: ((Double) -> Unit)? = null,
    /** Sin servicio elegido: al tocar una pieza, el servicio que el catálogo le asocia. */
    onServicioSugerido: ((String) -> Unit)? = null,
    mapaDental: pe.saniape.app.data.staff.MapaDental = pe.saniape.app.data.staff.MapaDental(),
) {
    val c = Sania.colors
    var hallazgos by remember { mutableStateOf<List<DienteHallazgo>?>(null) }
    var catalogo by remember { mutableStateOf<List<HallazgoDental>>(emptyList()) }
    var recarga by remember { mutableIntStateOf(0) }
    var abierto by remember { mutableStateOf(false) }
    LaunchedEffect(pacienteId, recarga) {
        catalogo = OdontogramaRepo.catalogo()
        hallazgos = OdontogramaRepo.hallazgosONull(pacienteId) ?: emptyList()
    }
    val cargando = hallazgos == null
    val pendientes = remember(hallazgos, catalogo, procedimientoId) {
        piezasPendientes(hallazgos.orEmpty(), catalogo, procedimientoId)
    }
    // Al elegir (o cambiar) el servicio vienen marcadas las que ese servicio cobra; una vez por servicio.
    var sugeridoPara by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(cargando, procedimientoId, pendientes) {
        if (cargando || procedimientoId == null || sugeridoPara == procedimientoId) return@LaunchedEffect
        sugeridoPara = procedimientoId
        onCambio(piezasSugeridas(pendientes))
    }
    // Lo elegido que ya no está pendiente (se borró en el odontograma) se suelta.
    LaunchedEffect(pendientes) {
        val vivos = pendientes.map { it.id }.toSet()
        if (elegidas.any { it !in vivos }) onCambio(elegidas.filter { it in vivos })
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.5.dp, c.borde, RoundedCornerShape(12.dp))
            .background(c.superficie).padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("🦷 Piezas de este tratamiento", color = c.navy, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { abierto = true }) { Text("Abrir odontograma", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
        when {
            cargando -> Text("Cargando el odontograma…", color = c.textoSuave, fontSize = 12.sp)
            pendientes.isEmpty() -> Text(
                "No tiene piezas pendientes en su odontograma. Ábrelo para marcar lo que vas a tratar: así el tratamiento queda con sus piezas y se ve el avance de cada una.",
                color = c.textoSuave, fontSize = 12.sp,
            )
            else -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    pendientes.forEach { p ->
                        val on = p.id in elegidas
                        Row(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .border(1.5.dp, if (on) c.navy else c.borde, RoundedCornerShape(20.dp))
                                .background(if (on) c.navy else c.superficie)
                                .clickable {
                                    val quitar = on
                                    onCambio(if (quitar) elegidas - p.id else elegidas + p.id)
                                    // Aún sin servicio: la pieza dice cuál es (caries → resina).
                                    val proc = p.procedimientoId
                                    if (!quitar && procedimientoId == null && proc != null && onServicioSugerido != null) {
                                        sugeridoPara = proc; onServicioSugerido(proc)
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(colorDe(p.color)))
                            Spacer(Modifier.width(6.dp))
                            Text(p.etiquetaDiente, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (on) c.sobreNavy else c.texto)
                            Spacer(Modifier.width(4.dp))
                            Text(p.hallazgo, fontSize = 12.sp, color = if (on) c.sobreNavy else c.texto)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (elegidas.isNotEmpty()) "${elegidas.size} ${if (elegidas.size == 1) "pieza queda" else "piezas quedan"} en este tratamiento: al completar cada cita se marca cuál se trabajó."
                    else "Toca las piezas que cubre este tratamiento. Sin ninguna, el tratamiento queda sin piezas.",
                    color = c.textoSuave, fontSize = 11.sp,
                )
                val total = totalPorPiezas(elegidas.size, precioLista)
                if (total > 0 && onUsarPrecio != null) {
                    val sim = simboloMoneda(moneda)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${elegidas.size} × $sim ${formatoPrecio(precioLista ?: 0.0)} = $sim ${formatoPrecio(total)}", fontSize = 12.sp, color = c.texto, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { onUsarPrecio(total) }) { Text("Usar este precio", fontSize = 12.sp) }
                    }
                }
            }
        }
    }
    if (abierto) {
        pe.saniape.app.ui.clinica.pacientes.DialogoForm(
            titulo = "🦷 Odontograma",
            subtitulo = pacienteNombre,
            textoAccion = "Listo, volver al tratamiento",
            onCancelar = { abierto = false; recarga++ },
            onAccion = { abierto = false; recarga++ },
            textoCancelar = "Cerrar",
        ) {
            OdontogramaVista(pacienteId = pacienteId, mapaDental = mapaDental, mostrarPresupuesto = false, puedeCrearTratamientos = false)
        }
    }
}

private fun formatoPrecio(n: Double): String = if (n % 1.0 == 0.0) n.toLong().toString() else ((kotlin.math.round(n * 100) / 100).toString())
