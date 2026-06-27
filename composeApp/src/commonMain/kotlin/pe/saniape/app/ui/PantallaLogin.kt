package pe.saniape.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.auth.recordarGoogleAuthLauncher

/**
 * Pantalla de login del paciente — SOLO Google nativo.
 * Look de marca Sania (igual que /paciente/login en la web).
 */
@Composable
fun PantallaLogin(onLogueado: () -> Unit) {
    val launcher = recordarGoogleAuthLauncher()
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Surface(color = Sand, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LogoSania(size = 64.dp)
            Spacer(Modifier.height(10.dp))
            Text("Sania", color = Navy, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Portal del paciente", color = Navy, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Ingresa para ver tus citas y reservar.",
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
                    containerColor = Blanco, contentColor = TextoPrincipal,
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (cargando) {
                    CircularProgressIndicator(
                        color = Navy, strokeWidth = 2.dp,
                        modifier = Modifier.height(20.dp).padding(end = 8.dp),
                    )
                    Text("Conectando…", fontWeight = FontWeight.Bold)
                } else {
                    Text("Continuar con Google", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Al continuar aceptas los Términos y la Política de Privacidad.",
                color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center,
            )
        }
    }
}