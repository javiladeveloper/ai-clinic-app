package pe.saniape.app.ui.clinica.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.staff.AddonCatalogo
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.DatosPlan
import pe.saniape.app.data.staff.PlanCatalogo
import pe.saniape.app.data.staff.PlanRepo
import pe.saniape.app.data.staff.formatearBytes
import pe.saniape.app.data.staff.nivelContratado
import pe.saniape.app.data.staff.textoEspacioMB
import pe.saniape.app.ui.CargandoLista
import pe.saniape.app.ui.clinica.BarraProgreso
import pe.saniape.app.ui.fechaDMA
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania

private fun soles(n: Double): String = "S/ " + (if (n % 1.0 == 0.0) n.toLong().toString().reversed().chunked(3).joinToString(",").reversed() else n.toString())

/**
 * 💎 Mi plan (Más → Administración). Gemelo de /suscripcion en la web, SOLO LECTURA:
 * plan actual, estado (prueba / activo / vencido), vencimiento, límites y uso
 * (profesionales, sedes, espacio de documentos), lo que incluye cada plan y los
 * servicios adicionales. Pagar o cambiar de plan NO se hace en la app (política de
 * Google Play sobre cobros): el botón abre la misma página de la web. Con permiso
 * "ajustes" (lo decide el padre; el servidor valida igual).
 */
@Composable
fun PantallaMiPlan(ctx: ContextoStaff, onSalir: () -> Unit) {
    val c = Sania.colors
    val acciones = recordarAcciones()
    var datos by remember { mutableStateOf<DatosPlan?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var recarga by remember { mutableIntStateOf(0) }
    var planAbierto by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ctx.clinicaId, recarga) {
        val (d, e) = PlanRepo.cargar()
        if (d != null) { datos = d; error = null } else if (datos == null) error = e
    }
    val irWeb = { acciones.abrirUrl("${Supabase.SITE_URL}/suscripcion") }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "← Más", color = c.sobreNavy, fontSize = Sania.txt.pequeno,
                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable { onSalir() }.padding(vertical = 2.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text("Mi plan", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
                    Text("Suscripción y funciones de tu clínica", color = c.sobreNavy.copy(alpha = 0.75f), fontSize = Sania.txt.mini)
                }
            }

            val d = datos
            when {
                d == null && error != null -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error.orEmpty(), color = c.textoSuave, fontSize = 13.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(Sania.dim.md))
                        Box(
                            Modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.navy)
                                .clickable { error = null; recarga++ }.padding(horizontal = 20.dp, vertical = 10.dp),
                        ) { Text("Reintentar", color = c.sobreNavy, fontWeight = FontWeight.Bold) }
                    }
                }
                d == null -> CargandoLista(filas = 4, conAvatar = false)
                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = Sania.dim.lg),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { Spacer(Modifier.height(2.dp)); Banner(d) }
                    item { TarjetaUso(d) }
                    d.incluyeActual?.let { filas ->
                        item {
                            Tarjeta("Lo que incluye tu plan") {
                                filas.forEach { f -> FilaCheck(f.ok, f.texto) }
                            }
                        }
                    }
                    item { Titulo("Planes") }
                    items(d.planes, key = { it.id }) { p ->
                        TarjetaPlan(
                            p, esActual = d.plan == p.id && !d.vencido, abierto = planAbierto == p.id, sedes = d.uso.sedesActivas,
                            onAlternar = { planAbierto = if (planAbierto == p.id) null else p.id }, onContratar = irWeb,
                        )
                    }
                    item { Titulo("Servicios adicionales") }
                    items(d.addons, key = { it.clave }) { a ->
                        TarjetaAddon(a, d, onVerWeb = irWeb)
                    }
                    item {
                        Text(
                            "💳 Para contratar o cambiar de plan se abre la web (inicia sesión con tu misma cuenta). " +
                                "La suscripción se renueva automáticamente y puedes cancelarla escribiéndonos.",
                            color = c.textoSuave, fontSize = 12.sp, lineHeight = 17.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = Sania.dim.md),
                        )
                        Spacer(Modifier.height(Sania.dim.xl))
                    }
                }
            }
        }
    }
}

@Composable
private fun Titulo(t: String) {
    Text(t.uppercase(), color = Sania.colors.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun Tarjeta(titulo: String?, contenido: @Composable () -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
    ) {
        if (titulo != null) Text(titulo, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        contenido()
    }
}

@Composable
private fun FilaCheck(ok: Boolean, texto: String) {
    val c = Sania.colors
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(if (ok) "✓" else "✕", color = if (ok) c.ok else c.textoSuave.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
        Text(texto, color = if (ok) c.texto else c.textoSuave.copy(alpha = 0.6f), fontSize = 13.sp, lineHeight = 17.sp)
    }
}

/** Plan actual + estado + vencimiento (el banner de la web). */
@Composable
private fun Banner(d: DatosPlan) {
    val c = Sania.colors
    val fondo = if (d.vencido) Color(0xFFB91C1C) else c.navy
    val icono = when { d.vencido -> "⚠️"; d.plan == "Plus" -> "🚀"; d.plan == "Premium" -> "💎"; d.plan == "Trial" -> "🎁"; else -> "🗓️" }
    val dias = d.diasRestantes
    val estado = when {
        d.vencido -> "● Vencido"
        d.esTrial -> "🎁 Prueba gratis" + (dias?.let { " · $it día${if (it != 1) "s" else ""} restante${if (it != 1) "s" else ""}" } ?: "")
        else -> "✓ Activo" + (if (dias != null && dias <= 7) " · renueva en $dias día${if (dias != 1) "s" else ""}" else "")
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.lg.dp)).background(fondo).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icono, fontSize = 32.sp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("TU PLAN ACTUAL", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(d.nombrePlan, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    estado, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(Sania.shape.pill.dp)).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        if (!d.planVence.isNullOrBlank()) {
            Text(
                (if (d.vencido) "Venció el " else if (d.esTrial) "La prueba termina el " else "Vigente hasta el ") + fechaDMA(d.planVence),
                color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp),
            )
        }
        if (!d.vencido && dias != null && dias in 0..30) {
            Spacer(Modifier.height(8.dp))
            Text("$dias de 30 días ${if (d.esTrial) "de prueba" else "del ciclo"}", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            BarraProgreso(fraccion = (dias / 30f).coerceAtLeast(0.04f), color = Color.White.copy(alpha = 0.85f))
        }
        if (d.vencido) {
            Text(
                "Tu período terminó. Tu clínica sigue funcionando con las funciones del plan Básico. Elige un plan para recuperar todas las funciones.",
                color = Color.White, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** Límites y uso: profesionales, sedes y espacio de documentos. */
@Composable
private fun TarjetaUso(d: DatosPlan) {
    val c = Sania.colors
    val u = d.uso
    Tarjeta("Límites y uso") {
        // Profesionales
        val max = u.maxProfesionales
        Text(
            "Profesionales activos: ${u.profesionalesActivos}" + (if (max != null) " de $max" else " (ilimitados)"),
            color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        )
        if (max != null) {
            Spacer(Modifier.height(5.dp))
            val llena = u.profesionalesActivos >= max
            BarraProgreso(fraccion = u.profesionalesActivos.toFloat() / max, color = if (llena) c.pend else c.navy)
            Text(
                if (u.maxProfesionalesPorSede != null && u.sedesActivas > 1) "${u.maxProfesionalesPorSede} por cada una de tus ${u.sedesActivas} sedes"
                else "Tu plan permite hasta $max profesionales activos",
                color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        // Sedes
        Text(
            "Sedes: ${u.sedesActivas} activa${if (u.sedesActivas == 1) "" else "s"}" + (if (u.sedesPagadas > 1) " · ${u.sedesPagadas} incluidas en tu suscripción" else ""),
            color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        // Documentos
        val usado = u.documentosUsadoBytes
        val tope = u.documentosMaxMB
        Text(
            "Documentos de las fichas: " + (usado?.let { formatearBytes(it) } ?: "—") +
                (if (tope != null) " de ${textoEspacioMB(tope)}" else " (sin límite de espacio)"),
            color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        )
        if (tope != null && usado != null) {
            Spacer(Modifier.height(5.dp))
            val frac = (usado.toFloat() / (tope * 1024f * 1024f))
            BarraProgreso(fraccion = frac, color = if (frac >= 0.9f) c.error else if (frac >= 0.75f) c.pend else c.navy)
        }
    }
}

@Composable
private fun TarjetaPlan(
    p: PlanCatalogo, esActual: Boolean, abierto: Boolean, sedes: Int,
    onAlternar: () -> Unit, onContratar: () -> Unit,
) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(if (esActual) 2.dp else 1.dp, if (esActual) c.ok else c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth().clickable { onAlternar() }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.nombre, color = c.texto, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    if (esActual) {
                        Text(
                            "✓ Tu plan", color = c.ok, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.ok.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    } else if (p.id == "Plus") {
                        Text(
                            "Recomendado", color = c.navy, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.chipBg).padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                p.precioMensual?.let { m ->
                    Text(
                        "${soles(m)} / mes  ·  ${soles(p.precioAnual)} / año (2 meses gratis)",
                        color = c.teal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp),
                    )
                    if (p.precioPorSede != null) {
                        Text("+ ${soles(p.precioPorSede)} por cada sede adicional", color = c.textoSuave, fontSize = 11.sp)
                    }
                }
            }
            Text(if (abierto) "▾" else "›", color = c.textoSuave, fontSize = 18.sp)
        }
        Text(p.descripcion, color = c.textoSuave, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
        if (abierto) {
            Spacer(Modifier.height(8.dp))
            p.incluye.forEach { FilaCheck(it.ok, it.texto) }
        }
        if (!esActual) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(if (p.id == "Plus") c.navy else c.chipBg)
                    .clickable { onContratar() }.padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Contratar ${p.nombre} en la web ↗", color = if (p.id == "Plus") c.sobreNavy else c.navy, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun TarjetaAddon(a: AddonCatalogo, d: DatosPlan, onVerWeb: () -> Unit) {
    val c = Sania.colors
    val activo = d.addonsActivos.firstOrNull { it.addon == a.clave }
    val requiereOtro = a.requierePlan != null && d.plan != a.requierePlan
    Tarjeta(null) {
        Row(verticalAlignment = Alignment.Top) {
            Text(a.nombre, color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (a.enConstruccion) {
                Text(
                    "En construcción", color = c.pend, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.pend.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Text(a.descripcion, color = c.textoSuave, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(8.dp))
        if (a.niveles.isNotEmpty()) {
            val propio = activo?.let { nivelContratado(a, it.precioMensual) }
            a.niveles.forEach { n ->
                val esMio = propio?.id == n.id
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (esMio) c.chipBg else c.fondo)
                        .border(if (esMio) 2.dp else 1.dp, if (esMio) c.navy else c.borde, RoundedCornerShape(10.dp)).padding(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(n.nombre + if (esMio) "  ·  Tu nivel" else "", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(n.cupo ?: n.detalle.firstOrNull().orEmpty(), color = c.textoSuave, fontSize = 11.sp)
                    }
                    Text("S/${n.precio.toLong()}/mes", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Text(
                "S/${a.precio.toLong()}/mes" + (a.pagoUnico?.let { " + S/${it.toLong()} única vez" } ?: ""),
                color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(6.dp))
        a.detalle.take(4).forEach { FilaCheck(true, it) }
        Spacer(Modifier.height(6.dp))
        when {
            requiereOtro -> Text("Disponible con el plan ${a.requierePlan}", color = c.pend, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            activo != null -> Text("✓ Activo en tu clínica", color = c.ok, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            else -> Text(
                if (a.enConstruccion) "Avísame cuando esté (en la web) ↗" else "Contratar en la web ↗",
                color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onVerWeb() }.padding(vertical = 6.dp),
            )
        }
    }
}
