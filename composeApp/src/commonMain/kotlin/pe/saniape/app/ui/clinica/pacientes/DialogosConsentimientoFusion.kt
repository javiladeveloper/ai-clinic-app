package pe.saniape.app.ui.clinica.pacientes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.BuscarPacienteRepo
import pe.saniape.app.data.staff.CoincidenciaPaciente
import pe.saniape.app.data.staff.ConsentimientoFirmaRepo
import pe.saniape.app.data.staff.FusionarFichaRepo
import pe.saniape.app.data.staff.RespuestaBusqueda
import pe.saniape.app.data.staff.numeroWhatsApp
import pe.saniape.app.data.staff.paisActivo
import pe.saniape.app.ui.AccionesNativas
import pe.saniape.app.ui.AlertaConTeclado
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.equipo.DibujoQr
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.ui.urlWhatsApp

/**
 * ✍ CONSENTIMIENTO desde el ⋯ de la ficha (paridad con la web, 11/11/2026): el
 * paciente lee y firma el compromiso en SU celular. Primero WhatsApp (si hay
 * teléfono), debajo el QR. Si la clínica tiene varios documentos, se elige uno.
 * Se pregunta cada 3 s si ya firmó; al cerrar sin firmar, el enlace vence.
 */
@Composable
internal fun DialogoConsentimientoFirma(
    pacienteId: String, nombre: String, telefono: String?, acciones: AccionesNativas, onCerrar: (firmo: Boolean) -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var estado by remember { mutableStateOf<ConsentimientoFirmaRepo.R?>(null) }
    var firmado by remember { mutableStateOf(false) }
    LaunchedEffect(pacienteId) { estado = ConsentimientoFirmaRepo.generar(pacienteId) }
    val enlace = estado as? ConsentimientoFirmaRepo.R.Enlace
    LaunchedEffect(enlace?.token) {
        val tk = enlace?.token ?: return@LaunchedEffect
        while (!firmado) {
            delay(3_000)
            if (ConsentimientoFirmaRepo.firmado(tk) == true) {
                firmado = true
                Toaster.exito("Documento firmado")
                onCerrar(true)
            }
        }
    }
    val cerrar = {
        enlace?.token?.let { tk -> if (!firmado) scope.launch { ConsentimientoFirmaRepo.anular(tk) } }
        onCerrar(firmado)
    }
    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text("✍ Firmar consentimiento") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Sania.dim.md),
            ) {
                when (val r = estado) {
                    null -> Text("Generando el enlace…", color = c.textoSuave, fontSize = Sania.txt.cuerpo)
                    is ConsentimientoFirmaRepo.R.Error -> Text(r.mensaje, color = c.error, fontSize = Sania.txt.cuerpo)
                    ConsentimientoFirmaRepo.R.SinPlantilla -> Text(
                        "Tu clínica todavía no escribió su compromiso. Se escribe en la web: Ajustes → Consentimientos.",
                        color = c.texto, fontSize = Sania.txt.cuerpo,
                    )
                    is ConsentimientoFirmaRepo.R.Elegir -> {
                        Text("¿Qué documento firma ${nombre.split(" ").first()}?", color = c.texto, fontSize = Sania.txt.cuerpo)
                        r.documentos.forEach { d ->
                            OutlinedButton(onClick = {
                                estado = null
                                scope.launch { estado = ConsentimientoFirmaRepo.generar(pacienteId, d.id) }
                            }, modifier = Modifier.fillMaxWidth()) { Text(d.nombre + if (d.porDefecto) " · el de siempre" else "") }
                        }
                    }
                    is ConsentimientoFirmaRepo.R.Enlace -> {
                        Text("${nombre.split(" ").first()} lo lee y lo firma con el dedo en su celular. El enlace vale 30 minutos.",
                            color = c.texto, fontSize = Sania.txt.cuerpo)
                        val tel = telefono.orEmpty().filter { it.isDigit() }
                        if (tel.isNotEmpty()) {
                            OutlinedButton(onClick = {
                                val wa = numeroWhatsApp(tel, paisActivo())
                                acciones.abrirUrl(urlWhatsApp(wa, ConsentimientoFirmaRepo.mensajeWhatsApp(nombre, r.url)))
                            }, modifier = Modifier.fillMaxWidth()) { Text("💬 Enviárselo por WhatsApp") }
                        }
                        Text("O que escanee este código:", color = c.textoSuave, fontSize = Sania.txt.cuerpo)
                        DibujoQr(r.url, Modifier.fillMaxWidth(0.8f))
                        TextButton(onClick = { acciones.copiarTexto(r.url, "Enlace para firmar") }) { Text("🔗 Copiar enlace") }
                        Text(if (firmado) "✓ Firmado" else "Esperando la firma…", color = if (firmado) c.ok else c.textoSuave, fontSize = Sania.txt.cuerpo)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = cerrar) { Text(if (firmado) "Listo" else "Cerrar") } },
    )
}

/**
 * 🔀 FUSIONAR desde el ⋯ de la ficha (paridad con la web, 11/11/2026; solo Admin).
 * La ficha abierta QUEDA y la elegida se junta en ella: 1) buscar la otra ficha,
 * 2) ver qué se mueve y si algo lo impide, 3) confirmar. En los datos que chocan
 * queda el de la ficha abierta; para elegir campo por campo, la web.
 */
@Composable
internal fun DialogoFusionarFicha(
    pacienteId: String, nombre: String, terminoPaciente: String, onCerrar: (fusiono: Boolean) -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var resultados by remember { mutableStateOf<List<CoincidenciaPaciente>>(emptyList()) }
    var buscando by remember { mutableStateOf(false) }
    var otra by remember { mutableStateOf<CoincidenciaPaciente?>(null) }
    var previa by remember { mutableStateOf<FusionarFichaRepo.Previa?>(null) }
    var fusionando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // La MISMA clave en todos los reintentos de esta fusión (la base no la repite).
    val clave = remember(otra?.id) { FusionarFichaRepo.nuevaClave() }

    LaunchedEffect(q) {
        if (q.trim().length < 2 || otra != null) { resultados = emptyList(); return@LaunchedEffect }
        delay(350)
        buscando = true
        resultados = when (val r = BuscarPacienteRepo.buscar(q, limite = 8)) {
            is RespuestaBusqueda.Ok -> r.resultados.filter { it.id != pacienteId }
            else -> emptyList()
        }
        buscando = false
    }
    LaunchedEffect(otra?.id) {
        val o = otra ?: return@LaunchedEffect
        previa = null
        previa = FusionarFichaRepo.previa(conservarId = pacienteId, absorberId = o.id)
    }

    AlertaConTeclado(
        onDismissRequest = { if (!fusionando) onCerrar(false) },
        title = { Text("🔀 Fusionar fichas") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Sania.dim.sm)) {
                val o = otra
                if (o == null) {
                    Text("¿Este $terminoPaciente está registrado dos veces? Busca la otra ficha: se juntará en la de $nombre.",
                        color = c.texto, fontSize = Sania.txt.cuerpo)
                    OutlinedTextField(value = q, onValueChange = { q = it }, singleLine = true,
                        placeholder = { Text("Nombre o DNI") }, modifier = Modifier.fillMaxWidth())
                    if (buscando) Text("Buscando…", color = c.textoSuave, fontSize = Sania.txt.cuerpo)
                    resultados.forEach { p ->
                        Column(Modifier.fillMaxWidth().clickable { otra = p }.padding(vertical = 8.dp)) {
                            Text(p.nombre, color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold)
                            Text(listOfNotNull(p.dniMascara?.let { "DNI $it" }, p.telefonoMascara, p.ultimaCita?.let { "última cita $it" }).joinToString(" · "),
                                color = c.textoSuave, fontSize = Sania.txt.pequeno)
                        }
                    }
                } else {
                    Text("La ficha de «${o.nombre}» se juntará en la de «$nombre», que es la que queda.", color = c.texto, fontSize = Sania.txt.cuerpo)
                    when (val pv = previa) {
                        null -> Text("Revisando las dos fichas…", color = c.textoSuave, fontSize = Sania.txt.cuerpo)
                        is FusionarFichaRepo.Previa.Error -> Text(pv.mensaje, color = c.error, fontSize = Sania.txt.cuerpo)
                        is FusionarFichaRepo.Previa.Ok -> {
                            val p = pv.previa
                            if (p.seMueve.isNotEmpty()) {
                                Text("Se mueve a esta ficha:", color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold)
                                p.seMueve.forEach { (que, n) -> Text("• $n $que", color = c.texto, fontSize = Sania.txt.cuerpo) }
                            } else Text("La otra ficha no tiene citas, tratamientos ni pagos.", color = c.textoSuave, fontSize = Sania.txt.cuerpo)
                            p.choques.forEach { ch ->
                                Text((if (ch.bloquea) "⛔ " else "⚠ ") + ch.detalle, color = if (ch.bloquea) c.error else c.texto, fontSize = Sania.txt.cuerpo)
                            }
                            Text("En los datos distintos (teléfono, dirección…) queda el de esta ficha. Para elegir campo por campo, hazlo desde la web. No se puede deshacer.",
                                color = c.textoSuave, fontSize = Sania.txt.pequeno)
                        }
                    }
                    TextButton(onClick = { otra = null; previa = null; error = null }, enabled = !fusionando) { Text("← Elegir otra ficha") }
                }
                error?.let { Text(it, color = c.error, fontSize = Sania.txt.cuerpo) }
            }
        },
        confirmButton = {
            val p = (previa as? FusionarFichaRepo.Previa.Ok)?.previa
            TextButton(
                enabled = otra != null && p != null && !p.bloqueada && !fusionando,
                onClick = {
                    val o = otra ?: return@TextButton
                    fusionando = true; error = null
                    scope.launch {
                        val e = FusionarFichaRepo.fusionar(pacienteId, o.id, clave)
                        fusionando = false
                        if (e == null) { Toaster.exito("Fichas fusionadas"); onCerrar(true) } else error = e
                    }
                },
            ) { Text(if (fusionando) "Fusionando…" else "Fusionar", color = c.error, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { onCerrar(false) }, enabled = !fusionando) { Text("Cancelar") } },
    )
}
