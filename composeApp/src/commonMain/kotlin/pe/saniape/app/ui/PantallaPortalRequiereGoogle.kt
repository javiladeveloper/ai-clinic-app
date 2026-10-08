package pe.saniape.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.ui.theme.Sania

/**
 * Portal del paciente abierto con una sesión de usuario+clave (staff que pasó a
 * "modo paciente"). El servidor solo da datos de paciente a sesiones que
 * entraron con Google/Apple — una clave la pudo poner un tercero (registro de
 * clínica, alta en Equipo), y son historias clínicas. En vez de pedir un DNI
 * que el servidor igual rechazaría, se explica qué hacer.
 */
@Composable
fun PantallaPortalRequiereGoogle(
    puedeIrAClinica: Boolean,
    onIrAClinica: () -> Unit,
    onCerrarSesion: () -> Unit,
) {
    val c = Sania.colors
    Box(Modifier.fillMaxSize().background(c.fondo).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().background(c.superficie, RoundedCornerShape(18.dp)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("🔒", fontSize = 40.sp)
            Text(
                "Tu portal de paciente se abre con Google",
                color = c.texto, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            Text(
                "Entraste con el usuario y la contraseña de la clínica. Para cuidar tus datos de salud, " +
                    "tu historial como paciente solo se muestra si entras con Google (o Apple) en \"Soy paciente\".",
                color = c.textoSuave, fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onCerrarSesion,
                colors = ButtonDefaults.buttonColors(containerColor = Navy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Cerrar sesión y entrar con Google", color = Blanco, fontWeight = FontWeight.Bold) }
            if (puedeIrAClinica) {
                Text(
                    "← Volver a mi clínica",
                    color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp).clickable { onIrAClinica() },
                )
            }
        }
    }
}
