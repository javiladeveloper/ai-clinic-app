package pe.saniape.app.ui.clinica.odontologia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import pe.saniape.app.data.staff.esHallazgoDeRango
import pe.saniape.app.data.staff.etiquetaHecho
import pe.saniape.app.data.staff.piezasDeLaArcada
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PanelDiente(
    diente: String,
    carasPreseleccionadas: List<String>,
    hallazgosDelDiente: List<DienteHallazgo>,
    catalogo: List<HallazgoDental>,
    onCerrar: () -> Unit,
    /**
     * existente != null → actualizar sus caras (ya sumadas); si no, registro nuevo.
     * dienteHasta: la pieza final de un hallazgo de varias piezas (null = solo esta).
     */
    onMarcar: (hallazgoId: String, caras: List<String>?, existente: DienteHallazgo?, dienteHasta: String?) -> Unit,
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
    // Puente, brackets, diastema: antes de registrar se elige hasta qué pieza
    // llega (Urbina 09/11/2026: un diastema quedaba solo en la 12 y no se veía).
    var rangoPorElegir by remember(diente) { mutableStateOf<HallazgoDental?>(null) }
    val otrasDeLaArcada = remember(diente) { piezasDeLaArcada(diente).orEmpty().filter { it != diente } }

    fun cerrarOSeguir() { if (llegoPorCara) onCerrar() else { caras = emptySet(); editando = null } }

    fun elegir(h: HallazgoDental) {
        val existente = pendientePorHallazgo[h.id]
        when {
            esHallazgoDeRango(h.nombre) && otrasDeLaArcada.isNotEmpty() -> { rangoPorElegir = h; return }
            esDePiezaEntera(h) -> onMarcar(h.id, null, null, null)
            existente != null && caras.isEmpty() -> {
                // Sin cara elegida: se abre el existente para editar sus caras.
                editando = existente
                caras = existente.superficies.orEmpty().toSet()
                return
            }
            existente != null -> onMarcar(h.id, ordenarCaras(existente.superficies.orEmpty() + caras), existente, null)
            else -> onMarcar(h.id, caras.takeIf { it.isNotEmpty() }?.let { ordenarCaras(it) }, null, null)
        }
        cerrarOSeguir()
    }

    fun registrarRango(h: HallazgoDental, hasta: String?) {
        onMarcar(h.id, null, null, hasta)
        rangoPorElegir = null
        cerrarOSeguir()
    }

    val nombreEditando = editando?.let { porId[it.hallazgoId]?.nombre }
    DialogoForm(
        titulo = "Pieza $diente",
        ancla = "odontograma.diente_panel",
        subtitulo = if (caras.isEmpty()) "Diente entero" else "Cara " + ordenarCaras(caras).joinToString(", ") { nombreCara(it) },
        textoAccion = editando?.let { "✓ Actualizar $nombreEditando" + (if (caras.isEmpty()) "" else " (${ordenarCaras(caras).joinToString("")})") } ?: "Listo",
        onCancelar = onCerrar,
        onAccion = {
            val e = editando
            if (e != null) {
                onMarcar(e.hallazgoId, ordenarCaras(caras), e, null)
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

        // ── Hasta qué pieza (puente, brackets, diastema) ─────────────────
        val rango = rangoPorElegir
        if (rango != null) {
            Spacer(Modifier.height(14.dp))
            EtqForm("${rango.nombre}: desde la $diente hasta la pieza…")
            Text(
                "El odontograma une las piezas con una línea. Un diastema va entre dos piezas (p. ej. de la 11 a la 21).",
                color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ChipPieza("Solo esta pieza", destacado = false) { registrarRango(rango, null) }
                otrasDeLaArcada.forEach { p -> ChipPieza(p, destacado = true) { registrarRango(rango, p) } }
                ChipPieza("✕ Cancelar", destacado = false) { rangoPorElegir = null }
            }
        }

        // ── Qué hallazgo ─────────────────────────────────────────────────
        if (editando == null && rango == null) {
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
 * Solo se ofrecen los que la clínica marcó "de boca" (R2, 09/11): una caries
 * o una extracción guardadas en "BOCA" no tienen pieza y no se pueden cobrar
 * bien. Y no se ofrece dos veces lo que ya está pendiente, porque duplicarlo
 * lo cobraría dos veces.
 */
@Composable
internal fun PanelBoca(
    catalogo: List<HallazgoDental>,
    yaMarcados: Set<String>,
    onCerrar: () -> Unit,
    onElegir: (String) -> Unit,
) {
    val c = Sania.colors
    val ordenados = remember(catalogo) { catalogo.filter { it.estado == "Activo" && it.porBoca && !it.marcaAusente } }

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
        if (ordenados.isEmpty()) {
            Text(
                "Ningún hallazgo del catálogo es «de toda la boca». Márcalo así en Ajustes → Hallazgos del odontograma (sarro, gingivitis…).",
                color = c.textoSuave, fontSize = 12.sp,
            )
        } else ChipsCatalogo(ordenados, onElegir = { onElegir(it.id) }, deshabilitados = yaMarcados)
        if (yaMarcados.isNotEmpty()) {
            Text(
                "Los apagados ya están registrados como pendientes.",
                color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Un número de pieza para elegir dónde termina un hallazgo de varias piezas. */
@Composable
private fun ChipPieza(texto: String, destacado: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(c.superficie)
            .border(1.dp, if (destacado) c.navy.copy(alpha = 0.5f) else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(texto, color = if (destacado) c.navy else c.textoSuave, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}