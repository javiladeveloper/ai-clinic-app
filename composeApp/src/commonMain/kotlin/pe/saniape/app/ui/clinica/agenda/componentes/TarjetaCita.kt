package pe.saniape.app.ui.clinica.agenda.componentes

import pe.saniape.app.data.staff.zonaDeSede
import pe.saniape.app.data.staff.LocalTerminologiaPaciente
import pe.saniape.app.data.staff.FlujoClinica
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.clickable
import pe.saniape.app.ui.theme.tocable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.CitaStaff
import pe.saniape.app.data.staff.AccionCitaItem
import pe.saniape.app.data.staff.AccionTarjeta
import pe.saniape.app.data.staff.EntradaAccionesCita
import pe.saniape.app.data.staff.GrupoAccion
import pe.saniape.app.data.staff.SalaAcciones
import pe.saniape.app.data.staff.TonoAccion
import pe.saniape.app.data.staff.planAccionesCita
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import pe.saniape.app.ui.clinica.agenda.modales.textoSoles
import pe.saniape.app.data.staff.EtapaLlegada
import pe.saniape.app.ui.hora12
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.EstadosColor
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.tutoriales.tourAncla
import pe.saniape.app.data.staff.simboloMoneda

/**
 * Tarjeta de una cita en la agenda. Muestra (como la web): color por tipo, hora,
 * paciente, "Sesión #N", costo, profesional, badge "🌐 Web", botones de contacto
 * (📞/💬) y las acciones según estado.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TarjetaCita(
    cita: CitaStaff,
    puedeVerCosto: Boolean,
    accionando: Boolean,
    onAccion: (AccionTarjeta) -> Unit,
    /**
     * "✓ Completar" está decidiendo si abre la evaluación (control de nutrición):
     * dice "Abriendo…" y la tarjeta no acepta otro toque mientras tanto.
     */
    decidiendo: Boolean = false,
    onVerResumen: (String) -> Unit = {},
    conteoFranja: Int = 1,
    /** La clínica hace odontología: ofrece abrir el odontograma desde la cita. */
    odontologia: Boolean = false,
    /**
     * "🩺 Crear tratamiento" sin salir de la agenda (gemelo de /citas web). Lo
     * decide la pantalla: cita que EVALÚA según el flujo + permiso 'sesiones'.
     */
    crearTratamiento: Boolean = false,
    /**
     * Sala de espera de la cita (solo HOY, con triaje o flujo médico). null =
     * la tarjeta de siempre: DALU y RENOVA no ven ningún cambio.
     */
    sala: SalaTarjeta? = null,
    /**
     * La cita va por la consulta guiada (especialidad médica, no dental). Decide
     * "▶ Atender" vs "✓ Completar" AUNQUE no haya sala de espera (otro día, o la
     * clínica no la usa), igual que /citas web (`esMedicaGuiada`). Antes solo salía
     * con la sala de espera de HOY y el resto de días mostraba "Completar".
     */
    medica: Boolean = false,
    /**
     * El flujo de la ESPECIALIDAD de la cita: cómo se llama cada tipo y si existe
     * el paso "→ Evaluación". Sin él, la tarjeta decía "CONSULTA" y ofrecía
     * "→ Evaluación" en una clínica que entra por Diagnóstico, o en medicina
     * general, que no tiene Evaluación aparte.
     */
    flujo: FlujoClinica = FlujoClinica(),
    /**
     * Permiso 'pagos': ofrece "💰 Cobrar" en la Consulta/Evaluación con costo aún
     * sin cobrar (gemelo de la moneda de /citas web). Sin él, la deuda de una cita
     * ya atendida se ve igual ("⚠ Debe S/ N"), pero no hay nada que tocar.
     */
    puedeCobrar: Boolean = false,
    /**
     * Estado de pago que arma el servidor (estado-pago): "¿ya pagó?" para quien
     * atiende. null = no llegó (sin red, endpoint aún no desplegado…): se usa lo
     * de siempre (💰 Pagado / ⚠ Debe de la Consulta/Evaluación).
     */
    estadoPago: pe.saniape.app.data.staff.EstadoPagoCita? = null,
    /** "🧠 Evaluación": la cita es de una evaluación psicológica y quien mira puede trabajarla. */
    evaluacionPsico: Boolean = false,
    /**
     * Con qué se pagó la cita cobrada ("Yape", "Efectivo + Yape"), como la moneda
     * de /citas web. Solo llega a quien ve la caja (permiso 'pagos'); null = no se
     * sabe (todavía, sin red) o no corresponde → "Pagado" a secas.
     */
    mediosPago: String? = null,
    /** "↺ Anular cobro" (Admin, cobro con varios medios): lo decide la pantalla. */
    anularCobro: Boolean = false,
    /**
     * Tocar la tarjeta abre el detalle de la cita (observaciones, diagnóstico, notas
     * de la sesión: gemelo de PopupCita web). El nombre y los botones siguen con lo suyo.
     */
    onAbrirDetalle: (() -> Unit)? = null,
    /**
     * 📝 Receta / indicaciones de la cita ATENDIDA: el texto del botón ("📝 Dar
     * indicaciones") o null = no aplica (módulo apagado, sin permiso, especialidad
     * que no receta). Lo decide la pantalla; si ya tiene una, ella avisa antes de otra.
     */
    textoReceta: String? = null,
    /** Indicador "💊 Receta N° 000012" / "📋 Indicaciones N° …" si la cita ya tiene una vinculada. */
    recetaVinculada: String? = null,
    /**
     * Cita de NUTRICIÓN y quien mira puede escribir en la ficha ('pacientes' o
     * 'sesiones'): "⚖️ Medidas" y "🥗 Plan alimentario" de esta atención, sin
     * buscar la pestaña (gemelo del menú de /citas web). Lo decide la pantalla.
     */
    nutricion: Boolean = false,
    /** Cita de GINECO-OBSTETRICIA y quien mira puede escribir en la ficha: "🤰 Embarazo". Lo decide la pantalla. */
    embarazo: Boolean = false,
    /** 🧾 Pedidos para recepción pendientes de ESTA cita (chip "🧾 Pedido pendiente"). */
    pedidos: List<pe.saniape.app.data.staff.PedidoRecepcion>? = null,
    /**
     * Texto de la acción "🧾 Pedido para recepción" / "🧾 Cobrar pedido" / "🧾 Ver
     * pedido", o null = no aplica (sin permiso, base sin pedidos). Lo decide la pantalla.
     */
    textoPedido: String? = null,
    onPedido: (pe.saniape.app.data.staff.PedidoRecepcion) -> Unit = {},
    /**
     * Sesión completada con el plan ya terminado: «＋ Ampliar» / «✓ Dar de alta»
     * (accionesSesionTerminada, gemelo de /citas web). Lo decide la pantalla.
     */
    terminado: pe.saniape.app.data.staff.AccionesTerminado? = null,
    /**
     * 💰 del saldo del PAQUETE en la sesión ya atendida (paqueteCobrableEnAgenda):
     * abona al tratamiento como «Registrar pago» de la ficha. Lo decide la pantalla.
     */
    abonarPaquete: Boolean = false,
    /**
     * ⚡ Atención directa (psicología, cita SIN tratamiento): ATENDER → «▶ Atender»
     * reemplaza al ✓ Completar; EVOLUCION → [botonEvolucion]. null = lo de siempre
     * (DALU y cualquier otro rubro no ven ningún cambio). Lo decide la pantalla.
     */
    atencionDirecta: pe.saniape.app.data.staff.OfertaAtencionDirecta? = null,
    /** «📝 Anotar evolución» (resaltado) / «📝 Evolución» de la atención directa ya atendida. */
    botonEvolucion: pe.saniape.app.data.staff.BotonEvolucionCita? = null,
) {
    // Multipaís: el costo va en la moneda de la sede de la cita ("todas las sedes").
    val moneda = pe.saniape.app.data.staff.monedaDeFila(cita.sedeId)
    val c = Sania.colors
    val acciones = recordarAcciones()
    // Permiso 'historia_clinica': sin él la tarjeta deja lo operativo (llegó,
    // completar, cobrar, editar, pedido) y no ofrece lo clínico: consulta guiada,
    // triaje, evaluación psicológica, receta, odontograma, medidas, embarazo.
    val verClinico = pe.saniape.app.data.staff.LocalVerHistoriaClinica.current
    val salaEf = if (verClinico) sala else sala?.copy(triajeOn = false, medica = false)
    // Color del tipo desde la config global (reutilizable en toda la app).
    val tipoColor = EstadosColor.tipo(cita.tipo)

    // Atenuar las cerradas (completada/cancelada) para que las activas resalten.
    val cerrada = cita.estado == "Completada" || cita.estado == "Cancelada"

    // Solapamiento en la franja (misma hora + mismo profesional): 2 = ámbar, 3+ = rojo.
    // Solo tinta las citas ACTIVAS (las cerradas ya se atenúan y no deben "gritar").
    val solape = EstadosColor.solape(conteoFranja).takeUnless { cerrada }

    // Barra de color lateral: el solapamiento manda (ámbar/rojo); si no, el tipo.
    val colorBarra = when { cerrada -> c.borde; solape != null -> solape.fg; else -> tipoColor.fg }
    Row(
        // SIN height(IntrinsicSize.Min): con 3 filas de botones (💰 Cobrar ·
        // ▶ Atender / 🧾 Pedido / ✏ Editar · ✕ Cancelar) la medida intrínseca del
        // FlowRow se quedaba corta y el clip CORTABA la última fila — OMER no
        // encontraba "Editar" para reprogramar (19/10/2026). La barra lateral se
        // dibuja detrás, a la altura real de la tarjeta.
        Modifier.fillMaxWidth()
            // Sombra corta: da el relieve que hace que la tarjeta se lea como un
            // objeto sobre el fondo y no como un rectángulo dibujado. Va antes
            // del clip para que se proyecte fuera de la forma.
            .shadow(2.dp, RoundedCornerShape(Sania.shape.md.dp))
            .clip(RoundedCornerShape(Sania.shape.md.dp)).background(solape?.bg ?: c.superficie)
            .border(1.dp, solape?.fg ?: c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .drawBehind { drawRect(colorBarra, size = Size(Sania.dim.acento.toPx(), size.height)) }
            .then(if (onAbrirDetalle != null) Modifier.clickable { onAbrirDetalle() } else Modifier),
    ) {
        Spacer(Modifier.width(Sania.dim.acento))

        Column(Modifier.fillMaxWidth().padding(Sania.dim.tarjeta)) {
            // Línea 1: CHIP de tipo prominente ····· badge de estado
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChipTipo(cita.tipo, flujo.nombreTipo(cita.tipo), cita.numeroSesion, atenuado = cerrada)
                Spacer(Modifier.weight(1f))
                BadgeEstadoCita(cita.estado, cita.confirmadaPorPaciente)
            }

            Spacer(Modifier.height(8.dp))

            // Hora grande (referencia rápida del día)
            Text(hora12(cita.hora), color = if (cerrada) c.textoSuave else c.navy,
                fontSize = Sania.txt.seccion, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(6.dp))

            // Línea: nombre del paciente + iconos de contacto a la derecha
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Tocar el nombre abre el resumen clínico (qué le hice la última vez,
                // motivo, diagnóstico, progreso). El "›" lo hace descubrible: antes
                // nada indicaba que fuera tocable y el fisio no lo encontraba.
                Row(
                    // Sin paciente vinculado no hay resumen: el toque abre el detalle
                    // de la cita (antes no hacía nada).
                    Modifier.weight(1f).clickable { cita.pacienteId?.let(onVerResumen) ?: onAbrirDetalle?.invoke() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(cita.pacienteNombre ?: "${LocalTerminologiaPaciente.current.Paciente}", color = c.texto,
                        fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false))
                    Text(" ›", color = c.textoSuave, fontSize = Sania.txt.cuerpo,
                        fontWeight = FontWeight.Bold)
                    cita.pacienteBadgeApoderado?.let {
                        Spacer(Modifier.width(6.dp))
                        pe.saniape.app.ui.clinica.pacientes.BadgeApoderado(it)
                    }
                }
                cita.pacienteTelefono?.takeIf { it.isNotBlank() }?.let { tel ->
                    IconoContacto("📞", c.navy) { acciones.abrirUrl("tel:${tel.filter { ch -> ch.isDigit() }}") }
                    Spacer(Modifier.width(6.dp))
                    // WhatsApp con la confirmación/recordatorio de la cita prellenado (el
                    // mismo texto que el 📱 de la web): un toque y sale listo para enviar.
                    // Cita pasada o cerrada: el chat vacío, como la web.
                    IconoContacto("💬", pe.saniape.app.ui.theme.Paleta.WhatsApp) {
                        val n = pe.saniape.app.data.staff.numeroWhatsApp(tel, pe.saniape.app.data.staff.paisActivo())
                        val msg = mensajeWhatsAppDeCita(cita, flujo)
                        acciones.abrirUrl(pe.saniape.app.ui.urlWhatsApp(n, msg))
                    }
                }
            }

            // Línea 3: procedimiento · profesional (sutil)
            val sub = listOfNotNull(cita.procedimiento, cita.terapeutaNombre?.let { "con $it" })
                .joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(sub, color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }

            // Sala de espera: ○ Por llegar · 🔔 Llegó · 12 min · 🩺 Triaje ✓ PA 120/80 · ▶ En consulta.
            salaEf?.let { IndicadorLlegada(it, verValores = verClinico) }

            // Cita virtual con enlace y viva: "🎥 Unirse · Google Meet" (abre la app de
            // Meet / Zoom o el navegador). Las presenciales no cambian en nada.
            val enlace = cita.enlaceVirtual
            if (enlace != null && pe.saniape.app.data.staff.puedeUnirseStaff(cita.modalidad, enlace, cita.estado)) {
                Spacer(Modifier.height(8.dp))
                BotonUnirse(enlace) {
                    // Revalidado antes de abrir (mismo criterio que la web y la base).
                    pe.saniape.app.data.staff.enlaceSeguro(enlace)?.let { acciones.abrirUrl(it) }
                        ?: pe.saniape.app.ui.Toaster.error("Ese enlace no es válido: cámbialo desde el detalle de la cita.")
                }
            }

            // Dinero de la cita (como la web): solo Consulta/Evaluación con costo; la
            // sesión se cobra desde su tratamiento, nunca como cita.
            val cobrable = cita.tipo != "Sesión" && (cita.costo ?: 0.0) > 0 && cita.estado != "Cancelada"
            val pagada = cita.pagadaAt != null
            // El badge del servidor manda: con él, no se repiten los chips locales de pago.
            val conBadgePago = estadoPago?.mostrable == true && cita.estado != "Cancelada"
            // "✓ Ya pagó" en texto sin el badge del servidor (no llegó): la misma regla
            // (citaYaPago, gemelo web) con la cita sola. Lo ve todo el que atiende.
            val yaPagoLocal = !conBadgePago && pe.saniape.app.data.staff.citaYaPago(
                cita.tipo, cita.estado, cita.costo, cita.pagadaAt, cita.tratamientoId)

            // Qué acciones existen (mismas condiciones y permisos de siempre) y dónde
            // va cada una: primaria · secundaria · 💰 · «⋯ Más» (planAccionesCita).
            val plan = planAccionesCita(EntradaAccionesCita(
                estado = cita.estado, tipo = cita.tipo,
                odontograma = verClinico && odontologia && cita.pacienteId != null,
                pasarA = flujo.labelEvaluacion.takeIf { flujo.pasaAEvaluacion(cita.tipo) },
                crearTratamiento = crearTratamiento && cita.pacienteId != null,
                // Tras completar la evaluación, el plan es lo que sigue (no en dental,
                // consulta médica guiada ni nutrición: ahí va en «⋯ Más», como la web).
                crearTratamientoPrincipal = crearTratamiento && cita.pacienteId != null &&
                    !odontologia && !medica && !nutricion,
                sala = salaEf?.takeIf { cita.pacienteId != null }?.let {
                    SalaAcciones(it.etapa, it.triajeOn, it.medica, it.marcando, triajeHecho = it.atencion?.triajeAt != null)
                },
                // Sin el permiso, la cita médica se cierra con "✓ Completar" (sin la consulta guiada).
                medica = verClinico && medica && cita.pacienteId != null,
                evaluacionPsico = verClinico && evaluacionPsico && cita.tratamientoId != null,
                cobrar = cobrable && puedeCobrar && !pagada,
                // Cita SIN precio (las importadas de Google Calendar entran sin
                // servicio ni costo): sin precio no hay 💰, así que se le pone aquí.
                ponerPrecio = puedeCobrar && cita.tipo != "Sesión" && (cita.costo ?: 0.0) <= 0 &&
                    cita.estado != "Cancelada" && !pagada,
                anularCobro = anularCobro && pagada,
                // 🧾 Comprobante: el recibo del cobro YA hecho, otra vez (gemelo de /citas web).
                comprobante = pe.saniape.app.data.staff.ofreceComprobanteCita(
                    cita.tipo, cita.costo, cita.pagadaAt, cita.estado, puedeCobrar),
                receta = textoReceta?.takeIf { verClinico && cita.pacienteId != null },
                nutricion = verClinico && nutricion && cita.pacienteId != null,
                embarazo = verClinico && embarazo && cita.pacienteId != null,
                pedido = textoPedido?.takeIf { cita.pacienteId != null && cita.estado != "Cancelada" },
                abonarPaquete = abonarPaquete && puedeCobrar,
                terminado = terminado?.takeIf { cita.pacienteId != null && cita.tratamientoId != null },
                directa = verClinico && atencionDirecta == pe.saniape.app.data.staff.OfertaAtencionDirecta.ATENDER,
                evolucion = botonEvolucion?.takeIf { verClinico && atencionDirecta == pe.saniape.app.data.staff.OfertaAtencionDirecta.EVOLUCION },
            ))

            // Chips: costo, Web, Asignar (discretos, en una línea)
            val chips = buildList {
                if (puedeVerCosto) {
                    when {
                        (cita.costo ?: 0.0) > 0 -> add(Triple("${simboloMoneda(moneda)} ${formato2(cita.costo!!)}", c.teal, c.tealBg))
                        cita.tipo == "Consulta" -> add(Triple("Gratis", c.teal, c.tealBg))
                    }
                }
                val medios = mediosPago?.takeIf { puedeCobrar && it.isNotBlank() }
                if (!conBadgePago && cobrable && puedeCobrar && pagada) {
                    add(Triple(if (medios != null) "💰 Pagado · $medios" else "💰 Pagado", c.ok, c.okBg))
                } else if (conBadgePago && cobrable && pagada && medios != null) {
                    // El badge del servidor ya dice "Pagado": aquí solo con qué.
                    add(Triple("💳 $medios", c.ok, c.okBg))
                }
                // Sin permiso de cobrar, la deuda tiene que verse igual: el profesional
                // necesita saber que el paciente no pagó aunque no sea él quien cobra.
                if (!conBadgePago && cobrable && !puedeCobrar && !pagada && cita.estado == "Completada") {
                    add(Triple("⚠ Debe ${textoSoles(cita.costo ?: 0.0, moneda)}", c.error, c.errorBg))
                }
                recetaVinculada?.takeIf { verClinico }?.let { add(Triple(it, c.purple, c.purpleBg)) }
                // Cita por videollamada (sin enlace todavía: el detalle dice qué falta).
                if (cita.esVirtual) add(Triple(if (cita.enlaceVirtual == null && !cerrada) "🎥 Virtual · sin enlace" else "🎥 Virtual", c.purple, c.purpleBg))
                if (cita.origen == "online") add(Triple("🌐 Web", c.purple, c.purpleBg))
                if (cita.terapeutaId == null && cita.origen == "online") add(Triple("⚠ Asignar", c.pend, c.pendBg))
                // Solapamiento: N citas en la misma hora del mismo profesional.
                if (!cerrada && solape != null) {
                    val icono = if (conteoFranja >= 3) "⛔" else "⚠"
                    add(Triple("$icono $conteoFranja en esta hora", solape.fg, solape.bg))
                }
            }
            if (chips.isNotEmpty() || conBadgePago || yaPagoLocal || !pedidos.isNullOrEmpty() || plan.cobro != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                    if (conBadgePago) BadgeEstadoPago(estadoPago, cita.estado)
                    // Junto al 💰 (que se queda: es el botón para cobrar / ver).
                    else if (yaPagoLocal) EtiquetaYaPago()
                    // 💰 compacto pegado a la píldora "Sin pagar · debe S/ N": cobrar sin un
                    // botón grande aparte (solo hay 💰 cuando hay deuda).
                    plan.cobro?.let { a ->
                        Column(Modifier.tourAncla("agenda.cobrar")) {
                            CobroCompacto(a, !accionando && !decidiendo) { onAccion(a.accion) }
                        }
                    }
                    chips.forEach { (t, fg, bg) -> Chip(t, fg, bg) }
                    pe.saniape.app.ui.clinica.pedidos.ChipPedido(pedidos, onPedido)
                }
            }

            // Nota de recepción (recordatorio del tratamiento) — 📌
            cita.notaRecepcion?.let { nota ->
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.pendBg).padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text("📌 $nota", color = c.pend, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }

            // Acciones: UNA primaria del momento + a lo sumo una secundaria + «⋯ Más»
            // con el resto (planAccionesCita). El 💰 va arriba, junto a la píldora de pago.
            if (plan.visibles.isNotEmpty() || plan.menu.isNotEmpty()) {
                Spacer(Modifier.height(Sania.dim.md))
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                Spacer(Modifier.height(Sania.dim.md))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val habilitado = !accionando && !decidiendo
                    fun texto(a: AccionCitaItem) =
                        if (decidiendo && a.accion == AccionTarjeta.Completar) "Abriendo…" else a.texto
                    plan.primaria?.let { a ->
                        Column(anclaTour(a.accion)?.let { Modifier.tourAncla(it) } ?: Modifier) {
                            BotonAccion(texto(a), colorTono(a.tono), habilitado, lleno = true) { onAccion(a.accion) }
                        }
                    }
                    plan.secundaria?.let { a ->
                        Column(anclaTour(a.accion)?.let { Modifier.tourAncla(it) } ?: Modifier) {
                            BotonAccion(texto(a), colorTono(a.tono), habilitado) { onAccion(a.accion) }
                        }
                    }
                    if (plan.menu.isNotEmpty()) {
                        MenuMasAcciones(plan.menu, habilitado) { onAccion(it) }
                    }
                }
            }
        }
    }
}

/** Anclas de los tutoriales (se señala la primera visible de la agenda). */
private fun anclaTour(a: AccionTarjeta): String? = when (a) {
    AccionTarjeta.Completar -> "agenda.completar"
    AccionTarjeta.Cobrar -> "agenda.cobrar"
    AccionTarjeta.Triaje -> "agenda.triaje"
    AccionTarjeta.Atender, AccionTarjeta.AtenderDirecto -> "agenda.atender"
    else -> null
}

@Composable
private fun colorTono(t: TonoAccion): Color {
    val c = Sania.colors
    return when (t) {
        TonoAccion.OK -> c.ok
        TonoAccion.INFO -> c.info
        TonoAccion.NAVY -> c.navy
        TonoAccion.PEND -> c.pend
        TonoAccion.ERROR -> c.error
        TonoAccion.PURPLE -> c.purple
        TonoAccion.TEAL -> c.teal
        TonoAccion.SUAVE -> c.textoSuave
    }
}

/**
 * «⋯ Más»: el resto de las acciones de la cita, por grupo (Atención · Cobro ·
 * Cita, con Cancelar en rojo al final). Mismo patrón de menú que la lista de
 * sesiones (DropdownMenu): no cambia la altura de la tarjeta al abrirse.
 */
@Composable
private fun MenuMasAcciones(
    grupos: List<Pair<GrupoAccion, List<AccionCitaItem>>>,
    habilitado: Boolean,
    onAccion: (AccionTarjeta) -> Unit,
) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(false) }
    Box {
        BotonAccion("⋯ Más", c.textoSuave, habilitado) { abierto = true }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false },
            modifier = Modifier.background(c.superficie)) {
            grupos.forEachIndexed { i, (grupo, items) ->
                if (i > 0) Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(c.borde))
                Text(grupo.titulo.uppercase(), color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                items.forEach { a ->
                    DropdownMenuItem(
                        text = {
                            Text(a.texto, color = if (a.tono == TonoAccion.ERROR) c.error else c.texto,
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        },
                        enabled = habilitado,
                        onClick = { abierto = false; onAccion(a.accion) },
                    )
                }
            }
        }
    }
}

/**
 * 💰 compacto junto a la píldora de pago (no un botón grande aparte): solo
 * aparece cuando hay deuda que cobrar.
 */
@Composable
private fun CobroCompacto(a: AccionCitaItem, habilitado: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
            .background(c.ok.copy(alpha = 0.14f))
            .border(1.dp, c.ok, RoundedCornerShape(Sania.shape.pill.dp))
            .tocable(habilitado = habilitado) { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) { Text(a.texto, color = c.ok, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

/**
 * Lo que la tarjeta necesita de la sala de espera (lo arma el ViewModel con
 * `etapaLlegada`). [medica] = la cita va por la consulta guiada.
 */
data class SalaTarjeta(
    val etapa: pe.saniape.app.data.staff.EtapaLlegada,
    val atencion: pe.saniape.app.data.staff.AtencionLlegada?,
    val triajeOn: Boolean,
    val medica: Boolean,
    val marcando: Boolean,
    /** Minutos en la sala (desde la llegada), o null. */
    val minutos: Int?,
)

/**
 * Indicador de la sala bajo la cita (gemelo de `indicadorLlegada` de /citas).
 * "Atendido" no se repite: ya lo dice el badge "Completada".
 */
@Composable
private fun IndicadorLlegada(
    s: SalaTarjeta,
    /** Sin 'historia_clinica' no se muestran los signos vitales del triaje. */
    verValores: Boolean = true,
) {
    val c = Sania.colors
    val espera = pe.saniape.app.data.staff.textoEspera(s.minutos)
    val (texto, color) = when (s.etapa) {
        EtapaLlegada.ATENDIDO -> return
        EtapaLlegada.POR_LLEGAR -> "○ ${EtapaLlegada.POR_LLEGAR.nombre}" to c.textoSuave
        EtapaLlegada.EN_CONSULTA -> "▶ ${EtapaLlegada.EN_CONSULTA.nombre}" to c.purple
        EtapaLlegada.LLEGO -> "🔔 ${EtapaLlegada.LLEGO.nombre}$espera${if (s.triajeOn) " · sin triaje" else ""}" to c.pend
        EtapaLlegada.TRIAJE -> {
            val partes = if (verValores) pe.saniape.app.data.staff.resumenTriaje(s.atencion) else ""
            "🩺 ${EtapaLlegada.TRIAJE.nombre}${if (partes.isNotBlank()) " $partes" else ""}$espera" to c.ok
        }
    }
    Text(texto, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 14.sp,
        modifier = Modifier.padding(top = 4.dp))
    if (s.etapa == EtapaLlegada.TRIAJE) s.atencion?.triajePorNombre?.let {
        Text("Tomado por $it", color = c.textoSuave, fontSize = 10.sp)
    }
}

/**
 * "🎥 Unirse · Zoom" (o el dominio: "Unirse · sala.clinica.pe"), gemelo de
 * `BotonUnirse` de la web. Ancho completo en la tarjeta: es la acción del momento.
 */
@Composable
fun BotonUnirse(enlace: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Sania.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(c.ok.copy(alpha = 0.12f))
            .border(1.dp, c.ok, RoundedCornerShape(Sania.shape.sm.dp))
            .tocable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text("🎥 Unirse", color = c.ok, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(" · ${pe.saniape.app.data.staff.etiquetaEnlace(enlace)}", color = c.ok, fontSize = 12.sp,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun BotonAccion(
    label: String, color: Color, habilitado: Boolean,
    /** La acción del momento: botón lleno (las demás, contorno tintado). */
    lleno: Boolean = false,
    onClick: () -> Unit,
) {
    val c = Sania.colors
    // Texto sobre el color lleno: blanco sobre navy; sobre los de estado, el color
    // de la tarjeta (claro: blanco; oscuro: el fondo, que contrasta con los tonos claros).
    val sobre = if (color == c.navy) c.sobreNavy else c.superficie
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(if (lleno) color else color.copy(alpha = 0.12f))
            .border(1.dp, color, RoundedCornerShape(Sania.shape.sm.dp))
            .tocable(habilitado = habilitado) { onClick() }
            .padding(horizontal = if (lleno) 16.dp else 12.dp, vertical = 8.dp),
    ) { Text(label, color = if (lleno) sobre else color, fontSize = if (lleno) 13.sp else 12.sp, fontWeight = FontWeight.Bold) }
}

/** Icono de contacto compacto (📞/💬) — botón redondo discreto junto al nombre. */
@Composable
private fun IconoContacto(emoji: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(color.copy(alpha = 0.12f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = 15.sp) }
}

@Composable
private fun Chip(texto: String, fg: Color, bg: Color) {
    Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg)
        .padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(texto, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

/** Chip prominente del TIPO de cita (icono + nombre en mayúsculas + color global). */
@Composable
private fun ChipTipo(tipo: String?, nombre: String, numeroSesion: Int?, atenuado: Boolean) {
    val color = EstadosColor.tipo(tipo)
    val fg = if (atenuado) Sania.colors.textoSuave else color.fg
    val bg = if (atenuado) Sania.colors.chipBg else color.bg
    val etiqueta = buildString {
        append(nombre.uppercase())
        if (tipo == "Sesión" && numeroSesion != null) append(" #$numeroSesion")
    }
    Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg)
        .padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text("${EstadosColor.iconoTipo(tipo)} $etiqueta", color = fg,
            fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BadgeEstadoCita(estado: String, confirmadaPaciente: Boolean = false) {
    val c = Sania.colors
    // Colores desde la config global (Confirmada=verde, Completada=azul, etc.).
    val color = EstadosColor.cita(estado)
    val etiqueta = EstadosColor.etiquetaCita(estado)
    Column(horizontalAlignment = Alignment.End) {
        Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(color.bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)) {
            Text(etiqueta, color = color.fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        if (confirmadaPaciente) {
            Text("✓ Confirmó el ${LocalTerminologiaPaciente.current.paciente}", color = c.ok, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp))
        }
    }
}

private fun formato2(n: Double): String {
    val cent = (n * 100).toLong()
    return "${cent / 100}.${(cent % 100).toString().padStart(2, '0')}"
}

/**
 * El WhatsApp prearmado de la cita (gemelo de whatsappDeCita en /citas web):
 * nombre completo, fecha, hora, servicio, profesional y dirección; si la cita
 * se agendó hoy, "Hemos confirmado su cita en …" (Centro OMER, 2026-10-14).
 * null = cita pasada o cerrada (se abre el chat vacío).
 */
private fun mensajeWhatsAppDeCita(cita: CitaStaff, flujo: FlujoClinica): String? {
    val ctx = pe.saniape.app.data.staff.StaffContextoRepo.actual
    val zona = ctx?.zonaDeSede(cita.sedeId)
    val hoy = pe.saniape.app.data.staff.hoyEnIso(zona)
    val porVenir = (cita.estado == "Pendiente" || cita.estado == "Confirmada") && cita.fecha.take(10) >= hoy
    if (!porVenir) return null
    // La dirección de la sede de la cita (sin sede: la principal); si no, la de la clínica.
    val sede = ctx?.sedes?.let { s -> s.firstOrNull { it.id == cita.sedeId } ?: s.firstOrNull { it.esPrincipal } }
    val enlace = cita.confirmacionToken?.let {
        "${pe.saniape.app.data.Supabase.SITE_URL.trimEnd('/')}/cita/${pe.saniape.app.ui.urlEncode(it)}"
    }
    return pe.saniape.app.ui.mensajeCita(
        nombrePaciente = cita.pacienteNombre,
        fecha = cita.fecha,
        hora = cita.hora,
        clinica = ctx?.clinicaNombre,
        hoy = hoy,
        enlace = enlace,
        servicio = cita.procedimiento ?: cita.tipo?.let { flujo.nombreTipo(it) },
        profesional = cita.terapeutaNombre,
        direccion = sede?.direccion?.takeIf { it.isNotBlank() } ?: ctx?.clinicaDireccion,
        recienAgendada = pe.saniape.app.ui.citaRecienAgendada(cita.creadaAt, hoy, pe.saniape.app.data.staff.zonaDe(zona)),
        // Cita virtual: el enlace en lugar de la dirección (gemelo de la web).
        virtual = cita.esVirtual,
        enlaceVirtual = cita.enlaceVirtual,
    )
}
