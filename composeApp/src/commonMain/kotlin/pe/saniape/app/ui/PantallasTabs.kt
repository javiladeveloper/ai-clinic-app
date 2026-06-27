package pe.saniape.app.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.PerfilPaciente
import pe.saniape.app.data.PerfilRepo
import pe.saniape.app.data.ResultadoPerfil
import pe.saniape.app.ui.theme.Sania

/**
 * Tab Más — perfil del paciente (correo/nombre de Google = solo lectura; DNI de
 * una sola vez; teléfono editable) + cerrar sesión.
 */
@Composable
fun PantallaMas(nombre: String?, onCerrarSesion: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()

    var cargando by remember { mutableStateOf(true) }
    var perfil by remember { mutableStateOf<PerfilPaciente?>(null) }
    var telefono by remember { mutableStateOf("") }
    var dni by remember { mutableStateOf("") }
    var guardando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var mensajeOk by remember { mutableStateOf(false) }
    var confirmarDni by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val p = PerfilRepo.cargar()
            perfil = p
            telefono = p?.telefono ?: ""
            dni = p?.dni ?: ""
        } catch (_: Exception) {}
        cargando = false
    }

    // El DNI ya existe (no editable) si el perfil cargado lo tiene.
    val dniBloqueado = !perfil?.dni.isNullOrBlank()

    // Guardado real (se llama directo si no hay DNI nuevo, o tras confirmar el popup).
    fun ejecutarGuardado() {
        guardando = true
        scope.launch {
            val r = PerfilRepo.guardar(
                telefono = telefono,
                dni = if (!dniBloqueado && dni.isNotBlank()) dni else null,
            )
            guardando = false
            when (r) {
                is ResultadoPerfil.Ok -> {
                    mensaje = "Datos guardados"; mensajeOk = true
                    perfil = PerfilRepo.cargar() // refleja DNI bloqueado si recién se seteó
                }
                is ResultadoPerfil.Error -> { mensaje = r.mensaje; mensajeOk = false }
            }
        }
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // Barra de título
            Box(Modifier.fillMaxWidth().background(c.navyDark)
                .padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg)) {
                Text("Mi cuenta", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
            }

            Column(Modifier.fillMaxWidth().padding(Sania.dim.xl)) {
                if (cargando) {
                    Box(Modifier.fillMaxWidth().padding(Sania.dim.xxl), Alignment.Center) {
                        CircularProgressIndicator(color = c.navy)
                    }
                } else {
                    // ── Datos de la cuenta (de Google, solo lectura) ──
                    Text("MIS DATOS", color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(Sania.dim.sm))

                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
                            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
                            .padding(Sania.dim.lg),
                    ) {
                        DatoSoloLectura("Nombre", perfil?.nombre ?: nombre ?: "—")
                        Spacer(Modifier.height(Sania.dim.md))
                        DatoSoloLectura("Correo", perfil?.email ?: "—")
                    }

                    Spacer(Modifier.height(Sania.dim.lg))

                    // ── DNI: una sola vez ──
                    Text("DNI", color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    if (dniBloqueado) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                                .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(perfil?.dni ?: "", color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold)
                            Text("🔒 No editable", color = c.textoSuave, fontSize = 12.sp)
                        }
                    } else {
                        OutlinedTextField(
                            value = dni,
                            onValueChange = { dni = it.filter { ch -> ch.isDigit() }.take(8) },
                            placeholder = { Text("Ingresa tu DNI (8 dígitos)", color = c.textoSuave) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("⚠ El DNI solo se ingresa una vez. Verifica que sea correcto: después no se puede cambiar.",
                            color = c.pend, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }

                    Spacer(Modifier.height(Sania.dim.lg))

                    // ── Teléfono: editable ──
                    Text("TELÉFONO", color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = telefono,
                        onValueChange = { telefono = it },
                        placeholder = { Text("Tu número de contacto", color = c.textoSuave) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    mensaje?.let {
                        Spacer(Modifier.height(Sania.dim.md))
                        Text((if (mensajeOk) "✓ " else "⚠ ") + it,
                            color = if (mensajeOk) c.ok else c.error, fontSize = Sania.txt.pequeno)
                    }

                    Spacer(Modifier.height(Sania.dim.lg))
                    Button(
                        onClick = {
                            if (guardando) return@Button
                            mensaje = null
                            if (telefono.isBlank()) { mensaje = "Indica tu teléfono"; mensajeOk = false; return@Button }
                            val seteaDni = !dniBloqueado && dni.isNotBlank()
                            if (seteaDni && dni.length != 8) {
                                mensaje = "El DNI debe tener 8 dígitos"; mensajeOk = false; return@Button
                            }
                            // Si registra DNI por primera vez → confirmar (es irreversible).
                            if (seteaDni) confirmarDni = true else ejecutarGuardado()
                        },
                        enabled = !guardando,
                        shape = RoundedCornerShape(Sania.shape.md.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.navy, contentColor = c.sobreNavy),
                        modifier = Modifier.fillMaxWidth().height(Sania.dim.boton),
                    ) {
                        if (guardando) {
                            CircularProgressIndicator(color = c.sobreNavy, strokeWidth = 2.dp,
                                modifier = Modifier.height(20.dp).padding(end = 8.dp))
                        }
                        Text("Guardar cambios", fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(Sania.dim.xxl))

                    // ── Cerrar sesión ──
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                            .clickable { onCerrarSesion() }.padding(Sania.dim.lg),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Cerrar sesión", color = c.error, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.SemiBold)
                        Text("→", color = c.error, fontSize = Sania.txt.cuerpo)
                    }
                    Spacer(Modifier.height(Sania.dim.xl))
                }
            }
        }
    }

    // Popup de confirmación del DNI (irreversible).
    if (confirmarDni) {
        AlertDialog(
            onDismissRequest = { confirmarDni = false },
            title = { Text("Confirma tu DNI") },
            text = {
                Text(
                    "Vas a registrar el DNI $dni. Una vez guardado, NO se podrá cambiar. " +
                        "¿Es correcto?",
                    color = c.texto, fontSize = Sania.txt.cuerpo,
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmarDni = false; ejecutarGuardado() }) {
                    Text("Sí, es correcto", color = c.navy, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmarDni = false }) {
                    Text("Revisar", color = c.textoSuave)
                }
            },
            containerColor = c.superficie,
        )
    }
}

@Composable
private fun DatoSoloLectura(label: String, valor: String) {
    val c = Sania.colors
    Column {
        Text(label, color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(valor, color = c.texto, fontSize = Sania.txt.cuerpo)
    }
}