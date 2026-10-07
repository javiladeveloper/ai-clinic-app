package pe.saniape.app.ui.clinica.atencion

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.AgendaRepo
import pe.saniape.app.data.staff.AtencionRepo
import pe.saniape.app.data.staff.CitaStaff
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.DatosConsultaApp
import pe.saniape.app.data.staff.DiagnosticoCie
import pe.saniape.app.data.staff.fechaLegibleCorta
import pe.saniape.app.data.staff.formatearNumeroReceta
import pe.saniape.app.data.staff.requiereConsentimiento
import pe.saniape.app.ui.AccionesNativas
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.agenda.modales.ModalCobrarCita
import pe.saniape.app.ui.clinica.agenda.modales.textoSoles
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.hora12
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.tutoriales.tourAncla

// ─────────────────────────────────────────────────────────────────────────────
// PASO "CIERRE" de la consulta guiada (sección de cierre de ConsultaGuiada.tsx):
// resumen, avisos, profesional que firma, cobro (el MISMO modal y endpoint de
// la agenda, con método y fecha del pago), imprimibles y "✓ Terminar atención".
// En este paso el pie de navegación no se pinta: los botones viven aquí, en el
// cuerpo (con el teclado abierto un pie quedaría tapado).
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PasoCierre(
    vm: AtencionViewModel,
    d: DatosConsultaApp,
    soloLectura: Boolean,
    acciones: AccionesNativas,
    ctx: ContextoStaff,
    /** "Ir a la ficha para cobrar" (procedimiento: se cobra en su tratamiento). */
    onVerFicha: () -> Unit = {},
) {
    val c = Sania.colors
    val f = d.flags
    val esProc = f.esProcedimiento
    val completada = f.completada
    val t = vm.borrador.textos
    var imprimiendo by remember { mutableStateOf<String?>(null) }
    var faltanConfirmar by remember { mutableStateOf<List<String>?>(null) }
    // El "Guardando…" previo a terminar vive en el VM: volver al paso no lo rehabilita.
    val preparando = vm.accionando == "preparar-cierre"

    val nota: String? = if (esProc) t["nota_procedimiento"] else null

    fun imprimir(clave: String, bloque: suspend () -> Unit) {
        if (imprimiendo != null) return
        imprimiendo = clave
        vm.lanzar {
            try { bloque() } finally { imprimiendo = null }
        }
    }

    fun intentarTerminar() {
        if (vm.terminando) return
        vm.lanzar("preparar-cierre") {
            // Se guarda primero: así los faltantes que llegan son los de lo escrito.
            val ok = vm.guardarAntes()
            if (!ok) return@lanzar
            val faltan = vm.datos?.flags?.faltantesAtencion.orEmpty()
            if (faltan.isNotEmpty()) faltanConfirmar = faltan else vm.terminar(nota)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ── Resumen ──
        Text("Resumen de la atención", color = c.navy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!esProc) FilaResumen("Motivo", t["motivo_consulta"])
            if (esProc) FilaResumen("Procedimiento", t["nota_procedimiento"])
            FilaResumen("Diagnóstico", textoDiagnosticos(vm.borrador.diagnosticos))
            FilaResumen("Receta", d.recetas.joinToString(" · ") {
                formatearNumeroReceta(it.numero) + if (it.estado == "Anulada") " (anulada)" else ""
            })
            if (!esProc) FilaResumen("Exámenes", vm.borrador.examenes.joinToString(", ") { it.nombre })
            if (!esProc) FilaResumen("Procedimientos", d.indicados.mapNotNull { it.procedimiento?.nombre?.ifBlank { null } }.joinToString(", "))
            if (esProc) {
                val cis = d.consentimientos.filter { it.estado != "Anulado" }
                FilaResumen(
                    "Consentimiento",
                    cis.joinToString(" · ") { "${it.procedimiento}: ${NOMBRE_ESTADO_CI[it.estado] ?: it.estado}" }
                        .ifBlank { if (requiereConsentimiento(d.cita)) "Falta" else "No requiere" },
                )
            }
            FilaResumen("Indicaciones", t["tratamiento"])
            val ctrl = d.atencion?.control
            FilaResumen(
                "Próximo control",
                ctrl?.fecha?.let { fechaLegibleCorta(it) + (ctrl.hora?.takeIf { h -> h.isNotBlank() }?.let { h -> " ${hora12(h)}" } ?: "") },
            )
        }

        // ── Avisos (no bloquean) ──
        if (f.avisosCierre.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.pendBg)
                    .border(1.dp, c.pend, RoundedCornerShape(Sania.shape.sm.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                f.avisosCierre.forEach { a -> Text("• $a", color = c.texto, fontSize = 13.sp) }
            }
        }

        // ── Profesional que atendió ──
        SelectorAtendio(vm, d, bloqueado = soloLectura || completada)

        // ── 💰 Cobro ──
        BloqueCobro(vm, d, ctx, onVerFicha)

        // ── Imprimibles ──
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonChico(
                if (imprimiendo == "indicaciones") "Abriendo…" else "🖨 Indicaciones y orden de exámenes",
                c.navy, c.superficie, borde = c.borde, habilitado = imprimiendo == null,
            ) {
                imprimir("indicaciones") {
                    // Como la web: se guarda antes de imprimir (la hoja sale de lo guardado).
                    if (vm.guardarAntes()) {
                        val html = AtencionRepo.htmlImprimible("indicaciones", d.cita.id)
                        if (html != null) acciones.abrirHtml(html, "Indicaciones")
                        else Toaster.error("No se pudieron abrir las indicaciones. Revisa tu conexión.")
                    }
                }
            }
            d.recetas.filter { it.estado != "Anulada" }.forEach { r ->
                val num = formatearNumeroReceta(r.numero)
                BotonChico(
                    if (imprimiendo == r.id) "Abriendo…" else "🖨 Receta $num",
                    c.navy, c.superficie, borde = c.borde, habilitado = imprimiendo == null,
                ) {
                    imprimir(r.id) {
                        val html = AtencionRepo.htmlImprimible("receta", r.id)
                        if (html != null) acciones.abrirHtml(html, "Receta $num")
                        else Toaster.error("No se pudo abrir la receta. Revisa tu conexión.")
                    }
                }
            }
        }

        // ── Acciones ──
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!soloLectura) {
                if (completada) {
                    BotonAncho(
                        if (vm.guardando) "Guardando…" else "Guardar cambios",
                        primario = true, habilitado = !vm.guardando && vm.sucio,
                    ) { vm.lanzar { vm.guardar() } }
                } else {
                    Column(Modifier.tourAncla("consulta.terminar")) {
                        BotonAncho(
                            when { vm.terminando -> "Terminando…"; preparando -> "Guardando…"; else -> "✓ Terminar atención" },
                            primario = true, habilitado = !vm.terminando && vm.accionando == null && !vm.guardando,
                        ) { intentarTerminar() }
                    }
                }
            }
            if (vm.paso > 0) {
                val anterior = vm.pasos.getOrNull(vm.paso - 1)?.titulo ?: "Atrás"
                BotonAncho("← $anterior", primario = false) { vm.atras() }
            }
        }
        if (completada) {
            Text(
                "Atención terminada. Las correcciones quedan registradas (auditoría); no se pierde lo anterior.",
                color = c.textoSuave, fontSize = 12.sp,
            )
        }
    }

    faltanConfirmar?.let { faltan ->
        DialogoForm(
            titulo = "¿Terminar igual?",
            subtitulo = null,
            textoAccion = "Terminar igual",
            accionHabilitada = !vm.terminando,
            onCancelar = { faltanConfirmar = null },
            onAccion = {
                faltanConfirmar = null
                vm.terminar(nota)
            },
            textoCancelar = "Seguir editando",
        ) {
            Text("Faltan: ${faltan.joinToString(" · ")}.", color = c.texto, fontSize = Sania.txt.cuerpo)
            Spacer(Modifier.height(8.dp))
            Text(
                "La historia clínica quedará incompleta (NTS 139). Puedes completarla después: las correcciones quedan registradas.",
                color = c.textoSuave, fontSize = 12.sp,
            )
        }
    }
}

/** "J06.9 Faringitis aguda (P); Fiebre (D)" — como el resumen de la web. */
internal fun textoDiagnosticos(l: List<DiagnosticoCie>): String =
    l.joinToString("; ") { "${it.codigo?.let { c -> "$c " } ?: ""}${it.descripcion} (${it.tipo})" }

@Composable
private fun FilaResumen(etiqueta: String, valor: String?) {
    val c = Sania.colors
    Column {
        Text(etiqueta, color = c.textoSuave, fontSize = 12.sp)
        Text(valor?.trim()?.ifBlank { null } ?: "—", color = c.texto, fontSize = 13.sp)
    }
}

// ── Profesional que atendió ──────────────────────────────────────────────────

/**
 * "Profesional que atendió (firma y sella)". Se ofrecen los activos + el ya
 * elegido. Como la web, solo se bloquea en solo lectura o con la atención terminada.
 */
@Composable
private fun SelectorAtendio(vm: AtencionViewModel, d: DatosConsultaApp, bloqueado: Boolean) {
    val c = Sania.colors
    val elegidoId = vm.borrador.terapeutaId
    val profesionales = d.profesionales.filter { it.estado != "Inactivo" || it.id == elegidoId }
    val fijo = bloqueado
    var menu by remember { mutableStateOf(false) }
    fun etiqueta(id: String?): String {
        val p = d.profesionales.firstOrNull { it.id == id } ?: return "— Elegir —"
        return p.nombre + (p.cmp?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: " · sin colegiatura")
    }
    Column {
        EtqForm("Profesional que atendió (firma y sella)")
        if (fijo) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.fondo)
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(horizontal = 12.dp, vertical = 13.dp),
            ) { Text(etiqueta(elegidoId), color = c.texto, fontSize = 14.sp) }
        } else {
            Box {
                CajaSelectorForm(etiqueta(elegidoId)) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    profesionales.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(etiqueta(p.id), fontSize = 14.sp) },
                            onClick = { menu = false; if (p.id != elegidoId) vm.terapeuta(p.id) },
                        )
                    }
                }
            }
        }
    }
}

// ── 💰 Cobro ─────────────────────────────────────────────────────────────────

/** La cita de la consulta con lo que necesita el modal de cobro de la agenda. */
internal fun citaStaffDeConsulta(d: DatosConsultaApp): CitaStaff {
    val cita = d.cita
    return CitaStaff(
        id = cita.id,
        fecha = cita.fecha,
        hora = cita.hora.orEmpty(),
        estado = cita.estado,
        tipo = cita.tipo.ifBlank { null },
        costo = cita.costo,
        duracion = cita.duracion,
        origen = null,
        confirmadaPorPaciente = false,
        numeroSesion = null,
        terapeutaId = cita.terapeuta_id,
        terapeutaNombre = cita.terapeuta?.nombre,
        pacienteId = cita.paciente?.id ?: cita.paciente_id,
        pacienteNombre = cita.paciente?.nombre,
        pacienteTelefono = cita.paciente?.telefono,
        tratamientoId = cita.tratamiento_id,
        procedimiento = cita.procedimiento?.nombre,
        especialidadId = cita.especialidad_id,
        notaRecepcion = null,
        pagadaAt = cita.pagada_at,
    )
}

/**
 * El cobro de la cita, con el mismo modal y endpoint que la agenda
 * (/api/staff/cita/cobrar): método, destino y fecha del pago.
 */
@Composable
private fun BloqueCobro(vm: AtencionViewModel, d: DatosConsultaApp, ctx: ContextoStaff, onVerFicha: () -> Unit) {
    val c = Sania.colors
    val cita = d.cita
    val monto = textoSoles(cita.costo)
    var abierto by remember { mutableStateOf(false) }
    // En el VM: salir del cierre y volver no rehabilita un cobro a mitad de envío.
    val cobrando = vm.accionando == "cobrar"
    // Con la terminología de la clínica (flujo de ctx; el de la especialidad lo resuelve la agenda).
    val nombreTipo = ctx.flujo.nombreTipo(cita.tipo.ifBlank { "Consulta" })

    Bloque {
        Text("💰 Cobro", color = c.navy, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        when {
            d.flags.esProcedimiento -> {
                val tr = cita.tratamiento
                Text(
                    "Se cobra en su tratamiento (${tr?.estado_pago?.ifBlank { null } ?: "Pendiente"}" +
                        (tr?.precio_acordado?.takeIf { it > 0 }?.let { " · ${textoSoles(it)}" } ?: "") + ").",
                    color = c.texto, fontSize = 13.sp,
                )
                Text(
                    "Ir a la ficha para cobrar", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.padding(top = 4.dp).clickable(onClick = onVerFicha),
                )
            }
            !d.flags.cobrable -> Text("Sin costo.", color = c.textoSuave, fontSize = 13.sp)
            cita.pagada_at != null -> Text("✓ Pagado $monto", color = c.ok, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            ctx.puede("pagos") -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Por cobrar ", color = c.texto, fontSize = 13.sp)
                Text(monto, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                BotonChico(if (cobrando) "Cobrando…" else "Cobrar", c.sobreNavy, c.ok, habilitado = vm.accionando == null) { abierto = true }
            }
            else -> Text("Por cobrar $monto — lo cobra recepción.", color = c.pend, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }

    if (abierto) {
        ModalCobrarCita(
            cita = citaStaffDeConsulta(d),
            onCancelar = { if (!cobrando) abierto = false },
            onConfirmar = { metodo, modo, fecha, pagos ->
                if (!cobrando) {
                    vm.lanzar("cobrar") {
                        val r = AgendaRepo.cobrarCita(cita.id, metodo, modo, fecha, pagos)
                        if (r.registrada) {
                            abierto = false
                            // Encolada: enviarOEncolar ya avisó "se registrará al volver la señal".
                            if (!r.encolada) Toaster.exito(textoCobrado(nombreTipo, monto, modo, fecha, cita.fecha, pagos, r.yaEstaba))
                            vm.recargar()
                        } else {
                            r.rechazo?.let { Toaster.error(pe.saniape.app.data.staff.mensajeRechazoCobro(it, pagos != null)) }
                        }
                    }
                }
            },
            guardando = cobrando,
            nombreTipo = nombreTipo,
        )
    }
}

/**
 * El toast del cobro, el mismo en la agenda y en la consulta guiada: "Cobrado
 * S/ 80.00 (fechado el 29/09)", "Cobrado S/ 40.00 (Efectivo + Yape)". Si la cita
 * [yaEstaba] cobrada (otra persona, el auto-cobro…), se dice eso: el servidor no
 * registró el cobro de nuevo.
 */
internal fun textoCobrado(
    nombreTipo: String, monto: String, modo: String, fechaPago: String, fechaCita: String,
    pagos: List<pe.saniape.app.data.staff.PartePago>? = null, yaEstaba: Boolean = false,
): String {
    if (yaEstaba) return "Esta ${nombreTipo.lowercase()} ya estaba cobrada: no se registró de nuevo"
    val medios = pagos?.takeIf { it.isNotEmpty() && modo != "gratis" }
        ?.let { " (${pe.saniape.app.data.staff.etiquetaMetodos(it)})" }.orEmpty()
    val fechada = if (modo != "gratis" && fechaPago.take(10) != fechaCita.take(10)) {
        val p = fechaPago.take(10).split("-")
        if (p.size == 3) " (fechado el ${p[2]}/${p[1]})" else ""
    } else ""
    return when (modo) {
        "gratis" -> "$nombreTipo sin costo: quedó saldada"
        "abonar" -> "$monto abonados al tratamiento$medios$fechada"
        else -> "Cobrado $monto$medios$fechada"
    }
}

// ── Botón ancho ──────────────────────────────────────────────────────────────

@Composable
private fun BotonAncho(texto: String, primario: Boolean, habilitado: Boolean = true, onClick: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.md.dp)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(forma)
            .background(if (primario) (if (habilitado) c.navy else c.borde) else c.superficie)
            .border(1.dp, if (primario) Color.Transparent else c.borde, forma)
            .clickable(enabled = habilitado, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            texto, color = if (primario) (if (habilitado) c.sobreNavy else c.textoSuave) else c.navy,
            fontWeight = FontWeight.Bold, fontSize = 15.sp,
        )
    }
}
