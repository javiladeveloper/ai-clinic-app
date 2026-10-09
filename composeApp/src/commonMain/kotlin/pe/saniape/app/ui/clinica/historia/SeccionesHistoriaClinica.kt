package pe.saniape.app.ui.clinica.historia

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import pe.saniape.app.data.staff.AtencionHc
import pe.saniape.app.data.staff.CUADRANTES_ADULTO
import pe.saniape.app.data.staff.CUADRANTES_DECIDUO
import pe.saniape.app.data.staff.ConsentimientoHc
import pe.saniape.app.data.staff.ConsultaHc
import pe.saniape.app.data.staff.DatosAntropometriaHc
import pe.saniape.app.data.staff.DatosAtencionesHc
import pe.saniape.app.data.staff.DatosConsentimientosHc
import pe.saniape.app.data.staff.DatosConsultasHc
import pe.saniape.app.data.staff.DatosContactoHc
import pe.saniape.app.data.staff.DatosDiagnosticoHc
import pe.saniape.app.data.staff.DatosEncabezadoHc
import pe.saniape.app.data.staff.DatosEvolucionEvaHc
import pe.saniape.app.data.staff.DatosExamenesHc
import pe.saniape.app.data.staff.DatosFiliacionHc
import pe.saniape.app.data.staff.DatosFisioEvaluacionHc
import pe.saniape.app.data.staff.DatosFotosHc
import pe.saniape.app.data.staff.DatosOdontogramaHc
import pe.saniape.app.data.staff.DatosParesHc
import pe.saniape.app.data.staff.DatosPeriodontogramaHc
import pe.saniape.app.data.staff.DatosPiezasSesionHc
import pe.saniape.app.data.staff.DatosPsicologiaHc
import pe.saniape.app.data.staff.DatosRecetasHc
import pe.saniape.app.data.staff.DatosSignosVitalesHc
import pe.saniape.app.data.staff.DatosTratamientosHc
import pe.saniape.app.data.staff.EvaluacionPsicoHc
import pe.saniape.app.data.staff.FirmaHc
import pe.saniape.app.data.staff.FotoHc
import pe.saniape.app.data.staff.HallazgoDentalHc
import pe.saniape.app.data.staff.HistoriaClinicaDoc
import pe.saniape.app.data.staff.ParHc
import pe.saniape.app.data.staff.PuntoDolor
import pe.saniape.app.data.staff.PuntoEvaHc
import pe.saniape.app.data.staff.PuntoSerieHc
import pe.saniape.app.data.staff.RecetaHc
import pe.saniape.app.data.staff.SeccionHc
import pe.saniape.app.data.staff.TratamientoHc
import pe.saniape.app.data.staff.hallazgosParaOdontograma
import pe.saniape.app.data.staff.numeroHc
import pe.saniape.app.data.staff.piezasPeriodontogramaHc
import pe.saniape.app.data.staff.seriePesoHc
import pe.saniape.app.data.staff.DatosNoLegiblesHc
import pe.saniape.app.data.staff.dineroHc
import pe.saniape.app.data.staff.textoHallazgoHc
import pe.saniape.app.data.staff.tieneDeciduosHc
import pe.saniape.app.ui.AccionesNativas
import pe.saniape.app.ui.VisorImagen
import pe.saniape.app.ui.clinica.fisio.CurvaDolor
import pe.saniape.app.ui.clinica.fisio.MapaCorporal
import pe.saniape.app.ui.clinica.odontologia.Boca
import pe.saniape.app.ui.theme.Sania

/**
 * Las secciones de la historia clínica, una por `tipo` del contrato. Solo
 * dibujan: qué va, en qué orden y con qué título lo decide el servidor.
 */

/** Lo que una sección puede pedirle a la pantalla. */
internal class AccionesHc(
    val acciones: AccionesNativas,
    /** Psicología: volver a pedir la historia con lo protegido (informe / puntajes). */
    val pedirPsico: (informe: Boolean, tests: Boolean) -> Unit,
    val psicoInforme: Boolean,
    val psicoTests: Boolean,
)

@Composable
internal fun SeccionHistoria(s: SeccionHc, doc: HistoriaClinicaDoc, acc: AccionesHc) {
    val d = s.datos
    // El encabezado es la cabecera del documento: sin tarjeta con título.
    if (d is DatosEncabezadoHc) { EncabezadoHc(d, doc); return }
    TarjetaSeccion(s.titulo, s.obligatoria) {
        when (d) {
            is DatosFiliacionHc -> FiliacionHc(d)
            is DatosContactoHc -> FilaPar(ParHc("Correo", d.email))
            is DatosDiagnosticoHc -> DiagnosticoHcVista(d)
            is DatosParesHc -> d.items.forEach { FilaPar(it) }
            is DatosAtencionesHc -> d.items.forEachIndexed { i, a -> if (i > 0) Separador(); AtencionVista(a) }
            is DatosConsentimientosHc -> {
                d.items.forEach { ConsentimientoVista(it) }
                if (d.nota.isNotBlank()) TextoSuave(d.nota)
            }
            is DatosTratamientosHc -> d.items.forEachIndexed { i, t -> if (i > 0) Separador(); TratamientoVista(t, doc.graficos, t.moneda ?: doc.regional.moneda) }
            is DatosConsultasHc -> d.items.forEach { ConsultaVista(it) }
            is DatosExamenesHc -> d.items.forEach { ex ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(ex.titulo, color = Sania.colors.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    TextoSuave("${fechaHc(ex.fecha)} · ${ex.estado}")
                    ex.resultado?.takeIf { it.isNotBlank() }?.let { Text(it, color = Sania.colors.texto, fontSize = 12.sp) }
                    if (ex.resultadoUrl != null) TextoSuave("📎 Resultado adjunto (se ve en Exámenes de la ficha)")
                }
            }
            is DatosFisioEvaluacionHc -> FisioEvaluacionVista(d, doc.graficos)
            is DatosEvolucionEvaHc -> d.series.forEach { serie ->
                Text(serie.titulo, color = Sania.colors.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                CurvaEva(serie.puntos)
                Spacer(Modifier.height(8.dp))
            }
            is DatosOdontogramaHc -> OdontogramaHcVista(d, doc.paciente.id)
            is DatosPeriodontogramaHc -> PeriodontogramaVista(d)
            is DatosPiezasSesionHc -> d.items.forEach { p ->
                FilaPar(ParHc("Sesión #${p.numero} · ${fechaHc(p.fecha)}", "${p.tratamiento}: ${p.piezas.joinToString(", ")}"))
            }
            is DatosPsicologiaHc -> PsicologiaVista(d, acc)
            is DatosSignosVitalesHc -> SignosVitalesVista(d, doc.graficos)
            is DatosRecetasHc -> d.items.forEach { RecetaVista(it) }
            is DatosAntropometriaHc -> AntropometriaVista(d, doc.graficos)
            is DatosFotosHc -> FotosVista(d, doc.graficos)
            DatosNoLegiblesHc -> Aviso("No se pudo mostrar esta sección aquí — ver el PDF (🖨 Imprimir / compartir PDF).",
                Sania.colors.pend, Sania.colors.pendBg)
            else -> {}
        }
    }
}

// ── Piezas comunes ──────────────────────────────────────────────────────────

@Composable
internal fun TarjetaSeccion(titulo: String, obligatoria: Boolean, contenido: @Composable () -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Text(titulo.uppercase(), color = c.lav, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp, modifier = Modifier.weight(1f))
            if (obligatoria) Text("Norma", color = c.textoSuave, fontSize = 9.sp)
        }
        contenido()
    }
}

@Composable
private fun FilaPar(p: ParHc) {
    val c = Sania.colors
    val vacio = p.valor.isBlank()
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("${p.etiqueta}:", color = if (p.alerta) c.error else c.textoSuave, fontSize = 12.sp,
            fontWeight = if (p.alerta) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.width(130.dp))
        Text(if (vacio) "—" else p.valor, color = when { p.alerta -> c.error; vacio -> c.textoSuave; else -> c.texto },
            fontSize = 12.sp, fontWeight = if (p.alerta) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TextoSuave(t: String) = Text(t, color = Sania.colors.textoSuave, fontSize = 11.sp)

@Composable
private fun Subtitulo(t: String) =
    Text(t.uppercase(), color = Sania.colors.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 0.4.sp, modifier = Modifier.padding(top = 8.dp, bottom = 3.dp))

@Composable
private fun Separador() =
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(Sania.colors.borde))

@Composable
private fun Aviso(texto: String, fg: Color, bg: Color) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(bg).border(1.dp, fg.copy(alpha = 0.4f), RoundedCornerShape(Sania.shape.sm.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) { Text(texto, color = fg, fontSize = 12.sp) }
}

private fun fechaHora(fecha: String, hora: String?): String =
    fechaHc(fecha) + (hora?.takeIf { it.isNotBlank() }?.let { " · ${it.take(5)}" } ?: "")

// ── General ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EncabezadoHc(d: DatosEncabezadoHc, doc: HistoriaClinicaDoc) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(c.chipBg), contentAlignment = Alignment.Center) {
                Text(d.iniciales.ifBlank { doc.paciente.iniciales }, color = c.navy, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("HISTORIA CLÍNICA — ${doc.clinica.nombre.uppercase()}", color = c.lav, fontSize = 9.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                Text(d.nombre.ifBlank { doc.paciente.nombre }, color = c.navy, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                TextoSuave("Generada el ${fechaHc(doc.fechaGeneracion)}")
            }
        }
        if (d.filas.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                d.filas.forEach { f ->
                    Row(
                        Modifier.clip(RoundedCornerShape(20.dp)).background(c.fondo)
                            .border(1.dp, c.borde, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text("${f.etiqueta}: ", color = c.textoSuave, fontSize = 11.sp)
                        Text(f.valor, color = c.texto, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FiliacionHc(d: DatosFiliacionHc) {
    val c = Sania.colors
    Text(d.numeroHC, color = c.navy, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    d.apertura?.let { TextoSuave("Apertura: ${fechaHc(it)}") }
    d.ipress?.let { TextoSuave(it) }
    Spacer(Modifier.height(6.dp))
    d.filas.forEach { FilaPar(it) }
    d.alergias?.takeIf { it.isNotBlank() }?.let { FilaPar(ParHc("Alergias", it, alerta = true)) }
}

@Composable
private fun DiagnosticoHcVista(d: DatosDiagnosticoHc) {
    val c = Sania.colors
    d.reciente?.let { r ->
        Text(r.texto, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        TextoSuave("Registrado el ${fechaHc(r.fecha)}")
    }
    d.inicial?.takeIf { it.isNotBlank() }?.let {
        if (d.reciente != null) FilaPar(ParHc("Motivo / diagnóstico inicial", it))
        else Text(it, color = c.texto, fontSize = 14.sp)
    }
}

// ── Atenciones (medicina / odontología) ─────────────────────────────────────

@Composable
private fun AtencionVista(a: AtencionHc) {
    val c = Sania.colors
    Text("${fechaHora(a.fecha, a.hora)} — ${a.titulo}", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    a.profesional?.let { TextoSuave("Atendió: $it") }
    a.triaje?.let { TextoSuave("Triaje: $it") }
    if (a.campos.isNotEmpty()) { Spacer(Modifier.height(4.dp)); a.campos.forEach { FilaPar(it) } }
    if (a.diagnosticos.isNotEmpty() || !a.diagnosticoSinCodificar.isNullOrBlank()) {
        Subtitulo("Diagnóstico")
        a.diagnosticos.forEach { dx ->
            Text(
                listOfNotNull(dx.codigo?.takeIf { it.isNotBlank() }, dx.descripcion).joinToString(" — ") +
                    (dx.tipoNombre.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""),
                color = c.texto, fontSize = 12.sp,
            )
        }
        a.diagnosticoSinCodificar?.takeIf { it.isNotBlank() }?.let { Text(it, color = c.texto, fontSize = 12.sp) }
    }
    if (a.recetas.isNotEmpty()) { Subtitulo("Receta"); a.recetas.forEach { RecetaVista(it) } }
    if (a.consentimientos.isNotEmpty()) { Subtitulo("Consentimiento"); a.consentimientos.forEach { ConsentimientoVista(it) } }
    // Solo en pantalla: lo que la norma espera y falta (no sale impreso).
    if (a.faltan.isNotEmpty()) Aviso("⚠ Falta según la norma: ${a.faltan.joinToString(", ")}", c.pend, c.pendBg)
    FirmaVista(a.firma)
}

@Composable
private fun RecetaVista(r: RecetaHc) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(c.fondo).padding(8.dp),
    ) {
        Text("℞ Receta ${r.numero} · ${fechaHc(r.fecha)}" + if (r.anulada) " · ANULADA" else "",
            color = if (r.anulada) c.error else c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        r.items.forEach { Text("• $it", color = if (r.anulada) c.textoSuave else c.texto, fontSize = 12.sp) }
        r.profesional?.let { TextoSuave(it) }
    }
}

@Composable
private fun ConsentimientoVista(k: ConsentimientoHc) {
    val c = Sania.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text("📝 ${k.procedimiento}", color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        TextoSuave(listOfNotNull(
            k.estado.takeIf { it.isNotBlank() },
            k.emitido?.let { "emitido ${fechaHc(it)}" },
            k.firmado?.let { "firmado ${fechaHc(it)}" },
            k.profesional,
        ).joinToString(" · "))
    }
}

@Composable
private fun FirmaVista(f: FirmaHc) {
    val c = Sania.colors
    if (f.nombre == null && f.digital == null) return
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(8.dp),
    ) {
        if (f.titulo.isNotBlank()) TextoSuave(f.titulo)
        f.nombre?.let { Text(it, color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        f.colegiatura?.let { TextoSuave(it) }
        f.digital?.let { dg ->
            Text("🔏 Firmado digitalmente por ${dg.firmante} · ${fechaHc(dg.firmadoAt)}", color = c.ok, fontSize = 11.sp)
            dg.certificado?.let { TextoSuave("Certificado: $it") }
        }
    }
}

// ── Tratamientos y consultas ────────────────────────────────────────────────

private fun puntosDolor(p: List<PuntoEvaHc>): List<PuntoDolor> = p.map { PuntoDolor(it.numero, it.fecha, it.inicio, it.fin) }

@Composable
private fun CurvaEva(puntos: List<PuntoEvaHc>) {
    if (puntos.isEmpty()) return
    CurvaDolor(puntosDolor(puntos))
}

@Composable
private fun TratamientoVista(t: TratamientoHc, graficos: Boolean, moneda: String) {
    val c = Sania.colors
    Row(verticalAlignment = Alignment.Top) {
        Text(t.nombre, color = c.navy, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(listOfNotNull(t.modalidad, t.estado).joinToString(" · "), color = c.textoSuave, fontSize = 11.sp, textAlign = TextAlign.End)
    }
    t.estadoPago?.let { TextoSuave("Pago: $it") }
    FilaPar(ParHc("Profesional", t.profesional ?: ""))
    t.fechaInicio?.let { FilaPar(ParHc("Inicio", fechaHc(it))) }
    if (t.sesiones.isNotBlank()) FilaPar(ParHc("Sesiones", t.sesiones))
    t.precios.forEach { FilaPar(it) }
    t.diagnostico?.takeIf { it.isNotBlank() }?.let { FilaPar(ParHc("Diagnóstico", it)) }
    t.notas?.takeIf { it.isNotBlank() }?.let { FilaPar(ParHc("Notas", it)) }
    if (graficos && t.curvaEva.isNotEmpty()) { Subtitulo("Dolor (EVA) por sesión"); CurvaEva(t.curvaEva) }
    val q = t.cuadro
    if (q.filas.isNotEmpty()) {
        Subtitulo("Sesiones realizadas")
        q.comunes.forEach { TextoSuave(it) }
        q.filas.forEach { s ->
            Column(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.fondo).padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Row {
                    Text("#${s.numero} · ${fechaHora(s.fecha, s.hora)}", color = c.texto, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (q.verCosto) s.costo?.let { Text(dineroHc(it, moneda), color = c.teal, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
                if (q.verProfesional) s.profesional?.let { TextoSuave(it) }
                if (q.verDuracion) s.duracion?.let { TextoSuave("$it min") }
                s.procedimientos?.takeIf { it.isNotBlank() }?.let { Text("Procedimientos: $it", color = c.texto, fontSize = 12.sp) }
                if (s.piezas.isNotEmpty()) Text("Piezas: ${s.piezas.joinToString(" · ")}", color = c.texto, fontSize = 12.sp)
                s.eva?.let { Text("EVA: $it", color = c.texto, fontSize = 12.sp) }
                s.motivo?.takeIf { it.isNotBlank() }?.let { TextoSuave(it) }
                if (q.verEvolucion) s.mejorias?.takeIf { it.isNotBlank() }?.let {
                    Text("Evolución: $it", color = c.ok, fontSize = 12.sp)
                }
            }
        }
    }
    t.pagos?.let { p ->
        Subtitulo("Pagos")
        p.filas.forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                Text("${fechaHc(f.fecha)} · ${f.metodo}" + (f.nota?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    color = c.texto, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(dineroHc(f.monto, moneda), color = c.texto, fontSize = 12.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Acordado ${dineroHc(p.acordado, moneda)}", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("Pagado ${dineroHc(p.pagado, moneda)}", color = c.ok, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("Saldo ${dineroHc(p.saldo, moneda)}", color = if (p.saldo > 0.005) c.error else c.ok, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ConsultaVista(k: ConsultaHc) {
    val c = Sania.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(fechaHora(k.fecha, k.hora) + (k.tipo?.let { " · $it" } ?: ""), color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        TextoSuave(listOfNotNull(k.profesional, k.estado).joinToString(" · "))
        k.diagnostico?.takeIf { it.isNotBlank() }?.let { Text("Diagnóstico: $it", color = c.texto, fontSize = 12.sp) }
        k.notas?.takeIf { it.isNotBlank() }?.let { Text(it, color = c.texto, fontSize = 12.sp) }
    }
}

// ── Fisioterapia ────────────────────────────────────────────────────────────

@Composable
private fun FisioEvaluacionVista(d: DatosFisioEvaluacionHc, graficos: Boolean) {
    val c = Sania.colors
    if (d.resumen.isNotBlank()) Text(d.resumen, color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    if (d.comparativo.isNotEmpty()) {
        Subtitulo(if (d.hayUltima) "Inicial → última" else "Evaluación inicial")
        d.comparativo.forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(f.etiqueta, color = c.texto, fontSize = 12.sp)
                    f.nota?.takeIf { it.isNotBlank() }?.let { TextoSuave(it) }
                }
                Text(f.inicial + (f.ultima?.let { " → $it" } ?: ""), color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (f.cambio.isNotBlank()) {
                    Text("  ${f.cambio}", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = when (f.mejora) { true -> c.ok; false -> c.error; null -> c.textoSuave })
                }
            }
        }
    }
    val hayZonas = !d.zonasInicial.isNullOrEmpty() || !d.zonasUltima.isNullOrEmpty()
    if (hayZonas) {
        Subtitulo("Zonas de dolor")
        if (graficos) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                d.zonasInicial?.takeIf { it.isNotEmpty() }?.let {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        TextoSuave("Inicial")
                        MapaCorporal(valor = it, onChange = null, ancho = 120.dp, leyenda = false, mostrarTexto = false)
                    }
                }
                d.zonasUltima?.takeIf { it.isNotEmpty() }?.let {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        TextoSuave("Última")
                        MapaCorporal(valor = it, onChange = null, ancho = 120.dp, leyenda = false, mostrarTexto = false)
                    }
                }
            }
        }
        if (d.zonasTexto.inicial.isNotEmpty()) FilaPar(ParHc("Inicial", d.zonasTexto.inicial.joinToString(", ")))
        if (d.zonasTexto.ultima.isNotEmpty()) FilaPar(ParHc("Última", d.zonasTexto.ultima.joinToString(", ")))
    }
    if (d.objetivos.isNotEmpty()) {
        Subtitulo("Objetivos · ${d.objetivosLogrados} de ${d.objetivos.size} logrados")
        d.objetivos.forEach { o ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text(if (o.logrado) "✓ " else "○ ", color = if (o.logrado) c.ok else c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Column(Modifier.weight(1f)) {
                    Text(o.texto + o.medida, color = c.texto, fontSize = 12.sp)
                    if (o.estado.isNotBlank()) TextoSuave(o.estado)
                }
            }
        }
    }
}

// ── Odontología ─────────────────────────────────────────────────────────────

@Composable
private fun BocaHc(titulo: String, hallazgos: List<HallazgoDentalHc>, pacienteId: String) {
    val (registros, catalogo) = remember(hallazgos) { hallazgosParaOdontograma(hallazgos, pacienteId) }
    val porDiente = remember(registros) { registros.groupBy { it.diente } }
    Subtitulo(titulo)
    // Solo para mirar: el odontograma nativo con onTocar vacío (como "Mostrar al paciente").
    Boca(cuadrantes = CUADRANTES_ADULTO, porDiente = porDiente, catalogo = catalogo, onTocar = { _, _ -> })
    if (tieneDeciduosHc(hallazgos)) {
        Spacer(Modifier.height(6.dp))
        Boca(cuadrantes = CUADRANTES_DECIDUO, porDiente = porDiente, catalogo = catalogo, onTocar = { _, _ -> })
    }
    if (hallazgos.isEmpty()) TextoSuave("Sin hallazgos registrados.")
    else hallazgos.forEach { Text("• ${textoHallazgoHc(it)}", color = Sania.colors.texto, fontSize = 11.sp) }
}

@Composable
private fun OdontogramaHcVista(d: DatosOdontogramaHc, pacienteId: String) {
    d.inicial?.let { BocaHc("Odontograma inicial · ${fechaHc(it.fecha)}", it.hallazgos, pacienteId) }
    BocaHc("Odontograma actual · ${fechaHc(d.actual.fecha)}", d.actual.hallazgos, pacienteId)
    FirmaVista(d.firma)
}

@Composable
private fun PeriodontogramaVista(d: DatosPeriodontogramaHc) {
    FilaPar(ParHc("Último examen", fechaHc(d.fecha)))
    d.profesional?.let { FilaPar(ParHc("Profesional", it)) }
    val piezas = piezasPeriodontogramaHc(d)
    if (piezas.isNotEmpty()) FilaPar(ParHc("Piezas evaluadas", "${piezas.size}"))
    d.notas?.takeIf { it.isNotBlank() }?.let { FilaPar(ParHc("Notas", it)) }
    TextoSuave("Las mediciones pieza por pieza salen en el PDF.")
}

// ── Psicología (privacidad) ─────────────────────────────────────────────────

@Composable
private fun PsicologiaVista(d: DatosPsicologiaHc, acc: AccionesHc) {
    val c = Sania.colors
    Aviso("🔒 Confidencial. El informe y los puntajes solo se incluyen si los pides; las fotos de dibujos, hojas y protocolos nunca.",
        c.purple, c.purpleBg)
    if (d.hayInforme || d.hayEvaluaciones) {
        val informe = d.incluyeInforme
        val tests = d.incluyeTests
        if (d.hayInforme) CasillaHc("Incluir el informe psicológico vigente", informe) { acc.pedirPsico(it, tests) }
        if (d.hayEvaluaciones) CasillaHc("Incluir los puntajes de los tests (material protegido)", tests) { acc.pedirPsico(informe, it) }
    }
    if (d.evaluaciones.isEmpty()) {
        if (!d.hayInforme && !d.hayEvaluaciones) TextoSuave("Aún no hay informe ni tests registrados.")
        return
    }
    d.evaluaciones.forEach { EvaluacionPsicoVista(it) }
}

@Composable
private fun CasillaHc(texto: String, marcada: Boolean, onCambio: (Boolean) -> Unit) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().clickable { onCambio(!marcada) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).background(if (marcada) c.purple else c.superficie)
                .border(1.5.dp, if (marcada) c.purple else c.borde, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) { if (marcada) Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(8.dp))
        Text(texto, color = c.texto, fontSize = 12.sp)
    }
}

@Composable
private fun EvaluacionPsicoVista(e: EvaluacionPsicoHc) {
    val c = Sania.colors
    Separador()
    Text(e.titulo, color = c.purple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    e.informe?.let { inf ->
        Subtitulo("Informe psicológico · versión ${inf.version}" + (inf.emitido?.let { " · emitido ${fechaHc(it)}" } ?: ""))
        inf.encabezado?.takeIf { it.isNotBlank() }?.let { Text(it, color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        inf.reemplazo?.takeIf { it.isNotBlank() }?.let { TextoSuave(it) }
        inf.filiacion.forEach { FilaPar(it) }
        inf.secciones.forEach { s ->
            Text("${s.numero}. ${s.titulo}", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            Text(s.texto, color = c.texto, fontSize = 12.sp)
        }
        if (inf.psicologo != null || inf.colegiatura != null) {
            Spacer(Modifier.height(6.dp))
            inf.psicologo?.let { Text(it, color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            inf.colegiatura?.let { TextoSuave(it) }
        }
        inf.lugarFecha?.let { TextoSuave(it) }
        inf.pie?.let { TextoSuave(it) }
    }
    e.tests.forEach { t ->
        Subtitulo("${t.nombre} · ${fechaHc(t.fecha)}")
        listOfNotNull(t.edadTexto, t.validez, t.global).forEach { TextoSuave(it) }
        t.puntajes.forEach { p ->
            Text(
                "${p.escala}: " + listOf("PD" to p.directo, "PT" to p.transformado, "Pc" to p.percentil)
                    .filter { it.second.isNotBlank() }.joinToString(" · ") { "${it.first} ${it.second}" } +
                    (p.categoria.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""),
                color = c.texto, fontSize = 12.sp,
            )
        }
    }
}

// ── Medicina y nutrición ────────────────────────────────────────────────────

@Composable
private fun SignosVitalesVista(d: DatosSignosVitalesHc, graficos: Boolean) {
    val c = Sania.colors
    d.tomas.forEach { t ->
        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text(fechaHora(t.fecha, t.hora), color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(t.texto, color = c.texto, fontSize = 12.sp)
            t.tomadoPor?.let { TextoSuave("Tomó: $it") }
        }
    }
    val pesos = d.tomas.mapNotNull { t -> t.peso?.let { PuntoSerieHc(t.fecha, it) } }
    if (graficos && pesos.size >= 2) { Spacer(Modifier.height(6.dp)); GraficoLineaHc("Peso por consulta", "kg", pesos) }
}

@Composable
private fun AntropometriaVista(d: DatosAntropometriaHc, graficos: Boolean) {
    val c = Sania.colors
    val serie = remember(d) { seriePesoHc(d) }
    if (graficos && serie.size >= 2) { GraficoLineaHc("Evolución del peso", "kg", serie); Spacer(Modifier.height(8.dp)) }
    d.cambioPeso?.let { cp ->
        val signo = if (cp.diferencia > 0) "+" else ""
        Text("De ${numeroHc(cp.desde)} kg a ${numeroHc(cp.hasta)} kg ($signo${numeroHc(cp.diferencia)} kg)",
            color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
    }
    d.mediciones.asReversed().forEach { m ->
        Column(
            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                .background(c.fondo).padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            val origen = when (m.origen) { "triaje" -> "triaje"; "ficha" -> "ficha"; "nutricion" -> "control"; else -> m.origen }
            Text(fechaHc(m.fecha) + (origen.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            val partes = listOfNotNull(
                m.peso?.let { "Peso ${numeroHc(it)} kg" },
                m.talla?.let { "Talla ${numeroHc(it)} cm" },
                m.imc?.let { "IMC ${numeroHc(it)}" + (m.clasificacionImc?.let { k -> " ($k)" } ?: "") },
                m.perimetroAbdominal?.let { "Abdomen ${numeroHc(it)} cm" },
                m.cintura?.let { "Cintura ${numeroHc(it)} cm" },
                m.cadera?.let { "Cadera ${numeroHc(it)} cm" },
                m.grasaPct?.let { "Grasa ${numeroHc(it)} %" },
                m.masaMuscular?.let { "Masa muscular ${numeroHc(it)} kg" },
            )
            if (partes.isNotEmpty()) Text(partes.joinToString(" · "), color = c.texto, fontSize = 12.sp)
            m.notas?.takeIf { it.isNotBlank() }?.let { TextoSuave(it) }
        }
    }
}

// ── Estética / capilar / podología ──────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FotosVista(d: DatosFotosHc, graficos: Boolean) {
    val c = Sania.colors
    // Visor dentro de la app (la URL firmada vence en 1 h: no se guarda).
    var abierta by remember(d) { mutableStateOf<FotoHc?>(null) }
    abierta?.let { f -> VisorImagen(url = f.url, titulo = f.nombre.ifBlank { null }) { abierta = null } }
    val abrir: (FotoHc) -> Unit = { abierta = it }
    if (graficos) d.pares.forEach { par ->
        Subtitulo(par.titulo)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FotoHcVista(par.antes, "Antes", abrir, Modifier.weight(1f))
            FotoHcVista(par.despues, "Después", abrir, Modifier.weight(1f))
        }
    }
    val enPares = if (graficos) d.pares.flatMap { listOf(it.antes.id, it.despues.id) }.toSet() else emptySet()
    val sueltas = d.fotos.filter { it.id !in enPares }
    if (sueltas.isNotEmpty()) {
        if (graficos && d.pares.isNotEmpty()) Subtitulo("Otras fotos")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sueltas.forEach { f -> FotoHcVista(f, null, abrir, Modifier.width(100.dp)) }
        }
    }
    TextoSuave("Toca una foto para verla en grande.")
}

@Composable
private fun FotoHcVista(f: FotoHc, etiqueta: String?, abrir: (FotoHc) -> Unit, modifier: Modifier) {
    val c = Sania.colors
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.fondo)
                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                .clickable(enabled = f.url != null) { abrir(f) },
            contentAlignment = Alignment.Center,
        ) {
            if (f.url != null) AsyncImage(model = f.url, contentDescription = etiqueta ?: f.nombre,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f))
            else Text("📷", fontSize = 22.sp)
        }
        Text(
            listOfNotNull(etiqueta ?: f.momento?.let { if (it == "Despues") "Después" else it }, fechaHc(f.fecha)).joinToString(" · "),
            color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp),
        )
    }
}
