package pe.saniape.app.ui.clinica.agenda.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.CeldaMes
import pe.saniape.app.data.staff.ConteoDia
import pe.saniape.app.data.staff.DIAS_SEMANA_CORTOS
import pe.saniape.app.data.staff.MesAgenda
import pe.saniape.app.data.staff.descripcionDia
import pe.saniape.app.data.staff.grillaMes
import pe.saniape.app.ui.theme.EstadosColor
import pe.saniape.app.ui.theme.Sania

/**
 * Encabezado "◀ Octubre 2026 ▾ ▶ · Hoy" de la agenda (modo día). Tocar el mes
 * abre/cierra el calendario; las flechas cambian de mes (y lo abren).
 */
@Composable
fun EncabezadoMesAgenda(
    mes: MesAgenda,
    abierto: Boolean,
    mostrarHoy: Boolean,
    onAnterior: () -> Unit,
    onSiguiente: () -> Unit,
    onAlternar: () -> Unit,
    onHoy: () -> Unit,
) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().padding(start = Sania.dim.lg - 8.dp, end = Sania.dim.lg, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotonFlecha("◀", "Mes anterior", onAnterior)
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(Sania.shape.pill.dp))
                .clickable(onClickLabel = if (abierto) "Cerrar calendario" else "Abrir calendario") { onAlternar() }
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .semantics(mergeDescendants = true) {
                    heading()
                    contentDescription = "${mes.titulo}, " + if (abierto) "calendario abierto" else "ver calendario del mes"
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "📅 ${mes.titulo}", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(4.dp))
            Text(if (abierto) "▴" else "▾", color = c.textoSuave, fontSize = 13.sp)
        }
        BotonFlecha("▶", "Mes siguiente", onSiguiente)
        if (mostrarHoy) {
            Spacer(Modifier.width(6.dp))
            Text(
                "Hoy", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.pill.dp))
                    .clickable(onClickLabel = "Ir a hoy") { onHoy() }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun BotonFlecha(simbolo: String, descripcion: String, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable { onClick() }
            .clearAndSetSemantics { contentDescription = descripcion; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { Text(simbolo, color = c.navy, fontSize = 14.sp) }
}

/**
 * Calendario del mes: grilla lunes→domingo, cada día con cuántas citas tiene y
 * puntos con el color de sus estados. Hoy con borde lavanda; el elegido, navy.
 * Deslizar a los lados cambia de mes. Sin listas lazy adentro (6 filas fijas).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarioMes(
    mes: MesAgenda,
    hoy: String,
    seleccionado: String,
    conteos: Map<String, ConteoDia>,
    cargando: Boolean,
    error: Boolean,
    /** El mes pasó el tope de filas: el conteo es parcial. */
    parcial: Boolean,
    onElegir: (String) -> Unit,
    onAnterior: () -> Unit,
    onSiguiente: () -> Unit,
    onReintentar: () -> Unit,
) {
    val c = Sania.colors
    val celdas = remember(mes, hoy) { grillaMes(mes, hoy) }
    val umbral = with(LocalDensity.current) { 64.dp.toPx() }
    // Alto de cada día según el tamaño de letra del teléfono: el mismo para
    // todos (semanas parejas) y con lugar para número + conteo + puntos.
    val altoCelda = maxOf(48f, 20f + 34f * LocalDensity.current.fontScale).dp
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Sania.dim.lg, vertical = 6.dp)
            .clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .pointerInput(mes) {
                var acumulado = 0f
                detectHorizontalDragGestures(
                    onDragStart = { acumulado = 0f },
                    onDragEnd = {
                        if (acumulado > umbral) onAnterior() else if (acumulado < -umbral) onSiguiente()
                        acumulado = 0f
                    },
                ) { _, dx -> acumulado += dx }
            }
            .padding(8.dp),
    ) {
        // Cabecera L M M J V S D (decorativa para el lector: cada día ya dice su fecha).
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
            DIAS_SEMANA_CORTOS.forEachIndexed { i, d ->
                Text(
                    d, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    color = if (i >= 5) c.textoSuave.copy(alpha = 0.8f) else c.textoSuave,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        celdas.chunked(7).forEach { semana ->
            Row(Modifier.fillMaxWidth()) {
                semana.forEach { celda ->
                    CeldaDia(
                        celda, conteos[celda.iso], celda.iso == seleccionado, onElegir,
                        Modifier.weight(1f).heightIn(min = altoCelda),
                    )
                }
            }
        }
        // Estado de la lectura (nunca bloquea: la grilla se usa igual).
        if (parcial) Text(
            "Mes con demasiadas citas: el conteo es parcial", color = c.pend, fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center,
        )
        when {
            cargando -> Text(
                "Contando citas…", color = c.textoSuave, fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center,
            )
            error -> Text(
                "No se pudieron contar las citas · Reintentar", color = c.error, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    .clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .clickable(onClickLabel = "Reintentar") { onReintentar() }.padding(6.dp),
            )
        }
        // Leyenda de los puntos (mismos colores que el badge de estado de la tarjeta).
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            listOf("Pendiente", "Confirmada", "Completada", "Cancelada").forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(EstadosColor.cita(e).fg))
                    Spacer(Modifier.width(4.dp))
                    Text(EstadosColor.etiquetaCita(e), color = c.textoSuave, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun CeldaDia(
    celda: CeldaMes,
    conteo: ConteoDia?,
    elegido: Boolean,
    onElegir: (String) -> Unit,
    modifier: Modifier,
) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    if (!celda.delMes) {
        // Relleno del mes anterior/siguiente: atenuado, sin conteo ni toque (como la web).
        Box(modifier.clearAndSetSemantics { }, contentAlignment = Alignment.TopCenter) {
            Text(
                celda.dia.toString(), color = c.textoSuave.copy(alpha = 0.35f), fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        return
    }
    val total = conteo?.total ?: 0
    val puntos = conteo?.estadosPuntos.orEmpty()
    val descripcion = descripcionDia(celda.iso, conteo, celda.esHoy, elegido)
    Column(
        modifier.padding(2.dp)
            .clip(forma)
            .background(if (elegido) c.navy else if (celda.esHoy) c.chipBg else c.superficie)
            .then(if (celda.esHoy && !elegido) Modifier.border(2.dp, c.lav, forma) else Modifier)
            .clickable { onElegir(celda.iso) }
            .clearAndSetSemantics {
                contentDescription = descripcion
                role = Role.Button
                selected = elegido
            }
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            celda.dia.toString(),
            color = if (elegido) c.sobreNavy else c.texto,
            fontSize = 14.sp,
            fontWeight = if (celda.esHoy || elegido) FontWeight.Bold else FontWeight.Normal,
        )
        // Número y puntos en líneas separadas: con letra grande, en una sola fila
        // no caben en el ancho de un día a 390 dp.
        if (total > 0) {
            val colorNum = if (elegido) c.sobreNavy
                else EstadosColor.cita(puntos.firstOrNull { it != "Cancelada" } ?: puntos.firstOrNull()).fg
            Text(
                if (total > 99) "99+" else total.toString(),
                color = colorNum, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            )
        }
        if (puntos.isNotEmpty()) {
            Row(Modifier.padding(top = 1.dp, bottom = 3.dp)) {
                puntos.forEach { e ->
                    Box(
                        Modifier.padding(horizontal = 1.dp).size(5.dp).clip(CircleShape)
                            .background(EstadosColor.cita(e).fg),
                    )
                }
            }
        }
    }
}
