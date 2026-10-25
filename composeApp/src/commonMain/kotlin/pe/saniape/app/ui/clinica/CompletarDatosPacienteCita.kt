package pe.saniape.app.ui.clinica

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.AltaRapidaRepo
import pe.saniape.app.data.staff.BuscarPacienteRepo
import pe.saniape.app.data.staff.HallazgoPadron
import pe.saniape.app.data.staff.LocalTerminologiaPaciente
import pe.saniape.app.data.staff.ResultadoAgregarDni
import pe.saniape.app.data.staff.consultaPadron
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoFecha
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.theme.Sania

/** Chips visibles antes de "+ más" (como CompletarDatosPaciente web). */
private const val MAX_CHIPS = 10

/**
 * "Completar datos de…" tras registrar al paciente desde la nueva cita, por DNI
 * o SOLO con el nombre — gemelo de `components/citas/CompletarDatosPaciente.tsx`.
 * TODO es opcional y "Ahora no" cierra sin guardar: en el mostrador con cola no
 * atrapa a recepción. Se guarda por POST /api/staff/paciente/actualizar con
 * solo lo escrito. Registrado sin DNI y con `datos_personales` (lo que la RLS
 * exige para escribir el documento): "🪪 Agregar su DNI".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DialogoCompletarDatosPaciente(
    pacienteId: String,
    nombreInicial: String,
    sinDni: Boolean,
    /** `puede("datos_personales")`. */
    puedeAgregarDni: Boolean,
    /** País de la sede (el padrón solo existe para el DNI peruano). */
    pais: String?,
    chipsTipos: List<String>,
    chipsSintomas: List<String>,
    onListo: () -> Unit,
    /** El DNI se agregó (y quizá el nombre oficial del padrón). */
    onDniGuardado: (dni: String, nombre: String) -> Unit,
    /** El teléfono que ya se dio al registrarlo (la tarjeta del alta rápida). */
    telefonoInicial: String? = null,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var nombre by remember { mutableStateOf(nombreInicial) }
    var faltaDni by remember { mutableStateOf(sinDni) }
    var dniAbierto by remember { mutableStateOf(false) }
    var fechaNacimiento by remember { mutableStateOf("") }
    var verFecha by remember { mutableStateOf(false) }
    var telefono by remember { mutableStateOf(telefonoInicial.orEmpty()) }
    var email by remember { mutableStateOf("") }
    var ocupacion by remember { mutableStateOf("") }
    var direccion by remember { mutableStateOf("") }
    var tipoPatologia by remember { mutableStateOf("") }
    var sintomas by remember { mutableStateOf("") }
    var verMasTipos by remember { mutableStateOf(false) }
    var verMasSintomas by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }

    fun guardar() {
        if (guardando) return
        if (fechaNacimiento.isNotEmpty() && fechaNacimiento > hoyClinicaIso()) {
            Toaster.error("La fecha de nacimiento no puede ser futura"); return
        }
        val campos = mapOf(
            "fechaNacimiento" to fechaNacimiento, "telefono" to telefono, "email" to email,
            "ocupacion" to ocupacion, "direccion" to direccion,
            "tipoPatologia" to tipoPatologia, "sintomas" to sintomas,
        ).filterValues { it.isNotBlank() }
        // Nada escrito: es lo mismo que "Ahora no".
        if (campos.isEmpty()) { onListo(); return }
        guardando = true
        scope.launch {
            val r = BuscarPacienteRepo.completarDatos(pacienteId, campos)
            guardando = false
            if (r.registrada) { Toaster.exito("Datos guardados"); onListo() }
            else Toaster.error(r.rechazo?.error ?: "No se pudo guardar")
        }
    }

    DialogoForm(
        titulo = "Completar datos de $nombre",
        subtitulo = "Todo es opcional",
        textoAccion = if (guardando) "Guardando…" else "Guardar datos",
        accionHabilitada = !guardando,
        onCancelar = onListo,
        onAccion = { guardar() },
        textoCancelar = "Ahora no",
    ) {
        Text(
            "Ya quedó registrado. Puedes completar su ficha ahora o dejarlo para después — nada de esto es obligatorio.",
            color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp),
        )
        if (faltaDni && puedeAgregarDni) {
            Column(
                Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.pendBg).border(1.dp, c.pend, RoundedCornerShape(Sania.shape.sm.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text("🪪 Se registró sin DNI. Si lo tiene a mano, agrégalo ahora.", color = c.texto, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                BotonContorno("Agregar su DNI", habilitado = !guardando) { dniAbierto = true }
            }
        }

        EtqForm("Fecha de nacimiento")
        CajaSelectorForm(fechaNacimiento.ifEmpty { "Elegir fecha" }) { verFecha = true }
        Spacer(Modifier.height(10.dp))
        CampoTexto("Teléfono", telefono, "987654321", KeyboardType.Phone) { telefono = it }
        CampoTexto("Correo", email, "correo@ejemplo.com", KeyboardType.Email) { email = it }
        CampoTexto("Ocupación", ocupacion, "Ej. Docente") { ocupacion = it }
        CampoTexto("Dirección", direccion, "Ej. Av. Bolognesi 123") { direccion = it }

        EtqForm("Patología / motivo")
        if (chipsTipos.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 8.dp)) {
                (if (verMasTipos) chipsTipos else chipsTipos.take(MAX_CHIPS)).forEach { t ->
                    Chip(t, activo = tipoPatologia == t) { tipoPatologia = if (tipoPatologia == t) "" else t }
                }
                if (chipsTipos.size > MAX_CHIPS) {
                    Chip(if (verMasTipos) "− menos" else "+ ${chipsTipos.size - MAX_CHIPS} más", activo = false) { verMasTipos = !verMasTipos }
                }
            }
        }
        CampoTexto(null, tipoPatologia, "O escribe uno personalizado…") { tipoPatologia = it }

        EtqForm("Síntomas")
        CampoTexto(null, sintomas, "Qué refiere el paciente", unaLinea = false) { sintomas = it }
        if (chipsSintomas.isNotEmpty()) {
            val marcados = sintomas.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (if (verMasSintomas) chipsSintomas else chipsSintomas.take(MAX_CHIPS)).forEach { s ->
                    val activo = s in marcados
                    Chip(s, activo) { sintomas = (if (activo) marcados - s else marcados + s).joinToString(", ") }
                }
                if (chipsSintomas.size > MAX_CHIPS) {
                    Chip(if (verMasSintomas) "− menos" else "+ ${chipsSintomas.size - MAX_CHIPS} más", activo = false) { verMasSintomas = !verMasSintomas }
                }
            }
        }
    }

    if (verFecha) {
        DialogoFecha(onElegir = { fechaNacimiento = it; verFecha = false }, onCerrar = { verFecha = false },
            inicial = fechaNacimiento.ifEmpty { null })
    }
    if (dniAbierto) {
        DialogoAgregarDni(
            pacienteId = pacienteId, nombre = nombre, pais = pais,
            onCerrar = { dniAbierto = false },
            onGuardado = { dni, nuevoNombre ->
                faltaDni = false
                nombre = nuevoNombre
                onDniGuardado(dni, nuevoNombre)
            },
        )
    }
}

/**
 * "🪪 Completar DNI" — gemelo de `components/citas/CompletarDni.tsx`: con un DNI
 * de 8 dígitos (sede de Perú) se consulta el padrón y se puede usar su nombre;
 * si el documento ya está en otra ficha NO se guarda (misma persona dos veces).
 */
@Composable
private fun DialogoAgregarDni(
    pacienteId: String, nombre: String, pais: String?,
    onCerrar: () -> Unit, onGuardado: (dni: String, nombre: String) -> Unit,
) {
    val c = Sania.colors
    val tpl = LocalTerminologiaPaciente.current
    val scope = rememberCoroutineScope()
    var doc by remember { mutableStateOf("") }
    var padron by remember { mutableStateOf<String?>(null) }
    var usarPadron by remember { mutableStateOf(true) }
    var buscando by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var duplicado by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // Cada cambio del documento invalida la consulta anterior al padrón.
    var consulta by remember { mutableStateOf(0) }

    fun buscarPadron(valor: String) {
        padron = null
        val n = ++consulta
        buscando = false
        if (!consultaPadron(valor, pais)) return
        buscando = true
        scope.launch {
            val h = AltaRapidaRepo.consultarPadron(valor)
            if (n != consulta) return@launch
            buscando = false
            padron = (h as? HallazgoPadron.Encontrado)?.nombre
        }
    }

    fun guardar() {
        if (guardando || buscando) return
        val dni = doc.trim()
        error = null; duplicado = null
        if (dni.count { it.isLetterOrDigit() } < 5) { error = "Escribe el DNI (8 dígitos) o el documento del ${tpl.paciente}."; return }
        guardando = true
        val nombrePadron = padron?.takeIf { usarPadron }
        scope.launch {
            val r = BuscarPacienteRepo.agregarDni(pacienteId, dni, nombrePadron)
            guardando = false
            when (r) {
                ResultadoAgregarDni.Ok -> {
                    val final = nombrePadron ?: nombre
                    Toaster.exito("DNI de $final guardado")
                    onGuardado(dni, final)
                    onCerrar()
                }
                is ResultadoAgregarDni.Duplicado -> duplicado = r.nombre
                is ResultadoAgregarDni.Error -> error = r.mensaje
            }
        }
    }

    DialogoForm(
        titulo = "🪪 Completar DNI",
        subtitulo = nombre,
        textoAccion = if (guardando) "Guardando…" else "Guardar DNI",
        accionHabilitada = !guardando && !buscando,
        onCancelar = onCerrar,
        onAccion = { guardar() },
        textoCancelar = "Ahora no",
    ) {
        Text("$nombre se registró sin DNI. Pídeselo y cárgalo aquí.", color = c.textoSuave, fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 10.dp))
        OutlinedTextField(
            value = doc,
            onValueChange = { v -> doc = v.trim(); duplicado = null; error = null; buscarPadron(v.trim()) },
            placeholder = { Text("DNI (8 dígitos) o documento extranjero", color = c.textoSuave) },
            singleLine = true, modifier = Modifier.fillMaxWidth(), colors = coloresCampoForm(),
        )
        if (buscando) Text("Buscando en el padrón…", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        padron?.let { p ->
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.chipBg)
                    .toggleable(value = usarPadron, role = Role.Checkbox, onValueChange = { usarPadron = it })
                    .padding(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(if (usarPadron) "☑" else "☐", color = c.navy, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Padrón: $p. Usar este nombre en la ficha" +
                        (if (p.equals(nombre, ignoreCase = true)) "" else " (hoy dice \"$nombre\")") + ".",
                    color = c.texto, fontSize = 13.sp,
                )
            }
        }
        duplicado?.let { otro ->
            Text(
                "Este documento ya está en la ficha de $otro. Probablemente es la misma persona registrada dos veces: no se guardó para no duplicarlo.",
                color = c.texto, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.pendBg).border(1.dp, c.pend, RoundedCornerShape(Sania.shape.sm.dp)).padding(10.dp),
            )
        }
        error?.let { Text(it, color = c.error, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun CampoTexto(
    etiqueta: String?, valor: String, placeholder: String,
    teclado: KeyboardType = KeyboardType.Text, unaLinea: Boolean = true, onCambio: (String) -> Unit,
) {
    if (etiqueta != null) EtqForm(etiqueta)
    OutlinedTextField(
        value = valor, onValueChange = onCambio,
        placeholder = { Text(placeholder, color = Sania.colors.textoSuave) },
        singleLine = unaLinea, minLines = if (unaLinea) 1 else 2,
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        modifier = Modifier.fillMaxWidth(), colors = coloresCampoForm(),
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun Chip(texto: String, activo: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    Text(
        texto, color = if (activo) c.sobreNavy else c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(forma).background(if (activo) c.navy else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, forma)
            .clickable(role = Role.Button) { onClick() }.padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun BotonContorno(texto: String, habilitado: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    Text(
        texto, color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(forma).border(1.dp, c.navy, forma)
            .clickable(enabled = habilitado, role = Role.Button) { onClick() }.padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
