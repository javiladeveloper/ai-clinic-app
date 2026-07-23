package pe.saniape.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.launch
import pe.saniape.app.data.SaludRepo

/**
 * Paso OBLIGATORIO del portal: pedir el DNI antes de mostrar nada más. El DNI es la
 * llave que conecta la cuenta (Google) con las fichas del paciente en sus clínicas
 * (muchas fichas no tienen correo, solo DNI). El server lo valida contra RENIEC +
 * coincidencia de nombre. Es el camino ideal, pero NO atrapa: se puede "Omitir por
 * ahora" (hay casos donde el DNI no basta — la clínica registró el nombre mal, o solo
 * el nombre sin DNI; para esos existe el código de clínica). Al omitir, entra igual y
 * ve lo que esté vinculado por correo/código; se le vuelve a pedir la próxima vez.
 */
@Composable
fun PantallaPedirDni(onListo: (String) -> Unit, onOmitir: () -> Unit) {
    val scope = rememberCoroutineScope()
    var dni by remember { mutableStateOf("") }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(Sand).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().background(Blanco, RoundedCornerShape(18.dp)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("🪪", fontSize = 40.sp)
            Text("Ingresa tu DNI", color = TextoPrincipal, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "Con tu DNI conectamos las atenciones que ya tuviste en tus clínicas. Es un paso único y seguro.",
                color = Muted, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = dni,
                onValueChange = { v -> dni = v.filter { it.isDigit() }.take(8); error = null },
                placeholder = { Text("Tu DNI (8 dígitos)", fontSize = 14.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = error != null,
                modifier = Modifier.fillMaxWidth(),
            )
            if (error != null) {
                Text(error!!, color = Color(0xFFDC2626), fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
            }
            Button(
                onClick = {
                    if (dni.length != 8 || guardando) return@Button
                    guardando = true; error = null
                    scope.launch {
                        val err = runCatching { SaludRepo.reclamarDni(dni) }.getOrElse { "No se pudo validar el DNI" }
                        guardando = false
                        if (err == null) onListo(dni) else error = err
                    }
                },
                enabled = dni.length == 8 && !guardando,
                colors = ButtonDefaults.buttonColors(containerColor = Navy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (guardando) CircularProgressIndicator(color = Blanco, modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
                else Text("Continuar", color = Blanco, fontWeight = FontWeight.Bold)
            }
            // Escape: no atrapar a nadie. Hay casos donde el DNI no basta (nombre mal
            // registrado en la clínica, o ficha sin DNI). Para esos, el código de clínica.
            Text(
                "Omitir por ahora",
                color = Muted, fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp).clickable(enabled = !guardando) { onOmitir() },
            )
        }
    }
}
