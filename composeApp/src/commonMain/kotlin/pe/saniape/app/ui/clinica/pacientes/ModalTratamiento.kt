package pe.saniape.app.ui.clinica.pacientes

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.EvaluacionRef
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.ProcedimientoRef
import pe.saniape.app.data.staff.TarifarioRef
import pe.saniape.app.data.staff.TerapeutaConEsp
import pe.saniape.app.ui.theme.Sania

/** Resultado del form de tratamiento (lo que se envía al endpoint crear). */
data class TratamientoNuevo(
    val procedimientoId: String,
    val terapeutaId: String?,
    val modalidad: String,
    val totalSesiones: Int?,
    val precioPaquete: Double?,
    val precioPorSesion: Double?,
    val precioAcordado: Double?,
    val diagnostico: String?,
    val citaOrigenId: String?,
    val medicacion: String?,
    val proximoControl: String?,
)

/**
 * Modal de crear tratamiento (igual que TratamientoForm web): servicio → especialidad
 * decide si usa sesiones. Si usa sesiones: modalidad Paquete (N+precio) o Sesión suelta
 * (precio/sesión). Si es Consulta (especialidad sin sesiones): solo costo de la consulta.
 * El profesional puede venir fijado (profesional vinculado).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModalCrearTratamiento(
    pacienteId: String,
    miTerapeutaId: String?,
    diagnosticoPrevio: String?,   // del paciente/evaluación, para precargar
    onCancelar: () -> Unit,
    onGuardar: (TratamientoNuevo) -> Unit,
) {
    val c = Sania.colors
    var procedimientos by remember { mutableStateOf<List<ProcedimientoRef>>(emptyList()) }
    var terapeutas by remember { mutableStateOf<List<TerapeutaConEsp>>(emptyList()) }
    var evaluaciones by remember { mutableStateOf<List<EvaluacionRef>>(emptyList()) }
    var proc by remember { mutableStateOf<ProcedimientoRef?>(null) }
    var terapeuta by remember { mutableStateOf<TerapeutaConEsp?>(null) }
    var evaluacion by remember { mutableStateOf<EvaluacionRef?>(null) }
    var modalidad by remember { mutableStateOf("Paquete") }
    var totalSesiones by remember { mutableStateOf("10") }
    var precioPaquete by remember { mutableStateOf("") }
    var precioPorSesion by remember { mutableStateOf("") }
    var precioAcordado by remember { mutableStateOf("") }
    var diagnostico by remember { mutableStateOf(diagnosticoPrevio ?: "") }
    var medicacion by remember { mutableStateOf("") }
    var proximoControl by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        procedimientos = runCatching { PacientesRepo.procedimientos() }.getOrDefault(emptyList())
        terapeutas = runCatching { PacientesRepo.terapeutasConEspecialidad() }.getOrDefault(emptyList())
        evaluaciones = runCatching { PacientesRepo.evaluacionesDe(pacienteId) }.getOrDefault(emptyList())
        if (miTerapeutaId != null) terapeuta = terapeutas.find { it.id == miTerapeutaId }
    }

    // Servicios filtrados por la especialidad del profesional elegido (igual que la web).
    val terId = if (miTerapeutaId != null) miTerapeutaId else terapeuta?.id
    val espsDelProf = terapeutas.find { it.id == terId }?.especialidadIds ?: emptyList()
    val procsVisibles = procedimientos.filter { p ->
        terId == null || p.especialidadId == null || p.especialidadId in espsDelProf
    }

    // Al elegir servicio: autocompletar precios + tarifario (si hay).
    LaunchedEffect(proc?.id) {
        proc?.let { p ->
            precioPorSesion = p.precio.toString()
            val tar10 = p.tarifarios.firstOrNull { it.cantidadSesiones == 10 } ?: p.tarifarios.firstOrNull()
            if (tar10 != null) {
                totalSesiones = tar10.cantidadSesiones.toString()
                precioPaquete = tar10.precioTotal.toString()
            } else {
                precioPaquete = p.precioPaquete?.toString() ?: ""
                totalSesiones = "10"
            }
        }
    }

    val esConsulta = proc?.usaSesiones == false
    val usaSesiones = proc != null && !esConsulta

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("➕ Nuevo tratamiento", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // Diagnóstico (precargado de la evaluación)
                Etq("Diagnóstico")
                OutlinedTextField(value = diagnostico, onValueChange = { diagnostico = it },
                    placeholder = { Text("Diagnóstico que motiva este tratamiento", color = c.textoSuave) },
                    minLines = 2, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))

                // Profesional (fijo si vinculado)
                Etq("Profesional que atenderá")
                if (miTerapeutaId != null) {
                    SelectorBox("${terapeuta?.nombre ?: "Tú"} (tú)", bloqueado = true) {}
                } else {
                    SelectorLista(terapeutas, terapeuta, { it.nombre }, "Sin asignar") { t ->
                        terapeuta = t
                        // Si el servicio ya no pertenece a la nueva especialidad, limpiarlo.
                        proc?.let { p -> if (p.especialidadId != null && p.especialidadId !in t.especialidadIds) proc = null }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // Servicio (filtrado por profesional)
                Etq("Servicio")
                SelectorLista(procsVisibles, proc, { it.nombre },
                    if (terId == null) "Elige profesional primero" else "Seleccionar…") { proc = it }

                // ¿Nació de una evaluación? (opcional)
                if (evaluaciones.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Etq("¿Nació de una evaluación? (opcional)")
                    SelectorLista(evaluaciones, evaluacion,
                        { "Evaluación ${it.fecha}" + (it.terapeutaNombre?.let { n -> " · $n" } ?: "") },
                        "Sin evaluación previa") { evaluacion = it }
                }

                if (usaSesiones) {
                    Spacer(Modifier.height(10.dp))
                    Etq("Modalidad de pago")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChipMod("📦 Paquete", modalidad == "Paquete", Modifier.weight(1f)) { modalidad = "Paquete" }
                        ChipMod("🎫 Suelta", modalidad == "Sesión suelta", Modifier.weight(1f)) { modalidad = "Sesión suelta" }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (modalidad == "Paquete") {
                        // Tarifario (si el servicio tiene paquetes predefinidos)
                        val tarifs = proc?.tarifarios ?: emptyList()
                        if (tarifs.isNotEmpty()) {
                            Etq("Elegir paquete del tarifario")
                            SelectorLista(tarifs, null as TarifarioRef?,
                                { "${it.cantidadSesiones} sesiones — S/ ${it.precioTotal}" },
                                "Personalizado o manual…") { t -> totalSesiones = t.cantidadSesiones.toString(); precioPaquete = t.precioTotal.toString() }
                            Spacer(Modifier.height(6.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) { Etq("N° sesiones"); CampoNum(totalSesiones) { totalSesiones = it } }
                            Column(Modifier.weight(1f)) { Etq("Precio paquete"); CampoNum(precioPaquete) { precioPaquete = it } }
                        }
                        Text("Puedes ajustar sesiones y precio; el tarifario solo los pre-llena.",
                            color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                    } else {
                        Etq("Precio por sesión")
                        CampoNum(precioPorSesion) { precioPorSesion = it }
                        Text("Se cobra por cada sesión realizada.", color = c.textoSuave, fontSize = 10.sp)
                    }
                } else if (esConsulta) {
                    // Especialidad sin sesiones (medicina, nutrición): consulta
                    Spacer(Modifier.height(10.dp))
                    Etq("Medicación / receta (opcional)")
                    OutlinedTextField(value = medicacion, onValueChange = { medicacion = it },
                        placeholder = { Text("Indicaciones, receta…", color = c.textoSuave) },
                        minLines = 2, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Etq("Próximo control (AAAA-MM-DD, opcional)")
                    OutlinedTextField(value = proximoControl, onValueChange = { proximoControl = it },
                        placeholder = { Text("2026-07-15", color = c.textoSuave) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Etq("Costo de la consulta (S/) — opcional")
                    CampoNum(precioAcordado) { precioAcordado = it }
                    Text("Déjalo vacío si es gratis.", color = c.textoSuave, fontSize = 10.sp)
                }
            }
        },
        confirmButton = {
            Box(
                Modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.navy)
                    .clickable {
                        val p = proc ?: return@clickable
                        onGuardar(
                            TratamientoNuevo(
                                procedimientoId = p.id,
                                terapeutaId = if (miTerapeutaId != null) miTerapeutaId else terapeuta?.id,
                                modalidad = if (esConsulta) "Consulta" else modalidad,
                                totalSesiones = if (usaSesiones && modalidad == "Paquete") totalSesiones.toIntOrNull() ?: 10
                                    else if (usaSesiones) 1 else null,
                                precioPaquete = if (usaSesiones && modalidad == "Paquete") precioPaquete.toDoubleOrNull() else null,
                                precioPorSesion = if (usaSesiones && modalidad == "Sesión suelta") precioPorSesion.toDoubleOrNull() else null,
                                precioAcordado = if (esConsulta) precioAcordado.toDoubleOrNull() else null,
                                diagnostico = diagnostico.trim().ifBlank { null },
                                citaOrigenId = evaluacion?.id,
                                medicacion = if (esConsulta) medicacion.trim().ifBlank { null } else null,
                                proximoControl = if (esConsulta) proximoControl.trim().ifBlank { null } else null,
                            )
                        )
                    }.padding(horizontal = 18.dp, vertical = 10.dp),
            ) { Text("Crear", color = c.sobreNavy, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}

/** Ampliar tratamiento: +sesiones, +monto opcional, nota. */
@Composable
fun ModalAmpliarTratamiento(
    t: pe.saniape.app.data.staff.TratamientoPaciente,
    onCancelar: () -> Unit,
    onConfirmar: (sesionesExtra: Int, montoExtra: Double, nota: String?) -> Unit,
) {
    val c = Sania.colors
    var sesiones by remember { mutableStateOf("") }
    var monto by remember { mutableStateOf("") }
    var nota by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("➕ Ampliar tratamiento", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Quedará en ${t.totalSesiones} + las que agregues.", color = c.textoSuave, fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 8.dp))
                Etq("Sesiones adicionales"); CampoNum(sesiones) { sesiones = it }
                Spacer(Modifier.height(8.dp))
                Etq("Monto adicional (S/) — opcional"); CampoNum(monto) { monto = it }
                Spacer(Modifier.height(8.dp))
                Etq("Motivo / acuerdo — opcional")
                OutlinedTextField(value = nota, onValueChange = { nota = it }, minLines = 2,
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.navy)
                .clickable {
                    val n = sesiones.toIntOrNull() ?: 0
                    if (n > 0) onConfirmar(n, monto.toDoubleOrNull() ?: 0.0, nota.trim().ifBlank { null })
                }.padding(horizontal = 18.dp, vertical = 10.dp)) {
                Text("Ampliar", color = c.sobreNavy, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}

/** Editar tratamiento: N° sesiones + precios (según modalidad). */
@Composable
fun ModalEditarTratamiento(
    t: pe.saniape.app.data.staff.TratamientoPaciente,
    onCancelar: () -> Unit,
    onGuardar: (totalSesiones: Int?, precioPaquete: Double?, precioPorSesion: Double?, precioAcordado: Double?) -> Unit,
) {
    val c = Sania.colors
    var totalSesiones by remember { mutableStateOf(t.totalSesiones.toString()) }
    var precioPaquete by remember { mutableStateOf(t.precioPaquete?.toString() ?: "") }
    var precioPorSesion by remember { mutableStateOf(t.precioPorSesion?.toString() ?: "") }
    var precioAcordado by remember { mutableStateOf(t.precioAcordado?.toString() ?: "") }
    val esPaquete = t.modalidad == "Paquete"
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("✏ Editar tratamiento", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (!t.esConsulta) {
                    Etq("N° de sesiones"); CampoNum(totalSesiones) { totalSesiones = it }
                    Spacer(Modifier.height(8.dp))
                    if (esPaquete) { Etq("Precio del paquete"); CampoNum(precioPaquete) { precioPaquete = it } }
                    else { Etq("Precio por sesión"); CampoNum(precioPorSesion) { precioPorSesion = it } }
                } else {
                    Etq("Costo de la consulta"); CampoNum(precioAcordado) { precioAcordado = it }
                }
            }
        },
        confirmButton = {
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.navy)
                .clickable {
                    onGuardar(
                        if (t.esConsulta) null else totalSesiones.toIntOrNull(),
                        if (esPaquete) precioPaquete.toDoubleOrNull() else null,
                        if (!esPaquete && !t.esConsulta) precioPorSesion.toDoubleOrNull() else null,
                        if (t.esConsulta) precioAcordado.toDoubleOrNull() else null,
                    )
                }.padding(horizontal = 18.dp, vertical = 10.dp)) {
                Text("Guardar", color = c.sobreNavy, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}

@Composable
private fun Etq(t: String) {
    Text(t, color = Sania.colors.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun CampoNum(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.filter { ch -> ch.isDigit() || ch == '.' }) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChipMod(texto: String, activo: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(if (activo) c.chipBg else c.superficie)
            .border(if (activo) 2.dp else 1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable { onClick() }.padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = if (activo) c.navy else c.texto, fontSize = 12.sp,
        fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal) }
}

@Composable
private fun SelectorBox(valor: String, bloqueado: Boolean = false, onClick: () -> Unit) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(if (bloqueado) c.chipBg else c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable(enabled = !bloqueado) { onClick() }.padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(valor, color = c.texto, fontSize = Sania.txt.cuerpo)
        Text(if (bloqueado) "🔒" else "▾", color = c.navy, fontSize = 12.sp)
    }
}

@Composable
private fun <T> SelectorLista(items: List<T>, elegido: T?, etiqueta: (T) -> String, placeholder: String, onElegir: (T) -> Unit) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(false) }
    Column {
        SelectorBox(elegido?.let(etiqueta) ?: placeholder) { abierto = !abierto }
        if (abierto) {
            LazyColumn(
                Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(max = 220.dp)
                    .clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)),
            ) {
                items(items) { item ->
                    Text(etiqueta(item), color = c.texto, fontSize = Sania.txt.cuerpo,
                        modifier = Modifier.fillMaxWidth().clickable { onElegir(item); abierto = false }
                            .padding(horizontal = 12.dp, vertical = 11.dp))
                }
            }
        }
    }
}
