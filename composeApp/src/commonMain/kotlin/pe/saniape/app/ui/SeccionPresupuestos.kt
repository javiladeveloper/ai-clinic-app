package pe.saniape.app.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.MiPresupuesto
import pe.saniape.app.data.staff.etiquetaDescuento
import pe.saniape.app.data.staff.fechaHoraAceptacion
import pe.saniape.app.data.staff.fechaLarga
import pe.saniape.app.data.staff.formatearDinero
import pe.saniape.app.data.staff.subtotalItem
import pe.saniape.app.data.staff.textoCobroItem
import pe.saniape.app.ui.theme.Sania

/**
 * 🧾 Mis presupuestos del portal del paciente (contrato §16): los que su clínica
 * le ENVIÓ, primero los que esperan respuesta. Cada uno con el detalle, el
 * descuento, el total y el ahorro, la vigencia, "📄 Ver / PDF" y, si sigue
 * vigente, "✓ Aceptar presupuesto" (con confirmación afuera). Tras aceptar, la
 * clínica crea los tratamientos y le escribe para agendar (la app no crea nada).
 *
 * Ítems del LazyColumn de la pantalla Salud (nada de listas perezosas anidadas).
 */
internal fun LazyListScope.seccionPresupuestos(
    items: List<MiPresupuesto>,
    espacioArriba: Boolean,
    respondiendo: String?,
    onVer: (MiPresupuesto) -> Unit,
    onAceptar: (MiPresupuesto) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "presu-etiqueta") {
        Column {
            if (espacioArriba) Spacer(Modifier.height(Sania.dim.sm))
            Text("🧾 MIS PRESUPUESTOS", color = Sania.colors.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
        }
    }
    items(items, key = { "presu-" + it.id }) { p -> TarjetaPresupuestoPortal(p, respondiendo == p.id, { onVer(p) }, { onAceptar(p) }) }
}

@Composable
private fun TarjetaPresupuestoPortal(p: MiPresupuesto, respondiendo: Boolean, onVer: () -> Unit, onAceptar: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.md.dp)
    fun din(n: Double) = formatearDinero(n, p.moneda)
    val (fg, bg) = when (p.estado) {
        "Aceptado" -> c.ok to c.okBg
        "Rechazado" -> c.error to c.errorBg
        "Vencido" -> c.pend to c.pendBg
        else -> c.info to c.infoBg
    }
    Column(Modifier.fillMaxWidth().clip(forma).background(c.superficie).border(1.dp, if (p.puedeResponder) c.navy else c.borde, forma).padding(Sania.dim.tarjeta)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.titulo, color = c.texto, fontSize = Sania.txt.seccion, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(if (p.estado == "Enviado") "Por responder" else p.estado, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(listOfNotNull(p.clinica?.nombre?.let { "🏥 $it" }, p.profesional).joinToString(" · "), color = c.textoSuave, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        p.items.forEach { it ->
            val cobro = textoCobroItem(it, p.moneda)
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(it.nombre + (it.detalle?.let { d -> " ($d)" } ?: ""), color = c.texto, fontSize = 13.sp)
                    if (cobro.isNotEmpty()) Text(cobro, color = c.textoSuave, fontSize = 11.sp)
                }
                Spacer(Modifier.width(8.dp))
                Text(din(subtotalItem(it, p.moneda)), color = c.texto, fontSize = 13.sp)
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(c.borde))
        if (p.descuento > 0) {
            Row(Modifier.fillMaxWidth()) {
                Text("Subtotal", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(din(p.subtotal), color = c.texto, fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth()) {
                Text(etiquetaDescuento(p.descuentoTipo, p.descuentoValor), color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("−${din(p.descuento)}", color = c.ok, fontSize = 12.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Total", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(din(p.total), color = c.navy, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (p.ahorro > 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("por separado ", color = c.textoSuave, fontSize = 12.sp)
                Text(din(p.porSeparado), color = c.textoSuave, fontSize = 12.sp, textDecoration = TextDecoration.LineThrough)
                Text(" · ahorras ${din(p.ahorro)}", color = c.ok, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        p.notasPaciente?.let { Text(it, color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
        Text(
            when (p.estado) {
                "Aceptado" -> "✓ Lo aceptaste${p.aceptadoAt?.let { " el ${fechaHoraAceptacion(it, null)}" } ?: ""}. Tu clínica te escribirá para agendar."
                "Rechazado" -> "No lo aceptaste."
                "Vencido" -> "Venció el ${fechaLarga(p.vigenciaHasta)}. Pide a tu clínica uno actualizado."
                else -> "Válido hasta el ${fechaLarga(p.vigenciaHasta)}."
            },
            color = if (p.estado == "Aceptado") c.ok else c.textoSuave, fontSize = 12.sp,
            fontWeight = if (p.estado == "Aceptado") FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .border(1.5.dp, c.lav, RoundedCornerShape(Sania.shape.sm.dp)).clickable(role = Role.Button, onClick = onVer)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text("📄 Ver / PDF", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            if (p.puedeResponder) {
                Box(
                    Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .background(if (respondiendo) c.borde else c.ok)
                        .clickable(enabled = !respondiendo, role = Role.Button, onClick = onAceptar)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (respondiendo) "Enviando…" else "✓ Aceptar", color = c.sobreNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
