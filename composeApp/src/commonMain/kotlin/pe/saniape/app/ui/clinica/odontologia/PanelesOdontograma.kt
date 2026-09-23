package pe.saniape.app.ui.clinica.odontologia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.COLOR_REALIZADO
import pe.saniape.app.data.staff.DienteHallazgo
import pe.saniape.app.data.staff.HallazgoDental
import pe.saniape.app.data.staff.SUPERFICIES
import pe.saniape.app.data.staff.nombreCara
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.theme.Sania

/**
 * El panel de un diente: lo que ya tiene y los chips para marcar algo nuevo.
 *
 * Se elige la cara PRIMERO (viene preseleccionada si se tocó una en el
 * diagrama) y después el hallazgo. Sin cara elegida, el hallazgo es del diente
 * entero — que es lo correcto para "Ausente", "Corona" o "Endodoncia".
 */
@Composable
internal fun PanelDiente(
    diente: String,
    carasPreseleccionadas: List<String>,
    hallazgosDelDiente: List<DienteHallazgo>,
    catalogo: List<HallazgoDental>,
    onCerrar: () -> Unit,
    onAgregar: (hallazgoId: String, caras: List<String>?) -> Unit,
    onAlternar: (DienteHallazgo) -> Unit,
    onQuitar: (DienteHallazgo) -> Unit,
) {
    val c = Sania.colors
    var caras by remember(diente) { mutableStateOf(carasPreseleccionadas.toSet()) }
    val porId = remember(catalogo) { catalogo.associateBy { it.id } }
    // Solo los ACTIVOS se ofrecen para marcar; los inactivos siguen pintándose
    // en lo ya marcado, pero no se usan en marcas nuevas.
    val ofrecibles = remember(catalogo) { catalogo.filter { it.estado == "Activo" } }

    DialogoForm(
        titulo = "Pieza $diente",
        subtitulo = if (caras.isEmpty()) "Diente entero" else "Cara " + caras.joinToString(", ") { nombreCara(it) },
        textoAccion = "Listo",
        onCancelar = onCerrar,
        onAccion = onCerrar,
    ) {
        // ── Lo que ya tiene ──────────────────────────────────────────────
        if (hallazgosDelDiente.isNotEmpty()) {
            EtqForm("Registrado")
            hallazgosDelDiente.forEach { h ->
                val hal = porId[h.hallazgoId]
                val donde = h.superficies?.joinToString(", ") { nombreCara(it) } ?: "diente entero"
                FilaHallazgo(
                    nombre = hal?.nombre ?: "Hallazgo",
                    color = colorDe(if (h.estado == "Realizado") COLOR_REALIZADO else hal?.color),
                    detalle = "${h.estado} · $donde",
                    soloLectura = false,
                    onAlternar = { onAlternar(h) },
                    onQuitar = { onQuitar(h) },
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        // ── Qué cara ─────────────────────────────────────────────────────
        EtqForm("Cara (opcional)")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SUPERFICIES.forEach { cara ->
                val sel = cara in caras
                Box(
                    Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .background(if (sel) c.navy else c.superficie)
                        .border(1.dp, if (sel) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                        .clickable { caras = if (sel) caras - cara else caras + cara }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(cara, color = if (sel) c.sobreNavy else c.texto, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        Text(
            "Sin cara elegida se marca el diente entero.",
            color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
        )

        // ── Qué hallazgo ─────────────────────────────────────────────────
        Spacer(Modifier.height(14.dp))
        EtqForm("Marcar")
        ChipsCatalogo(ofrecibles, onElegir = { h ->
            // "Ausente" y los de boca son del diente entero por definición: una
            // ausencia no tiene cara, así que se ignora la cara elegida.
            val sinCaras = h.marcaAusente || h.porBoca || caras.isEmpty()
            onAgregar(h.id, if (sinCaras) null else caras.toList().sorted())
        })
    }
}

/**
 * Elegir un hallazgo para la boca entera (sarro, gingivitis, bruxismo…).
 *
 * Solo se ofrecen los que la clínica marcó "de boca" más los que no tienen
 * sentido por pieza; y no se ofrece dos veces lo que ya está pendiente, porque
 * duplicarlo lo cobraría dos veces.
 */
@Composable
internal fun PanelBoca(
    catalogo: List<HallazgoDental>,
    yaMarcados: Set<String>,
    onCerrar: () -> Unit,
    onElegir: (String) -> Unit,
) {
    val c = Sania.colors
    val activos = remember(catalogo) { catalogo.filter { it.estado == "Activo" && !it.marcaAusente } }
    // Primero los de boca: son los que se buscan acá.
    val ordenados = remember(activos) { activos.sortedByDescending { it.porBoca } }

    DialogoForm(
        titulo = "Boca completa",
        subtitulo = "Un hallazgo que no es de una pieza",
        textoAccion = "Cerrar",
        onCancelar = onCerrar,
        onAccion = onCerrar,
    ) {
        Text(
            "Se cobra una sola vez en el presupuesto, por muchas piezas que afecte.",
            color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp),
        )
        ChipsCatalogo(ordenados, onElegir = { onElegir(it.id) }, deshabilitados = yaMarcados)
        if (yaMarcados.isNotEmpty()) {
            Text(
                "Los apagados ya están registrados como pendientes.",
                color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
