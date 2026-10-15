package pe.saniape.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.CUOTA_ATRASADA
import pe.saniape.app.data.CuotaPortal
import pe.saniape.app.data.DocMedicoPortal
import pe.saniape.app.data.DocumentosClinicos
import pe.saniape.app.data.PRESUPUESTO_ACEPTADO
import pe.saniape.app.data.PRESUPUESTO_REEMPLAZADO
import pe.saniape.app.data.PlanCuotasPortal
import pe.saniape.app.data.PresupuestoPortal
import pe.saniape.app.data.TonoDoc
import pe.saniape.app.data.cuotasVisibles
import pe.saniape.app.data.dineroDoc
import pe.saniape.app.data.estadoPresupuestoUi
import pe.saniape.app.data.lineaOrigenDoc
import pe.saniape.app.data.resumenItemsPresupuesto
import pe.saniape.app.data.staff.DOC_DESCANSO
import pe.saniape.app.data.tonoCuota
import pe.saniape.app.ui.theme.ColorEstado
import pe.saniape.app.ui.theme.Sania

/**
 * 🩺 Documentos médicos del portal del paciente (gemelo de
 * MisDocumentosClinicos.tsx de la web): informes, descansos y órdenes de
 * exámenes ("Ver / PDF" abre la misma hoja que imprime la clínica en el visor
 * con impresión), presupuestos (solo el vigente ofrece "Revisar y aceptar") y el
 * cronograma de cuotas de cada tratamiento, en SU moneda.
 *
 * Se agrega como ítems del LazyColumn de la pantalla Salud (nada de listas
 * perezosas anidadas). Sin nada no agrega nada: el portal de un paciente de
 * fisio no cambia.
 */
internal fun LazyListScope.seccionDocumentosMedicos(
    datos: DocumentosClinicos,
    espacioArriba: Boolean,
    /** Id del documento cuya hoja se está pidiendo (un doble toque no abre dos visores). */
    abriendo: String?,
    onVer: (DocMedicoPortal) -> Unit,
    onAbrirPresupuesto: (PresupuestoPortal) -> Unit,
) {
    if (!datos.tieneDatos) return
    item(key = "docmed-etiqueta") {
        Column {
            if (espacioArriba) Spacer(Modifier.height(Sania.dim.sm))
            Text("🩺 DOCUMENTOS MÉDICOS", color = Sania.colors.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
        }
    }
    grupoDocs("informes", "📄", "Informes médicos", datos.informes, abriendo, onVer)
    grupoDocs("descansos", "🛌", "Descansos médicos", datos.descansos, abriendo, onVer)
    grupoDocs("ordenes", "🧪", "Órdenes de exámenes", datos.ordenes, abriendo, onVer)
    if (datos.presupuestos.isNotEmpty()) {
        item(key = "docmed-g-presupuestos") { TituloGrupoDoc("🦷", "Presupuestos", datos.presupuestos.size) }
        items(datos.presupuestos, key = { "docmed-presu-" + it.id }) { p ->
            TarjetaPresupuestoPortal(p, onAbrir = { onAbrirPresupuesto(p) })
        }
    }
    if (datos.cuotas.isNotEmpty()) {
        item(key = "docmed-g-cuotas") { TituloGrupoDoc("📅", "Mis cuotas", datos.cuotas.size) }
        items(datos.cuotas, key = { "docmed-cuotas-" + it.id }) { plan -> TarjetaCuotasPortal(plan) }
    }
}

private fun LazyListScope.grupoDocs(
    clave: String,
    icono: String,
    titulo: String,
    docs: List<DocMedicoPortal>,
    abriendo: String?,
    onVer: (DocMedicoPortal) -> Unit,
) {
    if (docs.isEmpty()) return
    item(key = "docmed-g-$clave") { TituloGrupoDoc(icono, titulo, docs.size) }
    items(docs, key = { "docmed-$clave-" + it.id }) { d ->
        TarjetaDocMedicoPortal(d, abriendo = abriendo == d.id, onVer = { onVer(d) })
    }
}

@Composable
private fun TituloGrupoDoc(icono: String, titulo: String, n: Int) {
    val c = Sania.colors
    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$icono $titulo", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        Text("($n)", color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Modifier.tarjetaDoc(): Modifier {
    val c = Sania.colors
    return this.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
        .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
}

@Composable
internal fun colorTono(t: TonoDoc): ColorEstado {
    val c = Sania.colors
    return when (t) {
        TonoDoc.OK -> ColorEstado(c.ok, c.okBg)
        TonoDoc.ERROR -> ColorEstado(c.error, c.errorBg)
        TonoDoc.PEND -> ColorEstado(c.pend, c.pendBg)
        TonoDoc.NEUTRO -> ColorEstado(c.textoSuave, c.chipBg)
    }
}

@Composable
private fun PildoraDoc(texto: String, tono: TonoDoc) {
    val col = colorTono(tono)
    Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(col.bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(texto, color = col.fg, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun BotonDoc(texto: String, habilitado: Boolean = true, onClick: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    Box(
        Modifier.heightIn(min = 40.dp).clip(forma).border(1.5.dp, c.lav, forma).background(c.superficie)
            .clickable(enabled = habilitado, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

/** Informe, descanso u orden: título, fecha y número, quién y dónde; "Ver / PDF". */
@Composable
private fun TarjetaDocMedicoPortal(d: DocMedicoPortal, abriendo: Boolean, onVer: () -> Unit) {
    val c = Sania.colors
    Column(Modifier.tarjetaDoc().padding(Sania.dim.tarjeta)) {
        Text(d.titulo, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(
            listOf(fechaDMA(d.fecha), d.numeroTexto).filter { it.isNotBlank() }.joinToString(" · "),
            color = c.textoSuave, fontSize = 12.sp,
        )
        lineaOrigenDoc(d.profesional, d.clinica).takeIf { it.isNotBlank() }?.let {
            Text(it, color = c.textoSuave, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        d.detalle?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = c.texto, fontSize = 13.sp)
        }
        d.indicaciones?.let {
            Spacer(Modifier.height(2.dp))
            Text("Indicaciones: $it", color = c.textoSuave, fontSize = 12.sp)
        }
        // El descanso es un certificado general (sin diagnóstico por defecto): ahí no se dice nada.
        if (d.diagnosticoReservado && d.tipo != DOC_DESCANSO) {
            Spacer(Modifier.height(2.dp))
            Text("🔒 El diagnóstico no se imprime en este documento.", color = c.textoSuave, fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
        BotonDoc(if (abriendo) "Abriendo…" else "📄 Ver / PDF", habilitado = !abriendo, onClick = onVer)
    }
}

/** Presupuesto dental: estado, total en SU moneda, ítems; aceptar solo el vigente. */
@Composable
private fun TarjetaPresupuestoPortal(p: PresupuestoPortal, onAbrir: () -> Unit) {
    val c = Sania.colors
    val (estadoTxt, tono) = estadoPresupuestoUi(p.estado)
    val reemplazado = p.estado == PRESUPUESTO_REEMPLAZADO
    val tinta = if (reemplazado) c.textoSuave else c.texto
    Column(Modifier.tarjetaDoc().padding(Sania.dim.tarjeta)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.titulo, color = tinta, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                    PildoraDoc(estadoTxt, tono)
                }
                Text(
                    if (p.estado == PRESUPUESTO_ACEPTADO && p.aceptadoAt != null) "Aceptado el ${fechaDMA(p.aceptadoAt)}"
                    else "Enviado el ${fechaDMA(p.fecha)}",
                    color = c.textoSuave, fontSize = 12.sp,
                )
                lineaOrigenDoc(null, p.clinica).takeIf { it.isNotBlank() }?.let {
                    Text(it, color = c.textoSuave, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("TOTAL", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(dineroDoc(p.total, p.moneda), color = tinta, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        resumenItemsPresupuesto(p).takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = tinta, fontSize = 13.sp)
        }
        when {
            p.enlace != null -> {
                Spacer(Modifier.height(10.dp))
                BotonDoc(if (p.puedeAceptar) "👀 Revisar y aceptar" else "📄 Ver presupuesto", onClick = onAbrir)
            }
            reemplazado -> {
                Spacer(Modifier.height(6.dp))
                Text("Tu clínica te envió un presupuesto más reciente: revisa ese.", color = c.textoSuave, fontSize = 12.sp)
            }
        }
    }
}

/** Cronograma de cuotas de un tratamiento: "te falta pagar", estado y las cuotas pendientes. */
@Composable
private fun TarjetaCuotasPortal(plan: PlanCuotasPortal) {
    val c = Sania.colors
    var completo by rememberSaveable(plan.id) { mutableStateOf(false) }
    val r = plan.resumen
    Column(Modifier.tarjetaDoc().padding(Sania.dim.tarjeta)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(plan.titulo, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                lineaOrigenDoc(plan.profesional, plan.clinica).takeIf { it.isNotBlank() }?.let {
                    Text(it, color = c.textoSuave, fontSize = 12.sp)
                }
            }
            if (r.saldo > 0.005) {
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text("TE FALTA PAGAR", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(dineroDoc(r.saldo, plan.moneda), color = if (r.atrasadas > 0) c.error else c.pend,
                        fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Resumen en una línea: "1 de 4 pagadas · ⚠ 1 atrasada · S/ 50.00" o "✓ Al día".
        val hayInicial = plan.cuotas.any { it.numero == 0 }
        Text(
            "${r.pagadas} de ${plan.cuotas.size} pagadas" +
                if (hayInicial) " (inicial + ${plan.cuotas.size - 1} cuotas)" else "",
            color = c.textoSuave, fontSize = 12.sp,
        )
        val pendientes = plan.cuotas.any { it.estado != pe.saniape.app.data.CUOTA_PAGADA }
        Text(
            when {
                r.atrasadas > 0 -> "⚠ ${r.atrasadas} atrasada${if (r.atrasadas == 1) "" else "s"} · ${dineroDoc(r.montoAtrasado, plan.moneda)}"
                pendientes -> "✓ Al día"
                else -> "✓ Plan completo"
            },
            color = if (r.atrasadas > 0) c.error else c.ok, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        )
        val visibles = cuotasVisibles(plan, completo)
        if (visibles.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)),
            ) {
                visibles.forEachIndexed { i, q ->
                    if (i > 0) HorizontalDivider(color = c.borde)
                    FilaCuotaPortal(q, plan.moneda)
                }
            }
        }
        if (plan.cuotas.size > visibles.size || completo) {
            Text(
                if (completo) "Ver solo lo pendiente" else "Ver cronograma completo (${plan.cuotas.size})",
                color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp).clickable { completo = !completo }.padding(vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("Tus pagos se aplican a las cuotas en orden. ¿Ves algo que no cuadra? Consúltalo en tu clínica.",
            color = c.textoSuave, fontSize = 11.sp)
    }
}

@Composable
private fun FilaCuotaPortal(q: CuotaPortal, moneda: String) {
    val c = Sania.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(q.etiqueta, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("Vence ${fechaDMA(q.vence)}", color = c.textoSuave, fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(dineroDoc(q.monto, moneda), color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            // Un pago parcial: lo que falta de ESA cuota.
            if (q.pagado > 0.005 && q.saldo > 0.005) {
                Text("Falta ${dineroDoc(q.saldo, moneda)}", color = if (q.estado == CUOTA_ATRASADA) c.error else c.textoSuave, fontSize = 11.sp)
            }
            Spacer(Modifier.height(2.dp))
            PildoraDoc(q.estadoTexto, tonoCuota(q.estado))
        }
    }
}
