package pe.saniape.app.ui.clinica.fisio

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.EVA_VALORES
import pe.saniape.app.data.staff.MOTIVOS_NO_VOLVIO
import pe.saniape.app.data.staff.PuntoDolor
import pe.saniape.app.data.staff.colorEvaArgb
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.resumenDolor
import pe.saniape.app.data.staff.textoAvisoRenovacion
import pe.saniape.app.data.staff.textoMotivoCierre
import pe.saniape.app.ui.clinica.odontologia.recordarReconocedorVoz
import pe.saniape.app.ui.clinica.pacientes.DialogoFecha
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.theme.Sania

/**
 * Piezas de UI de las mejoras de FISIOTERAPIA (gemelas de los componentes de fisio de la
 * web). Quien las monta decide si corresponde (`citaEsFisio`): acá solo se pinta.
 */

/**
 * Escala EVA 0–10 en una fila de 11 botones: un toque marca, otro toque en el
 * mismo número lo quita (opcional, nunca bloquea el cierre). Gemelo de EvaEscala.tsx.
 */
@Composable
fun EvaEscala(etiqueta: String, valor: Int?, onCambio: (Int?) -> Unit) {
    val c = Sania.colors
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(etiqueta.uppercase(), color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp, modifier = Modifier.weight(1f))
            Text(if (valor == null) "sin marcar" else "$valor/10", color = if (valor == null) c.textoSuave else c.texto,
                fontSize = 11.sp, fontWeight = if (valor == null) FontWeight.Normal else FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            EVA_VALORES.forEach { n ->
                val activo = valor == n
                val color = Color(colorEvaArgb(n))
                Box(
                    Modifier.weight(1f).height(38.dp)
                        .clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .background(if (activo) color else c.superficie)
                        .border(1.5.dp, if (activo) color else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                        .semantics { contentDescription = "$n de 10" }
                        .clickable { onCambio(if (activo) null else n) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$n", color = if (activo) Color.White else c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    // Marca de color abajo (como el inset de la web): el valor se lee sin tocar.
                    if (!activo) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(color))
                }
            }
        }
    }
}

/** Las dos filas EVA del cierre de una sesión de fisio (al entrar / al salir). */
@Composable
fun BloqueEva(dolorInicio: Int?, dolorFin: Int?, onInicio: (Int?) -> Unit, onFin: (Int?) -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
            Text("📉", fontSize = 15.sp)
            Spacer(Modifier.width(7.dp))
            Text("Dolor (EVA 0–10)", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text("opcional", color = c.textoSuave, fontSize = 11.sp)
        }
        EvaEscala("Al entrar", dolorInicio, onInicio)
        Spacer(Modifier.height(10.dp))
        EvaEscala("Al salir", dolorFin, onFin)
    }
}

/**
 * Curva del dolor por sesión (entrada punteada, salida llena). Gemelo de
 * EvaCurva.tsx; sin datos no se pinta nada.
 */
@Composable
fun CurvaDolor(puntos: List<PuntoDolor>) {
    if (puntos.isEmpty()) return
    val c = Sania.colors
    val res = resumenDolor(puntos)
    val cEntrada = c.lav
    val cSalida = c.navy
    val cGrid = c.borde
    val cFondo = c.superficie
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("DOLOR (EVA)", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Spacer(Modifier.width(10.dp))
            Leyenda(cEntrada, punteada = true, texto = "Al entrar")
            Spacer(Modifier.width(8.dp))
            Leyenda(cSalida, punteada = false, texto = "Al salir")
            Spacer(Modifier.weight(1f))
            res?.let {
                Text("${it.desde} → ${it.hasta}" + if (it.cambio != 0) " (${if (it.cambio > 0) "+" else ""}${it.cambio})" else "",
                    color = c.texto, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            // Eje Y (0 / 5 / 10)
            Column(Modifier.height(96.dp).width(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                listOf("10", "5", "0").forEach { Text(it, color = c.textoSuave, fontSize = 8.sp) }
            }
            Canvas(
                Modifier.weight(1f).height(96.dp).semantics {
                    contentDescription = "Curva de dolor en ${puntos.size} sesiones" +
                        (res?.let { ", de ${it.desde} a ${it.hasta}" } ?: "")
                },
            ) {
                val padY = 6.dp.toPx()
                val padX = 8.dp.toPx()
                val iw = size.width - padX * 2
                val ih = size.height - padY * 2
                fun x(i: Int) = padX + if (puntos.size == 1) iw / 2 else i * iw / (puntos.size - 1)
                fun y(v: Int) = padY + ih - (v / 10f) * ih
                listOf(0, 5, 10).forEach { v ->
                    drawLine(cGrid, Offset(0f, y(v)), Offset(size.width, y(v)), strokeWidth = 1.dp.toPx())
                }
                fun linea(valor: (PuntoDolor) -> Int?, color: Color, punteada: Boolean) {
                    // Tramos continuos: una sesión sin ese valor corta la línea.
                    var path: Path? = null
                    val trazos = mutableListOf<Path>()
                    puntos.forEachIndexed { i, p ->
                        val v = valor(p)
                        if (v == null) { path?.let { trazos.add(it) }; path = null; return@forEachIndexed }
                        val actual = path
                        if (actual == null) path = Path().apply { moveTo(x(i), y(v)) } else actual.lineTo(x(i), y(v))
                    }
                    path?.let { trazos.add(it) }
                    trazos.forEach { t ->
                        drawPath(t, color, style = Stroke(
                            width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
                            pathEffect = if (punteada) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null,
                        ))
                    }
                }
                linea({ it.inicio }, cEntrada, punteada = true)
                linea({ it.fin }, cSalida, punteada = false)
                puntos.forEachIndexed { i, p ->
                    p.inicio?.let { v ->
                        drawCircle(cFondo, 3.5.dp.toPx(), Offset(x(i), y(v)))
                        drawCircle(cEntrada, 3.5.dp.toPx(), Offset(x(i), y(v)), style = Stroke(2.dp.toPx()))
                    }
                    p.fin?.let { v -> drawCircle(cSalida, 4.dp.toPx(), Offset(x(i), y(v))) }
                }
            }
        }
        // Eje X: número de sesión (todas si caben; si no, algunas + la última).
        val cada = maxOf(1, (puntos.size + 7) / 8)
        Row(Modifier.fillMaxWidth().padding(start = 16.dp)) {
            puntos.forEachIndexed { i, p ->
                Text(if (i % cada == 0 || i == puntos.lastIndex) "#${p.numero}" else "",
                    color = c.textoSuave, fontSize = 8.sp, modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
private fun Leyenda(color: Color, punteada: Boolean, texto: String) {
    val c = Sania.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 16.dp, height = 6.dp)) {
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx(),
                pathEffect = if (punteada) PathEffect.dashPathEffect(floatArrayOf(5f, 4f)) else null)
        }
        Spacer(Modifier.width(3.dp))
        Text(texto, color = c.textoSuave, fontSize = 10.sp)
    }
}

/** "⚠ N faltas sin aviso" (M10). Solo visibilidad: no descuenta ni aplica políticas. */
@Composable
fun AvisoFaltasSinAviso(n: Int) {
    if (n <= 0) return
    val c = Sania.colors
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(c.pendBg).border(1.dp, c.pend, RoundedCornerShape(Sania.shape.md.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text("⚠ $n ${if (n == 1) "falta" else "faltas"} sin aviso", color = c.pend, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * M3 · "Le quedan N sesiones · ¿renovar el paquete?" con ➕ Ampliar / 📦 Nuevo
 * paquete. Sin botones si quien mira no gestiona sesiones.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AvisoRenovacion(restantes: Int, onAmpliar: (() -> Unit)?, onNuevoPaquete: (() -> Unit)?) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(c.pendBg).border(1.dp, c.pend.copy(alpha = 0.4f), RoundedCornerShape(Sania.shape.sm.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⏳ ", fontSize = 13.sp)
            Text(textoAvisoRenovacion(restantes), color = c.pend, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(" · ¿renovar el paquete?", color = c.textoSuave, fontSize = 12.sp)
        }
        if (onAmpliar != null || onNuevoPaquete != null) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                onAmpliar?.let { f ->
                    Box(Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
                        .border(1.dp, c.navy.copy(alpha = 0.4f), RoundedCornerShape(Sania.shape.sm.dp))
                        .clickable { f() }.padding(horizontal = 12.dp, vertical = 7.dp)) {
                        Text("➕ Ampliar", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                onNuevoPaquete?.let { f ->
                    Box(Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.navy)
                        .clickable { f() }.padding(horizontal = 12.dp, vertical = 7.dp)) {
                        Text("📦 Nuevo paquete", color = c.sobreNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Lo que devuelve el modal "No volvió". */
data class DatosNoVolvio(val motivo: String, val detalle: String, val fecha: String, val cancelarFuturas: Boolean)

/**
 * M4 · "🚪 No volvió": cierra un tratamiento abandonado con motivo (chips +
 * detalle, obligatorio en "Otro"), fecha (hoy o antes) y, si tiene citas o
 * sesiones futuras pendientes, si se cancelan. Gemelo de NoVolvioModal.tsx.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModalNoVolvio(
    nombreTratamiento: String,
    futurasPendientes: Int,
    guardando: Boolean,
    onCancelar: () -> Unit,
    onConfirmar: (DatosNoVolvio) -> Unit,
) {
    val c = Sania.colors
    val hoy = remember { hoyClinicaIso() }
    var motivo by remember { mutableStateOf("") }
    var detalle by remember { mutableStateOf("") }
    var fecha by remember { mutableStateOf(hoy) }
    var cancelarFuturas by remember { mutableStateOf(true) }
    var eligiendoFecha by remember { mutableStateOf(false) }
    val valido = motivo.isNotEmpty() && textoMotivoCierre(motivo, detalle) != null && fecha.isNotEmpty() && fecha <= hoy

    if (eligiendoFecha) DialogoFecha(onElegir = { fecha = it }, onCerrar = { eligiendoFecha = false })

    DialogoForm(
        titulo = "🚪 No volvió",
        subtitulo = nombreTratamiento,
        textoAccion = if (guardando) "Guardando…" else "Cerrar tratamiento",
        accionHabilitada = valido && !guardando,
        onCancelar = { if (!guardando) onCancelar() },
        onAccion = { if (valido && !guardando) onConfirmar(DatosNoVolvio(motivo, detalle.trim(), fecha, cancelarFuturas)) },
    ) {
        Text(
            "El tratamiento queda Suspendido como abandonado: sale de Retención y del dinero en riesgo. " +
                "Lo atendido y lo pagado no se tocan, y podrás ▶ Reactivarlo si vuelve.",
            color = c.textoSuave, fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))
        EtqForm("Motivo")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MOTIVOS_NO_VOLVIO.forEach { m ->
                val activo = motivo == m.id
                Box(
                    Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                        .background(if (activo) c.navy else c.superficie)
                        .border(1.5.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
                        .clickable { motivo = m.id }.padding(horizontal = 12.dp, vertical = 7.dp),
                ) { Text(m.label, color = if (activo) c.sobreNavy else c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(12.dp))
        EtqForm(if (motivo == "otro") "Detalle" else "Detalle (opcional)")
        androidx.compose.material3.OutlinedTextField(
            colors = coloresCampoForm(),
            value = detalle, onValueChange = { detalle = it.take(300) },
            placeholder = { Text(if (motivo == "otro") "Cuéntanos qué pasó" else "Ej: se le llamó 3 veces", color = c.textoSuave) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        EtqForm("Fecha del cierre")
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                .background(c.superficie).border(1.dp, if (fecha > hoy) c.error else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                .clickable { eligiendoFecha = true }.padding(horizontal = 12.dp, vertical = 13.dp),
        ) { Text("📅 $fecha", color = c.texto, fontSize = 14.sp) }
        if (fecha > hoy) Text("La fecha no puede ser futura.", color = c.error, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
        if (futurasPendientes > 0) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.pendBg).border(1.dp, c.pend.copy(alpha = 0.4f), RoundedCornerShape(Sania.shape.sm.dp))
                    .clickable { cancelarFuturas = !cancelarFuturas }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(if (cancelarFuturas) "☑" else "☐", fontSize = 18.sp, color = if (cancelarFuturas) c.navy else c.textoSuave)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (futurasPendientes == 1) "Cancelar su cita/sesión futura pendiente"
                        else "Cancelar sus $futurasPendientes citas/sesiones futuras pendientes",
                        color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Text("Desmárcalo si todavía quieres esperarlo en la cita que ya tiene.", color = c.textoSuave, fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * Dictado 🎤 para los campos del cierre (técnicas y mejorías). UN reconocedor para
 * todo el modal: el botón que se toca elige a qué campo va lo dictado. Reusa el
 * reconocedor del odontograma (SpeechRecognizer es-PE; en iOS avisa usar el
 * micrófono del teclado). El texto se AGREGA al campo para revisarlo antes de guardar.
 */
class DictadoCampos internal constructor(
    val disponible: Boolean,
    private val iniciarEn: (String) -> Unit,
    private val parar: () -> Unit,
    private val destinoActual: () -> String?,
    private val escuchandoAhora: () -> Boolean,
) {
    fun escuchando(campo: String): Boolean = escuchandoAhora() && destinoActual() == campo
    fun alternar(campo: String) { if (escuchando(campo)) parar() else iniciarEn(campo) }
}

@Composable
fun recordarDictadoCampos(onFinal: (campo: String, texto: String) -> Unit): DictadoCampos {
    var destino by remember { mutableStateOf<String?>(null) }
    var escuchando by remember { mutableStateOf(false) }
    val control = recordarReconocedorVoz(
        onTexto = { texto, final -> if (final) destino?.let { onFinal(it, texto) } },
        onEscuchando = { escuchando = it },
        onError = { pe.saniape.app.ui.Toaster.error(it) },
    )
    return DictadoCampos(
        disponible = control.disponible,
        iniciarEn = { campo -> control.detener(); destino = campo; control.iniciar() },
        parar = { control.detener() },
        destinoActual = { destino },
        escuchandoAhora = { escuchando },
    )
}

/** Botón 🎤 de un campo (se pinta "escuchando" mientras dicta a ese campo). */
@Composable
fun BotonDictar(dictado: DictadoCampos, campo: String) {
    val c = Sania.colors
    val activo = dictado.escuchando(campo)
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
            .background(if (activo) c.error else c.chipBg)
            .clickable { dictado.alternar(campo) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(if (activo) "● Escuchando… (toca para parar)" else "🎤 Dictar",
            color = if (activo) c.sobreNavy else c.navy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Chips de mejoría (fisio): tocar agrega/quita (alternarMejoria); los marcados se resaltan. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipsMejoriaFisio(texto: String, onCambio: (String) -> Unit) {
    val c = Sania.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        pe.saniape.app.data.staff.MEJORIAS_RAPIDAS.forEach { chip ->
            val activo = pe.saniape.app.data.staff.tieneMejoria(texto, chip)
            Text(
                (if (activo) "✓ " else "") + chip,
                color = if (activo) c.sobreNavy else c.navy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                    .background(if (activo) c.navy else c.chipBg)
                    .clickable { onCambio(pe.saniape.app.data.staff.alternarMejoria(texto, chip)) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
