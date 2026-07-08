package pe.saniape.app.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.auth.AuthStaff
import pe.saniape.app.auth.recordarAppleAuthLauncher
import pe.saniape.app.auth.recordarGoogleAuthLauncher

/**
 * Login de Sania con DOS métodos (regla de negocio):
 *  - Pacientes → Google (Credential Manager nativo).
 *  - Clínicas / staff → usuario + contraseña (mismo Supabase Auth que la web).
 */
@Composable
fun PantallaLogin(onLogueado: () -> Unit) {
    val launcher = recordarGoogleAuthLauncher()
    val appleLauncher = recordarAppleAuthLauncher()   // null en Android → sin botón Apple
    val scope = rememberCoroutineScope()

    var modoStaff by remember { mutableStateOf(false) }   // false = paciente (Google)
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var verPassword by remember { mutableStateOf(false) }   // ojito mostrar/ocultar

    Surface(color = Sand, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LogoSania(size = 64.dp)
            Spacer(Modifier.height(10.dp))
            Text("Sania", color = Navy, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (modoStaff) "Acceso para clínicas" else "Portal del paciente",
                color = Navy, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (modoStaff) "Ingresa con tu usuario y contraseña."
                else "Ingresa para ver tus citas y reservar.",
                color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(34.dp))

            error?.let {
                Text(
                    "⚠ $it",
                    color = RedDanger, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                )
            }

            if (!modoStaff) {
                // ── Paciente: Google ──
                Button(
                    onClick = {
                        if (cargando) return@Button
                        cargando = true; error = null
                        // La PUERTA elegida manda — y se guarda ANTES de lanzar el login:
                        // el router reacciona al evento de sesión de Supabase, que puede llegar
                        // antes que el callback (guardarlo después perdía la carrera y una
                        // cuenta multi-rol caía en clínica).
                        pe.saniape.app.data.Preferencias.setModoActivo("paciente")
                        launcher.lanzar { exito, err ->
                            cargando = false
                            if (exito) onLogueado() else error = err
                        }
                    },
                    enabled = !cargando,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Blanco, contentColor = TextoPrincipal),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (cargando) {
                        CircularProgressIndicator(color = Navy, strokeWidth = 2.dp,
                            modifier = Modifier.height(20.dp).padding(end = 8.dp))
                        Text("Conectando…", fontWeight = FontWeight.Bold)
                    } else {
                        Text("Continuar con Google", fontWeight = FontWeight.Bold)
                    }
                }

                // ── Paciente: Apple (solo iOS; la App Store lo exige junto a Google) ──
                if (appleLauncher != null) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (cargando) return@Button
                            cargando = true; error = null
                            pe.saniape.app.data.Preferencias.setModoActivo("paciente")
                            appleLauncher.lanzar { exito, err ->
                                cargando = false
                                if (exito) onLogueado() else error = err
                            }
                        },
                        enabled = !cargando,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.Black, contentColor = Blanco),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        //  renderiza el logo de Apple en dispositivos Apple.
                        Text("  Continuar con Apple", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // ── Staff: usuario + contraseña ──
                // colors explícito: el texto que se escribe DEBE ser oscuro y visible
                // (el default de Material3 lo dejaba en un gris tenue casi invisible).
                val coloresCampo = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextoPrincipal,
                    unfocusedTextColor = TextoPrincipal,
                    cursorColor = Navy,
                    focusedBorderColor = Navy,
                    unfocusedBorderColor = BorderColor,
                    focusedContainerColor = Blanco,
                    unfocusedContainerColor = Blanco,
                )
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    placeholder = { Text("Correo", color = Muted) },
                    singleLine = true,
                    colors = coloresCampo,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    placeholder = { Text("Contraseña", color = Muted) },
                    singleLine = true,
                    colors = coloresCampo,
                    // Ojito para ver/ocultar la contraseña (evita errores al tipear).
                    visualTransformation = if (verPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Text(
                            text = if (verPassword) "🙈" else "👁",
                            fontSize = 18.sp,
                            modifier = Modifier
                                .clickable { verPassword = !verPassword }
                                .padding(horizontal = 12.dp),
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (cargando) return@Button
                        if (email.isBlank() || password.isBlank()) { error = "Completa correo y contraseña"; return@Button }
                        cargando = true; error = null
                        // Entró por "Acceso clínicas" → modo clínica (ANTES de lanzar, ver arriba).
                        pe.saniape.app.data.Preferencias.setModoActivo("clinica")
                        scope.launch {
                            val err = AuthStaff.ingresar(email, password)
                            cargando = false
                            if (err == null) onLogueado() else error = err
                        }
                    },
                    enabled = !cargando,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Blanco),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (cargando) {
                        CircularProgressIndicator(color = Blanco, strokeWidth = 2.dp,
                            modifier = Modifier.height(20.dp).padding(end = 8.dp))
                        Text("Ingresando…", fontWeight = FontWeight.Bold)
                    } else {
                        Text("Ingresar", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Alternar entre paciente (Google) y clínica (usuario/contraseña).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (modoStaff) "¿Eres paciente?" else "¿Eres de una clínica?",
                    color = Muted, fontSize = 13.sp,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (modoStaff) "Entrar con Google" else "Acceso clínicas",
                    color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { modoStaff = !modoStaff; error = null },
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Al continuar aceptas los Términos y la Política de Privacidad.",
                color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center,
            )
        }
    }
}