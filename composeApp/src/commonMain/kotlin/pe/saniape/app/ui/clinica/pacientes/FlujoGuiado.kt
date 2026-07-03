package pe.saniape.app.ui.clinica.pacientes

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import pe.saniape.app.data.staff.CitaHito
import pe.saniape.app.data.staff.TratamientoPaciente
import pe.saniape.app.ui.theme.Sania

/** Un paso del recorrido (bolita + label). */
private data class Paso(val label: String, val done: Boolean, val activo: Boolean)

/**
 * Barra de recorrido ADAPTATIVA por TIPO de tratamiento (Strategy, como la web), para incrustar
 * en la cabecera de cada TarjetaTratamiento. Aplica a CUALQUIER especialidad:
 *  - SESIONES (fisio/orto/etc):        Consulta → Evaluación → Sesiones (N/M) → Alta
 *  - CONSULTA/UNIDADES (medicina/etc): Consulta → Evaluación → Control → Alta
 *  - SERVICIO ÚNICO (blanqueamiento):  Consulta → Evaluación → Por hacer/Realizado → Pagado
 *    (sin "Alta": realizado + pagado ya es el fin. "Realizado" SOLO si el trat está Completado —
 *    no basta con tener consulta/evaluación hechas.)
 */
@Composable
fun BarraRecorrido(
    trat: TratamientoPaciente,
    consultaDone: Boolean,
    evalDone: Boolean,
    citaConsulta: CitaHito? = null,
    citaEvaluacion: CitaHito? = null,
    puedePagos: Boolean = false,
    expandido: Boolean = false,             // si la tarjeta está expandida (resalta el paso Sesiones)
    onEditarCita: (CitaHito) -> Unit = {},
    onToggleSesiones: () -> Unit = {},      // tocar la bolita Sesiones expande/colapsa la tarjeta
    onColapsarTarjeta: () -> Unit = {},     // colapsar la tarjeta al abrir una nube (1 activo a la vez)
    onAgendarControl: () -> Unit = {},      // en Control: el paciente necesita volver
    onDarAlta: () -> Unit = {},             // en Control: el caso se cierra (alta)
    onRegistrarAtencion: () -> Unit = {},   // Control: medicación/receta · Servicio único: registrar el servicio
    onRevertirServicio: () -> Unit = {},    // Servicio único: volver a "Por hacer" (conserva el pago)
) {
    val c = Sania.colors
    val esServUnico = trat.esServicioUnico
    val servRealizado = trat.servicioRealizado
    val servPagado = trat.estadoPago == "Pagado"
    val usaSesiones = !trat.esConsulta && !esServUnico
    val sesComp = trat.sesionesCompletadas
    val sesTot = trat.totalSesiones
    val altaTrat = trat.estado == "Alta"
    val completo = trat.estado == "Completado" || (sesTot > 0 && sesComp >= sesTot)
    // Bolita seleccionada (muestra su nube de referencia). Índice del paso abierto, o null.
    var hitoAbierto by remember { mutableStateOf<Int?>(null) }

    // Tercer paso: con sesiones = progreso N/M; sin sesiones = "Control" (la próxima cita
    // aprox). 'done' si ya se atendió (hubo consulta/evaluación); 'activo' si hay próximo
    // control agendado pero aún no ocurrió. No se marca por tener fecha futura sola.
    val atendido = consultaDone || evalDone
    val tieneProxControl = !trat.proximoControl.isNullOrBlank()
    val pasoTercero = when {
        esServUnico -> Paso(
            if (servRealizado) "Realizado" else "Por hacer",
            done = servRealizado, activo = !servRealizado && !altaTrat,
        )
        usaSesiones -> {
            val etq = if (sesComp > sesTot) "$sesTot/$sesTot +${sesComp - sesTot}" else "$sesComp/$sesTot ses."
            Paso(etq, done = completo, activo = !completo && !altaTrat)
        }
        else -> Paso("Control", done = atendido, activo = !altaTrat && tieneProxControl)
    }
    val pasoCuarto =
        if (esServUnico) Paso("Pagado", done = servPagado, activo = servRealizado && !servPagado)
        else Paso("Alta", done = altaTrat, activo = false)
    val pasos = listOf(
        Paso("Consulta", done = consultaDone, activo = false),
        Paso("Evaluación", done = evalDone, activo = false),
        pasoTercero,
        pasoCuarto,
    )
    // Cada bolita puede abrir su nube de referencia si hay una cita-hito asociada:
    //  - paso 0 (Consulta) → citaConsulta
    //  - paso 1 (Evaluación) → citaEvaluacion
    //  - paso 2 (Control, sin sesiones) → la cita que se realizó (consulta o evaluación)
    val citaDelPaso = { i: Int ->
        when {
            i == 0 -> citaConsulta
            i == 1 -> citaEvaluacion
            i == 2 && !usaSesiones && !esServUnico -> citaConsulta ?: citaEvaluacion
            else -> null
        }
    }

    // El paso "Sesiones" (índice 2, con sesiones) controla el expandir de la tarjeta.
    val esSesiones = { i: Int -> i == 2 && usaSesiones }
    // El paso "Control" (índice 2, sin sesiones) SIEMPRE abre su nube (próx. control + acciones),
    // aunque no haya una cita asociada — así el profesional puede agendar/dar de alta.
    val esControl = { i: Int -> i == 2 && !usaSesiones && !esServUnico }
    // El paso del SERVICIO (índice 2, servicio único): nube con detalles + registrar/revertir.
    val esServicio = { i: Int -> i == 2 && esServUnico }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        pasos.forEachIndexed { i, paso ->
            val cita = citaDelPaso(i)
            // Tocable si: tiene cita, o es Control, o es el paso del Servicio (único).
            val abreNube = cita != null || esControl(i) || esServicio(i)
            Column(Modifier.width(60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // "abierta" = nube abierta, o (paso Sesiones) la tarjeta está expandida.
                val abierta = hitoAbierto == i || (esSesiones(i) && expandido)
                val bg = when { paso.done -> c.ok; paso.activo -> c.navy; else -> c.superficie }
                val fg = when { paso.done || paso.activo -> c.sobreNavy; else -> c.textoSuave }
                val borde = when { abierta -> c.navy; paso.done -> c.ok; paso.activo -> c.navy; else -> c.borde }
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(bg)
                        .border(if (abierta) 3.dp else 2.dp, borde, CircleShape)
                        .let {
                            when {
                                // Sesiones: expande la tarjeta y cierra cualquier nube abierta.
                                esSesiones(i) -> it.clickable { hitoAbierto = null; onToggleSesiones() }
                                // Nube (cita o Control): alterna y colapsa la tarjeta (1 activo a la vez).
                                abreNube -> it.clickable {
                                    val abrir = hitoAbierto != i
                                    hitoAbierto = if (abrir) i else null
                                    if (abrir && expandido) onColapsarTarjeta()
                                }
                                else -> it
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { Text(if (paso.done) "✓" else "${i + 1}", color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                Text(
                    paso.label, color = if (paso.done) c.ok else if (paso.activo) c.navy else c.textoSuave,
                    fontSize = 8.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            if (i < pasos.size - 1) {
                Box(
                    Modifier.weight(1f).height(2.dp).padding(top = 13.dp)
                        .background(if (pasos[i].done) c.ok else c.borde),
                )
            }
        }
    }

    // Nube flotante del paso tocado. Renderiza si hay cita (Consulta/Eval/Control con datos)
    // O si es el paso Control/Servicio (aunque no haya cita → detalles + acciones).
    val abi = hitoAbierto
    val citaAbierta = abi?.let { citaDelPaso(it) }
    val esControlAbierto = abi == 2 && !usaSesiones && !esServUnico
    val esServicioAbierto = abi == 2 && esServUnico
    if (abi != null && (citaAbierta != null || esControlAbierto || esServicioAbierto)) {
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                .background(c.chipBg).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                .padding(10.dp),
        ) {
            // Detalle de la cita (si la hay)
            citaAbierta?.let { cita ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${if (cita.tipo == "Consulta") "💬" else "🔍"} ${cita.tipo}",
                        color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    // El ✏ Editar NO va en el paso Control (ahí está "Registrar atención").
                    // En Consulta/Evaluación (fisio Y servicio único) sí: edita la cita.
                    if (!esControlAbierto) {
                        Box(
                            Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.navy)
                                .clickable {
                                    if (!usaSesiones && !esServUnico) onRegistrarAtencion() else onEditarCita(cita)
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) { Text("✏ Editar", color = c.sobreNavy, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                FilaRef("Fecha", "${cita.fecha}${cita.hora?.let { " · $it" } ?: ""}")
                cita.terapeutaNombre?.let { FilaRef("Profesional", it) }
                if (puedePagos && cita.costo != null) FilaRef("Precio", if (cita.costo > 0) "S/ ${cita.costo}" else "Gratuita")
                cita.notas?.takeIf { it.isNotBlank() }?.let { FilaRef("Notas", it) }
            }
            // Paso Control: próxima cita aprox + decisión del profesional (aunque no haya cita).
            if (esControlAbierto) {
                if (citaAbierta == null) Text("🗓 Control", color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                // Lo registrado por el profesional al atender (diagnóstico/medicación).
                trat.medicacion?.takeIf { it.isNotBlank() }?.let { FilaRef("Medicación", it) }
                FilaRef("Próx. control", trat.proximoControl?.takeIf { it.isNotBlank() } ?: "Sin agendar")
                if (!altaTrat) {
                    Spacer(Modifier.height(8.dp))
                    // Registrar atención (medicación/receta) — el momento natural tras atender.
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.navy)
                            .clickable { onRegistrarAtencion() }.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("📝 Registrar atención (medicación/receta)", color = c.sobreNavy, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(8.dp))
                    Text("¿El paciente necesita volver?", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.navy)
                                .clickable { onAgendarControl() }.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("📅 Agendar control", color = c.sobreNavy, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(Sania.shape.sm.dp))
                                .background(c.ok.copy(alpha = 0.15f)).border(1.dp, c.ok, RoundedCornerShape(Sania.shape.sm.dp))
                                .clickable { onDarAlta() }.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("✓ Dar de alta", color = c.ok, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            // Paso del SERVICIO ÚNICO: detalles (diagnóstico/precio) + registrar o revertir.
            // Sin "Dar de alta": realizado + pagado ya es el fin del servicio.
            if (esServicioAbierto) {
                Text(
                    if (servRealizado) "✨ Servicio realizado" else "✨ Servicio por realizar",
                    color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                trat.diagnostico?.takeIf { it.isNotBlank() }?.let { FilaRef("Diagnóstico", it) }
                if (puedePagos) {
                    val precio = trat.precioAcordado ?: trat.precioBase ?: 0.0
                    FilaRef("Precio", "S/ ${if (precio % 1.0 == 0.0) precio.toInt() else precio}")
                }
                if (!altaTrat) {
                    Spacer(Modifier.height(8.dp))
                    if (!servRealizado) {
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.navy)
                                .clickable { onRegistrarAtencion() }.padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("📝 Registrar atención", color = c.sobreNavy, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    } else {
                        // Se marcó por error: vuelve a "Por hacer" (el pago se conserva).
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                                .clickable { onRevertirServicio() }.padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("↩ Revertir (volver a “Por hacer”)", color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaRef(etq: String, valor: String) {
    val c = Sania.colors
    Row(Modifier.padding(vertical = 1.dp)) {
        Text("$etq:", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.width(70.dp))
        Text(valor, color = c.texto, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
