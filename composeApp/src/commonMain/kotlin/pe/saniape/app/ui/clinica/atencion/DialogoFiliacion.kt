package pe.saniape.app.ui.clinica.atencion

import pe.saniape.app.data.staff.LocalTerminologiaPaciente
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.staff.AtencionRepo
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.theme.Sania

// ─────────────────────────────────────────────────────────────────────────────
// 📋 FILIACIÓN — la primera hoja de la HC (NTS 139, formato especial 1).
// GEMELO de components/historia/FiliacionModal.tsx y lib/historia-clinica.ts
// (CAMPOS_FILIACION y las listas). Lo que vive en la ficha (documento,
// nacimiento, teléfono, domicilio, ocupación) se corrige en ✏ Editar paciente.
//
// POST /api/staff/atencion/filiacion es PARCIAL: solo se mandan las claves que
// el usuario tocó o que tienen valor (lo que no viene, el servidor no lo toca).
// ─────────────────────────────────────────────────────────────────────────────

/** Claves de la hoja (CAMPOS_FILIACION de la web), en el orden del formulario. */
internal val CAMPOS_FILIACION = listOf(
    "tipo_documento", "sexo", "estado_civil", "grado_instruccion", "religion", "lugar_nacimiento",
    "procedencia", "domicilio_distrito", "domicilio_provincia", "domicilio_departamento",
    "grupo_sanguineo", "factor_rh", "tipo_seguro", "numero_seguro", "responsable_nombre",
    "responsable_dni", "responsable_parentesco", "responsable_telefono", "responsable_domicilio",
)

internal val TIPOS_DOCUMENTO = listOf("DNI", "Carné de extranjería", "Pasaporte", "Otro", "Sin documento")
internal val ESTADOS_CIVILES = listOf("Soltero(a)", "Casado(a)", "Conviviente", "Divorciado(a)", "Separado(a)", "Viudo(a)")
internal val GRADOS_INSTRUCCION = listOf(
    "Sin instrucción", "Inicial", "Primaria incompleta", "Primaria completa",
    "Secundaria incompleta", "Secundaria completa", "Superior técnica", "Superior universitaria", "Posgrado",
)
internal val GRUPOS_SANGUINEOS = listOf("O", "A", "B", "AB")
internal val FACTORES_RH = listOf("+", "−")
internal val TIPOS_SEGURO = listOf("Ninguno", "SIS", "EsSalud", "EPS / seguro privado", "SOAT", "FF.AA. / PNP", "Otro")

/** Sexo: la web guarda "F" / "M". Se muestran con el nombre completo. */
private val SEXOS = listOf("F" to "F — Femenino", "M" to "M — Masculino")

/** El faltante de un menor sin responsable (texto de faltantesFiliacion de la web). */
internal const val FALTA_RESPONSABLE = "Padre, madre o tutor (con DNI)"

/**
 * El cuerpo de `filiacion` (parcial): cada clave que el usuario [tocados] (vacía →
 * null, para borrarla) o que tiene valor. Lo demás no viaja y el servidor no lo toca.
 */
internal fun cuerpoFiliacion(valores: Map<String, String>, tocados: Set<String>): Map<String, String?> {
    val out = LinkedHashMap<String, String?>()
    for (k in CAMPOS_FILIACION) {
        val v = valores[k]?.trim().orEmpty()
        if (k in tocados) out[k] = v.ifEmpty { null }
        else if (v.isNotEmpty()) out[k] = v
    }
    return out
}

/**
 * Los faltantes que mandó el servidor, sin los que el usuario ya completó en esta
 * hoja (los de la ficha —documento, nacimiento, domicilio…— siguen hasta que se
 * editen allá).
 */
internal fun faltantesVigentes(faltantes: List<String>, valores: Map<String, String>): List<String> {
    fun lleno(k: String) = valores[k]?.isNotBlank() == true
    return faltantes.filterNot { f ->
        when (f) {
            "Sexo" -> lleno("sexo")
            "Estado civil" -> lleno("estado_civil")
            "Grado de instrucción" -> lleno("grado_instruccion")
            "Lugar de nacimiento" -> lleno("lugar_nacimiento")
            FALTA_RESPONSABLE -> lleno("responsable_nombre") && lleno("responsable_dni")
            else -> false
        }
    }
}

/** "N° HC 40125874" (o "… (provisional)" sin documento): formatearNumeroHC de la web. */
internal fun textoNumeroHc(numero: String?, origen: String?): String =
    if (numero.isNullOrBlank()) "N° HC —"
    else "N° HC $numero" + if (origen == "CORRELATIVO") " (provisional)" else ""

/**
 * Hoja de filiación del paciente [pacienteId]. [faltantes] = `flags.faltantesFiliacion`
 * de la consulta. [menorDeEdad] sale de la edad (esMenorDeEdad de la web) y marca
 * el responsable como obligatorio. Prellena con la HC si ya existe (lectura
 * directa, RLS de la clínica); si no se pudo leer, arranca vacía y el envío
 * parcial no borra nada. El toast de éxito lo muestra el diálogo, como la web.
 */
@Composable
fun DialogoFiliacion(
    pacienteId: String,
    faltantes: List<String>,
    menorDeEdad: Boolean,
    onCancelar: () -> Unit,
    onGuardada: () -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val valores = remember { mutableStateMapOf<String, String>() }
    val tocados = remember { mutableStateMapOf<String, Boolean>() }
    var cargando by remember { mutableStateOf(true) }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    /** null = no se sabe (no se pudo leer); "" = aún no tiene HC. */
    var numeroHc by remember { mutableStateOf<String?>(null) }
    var origenHc by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pacienteId) {
        val fila = runCatching {
            Supabase.client.postgrest["historias_clinicas"]
                .select(Columns.raw((CAMPOS_FILIACION + listOf("numero", "origen_numero")).joinToString(","))) {
                    filter { eq("paciente_id", pacienteId) }
                    limit(1)
                }
                .decodeList<JsonObject>()
        }
        fila.getOrNull()?.let { filas ->
            val hc = filas.firstOrNull()
            fun s(k: String) = (hc?.get(k) as? JsonPrimitive)?.contentOrNull
            numeroHc = s("numero").orEmpty()
            origenHc = s("origen_numero")
            // Lo que el usuario ya tocó mientras cargaba no se pisa.
            if (hc != null) for (k in CAMPOS_FILIACION) if (tocados[k] != true) s(k)?.let { valores[k] = it }
        }
        cargando = false
    }

    fun poner(k: String, v: String) {
        valores[k] = v
        tocados[k] = true
    }

    fun guardar() {
        if (guardando) return
        guardando = true
        error = null
        val cuerpo = cuerpoFiliacion(valores.toMap(), tocados.filterValues { it }.keys)
        scope.launch {
            val r = AtencionRepo.guardarFiliacion(pacienteId, cuerpo)
            guardando = false
            if (r.registrada) {
                // Como FiliacionModal: con HC, "actualizada"; sin HC, se acaba de abrir (con su número).
                val hc = r.cuerpo?.get("hc") as? JsonObject
                fun s(k: String) = (hc?.get(k) as? JsonPrimitive)?.contentOrNull
                Toaster.exito(
                    if (numeroHc == "") "Historia clínica abierta: ${textoNumeroHc(s("numero"), s("origen_numero"))}"
                    else "Filiación actualizada",
                )
                onGuardada()
            } else {
                val msg = r.rechazo?.error ?: "No se pudo guardar la filiación."
                error = msg
                Toaster.error(msg)
            }
        }
    }

    val pendientes = faltantesVigentes(faltantes, valores)
    val menor = menorDeEdad
    val sinHc = numeroHc == ""

    DialogoForm(
        titulo = "📋 Filiación",
        subtitulo = when {
            cargando -> "Historia clínica"
            numeroHc.isNullOrEmpty() -> "Historia clínica"
            else -> textoNumeroHc(numeroHc, origenHc)
        },
        textoAccion = when {
            guardando -> "Guardando…"
            sinHc -> "Abrir historia y guardar"
            else -> "Guardar"
        },
        accionHabilitada = !guardando && !cargando,
        onCancelar = { if (!guardando) onCancelar() },
        onAccion = ::guardar,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (pendientes.isNotEmpty()) {
                CajaAviso("Para la hoja de filiación falta: ${pendientes.joinToString(" · ")}.", c.texto, c.pendBg)
            }
            error?.let { CajaAviso(it, c.error, c.errorBg) }
            Text(
                "El documento, la fecha de nacimiento, el teléfono, el domicilio y la ocupación se corrigen en ✏ Editar del ${LocalTerminologiaPaciente.current.paciente}.",
                color = c.textoSuave, fontSize = 12.sp,
            )
            if (sinHc) {
                Text("Al guardar se abre la historia clínica del ${LocalTerminologiaPaciente.current.paciente}.", color = c.textoSuave, fontSize = 12.sp)
            }
            if (cargando) {
                Text("Cargando…", color = c.textoSuave, fontSize = 13.sp)
            } else {
                SelectorDialogo("Tipo de documento", valores["tipo_documento"].orEmpty(), TIPOS_DOCUMENTO.map { it to it }) { poner("tipo_documento", it) }
                SelectorDialogo("Sexo", valores["sexo"].orEmpty(), SEXOS) { poner("sexo", it) }
                SelectorDialogo("Estado civil", valores["estado_civil"].orEmpty(), ESTADOS_CIVILES.map { it to it }) { poner("estado_civil", it) }
                SelectorDialogo("Grado de instrucción", valores["grado_instruccion"].orEmpty(), GRADOS_INSTRUCCION.map { it to it }) { poner("grado_instruccion", it) }
                CampoDialogo("Lugar de nacimiento", valores["lugar_nacimiento"].orEmpty(), { poner("lugar_nacimiento", it) }, "Distrito / provincia / país")
                CampoDialogo("Procedencia", valores["procedencia"].orEmpty(), { poner("procedencia", it) }, "De dónde viene (si no vive en la ciudad)")
                CampoDialogo("Distrito (domicilio)", valores["domicilio_distrito"].orEmpty(), { poner("domicilio_distrito", it) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoDialogo("Provincia", valores["domicilio_provincia"].orEmpty(), { poner("domicilio_provincia", it) }, modifier = Modifier.weight(1f))
                    CampoDialogo("Departamento", valores["domicilio_departamento"].orEmpty(), { poner("domicilio_departamento", it) }, modifier = Modifier.weight(1f))
                }
                CampoDialogo("Religión", valores["religion"].orEmpty(), { poner("religion", it) }, "Opcional")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectorDialogo("Grupo sanguíneo", valores["grupo_sanguineo"].orEmpty(), GRUPOS_SANGUINEOS.map { it to it }, Modifier.weight(1f)) { poner("grupo_sanguineo", it) }
                    SelectorDialogo("Factor Rh", valores["factor_rh"].orEmpty(), FACTORES_RH.map { it to it }, Modifier.weight(1f)) { poner("factor_rh", it) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectorDialogo("Seguro", valores["tipo_seguro"].orEmpty(), TIPOS_SEGURO.map { it to it }, Modifier.weight(1f)) { poner("tipo_seguro", it) }
                    CampoDialogo("N° de seguro", valores["numero_seguro"].orEmpty(), { poner("numero_seguro", it) }, modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    ("Acompañante o persona responsable" + if (menor) " — obligatorio: es menor de edad" else "").uppercase(),
                    color = c.navy, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                CampoDialogo("Nombres y apellidos", valores["responsable_nombre"].orEmpty(), { poner("responsable_nombre", it) },
                    capitalizacion = KeyboardCapitalization.Words)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoDialogo("DNI", valores["responsable_dni"].orEmpty(), { poner("responsable_dni", it.take(15)) },
                        teclado = KeyboardType.Number, modifier = Modifier.weight(1f))
                    CampoDialogo("Teléfono", valores["responsable_telefono"].orEmpty(), { poner("responsable_telefono", it.take(20)) },
                        teclado = KeyboardType.Phone, modifier = Modifier.weight(1f))
                }
                CampoDialogo("Parentesco", valores["responsable_parentesco"].orEmpty(), { poner("responsable_parentesco", it) }, "Madre, padre, tutor…")
                CampoDialogo("Domicilio", valores["responsable_domicilio"].orEmpty(), { poner("responsable_domicilio", it) })
            }
        }
    }
}

// ── Piezas compartidas por los diálogos de la atención ───────────────────────

/** Recuadro de aviso (faltantes, errores del servidor). */
@Composable
internal fun CajaAviso(texto: String, fg: Color, bg: Color) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(bg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(texto, color = fg, fontSize = 13.sp) }
}

/** Campo de texto con etiqueta estándar (EtqForm) y los colores del tema. */
@Composable
internal fun CampoDialogo(
    label: String,
    valor: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    teclado: KeyboardType = KeyboardType.Text,
    capitalizacion: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    unaLinea: Boolean = true,
    minLineas: Int = 1,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier) {
        EtqForm(label)
        OutlinedTextField(
            value = valor,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = unaLinea,
            minLines = if (unaLinea) 1 else minLineas,
            textStyle = TextStyle(fontSize = 14.sp),
            placeholder = if (placeholder.isNotEmpty()) {
                { Text(placeholder, fontSize = 13.sp, maxLines = if (unaLinea) 1 else 3, overflow = TextOverflow.Ellipsis) }
            } else null,
            trailingIcon = trailing,
            keyboardOptions = KeyboardOptions(capitalization = capitalizacion, keyboardType = teclado),
            colors = coloresCampoForm(),
            shape = RoundedCornerShape(Sania.shape.sm.dp),
        )
    }
}

/**
 * Selector de una lista cerrada ([opciones] = valor a guardar → texto). La primera
 * opción "—" deja el campo vacío (como el `<option value="">—</option>` de la web).
 * Un valor que no está en la lista (dato antiguo) se muestra tal cual.
 */
@Composable
internal fun SelectorDialogo(
    label: String,
    valor: String,
    opciones: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    onElegir: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column(modifier) {
        if (label.isNotEmpty()) EtqForm(label)
        Box {
            val texto = if (valor.isBlank()) "—" else opciones.firstOrNull { it.first == valor }?.second ?: valor
            CajaSelectorForm(texto) { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("—", fontSize = 14.sp) }, onClick = { menu = false; onElegir("") })
                opciones.forEach { (v, t) ->
                    DropdownMenuItem(text = { Text(t, fontSize = 14.sp) }, onClick = { menu = false; onElegir(v) })
                }
            }
        }
    }
}
