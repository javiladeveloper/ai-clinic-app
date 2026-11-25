package pe.saniape.app.ui.clinica.audiologia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.AudiometriaOido
import pe.saniape.app.data.staff.DatosAuditivos
import pe.saniape.app.data.staff.EvaluacionAuditivaFila
import pe.saniape.app.data.staff.EvaluacionAuditivaRepo
import pe.saniape.app.data.staff.GradoPerdida
import pe.saniape.app.data.staff.HALLAZGOS_OTOSCOPIA
import pe.saniape.app.data.staff.Logo
import pe.saniape.app.data.staff.MOTIVOS_AUDITIVOS
import pe.saniape.app.data.staff.Oido
import pe.saniape.app.data.staff.PasoAuditivo
import pe.saniape.app.data.staff.TipoPerdida
import pe.saniape.app.data.staff.conclusionOido
import pe.saniape.app.data.staff.fechaLegibleCorta
import pe.saniape.app.data.staff.fraseOido
import pe.saniape.app.data.staff.gradoPersona
import pe.saniape.app.data.staff.hallazgosQueImpiden
import pe.saniape.app.data.staff.hayAudiometria
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.pasosDisponibles
import pe.saniape.app.data.staff.resumenEvaluacion
import pe.saniape.app.data.staff.sugiereAudifonos
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.DialogoRecomendar
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania

private val LADOS = listOf("derecho" to "Derecho", "izquierdo" to "Izquierdo", "ambos" to "Ambos")

@Composable
private fun Chip(texto: String, activo: Boolean, habilitado: Boolean = true, onClick: () -> Unit) {
    val c = Sania.colors
    Text(texto, color = if (activo) c.sobreNavy else c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(if (activo) c.navy else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
            .clickable(enabled = habilitado, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp))
}

@Composable
private fun Etq(t: String) {
    Text(t.uppercase(), color = Sania.colors.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
}

@Composable
private fun Campo(valor: String, placeholder: String, numero: Boolean = false, lineas: Int = 1, onCambio: (String) -> Unit) {
    OutlinedTextField(
        value = valor, onValueChange = onCambio, singleLine = lineas == 1, minLines = lineas,
        placeholder = { Text(placeholder, color = Sania.colors.textoSuave, fontSize = 13.sp) },
        keyboardOptions = if (numero) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SiNo(etiqueta: String, valor: Boolean?, onCambio: (Boolean?) -> Unit) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(etiqueta, color = c.texto, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Chip("Sí", valor == true) { onCambio(if (valor == true) null else true) }
        Spacer(Modifier.width(6.dp))
        Chip("No", valor == false) { onCambio(if (valor == false) null else false) }
    }
}

/**
 * 👂 El recorrido de la evaluación auditiva (gemelo de EditorAuditivo de la web): anamnesis →
 * videotoscopia (¿apto?) → audiometría → conclusión → audífonos. Guarda en cada «Guardar y seguir».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorAuditivo(
    pacienteId: String,
    pacienteNombre: String,
    citaId: String?,
    evaluacion: EvaluacionAuditivaFila?,
    fecha: String,
    onGuardado: (EvaluacionAuditivaFila) -> Unit,
    onCerrar: (() -> Unit)? = null,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = recordarAcciones()
    var d by remember { mutableStateOf(evaluacion?.datos ?: DatosAuditivos()) }
    var guardada by remember { mutableStateOf(evaluacion) }
    var paso by remember { mutableStateOf(PasoAuditivo.ANAMNESIS) }
    var via by remember { mutableStateOf(ViaAudiometria.AEREA) }
    var nr by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var sucio by remember { mutableStateOf(false) }
    var recomendando by remember { mutableStateOf(false) }
    fun cambiar(n: DatosAuditivos) { d = n; sucio = true }
    val disponibles = pasosDisponibles(d)
    if (paso !in disponibles) paso = disponibles.last()
    val idx = disponibles.indexOf(paso)

    suspend fun guardar(): Boolean {
        if (guardando) return false
        guardando = true
        val (r, err) = EvaluacionAuditivaRepo.guardar(guardada?.id, pacienteId, citaId, fecha, d)
        guardando = false
        if (r == null) { Toaster.error(err ?: "No se pudo guardar la evaluación"); return false }
        guardada = r; sucio = false
        onGuardado(r)
        return true
    }
    fun siguiente() {
        scope.launch {
            if ((sucio || guardada == null) && !guardar()) return@launch
            if (idx < disponibles.size - 1) paso = disponibles[idx + 1] else Toaster.exito("Evaluación guardada")
        }
    }
    fun informe() {
        scope.launch {
            if (sucio && !guardar()) return@launch
            acciones.abrirHtml(htmlInforme(d, pacienteNombre, guardada?.fecha ?: fecha), "Informe audiológico")
        }
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PasoAuditivo.entries.forEachIndexed { i, p ->
                Chip("${i + 1}. ${p.titulo}", paso == p, habilitado = p in disponibles) { paso = p }
            }
        }
        when (paso) {
            PasoAuditivo.ANAMNESIS -> {
                Etq("¿Por qué viene?")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MOTIVOS_AUDITIVOS.forEach { m ->
                        val on = m in d.anamnesis.motivos
                        Chip(m, on) { cambiar(d.copy(anamnesis = d.anamnesis.copy(motivos = if (on) d.anamnesis.motivos - m else d.anamnesis.motivos + m))) }
                    }
                }
                Etq("Oído afectado")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LADOS.forEach { (v, t) -> Chip(t, d.anamnesis.lado == v) { cambiar(d.copy(anamnesis = d.anamnesis.copy(lado = if (d.anamnesis.lado == v) null else v))) } }
                }
                Etq("¿Desde cuándo?")
                Campo(d.anamnesis.tiempo, "Ej. 2 años, progresivo") { cambiar(d.copy(anamnesis = d.anamnesis.copy(tiempo = it.take(120)))) }
                Spacer(Modifier.height(6.dp))
                SiNo("Expuesto a ruido", d.anamnesis.ruido) { cambiar(d.copy(anamnesis = d.anamnesis.copy(ruido = it))) }
                SiNo("Familiares con sordera", d.anamnesis.familiares) { cambiar(d.copy(anamnesis = d.anamnesis.copy(familiares = it))) }
                SiNo("Ya usó audífonos", d.anamnesis.audifonosPrevios) { cambiar(d.copy(anamnesis = d.anamnesis.copy(audifonosPrevios = it))) }
                Etq("Otros datos")
                Campo(d.anamnesis.notas, "Enfermedades, medicamentos, cirugías de oído, trabajo…", lineas = 2) { cambiar(d.copy(anamnesis = d.anamnesis.copy(notas = it.take(2000)))) }
            }
            PasoAuditivo.OTOSCOPIA -> {
                Oido.entries.forEach { o ->
                    val x = d.otoscopia.de(o)
                    Text(o.nombre, color = colorOido(o), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        HALLAZGOS_OTOSCOPIA.forEach { h ->
                            val on = h in x.hallazgos
                            Chip(h, on) { cambiar(d.copy(otoscopia = d.otoscopia.con(o, x.copy(hallazgos = if (on) x.hallazgos - h else x.hallazgos + h)))) }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Campo(x.nota, "Detalle (opcional)") { cambiar(d.copy(otoscopia = d.otoscopia.con(o, x.copy(nota = it.take(500))))) }
                }
                Text("Las imágenes de la videotoscopia se guardan en la ficha → 🔬 Exámenes.", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Etq("¿El oído está en condiciones para la audiometría?")
                val impiden = hallazgosQueImpiden(d)
                if (impiden.isNotEmpty()) Text("⚠ ${impiden.joinToString(", ")}: la audiometría no daría un buen resultado.", color = c.pend, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("✓ Apto: continuar", d.otoscopia.apto == true) { cambiar(d.copy(otoscopia = d.otoscopia.copy(apto = if (d.otoscopia.apto == true) null else true))) }
                    Chip("✗ No apto por ahora", d.otoscopia.apto == false) { cambiar(d.copy(otoscopia = d.otoscopia.copy(apto = if (d.otoscopia.apto == false) null else false))) }
                }
                if (d.otoscopia.apto == false) {
                    Etq("Qué se indica")
                    Campo(d.otoscopia.indicacion, "Ej. Lavado de oído en 3 días y volver para la audiometría", lineas = 2) {
                        cambiar(d.copy(otoscopia = d.otoscopia.copy(indicacion = it.take(500))))
                    }
                }
            }
            PasoAuditivo.AUDIOMETRIA -> {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                    Text("Estoy marcando: ", color = c.textoSuave, fontSize = 13.sp)
                    Chip("Vía aérea", via == ViaAudiometria.AEREA) { via = ViaAudiometria.AEREA }
                    Spacer(Modifier.width(6.dp))
                    Chip("Vía ósea", via == ViaAudiometria.OSEA) { via = ViaAudiometria.OSEA }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { nr = !nr }) {
                    Checkbox(nr, { nr = it }, colors = CheckboxDefaults.colors(checkedColor = c.navy))
                    Text("No respondió (↓)", color = c.texto, fontSize = 13.sp)
                }
                Text("Toca la cuadrícula en la frecuencia y el nivel del umbral. Tocar el mismo punto lo quita.", color = c.textoSuave, fontSize = 12.sp)
                Oido.entries.forEach { o ->
                    Spacer(Modifier.height(10.dp))
                    Audiograma(o, d.aud(o), via, nr, onChange = { v: AudiometriaOido -> cambiar(d.copy(audiometria = d.audiometria + (o to v))) })
                    Text(fraseOido(d, o), color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    Text("Borrar este oído", color = c.error, fontSize = 12.sp,
                        modifier = Modifier.clickable { cambiar(d.copy(audiometria = d.audiometria + (o to AudiometriaOido()))) }.padding(vertical = 4.dp))
                }
                Etq("Logoaudiometría")
                Oido.entries.forEach { o ->
                    val l = d.log(o)
                    Text(o.nombre, color = colorOido(o), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        fun n(s: String) = s.filter { it.isDigit() || it == '-' }.take(4).toIntOrNull()
                        Column(Modifier.weight(1f)) { Text("Umbral de voz (dB)", color = c.textoSuave, fontSize = 10.sp); Campo(l.srt?.toString() ?: "", "", numero = true) { cambiar(d.copy(logo = d.logo + (o to l.copy(srt = n(it))))) } }
                        Column(Modifier.weight(1f)) { Text("Discriminación (%)", color = c.textoSuave, fontSize = 10.sp); Campo(l.discriminacion?.toString() ?: "", "", numero = true) { cambiar(d.copy(logo = d.logo + (o to l.copy(discriminacion = n(it)?.coerceIn(0, 100))))) } }
                        Column(Modifier.weight(1f)) { Text("A qué nivel (dB)", color = c.textoSuave, fontSize = 10.sp); Campo(l.intensidad?.toString() ?: "", "", numero = true) { cambiar(d.copy(logo = d.logo + (o to l.copy(intensidad = n(it))))) } }
                    }
                }
            }
            PasoAuditivo.CONCLUSION -> {
                Text("Sania sugiere el grado (clasificación de la OMS, promedio de 500, 1000, 2000 y 4000 Hz) y el tipo (brecha aéreo-ósea de 15 dB o más = componente conductivo). Confírmalo o corrígelo: lo que elijas es lo que sale en el informe.",
                    color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.infoBg).padding(10.dp))
                gradoPersona(d)?.let { Text("Grado de la persona (mejor oído): ${it.etiqueta}", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)) }
                Oido.entries.forEach { o ->
                    val cv = conclusionOido(d, o)
                    Text(o.nombre, color = colorOido(o), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                    cv.sugerido.pta?.let { Text("Promedio $it dB HL" + (cv.sugerido.brecha?.let { b -> " · brecha $b dB" } ?: ""), color = c.textoSuave, fontSize = 12.sp) }
                    Text("Grado", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        GradoPerdida.entries.forEach { g ->
                            Chip(g.etiqueta.removePrefix("Pérdida ") + if (cv.sugerido.grado == g) " (sugerido)" else "", cv.grado == g) {
                                cambiar(d.copy(conclusion = d.conclusion.con(o, d.conclusion.de(o).copy(grado = g))))
                            }
                        }
                    }
                    Text("Tipo", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        TipoPerdida.entries.forEach { t ->
                            Chip(t.etiqueta + if (cv.sugerido.tipo == t) " (sugerido)" else "", cv.tipo == t) {
                                cambiar(d.copy(conclusion = d.conclusion.con(o, d.conclusion.de(o).copy(tipo = t))))
                            }
                        }
                    }
                }
                Etq("Conclusión")
                Campo(d.conclusion.texto, "Ej. ${Oido.entries.joinToString(". ") { "${it.nombre}: ${fraseOido(d, it).lowercase()}" }}", lineas = 3) {
                    cambiar(d.copy(conclusion = d.conclusion.copy(texto = it.take(3000))))
                }
                Etq("Recomendación")
                Campo(d.conclusion.recomendacion, "Ej. Adaptación de audífonos · Control audiométrico en 1 año · Evaluación por otorrino", lineas = 2) {
                    cambiar(d.copy(conclusion = d.conclusion.copy(recomendacion = it.take(2000))))
                }
                if (sugiereAudifonos(d)) Text("👂 Hay pérdida neurosensorial o mixta: el siguiente paso es la demostración de audífonos y su proforma.",
                    color = c.teal, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            }
            PasoAuditivo.AUDIFONOS -> {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)
                    .clickable { cambiar(d.copy(demostracion = d.demostracion.copy(hecha = !d.demostracion.hecha))) }) {
                    Checkbox(d.demostracion.hecha, { cambiar(d.copy(demostracion = d.demostracion.copy(hecha = it))) }, colors = CheckboxDefaults.colors(checkedColor = c.navy))
                    Text("Se hizo la demostración de audífonos", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                if (d.demostracion.hecha) {
                    Etq("Modelo que probó")
                    Campo(d.demostracion.modelo, "Marca y modelo") { cambiar(d.copy(demostracion = d.demostracion.copy(modelo = it.take(200)))) }
                    Etq("En qué oído")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LADOS.forEach { (v, t) -> Chip(t, d.demostracion.oido == v) { cambiar(d.copy(demostracion = d.demostracion.copy(oido = if (d.demostracion.oido == v) null else v))) } }
                    }
                    Etq("Cómo le fue")
                    Campo(d.demostracion.resultado, "Ej. Entiende mejor la conversación con ruido de fondo.", lineas = 2) {
                        cambiar(d.copy(demostracion = d.demostracion.copy(resultado = it.take(1000))))
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(10.dp),
                ) {
                    Text("🛍️ Proforma de audífonos", color = c.teal, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("🛍️ Recomendar productos", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 6.dp).clip(RoundedCornerShape(Sania.shape.md.dp)).border(1.dp, c.lav, RoundedCornerShape(Sania.shape.md.dp))
                            .clickable { recomendando = true }.padding(horizontal = 12.dp, vertical = 8.dp))
                    Text("Elige uno o dos audífonos y sus accesorios. El paciente se lleva la proforma y la recibe por WhatsApp; puede comprar todo, una parte o nada.",
                        color = c.textoSuave, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (idx > 0) Text("← Atrás", color = c.textoSuave, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { paso = disponibles[idx - 1] }.padding(8.dp))
            else onCerrar?.let { Text("Cerrar", color = c.textoSuave, fontSize = 14.sp, modifier = Modifier.clickable { it() }.padding(8.dp)) }
            Spacer(Modifier.weight(1f))
            if (sucio) Text("Sin guardar  ", color = c.pend, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (guardada != null && hayAudiometria(d)) {
                Text("🖨 Informe", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
                        .clickable(enabled = !guardando) { informe() }.padding(horizontal = 10.dp, vertical = 9.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(if (guardando) "Guardando…" else if (idx < disponibles.size - 1) "Guardar y seguir →" else "Guardar",
                color = c.sobreNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(if (guardando) c.navy.copy(alpha = 0.6f) else c.navy)
                    .clickable(enabled = !guardando) { siguiente() }.padding(horizontal = 12.dp, vertical = 9.dp))
        }
    }

    if (recomendando) DialogoRecomendar(pacienteId, citaId, onCerrar = { recomendando = false }, onGuardado = { recomendando = false })
}

/** 👂 Pestaña de la ficha: el historial de evaluaciones y el recorrido para una nueva. */
@Composable
fun ContenidoEvaluacionAuditiva(pacienteId: String, pacienteNombre: String, puedeEditar: Boolean) {
    val c = Sania.colors
    var lista by remember(pacienteId) { mutableStateOf<List<EvaluacionAuditivaFila>?>(null) }
    var error by remember(pacienteId) { mutableStateOf(false) }
    var intento by remember { mutableIntStateOf(0) }
    // null = lista; "nueva" = editor vacío; id = editor de esa.
    var abierta by remember(pacienteId) { mutableStateOf<String?>(null) }
    LaunchedEffect(pacienteId, intento) {
        val r = EvaluacionAuditivaRepo.listar(pacienteId)
        error = r == null
        lista = r
    }
    val ab = abierta
    if (ab != null) {
        val ev = lista.orEmpty().find { it.id == ab }
        EditorAuditivo(pacienteId, pacienteNombre, citaId = ev?.citaId, evaluacion = ev, fecha = ev?.fecha ?: hoyClinicaIso(),
            onGuardado = { intento++ }, onCerrar = { abierta = null })
        Text("← Volver a la lista", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { abierta = null; intento++ }.padding(vertical = 10.dp))
        return
    }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("👂 EVALUACIÓN AUDITIVA", color = c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (puedeEditar) Text("＋ Nueva evaluación", color = c.sobreNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.navy).clickable { abierta = "nueva" }
                    .padding(horizontal = 12.dp, vertical = 9.dp))
        }
        Spacer(Modifier.height(10.dp))
        val l = lista
        when {
            error -> Text("No se pudo cargar la evaluación auditiva. Toca para reintentar.", color = c.error, fontSize = 13.sp, modifier = Modifier.clickable { intento++ })
            l == null -> Text("Cargando…", color = c.textoSuave, fontSize = 13.sp)
            l.isEmpty() -> Text("Sin evaluaciones auditivas todavía.", color = c.textoSuave, fontSize = 13.sp)
            else -> l.forEach { e ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
                        .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(fechaLegibleCorta(e.fecha) + if (e.citaId != null) " · de una consulta" else "", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(resumenEvaluacion(e.datos), color = c.textoSuave, fontSize = 12.sp)
                    }
                    Text(if (puedeEditar) "Abrir" else "Ver", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
                            .clickable { abierta = e.id }.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
    }
}

/** La evaluación DENTRO de la consulta (paso Examen): una por cita. */
@Composable
fun EvaluacionAuditivaDeCita(pacienteId: String, pacienteNombre: String, citaId: String, fechaCita: String) {
    var lista by remember(pacienteId) { mutableStateOf<List<EvaluacionAuditivaFila>?>(null) }
    LaunchedEffect(pacienteId) { lista = EvaluacionAuditivaRepo.listar(pacienteId) ?: emptyList() }
    val l = lista ?: return
    val propia = l.find { it.citaId == citaId }
    val hoy = hoyClinicaIso()
    // Clave fija por cita (no por id): el primer guardado no debe rehacer el editor en el paso 1.
    androidx.compose.runtime.key(citaId) {
        EditorAuditivo(pacienteId, pacienteNombre, citaId, propia, if (fechaCita > hoy) hoy else fechaCita, onGuardado = {})
    }
}

// ── Informe imprimible (HTML con el audiograma en SVG) ──────────────────────────

private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private fun svgAudiograma(o: Oido, a: AudiometriaOido): String {
    val color = if (o == Oido.OD) "#dc2626" else "#2563eb"
    val w = 340f; val h = 372f; val izq = 38f; val der = 12f; val arr = 26f; val aba = 22f
    val fa = pe.saniape.app.data.staff.FRECUENCIAS_AEREA
    fun x(f: Int) = izq + (fa.indexOf(f) + 0.5f) * ((w - izq - der) / fa.size)
    fun y(db: Int) = arr + ((db + 10f) / 130f) * (h - arr - aba)
    val sb = StringBuilder("<svg viewBox='0 0 340 372' width='300' xmlns='http://www.w3.org/2000/svg' style='border:1px solid #dde1f0;border-radius:10px'>")
    for (db in -10..120 step 10) sb.append("<line x1='$izq' x2='${w - der}' y1='${y(db)}' y2='${y(db)}' stroke='#dde1f0' stroke-width='0.7'/><text x='${izq - 5}' y='${y(db) + 3}' font-size='9' text-anchor='end' fill='#6b7280'>$db</text>")
    fa.forEach { f -> sb.append("<line x1='${x(f)}' x2='${x(f)}' y1='$arr' y2='${h - aba}' stroke='#dde1f0' stroke-width='0.7'/><text x='${x(f)}' y='${arr - 9}' font-size='9.5' text-anchor='middle' fill='#6b7280'>${if (f >= 1000) "${f / 1000}k" else "$f"}</text>") }
    val pts = fa.mapNotNull { f -> a.aerea[f]?.takeIf { !it.nr }?.let { "${x(f)},${y(it.db)}" } }
    if (pts.size > 1) sb.append("<polyline points='${pts.joinToString(" ")}' fill='none' stroke='$color' stroke-width='1.8'/>")
    a.osea.forEach { (f, u) ->
        val cx = x(f); val cy = y(u.db)
        val d = if (o == Oido.OD) "M${cx - 4},${cy - 6} l-6,6 l6,6" else "M${cx + 4},${cy - 6} l6,6 l-6,6"
        sb.append("<path d='$d' stroke='$color' stroke-width='2' fill='none'/>")
    }
    a.aerea.forEach { (f, u) ->
        val cx = x(f); val cy = y(u.db)
        if (o == Oido.OD) sb.append("<circle cx='$cx' cy='$cy' r='5.5' fill='#fff' stroke='$color' stroke-width='2'/>")
        else sb.append("<path d='M${cx - 5},${cy - 5} l10,10 M${cx + 5},${cy - 5} l-10,10' stroke='$color' stroke-width='2.2'/>")
    }
    return sb.append("</svg>").toString()
}

fun htmlInforme(d: DatosAuditivos, paciente: String, fecha: String): String {
    val clinica = pe.saniape.app.data.staff.StaffContextoRepo.actual?.clinicaNombre.orEmpty()
    fun fila(t: String, v: String) = if (v.isBlank()) "" else "<div><b>${esc(t)}:</b> ${esc(v)}</div>"
    val an = d.anamnesis
    val sb = StringBuilder()
    sb.append("<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>")
    sb.append("<style>body{font-family:sans-serif;color:#1e2d5e;padding:18px;font-size:13px}h1{font-size:18px;margin:0}h2{font-size:13px;color:#2c3e7a;text-transform:uppercase;margin:16px 0 6px}.g{display:flex;gap:16px;flex-wrap:wrap}</style></head><body>")
    sb.append("<h1>${esc(clinica)}</h1><div style='color:#6b7280'>Informe audiológico</div><hr>")
    sb.append("<div><b>Paciente:</b> ${esc(paciente)} &nbsp; <b>Fecha:</b> ${esc(fechaLegibleCorta(fecha))}</div>")
    sb.append("<h2>Anamnesis</h2>")
    sb.append(fila("Motivo", an.motivos.joinToString(", ")))
    sb.append(fila("Oído afectado", an.lado.orEmpty()))
    sb.append(fila("Tiempo de evolución", an.tiempo))
    sb.append(fila("Antecedentes", listOfNotNull("exposición a ruido".takeIf { an.ruido == true }, "familiares con sordera".takeIf { an.familiares == true },
        "usó audífonos".takeIf { an.audifonosPrevios == true }, an.notas.takeIf { it.isNotBlank() }).joinToString(", ")))
    sb.append("<h2>Videotoscopia</h2>")
    Oido.entries.forEach { o -> val x = d.otoscopia.de(o); sb.append(fila(o.nombre, (x.hallazgos + listOfNotNull(x.nota.takeIf { it.isNotBlank() })).joinToString(", ").ifBlank { "Sin hallazgos registrados" })) }
    if (d.otoscopia.apto == false) sb.append(fila("No apto para audiometría", d.otoscopia.indicacion))
    if (hayAudiometria(d)) {
        sb.append("<h2>Audiometría tonal</h2><div class='g'>")
        Oido.entries.forEach { o -> sb.append("<div><div style='font-weight:bold;color:${if (o == Oido.OD) "#dc2626" else "#2563eb"}'>${o.nombre}</div>${svgAudiograma(o, d.aud(o))}</div>") }
        sb.append("</div>")
        val hayLogo = Oido.entries.any { d.log(it) != Logo() }
        if (hayLogo) {
            sb.append("<h2>Logoaudiometría</h2>")
            Oido.entries.forEach { o -> val l = d.log(o)
                sb.append(fila(o.nombre, listOfNotNull(l.srt?.let { "umbral de voz $it dB" }, l.discriminacion?.let { "discriminación $it %" }, l.intensidad?.let { "a $it dB" }).joinToString(" · "))) }
        }
        sb.append("<h2>Conclusión</h2>")
        Oido.entries.forEach { o -> sb.append(fila(o.nombre, fraseOido(d, o))) }
        sb.append(fila("Conclusión", d.conclusion.texto))
        sb.append(fila("Recomendación", d.conclusion.recomendacion))
    }
    if (d.demostracion.hecha) {
        sb.append("<h2>Demostración de audífonos</h2>")
        sb.append(fila("Modelo", d.demostracion.modelo)); sb.append(fila("Oído", d.demostracion.oido.orEmpty())); sb.append(fila("Resultado", d.demostracion.resultado))
    }
    sb.append("</body></html>")
    return sb.toString()
}

@Suppress("unused") private val colorNoUsado = Color.Unspecified
