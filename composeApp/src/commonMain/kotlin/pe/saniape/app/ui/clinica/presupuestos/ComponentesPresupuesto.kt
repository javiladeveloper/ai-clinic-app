package pe.saniape.app.ui.clinica.presupuestos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.TEXTO_CANDADO_PRESUPUESTOS
import pe.saniape.app.data.staff.compararConLista
import pe.saniape.app.data.staff.leerPrecio
import pe.saniape.app.data.staff.simboloMoneda
import pe.saniape.app.data.staff.textoLista
import pe.saniape.app.data.staff.textoPrecioNegociado
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.theme.Sania

/** Colores del estado visible (BADGE_ESTADO de la web: gris, azul, ámbar, verde, rojo). */
@Composable
internal fun coloresEstadoPresupuesto(estado: String): Pair<Color, Color> {
    val c = Sania.colors
    return when (estado) {
        "Enviado" -> c.info to c.infoBg
        "Vencido" -> c.pend to c.pendBg
        "Aceptado" -> c.ok to c.okBg
        "Rechazado" -> c.error to c.errorBg
        else -> c.textoSuave to c.chipBg
    }
}

@Composable
internal fun PildoraPresu(texto: String, fg: Color, bg: Color) {
    Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(texto, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Botón chico de acción. `lleno` = navy; `ok` = verde; `peligro` = rojo. */
@Composable
internal fun BotonPresu(
    texto: String,
    lleno: Boolean = false,
    ok: Boolean = false,
    peligro: Boolean = false,
    habilitado: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    val (fondo, tinta, borde) = when {
        !habilitado -> Triple(c.borde, c.textoSuave, c.borde)
        ok -> Triple(c.ok, c.sobreNavy, c.ok)
        lleno -> Triple(c.navy, c.sobreNavy, c.navy)
        peligro -> Triple(c.errorBg, c.error, c.error.copy(alpha = 0.4f))
        else -> Triple(c.superficie, c.navy, c.lav)
    }
    Box(
        modifier.heightIn(min = 40.dp).clip(forma).background(fondo).border(1.5.dp, borde, forma)
            .clickable(enabled = habilitado, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = tinta, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
}

/** Chip de elección (activo = navy). */
@Composable
internal fun ChipPresu(texto: String, activo: Boolean, habilitado: Boolean = true, onClick: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.pill.dp)
    Box(
        Modifier.heightIn(min = 36.dp).clip(forma).background(if (activo) c.navy else c.superficie)
            .border(1.5.dp, if (activo) c.navy else c.lav, forma)
            .clickable(enabled = habilitado, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = if (activo) c.sobreNavy else if (habilitado) c.texto else c.textoSuave, fontSize = 12.sp,
            fontWeight = FontWeight.Bold, maxLines = 2)
    }
}

/** Tarjeta blanca con borde (lista de presupuestos, ítems del editor). */
@Composable
internal fun TarjetaPresu(fondo: Color? = null, contenido: @Composable ColumnScope.() -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.md.dp)
    Column(
        Modifier.fillMaxWidth().clip(forma).background(fondo ?: c.superficie).border(1.dp, c.borde, forma).padding(12.dp),
        content = contenido,
    )
}

/** Campo de texto del editor (una línea o varias). */
@Composable
internal fun CampoPresu(
    valor: String,
    onCambio: (String) -> Unit,
    etiqueta: String? = null,
    placeholder: String = "",
    multilinea: Boolean = false,
    max: Int? = null,
    teclado: KeyboardType = KeyboardType.Text,
    habilitado: Boolean = true,
    error: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val c = Sania.colors
    Column(modifier) {
        etiqueta?.let { EtqForm(it) }
        OutlinedTextField(
            value = valor,
            onValueChange = { v -> onCambio(if (max != null) v.take(max) else v) },
            placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder, color = c.textoSuave, fontSize = 13.sp) }) else null,
            singleLine = !multilinea,
            minLines = if (multilinea) 3 else 1,
            enabled = habilitado,
            isError = error,
            keyboardOptions = KeyboardOptions(keyboardType = teclado),
            colors = coloresCampoForm(),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Gemelo de CampoPrecioAcordado (precio siempre negociable): el campo, el botón
 * "↺ Precio de lista" / "↺ Precio del paquete" (vuelve al precio EXACTO del
 * catálogo) y debajo la diferencia: verde si es menos que la lista, neutro
 * "sobre lista" si es más (no es un descuento).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CampoPrecioPresu(
    etiqueta: String,
    valor: String,
    onCambio: (String) -> Unit,
    lista: Double?,
    moneda: String,
    habilitado: Boolean,
    textoBoton: String = "↺ Precio de lista",
    placeholder: String? = null,
) {
    val c = Sania.colors
    val lectura = leerPrecio(valor, moneda)
    val efectivo = if (lectura.ok) lectura.valor else null
    val distinto = lista != null && efectivo != null && compararConLista(efectivo, lista).distinto
    val mostrarBoton = lista != null && habilitado && (distinto || (lectura.ok && lectura.valor == null))
    val diferencia = lista?.let { textoPrecioNegociado(efectivo, it, moneda) }
    val descuento = lista != null && efectivo != null && efectivo < lista
    Column(Modifier.fillMaxWidth()) {
        EtqForm("$etiqueta (${simboloMoneda(moneda)})")
        FlowRow(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedTextField(
                value = valor,
                onValueChange = { onCambio(it.replace(',', '.')) },
                placeholder = { Text(placeholder ?: (lista?.let { textoLista(it, moneda) } ?: "0.00"), color = c.textoSuave, fontSize = 13.sp) },
                singleLine = true,
                enabled = habilitado,
                isError = !lectura.ok,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = coloresCampoForm(),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                modifier = Modifier.widthIn(min = 120.dp, max = 180.dp),
            )
            if (mostrarBoton) {
                Text(
                    textoBoton, color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .clickable(role = Role.Button) { onCambio(textoLista(lista!!, moneda)) }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                )
            }
        }
        when {
            !lectura.ok -> Text(lectura.error ?: "", color = c.error, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
            diferencia != null -> Text(diferencia, color = if (descuento) c.ok else c.textoSuave, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

/** Candado de plan (PlanLock de la web): se VE con 🔒 y "Mejorar plan". */
@Composable
internal fun CandadoPresupuestos(onMejorarPlan: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.md.dp)
    Column(
        Modifier.fillMaxWidth().clip(forma).background(c.chipBg).border(1.dp, c.borde, forma).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🔒", fontSize = 26.sp)
        Spacer(Modifier.height(4.dp))
        Text("Presupuestos y paquetes", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(TEXTO_CANDADO_PRESUPUESTOS, color = c.textoSuave, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        BotonPresu("Mejorar plan →", lleno = true, onClick = onMejorarPlan)
    }
}

/** Fila "etiqueta ……… valor" de los totales. */
@Composable
internal fun FilaTotal(etiqueta: String, valor: String, color: Color? = null, negrita: Boolean = false) {
    val c = Sania.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(etiqueta, color = if (negrita) c.texto else c.textoSuave, fontSize = 13.sp,
            fontWeight = if (negrita) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text(valor, color = color ?: c.texto, fontSize = 13.sp, fontWeight = if (negrita) FontWeight.Bold else FontWeight.Normal)
    }
}
