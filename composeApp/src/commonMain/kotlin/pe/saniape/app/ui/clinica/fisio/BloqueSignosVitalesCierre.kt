package pe.saniape.app.ui.clinica.fisio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import pe.saniape.app.ui.Toaster
import pe.saniape.app.data.staff.AtencionMedicaRepo
import pe.saniape.app.data.staff.VitalesCierreRepo
import pe.saniape.app.data.staff.antecedentesDeRiesgo
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.medicionesDeCierre
import pe.saniape.app.data.staff.resumenVitales
import pe.saniape.app.ui.clinica.agenda.modales.VitalesCampos
import pe.saniape.app.ui.theme.Sania

/**
 * Estado del bloque «🩺 Signos vitales» del cierre (data/staff/VitalesCierre).
 * [valores] null = no se tocó: lo que tomó recepción queda tal cual y [guardar]
 * no escribe nada. Quien completa llama a [guardar] DESPUÉS de completar.
 */
@Stable
class EstadoVitalesCierre(val mediciones: List<String>) {
    var citaId by mutableStateOf<String?>(null)
    var listo by mutableStateOf(false)
    var previo by mutableStateOf<Map<String, String>>(emptyMap())
    var valores by mutableStateOf<Map<String, String>?>(null)
    var riesgo by mutableStateOf<List<String>>(emptyList())
    var edad by mutableStateOf<Int?>(null)
    var abierto by mutableStateOf(false)

    suspend fun guardar(): Boolean = VitalesCierreRepo.guardar(citaId, valores, mediciones)
}

/**
 * El estado del bloque, o null si no aplica (sin «Historia clínica», o la clínica
 * no toma triaje en esta especialidad: [activo]). Sin cita ligada el bloque no se
 * pinta. Precarga lo que ya tenga la cita y lee los antecedentes del paciente.
 */
@Composable
fun recordarVitalesCierre(
    activo: Boolean,
    citaId: String?,
    sesionId: String?,
    pacienteId: String?,
    camposClinica: List<String>,
): EstadoVitalesCierre? {
    val est = remember(citaId, sesionId) { EstadoVitalesCierre(medicionesDeCierre(camposClinica)) }
    LaunchedEffect(est, activo) {
        if (!activo) return@LaunchedEffect
        val cid = VitalesCierreRepo.citaDeAtencion(citaId, sesionId) ?: return@LaunchedEffect
        est.citaId = cid
        coroutineScope {
            val datos = async { runCatching { AtencionMedicaRepo.datosTriaje(cid, pacienteId, hoyClinicaIso()) }.getOrNull() }
            val ant = async { VitalesCierreRepo.antecedentes(pacienteId) }
            val d = datos.await()
            est.previo = d?.valores.orEmpty()
            est.edad = d?.edad
            est.riesgo = antecedentesDeRiesgo(ant.await())
        }
        // Con HTA, diabetes… la toma es de cada sesión: el bloque arranca abierto.
        if (est.riesgo.isNotEmpty()) est.abierto = true
        est.listo = true
    }
    return est.takeIf { activo }
}

private val alcanceVitales = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Tras completar: guarda los signos vitales escritos como el triaje de la cita
 * (sin señal, a la cola, igual que el triaje). Sin completar, no se guarda nada.
 */
fun vitalesTrasCompletar(ok: Boolean, est: EstadoVitalesCierre?) {
    if (!ok || est == null || est.valores == null) return
    alcanceVitales.launch {
        if (!est.guardar()) Toaster.error("La atención se completó, pero los signos vitales no se guardaron: regístralos con 🩺 Triaje.")
    }
}

@Composable
fun BloqueSignosVitalesCierre(est: EstadoVitalesCierre) {
    if (!est.listo) return
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.md.dp)
    val valores = est.valores ?: est.previo
    val resumen = resumenVitales(valores)
    val conRiesgo = est.riesgo.isNotEmpty()
    Column(
        Modifier.fillMaxWidth().clip(forma).border(1.5.dp, if (conRiesgo) c.pend else c.borde, forma).background(c.superficie),
    ) {
        Row(
            Modifier.fillMaxWidth().semantics { role = Role.Button }
                .clickable { est.abierto = !est.abierto }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🩺 Signos vitales", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    if (conRiesgo) Text("· tomar en cada sesión", color = c.pend, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    else Text("(opcional)", color = c.textoSuave, fontSize = 12.sp)
                }
                if (conRiesgo) {
                    Text("⚠ Antecedente: ${est.riesgo.joinToString(", ")}", color = c.pend, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
                }
                Text(
                    when {
                        resumen.isNotEmpty() && est.valores == null -> "Tomados en el triaje: $resumen"
                        resumen.isNotEmpty() -> resumen
                        else -> "Presión, frecuencia cardiaca, saturación… de esta sesión"
                    },
                    color = c.textoSuave, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(if (est.abierto) "Ocultar" else if (resumen.isNotEmpty()) "Editar" else "Agregar",
                color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        if (est.abierto) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
                VitalesCampos(valores = valores, onChange = { est.valores = it }, edad = est.edad, campos = est.mediciones)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}
