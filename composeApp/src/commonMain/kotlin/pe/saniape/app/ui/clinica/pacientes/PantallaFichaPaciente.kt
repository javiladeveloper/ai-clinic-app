package pe.saniape.app.ui.clinica.pacientes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.PacienteStaff
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.SesionFicha
import pe.saniape.app.data.staff.TecnicasRepo
import pe.saniape.app.data.staff.TratamientoPaciente
import pe.saniape.app.ui.ManejarAtras
import pe.saniape.app.ui.hora12
import pe.saniape.app.ui.recordarAcciones
import kotlinx.datetime.toLocalDateTime
import pe.saniape.app.ui.theme.EstadosColor
import pe.saniape.app.ui.theme.Sania

/** Petición de subir un archivo: documento suelto o resultado de una solicitud. */
data class SubidaDoc(val categoria: String, val solicitudId: String?)

/**
 * Ficha del paciente (staff). Resumen + tratamientos con su progreso. Acciones de
 * sesiones/pagos se agregan en el siguiente paso (reusan endpoints de la web).
 */
@Composable
fun PantallaFichaPaciente(ctx: ContextoStaff, pacienteInicial: PacienteStaff, onCerrar: () -> Unit) {
    val c = Sania.colors
    val acciones = recordarAcciones()
    ManejarAtras(activo = true, onAtras = onCerrar)

    val scope = rememberCoroutineScope()
    // Recarga fresca por id (el inicial viene de la lista; aquí traemos lo último).
    var paciente by remember { mutableStateOf(pacienteInicial) }
    var cargando by remember { mutableStateOf(true) }
    // Sesión a completar + su anterior (referencia de evolución para mejorías).
    var completarSesion by remember { mutableStateOf<Pair<SesionFicha, SesionFicha?>?>(null) }
    var editandoPaciente by remember { mutableStateOf(false) }
    var menuPaciente by remember { mutableStateOf(false) }
    var crearSesionEn by remember { mutableStateOf<TratamientoPaciente?>(null) }
    var creandoTratamiento by remember { mutableStateOf(false) }
    var ampliarTratamiento by remember { mutableStateOf<TratamientoPaciente?>(null) }
    var editarTratamiento by remember { mutableStateOf<TratamientoPaciente?>(null) }
    var registrarAtencion by remember { mutableStateOf<TratamientoPaciente?>(null) }
    var recargarToken by remember { mutableStateOf(0) }   // fuerza recarga de las tarjetas
    var tab by remember { mutableStateOf("atenciones") }
    var hitos by remember { mutableStateOf<pe.saniape.app.data.staff.HitosPaciente?>(null) }
    var saldoPendiente by remember { mutableStateOf<Double?>(null) }
    var editarCitaHito by remember { mutableStateOf<pe.saniape.app.data.staff.CitaHito?>(null) }
    // Subida de documento/resultado: categoria "Documento" (suelto) o "Resultado" (de una solicitud).
    var subirDoc by remember { mutableStateOf<SubidaDoc?>(null) }
    var subiendo by remember { mutableStateOf(false) }
    var editarClinico by remember { mutableStateOf(false) }
    var derivarTrat by remember { mutableStateOf<TratamientoPaciente?>(null) }
    var crearCita by remember { mutableStateOf<pe.saniape.app.ui.clinica.PrefillCita?>(null) }
    var especialidadesClinica by remember { mutableStateOf<List<pe.saniape.app.data.staff.EspecialidadClinica>>(emptyList()) }
    LaunchedEffect(Unit) {
        especialidadesClinica = runCatching { PacientesRepo.especialidadesClinica() }.getOrDefault(emptyList())
    }
    LaunchedEffect(pacienteInicial.id, recargarToken) {
        paciente = runCatching { PacientesRepo.porId(pacienteInicial.id) }.getOrNull() ?: pacienteInicial
        hitos = runCatching { PacientesRepo.hitosDe(pacienteInicial.id) }.getOrNull()
        // Saldo general (todos los tratamientos del paciente): acordado − pagado.
        saldoPendiente = runCatching { PacientesRepo.saldoPendienteDe(paciente.tratamientos) }.getOrNull()
        cargando = false
    }
    fun recargar() { recargarToken++ }

    // Crear cita (control que nace de un tratamiento) — pantalla completa.
    crearCita?.let { prefill ->
        pe.saniape.app.ui.clinica.PantallaCrearCita(
            ctx = ctx, fechaInicial = prefill.fecha, prefill = prefill,
            onListo = { crearCita = null; recargar() },
            onCancelar = { crearCita = null },
        )
        return
    }

    // Selector de archivo nativo para subir documentos/resultados. Al elegir: sube al
    // bucket privado (vía endpoint) y registra el documento o lo adjunta a la solicitud.
    val pedido = subirDoc
    val abrirSelector = pe.saniape.app.ui.recordarSelectorArchivo { archivo ->
        val p = pedido ?: return@recordarSelectorArchivo
        subirDoc = null
        scope.launch {
            subiendo = true
            val prefijo = if (p.categoria == "Resultado") "res" else "doc"
            val subido = pe.saniape.app.data.staff.SolicitudesRepo.subirArchivo(
                paciente.id, archivo.nombre, archivo.bytes, archivo.mime, prefijo,
            )
            if (subido != null) {
                val (path, tipo) = subido
                if (p.solicitudId != null) {
                    // Adjuntar como resultado del examen (conserva la nota existente vacía aquí).
                    pe.saniape.app.data.staff.SolicitudesRepo.registrarResultado(p.solicitudId, null, path)
                } else {
                    pe.saniape.app.data.staff.SolicitudesRepo.registrarDocumento(paciente.id, archivo.nombre, path, tipo)
                }
            }
            subiendo = false
            recargar()
        }
    }
    LaunchedEffect(subirDoc) { if (subirDoc != null) abrirSelector() }

    val verContacto = ctx.esGestor && !ctx.modoClinico
    val flagColor = when (paciente.flag) {
        "rojo" -> c.error; "amarillo" -> c.pend; else -> c.ok
    }
    val estado = EstadosColor.paciente(paciente.estado)

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Toolbar nativa
            Row(
                Modifier.fillMaxWidth().background(c.navyDark)
                    .padding(horizontal = Sania.dim.lg, vertical = Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(c.sobreNavy.copy(alpha = 0.15f))
                        .clickable { onCerrar() },
                    contentAlignment = Alignment.Center,
                ) { Text("←", color = c.sobreNavy, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(Sania.dim.md))
                Text("Ficha del paciente", color = c.sobreNavy, fontSize = Sania.txt.subtitulo,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                // Editar paciente + menú (dar de baja/reactivar): solo gestor.
                if (ctx.puede("pacientes")) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(c.sobreNavy.copy(alpha = 0.15f))
                            .clickable { editandoPaciente = true },
                        contentAlignment = Alignment.Center,
                    ) { Text("✏", color = c.sobreNavy, fontSize = 16.sp) }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(c.sobreNavy.copy(alpha = 0.15f))
                            .clickable { menuPaciente = !menuPaciente },
                        contentAlignment = Alignment.Center,
                    ) { Text("⋯", color = c.sobreNavy, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                }
            }

            // Menú del paciente: dar de baja / reactivar.
            if (menuPaciente) {
                Column(Modifier.fillMaxWidth().background(c.superficie)) {
                    val inactivo = paciente.estado == "Inactivo"
                    Text(
                        if (inactivo) "↻ Reactivar paciente" else "Dar de baja al paciente",
                        color = if (inactivo) c.ok else c.error, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth().clickable {
                            menuPaciente = false
                            scope.launch {
                                PacientesRepo.cambiarEstadoPaciente(paciente.id, if (inactivo) "Nuevo" else "Inactivo")
                                recargar()
                            }
                        }.padding(horizontal = Sania.dim.xl, vertical = Sania.dim.md),
                    )
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                }
            }

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Sania.dim.xl)) {
                // ── Cabecera: nombre + semáforo + estado ──
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(flagColor))
                    Spacer(Modifier.width(Sania.dim.sm))
                    Text(paciente.nombre, color = c.texto, fontSize = Sania.txt.titulo, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(estado.bg)
                        .padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text(paciente.estado ?: "—", color = estado.fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Datos: edad · ocupación
                val datos = listOfNotNull(
                    paciente.edad?.let { "$it años" },
                    paciente.ocupacion,
                ).joinToString(" · ")
                if (datos.isNotBlank()) {
                    Text(datos, color = c.textoSuave, fontSize = Sania.txt.cuerpo,
                        modifier = Modifier.padding(top = 4.dp))
                }

                // Contacto (gestor): teléfono con 📞/💬
                if (verContacto) {
                    paciente.telefono?.takeIf { it.isNotBlank() }?.let { tel ->
                        Spacer(Modifier.height(Sania.dim.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BotonContacto("📞 Llamar", c.navy) { acciones.abrirUrl("tel:${tel.filter { ch -> ch.isDigit() }}") }
                            BotonContacto("💬 WhatsApp", androidx.compose.ui.graphics.Color(0xFF25D366)) {
                                val n = tel.filter { ch -> ch.isDigit() }.let { if (it.length <= 9) "51$it" else it }
                                acciones.abrirUrl("https://wa.me/$n")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Sania.dim.lg))

                // ── Stat cards: Progreso · Saldo · Próxima cita · Última atención ──
                // Saldo GENERAL del paciente (todos sus tratamientos): acordado − pagado.
                val saldo = saldoPendiente
                // Stat card ADAPTATIVA (evita un número sumado confuso con multi-tratamiento):
                //  - 1 solo tratamiento por sesiones activo → PROGRESO "N/M ses."
                //  - varios tratamientos activos → ATENCIONES "N activos"
                //  - solo consultas (sin sesiones) → EN CONTROL
                //  - nada → ESTADO del paciente
                val activos = paciente.tratamientos.filter { it.estado == "Activo" }
                val activosSesion = activos.filter { !it.esConsulta }
                val (statLabel, statValor, statColor) = when {
                    activosSesion.size == 1 -> {
                        val t = activosSesion.first()
                        Triple("PROGRESO", "${t.sesionesCompletadas}/${t.totalSesiones} ses.",
                            if (t.sesionesCompletadas >= t.totalSesiones) c.ok else c.navy)
                    }
                    activos.size > 1 -> Triple("ATENCIONES", "${activos.size} activos", c.navy)
                    activos.size == 1 && activos.first().esConsulta -> Triple("ATENCIÓN", "En control", c.navy)
                    else -> Triple("ESTADO", paciente.estado ?: "—", c.navy)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(statLabel, statValor, statColor, Modifier.weight(1f))
                    if (ctx.puede("pagos")) {
                        StatCard(
                            "SALDO PENDIENTE",
                            when { saldo == null -> "…"; saldo > 0.005 -> "S/ ${formatoMonto(saldo)}"; else -> "S/ 0.00" },
                            if (saldo != null && saldo > 0.005) c.error else c.ok, Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
                if (ctx.puede("citas")) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard("PRÓXIMA CITA",
                            hitos?.proximaCitaFecha?.let { "$it ${hitos?.proximaCitaHora ?: ""}".trim() } ?: "Sin citas",
                            c.navy, Modifier.weight(1f))
                        StatCard("ÚLTIMA ATENCIÓN", hitos?.ultimaAtencionFecha ?: "—", c.textoSuave, Modifier.weight(1f))
                    }
                }

                Spacer(Modifier.height(Sania.dim.lg))

                // ── Tabs: Atenciones · Exámenes · Pagos(perm) · Resumen ──
                val tabs = buildList {
                    add("atenciones" to "🩺 Atenciones")
                    add("examenes" to "🔬 Exámenes")
                    if (ctx.puede("pagos")) add("pagos" to "💰 Pagos")
                    add("resumen" to "📋 Resumen")
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tabs.forEach { (key, label) ->
                        val activo = tab == key
                        Box(
                            Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                                .background(if (activo) c.navy else c.superficie)
                                .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
                                .clickable { tab = key }.padding(horizontal = 14.dp, vertical = 8.dp),
                        ) { Text(label, color = if (activo) c.sobreNavy else c.texto, fontSize = 12.sp,
                            fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal, maxLines = 1) }
                    }
                }
                Spacer(Modifier.height(Sania.dim.md))

                if (cargando) {
                    Box(Modifier.fillMaxWidth().padding(Sania.dim.lg), Alignment.Center) {
                        CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp)
                    }
                } else when (tab) {
                    "atenciones" -> ContenidoAtenciones(
                        ctx = ctx, paciente = paciente, hitos = hitos,
                        onCompletarSesion = { ses, anterior -> completarSesion = ses to anterior },
                        onRecargar = { recargar() },
                        onEditarTrat = { editarTratamiento = it },
                        onAmpliarTrat = { ampliarTratamiento = it },
                        onCambiarEstadoTrat = { tId, est -> scope.launch { PacientesRepo.cambiarEstadoTratamiento(tId, est); recargar() } },
                        onCrearSesion = { crearSesionEn = it },
                        onNuevoTratamiento = { creandoTratamiento = true },
                        onEditarCita = { editarCitaHito = it },
                        onDerivar = { derivarTrat = it },
                        // Derivar solo si hay plan Premium Y la clínica tiene >1 especialidad.
                        puedeDerivar = ctx.can("derivaciones") && especialidadesClinica.size > 1,
                        // Agendar control: cita vinculada AL TRATAMIENTO de origen (trazabilidad).
                        onAgendarControl = { t ->
                            crearCita = pe.saniape.app.ui.clinica.PrefillCita(
                                tipo = "Consulta",
                                pacienteId = paciente.id, pacienteNombre = paciente.nombre,
                                fecha = pe.saniape.app.ui.clinica.agenda.hoyIso(), hora = "09:00",
                                terapeutaId = t.terapeutaId,
                                especialidadId = especialidadesClinica.firstOrNull { it.nombre == t.especialidadNombre }?.id,
                                tratamientoId = t.id,
                            )
                        },
                        // Registrar atención (clínico): diagnóstico/medicación/próximo control.
                        onRegistrarAtencion = { registrarAtencion = it },
                    )
                    "examenes" -> {
                        if (subiendo) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                                CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Subiendo archivo…", color = c.textoSuave, fontSize = Sania.txt.pequeno)
                            }
                        }
                        ContenidoExamenes(
                            ctx = ctx, paciente = paciente, acciones = acciones,
                            onAbrirSubida = { categoria, solId -> subirDoc = SubidaDoc(categoria, solId) },
                            recargaToken = recargarToken,
                        )
                    }
                    "pagos" -> ContenidoPagos(
                        ctx = ctx, paciente = paciente, recargaToken = recargarToken,
                        onVerEnAtenciones = { tab = "atenciones" },
                    )
                    "resumen" -> ContenidoResumen(
                        ctx = ctx, paciente = paciente, acciones = acciones,
                        onEditarClinico = { editarClinico = true },
                    )
                }

                Spacer(Modifier.height(Sania.dim.xxl))
            }
        }
    }

    // Modal crear tratamiento
    if (creandoTratamiento) {
        ModalCrearTratamiento(
            pacienteId = paciente.id,
            miTerapeutaId = ctx.miTerapeutaId,
            diagnosticoPrevio = paciente.diagnostico,
            onCancelar = { creandoTratamiento = false },
            onGuardar = { nuevo ->
                creandoTratamiento = false
                scope.launch {
                    PacientesRepo.crearTratamiento(
                        pacienteId = paciente.id, procedimientoId = nuevo.procedimientoId,
                        terapeutaId = nuevo.terapeutaId, modalidad = nuevo.modalidad,
                        totalSesiones = nuevo.totalSesiones, precioPaquete = nuevo.precioPaquete,
                        precioPorSesion = nuevo.precioPorSesion, precioAcordado = nuevo.precioAcordado,
                        diagnostico = nuevo.diagnostico, citaOrigenId = nuevo.citaOrigenId,
                        medicacion = nuevo.medicacion, proximoControl = nuevo.proximoControl,
                    )
                    recargar()
                }
            },
        )
    }

    // Modal crear sesión para un tratamiento (esencial de la web).
    crearSesionEn?.let { t ->
        ModalCrearSesion(
            t = t,
            miTerapeutaId = ctx.miTerapeutaId,
            puedePagos = ctx.puede("pagos"),
            onCancelar = { crearSesionEn = null },
            onGuardar = { fecha, hora, dur, terapeutaId, estado, costo, notas ->
                crearSesionEn = null
                scope.launch {
                    PacientesRepo.crearSesion(
                        paciente.id, t.id, terapeutaId, fecha, hora,
                        duracion = dur, estado = estado, costo = costo, notas = notas,
                    )
                    recargar()
                }
            },
        )
    }

    // Modal ampliar tratamiento
    ampliarTratamiento?.let { t ->
        ModalAmpliarTratamiento(
            t = t,
            onCancelar = { ampliarTratamiento = null },
            onConfirmar = { sesionesExtra, montoExtra, nota ->
                ampliarTratamiento = null
                scope.launch { PacientesRepo.ampliarTratamiento(t.id, sesionesExtra, montoExtra, nota); recargar() }
            },
        )
    }

    // Modal editar tratamiento (precio/sesiones)
    editarTratamiento?.let { t ->
        ModalEditarTratamiento(
            t = t,
            onCancelar = { editarTratamiento = null },
            onGuardar = { totalSes, precioPaq, precioSes, precioAcord, diag, medic, proxControl ->
                editarTratamiento = null
                scope.launch {
                    PacientesRepo.editarTratamiento(t.id, totalSes, precioPaq, precioSes, precioAcord,
                        diag, medic, proxControl)
                    recargar()
                }
            },
        )
    }

    // Modal "Editar consulta" (todo en uno): cita (fecha/hora) + clínico + costo.
    registrarAtencion?.let { t ->
        // La cita de ESTA consulta (su especialidad), para editar fecha/hora.
        val citaConsulta = hitos?.consultas?.firstOrNull { it.tratamientoId == t.id }
            ?: hitos?.consultas?.firstOrNull { t.especialidadId != null && it.especialidadId == t.especialidadId }
            ?: hitos?.evaluaciones?.firstOrNull { it.tratamientoId == t.id }
            ?: hitos?.evaluaciones?.firstOrNull { t.especialidadId != null && it.especialidadId == t.especialidadId }
        ModalEditarConsulta(
            t = t, cita = citaConsulta,
            esGestor = ctx.esGestor, puedePagos = ctx.puede("pagos"),
            onCancelar = { registrarAtencion = null },
            onGuardar = { e ->
                registrarAtencion = null
                scope.launch {
                    // Clínico + costo en el tratamiento; fecha/hora/notas en la cita (si hay).
                    PacientesRepo.editarTratamiento(t.id, null, null, null, e.costo,
                        e.diagnostico, e.medicacion, e.proximoControl)
                    citaConsulta?.let { ci ->
                        if (e.fecha.isNotBlank()) PacientesRepo.editarCitaHito(ci.id, e.fecha, e.hora, ci.notas)
                    }
                    recargar()
                }
            },
        )
    }

    // Modal editar cita-hito (Consulta/Evaluación realizada): fecha/hora/notas.
    editarCitaHito?.let { cita ->
        ModalEditarCitaHito(
            cita = cita,
            onCancelar = { editarCitaHito = null },
            onGuardar = { fecha, hora, notas ->
                editarCitaHito = null
                scope.launch {
                    PacientesRepo.editarCitaHito(cita.id, fecha, hora, notas); recargar()
                }
            },
        )
    }

    // Modal "Derivar tratamiento" a otra especialidad (nace del tratamiento, no del paciente).
    derivarTrat?.let { t ->
        val actual = t.especialidadNombre
        val destinos = especialidadesClinica.filter { it.nombre != actual }
        ModalDerivar(
            especialidadActual = actual,
            destinos = destinos,
            onCancelar = { derivarTrat = null },
            onGuardar = { desc, espDestinoId ->
                derivarTrat = null
                scope.launch {
                    pe.saniape.app.data.staff.SolicitudesRepo.crearSolicitud(
                        paciente.id, "Derivacion", desc, ctx.miTerapeutaId, espDestinoId, tratamientoId = t.id,
                    )
                    recargar()
                }
            },
        )
    }

    // Modal "Editar datos clínicos" (diagnóstico/alergias/medicación/antecedentes).
    if (editarClinico) {
        ModalDatosClinicos(
            paciente = paciente,
            onCancelar = { editarClinico = false },
            onGuardar = { diag, alergias, medic, antec ->
                editarClinico = false
                scope.launch {
                    PacientesRepo.editarDatosClinicos(paciente.id, diag, alergias, medic, antec); recargar()
                }
            },
        )
    }

    // Modal "Editar paciente" (gestor): datos esenciales + semáforo.
    if (editandoPaciente) {
        ModalEditarPaciente(
            paciente = paciente,
            onCancelar = { editandoPaciente = false },
            onGuardar = { nombre, tel, ocup, edad, flag, diag ->
                editandoPaciente = false
                scope.launch {
                    PacientesRepo.actualizarPaciente(paciente.id, nombre, tel, ocup, edad, flag, diag)
                    recargar()
                }
            },
        )
    }

    // Modal "Completar sesión": técnicas (autocomplete) + mejorías (desde la #2).
    completarSesion?.let { (ses, anterior) ->
        ModalCompletarSesion(
            ses = ses,
            anterior = anterior,
            onCancelar = { completarSesion = null },
            onConfirmar = { tecnicas, mejorias ->
                completarSesion = null
                scope.launch {
                    PacientesRepo.cambiarEstadoSesion(
                        ses.id, "Completada",
                        notas = tecnicas,
                        // mejorías solo desde la sesión #2 ("" limpia, null = no tocar)
                        mejorias = if (ses.numero > 1) (mejorias ?: "") else null,
                    )
                    // Aprender las técnicas para sugerirlas la próxima vez (fire-and-forget).
                    tecnicas?.let { TecnicasRepo.registrar(it) }
                    recargar()
                }
            },
        )
    }
}

/**
 * Completar una sesión (igual que la web): "Procedimientos realizados" con chips +
 * autocompletado (TecnicasInput) y, desde la sesión #2, "Mejorías / evolución".
 * Muestra la sesión anterior como referencia.
 */
@Composable
private fun ModalCompletarSesion(
    ses: SesionFicha,
    anterior: SesionFicha?,
    onCancelar: () -> Unit,
    onConfirmar: (tecnicas: String?, mejorias: String?) -> Unit,
) {
    val c = Sania.colors
    var tecnicas by remember { mutableStateOf(ses.notas ?: "") }
    var mejorias by remember { mutableStateOf(ses.mejorias ?: "") }
    val muestraMejorias = ses.numero > 1

    DialogoForm(
        titulo = "Completar sesión #${ses.numero}",
        subtitulo = "Registra lo realizado en la sesión",
        textoAccion = "✓ Completar",
        onCancelar = onCancelar,
        onAccion = { onConfirmar(tecnicas.trim().ifBlank { null }, mejorias.trim().ifBlank { null }) },
    ) {
        // Referencia: qué se hizo la sesión anterior (evolución).
        if (muestraMejorias && anterior != null &&
            (!anterior.notas.isNullOrBlank() || !anterior.mejorias.isNullOrBlank())) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.chipBg).padding(10.dp),
            ) {
                Text("Sesión anterior (#${anterior.numero})", color = c.textoSuave,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
                anterior.notas?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                }
                anterior.mejorias?.takeIf { it.isNotBlank() }?.let {
                    Text("↗ $it", color = c.ok, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        TarjetaForm(titulo = "Procedimientos realizados", icono = "🩹") {
            pe.saniape.app.ui.clinica.agenda.componentes.TecnicasInput(
                value = tecnicas, onChange = { tecnicas = it },
            )
        }

        if (muestraMejorias) {
            Spacer(Modifier.height(12.dp))
            TarjetaForm(titulo = "Mejorías / evolución", icono = "↗") {
                androidx.compose.material3.OutlinedTextField(
                    value = mejorias, onValueChange = { mejorias = it },
                    placeholder = { Text("Ej: Menos dolor al caminar, mayor rango…", color = c.textoSuave) },
                    modifier = Modifier.fillMaxWidth(), minLines = 2,
                )
            }
        }
    }
}

/**
 * Crear una sesión para un tratamiento (esencial de la web):
 *  - fecha/hora con pickers nativos + duración; avisa si la fecha/hora ya pasó.
 *  - profesional de la(s) misma(s) especialidad(es) del tratamiento (o fijo si vinculado).
 *  - número de sesión informativo (no editable).
 *  - estado (Planificada / En progreso / Completada).
 *  - costo: Sesión suelta = editable; Paquete = informativo (incluida en el paquete).
 *  - notas clínicas. El pago se registra aparte (en la sección Pagos).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ModalCrearSesion(
    t: TratamientoPaciente,
    miTerapeutaId: String?,
    puedePagos: Boolean,
    onCancelar: () -> Unit,
    onGuardar: (fecha: String, hora: String, duracion: Int, terapeutaId: String?, estado: String, costo: Double?, notas: String?) -> Unit,
) {
    val c = Sania.colors
    val esPaquete = t.modalidad == "Paquete"
    var fecha by remember { mutableStateOf(pe.saniape.app.ui.clinica.agenda.hoyIso()) }
    var hora by remember { mutableStateOf("09:00") }
    var duracion by remember { mutableStateOf(45) }
    var estado by remember { mutableStateOf("Planificada") }
    // En suelta el costo se pre-llena con el precio por sesión; en paquete es informativo (0).
    var costo by remember { mutableStateOf(if (esPaquete) "" else (t.precioPorSesion?.toString() ?: "")) }
    var notas by remember { mutableStateOf("") }
    var terapeutaId by remember { mutableStateOf(miTerapeutaId ?: t.terapeutaId) }
    var mostrarFecha by remember { mutableStateOf(false) }
    var mostrarHora by remember { mutableStateOf(false) }

    // Profesionales de la(s) especialidad(es) del tratamiento + número de sesión informativo.
    var terapeutas by remember { mutableStateOf<List<pe.saniape.app.data.staff.TerapeutaConEsp>>(emptyList()) }
    var numeroInfo by remember { mutableStateOf(t.sesionesCompletadas + 1) }
    LaunchedEffect(t.id) {
        terapeutas = runCatching { PacientesRepo.terapeutasConEspecialidad() }.getOrDefault(emptyList())
        val ses = runCatching { PacientesRepo.sesionesDe(t.id) }.getOrDefault(emptyList())
        numeroInfo = (ses.maxOfOrNull { it.numero } ?: t.sesionesCompletadas).coerceAtLeast(t.sesionesCompletadas) + 1
    }
    // Especialidades del profesional actual del tratamiento → filtra colegas de la misma especialidad.
    val espsDelTrat = terapeutas.find { it.id == t.terapeutaId }?.especialidadIds ?: emptyList()
    val colegas = if (espsDelTrat.isEmpty()) terapeutas
        else terapeutas.filter { ter -> ter.especialidadIds.any { it in espsDelTrat } }

    // Aviso si la fecha/hora elegida ya pasó (solo advertencia, igual que la web).
    val hoy = pe.saniape.app.ui.clinica.agenda.hoyIso()
    val fechaPasada = fecha < hoy

    if (mostrarFecha) {
        val estadoP = androidx.compose.material3.rememberDatePickerState()
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { mostrarFecha = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    estadoP.selectedDateMillis?.let { ms ->
                        val d = kotlinx.datetime.Instant.fromEpochMilliseconds(ms)
                            .toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date
                        fecha = "${d.year}-${d.monthNumber.toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}"
                    }
                    mostrarFecha = false
                }) { Text("Aceptar", color = c.navy) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { mostrarFecha = false }) { Text("Cancelar", color = c.textoSuave) } },
        ) { androidx.compose.material3.DatePicker(state = estadoP) }
    }
    if (mostrarHora) {
        val partes = hora.split(":")
        val estadoP = androidx.compose.material3.rememberTimePickerState(
            initialHour = partes.getOrNull(0)?.toIntOrNull() ?: 9,
            initialMinute = partes.getOrNull(1)?.toIntOrNull() ?: 0,
            is24Hour = false,
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { mostrarHora = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    hora = "${estadoP.hour.toString().padStart(2, '0')}:${estadoP.minute.toString().padStart(2, '0')}"
                    mostrarHora = false
                }) { Text("Aceptar", color = c.navy) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { mostrarHora = false }) { Text("Cancelar", color = c.textoSuave) } },
        ) { Box(Modifier.fillMaxWidth().padding(Sania.dim.lg), Alignment.Center) { androidx.compose.material3.TimePicker(state = estadoP) } }
    }

    DialogoForm(
        titulo = "Nueva sesión #$numeroInfo",
        subtitulo = if (esPaquete) "Incluida en el paquete" else "Sesión suelta",
        textoAccion = "Agendar sesión",
        accionHabilitada = fecha.isNotBlank() && hora.isNotBlank(),
        onCancelar = onCancelar,
        onAccion = {
            onGuardar(
                fecha.trim(), hora.trim(), duracion, terapeutaId, estado,
                if (esPaquete) null else costo.toDoubleOrNull(),
                notas.trim().ifBlank { null },
            )
        },
    ) {
        TarjetaForm(titulo = "Programación", icono = "📅") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { EtqForm("Fecha"); SelectorBoxFicha(fecha) { mostrarFecha = true } }
                Column(Modifier.weight(1f)) { EtqForm("Hora"); SelectorBoxFicha(hora12(hora)) { mostrarHora = true } }
            }
            Spacer(Modifier.height(10.dp))
            EtqForm("Duración")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(30, 45, 60).forEach { d ->
                    val activo = duracion == d
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .background(if (activo) c.navy else c.superficie)
                            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                            .clickable { duracion = d }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("$d min", color = if (activo) c.sobreNavy else c.texto, fontSize = 12.sp,
                        fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal) }
                }
            }
            if (fechaPasada) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.pendBg).padding(10.dp)) {
                    Text("⚠ La fecha elegida ya pasó. Si la sesión ya ocurrió, está bien registrarla.",
                        color = c.pend, fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        TarjetaForm(titulo = "Atención", icono = "🩺") {
            EtqForm("Profesional")
            if (miTerapeutaId != null) {
                SelectorBoxFicha("${colegas.find { it.id == miTerapeutaId }?.nombre ?: "Tú"} (tú)", bloqueado = true) {}
            } else {
                SelectorListaFicha(colegas, colegas.find { it.id == terapeutaId },
                    { it.nombre }, "Sin asignar") { terapeutaId = it.id }
            }
            Spacer(Modifier.height(10.dp))
            EtqForm("Estado")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Planificada", "En progreso", "Completada").forEach { e ->
                    val activo = estado == e
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .background(if (activo) c.navy else c.superficie)
                            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                            .clickable { estado = e }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(e, color = if (activo) c.sobreNavy else c.texto, fontSize = 11.sp,
                        fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal, maxLines = 1) }
                }
            }
            if (puedePagos) {
                Spacer(Modifier.height(10.dp))
                if (esPaquete) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .background(c.purpleBg).padding(10.dp)) {
                        Column {
                            Text("📦 Sesión incluida en el paquete", color = c.purple,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("El paquete se cobra aparte (puede pagarse en partes). Registra los abonos en Pagos.",
                                color = c.texto, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                } else {
                    EtqForm("Costo de la sesión (S/)")
                    CampoFicha("", costo, soloNumero = true) { costo = it }
                }
            }
            Spacer(Modifier.height(10.dp))
            EtqForm("Notas clínicas")
            CampoFicha("", notas, multilinea = true) { notas = it }
        }
    }
}

/** Editar una cita-hito ya realizada (Consulta/Evaluación): fecha/hora/notas (no toca costo). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ModalEditarCitaHito(
    cita: pe.saniape.app.data.staff.CitaHito,
    onCancelar: () -> Unit,
    onGuardar: (fecha: String, hora: String, notas: String?) -> Unit,
) {
    val c = Sania.colors
    var fecha by remember { mutableStateOf(cita.fecha) }
    var hora by remember { mutableStateOf(cita.hora ?: "09:00") }
    var notas by remember { mutableStateOf(cita.notas ?: "") }
    var mostrarFecha by remember { mutableStateOf(false) }
    var mostrarHora by remember { mutableStateOf(false) }

    if (mostrarFecha) {
        val estadoP = androidx.compose.material3.rememberDatePickerState()
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { mostrarFecha = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    estadoP.selectedDateMillis?.let { ms ->
                        val d = kotlinx.datetime.Instant.fromEpochMilliseconds(ms)
                            .toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date
                        fecha = "${d.year}-${d.monthNumber.toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}"
                    }
                    mostrarFecha = false
                }) { Text("Aceptar", color = c.navy) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { mostrarFecha = false }) { Text("Cancelar", color = c.textoSuave) } },
        ) { androidx.compose.material3.DatePicker(state = estadoP) }
    }
    if (mostrarHora) {
        val partes = hora.split(":")
        val estadoP = androidx.compose.material3.rememberTimePickerState(
            initialHour = partes.getOrNull(0)?.toIntOrNull() ?: 9,
            initialMinute = partes.getOrNull(1)?.toIntOrNull() ?: 0, is24Hour = false,
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { mostrarHora = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    hora = "${estadoP.hour.toString().padStart(2, '0')}:${estadoP.minute.toString().padStart(2, '0')}"
                    mostrarHora = false
                }) { Text("Aceptar", color = c.navy) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { mostrarHora = false }) { Text("Cancelar", color = c.textoSuave) } },
        ) { Box(Modifier.fillMaxWidth().padding(Sania.dim.lg), Alignment.Center) { androidx.compose.material3.TimePicker(state = estadoP) } }
    }

    DialogoForm(
        titulo = "Editar ${cita.tipo.lowercase()}",
        subtitulo = "${cita.fecha}${cita.hora?.let { " · ${hora12(it)}" } ?: ""}",
        textoAccion = "Guardar",
        onCancelar = onCancelar,
        onAccion = { onGuardar(fecha.trim(), hora.trim(), notas.trim().ifBlank { null }) },
    ) {
        TarjetaForm(titulo = cita.tipo, icono = if (cita.tipo == "Consulta") "💬" else "🔍") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { EtqForm("Fecha"); SelectorBoxFicha(fecha) { mostrarFecha = true } }
                Column(Modifier.weight(1f)) { EtqForm("Hora"); SelectorBoxFicha(hora12(hora)) { mostrarHora = true } }
            }
            Spacer(Modifier.height(10.dp))
            EtqForm("Notas")
            CampoFicha("", notas, multilinea = true) { notas = it }
            Spacer(Modifier.height(4.dp))
            Text("El precio/cobro no se edita aquí (afecta caja). Hazlo desde la web si es necesario.",
                color = c.textoSuave, fontSize = 10.sp)
        }
    }
}

@Composable
private fun EtqMini(t: String) {
    Text(t.uppercase(), color = Sania.colors.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun SelectorBoxFicha(valor: String, bloqueado: Boolean = false, onClick: () -> Unit) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(if (bloqueado) c.chipBg else c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable(enabled = !bloqueado) { onClick() }.padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(valor, color = c.texto, fontSize = Sania.txt.cuerpo)
        Text(if (bloqueado) "🔒" else "▾", color = c.navy, fontSize = 12.sp)
    }
}

@Composable
private fun <T> SelectorListaFicha(items: List<T>, elegido: T?, etiqueta: (T) -> String, placeholder: String, onElegir: (T) -> Unit) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(false) }
    Column {
        SelectorBoxFicha(elegido?.let(etiqueta) ?: placeholder) { abierto = !abierto }
        if (abierto) {
            Column(
                Modifier.fillMaxWidth().padding(top = 4.dp)
                    .clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)),
            ) {
                if (items.isEmpty()) {
                    Text("(Sin opciones)", color = c.textoSuave, fontSize = Sania.txt.pequeno,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp))
                }
                items.forEach { item ->
                    Text(etiqueta(item), color = c.texto, fontSize = Sania.txt.cuerpo,
                        modifier = Modifier.fillMaxWidth().clickable { onElegir(item); abierto = false }
                            .padding(horizontal = 12.dp, vertical = 11.dp))
                }
            }
        }
    }
}

@Composable
private fun ModalEditarPaciente(
    paciente: PacienteStaff,
    onCancelar: () -> Unit,
    onGuardar: (nombre: String, tel: String?, ocup: String?, edad: Int?, flag: String?, diag: String?) -> Unit,
) {
    val c = Sania.colors
    var nombre by remember { mutableStateOf(paciente.nombre) }
    var telefono by remember { mutableStateOf(paciente.telefono ?: "") }
    var ocupacion by remember { mutableStateOf(paciente.ocupacion ?: "") }
    var edad by remember { mutableStateOf(paciente.edad?.toString() ?: "") }
    var diagnostico by remember { mutableStateOf(paciente.diagnostico ?: "") }
    var flag by remember { mutableStateOf(paciente.flag ?: "verde") }

    DialogoForm(
        titulo = "Editar paciente",
        subtitulo = paciente.nombre,
        textoAccion = "Guardar",
        accionHabilitada = nombre.isNotBlank(),
        onCancelar = onCancelar,
        onAccion = {
            onGuardar(nombre.trim(), telefono.trim().ifBlank { null }, ocupacion.trim().ifBlank { null },
                edad.toIntOrNull(), flag, diagnostico.trim().ifBlank { null })
        },
    ) {
        TarjetaForm(titulo = "Datos del paciente", icono = "👤") {
            CampoFicha("Nombre", nombre) { nombre = it }
            Spacer(Modifier.height(8.dp))
            CampoFicha("Teléfono", telefono) { telefono = it }
            Spacer(Modifier.height(8.dp))
            CampoFicha("Ocupación", ocupacion) { ocupacion = it }
            Spacer(Modifier.height(8.dp))
            CampoFicha("Edad", edad, soloNumero = true) { edad = it }
            Spacer(Modifier.height(8.dp))
            CampoFicha("Motivo / Diagnóstico", diagnostico, multilinea = true) { diagnostico = it }
            Spacer(Modifier.height(10.dp))
            EtqForm("Comportamiento (semáforo)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("verde" to c.ok, "amarillo" to c.pend, "rojo" to c.error).forEach { (f, col) ->
                    val activo = flag == f
                    Box(
                        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                            .background(if (activo) col else c.superficie)
                            .border(1.dp, col, RoundedCornerShape(Sania.shape.pill.dp))
                            .clickable { flag = f }.padding(horizontal = 14.dp, vertical = 6.dp),
                    ) { Text(f.replaceFirstChar { it.uppercase() }, color = if (activo) c.sobreNavy else col,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun CampoFicha(
    label: String, value: String, soloNumero: Boolean = false, multilinea: Boolean = false, onChange: (String) -> Unit,
) {
    val c = Sania.colors
    Text(label, color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = { if (soloNumero) onChange(it.filter { ch -> ch.isDigit() }) else onChange(it) },
        singleLine = !multilinea, minLines = if (multilinea) 2 else 1,
        keyboardOptions = if (soloNumero) androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number) else androidx.compose.foundation.text.KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

@Composable
private fun Etiqueta(t: String) {
    Text(t, color = Sania.colors.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun BotonContacto(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(Sania.shape.sm.dp))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 7.dp),
    ) { Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

/** Formatea un monto a 2 decimales (sin depender de String.format, no disponible en common). */
private fun formatoMonto(n: Double): String {
    val cent = (n * 100).toLong()
    return "${cent / 100}.${(cent % 100).toString().padStart(2, '0')}"
}

/** Tarjeta de estadística (Estado / Saldo / Próxima cita / Última atención). */
@Composable
private fun StatCard(label: String, valor: String, valorColor: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val c = Sania.colors
    Column(
        modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(12.dp),
    ) {
        Text(label, color = c.textoSuave, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(4.dp))
        Text(valor, color = valorColor, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Pestaña Atenciones: Recorrido (FlujoGuiado) + tarjetas de tratamiento + nuevo tratamiento. */
@Composable
private fun ContenidoAtenciones(
    ctx: ContextoStaff,
    paciente: PacienteStaff,
    hitos: pe.saniape.app.data.staff.HitosPaciente?,
    onCompletarSesion: (SesionFicha, SesionFicha?) -> Unit,
    onRecargar: () -> Unit,
    onEditarTrat: (TratamientoPaciente) -> Unit,
    onAmpliarTrat: (TratamientoPaciente) -> Unit,
    onCambiarEstadoTrat: (String, String) -> Unit,
    onCrearSesion: (TratamientoPaciente) -> Unit,
    onNuevoTratamiento: () -> Unit,
    onEditarCita: (pe.saniape.app.data.staff.CitaHito) -> Unit,
    onDerivar: (TratamientoPaciente) -> Unit,
    puedeDerivar: Boolean,
    onAgendarControl: (TratamientoPaciente) -> Unit,
    onRegistrarAtencion: (TratamientoPaciente) -> Unit,
) {
    val c = Sania.colors
    Column {
        // Tratamientos: cada tarjeta lleva SU barra de recorrido (sin duplicar arriba).
        Etiqueta("Tratamientos")
        if (paciente.tratamientos.isEmpty()) {
            Text("Sin tratamientos registrados.", color = c.textoSuave, fontSize = Sania.txt.cuerpo)
        } else {
            paciente.tratamientos.forEach { t ->
                Spacer(Modifier.height(Sania.dim.sm))
                // La cita-hito de ESTE tratamiento: la vinculada por tratamiento_id, o de su
                // misma especialidad (no de otra). Así el recorrido no mezcla especialidades.
                fun citaDeEste(lista: List<pe.saniape.app.data.staff.CitaHito>?) =
                    lista?.firstOrNull { it.tratamientoId == t.id }
                        ?: lista?.firstOrNull { t.especialidadId != null && it.especialidadId == t.especialidadId }
                val citaC = citaDeEste(hitos?.consultas)
                val citaE = citaDeEste(hitos?.evaluaciones)
                TarjetaTratamiento(
                    t = t, verPagos = ctx.puede("pagos"), esAdmin = ctx.esAdmin,
                    puedeSesiones = ctx.puede("sesiones"),
                    consultaDone = citaC != null, evalDone = citaE != null,
                    citaConsulta = citaC, citaEvaluacion = citaE,
                    onEditarCita = onEditarCita,
                    onCompletarSesion = onCompletarSesion,
                    onCambioRealizado = onRecargar,
                    onEditar = onEditarTrat, onAmpliar = onAmpliarTrat,
                    onCambiarEstadoTrat = onCambiarEstadoTrat,
                    onCrearSesion = onCrearSesion,
                    // Derivar: requiere plan Premium Y que la clínica tenga >1 especialidad
                    // (si solo hay una, no hay a dónde derivar).
                    onDerivar = onDerivar, puedeDerivar = puedeDerivar,
                    onAgendarControl = onAgendarControl,
                    onRegistrarAtencion = onRegistrarAtencion,
                )
            }
        }
        if (ctx.puede("sesiones") || ctx.puede("pacientes")) {
            Spacer(Modifier.height(Sania.dim.md))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
                    .background(c.navy).clickable { onNuevoTratamiento() }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("➕ Nuevo tratamiento", color = c.sobreNavy, fontWeight = FontWeight.Bold) }
        }
    }
}

/**
 * Pestaña Pagos: resumen financiero del paciente SIN duplicar la PagoCard de Atenciones.
 * Mini-resumen total (acordado/pagado/saldo) + una fila RESUMIDA por tratamiento (cifras +
 * barra + estado de pago). Cobrar/editar se hace en Atenciones (botón "Ver →" lleva ahí).
 */
@Composable
private fun ContenidoPagos(
    ctx: ContextoStaff, paciente: PacienteStaff, recargaToken: Int, onVerEnAtenciones: () -> Unit,
) {
    val c = Sania.colors
    var resumen by remember { mutableStateOf<pe.saniape.app.data.staff.ResumenPagos?>(null) }
    LaunchedEffect(paciente.id, recargaToken) {
        resumen = runCatching { PacientesRepo.resumenPagosDe(paciente.tratamientos) }.getOrNull()
    }

    val facturables = paciente.tratamientos.filter { it.estado != "Cancelado" }
    val r = resumen

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Mini-resumen total
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
                .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
        ) {
            Text("💰 RESUMEN DE PAGOS", color = c.textoSuave, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CifraPago("Acordado", r?.let { "S/ ${formatoMonto(it.acordado)}" } ?: "…", c.texto, Modifier.weight(1f))
                CifraPago("Pagado", r?.let { "S/ ${formatoMonto(it.pagado)}" } ?: "…", c.ok, Modifier.weight(1f))
                CifraPago("Saldo", r?.let { "S/ ${formatoMonto(it.saldo)}" } ?: "…",
                    if (r != null && r.saldo > 0.005) c.error else c.ok, Modifier.weight(1f))
            }
        }

        if (facturables.isEmpty()) {
            Text("Sin tratamientos facturables.", color = c.textoSuave, fontSize = Sania.txt.cuerpo)
        } else {
            // Resumen POR tratamiento (no la PagoCard completa — eso vive en Atenciones).
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
                    .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
            ) {
                Text("POR TRATAMIENTO", color = c.textoSuave, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                facturables.forEachIndexed { i, t ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde).padding(vertical = 0.dp))
                    FilaPagoResumen(
                        t = t, pagado = r?.porTratamiento?.get(t.id) ?: 0.0,
                        onVer = onVerEnAtenciones,
                    )
                }
            }
            Text("El detalle de cada pago y el cobro se gestionan en la pestaña Atenciones.",
                color = c.textoSuave, fontSize = 10.sp)
        }
    }
}

/** Fila resumida de pagos de un tratamiento: nombre + acordado/pagado/saldo + barra + estado. */
@Composable
private fun FilaPagoResumen(t: TratamientoPaciente, pagado: Double, onVer: () -> Unit) {
    val c = Sania.colors
    val acordado = t.montoAcordado
    val saldo = (acordado - pagado).coerceAtLeast(0.0)
    val frac = if (acordado > 0) (pagado / acordado).toFloat().coerceIn(0f, 1f) else 0f
    val pagoEst = EstadosColor.cita(when (t.estadoPago) { "Pagado" -> "Confirmada"; "Parcial" -> "Pendiente"; else -> "Cancelada" })
    Column(Modifier.fillMaxWidth().clickable { onVer() }.padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${if (t.esConsulta) "🩺" else if (t.modalidad == "Paquete") "📦" else "🎫"} ${t.procedimiento ?: "Plan de atención"}",
                color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(pagoEst.bg)
                .padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(t.estadoPago ?: "—", color = pagoEst.fg, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(6.dp))
            Text("Ver →", color = c.navy, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Acordado S/ ${formatoMonto(acordado)}", color = c.textoSuave, fontSize = 11.sp)
            Text("Pagado S/ ${formatoMonto(pagado)}", color = c.ok, fontSize = 11.sp)
            Text("Saldo S/ ${formatoMonto(saldo)}", color = if (saldo > 0.005) c.error else c.ok, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.chipBg)) {
            Box(Modifier.fillMaxWidth(frac).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.ok))
        }
    }
}

@Composable
private fun CifraPago(label: String, valor: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val c = Sania.colors
    Column(
        modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.fondo)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(10.dp),
    ) {
        Text(label.uppercase(), color = c.textoSuave, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(valor, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/**
 * Pestaña Resumen (espeja la web): info card (datos + IMC), datos clínicos importantes
 * (editables), Resumen IA (plan Plus, streaming→texto completo) e Historia clínica (PDF).
 */
@Composable
private fun ContenidoResumen(
    ctx: ContextoStaff, paciente: PacienteStaff, acciones: pe.saniape.app.ui.AccionesNativas,
    onEditarClinico: () -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var aiTexto by remember { mutableStateOf<String?>(null) }
    var aiCargando by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Info card: datos del paciente + IMC
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
                .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
        ) {
            Etiqueta("Datos del paciente")
            val imcTxt = paciente.imc?.let { "${formatoMonto(it)}" }
            val datos = listOfNotNull(
                paciente.dni?.let { "DNI" to it },
                paciente.edad?.let { "Edad" to "$it años" },
                paciente.ocupacion?.let { "Ocupación" to it },
                paciente.talla?.let { "Talla" to "$it cm" },
                paciente.peso?.let { "Peso" to "${formatoMonto(it)} kg" },
                imcTxt?.let { "IMC" to it },
                paciente.fechaIngreso?.let { "Ingreso" to it },
            )
            if (datos.isEmpty()) Text("Sin datos adicionales.", color = c.textoSuave, fontSize = Sania.txt.cuerpo)
            datos.forEach { (k, v) ->
                Row(Modifier.padding(vertical = 1.dp)) {
                    Text("$k:", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.width(80.dp))
                    Text(v, color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Datos clínicos importantes (editables)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
                .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Text("IMPORTANTE PARA EL TRATAMIENTO", color = c.textoSuave, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, modifier = Modifier.weight(1f))
                Text("✏ Editar", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onEditarClinico() })
            }
            FilaClinica("Diagnóstico", paciente.diagnostico)
            FilaClinica("Alergias", paciente.alergias)
            FilaClinica("Medicación", paciente.medicacionActual)
            FilaClinica("Antecedentes", paciente.antecedentes)
        }

        // Resumen IA (plan Plus)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
                .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
        ) {
            Text("✨ RESUMEN CLÍNICO CON IA", color = c.textoSuave, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(8.dp))
            if (!ctx.can("ia")) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg).padding(12.dp)) {
                    Text("🔒 El resumen con IA es una función del plan Plus.", color = c.textoSuave, fontSize = 11.sp)
                }
            } else {
                aiTexto?.let {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg).padding(12.dp)) {
                        Text(it, color = c.texto, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .background(if (aiCargando) c.borde else c.navy)
                        .clickable(enabled = !aiCargando) {
                            scope.launch {
                                aiCargando = true
                                aiTexto = PacientesRepo.resumenIA(paciente)
                                    ?: "⚠ El asistente de IA no está disponible. Intenta más tarde."
                                aiCargando = false
                            }
                        }.padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (aiCargando) "Generando…" else if (aiTexto != null) "↺ Regenerar análisis" else "Generar análisis médico",
                        color = c.sobreNavy, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // Historia clínica (PDF imprimible). Se pide al endpoint con Bearer y se muestra en
        // el visor nativo (con "Imprimir / Guardar PDF"), sin pedir login en el navegador.
        var histCargando by remember { mutableStateOf(false) }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
                .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
        ) {
            Text("📂 HISTORIA CLÍNICA", color = c.textoSuave, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Text("Documento con antecedentes, tratamientos, sesiones y citas — listo para imprimir o guardar como PDF.",
                color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(if (histCargando) c.borde else c.navy)
                    .clickable(enabled = !histCargando) {
                        scope.launch {
                            histCargando = true
                            val html = PacientesRepo.historiaHtml(paciente.id)
                            histCargando = false
                            if (html != null) acciones.abrirHtml(html, paciente.nombre)
                        }
                    }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (histCargando) "Generando…" else "Ver historia (PDF)",
                    color = c.sobreNavy, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun FilaClinica(etq: String, valor: String?) {
    val c = Sania.colors
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("$etq:", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.width(90.dp))
        Text(valor?.takeIf { it.isNotBlank() } ?: "—",
            color = if (valor.isNullOrBlank()) c.textoSuave else c.texto, fontSize = 12.sp,
            fontWeight = if (valor.isNullOrBlank()) FontWeight.Normal else FontWeight.Medium)
    }
}

/** Modal editar datos clínicos: diagnóstico, alergias, medicación, antecedentes. */
@Composable
private fun ModalDatosClinicos(
    paciente: PacienteStaff,
    onCancelar: () -> Unit,
    onGuardar: (diag: String?, alergias: String?, medic: String?, antec: String?) -> Unit,
) {
    var diag by remember { mutableStateOf(paciente.diagnostico ?: "") }
    var alergias by remember { mutableStateOf(paciente.alergias ?: "") }
    var medic by remember { mutableStateOf(paciente.medicacionActual ?: "") }
    var antec by remember { mutableStateOf(paciente.antecedentes ?: "") }
    DialogoForm(
        titulo = "Datos clínicos",
        subtitulo = paciente.nombre,
        textoAccion = "Guardar",
        onCancelar = onCancelar,
        onAccion = {
            onGuardar(diag.trim().ifBlank { null }, alergias.trim().ifBlank { null },
                medic.trim().ifBlank { null }, antec.trim().ifBlank { null })
        },
    ) {
        TarjetaForm(titulo = "Importante para el tratamiento", icono = "📋") {
            CampoFicha("Diagnóstico", diag, multilinea = true) { diag = it }
            Spacer(Modifier.height(8.dp))
            CampoFicha("Alergias", alergias, multilinea = true) { alergias = it }
            Spacer(Modifier.height(8.dp))
            CampoFicha("Medicación actual", medic, multilinea = true) { medic = it }
            Spacer(Modifier.height(8.dp))
            CampoFicha("Antecedentes", antec, multilinea = true) { antec = it }
        }
    }
}

