package pe.saniape.app.ui.clinica.finanzas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.FinanzasRepo
import pe.saniape.app.data.staff.GastoFijo
import pe.saniape.app.data.staff.SedeActiva
import pe.saniape.app.tutoriales.PantallaTutorial
import pe.saniape.app.tutoriales.tourAncla
import pe.saniape.app.ui.ManejarAtras
import pe.saniape.app.ui.clinica.ChipSede
import pe.saniape.app.ui.clinica.EstadoVacio
import pe.saniape.app.ui.theme.Sania

/** Categorías sugeridas que da el servidor (oficiales de la clínica + semillas). */
internal data class CategoriasFin(val egreso: List<String>, val ingreso: List<String>, val gastoFijo: List<String>) {
    companion object {
        // Mientras llega la respuesta (o sin red): las semillas de la web.
        val SEMILLAS = CategoriasFin(
            egreso = listOf("Insumos", "Equipos e instrumentos", "Compra única", "Alquiler", "Servicios (luz/agua/internet)", "Sueldos", "Publicidad", "Mantenimiento", "Gasto fijo", "Otro"),
            ingreso = listOf("Venta de productos", "Otro ingreso"),
            gastoFijo = listOf("Gasto fijo", "Insumos", "Equipos e instrumentos", "Alquiler", "Servicios (luz/agua/internet)", "Sueldos", "Publicidad", "Mantenimiento", "Otro"),
        )
    }
}

private enum class TabFin(val titulo: String) { Caja("📋 Caja"), Cierre("🧮 Cierre"), PorCobrar("💰 Por cobrar"), Gastos("🔁 Gastos fijos") }

/**
 * Más → 💸 Finanzas y caja, NATIVO (antes abría /finanzas en el navegador).
 * Pestañas: Caja (kardex), Cierre de caja, Por cobrar (con permiso de pagos) y
 * Gastos fijos. Permiso `finanzas` (como el menú de la web) y plan Premium
 * (si no, el mismo aviso de plan que la web; el servidor responde 402 igual).
 *
 * Quedan en la web por ahora: el cobro con tarjeta (enlace de Mercado Pago), los
 * reportes de gerencia y exportar a Excel.
 */
@Composable
fun PantallaFinanzas(ctx: ContextoStaff, onSalir: () -> Unit) = PantallaTutorial("Finanzas") {
    val c = Sania.colors
    ManejarAtras(activo = true, onAtras = onSalir)
    var tab by remember { mutableStateOf(TabFin.Caja) }
    val tabs = buildList {
        add(TabFin.Caja); add(TabFin.Cierre)
        if (ctx.puede("pagos")) add(TabFin.PorCobrar)
        add(TabFin.Gastos)
    }

    // Gastos fijos + categorías sugeridas: una sola lectura que usan dos pestañas.
    var gastos by remember { mutableStateOf<List<GastoFijo>?>(null) }
    var categorias by remember { mutableStateOf(CategoriasFin.SEMILLAS) }
    var falloGastos by remember { mutableStateOf<String?>(null) }
    var recargaGastos by remember { mutableIntStateOf(0) }
    val sede by SedeActiva.estado.collectAsState()
    val conPlan = ctx.can("finanzas")
    LaunchedEffect(sede.filtro, recargaGastos, conPlan) {
        if (!conPlan) return@LaunchedEffect
        falloGastos = null
        val (r, err) = FinanzasRepo.gastos()
        if (r == null) { falloGastos = err ?: "No se pudieron cargar los gastos fijos."; return@LaunchedEffect }
        gastos = r.gastos
        categorias = CategoriasFin(
            egreso = r.catEgreso.ifEmpty { CategoriasFin.SEMILLAS.egreso },
            ingreso = r.catIngreso.ifEmpty { CategoriasFin.SEMILLAS.ingreso },
            gastoFijo = r.catGastoFijo.ifEmpty { CategoriasFin.SEMILLAS.gastoFijo },
        )
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("← Más", color = c.sobreNavy, fontSize = Sania.txt.pequeno,
                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable(onClick = onSalir).padding(vertical = 2.dp))
                    Spacer(Modifier.height(2.dp))
                    Text("💸 Finanzas y caja", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
                    // Multisede: caja, cierre y gastos son de la sede activa (cada local su cajón).
                    ChipSede(Modifier.padding(top = 2.dp))
                }
            }
            if (!conPlan) {
                Box(Modifier.fillMaxSize().padding(Sania.dim.lg)) {
                    EstadoVacio("🔒", "El kardex financiero es una función Premium",
                        "Registra ingresos y egresos, controla la caja de tu clínica y mira la evolución de tus finanzas. Disponible desde el plan Premium.")
                }
                return@Column
            }
            // Pestañas (subrayado con drawBehind, sin medidas intrínsecas).
            Row(Modifier.fillMaxWidth().background(c.superficie).horizontalScroll(rememberScrollState())) {
                tabs.forEach { t ->
                    val activo = tab == t
                    val colorLinea = c.navy
                    Text(
                        t.titulo, color = if (activo) c.navy else c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.tourAncla("finanzas.tab_${t.name.lowercase()}")
                            .clickable { tab = t }
                            .drawBehind {
                                if (activo) drawLine(colorLinea, Offset(0f, size.height - 1.5.dp.toPx()), Offset(size.width, size.height - 1.5.dp.toPx()), strokeWidth = 3.dp.toPx())
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
            Box(Modifier.weight(1f)) {
                when (tab) {
                    TabFin.Caja -> TabCaja(ctx, categorias)
                    TabFin.Cierre -> TabCierre()
                    TabFin.PorCobrar -> TabPorCobrar()
                    TabFin.Gastos -> TabGastosFijos(gastos, categorias.gastoFijo, falloGastos) { recargaGastos++ }
                }
            }
        }
    }
}
