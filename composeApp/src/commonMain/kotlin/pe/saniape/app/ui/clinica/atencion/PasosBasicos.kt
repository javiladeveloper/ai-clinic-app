package pe.saniape.app.ui.clinica.atencion

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.AtencionRepo
import pe.saniape.app.data.staff.ConsentimientoApp
import pe.saniape.app.data.staff.DatosConsultaApp
import pe.saniape.app.data.staff.FraseFrecuenteApp
import pe.saniape.app.data.staff.MEDICIONES_TRIAJE
import pe.saniape.app.data.staff.leerCamposTriaje
import pe.saniape.app.data.staff.requiereConsentimiento
import pe.saniape.app.ui.AccionesNativas
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.agenda.modales.VitalesCampos
import pe.saniape.app.ui.clinica.odontologia.recordarReconocedorVoz
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.theme.Sania

// ─────────────────────────────────────────────────────────────────────────────
// PASOS BÁSICOS de la consulta guiada: Motivo, Vitales, Examen y Procedimiento.
// Gemelos de los bloques de ConsultaGuiada.tsx (mismos textos) y del campo con
// frases rápidas (CampoConFrases.tsx). Todo escribe en el borrador del VM; el
// guardado (a mano o al cambiar de paso) lo hace AtencionViewModel.
// ─────────────────────────────────────────────────────────────────────────────

/** Paso "Motivo y anamnesis" (solo consulta). */
@Composable
internal fun PasoMotivo(vm: AtencionViewModel, d: DatosConsultaApp, soloLectura: Boolean) {
    val dental = d.flags.dental
    val t = vm.borrador.textos
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CampoTextoClinico(
            label = "Motivo de consulta",
            valor = t["motivo_consulta"].orEmpty(), onChange = { vm.texto("motivo_consulta", it) },
            frases = frasesDe(d, "motivo"), soloLectura = soloLectura, minLineas = 2,
            placeholder = if (dental) "Ej. Dolor dental al masticar" else "Ej. Dolor de garganta y fiebre",
        )
        CampoTextoClinico(
            label = if (dental) "Tiempo de enfermedad (opcional)" else "Tiempo de enfermedad",
            valor = t["tiempo_enfermedad"].orEmpty(), onChange = { vm.texto("tiempo_enfermedad", it) },
            frases = frasesDe(d, "tiempo"), soloLectura = soloLectura, minLineas = 1,
            placeholder = "Ej. 3 días · inicio brusco · curso progresivo",
        )
        CampoTextoClinico(
            label = if (dental) "Enfermedad actual" else "Relato / síntomas y signos principales",
            valor = t["relato"].orEmpty(), onChange = { vm.texto("relato", it) },
            frases = emptyList(), soloLectura = soloLectura, minLineas = 4,
            placeholder = "Relato cronológico…",
        )
        if (!dental) {
            CampoTextoClinico(
                label = "Funciones biológicas",
                valor = t["funciones_biologicas"].orEmpty(), onChange = { vm.texto("funciones_biologicas", it) },
                frases = emptyList(), soloLectura = soloLectura, minLineas = 1,
                placeholder = "Apetito, sed, sueño, orina, deposiciones",
            )
        }
    }
}

/**
 * Paso "Funciones vitales": las del triaje, editables. Muestra las mediciones
 * que toma la clínica ([leerCamposTriaje]) más la presión (la consulta siempre
 * la ofrece) y cualquier otra que ya tenga un valor guardado (no se esconde un
 * dato cargado).
 */
@Composable
internal fun PasoVitales(vm: AtencionViewModel, d: DatosConsultaApp, soloLectura: Boolean) {
    val c = Sania.colors
    val valores = vm.borrador.vitales
    val campos = remember(d.modulos.camposTriaje, valores) { camposVitalesConsulta(d.modulos.camposTriaje, valores) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            (if (d.atencion?.triaje_at != null) "Tomadas en el triaje; puedes corregirlas." else "Sin triaje: regístralas aquí.") +
                (if (d.flags.dental) " En odontología son opcionales." else ""),
            color = c.textoSuave, fontSize = 12.sp,
        )
        VitalesCampos(
            valores = valores,
            // Solo se mandan al VM las claves que cambiaron (cada vital() marca sucio).
            onChange = { m -> m.forEach { (k, v) -> if (valores[k].orEmpty() != v) vm.vital(k, v) } },
            edad = d.flags.edad,
            campos = campos,
            habilitado = !soloLectura,
        )
    }
}

/** Paso "Examen físico" / "Examen estomatológico" (solo consulta). */
@Composable
internal fun PasoExamen(vm: AtencionViewModel, d: DatosConsultaApp, soloLectura: Boolean) {
    val dental = d.flags.dental
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CampoTextoClinico(
            label = if (dental) "Examen clínico estomatológico (extra e intraoral)" else "Examen físico: general y regional",
            valor = vm.borrador.textos["examen_fisico"].orEmpty(), onChange = { vm.texto("examen_fisico", it) },
            frases = frasesDe(d, "examen"), soloLectura = soloLectura, minLineas = 6,
            placeholder = if (dental) "Ej. Tejidos blandos sin alteraciones; pieza 36 con cavidad profunda…"
            else "Ej. BEG, LOTEP. Orofaringe congestiva, amígdalas hipertróficas con exudado…",
            porLinea = true,
        )
        if (dental) {
            Text("Los hallazgos por pieza van en el 🦷 Odontograma (arriba).", color = Sania.colors.textoSuave, fontSize = 12.sp)
        }
    }
}

/**
 * Paso "Consentimiento y procedimiento" (cita de procedimiento): estado del
 * consentimiento con 🖨 Imprimir y ✍ Firmó / No aceptó, y la nota del
 * procedimiento realizado.
 */
@Composable
internal fun PasoProcedimiento(vm: AtencionViewModel, d: DatosConsultaApp, soloLectura: Boolean, acciones: AccionesNativas) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var imprimiendo by remember { mutableStateOf<String?>(null) }
    val cis = d.consentimientos.filter { it.estado != "Anulado" }
    // Regla de la web: lo pide el servicio si tiene alguna plantilla ACTIVA
    // (sin plantilla el servidor no emite nada ni avisa).
    val requiere = requiereConsentimiento(d.cita)

    fun imprimir(ci: ConsentimientoApp) {
        if (imprimiendo != null) return
        imprimiendo = ci.id
        scope.launch {
            val html = AtencionRepo.htmlImprimible("consentimiento", ci.id)
            imprimiendo = null
            if (html != null) acciones.abrirHtml(html, "Consentimiento informado")
            else Toaster.error("No se pudo abrir el consentimiento. Revisa tu conexión.")
        }
    }

    // En el scope del VM (vm.accionando = "firma:<id>"): cambiar de paso a mitad
    // del envío no lo corta ni rehabilita los botones.
    fun marcar(ci: ConsentimientoApp, resultado: String) {
        vm.lanzar("firma:${ci.id}") {
            val ok = vm.accionPlan { AtencionRepo.firmaConsentimiento(d.cita.id, ci.id, resultado) }
            if (ok) Toaster.exito(if (resultado == "Firmado") "Consentimiento firmado registrado" else "Negativa registrada")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            Text("📄 Consentimiento informado", color = c.navy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            when {
                cis.isEmpty() && !requiere -> Text(
                    "Este servicio no tiene plantilla de consentimiento asociada (Configuración → Historia clínica). " +
                        "Si el procedimiento la requiere, emítela desde la ficha.",
                    color = c.textoSuave, fontSize = 13.sp,
                )
                cis.isEmpty() -> Text(
                    "El consentimiento todavía no se generó. Vuelve a abrir la atención o emítelo desde la ficha.",
                    color = c.textoSuave, fontSize = 13.sp,
                )
                else -> cis.forEach { ci ->
                    FilaConsentimiento(
                        ci = ci,
                        puedeFirmar = ci.estado == "Pendiente" && !soloLectura,
                        ocupado = vm.accionando != null,
                        imprimiendo = imprimiendo == ci.id,
                        onImprimir = { imprimir(ci) },
                        onFirmo = { marcar(ci, "Firmado") },
                        onNoAcepto = { marcar(ci, "Rechazado") },
                    )
                }
            }
            if (cis.any { it.estado == "Pendiente" }) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Imprímelo, que lo firmen el paciente (con huella) y el profesional, y regístralo aquí antes de " +
                        "realizar el procedimiento. La foto del papel firmado se adjunta desde la ficha.",
                    color = c.pend, fontSize = 12.sp,
                )
            }
        }
        CampoTextoClinico(
            label = "Procedimiento realizado",
            valor = vm.borrador.textos["nota_procedimiento"].orEmpty(), onChange = { vm.texto("nota_procedimiento", it) },
            frases = emptyList(), soloLectura = soloLectura, minLineas = 6,
            placeholder = "Técnica, anestesia usada, hallazgos, material, complicaciones…",
        )
    }
}

/** Nombre de cada estado del consentimiento (NOMBRE_ESTADO_CI de la web). */
internal val NOMBRE_ESTADO_CI = mapOf(
    "Pendiente" to "Pendiente de firma",
    "Firmado" to "Firmado (aceptó)",
    "Rechazado" to "Rechazado (no aceptó)",
    "Revocado" to "Revocado",
    "Anulado" to "Anulado",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaConsentimiento(
    ci: ConsentimientoApp,
    puedeFirmar: Boolean,
    ocupado: Boolean,
    imprimiendo: Boolean,
    onImprimir: () -> Unit,
    onFirmo: () -> Unit,
    onNoAcepto: () -> Unit,
) {
    val c = Sania.colors
    val (fg, bg) = when (ci.estado) {
        "Pendiente" -> c.pend to c.pendBg
        "Firmado" -> c.ok to c.okBg
        "Rechazado" -> c.error to c.errorBg
        "Revocado" -> c.purple to c.purpleBg
        else -> c.textoSuave to c.chipBg
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(ci.procedimiento.ifBlank { "Procedimiento" }, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterVertically))
            Insignia(NOMBRE_ESTADO_CI[ci.estado] ?: ci.estado, fg, bg, Modifier.align(Alignment.CenterVertically))
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonChico(if (imprimiendo) "Abriendo…" else "🖨 Imprimir", c.navy, c.superficie, borde = c.borde,
                habilitado = !imprimiendo, onClick = onImprimir)
            if (puedeFirmar) {
                BotonChico("✍ Firmó (aceptó)", c.sobreNavy, c.ok, habilitado = !ocupado, onClick = onFirmo)
                BotonChico("No aceptó", c.textoSuave, c.superficie, borde = c.borde, habilitado = !ocupado, onClick = onNoAcepto)
            }
        }
    }
}

// ── Campo de texto clínico con frases rápidas y dictado ──────────────────────

/**
 * Dueño del dictado de la pantalla: el campo que tiene el micrófono (su token),
 * o null. Uno a la vez: dos reconocedores continuos se pelean el micrófono. Lo
 * provee [PantallaAtencion]; sin proveedor cada campo dicta por su cuenta.
 */
internal val LocalDictadoActivo = staticCompositionLocalOf<MutableState<Any?>?> { null }

/** Chips visibles antes de "+N más" (en móvil una lista larga empuja el formulario). */
private const val CHIPS_VISIBLES = 10

/**
 * Campo de texto de la atención (gemelo de CampoConFrases.tsx):
 *  - Chips debajo con las frases que aprendió la clínica: tocar uno AGREGA su
 *    frase al final (con coma, o en otra línea si [porLinea]); nunca reemplaza.
 *    Puesto = ✓; tocarlo otra vez lo quita.
 *  - 🎤 dicta con el reconocedor del sistema: cada frase terminada se AGREGA al
 *    final con un espacio. Un toque graba sin parar (manos libres); otro toque
 *    lo detiene.
 * El texto sigue siendo libre: el médico edita lo que quiera.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CampoTextoClinico(
    label: String,
    valor: String,
    onChange: (String) -> Unit,
    frases: List<FraseFrecuenteApp>,
    soloLectura: Boolean,
    minLineas: Int = 3,
    placeholder: String? = null,
    /** true = las frases van una por línea (examen, indicaciones); false = con coma. */
    porLinea: Boolean = false,
) {
    val c = Sania.colors
    // El dictado llega por callback: siempre sobre el valor y el onChange de AHORA.
    val valorActual by rememberUpdatedState(valor)
    val onChangeActual by rememberUpdatedState(onChange)
    var escuchando by remember { mutableStateOf(false) }
    var parcial by remember { mutableStateOf("") }
    var todos by remember { mutableStateOf(false) }
    // Un solo dictado por pantalla: este campo es dueño si su token es el activo.
    val dueno = LocalDictadoActivo.current
    val yo = remember { Any() }
    val esActivo = dueno == null || dueno.value === yo

    val dictado = recordarReconocedorVoz(
        onTexto = { t, final ->
            if (final) {
                parcial = ""
                if (t.isNotBlank()) onChangeActual(agregarDictado(valorActual, t))
            } else parcial = t
        },
        onEscuchando = {
            escuchando = it
            if (!it) {
                parcial = ""
                // Terminó (■, error o fin): suelta el micrófono si todavía era suyo.
                if (dueno != null && dueno.value === yo) dueno.value = null
            }
        },
        onError = { Toaster.error(it) },
    )
    // Si el campo pasa a solo lectura se deja de escuchar.
    LaunchedEffect(soloLectura) { if (soloLectura && escuchando) dictado.detener() }
    // Otro campo tomó el micrófono (o la pantalla lo soltó): este se detiene.
    LaunchedEffect(esActivo) { if (!esActivo && escuchando) dictado.detener() }
    // Al salir de la composición (cambio de paso, salir de la atención) se corta
    // y se libera el turno.
    DisposableEffect(Unit) {
        onDispose {
            if (escuchando) dictado.detener()
            if (dueno != null && dueno.value === yo) dueno.value = null
        }
    }

    Column(Modifier.fillMaxWidth()) {
        EtqForm(label)
        OutlinedTextField(
            value = valor,
            onValueChange = onChange,
            enabled = !soloLectura,
            minLines = minLineas,
            colors = coloresCampoClinico(),
            placeholder = placeholder?.let { p -> { Text(p, color = c.textoSuave, fontSize = 13.sp) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = c.texto),
            trailingIcon = if (!soloLectura && dictado.disponible) {
                {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape)
                            .background(if (escuchando) c.error else c.chipBg)
                            .clickable {
                                if (escuchando) dictado.detener()
                                else {
                                    dueno?.value = yo
                                    dictado.iniciarContinuo()
                                }
                            }
                            .semantics { contentDescription = if (escuchando) "Detener dictado" else "Dictar $label" },
                        contentAlignment = Alignment.Center,
                    ) { Text(if (escuchando) "■" else "🎤", fontSize = 15.sp, color = if (escuchando) c.sobreNavy else c.navy) }
                }
            } else null,
            modifier = Modifier.fillMaxWidth(),
        )
        if (escuchando) {
            Text(
                if (parcial.isBlank()) "🎙 Escuchando… toca ■ para terminar." else "🎙 $parcial",
                color = c.error, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (!soloLectura && frases.isNotEmpty()) {
            val visibles = if (todos) frases else frases.take(CHIPS_VISIBLES)
            val ocultos = frases.size - visibles.size
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                visibles.forEach { f ->
                    val puesto = fraseUsada(valor, f.texto)
                    ChipFrase(
                        texto = (if (puesto) "✓ " else "+ ") + etiquetaFrase(f.texto),
                        puesto = puesto,
                        onClick = { onChange(if (puesto) quitarFrase(valor, f.texto, porLinea) else insertarFrase(valor, f.texto, porLinea)) },
                    )
                }
                if (ocultos > 0) {
                    Box(
                        Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(Sania.shape.pill.dp))
                            .clickable { todos = true }.padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("+$ocultos más", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/**
 * Los colores del formulario, pero en solo lectura el texto se lee entero (el
 * gris apagado de Material es para campos inactivos, no para leer una HC).
 */
@Composable
private fun coloresCampoClinico(): TextFieldColors {
    val c = Sania.colors
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = c.texto, unfocusedTextColor = c.texto, disabledTextColor = c.texto,
        cursorColor = c.navy,
        focusedBorderColor = c.navy, unfocusedBorderColor = c.borde, disabledBorderColor = c.borde,
        focusedContainerColor = c.superficie, unfocusedContainerColor = c.superficie, disabledContainerColor = c.fondo,
    )
}

@Composable
private fun ChipFrase(texto: String, puesto: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.pill.dp)
    Box(
        Modifier.heightIn(min = 32.dp).widthIn(max = 300.dp).clip(forma)
            .background(if (puesto) c.navy else c.chipBg)
            .border(1.dp, if (puesto) c.navy else c.borde, forma)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(texto, color = if (puesto) c.sobreNavy else c.navy, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun Insignia(texto: String, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg).padding(horizontal = 9.dp, vertical = 3.dp)) {
        Text(texto, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun BotonChico(
    texto: String, fg: Color, bg: Color, borde: Color? = null, habilitado: Boolean = true, onClick: () -> Unit,
) {
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    var m = Modifier.heightIn(min = 36.dp).clip(forma).background(if (habilitado) bg else bg.copy(alpha = 0.5f))
    if (borde != null) m = m.border(1.dp, borde, forma)
    Box(
        m.clickable(enabled = habilitado, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

// ── Puras (gemelas de lib/frases-clinicas.ts) ────────────────────────────────

/**
 * Frases aprendidas de un campo, las más usadas primero. Odontología guarda
 * aparte con prefijo (`odonto_motivo`), como `claveFrase` de la web.
 */
internal fun frasesDe(d: DatosConsultaApp, campo: String): List<FraseFrecuenteApp> {
    val clave = if (d.flags.dental) "odonto_$campo" else campo
    return d.frases.filter { it.campo == clave && it.texto.isNotBlank() }
        .distinctBy { normalizarFrase(it.texto) }
        .sortedByDescending { it.usos }
}

/**
 * Mediciones a mostrar en el paso de vitales: las de la clínica + la presión +
 * las que ya tienen valor, en el orden de siempre.
 */
internal fun camposVitalesConsulta(camposClinica: List<String>, valores: Map<String, String>): List<String> {
    val base = leerCamposTriaje(camposClinica.takeIf { it.isNotEmpty() }).toMutableSet()
    base += "presion"
    for (m in MEDICIONES_TRIAJE) {
        val columnas = if (m == "presion") listOf("presion_sistolica", "presion_diastolica") else listOf(m)
        if (columnas.any { !valores[it].isNullOrBlank() }) base += m
    }
    return MEDICIONES_TRIAJE.filter { it in base }
}

/** Texto del chip: la primera línea de la frase (un bloque puede tener varias). */
internal fun etiquetaFrase(texto: String): String {
    val lineas = lineasDe(texto)
    val primera = lineas.firstOrNull() ?: texto.trim()
    return if (lineas.size > 1) "$primera…" else primera
}

/** Lo dictado se suma al final con un espacio (nunca reemplaza). */
internal fun agregarDictado(texto: String, dictado: String): String {
    val d = dictado.trim()
    if (d.isEmpty()) return texto
    val base = texto.trimEnd()
    return if (base.isEmpty()) d.replaceFirstChar { it.uppercase() } else "$base $d"
}

private val TILDES = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'à' to 'a', 'è' to 'e', 'ì' to 'i', 'ò' to 'o', 'ù' to 'u')

/** Sin tildes, minúsculas, espacios colapsados y sin puntuación al final. */
internal fun normalizarFrase(s: String): String =
    s.lowercase().map { TILDES[it] ?: it }.joinToString("")
        .replace(Regex("\\s+"), " ").trim().replace(Regex("[\\s.,;:]+$"), "")

private fun lineasDe(texto: String): List<String> = texto.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

/** ¿El chip ya está en el texto? (un bloque, si están todas sus líneas). */
internal fun fraseUsada(texto: String, frase: String): Boolean {
    val t = normalizarFrase(texto)
    if (t.isEmpty()) return false
    val partes = lineasDe(frase)
    return partes.isNotEmpty() && partes.all { t.contains(normalizarFrase(it)) }
}

private val MAYUSCULA = Regex("[A-ZÁÉÍÓÚÑ]")

/** Minúscula inicial al encadenar con coma ("Fiebre, tos"), salvo siglas ("PA", "ATM"). */
private fun enMinuscula(f: String): String {
    if (f.length > 1 && MAYUSCULA.matches(f[1].toString())) return f
    return f.replaceFirstChar { it.lowercase() }
}

private fun enMayuscula(f: String): String = f.replaceFirstChar { it.uppercase() }

/**
 * Agrega la frase AL FINAL con su separador (coma o salto de línea). Un bloque
 * de varias líneas agrega solo las que faltan. Nunca reemplaza.
 */
internal fun insertarFrase(texto: String, frase: String, porLinea: Boolean): String {
    var r = texto
    for (p in lineasDe(frase).filter { !fraseUsada(texto, it) }) {
        val base = r.trimEnd()
        r = when {
            base.isEmpty() -> if (porLinea) p else enMayuscula(p)
            porLinea -> "$base\n$p"
            base.endsWith(",") || base.endsWith(";") -> "$base ${enMinuscula(p)}"
            base.endsWith(".") -> "$base $p"
            else -> "$base, ${enMinuscula(p)}"
        }
    }
    return r
}

/**
 * Quita la frase (deshacer un chip): solo si está escrita tal cual (sin
 * distinguir mayúsculas); si el médico ya la editó, no toca nada. También quita
 * el separador que la acompañaba.
 */
internal fun quitarFrase(texto: String, frase: String, porLinea: Boolean): String {
    var r = texto
    for (p in lineasDe(frase)) {
        val i = r.lowercase().indexOf(p.lowercase())
        if (i < 0) continue
        val antes = r.substring(0, i)
        val despues = r.substring(i + p.length)
        r = if (porLinea) {
            val a = antes.replace(Regex("\\n?[ \\t]*$"), "")
            val d = despues.replace(Regex("^[ \\t]*\\n?"), "")
            (a + (if (antes.isNotBlank() && despues.isNotBlank()) "\n" else "") + d).trim()
        } else {
            val a = antes.replace(Regex("[,;]?\\s*$"), "")
            val d = despues.replace(Regex("^\\s*[,;]?\\s*"), "")
            when {
                a.isNotEmpty() && d.isNotEmpty() -> "$a, $d"
                a.isNotEmpty() -> a
                else -> enMayuscula(d)
            }
        }
    }
    return r
}
