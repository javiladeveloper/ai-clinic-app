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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.Apoderado
import pe.saniape.app.data.staff.DatosApoderado
import pe.saniape.app.data.staff.PacienteStaff
import pe.saniape.app.ui.theme.Sania

/**
 * Apoderado en el formulario del paciente (alta y edición) — gemelo de la web.
 * Con edad < 18 la sección aparece sola; para un adulto, la casilla "Necesita
 * apoderado" la abre. Nada es obligatorio: si falta nombre o DNI, solo se avisa.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeccionApoderado(
    esMenor: Boolean,
    datos: DatosApoderado,
    onChange: (DatosApoderado) -> Unit,
) {
    val c = Sania.colors
    val necesita = Apoderado.necesita(esMenor, datos.requiere)

    // Adulto: la casilla para pedir representante. Al menor no se le pregunta.
    if (!esMenor) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { onChange(datos.copy(requiere = !datos.requiere)) },
        ) {
            Checkbox(checked = datos.requiere, onCheckedChange = { onChange(datos.copy(requiere = it)) },
                colors = CheckboxDefaults.colors(checkedColor = c.navy))
            Text("Necesita apoderado (adulto con representante)", color = c.textoSuave, fontSize = 12.sp)
        }
    }
    if (!necesita) return

    Spacer(Modifier.height(8.dp))
    TarjetaForm(titulo = if (esMenor) "Apoderado (menor de edad)" else "Apoderado", icono = "👪") {
        if (Apoderado.falta(true, datos.nombre, datos.dni)) {
            AvisoFaltaApoderado()
            Spacer(Modifier.height(10.dp))
        }
        EtqForm("Nombre del apoderado")
        OutlinedTextField(colors = coloresCampoForm(), value = datos.nombre,
            onValueChange = { onChange(datos.copy(nombre = it)) },
            placeholder = { Text("Nombres y apellidos", color = c.textoSuave) },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                EtqForm("DNI")
                OutlinedTextField(colors = coloresCampoForm(), value = datos.dni,
                    // Puede ser RUT o pasaporte (Tacna es frontera): no se filtra a dígitos.
                    onValueChange = { onChange(datos.copy(dni = it.take(20))) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())
            }
            Column(Modifier.weight(1f)) {
                EtqForm("Teléfono")
                OutlinedTextField(colors = coloresCampoForm(), value = datos.telefono,
                    onValueChange = { onChange(datos.copy(telefono = it.filter { ch -> ch.isDigit() || ch == '+' }.take(15))) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(10.dp))
        EtqForm("Parentesco")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Apoderado.PARENTESCOS.forEach { p ->
                val activo = datos.parentesco == p
                Box(
                    Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                        .background(if (activo) c.navy else c.superficie)
                        .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
                        // Tocar el elegido lo quita (el campo no es obligatorio).
                        .clickable { onChange(datos.copy(parentesco = if (activo) "" else p)) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(p, color = if (activo) c.sobreNavy else c.texto, fontSize = 12.sp,
                        fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { onChange(datos.copy(recibeAvisos = !datos.recibeAvisos)) },
        ) {
            Text("Enviar los recordatorios de WhatsApp al apoderado", color = c.texto, fontSize = 13.sp,
                modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Switch(checked = datos.recibeAvisos, onCheckedChange = { onChange(datos.copy(recibeAvisos = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = c.navy))
        }
    }
}

/** Aviso ámbar "Falta el apoderado (nombre y DNI)". No bloquea nada. */
@Composable
fun AvisoFaltaApoderado(modifier: Modifier = Modifier) {
    val c = Sania.colors
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(c.pendBg).border(1.dp, c.pend, RoundedCornerShape(Sania.shape.sm.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text("⚠ ${Apoderado.AVISO_FALTA}", color = c.pend, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Badge junto al nombre: "Menor" o "Con apoderado". [texto] null = nada. */
@Composable
fun BadgeApoderado(texto: String?, modifier: Modifier = Modifier) {
    if (texto == null) return
    val c = Sania.colors
    Box(
        modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.purpleBg)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(texto, color = c.purple, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/**
 * El apoderado en la ficha: nombre, parentesco, DNI y teléfono (el teléfono solo
 * para quien ve contactos), o el aviso ámbar si lo necesita y falta.
 */
@Composable
fun TarjetaApoderadoFicha(paciente: PacienteStaff, verContacto: Boolean) {
    val c = Sania.colors
    val necesita = Apoderado.necesita(paciente.esMenor, paciente.requiereApoderado)
    val hay = !paciente.apoderadoNombre.isNullOrBlank() || !paciente.apoderadoDni.isNullOrBlank()
    if (!necesita && !hay) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text("👪 APODERADO", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        if (hay) {
            val linea1 = listOfNotNull(
                paciente.apoderadoNombre?.takeIf { it.isNotBlank() },
                paciente.apoderadoParentesco?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (linea1.isNotBlank()) {
                Text(linea1, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 3.dp))
            }
            val linea2 = listOfNotNull(
                paciente.apoderadoDni?.takeIf { it.isNotBlank() }?.let { "DNI $it" },
                paciente.apoderadoTelefono?.takeIf { it.isNotBlank() && verContacto }?.let { "Tel. $it" },
            ).joinToString(" · ")
            if (linea2.isNotBlank()) Text(linea2, color = c.textoSuave, fontSize = 12.sp)
            if (paciente.apoderadoRecibeAvisos) {
                Text("Recibe los recordatorios de WhatsApp", color = c.textoSuave, fontSize = 11.sp)
            }
        }
        if (paciente.faltaApoderado) {
            Spacer(Modifier.height(6.dp))
            AvisoFaltaApoderado()
        }
    }
}
