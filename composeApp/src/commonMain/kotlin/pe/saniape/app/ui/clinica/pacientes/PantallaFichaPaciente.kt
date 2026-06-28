package pe.saniape.app.ui.clinica.pacientes

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.PacienteStaff
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.SesionFicha
import pe.saniape.app.data.staff.TratamientoPaciente
import pe.saniape.app.ui.ManejarAtras
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.EstadosColor
import pe.saniape.app.ui.theme.Sania

/**
 * Ficha del paciente (staff). Resumen + tratamientos con su progreso. Acciones de
 * sesiones/pagos se agregan en el siguiente paso (reusan endpoints de la web).
 */
@Composable
fun PantallaFichaPaciente(ctx: ContextoStaff, pacienteInicial: PacienteStaff, onCerrar: () -> Unit) {
    val c = Sania.colors
    val acciones = recordarAcciones()
    ManejarAtras(activo = true, onAtras = onCerrar)

    val scope = rememberCoroutineScope()
    // Recarga fresca por id (el inicial viene de la lista; aquí traemos lo último).
    var paciente by remember { mutableStateOf(pacienteInicial) }
    var cargando by remember { mutableStateOf(true) }
    var completarSesion by remember { mutableStateOf<SesionFicha?>(null) }
    var editandoPaciente by remember { mutableStateOf(false) }
    var recargarToken by remember { mutableStateOf(0) }   // fuerza recarga de las tarjetas
    LaunchedEffect(pacienteInicial.id, recargarToken) {
        paciente = runCatching { PacientesRepo.porId(pacienteInicial.id) }.getOrNull() ?: pacienteInicial
        cargando = false
    }
    fun recargar() { recargarToken++ }

    val verContacto = ctx.esGestor && !ctx.modoClinico
    val flagColor = when (paciente.flag) {
        "rojo" -> c.error; "amarillo" -> c.pend; else -> c.ok
    }
    val estado = EstadosColor.paciente(paciente.estado)

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Toolbar nativa
            Row(
                Modifier.fillMaxWidth().background(c.navyDark)
                    .padding(horizontal = Sania.dim.lg, vertical = Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(c.sobreNavy.copy(alpha = 0.15f))
                        .clickable { onCerrar() },
                    contentAlignment = Alignment.Center,
                ) { Text("←", color = c.sobreNavy, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(Sania.dim.md))
                Text("Ficha del paciente", color = c.sobreNavy, fontSize = Sania.txt.subtitulo,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                // Editar paciente: solo gestor (no en modoClinico, que es vista sin edición).
                if (ctx.puede("pacientes")) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(c.sobreNavy.copy(alpha = 0.15f))
                            .clickable { editandoPaciente = true },
                        contentAlignment = Alignment.Center,
                    ) { Text("✏", color = c.sobreNavy, fontSize = 16.sp) }
                }
            }

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Sania.dim.xl)) {
                // ── Cabecera: nombre + semáforo + estado ──
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(flagColor))
                    Spacer(Modifier.width(Sania.dim.sm))
                    Text(paciente.nombre, color = c.texto, fontSize = Sania.txt.titulo, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(estado.bg)
                        .padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text(paciente.estado ?: "—", color = estado.fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Datos: edad · ocupación
                val datos = listOfNotNull(
                    paciente.edad?.let { "$it años" },
                    paciente.ocupacion,
                ).joinToString(" · ")
                if (datos.isNotBlank()) {
                    Text(datos, color = c.textoSuave, fontSize = Sania.txt.cuerpo,
                        modifier = Modifier.padding(top = 4.dp))
                }

                // Contacto (gestor): teléfono con 📞/💬
                if (verContacto) {
                    paciente.telefono?.takeIf { it.isNotBlank() }?.let { tel ->
                        Spacer(Modifier.height(Sania.dim.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BotonContacto("📞 Llamar", c.navy) { acciones.abrirUrl("tel:${tel.filter { ch -> ch.isDigit() }}") }
                            BotonContacto("💬 WhatsApp", androidx.compose.ui.graphics.Color(0xFF25D366)) {
                                val n = tel.filter { ch -> ch.isDigit() }.let { if (it.length <= 9) "51$it" else it }
                                acciones.abrirUrl("https://wa.me/$n")
                            }
                        }
                    }
                }

                // Motivo / diagnóstico
                paciente.diagnostico?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(Sania.dim.md))
                    Etiqueta("Motivo / Diagnóstico")
                    Text(it, color = c.texto, fontSize = Sania.txt.cuerpo)
                }

                Spacer(Modifier.height(Sania.dim.lg))

                // ── Tratamientos ──
                Etiqueta("Atenciones / Tratamientos")
                if (cargando) {
                    Box(Modifier.fillMaxWidth().padding(Sania.dim.lg), Alignment.Center) {
                        CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp)
                    }
                } else if (paciente.tratamientos.isEmpty()) {
                    Text("Sin tratamientos registrados.", color = c.textoSuave, fontSize = Sania.txt.cuerpo)
                } else {
                    paciente.tratamientos.forEach { t ->
                        Spacer(Modifier.height(Sania.dim.sm))
                        TarjetaTratamiento(
                            t = t,
                            verPagos = ctx.puede("pagos"),
                            esAdmin = ctx.esAdmin,
                            puedeSesiones = ctx.puede("sesiones"),
                            onCompletarSesion = { completarSesion = it },
                            onCambioRealizado = { recargar() },
                        )
                    }
                }

                Spacer(Modifier.height(Sania.dim.xxl))
            }
        }
    }

    // Modal "Editar paciente" (gestor): datos esenciales + semáforo.
    if (editandoPaciente) {
        ModalEditarPaciente(
            paciente = paciente,
            onCancelar = { editandoPaciente = false },
            onGuardar = { nombre, tel, ocup, edad, flag, diag ->
                editandoPaciente = false
                scope.launch {
                    PacientesRepo.actualizarPaciente(paciente.id, nombre, tel, ocup, edad, flag, diag)
                    recargar()
                }
            },
        )
    }

    // Modal "Completar sesión": observaciones (procedimientos realizados).
    completarSesion?.let { ses ->
        ModalCompletarSesion(
            ses = ses,
            onCancelar = { completarSesion = null },
            onConfirmar = { obs ->
                completarSesion = null
                scope.launch {
                    PacientesRepo.cambiarEstadoSesion(ses.id, "Completada", notas = obs)
                    recargar()
                }
            },
        )
    }
}

@Composable
private fun ModalCompletarSesion(ses: SesionFicha, onCancelar: () -> Unit, onConfirmar: (String?) -> Unit) {
    val c = Sania.colors
    var texto by remember { mutableStateOf(ses.notas ?: "") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("✓ Completar sesión #${ses.numero}", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Procedimientos realizados", color = c.textoSuave, fontSize = Sania.txt.mini,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = texto, onValueChange = { texto = it },
                    placeholder = { Text("Qué se hizo en la sesión…", color = c.textoSuave) },
                    modifier = Modifier.fillMaxWidth(), minLines = 3,
                )
            }
        },
        confirmButton = {
            Box(
                Modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.navy)
                    .clickable { onConfirmar(texto.trim().ifBlank { null }) }
                    .padding(horizontal = 20.dp, vertical = 11.dp),
            ) { Text("✓ Completar", color = c.sobreNavy, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave) }
        },
        containerColor = c.superficie,
    )
}

@Composable
private fun ModalEditarPaciente(
    paciente: PacienteStaff,
    onCancelar: () -> Unit,
    onGuardar: (nombre: String, tel: String?, ocup: String?, edad: Int?, flag: String?, diag: String?) -> Unit,
) {
    val c = Sania.colors
    var nombre by remember { mutableStateOf(paciente.nombre) }
    var telefono by remember { mutableStateOf(paciente.telefono ?: "") }
    var ocupacion by remember { mutableStateOf(paciente.ocupacion ?: "") }
    var edad by remember { mutableStateOf(paciente.edad?.toString() ?: "") }
    var diagnostico by remember { mutableStateOf(paciente.diagnostico ?: "") }
    var flag by remember { mutableStateOf(paciente.flag ?: "verde") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("✏ Editar paciente", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                CampoFicha("Nombre", nombre) { nombre = it }
                Spacer(Modifier.height(8.dp))
                CampoFicha("Teléfono", telefono) { telefono = it }
                Spacer(Modifier.height(8.dp))
                CampoFicha("Ocupación", ocupacion) { ocupacion = it }
                Spacer(Modifier.height(8.dp))
                CampoFicha("Edad", edad, soloNumero = true) { edad = it }
                Spacer(Modifier.height(8.dp))
                CampoFicha("Motivo / Diagnóstico", diagnostico, multilinea = true) { diagnostico = it }
                Spacer(Modifier.height(10.dp))
                Text("Comportamiento (semáforo)", color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("verde" to c.ok, "amarillo" to c.pend, "rojo" to c.error).forEach { (f, col) ->
                        val activo = flag == f
                        Box(
                            Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                                .background(if (activo) col else c.superficie)
                                .border(1.dp, col, RoundedCornerShape(Sania.shape.pill.dp))
                                .clickable { flag = f }.padding(horizontal = 14.dp, vertical = 6.dp),
                        ) { Text(f.replaceFirstChar { it.uppercase() }, color = if (activo) c.sobreNavy else col,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        },
        confirmButton = {
            Box(
                Modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.navy)
                    .clickable {
                        if (nombre.isBlank()) return@clickable
                        onGuardar(nombre.trim(), telefono.trim().ifBlank { null }, ocupacion.trim().ifBlank { null },
                            edad.toIntOrNull(), flag, diagnostico.trim().ifBlank { null })
                    }.padding(horizontal = 18.dp, vertical = 10.dp),
            ) { Text("Guardar", color = c.sobreNavy, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}

@Composable
private fun CampoFicha(
    label: String, value: String, soloNumero: Boolean = false, multilinea: Boolean = false, onChange: (String) -> Unit,
) {
    val c = Sania.colors
    Text(label, color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = { if (soloNumero) onChange(it.filter { ch -> ch.isDigit() }) else onChange(it) },
        singleLine = !multilinea, minLines = if (multilinea) 2 else 1,
        keyboardOptions = if (soloNumero) androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number) else androidx.compose.foundation.text.KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

@Composable
private fun Etiqueta(t: String) {
    Text(t, color = Sania.colors.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun BotonContacto(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(Sania.shape.sm.dp))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 7.dp),
    ) { Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}