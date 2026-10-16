package pe.saniape.app.ui.clinica.agenda.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.staff.AgendaRepo
import pe.saniape.app.data.staff.CapacidadMeet
import pe.saniape.app.data.staff.MODALIDAD_PRESENCIAL
import pe.saniape.app.data.staff.MODALIDAD_VIRTUAL
import pe.saniape.app.data.staff.ModoEnlace
import pe.saniape.app.data.staff.ValorModalidad
import pe.saniape.app.data.staff.textoCapacidadMeet
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.ui.theme.tocable

/**
 * "¿Dónde se atiende?" del formulario de la cita (gemelo de `SelectorModalidadCita`
 * de la web): 📍 En el local / 🎥 Videollamada y, si es virtual, Google Meet
 * automático (Premium+ con Google conectado) o un enlace (pegado, o el fijo del
 * servicio). Arranca con lo que dice el servicio; lo tocado a mano ya no lo pisa.
 *
 * La capacidad del Meet se pregunta al servidor SOLO cuando la cita es virtual
 * y el plan crea el Meet: una presencial no hace ni un viaje más.
 */
@Composable
fun SelectorModalidadCita(
    valor: ValorModalidad,
    onChange: (ValorModalidad) -> Unit,
    /** El plan crea el Meet solo (`ctx.can("citasVirtualesMeet")`). */
    meetPlan: Boolean,
    terapeutaId: String?,
    habilitado: Boolean = true,
) {
    val c = Sania.colors
    val acciones = recordarAcciones()
    var capacidad by remember { mutableStateOf<CapacidadMeet?>(null) }
    LaunchedEffect(valor.esVirtual, meetPlan, terapeutaId) {
        capacidad = if (valor.esVirtual && meetPlan) AgendaRepo.capacidadMeet(terapeutaId) else null
    }
    fun cambiar(v: ValorModalidad) = onChange(v.copy(tocado = true))
    // El plan no lo permite (o el servidor dijo que no): el Meet no se puede elegir.
    val meetElegible = meetPlan && capacidad?.permitido != false

    Column(Modifier.fillMaxWidth()) {
        Text("¿Dónde se atiende?", color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OpcionModalidad("📍 En el local", !valor.esVirtual, habilitado, Modifier.weight(1f)) {
                cambiar(valor.copy(modalidad = MODALIDAD_PRESENCIAL))
            }
            OpcionModalidad("🎥 Videollamada", valor.esVirtual, habilitado, Modifier.weight(1f)) {
                cambiar(valor.copy(
                    modalidad = MODALIDAD_VIRTUAL,
                    modo = if (valor.enlace.isNotBlank()) ModoEnlace.ENLACE else if (meetPlan) ModoEnlace.MEET else ModoEnlace.ENLACE,
                ))
            }
        }
        if (valor.esVirtual) {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.chipBg).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                    .padding(Sania.dim.md),
            ) {
                FilaRadio(
                    titulo = "Crear Google Meet automáticamente",
                    elegido = valor.modo == ModoEnlace.MEET,
                    habilitado = habilitado && meetElegible,
                    onClick = { cambiar(valor.copy(modo = ModoEnlace.MEET)) },
                ) {
                    textoCapacidadMeet(meetPlan, capacidad)?.let { t ->
                        val aviso = meetPlan && capacidad?.disponible == false
                        Text(t, color = if (aviso) c.pend else c.textoSuave, fontSize = 11.sp)
                        if (aviso && capacidad?.motivo != "plan") {
                            Text("🔗 Abrir Ajustes → Calendario", color = c.navy, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 2.dp).tocable {
                                    acciones.abrirUrl("${Supabase.SITE_URL.trimEnd('/')}/configuracion/calendario")
                                })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                FilaRadio(
                    titulo = "Usar un enlace (Meet, Zoom, Teams…)",
                    elegido = valor.modo == ModoEnlace.ENLACE,
                    habilitado = habilitado,
                    onClick = { cambiar(valor.copy(modo = ModoEnlace.ENLACE)) },
                ) {
                    if (valor.modo == ModoEnlace.ENLACE) {
                        OutlinedTextField(
                            value = valor.enlace,
                            onValueChange = { cambiar(valor.copy(enlace = it)) },
                            enabled = habilitado,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            placeholder = { Text("https://… (puedes pegarlo después)", color = c.textoSuave, fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                }
                if (valor.modo == ModoEnlace.MEET && (!meetPlan || capacidad?.disponible == false)) {
                    Text("La cita se guarda igual; el enlace se pega después desde la agenda.",
                        color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun OpcionModalidad(texto: String, activa: Boolean, habilitado: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(if (activa) c.navy else c.superficie)
            .border(2.dp, if (activa) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .tocable(habilitado = habilitado) { onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = if (activa) c.sobreNavy else c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun FilaRadio(
    titulo: String,
    elegido: Boolean,
    habilitado: Boolean,
    onClick: () -> Unit,
    debajo: @Composable () -> Unit = {},
) {
    val c = Sania.colors
    Row(verticalAlignment = Alignment.Top) {
        Text(if (elegido) "◉" else "○", color = if (habilitado) c.navy else c.textoSuave, fontSize = 16.sp,
            modifier = Modifier.tocable(habilitado = habilitado) { onClick() })
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, color = if (habilitado) c.texto else c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.tocable(habilitado = habilitado) { onClick() })
            debajo()
        }
    }
}
