package pe.saniape.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.auth.AuthEmail
import pe.saniape.app.auth.recordarGoogleAuthLauncher

/**
 * Pantalla de login del paciente.
 *  - Botón principal: Google nativo.
 *  - Bloque TEMPORAL email+contraseña para poder probar sin la config de Google.
 * Look de marca Sania (igual que /paciente/login en la web).
 */
@Composable
fun PantallaLogin(onLogueado: () -> Unit) {
    val launcher = recordarGoogleAuthLauncher()
    val scope = rememberCoroutineScope()
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // TEMPORAL: email/contraseña
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Surface(color = Sand, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Sania", color = Navy, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Portal del paciente", color = Navy, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Ingresa para ver tus citas y reservar.",
                color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))

            error?.let {
                Text(
                    "⚠ $it",
                    color = RedDanger, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                )
            }

            // Botón Google (login definitivo)
            Button(
                onClick = {
                    if (cargando) return@Button
                    cargando = true; error = null
                    launcher.lanzar { exito, err ->
                        cargando = false
                        if (exito) onLogueado() else error = err
                    }
                },
                enabled = !cargando,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White, contentColor = Color(0xFF1F2937),
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    if (cargando) "Conectando…" else "Continuar con Google",
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Bloque TEMPORAL: email + contraseña ──────────────────────────
            Text(
                "— o entra con tu correo (temporal) —",
                color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email, imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password, imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))

            Button(
                onClick = {
                    if (cargando) return@Button
                    if (email.isBlank() || password.isBlank()) {
                        error = "Escribe tu correo y contraseña."; return@Button
                    }
                    cargando = true; error = null
                    scope.launch {
                        try {
                            AuthEmail.entrar(email, password)
                            onLogueado()
                        } catch (e: Exception) {
                            error = "Correo o contraseña incorrectos."
                        } finally {
                            cargando = false
                        }
                    }
                },
                enabled = !cargando,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (cargando) {
                    CircularProgressIndicator(
                        color = Color.White, strokeWidth = 2.dp,
                        modifier = Modifier.height(20.dp).padding(end = 8.dp),
                    )
                }
                Text("Entrar", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Al continuar aceptas los Términos y la Política de Privacidad.",
                color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center,
            )
        }
    }
}