package pe.saniape.app.ui.clinica.certificados

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import pe.saniape.app.data.staff.BorradorCertificado
import pe.saniape.app.data.staff.CLAVES_FINALIDAD
import pe.saniape.app.data.staff.CONCLUSION_BUENA
import pe.saniape.app.data.staff.CONCLUSION_HALLAZGOS
import pe.saniape.app.data.staff.CertificadoRepo
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.EstadoHallazgo
import pe.saniape.app.data.staff.FINALIDADES
import pe.saniape.app.data.staff.HALLAZGOS
import pe.saniape.app.data.staff.ICONO_CERTIFICADO
import pe.saniape.app.data.staff.MAX_DIAS_ATRAS_CERTIFICADO
import pe.saniape.app.data.staff.NOMBRE_CERTIFICADO
import pe.saniape.app.data.staff.OpcionesCertificado
import pe.saniape.app.data.staff.PediatriaRepo
import pe.saniape.app.data.staff.PlantillaCertificado
import pe.saniape.app.data.staff.PrefillCertificado
import pe.saniape.app.data.staff.aPlantilla
import pe.saniape.app.data.staff.armarTexto
import pe.saniape.app.data.staff.cuerpoEmitirCertificado
import pe.saniape.app.data.staff.edadDe
import pe.saniape.app.data.staff.examenMentalEnLinea
import pe.saniape.app.data.staff.formatearNumeroCertificado
import pe.saniape.app.data.staff.lineaFinalidad
import pe.saniape.app.data.staff.opcionesPorDefecto
import pe.saniape.app.data.staff.piezasCertificado
import pe.saniape.app.data.staff.plantillaUsaCasillas
import pe.saniape.app.data.staff.requiereConfirmarConclusion
import pe.saniape.app.data.staff.restarDias
import pe.saniape.app.data.staff.validarCertificado
import pe.saniape.app.data.staff.visiblePorDefecto
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.atencion.CajaAviso
import pe.saniape.app.ui.clinica.atencion.CampoDialogo
import pe.saniape.app.ui.clinica.atencion.SelectorDialogo
import pe.saniape.app.ui.clinica.atencion.nuevaClaveCliente
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoFecha
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.fechaDMA
import pe.saniape.app.ui.theme.Sania

// ─────────────────────────────────────────────────────────────────────────────
// 🧠 "Certificado de salud mental": el formulario para EMITIRLO — gemelo de
// components/certificados/CertificadoSaludMentalForm.tsx. La parte clínica son
// CASILLAS (una por hallazgo, la normal marcada): con todo en normal el texto es
// IDÉNTICO al de los psiquiatras; con un toque se marca una alternativa o se
// edita el párrafo a mano. "Buena salud mental" con hallazgos alterados exige
// confirmación (también en el servidor y en la base). Emitido no se edita: se
// anula y se emite otro. Reglas puras: data/staff/CertificadoSaludMental.kt.
// ─────────────────────────────────────────────────────────────────────────────

/** La vista previa de "mi texto predeterminado" (texto con marcadores; ¿es el de la clínica?). */
private data class PlantillaEnEdicion(val texto: String, val deClinica: Boolean)

/**
 * [citaId]/[atencionId]/[tratamientoId]: desde el cierre de la consulta (el
 * examen mental de esa atención, su sede y especialidad). [onEmitido] recibe el
 * id y el número ("CSM N° 000001") para abrir la hoja.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DialogoCertificadoSaludMental(
    ctx: ContextoStaff,
    pacienteId: String,
    pacienteNombre: String,
    citaId: String? = null,
    atencionId: String? = null,
    tratamientoId: String? = null,
    onCancelar: () -> Unit,
    onEmitido: (id: String, numeroTexto: String) -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val esAdmin = ctx.esAdmin

    var datos by remember { mutableStateOf<PrefillCertificado?>(null) }
    var errorCarga by remember { mutableStateOf<String?>(null) }
    var terapeutaId by remember { mutableStateOf("") }
    var fecha by remember { mutableStateOf("") }
    var lugar by remember { mutableStateOf("") }
    var op by remember { mutableStateOf(opcionesPorDefecto()) }
    var incluirEm by remember { mutableStateOf(false) }
    // Edición a mano: las casillas dejan de mandar hasta "↺ volver a las casillas".
    var manual by remember { mutableStateOf<String?>(null) }
    var confirmarVolver by remember { mutableStateOf(false) }
    var confirma by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(true) }
    // Si el profesional ya decidió la visibilidad, las casillas no la cambian.
    var visibleTocado by remember { mutableStateOf(false) }
    // Género para el texto ('F' | 'M' | null = el original, "identificado"). Se guarda en la ficha al emitir.
    var sexo by remember { mutableStateOf<String?>(null) }
    var errores by remember { mutableStateOf<List<String>>(emptyList()) }
    var emitiendo by remember { mutableStateOf(false) }
    // Una apertura = una clave: un reintento tras un corte devuelve el ya emitido (no gasta otro número).
    val clave = remember { nuevaClaveCliente() }
    var plantillaEnEdicion by remember { mutableStateOf<PlantillaEnEdicion?>(null) }
    var guardandoPlantilla by remember { mutableStateOf(false) }
    var calendario by remember { mutableStateOf(false) }

    LaunchedEffect(pacienteId, citaId) {
        when (val r = CertificadoRepo.prefill(pacienteId, citaId)) {
            is CertificadoRepo.Prefill.Ok -> {
                val d = r.datos
                datos = d
                sexo = d.paciente.sexo
                fecha = d.hoy
                lugar = d.lugar.orEmpty()
                val ter = d.terapeutaId.orEmpty()
                terapeutaId = ter
                op = opcionesPorDefecto(d.firmantes.firstOrNull { it.id == ter }?.suscribe ?: "El")
            }
            CertificadoRepo.Prefill.NoDisponible -> errorCarga = "El certificado de salud mental todavía no está disponible. Actualiza más tarde."
            is CertificadoRepo.Prefill.Error -> errorCarga = r.mensaje
        }
    }

    val d = datos
    val firmante = d?.firmantes?.firstOrNull { it.id == terapeutaId }
    // Plantilla vigente: la del firmante → la de la clínica → la de fábrica.
    val propia = d?.plantillas?.firstOrNull { !it.terapeutaId.isNullOrEmpty() && it.terapeutaId == terapeutaId }
    val deClinica = d?.plantillas?.firstOrNull { it.terapeutaId.isNullOrEmpty() }
    val plantilla = propia?.texto ?: deClinica?.texto
    val usaCasillas = plantillaUsaCasillas(plantilla)
    val examenMental = d?.examenMental
    val opciones: OpcionesCertificado = op.copy(examenMental = if (incluirEm) examenMental else null)
    // La edad AL DÍA del certificado (cambiar la fecha cambia la edad) y el género elegido.
    val pacienteTexto = d?.paciente?.copy(
        sexo = sexo,
        edad = if (d.fechaNacimiento != null && fecha.isNotBlank()) edadDe(d.fechaNacimiento, d.paciente.edad, fecha) else d.paciente.edad,
    )
    val armado = pacienteTexto?.let { armarTexto(plantilla, it, opciones) }.orEmpty()
    val texto = manual ?: armado
    val pideConfirmar = requiereConfirmarConclusion(texto, op.hallazgos)
    // Una plantilla sin [CONCLUSION] abre como texto a mano: las casillas no la cambian.
    val casillasActivas = usaCasillas && manual == null
    // ¿De quién es el texto predeterminado que se guardaría?
    val esAjeno = esAdmin && terapeutaId.isNotBlank() && terapeutaId != ctx.miTerapeutaId

    fun cambiarFirmante(id: String) {
        terapeutaId = id
        d?.firmantes?.firstOrNull { it.id == id }?.let { f -> op = op.copy(suscribe = f.suscribe) }
    }

    fun marcarHallazgo(k: String, incluido: Boolean? = null, opcion: String? = null) {
        val previo = op.hallazgos[k] ?: EstadoHallazgo(true, "normal")
        val hallazgos = op.hallazgos + (k to EstadoHallazgo(incluido ?: previo.incluido, opcion ?: previo.opcion))
        op = op.copy(hallazgos = hallazgos)
        confirma = false
        // Con hallazgos alterados, "visible para el paciente" se apaga (si nadie lo tocó).
        if (!visibleTocado) visible = visiblePorDefecto(hallazgos)
    }

    fun guardarPlantilla() {
        val pl = plantillaEnEdicion ?: return
        if (guardandoPlantilla) return
        val destino = if (pl.deClinica) null else terapeutaId.ifBlank { null }
        if (!pl.deClinica && destino == null) { Toaster.error("Elige primero quién firma."); return }
        guardandoPlantilla = true
        scope.launch {
            try {
                val r = CertificadoRepo.guardarPlantilla(destino, pl.texto)
                if (!r.registrada) { Toaster.error(r.rechazo?.error ?: "No se pudo guardar"); return@launch }
                Toaster.exito(
                    when {
                        pl.deClinica -> "Texto predeterminado de la clínica guardado"
                        esAjeno -> "Texto predeterminado de ${firmante?.nombre ?: "el profesional"} guardado"
                        else -> "Tu texto predeterminado quedó guardado"
                    },
                )
                datos = datos?.let { x ->
                    x.copy(plantillas = x.plantillas.filter { (it.terapeutaId?.ifEmpty { null }) != destino } +
                        PlantillaCertificado(destino, pl.texto.trim(), null))
                }
                plantillaEnEdicion = null
                manual = null
            } finally {
                guardandoPlantilla = false
            }
        }
    }

    fun restablecerPlantilla() {
        val p = propia ?: return
        if (guardandoPlantilla) return
        guardandoPlantilla = true
        scope.launch {
            try {
                val r = CertificadoRepo.restablecerPlantilla(p.terapeutaId)
                if (!r.registrada) { Toaster.error(r.rechazo?.error ?: "No se pudo restablecer"); return@launch }
                datos = datos?.let { x -> x.copy(plantillas = x.plantillas.filter { it.terapeutaId != p.terapeutaId }) }
                manual = null
                Toaster.exito("Volviste al texto predeterminado")
            } finally {
                guardandoPlantilla = false
            }
        }
    }

    fun emitir() {
        val dd = datos ?: return
        if (emitiendo) return
        val b = BorradorCertificado(
            pacienteId = pacienteId, terapeutaId = terapeutaId.ifBlank { null }, fecha = fecha, texto = texto,
            lugar = lugar, finalidad = lineaFinalidad(op.finalidad, op.finalidadOtra),
            hallazgos = op.hallazgos, textoManual = manual != null, confirmaConclusion = confirma,
            visiblePaciente = visible,
            especialidadId = firmante?.especialidadId,
            citaId = citaId, atencionId = atencionId ?: dd.atencionId, tratamientoId = tratamientoId ?: dd.citaTratamientoId,
        )
        val errs = validarCertificado(b, dd.hoy)
        errores = errs
        if (errs.isNotEmpty()) return
        emitiendo = true
        scope.launch {
            try {
                // El género elegido va a la ficha ANTES de emitir (el certificado congela el de la
                // ficha). Si no se puede (sin permiso), se emite igual: el texto ya lo lleva.
                val s = sexo
                if (s != null && s != dd.paciente.sexo) {
                    val rs = PediatriaRepo.actualizarFicha(pacienteId, sexo = s, tocarSexo = true)
                    if (!rs.registrada) Toaster.error("No se pudo guardar el sexo en la ficha; el certificado sale igual.")
                    else datos = datos?.let { x -> x.copy(paciente = x.paciente.copy(sexo = s)) }
                }
                val r = CertificadoRepo.emitir(cuerpoEmitirCertificado(b, clave))
                if (r.registrada) {
                    val cert = r.cuerpo?.get("certificado") as? JsonObject
                    val id = (cert?.get("id") as? JsonPrimitive)?.contentOrNull.orEmpty()
                    val numero = formatearNumeroCertificado((cert?.get("numero") as? JsonPrimitive)?.intOrNull)
                    val repetido = (r.cuerpo?.get("repetido") as? JsonPrimitive)?.booleanOrNull == true
                    Toaster.exito(if (repetido) "Ya se había emitido: $numero" else "$NOMBRE_CERTIFICADO emitido · $numero")
                    onEmitido(id, numero)
                } else {
                    val lista = r.rechazo?.error?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() }
                        ?.ifEmpty { null } ?: listOf("No se pudo emitir el certificado.")
                    errores = lista
                    Toaster.error(lista.first())
                }
            } finally {
                emitiendo = false
            }
        }
    }

    DialogoForm(
        titulo = "$ICONO_CERTIFICADO $NOMBRE_CERTIFICADO",
        subtitulo = pacienteNombre,
        textoAccion = if (emitiendo) "Emitiendo…" else "Emitir e imprimir",
        accionHabilitada = d != null && !emitiendo && terapeutaId.isNotBlank() && !(pideConfirmar && !confirma),
        onCancelar = { if (!emitiendo) onCancelar() },
        onAccion = ::emitir,
    ) {
        val carga = errorCarga
        when {
            carga != null -> CajaAviso(carga, c.error, c.errorBg)
            d == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp)
                Text("Preparando el certificado…", color = c.textoSuave, fontSize = 13.sp)
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (errores.isNotEmpty()) CajaAviso(errores.joinToString("\n") { "• $it" }, c.error, c.errorBg)

                // ── Quién firma ──
                Column {
                    EtqForm("Profesional que firma y sella")
                    when {
                        d.firmantes.isEmpty() -> Text(
                            if (esAdmin) "Ningún psiquiatra o psicólogo tiene N° de colegiatura: complétalo en Equipo."
                            else "Solo un psiquiatra o psicólogo con N° de colegiatura firma el certificado: revisa tu ficha en Equipo.",
                            color = c.error, fontSize = 13.sp,
                        )
                        // El profesional firma solo a su nombre (el servidor también lo exige: PROFESIONAL_AJENO).
                        !esAdmin && !ctx.miTerapeutaId.isNullOrBlank() ->
                            CajaFijaCert(firmante?.let { "${it.nombre} · ${it.colegiatura}" } ?: "—")
                        else -> SelectorDialogo("", terapeutaId, d.firmantes.map { it.id to "${it.nombre} · ${it.colegiatura}" }) {
                            cambiarFirmante(it)
                        }
                    }
                }

                // ── Fecha y lugar ──
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        EtqForm("Fecha")
                        CajaSelectorForm("📅 ${fechaDMA(fecha)}") { calendario = true }
                    }
                    CampoDialogo("Lugar", lugar, { lugar = it.take(80) }, "Tacna", modifier = Modifier.weight(1f))
                }
                Text("Entre hoy y $MAX_DIAS_ATRAS_CERTIFICADO días atrás. La edad del texto se calcula a esa fecha.",
                    color = c.textoSuave, fontSize = 11.sp)

                // ── Motivo (chips de un toque) ──
                Column {
                    EtqForm("Motivo")
                    if (manual != null) {
                        Text("Texto editado a mano: el motivo solo queda en la lista de documentos; el párrafo no cambia.",
                            color = c.pend, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CLAVES_FINALIDAD.forEach { f ->
                            ChipCert(FINALIDADES.getValue(f).chip, activo = op.finalidad == f) { op = op.copy(finalidad = f) }
                        }
                    }
                    if (op.finalidad == "otro") {
                        CampoDialogo("Para qué es el certificado", op.finalidadOtra.orEmpty(),
                            { op = op.copy(finalidadOtra = it.take(160)) }, "Para… (ej. el trámite de adopción)",
                            modifier = Modifier.padding(top = 8.dp))
                    }
                }

                // ── Encabezado y paciente (en texto a mano no cambian el párrafo) ──
                Column(Modifier.alpha(if (manual != null) 0.5f else 1f)) {
                    EtqForm("Encabezado y paciente" + if (manual != null) " (texto editado a mano)" else "")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("El", "La").forEach { s ->
                            ChipCert("$s que suscribe", activo = op.suscribe == s, habilitado = manual == null) { op = op.copy(suscribe = s) }
                        }
                        listOf("M" to "Paciente masculino", "F" to "Paciente femenino").forEach { (v, etq) ->
                            ChipCert(etq, activo = sexo == v, habilitado = manual == null) { sexo = if (sexo == v) null else v }
                        }
                    }
                    Text("El sexo elegido se guarda en la ficha del paciente.", color = c.textoSuave, fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp))
                }

                // ── Casillas del examen ──
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
                        .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Examen de salud mental", color = c.navy, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            !usaCasillas -> "Tu texto predeterminado no usa las casillas: edita el párrafo directamente."
                            manual != null -> "Texto editado a mano: las casillas no lo cambian."
                            else -> "Todo en normal = el texto de siempre. Desmarca para quitar una frase o elige la alternativa."
                        },
                        color = c.textoSuave, fontSize = 12.sp,
                    )
                    Column(Modifier.alpha(if (casillasActivas) 1f else 0.5f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HALLAZGOS.forEach { h ->
                            val est = op.hallazgos[h.clave] ?: EstadoHallazgo(true, "normal")
                            Column {
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                                        .clickable(enabled = casillasActivas) { marcarHallazgo(h.clave, incluido = !est.incluido) },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        est.incluido, { marcarHallazgo(h.clave, incluido = it) }, enabled = casillasActivas,
                                        colors = CheckboxDefaults.colors(checkedColor = c.navy),
                                    )
                                    Text(h.titulo, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                FlowRow(
                                    Modifier.padding(start = 8.dp).alpha(if (est.incluido) 1f else 0.5f),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    (listOf(h.normal) + h.alternativas).forEach { o ->
                                        val activo = est.incluido && est.opcion == o.clave
                                        ChipCert(
                                            if (o.clave == "normal") "✓ ${o.etiqueta}" else o.etiqueta,
                                            activo = activo, alterno = activo && o.clave != "normal", habilitado = casillasActivas,
                                        ) { marcarHallazgo(h.clave, incluido = true, opcion = o.clave) }
                                    }
                                }
                            }
                        }
                    }
                    if (examenMental != null) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg)
                                .clickable(enabled = casillasActivas) { incluirEm = !incluirEm }.padding(end = 10.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Checkbox(incluirEm, { incluirEm = it }, enabled = casillasActivas, colors = CheckboxDefaults.colors(checkedColor = c.navy))
                            Column(Modifier.padding(top = 12.dp)) {
                                Text("Incluir el examen mental de hoy", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(examenMentalEnLinea(examenMental).orEmpty(), color = c.textoSuave, fontSize = 12.sp)
                            }
                        }
                    }
                    // Conclusión
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChipCert("Goza de buena salud mental", activo = op.conclusion == CONCLUSION_BUENA, habilitado = casillasActivas) {
                            op = op.copy(conclusion = CONCLUSION_BUENA); confirma = false
                        }
                        ChipCert("Describir los hallazgos", activo = op.conclusion == CONCLUSION_HALLAZGOS, habilitado = casillasActivas) {
                            op = op.copy(conclusion = CONCLUSION_HALLAZGOS)
                        }
                    }
                }

                // ── Coherencia: "buena salud mental" con hallazgos alterados ──
                if (pideConfirmar) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.pendBg)
                            .border(1.dp, c.pend, RoundedCornerShape(Sania.shape.sm.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("⚠ Marcaste hallazgos alterados y el texto dice \"buena salud mental\".",
                            color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("La conclusión sugerida describe los hallazgos sin afirmar buena salud mental (\"se evidencia que…\").",
                            color = c.texto, fontSize = 12.sp)
                        if (casillasActivas) {
                            BotonCert("Usar la conclusión sugerida", primario = true) { op = op.copy(conclusion = CONCLUSION_HALLAZGOS) }
                        }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable { confirma = !confirma },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(confirma, { confirma = it }, colors = CheckboxDefaults.colors(checkedColor = c.navy))
                            Text("Confirmo que goza de buena salud mental pese a estos hallazgos", color = c.texto, fontSize = 13.sp)
                        }
                    }
                }

                // ── El párrafo, siempre editable ──
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { EtqForm("Texto del certificado") }
                        if (manual != null && usaCasillas && !confirmarVolver) {
                            Text("↺ Volver a las casillas", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable { confirmarVolver = true }
                                    .padding(horizontal = 6.dp, vertical = 8.dp))
                        }
                    }
                    if (confirmarVolver) {
                        Column(
                            Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Se pierde lo que escribiste a mano.", color = c.texto, fontSize = 13.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                BotonCert("Sí, volver a las casillas", primario = false) { manual = null; confirmarVolver = false }
                                BotonCert("Cancelar", primario = false, suave = true) { confirmarVolver = false }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = texto,
                        onValueChange = { manual = it.take(5000); confirma = false },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 8,
                        textStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
                        colors = coloresCampoForm(),
                        shape = RoundedCornerShape(Sania.shape.sm.dp),
                    )
                    FlowRow(
                        Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        EnlaceCert(
                            "💾 " + if (esAjeno) "Guardar como predeterminado de ${firmante?.nombre ?: "el profesional"}"
                            else "Guardar como mi texto predeterminado",
                        ) {
                            pacienteTexto?.let { pt ->
                                plantillaEnEdicion = PlantillaEnEdicion(aPlantilla(texto, piezasCertificado(pt, opciones)), deClinica = false)
                            }
                        }
                        if (propia != null) {
                            EnlaceCert("Volver al texto ${if (deClinica != null) "de la clínica" else "de fábrica"}", suave = true,
                                habilitado = !guardandoPlantilla) { restablecerPlantilla() }
                        }
                    }
                }

                // ── Vista previa de "mi texto predeterminado" ──
                plantillaEnEdicion?.let { pl ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
                            .border(1.dp, c.navyLight, RoundedCornerShape(Sania.shape.md.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Así queda tu texto predeterminado. Los datos de cada paciente van entre corchetes ([NOMBRE], " +
                                "[IDENTIFICACION], [EDAD]…) y [CONCLUSION] es lo que arman las casillas. Lo que escribiste a mano queda tal cual.",
                            color = c.texto, fontSize = 12.sp,
                        )
                        OutlinedTextField(
                            value = pl.texto,
                            onValueChange = { v -> plantillaEnEdicion = pl.copy(texto = v.take(5000)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 6,
                            textStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            colors = coloresCampoForm(),
                            shape = RoundedCornerShape(Sania.shape.sm.dp),
                        )
                        if (esAjeno && !pl.deClinica) {
                            Text("⚠ Vas a cambiar el texto predeterminado de ${firmante?.nombre ?: "otro profesional"}, no el tuyo.",
                                color = c.pend, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        if (esAdmin) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                                    .clickable { plantillaEnEdicion = pl.copy(deClinica = !pl.deClinica) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(pl.deClinica, { v -> plantillaEnEdicion = pl.copy(deClinica = v) },
                                    colors = CheckboxDefaults.colors(checkedColor = c.navy))
                                Text("Para toda la clínica (quien no tenga uno propio)", color = c.texto, fontSize = 13.sp)
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            BotonCert(if (guardandoPlantilla) "Guardando…" else "Guardar", primario = true, habilitado = !guardandoPlantilla) {
                                guardarPlantilla()
                            }
                            BotonCert("Cancelar", primario = false, suave = true, habilitado = !guardandoPlantilla) { plantillaEnEdicion = null }
                        }
                    }
                }

                // ── ¿Lo ve el paciente en su app? ──
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                        .clickable { visible = !visible; visibleTocado = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("📱 Visible para el paciente en su app", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (visiblePorDefecto(op.hallazgos)) "Encendido: el certificado es para que lo entregue. Puedes cambiarlo después desde la ficha."
                            else "Apagado porque marcaste hallazgos alterados: enciéndelo solo si se lo dejas al paciente.",
                            color = c.textoSuave, fontSize = 11.sp,
                        )
                    }
                    Switch(
                        checked = visible, onCheckedChange = { visible = it; visibleTocado = true },
                        colors = SwitchDefaults.colors(checkedTrackColor = c.navy, checkedThumbColor = c.sobreNavy),
                    )
                }
                Text("Emitido no se edita: si sale mal, se anula y se emite otro.", color = c.textoSuave, fontSize = 11.sp)
            }
        }
    }

    if (calendario && d != null) {
        DialogoFecha(
            inicial = fecha,
            onCerrar = { calendario = false },
            onElegir = { f ->
                if (f <= d.hoy && f >= restarDias(d.hoy, MAX_DIAS_ATRAS_CERTIFICADO)) fecha = f
                else Toaster.error("La fecha debe estar entre hoy y $MAX_DIAS_ATRAS_CERTIFICADO días atrás.")
            },
        )
    }
}

// ── Piezas ───────────────────────────────────────────────────────────────────

/** Chip de un toque. [alterno] = una alternativa marcada (ámbar, como la web). */
@Composable
internal fun ChipCert(
    texto: String,
    activo: Boolean,
    alterno: Boolean = false,
    habilitado: Boolean = true,
    onClick: () -> Unit,
) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.pill.dp)
    val (fondo, borde, color) = when {
        alterno -> Triple(c.pendBg, c.pend, c.pend)
        activo -> Triple(c.navy, c.navy, c.sobreNavy)
        else -> Triple(c.superficie, c.borde, c.texto)
    }
    Box(
        Modifier.heightIn(min = 36.dp).clip(forma).background(fondo).border(1.dp, borde, forma)
            .clickable(enabled = habilitado, onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

@Composable
internal fun BotonCert(texto: String, primario: Boolean, suave: Boolean = false, habilitado: Boolean = true, onClick: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    Box(
        Modifier.heightIn(min = 38.dp).clip(forma)
            .background(if (primario) (if (habilitado) c.navy else c.navy.copy(alpha = 0.5f)) else c.superficie)
            .border(1.dp, if (primario) c.navy else if (suave) c.borde else c.navy, forma)
            .clickable(enabled = habilitado, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = if (primario) c.sobreNavy else if (suave) c.textoSuave else c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EnlaceCert(texto: String, suave: Boolean = false, habilitado: Boolean = true, onClick: () -> Unit) {
    val c = Sania.colors
    Text(
        texto, color = if (suave) c.textoSuave else c.navy, fontSize = 12.sp,
        fontWeight = if (suave) FontWeight.Normal else FontWeight.Bold, textDecoration = TextDecoration.Underline,
        modifier = Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
            .clickable(enabled = habilitado, onClick = onClick).padding(vertical = 9.dp),
    )
}

@Composable
private fun CajaFijaCert(texto: String) {
    val c = Sania.colors
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.fondo)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(horizontal = 12.dp, vertical = 13.dp),
    ) { Text(texto, color = c.texto, fontSize = 14.sp) }
}
