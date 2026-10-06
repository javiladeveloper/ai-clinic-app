package pe.saniape.app.ui.clinica.agenda.componentes

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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.CitaStaff
import pe.saniape.app.ui.clinica.agenda.modales.textoSoles
import pe.saniape.app.data.staff.EtapaLlegada
import pe.saniape.app.ui.hora12
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.EstadosColor
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.ui.nombreDeSaludo

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
) {
    val c = Sania.colors
    val acciones = recordarAcciones()
    // Color del tipo desde la config global (reutilizable en toda la app).
    val tipoColor = EstadosColor.tipo(cita.tipo)

    // Atenuar las cerradas (completada/cancelada) para que las activas resalten.
    val cerrada = cita.estado == "Completada" || cita.estado == "Cancelada"

    // Solapamiento en la franja (misma hora + mismo profesional): 2 = ámbar, 3+ = rojo.
    // Solo tinta las citas ACTIVAS (las cerradas ya se atenúan y no deben "gritar").
    val solape = EstadosColor.solape(conteoFranja).takeUnless { cerrada }

    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            // Sombra corta: da el relieve que hace que la tarjeta se lea como un
            // objeto sobre el fondo y no como un rectángulo dibujado. Va antes
            // del clip para que se proyecte fuera de la forma.
            .shadow(2.dp, RoundedCornerShape(Sania.shape.md.dp))
            .clip(RoundedCornerShape(Sania.shape.md.dp)).background(solape?.bg ?: c.superficie)
            .border(1.dp, solape?.fg ?: c.borde, RoundedCornerShape(Sania.shape.md.dp)),
    ) {
        // Barra de color lateral: el solapamiento manda (ámbar/rojo); si no, el tipo.
        Box(Modifier.width(Sania.dim.acento).fillMaxHeight()
            .background(when { cerrada -> c.borde; solape != null -> solape.fg; else -> tipoColor.fg }))

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
                    Modifier.weight(1f).clickable { cita.pacienteId?.let(onVerResumen) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(cita.pacienteNombre ?: "Paciente", color = c.texto,
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
                    // WhatsApp con el RECORDATORIO de la cita prellenado (como el 📱 de la web):
                    // un toque y el mensaje sale listo para enviar.
                    IconoContacto("💬", pe.saniape.app.ui.theme.Paleta.WhatsApp) {
                        val n = tel.filter { ch -> ch.isDigit() }.let { if (it.length <= 9) "51$it" else it }
                        val nombre = nombreDeSaludo(cita.pacienteNombre) ?: ""
                        val msg = "Hola $nombre 👋 Te recordamos tu cita" +
                            (cita.tipo?.let { " de ${flujo.nombreTipo(it).lowercase()}" } ?: "") +
                            " el ${cita.fecha} a las ${hora12(cita.hora)}. ¿Nos confirmas tu asistencia? 🙌"
                        acciones.abrirUrl("https://wa.me/$n?text=${pe.saniape.app.ui.urlEncode(msg)}")
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
            sala?.let { IndicadorLlegada(it) }

            // Dinero de la cita (como la web): solo Consulta/Evaluación con costo; la
            // sesión se cobra desde su tratamiento, nunca como cita.
            val cobrable = cita.tipo != "Sesión" && (cita.costo ?: 0.0) > 0 && cita.estado != "Cancelada"
            val pagada = cita.pagadaAt != null
            // El badge del servidor manda: con él, no se repiten los chips locales de pago.
            val conBadgePago = estadoPago?.mostrable == true && cita.estado != "Cancelada"

            // Chips: costo, Web, Asignar (discretos, en una línea)
            val chips = buildList {
                if (puedeVerCosto) {
                    when {
                        (cita.costo ?: 0.0) > 0 -> add(Triple("S/ ${formato2(cita.costo!!)}", c.teal, c.tealBg))
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
                    add(Triple("⚠ Debe ${textoSoles(cita.costo ?: 0.0)}", c.error, c.errorBg))
                }
                if (cita.origen == "online") add(Triple("🌐 Web", c.purple, c.purpleBg))
                if (cita.terapeutaId == null && cita.origen == "online") add(Triple("⚠ Asignar", c.pend, c.pendBg))
                // Solapamiento: N citas en la misma hora del mismo profesional.
                if (!cerrada && solape != null) {
                    val icono = if (conteoFranja >= 3) "⛔" else "⚠"
                    add(Triple("$icono $conteoFranja en esta hora", solape.fg, solape.bg))
                }
            }
            if (chips.isNotEmpty() || conBadgePago) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                    if (conBadgePago) BadgeEstadoPago(estadoPago, cita.estado)
                    chips.forEach { (t, fg, bg) -> Chip(t, fg, bg) }
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

            // Acciones según estado (separadas por un divisor sutil)
            val acc = accionesPara(cita.estado, cita.tipo, odontologia && cita.pacienteId != null,
                pasarA = flujo.labelEvaluacion.takeIf { flujo.pasaAEvaluacion(cita.tipo) },
                crearTratamiento = crearTratamiento && cita.pacienteId != null,
                sala = sala?.takeIf { cita.pacienteId != null },
                evaluacionPsico = evaluacionPsico && cita.tratamientoId != null,
                cobrar = cobrable && puedeCobrar && !pagada,
                anularCobro = anularCobro && pagada)
            if (acc.isNotEmpty()) {
                Spacer(Modifier.height(Sania.dim.md))
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                Spacer(Modifier.height(Sania.dim.md))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    acc.forEach { (label, accion, color) ->
                        BotonAccion(label, color, !accionando) { onAccion(accion) }
                    }
                }
            }
        }
    }
}

enum class AccionTarjeta {
    Confirmar, Completar, Cancelar, Revertir, Editar, PasarEvaluacion, Repetir, Odontograma, CrearTratamiento,
    /** Sala de espera: "🔔 Llegó" y "🩺 Triaje". */
    Llego, Triaje,
    /** "▶ Atender": la consulta guiada (nativa, pantalla completa). */
    Atender,
    /** "💰 Cobrar": registrar el cobro de una Consulta/Evaluación (método + fecha del pago). */
    Cobrar,
    /** "🧠 Evaluación": el espacio de trabajo de la evaluación psicológica de su tratamiento. */
    EvaluacionPsico,
    /** "↺ Anular cobro": borra el cobro dividido de caja y la cita vuelve a "por cobrar" (solo Admin). */
    AnularCobro,
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
private fun IndicadorLlegada(s: SalaTarjeta) {
    val c = Sania.colors
    val espera = pe.saniape.app.data.staff.textoEspera(s.minutos)
    val (texto, color) = when (s.etapa) {
        EtapaLlegada.ATENDIDO -> return
        EtapaLlegada.POR_LLEGAR -> "○ ${EtapaLlegada.POR_LLEGAR.nombre}" to c.textoSuave
        EtapaLlegada.EN_CONSULTA -> "▶ ${EtapaLlegada.EN_CONSULTA.nombre}" to c.purple
        EtapaLlegada.LLEGO -> "🔔 ${EtapaLlegada.LLEGO.nombre}$espera${if (s.triajeOn) " · sin triaje" else ""}" to c.pend
        EtapaLlegada.TRIAJE -> {
            val partes = pe.saniape.app.data.staff.resumenTriaje(s.atencion)
            "🩺 ${EtapaLlegada.TRIAJE.nombre}${if (partes.isNotBlank()) " $partes" else ""}$espera" to c.ok
        }
    }
    Text(texto, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 14.sp,
        modifier = Modifier.padding(top = 4.dp))
    if (s.etapa == EtapaLlegada.TRIAJE) s.atencion?.triajePorNombre?.let {
        Text("Tomado por $it", color = c.textoSuave, fontSize = 10.sp)
    }
}

/** Acciones disponibles según estado/tipo (espeja accionesCita de la web). */
@Composable
private fun accionesPara(
    estado: String,
    tipo: String?,
    /** Solo en clínicas de odontología y con paciente. */
    odontograma: Boolean = false,
    crearTratamiento: Boolean = false,
    sala: SalaTarjeta? = null,
    /** Nombre del paso Evaluación en el flujo de la cita; null = no hay paso al que pasar. */
    pasarA: String? = null,
    /** Consulta/Evaluación con costo, sin cobrar, y quien mira tiene permiso 'pagos'. */
    cobrar: Boolean = false,
    evaluacionPsico: Boolean = false,
    /** Cobro dividido ya hecho y quien mira es Admin con 'pagos'. */
    anularCobro: Boolean = false,
): List<Triple<String, AccionTarjeta, Color>> {
    val c = Sania.colors
    val lista = mutableListOf<Triple<String, AccionTarjeta, Color>>()
    // El dinero va primero, como la moneda de la web: el paciente llega al
    // mostrador y paga antes de pasar (o paga hoy la evaluación de mañana).
    if (cobrar) lista.add(Triple("💰 Cobrar", AccionTarjeta.Cobrar, c.ok))
    val activa = estado == "Pendiente" || estado == "Confirmada"
    // SALA DE ESPERA (gemelo de `principalSala` de /citas): la acción del MOMENTO
    // reemplaza a Confirmar/Completar mientras el paciente recorre la sala:
    // por llegar → Llegó; llegó → Triaje (si la clínica lo toma); después →
    // Atender (consulta guiada, nativa) o Completar. Lo demás queda detrás.
    val principal: AccionTarjeta? = when {
        sala == null || !activa -> null
        sala.etapa == EtapaLlegada.POR_LLEGAR -> AccionTarjeta.Llego
        sala.etapa == EtapaLlegada.LLEGO && sala.triajeOn -> AccionTarjeta.Triaje
        else -> AccionTarjeta.Atender
    }
    val textoAtender = if (sala?.medica == true) (if (sala.etapa == EtapaLlegada.EN_CONSULTA) "▶ Continuar" else "▶ Atender") else "✓ Completar"
    val accionAtender = if (sala?.medica == true) AccionTarjeta.Atender else AccionTarjeta.Completar
    val textoTriaje = if (sala?.atencion?.triajeAt != null) "🩺 Triaje ✓ (corregir)" else "🩺 Triaje"
    when (principal) {
        AccionTarjeta.Llego -> lista.add(Triple(if (sala?.marcando == true) "…" else "🔔 Llegó", AccionTarjeta.Llego, c.ok))
        AccionTarjeta.Triaje -> lista.add(Triple("🩺 Triaje", AccionTarjeta.Triaje, c.info))
        AccionTarjeta.Atender -> lista.add(Triple(textoAtender, accionAtender, c.navy))
        else -> Unit
    }
    if (principal != null) {
        if (estado == "Pendiente") lista.add(Triple("✓ Confirmar", AccionTarjeta.Confirmar, c.ok))
        if (sala?.triajeOn == true && principal != AccionTarjeta.Triaje) lista.add(Triple(textoTriaje, AccionTarjeta.Triaje, c.info))
        if (principal != AccionTarjeta.Atender) lista.add(Triple(textoAtender, accionAtender, c.navy))
    } else if (estado == "Pendiente") lista.add(Triple("✓ Confirmar", AccionTarjeta.Confirmar, c.ok))
    // Evaluación psicológica: el espacio de trabajo, en cualquier estado (como la web).
    if (evaluacionPsico && estado != "Cancelada") lista.add(Triple("🧠 Evaluación", AccionTarjeta.EvaluacionPsico, c.purple))
    if (pasarA != null && activa) lista.add(Triple("→ $pasarA", AccionTarjeta.PasarEvaluacion, c.info))
    if (activa && principal == null) lista.add(Triple("✓ Completar", AccionTarjeta.Completar, c.navy))
    if (estado == "Completada" || estado == "Cancelada") lista.add(Triple("↩ Revertir", AccionTarjeta.Revertir, c.pend))
    // Repetir: agendar la SIGUIENTE cita del mismo paciente en 1 toque (misma info,
    // fecha propuesta a futuro). Muy usado para citar la próxima sesión/control.
    if (estado == "Completada") lista.add(Triple("🔁 Repetir", AccionTarjeta.Repetir, c.teal))
    // Odontograma desde la cita, en cualquier estado: el dentista lo abre para
    // revisar o marcar sin ir a la ficha. No en las sesiones de tratamiento
    // (ahí se atiende lo ya presupuestado). Mismo criterio que la web.
    if (odontograma && tipo != "Sesión") lista.add(Triple("🦷 Odontograma", AccionTarjeta.Odontograma, c.info))
    // Crear el plan desde la cita que evalúa (no cancelada), como la web.
    if (crearTratamiento && estado != "Cancelada") lista.add(Triple("🩺 Crear tratamiento", AccionTarjeta.CrearTratamiento, c.purple))
    // Corregir un cobro dividido es anularlo y volver a cobrar (como la web, solo Admin).
    if (anularCobro && estado != "Cancelada") lista.add(Triple("↺ Anular cobro", AccionTarjeta.AnularCobro, c.error))
    if (estado != "Cancelada" && estado != "Completada") {
        lista.add(Triple("✏ Editar", AccionTarjeta.Editar, c.textoSuave))
        lista.add(Triple("✕ Cancelar", AccionTarjeta.Cancelar, c.error))
    }
    return lista
}

@Composable
private fun BotonAccion(label: String, color: Color, habilitado: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color, RoundedCornerShape(Sania.shape.sm.dp))
            .tocable(habilitado = habilitado) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
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
            Text("✓ Confirmó el paciente", color = c.ok, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp))
        }
    }
}

private fun formato2(n: Double): String {
    val cent = (n * 100).toLong()
    return "${cent / 100}.${(cent % 100).toString().padStart(2, '0')}"
}