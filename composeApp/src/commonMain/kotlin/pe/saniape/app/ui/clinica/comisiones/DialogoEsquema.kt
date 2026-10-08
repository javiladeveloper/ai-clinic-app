package pe.saniape.app.ui.clinica.comisiones

import pe.saniape.app.data.staff.LocalTerminologiaPaciente
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.AjusteForm
import pe.saniape.app.data.staff.CatalogosCobroRepo
import pe.saniape.app.data.staff.ComisionesRepo
import pe.saniape.app.data.staff.EsquemaComision
import pe.saniape.app.data.staff.FormEsquema
import pe.saniape.app.data.staff.MetodoPago
import pe.saniape.app.data.staff.MiembroEquipoComision
import pe.saniape.app.data.staff.PersonaComision
import pe.saniape.app.data.staff.ReglasComisiones
import pe.saniape.app.data.staff.TramoForm
import pe.saniape.app.data.staff.nuevoUuid
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.equipo.OpcionElegible
import pe.saniape.app.ui.clinica.equipo.escribir
import pe.saniape.app.ui.clinica.finanzas.ChipFin
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.theme.Sania

/**
 * Nuevo / editar esquema de comisión (gemelo de FormPlantilla de la web): por
 * metas (pirámide de niveles, de paquetes o de evaluaciones) o un % de lo que
 * cobra; qué paquetes cuentan; cada cuánto se reinicia; quiénes están y lo
 * pactado con cada uno (su %, si sale sola al cobrar, con qué se le paga).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DialogoEsquema(
    esquema: EsquemaComision?,
    personal: List<PersonaComision>,
    onCerrar: () -> Unit,
    onGuardado: () -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var f by remember { mutableStateOf(FormEsquema.desde(esquema)) }
    // El id del esquema NUEVO, uno por formulario: un reintento no duplica.
    val idNuevo = remember { if (esquema == null) nuevoUuid() else null }
    var guardando by remember { mutableStateOf(false) }
    var personas by remember { mutableStateOf(personal) }
    var equipo by remember { mutableStateOf<List<MiembroEquipoComision>>(emptyList()) }
    var metodos by remember { mutableStateOf<List<MetodoPago>>(emptyList()) }
    LaunchedEffect(Unit) {
        if (personas.isEmpty()) personas = ComisionesRepo.personalActivo()
        metodos = CatalogosCobroRepo.metodosPago()
        equipo = ComisionesRepo.equipoSinFicha(personas)
    }
    val nombresMetodo = metodos.map { it.nombre }.ifEmpty { ReglasComisiones.METODOS_BASE }

    fun guardar() {
        if (guardando) return
        ReglasComisiones.validar(f) { id -> personas.firstOrNull { it.id == id }?.nombre }?.let { Toaster.error(it); return }
        guardando = true
        scope.launch {
            try {
                val r = escribir(porDefecto = "No se pudo guardar") { ComisionesRepo.guardar(f, idNuevo) }
                if (r.registrada) { Toaster.exito("Esquema guardado"); onGuardado() }
            } finally { guardando = false }
        }
    }

    fun ajustar(id: String, cambio: (AjusteForm) -> AjusteForm) {
        f = f.copy(ajustes = f.ajustes + (id to cambio(f.ajusteDe(id))))
    }

    DialogoForm(
        titulo = if (esquema != null) "Editar esquema" else "Nuevo esquema de comisión",
        subtitulo = null,
        textoAccion = if (guardando) "Guardando…" else "Guardar",
        accionHabilitada = !guardando,
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = { guardar() },
    ) {
        // Lo primero, porque cambia todo lo de abajo.
        EtqForm("Cómo se le paga")
        OpcionElegible("Por metas", detalle = "Llega a X paquetes y cobra un bono", elegida = f.tipo == "tramos") { f = f.copy(tipo = "tramos") }
        OpcionElegible("Un % de lo que cobra", detalle = "Sin sueldo fijo: se lleva su parte", elegida = f.tipo == "porcentaje") { f = f.copy(tipo = "porcentaje") }

        Spacer(Modifier.height(Sania.dim.md))
        EtqForm("Nombre del esquema")
        OutlinedTextField(
            value = f.nombre, onValueChange = { f = f.copy(nombre = it) }, singleLine = true,
            placeholder = { Text(if (f.tipo == "porcentaje") "Ej. Masajistas" else "Ej. Pirámide paquetes de 10") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
        )

        if (f.tipo == "porcentaje") {
            Spacer(Modifier.height(Sania.dim.md))
            EtqForm("Porcentaje por defecto")
            Row(verticalAlignment = Alignment.CenterVertically) {
                CampoNumero(f.porcentaje, "40", Modifier.width(100.dp)) { f = f.copy(porcentaje = it) }
                Text("  % de lo cobrado", color = c.textoSuave, fontSize = 13.sp)
            }
            Ayuda("Es el que se usa para quien no tenga uno propio. Más abajo puedes ponerle a cada persona el suyo: dos personas con porcentajes distintos pueden estar en este mismo esquema.")
        }

        if (f.tipo == "tramos") {
            Spacer(Modifier.height(Sania.dim.md))
            EtqForm("Qué se cuenta")
            OpcionElegible("Paquetes vendidos", detalle = "Pirámide de los profesionales", elegida = f.unidad == "paquetes") { f = f.copy(unidad = "paquetes") }
            OpcionElegible("Evaluaciones atendidas", detalle = "Bono de recepción: las de toda la clínica", elegida = f.unidad == "evaluaciones") { f = f.copy(unidad = "evaluaciones") }
            if (f.porEvaluaciones) {
                Ayuda("Cuentan las citas de Evaluación o Consulta que quedaron atendidas en el mes. Si pones a un profesional, cuentan solo las suyas.")
            }
        }

        if (!f.porEvaluaciones) {
            Spacer(Modifier.height(Sania.dim.md))
            EtqForm("Qué paquetes cuentan")
            Row(verticalAlignment = Alignment.CenterVertically) {
                CampoNumero(f.minSes, "10", Modifier.width(80.dp), decimal = false) { f = f.copy(minSes = it) }
                Text("  a  ", color = c.textoSuave, fontSize = 13.sp)
                CampoNumero(f.maxSes, "10", Modifier.width(80.dp), decimal = false) { f = f.copy(maxSes = it) }
                Text("  sesiones", color = c.textoSuave, fontSize = 13.sp)
            }
            Ayuda(
                if (f.tipo == "porcentaje") "Déjalo vacío para que cuente todo lo que cobre. Solo usa el rango si el porcentaje aplica a paquetes de un tamaño concreto."
                else "Es lo que separa un esquema de otro: pon 10 a 10 para que solo cuenten los paquetes de 10. Deja el segundo vacío para “de esa cantidad en adelante”, o los dos vacíos para que cuenten todos.",
            )
            Spacer(Modifier.height(Sania.dim.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Solo paquetes de S/  ", color = c.textoSuave, fontSize = 13.sp)
                CampoNumero(f.montoPaq, "cualquiera", Modifier.width(130.dp)) { f = f.copy(montoPaq = it) }
            }
            Ayuda("Si lo llenas, un paquete cobrado a otro precio (más caro o con descuento) no cuenta. Vacío = cuenta cualquier monto.")
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { f = f.copy(soloNuevos = !f.soloNuevos) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = f.soloNuevos, onCheckedChange = { f = f.copy(soloNuevos = it) },
                    colors = CheckboxDefaults.colors(checkedColor = c.navy))
                Column(Modifier.weight(1f)) {
                    Text("Solo ${LocalTerminologiaPaciente.current.pacientes} nuevos", color = c.texto, fontSize = 14.sp)
                    Text("Las renovaciones no cuentan: si el ${LocalTerminologiaPaciente.current.paciente} ya había pagado un paquete antes, el siguiente no suma.",
                        color = c.textoSuave, fontSize = 11.sp)
                }
            }
        }

        if (f.tipo == "tramos") {
            Spacer(Modifier.height(Sania.dim.md))
            EtqForm("Niveles")
            f.tramos.forEachIndexed { i, t ->
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(colorNivel(i)))
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(
                        value = t.nombre, onValueChange = { v -> f = f.copy(tramos = f.tramos.mapIndexed { j, x -> if (j == i) x.copy(nombre = v) else x }) },
                        singleLine = true, placeholder = { Text("Nivel") }, colors = coloresCampoForm(), modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(4.dp))
                    CampoNumero(t.objetivo, if (f.porEvaluaciones) "eval." else "paq.", Modifier.width(72.dp), decimal = false) { v ->
                        f = f.copy(tramos = f.tramos.mapIndexed { j, x -> if (j == i) x.copy(objetivo = v) else x })
                    }
                    Text(" S/", color = c.textoSuave, fontSize = 12.sp)
                    CampoNumero(t.montoBono, "bono", Modifier.width(84.dp)) { v ->
                        f = f.copy(tramos = f.tramos.mapIndexed { j, x -> if (j == i) x.copy(montoBono = v) else x })
                    }
                    Text("✕", color = c.textoSuave, fontSize = 14.sp,
                        modifier = Modifier.clip(CircleShape).clickable { f = f.copy(tramos = f.tramos.filterIndexed { j, _ -> j != i }) }.padding(6.dp))
                }
            }
            Text("+ Agregar nivel", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { f = f.copy(tramos = f.tramos + TramoForm()) }.padding(vertical = 6.dp))
            Ayuda(
                "Se paga el nivel más alto alcanzado: " +
                    (if (f.porEvaluaciones) "con 56 ${LocalTerminologiaPaciente.current.pacientes} evaluados se cobra el bono del nivel de 50, no cero." else "con 14 paquetes se cobra el bono del nivel de 12, no cero.") +
                    " Los bonos no se suman entre niveles.",
            )
        }

        Spacer(Modifier.height(Sania.dim.md))
        EtqForm("Cada cuánto se reinicia")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Cada  ", color = c.textoSuave, fontSize = 13.sp)
            CampoNumero(f.periodoMeses, "1", Modifier.width(80.dp), decimal = false) { f = f.copy(periodoMeses = it) }
            Text("  ${if (f.periodoMeses.trim() == "1") "mes" else "meses"}", color = c.textoSuave, fontSize = 13.sp)
        }

        Spacer(Modifier.height(Sania.dim.md))
        EtqForm("Quiénes están en este esquema")
        if (personas.isEmpty()) Text("No hay profesionales activos.", color = c.textoSuave, fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            personas.forEach { t ->
                val activo = t.id in f.asignados
                ChipFin(t.nombre, activo = activo) {
                    f = f.copy(asignados = if (activo) f.asignados - t.id else f.asignados + t.id)
                }
            }
        }
        // Recepción y el resto del equipo: solo en esquemas por evaluaciones.
        if (f.porEvaluaciones && equipo.isNotEmpty()) {
            Spacer(Modifier.height(Sania.dim.sm))
            Text("EQUIPO (RECEPCIÓN Y OTROS)", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                equipo.forEach { m ->
                    val activo = m.id in f.asignadosEquipo
                    val etiqueta = if (m.rol.isNotBlank() && m.rol != m.nombre) "${m.nombre} · ${m.rol}" else m.nombre
                    ChipFin(etiqueta, activo = activo) {
                        f = f.copy(asignadosEquipo = if (activo) f.asignadosEquipo - m.id else f.asignadosEquipo + m.id)
                    }
                }
            }
            Ayuda("Cada persona del equipo que marques cobra el bono del nivel que alcance la clínica.")
        }
        Ayuda("Una persona puede estar en varios esquemas a la vez y cobra de todos los que alcance.")

        // Lo pactado con CADA persona (su %, cuándo y con qué se le paga).
        if (f.asignados.isNotEmpty()) {
            Spacer(Modifier.height(Sania.dim.md))
            EtqForm("Lo pactado con cada uno")
            f.asignados.forEach { id ->
                val nombre = personas.firstOrNull { it.id == id }?.nombre ?: "—"
                val aj = f.ajusteDe(id)
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .background(c.superficie).padding(10.dp),
                ) {
                    Text(nombre, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (f.tipo == "porcentaje") {
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            CampoNumero(aj.porcentaje, f.porcentaje.ifBlank { "40" }, Modifier.width(100.dp)) { v -> ajustar(id) { it.copy(porcentaje = v) } }
                            Text("  %", color = c.textoSuave, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChipFin("La reviso antes de pagar", activo = aj.liquidacion != "egreso") { ajustar(id) { it.copy(liquidacion = "aprobar") } }
                        ChipFin("Sale sola al cobrar", activo = aj.liquidacion == "egreso") { ajustar(id) { it.copy(liquidacion = "egreso") } }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Con qué se le paga", color = c.textoSuave, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Vacío = no eligió (se usa Efectivo).
                        ChipFin("Sin elegir", activo = aj.metodoPago.isBlank()) { ajustar(id) { it.copy(metodoPago = "") } }
                        nombresMetodo.forEach { m -> ChipFin(m, activo = aj.metodoPago == m) { ajustar(id) { it.copy(metodoPago = m) } } }
                    }
                }
            }
            Ayuda(
                (if (f.tipo == "porcentaje") "Sin porcentaje propio se usa el del esquema. " else "") +
                    "“Sale sola al cobrar” registra el egreso apenas se cobra: en Finanzas se filtra por comisión y se paga lo del día.",
            )
        }

        Spacer(Modifier.height(Sania.dim.md))
        Text(
            if (f.porEvaluaciones) "Cuenta cada ${LocalTerminologiaPaciente.current.paciente} distinto con una Evaluación o Consulta atendida en el mes. Se paga desde aquí al cerrar el mes y sale como egreso de la caja."
            else "Un paquete cuenta cuando está pagado y se le asignó a esa persona. El profesional ve su nivel y cuánto le falta, pero no el monto.",
            color = c.textoSuave, fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(c.chipBg).padding(10.dp),
        )
    }
}

@Composable
private fun Ayuda(texto: String) {
    Text(texto, color = Sania.colors.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun CampoNumero(valor: String, placeholder: String, modifier: Modifier, decimal: Boolean = true, onCambio: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = { v -> onCambio(v.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }) },
        singleLine = true,
        placeholder = { Text(placeholder, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        colors = coloresCampoForm(),
        modifier = modifier,
    )
}
