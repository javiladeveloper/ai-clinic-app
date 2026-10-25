package pe.saniape.app.ui.clinica.atencion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.DiagnosticoCie
import pe.saniape.app.data.staff.ItemRecetaPrevia
import pe.saniape.app.data.staff.LaUltimaVezApp
import pe.saniape.app.data.staff.decimal
import pe.saniape.app.data.staff.diagnosticoYaEsta
import pe.saniape.app.data.staff.fechaUltimaVez
import pe.saniape.app.data.staff.lineaDiagnostico
import pe.saniape.app.data.staff.lineaMedicamento
import pe.saniape.app.ui.clinica.BloqueContextoCaso
import pe.saniape.app.ui.clinica.BloqueEvolucionAnterior
import pe.saniape.app.ui.clinica.BloquePlegable
import pe.saniape.app.ui.clinica.EstadoContextoCaso
import pe.saniape.app.ui.clinica.EtiquetaCaso
import pe.saniape.app.ui.clinica.TextoPlegable
import pe.saniape.app.ui.hora12
import pe.saniape.app.ui.theme.Sania

// «🗒 LA ÚLTIMA VEZ» en la consulta guiada y en el resumen clínico — gemelo de
// components/atencion-medica/UltimaVezConsulta.tsx (web). Qué atención es «la
// anterior» lo decide el servidor (lib/ultima-atencion.ts); aquí se pinta,
// abierto de entrada y plegable, con los atajos «↻ Mismos diagnósticos» y
// «↻ Repetir receta» (solo si se puede editar). Psicología: el «📋 Contexto
// del caso» de ▶ Atender (el mismo bloque) y la última evolución.

/**
 * «↻ Repetir receta»: los medicamentos anteriores como filas EDITABLES del
 * diálogo de hoy (itemsParaRepetir de la web). No emite nada: el médico revisa
 * y emite; el servidor valida como siempre (colegiatura, controlados, receta
 * especial bloqueada). La cantidad anterior se respeta. Tope de la receta.
 */
internal fun itemsParaRepetir(items: List<ItemRecetaPrevia>): List<ItemRecetaForm> =
    items.filter { it.dci.isNotBlank() }.take(MAX_ITEMS_RECETA).map {
        ItemRecetaForm(
            dci = it.dci.trim(), marca = it.marca?.trim().orEmpty(), concentracion = it.concentracion.trim(),
            forma = it.forma.trim(), via = it.via.trim(), dosis = it.dosis.trim(), frecuencia = it.frecuencia.trim(),
            duracion = it.duracion.trim(), cantidad = if (it.cantidad > 0) decimal(it.cantidad) else "",
            unidad = it.unidad?.trim().orEmpty(), indicaciones = it.indicaciones?.trim().orEmpty(),
        )
    }

/**
 * El bloque. [u] null o sin datos → nada. [diagnosticosHoy]: lo que ya está hoy
 * no se ofrece marcado. [onRepetirDiagnosticos]/[onRepetirReceta] null = sin
 * botón (solo lectura, resumen clínico).
 */
@Composable
fun BloqueUltimaVez(
    u: LaUltimaVezApp?,
    diagnosticosHoy: List<DiagnosticoCie> = emptyList(),
    onRepetirDiagnosticos: ((List<DiagnosticoCie>) -> Unit)? = null,
    onRepetirReceta: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (u == null || !u.tieneDatos) return
    val c = Sania.colors
    val a = u.anterior
    if (a == null) {
        // Psicología: lo que ya usa ▶ Atender, sin duplicarlo.
        val pendientes = u.diagnosticos.filterNot { diagnosticoYaEsta(diagnosticosHoy, it) }
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            u.contexto?.let { BloqueContextoCaso(EstadoContextoCaso(it.aContextoCaso(), false), abiertoInicial = true) }
            u.evolucion?.let { BloqueEvolucionAnterior(it.fecha, it.evolucion.campos()) }
            if (onRepetirDiagnosticos != null && pendientes.isNotEmpty()) {
                BotonChico("↻ Misma impresión diagnóstica", c.navy, c.superficie, borde = c.borde) { onRepetirDiagnosticos(pendientes) }
            }
        }
        return
    }
    // Marcados de entrada: los anteriores que todavía no están hoy (por atención).
    var desmarcados by remember(a.atencionId) { mutableStateOf(emptySet<Int>()) }
    val yaEstan = a.diagnosticos.map { diagnosticoYaEsta(diagnosticosHoy, it) }
    val elegidos = a.diagnosticos.filterIndexed { i, _ -> !yaEstan[i] && i !in desmarcados }
    val detalle = listOfNotNull(fechaUltimaVez(a.fecha), a.profesional?.takeIf { it.isNotBlank() }).joinToString(" · ")
    Column(modifier) {
        BloquePlegable("🗒 La última vez", detalle, abiertoInicial = true, fondo = c.chipBg, borde = c.borde) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (a.diagnosticos.isNotEmpty()) {
                    Column {
                        EtiquetaCaso("Diagnóstico")
                        a.diagnosticos.forEachIndexed { i, d ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (onRepetirDiagnosticos != null) {
                                    Checkbox(
                                        checked = !yaEstan[i] && i !in desmarcados, enabled = !yaEstan[i],
                                        onCheckedChange = { m -> desmarcados = if (m) desmarcados - i else desmarcados + i },
                                        colors = CheckboxDefaults.colors(checkedColor = c.navy),
                                        modifier = Modifier.size(36.dp),
                                    )
                                }
                                Text(
                                    lineaDiagnostico(d) + if (yaEstan[i] && onRepetirDiagnosticos != null) "  ✓ ya está hoy" else "",
                                    color = if (yaEstan[i] && onRepetirDiagnosticos != null) c.textoSuave else c.texto,
                                    fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        if (onRepetirDiagnosticos != null && elegidos.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            BotonChico(
                                "↻ Mismos diagnósticos" + if (elegidos.size < a.diagnosticos.size) " (${elegidos.size})" else "",
                                c.navy, c.superficie, borde = c.borde,
                            ) { onRepetirDiagnosticos(elegidos) }
                        }
                    }
                }
                if (a.receta.isNotEmpty()) {
                    Column {
                        EtiquetaCaso("Receta")
                        a.receta.forEach { Text("• ${lineaMedicamento(it)}", color = c.texto, fontSize = 12.sp, lineHeight = 17.sp) }
                        if (onRepetirReceta != null) {
                            Spacer(Modifier.height(4.dp))
                            BotonChico("↻ Repetir receta", c.navy, c.superficie, borde = c.borde, onClick = onRepetirReceta)
                        }
                    }
                }
                a.indicaciones?.takeIf { it.isNotBlank() }?.let {
                    Column {
                        EtiquetaCaso("Indicaciones")
                        TextoPlegable(it.trim())
                    }
                }
                a.control?.takeIf { it.fecha.isNotBlank() }?.let { ctl ->
                    Column {
                        EtiquetaCaso("Control indicado")
                        val estado = ctl.estado?.takeIf { it.isNotBlank() && it != "Confirmada" && it != "Pendiente" }
                        Row {
                            Text(
                                fechaUltimaVez(ctl.fecha) + (ctl.hora?.takeIf { it.isNotBlank() }?.let { " ${hora12(it)}" } ?: ""),
                                color = c.texto, fontSize = 12.sp,
                            )
                            estado?.let {
                                Spacer(Modifier.width(4.dp))
                                Text("· $it", color = c.textoSuave, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
