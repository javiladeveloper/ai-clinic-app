package pe.saniape.app.ui.clinica

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.AltaRapidaRepo
import pe.saniape.app.data.staff.FichaDeBaja
import pe.saniape.app.data.staff.HallazgoPadron
import pe.saniape.app.data.staff.LocalTerminologiaPaciente
import pe.saniape.app.data.staff.ReactivarRepo
import pe.saniape.app.data.staff.RefNombre
import pe.saniape.app.data.staff.consultaPadron
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.theme.Sania

/**
 * "No hay ningún paciente con documento X" dentro del buscador de Crear cita —
 * gemelo del bloque `dniNuevo` de BuscadorPaciente (web):
 *  - DNI de 8 dígitos en sede de Perú → "🔍 Buscar en RENIEC" → "✓ Es correcto, registrar".
 *  - RENIEC sin datos, documento extranjero o sede de otro país → nombre a mano → "✓ Registrar".
 *  - Si el documento es de una ficha DADA DE BAJA → se ofrece reactivarla (con su historial).
 * Al registrar/reactivar, el paciente queda elegido en la cita ([onListo]).
 */
@Composable
internal fun PanelAltaRapida(
    documento: String,
    pais: String?,
    /** Reactivar pide 'datos_personales' en el servidor (como el botón de la web). */
    puedeReactivar: Boolean,
    onListo: (RefNombre) -> Unit,
) {
    val c = Sania.colors
    val tpl = LocalTerminologiaPaciente.current
    val scope = rememberCoroutineScope()
    var hallazgo by remember(documento) { mutableStateOf<HallazgoPadron?>(null) }
    var nombreManual by remember(documento) { mutableStateOf("") }
    var baja by remember(documento) { mutableStateOf<FichaDeBaja?>(null) }
    var reactivando by remember(documento) { mutableStateOf(false) }
    val conPadron = consultaPadron(documento, pais)
    // Documento que no es un DNI peruano: su tipo se elige. Se MUESTRA el que se
    // deduce (RUT en Perú como siempre, CI en Bolivia…), pero solo se guarda si
    // se elige a mano: lo adivinado queda null = el del país.
    val tiposAlta = remember(pais) {
        pe.saniape.app.data.staff.todasOpcionesDocumento(pais)
            .filter { !(pe.saniape.app.data.staff.usaReniec(pais) && it.first == "DNI") }
    }
    var tipoElegido by remember(documento) { mutableStateOf<String?>(null) }
    val tipoManual = tipoElegido ?: pe.saniape.app.data.staff.deducirTipoDocumento(documento, pais)

    // ¿Es alguien que se dio de baja y vuelve? Consulta chica a la base propia
    // (no al padrón), con pausa para no disparar una por tecla.
    LaunchedEffect(documento) {
        delay(400)
        baja = ReactivarRepo.porDocumento(documento)
    }

    fun registrar(nombre: String, tipo: String?) {
        if (nombre.isBlank() || hallazgo == HallazgoPadron.Creando) return
        hallazgo = HallazgoPadron.Creando
        scope.launch {
            val r = AltaRapidaRepo.registrar(nombre, documento, tipo)
            when {
                !r.ok -> hallazgo = HallazgoPadron.Error(r.error ?: "No se pudo registrar.")
                r.inactivo -> {
                    // No se creó nada: es una ficha de baja. Se ofrece reactivarla.
                    val f = ReactivarRepo.porDocumento(documento)
                    baja = f
                    hallazgo = if (f != null && puedeReactivar) null
                        else HallazgoPadron.Error("Ese documento es de un ${tpl.paciente} dado de baja. Reactívalo desde su ficha.")
                }
                else -> {
                    Toaster.exito("${tpl.Paciente} registrado")
                    onListo(RefNombre(r.id!!, nombre.trim(), documento, sedeId = r.sedeId))
                }
            }
        }
    }

    fun reactivar(f: FichaDeBaja) {
        if (reactivando) return
        reactivando = true
        scope.launch {
            val r = ReactivarRepo.reactivar(f.id)
            reactivando = false
            if (!r.ok || r.id == null) { hallazgo = HallazgoPadron.Error(r.error ?: "No se pudo reactivar"); baja = null; return@launch }
            Toaster.exito("Ficha reactivada")
            onListo(RefNombre(r.id, f.nombre, f.dni ?: documento))
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp)) {
        val b = baja
        if (b != null && hallazgo == null) {
            // Estaba DADO DE BAJA: se reactiva su ficha en vez de registrarlo otra vez.
            Text(b.nombre, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                "Estaba dado de baja" + (b.fechaBajaTexto?.let { " el $it" } ?: "") +
                    (if (b.nTratamientos > 0) " · su historial se conserva" else ""),
                color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp),
            )
            if (puedeReactivar) {
                BotonPanel(if (reactivando) "Reactivando…" else "↻ Reactivar su ficha y elegirlo", c.ok, habilitado = !reactivando) { reactivar(b) }
            } else {
                Text("Pide a quien gestiona ${tpl.pacientes} que lo reactive desde su ficha.",
                    color = c.textoSuave, fontSize = 12.sp)
            }
            return@Column
        }

        when (val h = hallazgo) {
            null -> {
                Text("No hay ningún ${tpl.paciente} con documento $documento",
                    color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (conPadron) {
                    Text("Búscalo en RENIEC y queda registrado al agendar.",
                        color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                    BotonPanel("🔍 Buscar $documento en RENIEC", c.navy) {
                        hallazgo = HallazgoPadron.Buscando
                        scope.launch { hallazgo = AltaRapidaRepo.consultarPadron(documento) }
                    }
                } else {
                    // Documento extranjero (o sede fuera de Perú): el padrón no lo
                    // conoce, así que el nombre se escribe a mano y se registra igual.
                    Text(
                        if (pe.saniape.app.data.staff.usaReniec(pais)) "Documento extranjero: escribe su nombre y queda registrado al agendar."
                        else "Escribe su nombre y queda registrado al agendar.",
                        color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                    )
                    pe.saniape.app.ui.clinica.ajustes.ChipsEleccion(tiposAlta, tipoManual) { tipoElegido = it }
                    Spacer(Modifier.height(6.dp))
                    CampoNombreManual(nombreManual) { nombreManual = it }
                    BotonPanel("✓ Registrar $documento", c.ok, habilitado = nombreManual.isNotBlank()) { registrar(nombreManual, tipoElegido) }
                }
            }
            HallazgoPadron.Buscando -> Text("Buscando en RENIEC…", color = c.textoSuave, fontSize = 13.sp)
            HallazgoPadron.Creando -> Text("Registrando…", color = c.textoSuave, fontSize = 13.sp)
            is HallazgoPadron.Encontrado -> {
                Text("ENCONTRADO", color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(h.nombre, color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("DNI $documento", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonPanel("✓ Es correcto, registrar", c.ok, modifier = Modifier.weight(1f)) { registrar(h.nombre, null) }
                    BotonPanel("Cancelar", null) { hallazgo = null }
                }
            }
            HallazgoPadron.SinDatos -> {
                Text("RENIEC no devolvió datos para $documento.", color = c.pend, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                CampoNombreManual(nombreManual) { nombreManual = it }
                BotonPanel("✓ Registrar con este nombre", c.ok, habilitado = nombreManual.isNotBlank()) { registrar(nombreManual, null) }
            }
            is HallazgoPadron.Error -> Text(h.mensaje, color = c.error, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CampoNombreManual(valor: String, onCambio: (String) -> Unit) {
    OutlinedTextField(
        value = valor, onValueChange = onCambio,
        placeholder = { Text("Nombre y apellidos", color = Sania.colors.textoSuave) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
}

/** Botón del panel: relleno de [color] o, con null, contorno gris (Cancelar). */
@Composable
private fun BotonPanel(
    texto: String, color: Color?, modifier: Modifier = Modifier, habilitado: Boolean = true, onClick: () -> Unit,
) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    // Los botones de ancho completo (relleno, sin modifier propio) ocupan la fila.
    val ancho = if (color != null && modifier == Modifier) Modifier.fillMaxWidth() else Modifier
    val base = modifier.then(ancho).clip(forma)
    val conFondo = if (color != null) base.background(if (habilitado) color else color.copy(alpha = 0.4f))
        else base.border(1.dp, c.borde, forma)
    Box(
        conFondo.clickable(enabled = habilitado) { onClick() }.padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = if (color != null) c.sobreNavy else c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
