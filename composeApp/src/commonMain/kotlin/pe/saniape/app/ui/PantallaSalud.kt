package pe.saniape.app.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.Documento
import pe.saniape.app.data.Saldo
import pe.saniape.app.data.SaludRepo
import pe.saniape.app.data.Tratamiento
import pe.saniape.app.ui.theme.Sania

/**
 * Tab Salud — mi(s) tratamiento(s) con progreso + timeline, saldo (si la clínica
 * lo habilitó) y mis documentos. Igual que MiTratamiento de la web.
 */
@Composable
fun PantallaSalud() {
    val c = Sania.colors
    val acciones = recordarAcciones()
    val scope = rememberCoroutineScope()

    var cargando by remember { mutableStateOf(true) }
    var tratamientos by remember { mutableStateOf<List<Tratamiento>>(emptyList()) }
    var saldos by remember { mutableStateOf<Map<String, Saldo>>(emptyMap()) }
    var documentos by remember { mutableStateOf<List<Documento>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            tratamientos = SaludRepo.tratamientos()
            saldos = SaludRepo.saldos()
            documentos = SaludRepo.documentos()
        } catch (_: Exception) { /* secciones vacías */ }
        finally { cargando = false }
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Barra de título
            Box(Modifier.fillMaxWidth().background(c.navyDark)
                .padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg)) {
                Text("Mi salud", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
            }

            when {
                cargando -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = c.navy)
                }
                tratamientos.isEmpty() && documentos.isEmpty() ->
                    Box(Modifier.fillMaxSize().padding(Sania.dim.xxl), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("💙", fontSize = 44.sp)
                            Spacer(Modifier.height(Sania.dim.md))
                            Text("Aún no tienes tratamientos", color = c.texto,
                                fontSize = Sania.txt.seccion, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(Sania.dim.sm))
                            Text("Cuando una clínica registre tu tratamiento, verás aquí tu progreso y documentos.",
                                color = c.textoSuave, fontSize = Sania.txt.cuerpo,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = Sania.dim.lg),
                    verticalArrangement = Arrangement.spacedBy(Sania.dim.md),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Sania.dim.lg),
                ) {
                    val ordenados = tratamientos.sortedByDescending { it.estado == "Activo" }
                    if (ordenados.isNotEmpty()) {
                        item { Etiqueta("MI TRATAMIENTO") }
                        items(ordenados) { t -> TarjetaTratamiento(t, saldos[t.id]) }
                    }
                    if (documentos.isNotEmpty()) {
                        item { Spacer(Modifier.height(Sania.dim.sm)); Etiqueta("MIS DOCUMENTOS") }
                        items(documentos) { d ->
                            TarjetaDocumento(d, onAbrir = {
                                scope.launch {
                                    val url = SaludRepo.urlDocumento(d.path)
                                    if (url != null) acciones.abrirUrl(url)
                                }
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Etiqueta(t: String) {
    Text(t, color = Sania.colors.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
}

@Composable
private fun TarjetaTratamiento(t: Tratamiento, saldo: Saldo?) {
    val c = Sania.colors
    var verSesiones by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(Sania.dim.tarjeta),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(t.procedimiento, color = c.texto, fontSize = Sania.txt.seccion, fontWeight = FontWeight.Bold)
                t.clinica?.let { Text("🏥 $it", color = c.textoSuave, fontSize = 12.sp) }
            }
            val activo = t.estado == "Activo"
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                .background(if (activo) c.okBg else c.chipBg).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(if (activo) "En curso" else if (t.estado == "Completado") "Terminado" else t.estado,
                    color = if (activo) c.ok else c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Progreso
        if (t.usaSesiones && t.totalSesiones != null) {
            val pct = (t.sesionesCompletadas.toFloat() / t.totalSesiones).coerceIn(0f, 1f)
            Spacer(Modifier.height(Sania.dim.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Progreso", color = c.textoSuave, fontSize = 12.sp)
                Text("${t.sesionesCompletadas} de ${t.totalSesiones} sesiones",
                    color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(Sania.dim.barraProgreso)
                .clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.chipBg)) {
                Box(Modifier.fillMaxWidth(pct).height(Sania.dim.barraProgreso)
                    .clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.ok))
            }
        }

        // Saldo
        if (saldo != null && saldo.acordado > 0) {
            Spacer(Modifier.height(Sania.dim.md))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                .background(c.fondo).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Saldo del tratamiento", color = c.textoSuave, fontSize = 13.sp)
                Text(if (saldo.saldo > 0) "Debes S/ ${formato2(saldo.saldo)}" else "Pagado ✓",
                    color = if (saldo.saldo > 0) c.pend else c.ok, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Timeline de sesiones
        if (t.sesiones.isNotEmpty()) {
            Spacer(Modifier.height(Sania.dim.md))
            Text(if (verSesiones) "Ocultar sesiones" else "Ver mis sesiones",
                color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { verSesiones = !verSesiones })
            AnimatedVisibility(verSesiones) {
                Column(Modifier.padding(top = Sania.dim.sm)) {
                    t.sesiones.forEach { s ->
                        val colorPunto = when (s.estado) {
                            "Completada" -> c.ok
                            "En progreso" -> c.info
                            "Reprogramada" -> c.pend
                            else -> c.textoSuave
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(8.dp))
                                .background(colorPunto))
                            Spacer(Modifier.width(8.dp))
                            Text("Sesión #${s.numero}", color = c.texto, fontSize = 13.sp)
                            Spacer(Modifier.width(6.dp))
                            Text("· ${s.fecha}", color = c.textoSuave, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            Text(s.estado, color = c.textoSuave, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaDocumento(d: Documento, onAbrir: () -> Unit) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable { onAbrir() }.padding(Sania.dim.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("📄", fontSize = 22.sp)
        Spacer(Modifier.width(Sania.dim.md))
        Column(Modifier.weight(1f)) {
            Text(d.nombre, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("${d.categoria} · ${d.fecha.take(10)}", color = c.textoSuave, fontSize = 12.sp)
        }
        Text("Abrir →", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formato2(n: Double): String {
    val centavos = (n * 100).toLong()
    return "${centavos / 100}.${(centavos % 100).toString().padStart(2, '0')}"
}