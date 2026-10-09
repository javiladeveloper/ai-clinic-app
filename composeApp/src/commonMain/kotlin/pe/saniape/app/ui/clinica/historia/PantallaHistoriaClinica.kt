package pe.saniape.app.ui.clinica.historia

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.CargaHistoriaHc
import pe.saniape.app.data.staff.HistoriaClinicaDoc
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.parametroPsicoHc
import pe.saniape.app.ui.AccionesNativas
import pe.saniape.app.ui.ManejarAtras
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.theme.Sania

/**
 * Historia clínica NATIVA (sin WebView), gemela de `/pacientes/[id]/historia` en
 * la web. El servidor arma la estructura (`?formato=json`): secciones de la
 * norma + un apartado por especialidad (Premium/Plus) o un solo bloque estándar
 * (Básico); la app dibuja bloque por bloque, en el orden en que llegan.
 *
 * "Imprimir / compartir PDF" usa el HTML del MISMO endpoint (el de siempre),
 * con el mismo formato y lo protegido de psicología que se ve en pantalla.
 *
 * Compatibilidad: si el servidor es viejo (responde HTML a `formato=json`), se
 * abre ese HTML en el visor como antes y la pantalla se cierra sola.
 */
@Composable
fun PantallaHistoriaClinica(
    pacienteId: String,
    pacienteNombre: String,
    acciones: AccionesNativas,
    onSalir: () -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    ManejarAtras(activo = true) { onSalir() }

    // null = lo que el servidor elija (por especialidad si el plan lo permite).
    var estilo by remember(pacienteId) { mutableStateOf<String?>(null) }
    var psicoInforme by remember(pacienteId) { mutableStateOf(false) }
    var psicoTests by remember(pacienteId) { mutableStateOf(false) }
    var reintento by remember { mutableIntStateOf(0) }
    var doc by remember(pacienteId) { mutableStateOf<HistoriaClinicaDoc?>(null) }
    var error by remember(pacienteId) { mutableStateOf<String?>(null) }
    var cargando by remember(pacienteId) { mutableStateOf(true) }
    var imprimiendo by remember { mutableStateOf(false) }
    val psico = parametroPsicoHc(psicoInforme, psicoTests)

    LaunchedEffect(pacienteId, estilo, psico, reintento) {
        cargando = true
        error = null
        when (val r = PacientesRepo.historiaJson(pacienteId, estilo, psico)) {
            is CargaHistoriaHc.Ok -> doc = r.doc
            is CargaHistoriaHc.Error -> error = r.mensaje
            CargaHistoriaHc.NoSoportado -> {
                // Web vieja: la historia imprimible de siempre, en el visor.
                val html = PacientesRepo.historiaHtml(pacienteId)
                if (html != null) acciones.abrirHtml(html, pacienteNombre) else Toaster.error("No se pudo abrir la historia clínica.")
                onSalir()
                return@LaunchedEffect
            }
        }
        cargando = false
    }

    fun imprimir() {
        if (imprimiendo) return
        scope.launch {
            imprimiendo = true
            val html = PacientesRepo.historiaHtml(pacienteId, estilo = doc?.formato ?: estilo, psico = psico)
            imprimiendo = false
            if (html != null) acciones.abrirHtml(html, pacienteNombre) else Toaster.error("No se pudo generar el PDF. Revisa tu conexión.")
        }
    }

    val accionesHc = AccionesHc(
        acciones = acciones,
        pedirPsico = { informe, tests -> psicoInforme = informe; psicoTests = tests },
        psicoInforme = psicoInforme, psicoTests = psicoTests,
    )

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Cabecera(
                nombre = doc?.paciente?.nombre?.ifBlank { null } ?: pacienteNombre,
                clinica = doc?.clinica?.let { cl -> listOfNotNull(cl.nombre.ifBlank { null }, cl.ipress).joinToString(" · ") },
                imprimiendo = imprimiendo, puedeImprimir = doc != null,
                onVolver = onSalir, onImprimir = ::imprimir,
            )
            val d = doc
            when {
                d == null && cargando -> Centro {
                    CircularProgressIndicator(color = c.navy)
                    Spacer(Modifier.height(Sania.dim.md))
                    Text("Armando la historia clínica…", color = c.textoSuave, fontSize = Sania.txt.pequeno)
                }
                d == null -> Centro {
                    Text(error ?: "No se pudo abrir la historia clínica.", color = c.error, fontSize = Sania.txt.cuerpo, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(Sania.dim.md))
                    BotonHc("Reintentar", relleno = true) { reintento++ }
                }
                else -> {
                    val scroll = rememberScrollState()
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)
                            .padding(horizontal = Sania.dim.lg, vertical = Sania.dim.md),
                    ) {
                        SelectorFormato(d, cargando) { estilo = it }
                        if (d.permisos.soloLoMio) AvisoInfo("Ves solo tus tratamientos y atenciones con este paciente.")
                        error?.let { AvisoInfo("⚠ $it") }
                        d.bloques.forEach { b ->
                            b.titulo?.let { TituloBloque(it) }
                            b.secciones.forEach { s -> SeccionHistoria(s, d, accionesHc) }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text(d.pie.izquierda, color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.weight(1f))
                            Text(d.pie.derecha, color = c.textoSuave, fontSize = 10.sp, textAlign = TextAlign.End)
                        }
                        Spacer(Modifier.height(Sania.dim.xxl))
                    }
                }
            }
        }
    }
}

@Composable
private fun Cabecera(
    nombre: String, clinica: String?, imprimiendo: Boolean, puedeImprimir: Boolean,
    onVolver: () -> Unit, onImprimir: () -> Unit,
) {
    val c = Sania.colors
    Column(Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = Sania.dim.lg, vertical = Sania.dim.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotonCabecera("← Volver", onVolver)
            Spacer(Modifier.weight(1f))
            if (puedeImprimir) BotonCabecera(if (imprimiendo) "Generando…" else "🖨 Imprimir / compartir PDF", onImprimir)
        }
        Spacer(Modifier.height(Sania.dim.sm))
        Text("📂 Historia clínica", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
        Text(nombre, color = c.sobreNavy, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        clinica?.takeIf { it.isNotBlank() }?.let { Text(it, color = c.sobreNavy.copy(alpha = 0.75f), fontSize = 12.sp) }
    }
}

@Composable
private fun BotonCabecera(texto: String, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).border(1.dp, c.sobreNavy.copy(alpha = 0.4f), RoundedCornerShape(Sania.shape.sm.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    ) { Text(texto, color = c.sobreNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun BotonHc(texto: String, relleno: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(if (relleno) c.navy else c.superficie)
            .border(1.dp, if (relleno) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
    ) { Text(texto, color = if (relleno) c.sobreNavy else c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

/** "Por especialidad / Estándar" (Premium/Plus) o la nota del formato estándar (Básico). */
@Composable
private fun SelectorFormato(d: HistoriaClinicaDoc, cargando: Boolean, onElegir: (String) -> Unit) {
    val c = Sania.colors
    if (!d.formatoEspecialidadDisponible) {
        AvisoInfo("Formato estándar. Los apartados por especialidad, los gráficos y la personalización vienen con los planes Premium y Plus.")
        return
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("especialidad" to "Por especialidad", "estandar" to "Estándar").forEach { (clave, texto) ->
            val activo = d.formato == clave
            Box(
                Modifier.clip(RoundedCornerShape(20.dp)).background(if (activo) c.navy else c.superficie)
                    .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(20.dp))
                    .clickable(enabled = !activo && !cargando) { onElegir(clave) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) { Text(texto, color = if (activo) c.sobreNavy else c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
        if (cargando) {
            Spacer(Modifier.weight(1f))
            CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp, modifier = Modifier.padding(4.dp).height(18.dp))
        }
    }
}

@Composable
private fun TituloBloque(texto: String) {
    val c = Sania.colors
    Text(texto, color = c.navy, fontSize = Sania.txt.seccion, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
}

@Composable
private fun AvisoInfo(texto: String) {
    val c = Sania.colors
    Box(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(c.infoBg).padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(texto, color = c.info, fontSize = 12.sp) }
}

@Composable
private fun Centro(contenido: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(Sania.dim.xl), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { contenido() }
    }
}
