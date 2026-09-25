package pe.saniape.app.ui.clinica.odontologia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
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
import pe.saniape.app.data.staff.esDePiezaEntera
import pe.saniape.app.data.staff.etiquetaHecho
import pe.saniape.app.data.staff.nombreCara
import pe.saniape.app.data.staff.ordenarCaras
import pe.saniape.app.data.staff.pintarDiente
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.theme.Sania

/**
 * El panel de un diente: lo que ya tiene y los chips para marcar algo nuevo.
 *
 * Se elige la cara PRIMERO (viene preseleccionada si se tocó una en el
 * diagrama; en el celular, sobre la pieza en grande) y después el hallazgo. Sin
 * cara elegida, el hallazgo es del diente entero — lo correcto para "Ausente",
 * "Corona" o "Endodoncia".
 *
 * Un hallazgo que la pieza YA tiene pendiente no se duplica: su chip (✎) le
 * SUMA las caras elegidas. Una caries en M a la que luego se le ve otra en O
 * queda como UNA caries MO — es una sola resina, y así se cobra. Si se toca sin
 * cara elegida, abre el existente con sus caras para editarlas. Llegando por
 * una cara del dibujo, tocar el hallazgo registra de una vez y cierra.
 * Gemelo de components/odontologia/DientePanel.tsx.
 */
@Composable
internal fun PanelDiente(
    diente: String,
    carasPreseleccionadas: List<String>,
    hallazgosDelDiente: List<DienteHallazgo>,
    catalogo: List<HallazgoDental>,
    onCerrar: () -> Unit,
    /** existente != null → actualizar sus caras (ya sumadas); si no, registro nuevo. */
    onMarcar: (hallazgoId: String, caras: List<String>?, existente: DienteHallazgo?) -> Unit,
    onAlternar: (DienteHallazgo) -> Unit,
    onQuitar: (DienteHallazgo) -> Unit,
    onNota: (DienteHallazgo, String?) -> Unit = { _, _ -> },
) {
    val c = Sania.colors
    var caras by remember(diente) { mutableStateOf(carasPreseleccionadas.toSet()) }
    // Registro pendiente cuyas caras se están editando (null = se marca uno nuevo).
    var editando by remember(diente) { mutableStateOf<DienteHallazgo?>(null) }
    val llegoPorCara = carasPreseleccionadas.isNotEmpty()
    val porId = remember(catalogo) { catalogo.associateBy { it.id } }
    // Solo los ACTIVOS y de PIEZA se ofrecen (los de boca van en "Boca completa").
    val ofrecibles = remember(catalogo) { catalogo.filter { it.estado == "Activo" && !it.porBoca } }
    // Recordados: el panel se recompone con cada toque de cara (la selección);
    // esto solo cambia cuando cambia lo registrado en la pieza.
    val pendientePorHallazgo = remember(hallazgosDelDiente) {
        hallazgosDelDiente.filter { it.estado == "Pendiente" }.groupBy { it.hallazgoId }.mapValues { it.value.first() }
    }
    // Los de pieza entera ya pendientes no tienen nada que ampliar: chip apagado.
    val deshabilitados = remember(ofrecibles, pendientePorHallazgo) {
        ofrecibles.filter { it.id in pendientePorHallazgo && esDePiezaEntera(it) }.map { it.id }.toSet()
    }
    val pintado = remember(hallazgosDelDiente, catalogo) { pintarDiente(hallazgosDelDiente, catalogo) }

    fun elegir(h: HallazgoDental) {
        val existente = pendientePorHallazgo[h.id]
        when {
            esDePiezaEntera(h) -> onMarcar(h.id, null, null)
            existente != null && caras.isEmpty() -> {
                // Sin cara elegida: se abre el existente para editar sus caras.
                editando = existente
                caras = existente.superficies.orEmpty().toSet()
                return
            }
            existente != null -> onMarcar(h.id, ordenarCaras(existente.superficies.orEmpty() + caras), existente)
            else -> onMarcar(h.id, caras.takeIf { it.isNotEmpty() }?.let { ordenarCaras(it) }, null)
        }
        if (llegoPorCara) onCerrar() else { caras = emptySet(); editando = null }
    }

    val nombreEditando = editando?.let { porId[it.hallazgoId]?.nombre }
    DialogoForm(
        titulo = "Pieza $diente",
        subtitulo = if (caras.isEmpty()) "Diente entero" else "Cara " + ordenarCaras(caras).joinToString(", ") { nombreCara(it) },
        textoAccion = editando?.let { "✓ Actualizar $nombreEditando" + (if (caras.isEmpty()) "" else " (${ordenarCaras(caras).joinToString("")})") } ?: "Listo",
        onCancelar = onCerrar,
        onAccion = {
            val e = editando
            if (e != null) {
                onMarcar(e.hallazgoId, ordenarCaras(caras), e)
                editando = null; caras = emptySet()
            } else onCerrar()
        },
    ) {
        // ── Lo que ya tiene ──────────────────────────────────────────────
        if (hallazgosDelDiente.isNotEmpty()) {
            EtqForm("Registrado")
            hallazgosDelDiente.forEach { h ->
                val hal = porId[h.hallazgoId]
                val donde = buildString {
                    append(h.superficies?.joinToString("") ?: "diente entero")
                    h.dienteHasta?.let { append(" · hasta la $it") }
                }
                val hecho = h.estado == "Realizado"
                FilaHallazgo(
                    nombre = hal?.nombre ?: "Hallazgo",
                    color = colorDe(if (hecho) COLOR_REALIZADO else hal?.color),
                    // La caries tratada se lee "restaurada", no "realizada".
                    detalle = (if (hecho) "✓ " + etiquetaHecho(hal?.nombre.orEmpty()) else "Pendiente") + " · $donde",
                    realizado = hecho,
                    soloLectura = false,
                    onAlternar = { onAlternar(h) },
                    onQuitar = { onQuitar(h) },
                    nota = h.notas,
                    onNota = { onNota(h, it) },
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        // ── Qué cara: la pieza en grande, cada cara es un blanco de dedo ──
        EtqForm(if (editando != null) "Caras de ${nombreEditando.orEmpty()}" else "Cara (opcional)")
        if (editando != null) {
            Text(
                "Esta pieza ya tiene $nombreEditando. Marca todas las caras afectadas: queda UNA sola y se cobra una vez.",
                color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            DienteDiagrama(
                diente = diente, tam = 150.dp, pintado = pintado, seleccion = caras,
                onTocarCara = { cara -> caras = if (cara in caras) caras - cara else caras + cara },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
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
            if (llegoPorCara) "Cara elegida: toca el hallazgo y queda registrado."
            else "Sin cara elegida se marca el diente entero.",
            color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
        )

        // ── Qué hallazgo ─────────────────────────────────────────────────
        if (editando == null) {
            Spacer(Modifier.height(14.dp))
            EtqForm("Marcar")
            ChipsCatalogo(
                ofrecibles, onElegir = { h -> elegir(h) }, deshabilitados = deshabilitados,
                // ✎ = ya está pendiente en esta pieza: tocarlo le suma caras.
                marcados = pendientePorHallazgo.keys - deshabilitados,
            )
        }
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
