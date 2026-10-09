package pe.saniape.app.ui.clinica.pacientes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.AdmisionQr
import pe.saniape.app.data.staff.AdmisionQrRepo
import pe.saniape.app.data.staff.LocalTerminologiaPaciente
import pe.saniape.app.data.staff.cuentaAtras
import pe.saniape.app.data.staff.textoWhatsAppAdmision
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.equipo.DibujoQr
import pe.saniape.app.ui.theme.Sania

/**
 * "📱 QR de admisión" — gemelo de QrAdmision (web, Pacientes). Pide el token al
 * servidor al abrir y muestra el QR en grande para que el paciente nuevo lo
 * escanee y llene su ficha desde su celular. Vale 10 minutos y un solo registro:
 * se genera uno por paciente ("↻ Generar otro").
 */
@Composable
fun DialogoQrAdmision(clinicaNombre: String?, onCerrar: () -> Unit) {
    val c = Sania.colors
    val tpl = LocalTerminologiaPaciente.current
    val scope = rememberCoroutineScope()
    val acciones = pe.saniape.app.ui.recordarAcciones()
    var qr by remember { mutableStateOf<AdmisionQr?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var generando by remember { mutableStateOf(false) }
    var restante by remember { mutableIntStateOf(0) }

    fun generar() {
        if (generando) return
        generando = true; qr = null; error = null
        scope.launch {
            val r = AdmisionQrRepo.generar()
            generando = false
            qr = r.qr; error = r.error
            restante = (r.qr?.ttlMin ?: 0) * 60
        }
    }
    LaunchedEffect(Unit) { generar() }
    // Cuenta atrás de la vigencia (se reinicia con cada código nuevo).
    LaunchedEffect(qr) {
        if (qr == null) return@LaunchedEffect
        while (restante > 0) { delay(1_000); restante -= 1 }
    }
    val q = qr
    val vencido = q != null && restante <= 0

    DialogoForm(
        titulo = "📱 QR de admisión",
        subtitulo = "El ${tpl.paciente} se registra solo desde su celular",
        textoAccion = "Listo",
        textoCancelar = "Cerrar",
        onCancelar = onCerrar,
        onAccion = onCerrar,
    ) {
        Text(
            "El ${tpl.paciente} nuevo lo escanea y llena sus datos desde su celular; la ficha te llega con aviso. " +
                "Vale ${q?.ttlMin ?: 10} minutos y un solo registro — genera uno por ${tpl.paciente}.",
            color = c.textoSuave, fontSize = 13.sp,
        )
        Spacer(Modifier.height(Sania.dim.md))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                error != null -> Text(error!!, color = c.error, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center)
                q == null -> Box(Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f), Alignment.Center) {
                    CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp)
                }
                vencido -> Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), Alignment.Center) {
                    Text("El código venció. Genera otro.", color = c.textoSuave, fontSize = 14.sp)
                }
                else -> {
                    // En grande: lo escanea el paciente desde el otro lado del mostrador.
                    DibujoQr(q.url, Modifier.widthIn(max = 320.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(Sania.shape.md.dp)))
                    Spacer(Modifier.height(Sania.dim.sm))
                    Text("⏳ Vence en ${cuentaAtras(restante)}",
                        color = if (restante < 60) c.error else c.navy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(Sania.dim.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotonQr("🔗 Copiar enlace") {
                            acciones.copiarTexto(q.url, "Enlace de admisión")
                            Toaster.exito("Enlace copiado (vale ${q.ttlMin} min)")
                        }
                        BotonQr("📱 WhatsApp") {
                            val texto = textoWhatsAppAdmision(q.url, q.ttlMin, clinicaNombre)
                            acciones.abrirUrl("https://wa.me/?text=" + pe.saniape.app.ui.urlEncode(texto))
                        }
                    }
                }
            }
            Spacer(Modifier.height(Sania.dim.sm))
            if (!generando) BotonQr("↻ Generar otro") { generar() }
        }
    }
}

@Composable
private fun BotonQr(texto: String, onClick: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.pill.dp)
    Box(
        Modifier.clip(forma).background(c.superficie).border(1.dp, c.borde, forma)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
    ) { Text(texto, color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}
