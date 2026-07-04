package pe.saniape.app.ui.reservar

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import pe.saniape.app.data.ClinicaDetalle
import pe.saniape.app.data.ClinicaDir
import pe.saniape.app.data.ClinicaRepo
import pe.saniape.app.data.ProfDetalle
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania

/**
 * Mini-landing COMPLETA de la clínica (igual que /c/[slug] en la web):
 * avatar, calificación, especialidades, equipo, horario, cómo llegar (abre Maps
 * nativo), redes sociales y CTA de reserva.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaLandingClinica(
    clinica: ClinicaDir,
    onReservar: () -> Unit,
    onAtras: () -> Unit,
) {
    val c = Sania.colors
    val acciones = recordarAcciones()
    var cargando by remember { mutableStateOf(true) }
    var d by remember { mutableStateOf<ClinicaDetalle?>(null) }

    LaunchedEffect(clinica.slug) {
        try { d = ClinicaRepo.detalle(clinica.slug) } catch (_: Exception) {}
        cargando = false
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Barra superior
            Row(
                Modifier.fillMaxWidth().background(c.navyDark)
                    .padding(horizontal = Sania.dim.xl, vertical = Sania.dim.md),
            ) {
                Text("← Volver", color = c.sobreNavy, fontSize = Sania.txt.pequeno,
                    fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onAtras() })
            }

            if (cargando) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = c.navy) }
                return@Column
            }
            val det = d
            if (det == null) {
                Box(Modifier.fillMaxSize().padding(Sania.dim.xxl), Alignment.Center) {
                    Text("No se pudo cargar la clínica.", color = c.error, fontSize = Sania.txt.cuerpo)
                }
                return@Column
            }

            val marca = parseColor(det.colorPrincipal) ?: c.navy

            Column(
                Modifier.fillMaxSize().weight(1f).verticalScroll(rememberScrollState()),
            ) {
                // ── Encabezado: avatar + nombre + calificación + ubicación ──
                Column(
                    Modifier.fillMaxWidth().padding(Sania.dim.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Avatar(det.nombre.first().toString(), marca, 84.dp, 30.sp)
                    Spacer(Modifier.height(Sania.dim.md))
                    Text(det.nombre, color = c.texto, fontSize = Sania.txt.titulo,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    det.calificacion?.let { cal ->
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(estrellas(cal), color = c.pend, fontSize = Sania.txt.cuerpo)
                            Spacer(Modifier.size(6.dp))
                            Text("${formato1(cal)} (${det.totalResenas})", color = c.textoSuave, fontSize = 12.sp)
                        }
                    }
                    if (det.ubicacion.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("📍 ${det.ubicacion}", color = c.textoSuave, fontSize = Sania.txt.pequeno,
                            textAlign = TextAlign.Center)
                    }
                    det.descripcion?.let {
                        Spacer(Modifier.height(Sania.dim.sm))
                        Text(it, color = c.textoSuave, fontSize = Sania.txt.pequeno, textAlign = TextAlign.Center)
                    }
                }

                // ── Especialidades ──
                if (det.especialidades.isNotEmpty()) {
                    Bloque("ESPECIALIDADES") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            det.especialidades.forEach { esp ->
                                Box(
                                    Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                                        .background(c.chipBg).padding(horizontal = 12.dp, vertical = 5.dp),
                                ) { Text(esp, color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                            }
                        }
                    }
                }

                // ── Nuestro equipo ──
                if (det.profesionales.isNotEmpty()) {
                    Bloque("NUESTRO EQUIPO") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Sania.dim.lg),
                            verticalArrangement = Arrangement.spacedBy(Sania.dim.md)) {
                            det.profesionales.forEach { p -> Profesional(p, marca) }
                        }
                    }
                }

                // ── Horario de atención ──
                val horariosActivos = det.horarios.filter { it.activo }
                if (horariosActivos.isNotEmpty()) {
                    Bloque("HORARIO DE ATENCIÓN") {
                        Column {
                            horariosActivos.forEach { h ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(h.dia, color = c.texto, fontSize = Sania.txt.pequeno, fontWeight = FontWeight.Bold)
                                    Text("${h.apertura} – ${h.cierre}", color = c.textoSuave, fontSize = Sania.txt.pequeno)
                                }
                            }
                        }
                    }
                }

                // ── Cómo llegar (abre Maps nativo) ──
                if (det.ubicacion.isNotBlank() || det.referencia != null || (det.lat != null && det.lng != null)) {
                    Bloque("CÓMO LLEGAR") {
                        Column {
                            if (det.ubicacion.isNotBlank())
                                Text("📍 ${det.ubicacion}", color = c.texto, fontSize = Sania.txt.pequeno)
                            det.referencia?.let {
                                Text("🧭 $it", color = c.textoSuave, fontSize = Sania.txt.pequeno)
                            }
                            if (det.lat != null && det.lng != null) {
                                Spacer(Modifier.height(Sania.dim.sm))
                                BotonContorno("🗺️  Ver en Google Maps") {
                                    acciones.abrirMapa(det.lat!!, det.lng!!, det.nombre)
                                }
                            }
                        }
                    }
                }

                // ── Redes y contacto ──
                val redes = buildList {
                    waNumero(det.whatsappContacto)?.let { add(Triple("💬 WhatsApp", "https://wa.me/$it", Color(0xFF25D366))) }
                    det.facebook?.let { add(Triple("📘 Facebook", it, Color(0xFF1877F2))) }
                    det.instagram?.let { add(Triple("📸 Instagram", it, Color(0xFFE4405F))) }
                    det.tiktok?.let { add(Triple("🎵 TikTok", it, Color(0xFF000000))) }
                    det.sitioWeb?.let { add(Triple("🌐 Sitio web", it, marca)) }
                }
                if (redes.isNotEmpty()) {
                    Bloque("SÍGUENOS Y CONTÁCTANOS") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            redes.forEach { (label, url, color) ->
                                Box(
                                    Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
                                        .background(color).clickable { acciones.abrirUrl(url) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                ) { Text(label, color = Color.White, fontSize = Sania.txt.pequeno, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Sania.dim.xxl))
            }

            // CTA fijo abajo. La reserva ONLINE es del plan Plus (permiteReserva viene del
            // directorio) ADEMÁS del toggle de la clínica (det.reservas). Una Premium se ve
            // en el directorio pero su CTA es contactar por WhatsApp, no reservar.
            val puedeReservar = det.reservas && clinica.permiteReserva
            val wa = waNumero(det.whatsappContacto ?: clinica.whatsappContacto)
            if (puedeReservar) {
                Box(Modifier.fillMaxWidth().background(c.superficie).padding(Sania.dim.lg)) {
                    Button(
                        onClick = onReservar,
                        shape = RoundedCornerShape(Sania.shape.md.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = marca, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(Sania.dim.boton),
                    ) { Text("📅  Reservar mi cita", fontWeight = FontWeight.Bold) }
                }
            } else if (wa != null) {
                Box(Modifier.fillMaxWidth().background(c.superficie).padding(Sania.dim.lg)) {
                    Button(
                        onClick = { acciones.abrirUrl("https://wa.me/$wa") },
                        shape = RoundedCornerShape(Sania.shape.md.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366), contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(Sania.dim.boton),
                    ) { Text("💬  Agendar por WhatsApp", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun Bloque(titulo: String, contenido: @Composable () -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Sania.dim.xl, vertical = Sania.dim.sm)
            .clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .padding(Sania.dim.lg),
    ) {
        Text(titulo, color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Sania.dim.sm))
        contenido()
    }
}

@Composable
private fun Profesional(p: ProfDetalle, marca: Color) {
    val c = Sania.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
        Avatar(p.nombre.first().toString(), parseColor(p.color) ?: marca, 60.dp, 20.sp)
        Spacer(Modifier.height(4.dp))
        Text(p.nombre, color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        p.especialidad?.let { Text(it, color = c.textoSuave, fontSize = 11.sp, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun Avatar(inicial: String, color: Color, tam: androidx.compose.ui.unit.Dp, fuente: androidx.compose.ui.unit.TextUnit) {
    Box(Modifier.size(tam).clip(CircleShape).background(color), Alignment.Center) {
        Text(inicial, color = Color.White, fontSize = fuente, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BotonContorno(texto: String, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 9.dp),
    ) { Text(texto, color = c.navy, fontSize = Sania.txt.pequeno, fontWeight = FontWeight.Bold) }
}

// ── helpers ──
private fun parseColor(hex: String?): Color? {
    if (hex == null) return null
    val h = hex.removePrefix("#")
    val v = h.toLongOrNull(16) ?: return null
    return when (h.length) {
        6 -> Color(0xFF000000 or v)
        8 -> Color(v)
        else -> null
    }
}

private fun estrellas(n: Double): String {
    val llenas = n.toInt().coerceIn(0, 5)
    return "★".repeat(llenas) + "☆".repeat(5 - llenas)
}

private fun formato1(n: Double): String {
    val entero = n.toInt()
    val dec = ((n - entero) * 10).toInt()
    return "$entero.$dec"
}

/** Normaliza el número de WhatsApp (prefijo 51 Perú si es corto). */
private fun waNumero(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val d = raw.filter { it.isDigit() }
    if (d.isEmpty()) return null
    return if (raw.trim().startsWith("+")) d else if (d.length <= 9) "51$d" else d
}