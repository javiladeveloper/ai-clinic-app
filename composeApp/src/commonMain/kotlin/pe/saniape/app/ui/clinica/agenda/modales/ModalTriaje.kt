package pe.saniape.app.ui.clinica.agenda.modales

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.AlertaVital
import pe.saniape.app.data.staff.AtencionMedicaRepo
import pe.saniape.app.data.staff.CitaStaff
import pe.saniape.app.data.staff.DatosTriaje
import pe.saniape.app.data.staff.NivelAlerta
import pe.saniape.app.data.staff.aNumero
import pe.saniape.app.data.staff.alertaPresion
import pe.saniape.app.data.staff.alertaVital
import pe.saniape.app.data.staff.calcularImc
import pe.saniape.app.data.staff.clasificarImc
import pe.saniape.app.data.staff.decimal
import pe.saniape.app.data.staff.fijo
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.rangoDe
import pe.saniape.app.data.staff.tallaEnCm
import pe.saniape.app.data.staff.validarTriaje
import pe.saniape.app.data.staff.valorFueraDeRango
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.theme.Sania

/**
 * "🩺 Triaje" (gemelo de TriajeModal + VitalesCampos de la web): enfermería,
 * recepción o el propio profesional toma los signos vitales y medidas ANTES de
 * la atención. Muestra SOLO las mediciones que eligió la clínica ([campos]),
 * con su unidad, el IMC en vivo y la lectura por color (umbrales de adulto).
 *
 * Precarga el triaje que ya tuviera la cita (se puede corregir). Sin señal el
 * formulario abre vacío y se guarda igual: la cola lo sube al volver la señal.
 * [onGuardar] recibe los valores ya validados y un callback para mostrar aquí
 * el "no" del servidor sin cerrar el formulario.
 */
@Composable
fun ModalTriaje(
    cita: CitaStaff,
    campos: List<String>,
    /** La cita va por el flujo médico: el texto habla de la NTS 139. */
    flujoMedico: Boolean,
    guardando: Boolean,
    onCancelar: () -> Unit,
    onGuardar: (valores: Map<String, String>, motivo: String, onRechazo: (String) -> Unit) -> Unit,
) {
    val c = Sania.colors
    var datos by remember(cita.id) { mutableStateOf<DatosTriaje?>(null) }
    var valores by remember(cita.id) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var motivo by remember(cita.id) { mutableStateOf("") }
    var errores by remember(cita.id) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(cita.id) {
        val d = AtencionMedicaRepo.datosTriaje(cita.id, cita.pacienteId, hoyClinicaIso())
        datos = d
        valores = d.valores
        motivo = d.motivo.orEmpty()
    }
    val d = datos

    DialogoForm(
        titulo = "🩺 Triaje",
        subtitulo = cita.pacienteNombre ?: "Paciente",
        textoAccion = if (guardando) "Guardando…" else "Guardar triaje",
        accionHabilitada = !guardando && d != null,
        onCancelar = onCancelar,
        onAccion = {
            val e = validarTriaje(valores, campos)
            errores = e
            if (e.isEmpty()) onGuardar(valores, motivo) { errores = listOf(it) }
        },
    ) {
        if (d == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp)
            }
            return@DialogoForm
        }
        Text(
            buildString {
                d.edad?.let { append("$it años · ") }
                append(
                    if (flujoMedico) "Funciones vitales antes de la consulta (NTS 139). Quedan ligadas a esta cita."
                    else "Signos vitales y medidas antes de la atención. Quedan ligados a esta cita y al historial del paciente."
                )
                d.yaTenia?.let { append(" Triaje anterior: $it.") }
            },
            color = c.textoSuave, fontSize = 12.sp, lineHeight = 16.sp,
        )
        d.copiadoDe?.let { de ->
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.pendBg)
                .padding(horizontal = 12.dp, vertical = 7.dp)) {
                Text("Copiado del triaje de hoy ($de). Revisa y guarda: queda también en esta cita.",
                    color = c.texto, fontSize = 12.sp)
            }
        }
        d.alergias?.let { al ->
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.errorBg)
                .padding(horizontal = 12.dp, vertical = 7.dp)) {
                Text("⚠ Alergias: $al", color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
        VitalesCampos(valores = valores, onChange = { valores = it; errores = emptyList() }, edad = d.edad, campos = campos)
        Spacer(Modifier.height(12.dp))
        EtqForm("Motivo que refiere el paciente (opcional)")
        OutlinedTextField(
            value = motivo, onValueChange = { motivo = it.take(300) },
            colors = coloresCampoForm(), singleLine = false, minLines = 1,
            placeholder = { Text("Ej. Dolor de cabeza desde ayer", color = c.textoSuave, fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        if (errores.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.errorBg)
                .padding(horizontal = 12.dp, vertical = 8.dp)) {
                errores.forEach { Text("• $it", color = c.error, fontSize = 12.sp) }
            }
        }
    }
}

/**
 * Los campos de signos vitales y medidas (gemelo de VitalesCampos.tsx). Sin
 * números de ejemplo DENTRO del campo (parecían datos cargados): la unidad a la
 * derecha y el rango normal como ayuda debajo.
 */
@Composable
fun VitalesCampos(
    valores: Map<String, String>,
    onChange: (Map<String, String>) -> Unit,
    edad: Int?,
    campos: List<String>,
    /** false = solo lectura (la consulta guiada de quien no puede atender). */
    habilitado: Boolean = true,
) {
    val c = Sania.colors
    fun ver(m: String) = m in campos
    val signos = listOf("frecuencia_cardiaca", "frecuencia_respiratoria", "temperatura", "saturacion_o2").filter(::ver)
    val medidas = listOf("peso", "talla", "perimetro_abdominal").filter(::ver)
    fun num(k: String): Double? = aNumero(valores[k]).let { if (k == "talla") tallaEnCm(it) else it }
    fun set(k: String, v: String) = onChange(valores + (k to v.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(6)))

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (ver("presion") || signos.isNotEmpty()) {
            GrupoVitales("Signos vitales")
            if (ver("presion")) {
                // La presión en un solo bloque "120 / 80", como se anota a mano; su
                // lectura toma la peor de las dos.
                EtqForm("Presión arterial")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CampoNumero(valores["presion_sistolica"].orEmpty(), { set("presion_sistolica", it) },
                        fuera = valorFueraDeRango("presion_sistolica", valores["presion_sistolica"]), unidad = null,
                        descripcion = "Sistólica", modifier = Modifier.weight(1f), habilitado = habilitado)
                    Text(" / ", color = c.textoSuave, fontSize = 18.sp)
                    CampoNumero(valores["presion_diastolica"].orEmpty(), { set("presion_diastolica", it) },
                        fuera = valorFueraDeRango("presion_diastolica", valores["presion_diastolica"]), unidad = null,
                        descripcion = "Diastólica", modifier = Modifier.weight(1f), habilitado = habilitado)
                    Spacer(Modifier.width(6.dp))
                    Text("mmHg", color = c.textoSuave, fontSize = 11.sp)
                }
                val fueraPa = valorFueraDeRango("presion_sistolica", valores["presion_sistolica"]) ||
                    valorFueraDeRango("presion_diastolica", valores["presion_diastolica"])
                when {
                    fueraPa -> PieRojo("Revisa el valor")
                    else -> alertaPresion(num("presion_sistolica"), num("presion_diastolica"), edad)?.let { ChipAlerta(it) }
                        ?: PieAyuda("Normal menor a 120/80")
                }
            }
            signos.forEach { k -> CampoVital(k, valores, ::set, edad, habilitado) }
        }
        if (medidas.isNotEmpty()) {
            GrupoVitales("Medidas")
            if (ver("peso")) CampoVital("peso", valores, ::set, edad, habilitado)
            if (ver("talla")) CampoVital("talla", valores, ::set, edad, habilitado)
            // El IMC necesita las dos: una clínica que solo pesa no lo ve.
            if (ver("peso") && ver("talla")) {
                val imc = calcularImc(num("peso")?.takeUnless { it.isNaN() }, num("talla")?.takeUnless { it.isNaN() })
                val clasif = clasificarImc(imc, edad)
                EtqForm("IMC")
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .background(c.chipBg).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (imc == null) {
                        Text("Se calcula con peso y talla", color = c.textoSuave, fontSize = 12.sp)
                    } else {
                        Text(fijo(imc, 1), color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        clasif?.let { cl ->
                            Spacer(Modifier.width(8.dp))
                            val (fg, bg) = when {
                                cl == "Normal" -> c.ok to c.okBg
                                cl.startsWith("Obesidad") -> c.error to c.errorBg
                                else -> c.pend to c.pendBg
                            }
                            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text(cl, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            if (ver("perimetro_abdominal")) CampoVital("perimetro_abdominal", valores, ::set, edad, habilitado)
        }
        Text(
            "Los colores usan umbrales de adulto y son solo una ayuda: la lectura clínica es del profesional.",
            color = c.textoSuave, fontSize = 10.sp,
        )
    }
}

/** Etiqueta y ayuda de cada campo (CAMPOS de VitalesCampos.tsx). */
private val ETIQUETAS: Map<String, Pair<String, String?>> = mapOf(
    "frecuencia_cardiaca" to ("Frecuencia cardiaca" to "Normal 60–100"),
    "frecuencia_respiratoria" to ("Frecuencia respiratoria" to "Normal 12–20"),
    "temperatura" to ("Temperatura" to "Normal 36–37.4"),
    "saturacion_o2" to ("Saturación de O₂" to "Normal 95–100"),
    "peso" to ("Peso" to null),
    "talla" to ("Talla" to "En cm o en metros (1.70)"),
    "perimetro_abdominal" to ("Perímetro abdominal" to "Opcional · cintura"),
)

@Composable
private fun CampoVital(k: String, valores: Map<String, String>, set: (String, String) -> Unit, edad: Int?, habilitado: Boolean = true) {
    val (etiqueta, ayuda) = ETIQUETAS[k] ?: (k to null)
    val texto = valores[k].orEmpty()
    val bruto = aNumero(texto)
    // "1.70" en la talla = metros: se muestra su equivalente y no la unidad "cm".
    val tallaEnMetros = k == "talla" && bruto != null && !bruto.isNaN() && bruto > 0 && bruto < 3
    val fuera = valorFueraDeRango(k, texto)
    val n = if (k == "talla") tallaEnCm(bruto) else bruto
    val alerta = if (k == "perimetro_abdominal") null else alertaVital(k, n, edad)
    EtqForm(etiqueta)
    CampoNumero(texto, { set(k, it) }, fuera = fuera, unidad = if (tallaEnMetros) "m" else rangoDe(k)?.unidad,
        descripcion = rangoDe(k)?.nombre ?: etiqueta, modifier = Modifier.fillMaxWidth(), habilitado = habilitado)
    when {
        fuera -> PieRojo("Revisa el valor")
        tallaEnMetros -> PieAyuda("= ${n?.let { decimal(it) }} cm")
        alerta != null -> ChipAlerta(alerta)
        ayuda != null -> PieAyuda(ayuda)
    }
}

@Composable
private fun CampoNumero(
    valor: String, onCambio: (String) -> Unit, fuera: Boolean, unidad: String?, descripcion: String, modifier: Modifier,
    habilitado: Boolean = true,
) {
    val c = Sania.colors
    OutlinedTextField(
        value = valor, onValueChange = onCambio,
        singleLine = true, isError = fuera, enabled = habilitado,
        colors = coloresCampoForm(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        suffix = unidad?.let { u -> { Text(u, color = c.textoSuave, fontSize = 12.sp) } },
        // Accesibilidad: el lector de pantalla anuncia qué medida es (la presión no lleva etiqueta propia).
        modifier = modifier.padding(top = 2.dp).semantics { contentDescription = descripcion },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = c.texto),
    )
}

@Composable
private fun GrupoVitales(t: String) {
    Text(t.uppercase(), color = Sania.colors.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun PieAyuda(t: String) {
    Text(t, color = Sania.colors.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
}

@Composable
private fun PieRojo(t: String) {
    Text(t, color = Sania.colors.error, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
}

/** Chip de la lectura por color: rojo = alerta, ámbar = atención. */
@Composable
fun ChipAlerta(a: AlertaVital) {
    val (fg, bg) = coloresAlerta(a.nivel)
    Box(Modifier.padding(top = 3.dp).clip(RoundedCornerShape(4.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(a.texto, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** (texto, fondo) de un nivel de alerta. */
@Composable
fun coloresAlerta(n: NivelAlerta?): Pair<Color, Color> {
    val c = Sania.colors
    return when (n) {
        NivelAlerta.ALERTA -> c.error to c.errorBg
        NivelAlerta.ATENCION -> c.pend to c.pendBg
        null -> c.texto to c.superficie
    }
}
