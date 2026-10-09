package pe.saniape.app.ui.clinica.servicios

import pe.saniape.app.data.staff.LocalTerminologiaPaciente
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.EspecialidadServicio
import pe.saniape.app.data.staff.FormServicio
import pe.saniape.app.data.staff.PROTOCOLOS_CONTROLES
import pe.saniape.app.data.staff.PasoServicioForm
import pe.saniape.app.data.staff.ServicioApp
import pe.saniape.app.data.staff.ServiciosRepo
import pe.saniape.app.data.staff.cuerpoGuardarServicio
import pe.saniape.app.data.staff.esEspecialidadPsico
import pe.saniape.app.data.staff.esServicioDental
import pe.saniape.app.data.staff.especialidadInicial
import pe.saniape.app.data.staff.etiquetaControl
import pe.saniape.app.data.staff.modoEfectivoForm
import pe.saniape.app.data.staff.modoHeredado
import pe.saniape.app.data.staff.problemaFormServicio
import pe.saniape.app.data.staff.dineroActivo
import pe.saniape.app.data.staff.simboloActivo
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.conIndicador
import pe.saniape.app.ui.theme.Sania

/**
 * Crear / editar un servicio. Gemelo de components/procedimientos/ProcedimientoForm.tsx:
 * nombre, descripción, especialidad (por defecto la inicial), modo de cobro
 * (heredar / pago único / sesiones / unidades), evaluación psicológica (clínicas
 * con psicología), precio, paquetes, precio por caras (dental), unidad, serie
 * automática, pasos encadenados y controles. Guarda TODO en una sola llamada
 * (/api/staff/servicio); el servidor valida.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormularioServicio(
    ctx: ContextoStaff,
    inicial: ServicioApp?,
    especialidades: List<EspecialidadServicio>,
    servicios: List<ServicioApp>,
    onCerrar: () -> Unit,
    onGuardado: () -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val activas = remember(especialidades) { especialidades.filter { it.activa } }
    var f by remember(inicial?.id) { mutableStateOf(FormServicio.desde(inicial, especialidadInicial(activas)?.id)) }
    var guardando by remember { mutableStateOf(false) }
    // Los pasos se mandan ENTEROS al guardar (reemplazo). true SOLO si se leyeron
    // bien: si la lectura falla, guardar borraría la cadena (lavado → PRP de Renova).
    var pasosCargados by remember(inicial?.id) { mutableStateOf(inicial == null) }
    var pasosFallo by remember(inicial?.id) { mutableStateOf(false) }
    var intentoPasos by remember { mutableStateOf(0) }
    // Agregador de control a medida: número + unidad (1 días · 7 semanas · 30 meses · 365 años).
    var ctrlNum by remember { mutableStateOf("") }
    var ctrlUnidad by remember { mutableStateOf(30) }
    // Selector de servicio abierto: índice del paso, o -1 = devolución. null = cerrado.
    var eligiendoServicio by remember { mutableStateOf<Int?>(null) }

    // Los pasos del servicio en edición se leen aparte (consulta plana).
    LaunchedEffect(inicial?.id, intentoPasos) {
        val id = inicial?.id ?: return@LaunchedEffect
        pasosFallo = false
        try {
            val pasos = ServiciosRepo.pasosDe(id)
            f = f.copy(pasos = pasos)
            pasosCargados = true
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sin los pasos no se guarda: el botón queda apagado hasta "Reintentar".
            pasosFallo = true
        }
    }

    // Los demás servicios activos (para pasos y devolución).
    val otros = remember(servicios, inicial?.id) { servicios.filter { it.activo && it.id != inicial?.id }.sortedBy { it.nombre.lowercase() } }
    val mostrarTipoClinico = !inicial?.tipoClinico.isNullOrBlank() || activas.any { esEspecialidadPsico(it) }
    val modo = modoEfectivoForm(f, activas)
    val heredado = modoHeredado(f.especialidadId, activas)
    val conSesiones = modo == "sesiones"
    val porUnidades = modo == "unidades"
    val evalActiva = mostrarTipoClinico && f.evalPsico
    val esDental = ctx.mapaDental.activo && esServicioDental(f.especialidadId.ifBlank { null }, ctx.mapaDental)
    // Un precio que no se entiende no se guarda como S/ 0: se avisa y no se guarda.
    val problema = problemaFormServicio(f, activas, mostrarTipoClinico, esDental)
    /** Nombre de un servicio elegido (puede estar inactivo: igual se muestra). */
    fun nombreDe(id: String): String? = servicios.find { it.id == id }?.let { s ->
        s.nombre + (if (s.activo) "" else " (inactivo)")
    }

    fun guardar() {
        // Sin los pasos cargados, guardar los borraría (se mandan enteros).
        if (guardando || !f.listoParaGuardar || !pasosCargados || problema != null) return
        guardando = true
        val cuerpo = cuerpoGuardarServicio(
            f, activas, inicial?.id, inicial?.tipoClinico, mostrarTipoClinico, esDental,
            incluirPasos = pasosCargados,
        )
        scope.launch {
            val r = try { conIndicador { ServiciosRepo.guardar(cuerpo) } } finally { guardando = false }
            if (r.registrada) {
                Toaster.exito(if (inicial == null) "Servicio creado" else "Servicio actualizado")
                onGuardado()
            } else Toaster.error(r.rechazo?.error ?: "Error al guardar. Intenta de nuevo.")
        }
    }

    eligiendoServicio?.let { idx ->
        SelectorServicio(
            servicios = otros,
            onElegir = { s ->
                f = if (idx < 0) f.copy(devolucionProcId = s.id)
                else f.copy(pasos = f.pasos.mapIndexed { j, p -> if (j == idx) p.copy(pasoId = s.id) else p })
                eligiendoServicio = null
            },
            onCerrar = { eligiendoServicio = null },
        )
    }

    DialogoForm(
        titulo = if (inicial == null) "Nuevo servicio" else "Editar servicio",
        subtitulo = null,
        textoAccion = when {
            guardando -> "Guardando…"
            inicial == null -> "Crear servicio"
            else -> "Guardar cambios"
        },
        accionHabilitada = !guardando && f.listoParaGuardar && pasosCargados && problema == null,
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = { guardar() },
    ) {
        problema?.let {
            Text(
                "⚠ $it", color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.errorBg).padding(10.dp),
            )
            Spacer(Modifier.height(10.dp))
        }
        EtqForm("Nombre del servicio *")
        OutlinedTextField(
            value = f.nombre, onValueChange = { f = f.copy(nombre = it) },
            placeholder = { Text("Ej. Consulta general, Limpieza dental…", color = Sania.colors.textoSuave) }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
        )
        Espacio()
        EtqForm("Descripción")
        OutlinedTextField(
            value = f.descripcion, onValueChange = { f = f.copy(descripcion = it) },
            placeholder = { Text("Breve descripción del servicio…", color = Sania.colors.textoSuave) }, minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
        )

        // Especialidad
        Espacio()
        EtqForm("Especialidad")
        if (activas.isEmpty()) {
            Text("No hay especialidades. Créala primero en Más → Especialidades.", color = c.textoSuave, fontSize = 12.sp)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ChipOpcion("🌐 General", f.especialidadId.isBlank()) { f = f.copy(especialidadId = "") }
                activas.forEach { e ->
                    ChipOpcion("${e.icono.orEmpty()} ${e.nombre}".trim(), f.especialidadId == e.id, colorDeHex(e.color)) {
                        f = f.copy(especialidadId = e.id)
                    }
                }
            }
        }

        // Modo de cobro del SERVICIO (por defecto, heredado de la especialidad).
        Espacio()
        EtqForm("¿Cómo se cobra este servicio?")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Triple("simple", "Pago único", "Una vez"), Triple("sesiones", "Por sesiones", "Paquetes"), Triple("unidades", "Por unidades", "Cantidad"))
                .forEach { (v, t, d) ->
                    val sel = modo == v
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .background(if (sel) c.navy else c.superficie)
                            .border(1.dp, if (sel) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                            // Tocar el que ya está fijado vuelve a "heredar".
                            .clickable { f = f.copy(modoCobro = if (f.modoCobro == v) "" else v) }
                            .padding(vertical = 9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(t, color = if (sel) c.sobreNavy else c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(d, color = if (sel) c.sobreNavy.copy(alpha = 0.8f) else c.textoSuave, fontSize = 10.sp)
                    }
                }
        }
        Ayuda(
            when {
                f.modoCobro.isBlank() -> "Heredado de la especialidad (${if (heredado == "sesiones") "por sesiones" else "pago único"}). Toca una opción para fijarlo."
                porUnidades -> "Se cobra cantidad × precio por unidad (ej. folículos, unidades de botox, piezas). El precio final es editable al crear el tratamiento."
                conSesiones -> "Se manejan sesiones y puedes configurar paquetes con descuento."
                else -> "Cobro único por el servicio (sin sesiones ni unidades)."
            },
        )

        // Evaluación psicológica (solo clínicas con psicología).
        if (mostrarTipoClinico) {
            Espacio()
            Caja {
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.clickable {
                    val marcar = !f.evalPsico
                    f = f.copy(evalPsico = marcar, modoCobro = if (marcar) "sesiones" else f.modoCobro)
                }) {
                    Checkbox(
                        checked = f.evalPsico,
                        onCheckedChange = { m -> f = f.copy(evalPsico = m, modoCobro = if (m) "sesiones" else f.modoCobro) },
                        colors = CheckboxDefaults.colors(checkedColor = c.navy),
                    )
                    Column(Modifier.padding(top = 12.dp)) {
                        Text("🧠 Es una evaluación psicológica", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Un paquete con un solo cobro: en sus citas se llena la entrevista, la observación, los tests, el análisis y el plan, y termina en el informe psicológico.",
                            color = c.textoSuave, fontSize = 12.sp,
                        )
                    }
                }
                if (f.evalPsico) {
                    Spacer(Modifier.height(8.dp))
                    EtqForm("Citas estimadas")
                    CampoNumero(f.citasEstimadas, "4", decimal = false) { f = f.copy(citasEstimadas = it.filter(Char::isDigit).take(2)) }
                    Ayuda("Depende de cada ${LocalTerminologiaPaciente.current.paciente}: en la ficha se pueden agregar citas sin cambiar el precio.")
                    Spacer(Modifier.height(8.dp))
                    EtqForm("Devolución de resultados")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(Triple("incluida", "Incluida", "La última cita del paquete"), Triple("adicional", "Con costo adicional", "Se cobra aparte"))
                            .forEach { (v, t, d) ->
                                val sel = f.devolucion == v
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(Sania.shape.sm.dp))
                                        .background(if (sel) c.navy else c.superficie)
                                        .border(1.dp, if (sel) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                                        .clickable { f = f.copy(devolucion = v) }.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(t, color = if (sel) c.sobreNavy else c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(d, color = if (sel) c.sobreNavy.copy(alpha = 0.8f) else c.textoSuave, fontSize = 10.sp)
                                }
                            }
                    }
                    if (f.devolucion == "adicional") {
                        Spacer(Modifier.height(8.dp))
                        EtqForm("Servicio de devolución")
                        val elegido = servicios.find { it.id == f.devolucionProcId }
                        CajaSelectorForm(
                            elegido?.let { "${nombreDe(it.id)} · ${dineroActivo(it.precio)}" } ?: "Elegir servicio…",
                        ) { eligiendoServicio = -1 }
                    }
                    Ayuda("La plantilla del informe psicológico se personaliza en la web (Configuración).")
                }
            }
        }

        Espacio()
        EtqForm(
            when {
                evalActiva -> "Precio de la evaluación completa (${simboloActivo()}) *"
                conSesiones -> "Precio base por sesión (${simboloActivo()}) *"
                porUnidades -> "Precio de referencia (${simboloActivo()}) *"
                else -> "Precio (${simboloActivo()}) *"
            },
        )
        CampoNumero(f.precio, "100") { f = f.copy(precio = it) }

        // Paquetes promocionales — solo por sesiones (y no en la evaluación psicológica).
        if (conSesiones && !evalActiva) {
            Espacio()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    EtqForm("Paquetes promocionales")
                    Text("Opciones de paquetes con descuento para este servicio.", color = c.textoSuave, fontSize = 11.sp)
                }
                BotonContorno("+ Añadir paquete") { f = f.copy(tarifarios = f.tarifarios + ("" to "")) }
            }
            Spacer(Modifier.height(6.dp))
            if (f.tarifarios.isEmpty()) {
                Caja { Text("No hay paquetes configurados para este servicio.", color = c.textoSuave, fontSize = 12.sp) }
            }
            f.tarifarios.forEachIndexed { i, (n, p) ->
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Nº SESIONES", color = c.textoSuave, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        CampoNumero(n, "Ej. 5", decimal = false) { v -> f = f.copy(tarifarios = f.tarifarios.mapIndexed { j, t -> if (j == i) v.filter(Char::isDigit).take(3) to t.second else t }) }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("PRECIO TOTAL (${simboloActivo()})", color = c.textoSuave, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        CampoNumero(p, "Ej. 400") { v -> f = f.copy(tarifarios = f.tarifarios.mapIndexed { j, t -> if (j == i) t.first to v else t }) }
                    }
                    Spacer(Modifier.width(6.dp))
                    Quitar { f = f.copy(tarifarios = f.tarifarios.filterIndexed { j, _ -> j != i }) }
                }
            }
        }

        // Odontología: precio según cuántas caras tiene la pieza.
        if (esDental && modo != "sesiones") {
            Espacio()
            Caja {
                EtqForm("🦷 Precio según caras (opcional)")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("1" to "1 cara", "2" to "2 caras", "3" to "3 o más").forEach { (k, etq) ->
                        Column(Modifier.weight(1f)) {
                            Text(etq, color = c.textoSuave, fontSize = 11.sp)
                            CampoNumero(f.precioCaras[k].orEmpty(), simboloActivo()) { v -> f = f.copy(precioCaras = f.precioCaras + (k to v)) }
                        }
                    }
                }
                Ayuda("El presupuesto del odontograma cobra cada pieza según las caras marcadas. Una pieza sin caras (entera) se cobra como «3 o más». Si lo dejas vacío, se usa el precio de arriba.")
            }
        }

        // Parámetros de la unidad.
        if (porUnidades) {
            Espacio()
            Caja {
                EtqForm("Nombre de la unidad")
                OutlinedTextField(
                    value = f.unidadLabel, onValueChange = { f = f.copy(unidadLabel = it) },
                    placeholder = { Text("Ej. folículos, unidades, piezas", color = Sania.colors.textoSuave) }, singleLine = true,
                    colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                EtqForm("Precio por unidad (${simboloActivo()}) — sugerido")
                CampoNumero(f.precioUnitario, "Ej. 1.50") { f = f.copy(precioUnitario = it) }
            }
        }

        // Serie AUTOPROGRAMADA: al agendar la 1ª sesión se agendan las demás.
        if (conSesiones) {
            Espacio()
            Caja {
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.clickable { f = f.copy(serieAuto = !f.serieAuto) }) {
                    Checkbox(checked = f.serieAuto, onCheckedChange = { f = f.copy(serieAuto = it) }, colors = CheckboxDefaults.colors(checkedColor = c.navy))
                    Column(Modifier.padding(top = 12.dp)) {
                        Text("📅 Programar todas las sesiones automáticamente", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Al agendar la primera, el resto del plan se agenda solo.", color = c.textoSuave, fontSize = 12.sp)
                    }
                }
                if (f.serieAuto) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                        Text("una sesión cada", color = c.texto, fontSize = 13.sp)
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.width(76.dp)) { CampoNumero(f.serieIntervalo, "15", decimal = false) { f = f.copy(serieIntervalo = it.filter(Char::isDigit).take(3)) } }
                        Spacer(Modifier.width(6.dp))
                        Text("días", color = c.texto, fontSize = 13.sp)
                    }
                    Ayuda("Cada fecha cae en día que la clínica atiende (si toca domingo, se corre y las siguientes también). Reagendar una sesión corre las que faltan con el mismo desplazamiento.")
                }
            }
        }

        // Pasos ENCADENADOS: primero lo que sigue a la atención, después los controles.
        Espacio()
        Caja {
            Text("⛓️ Después de este servicio (opcional)", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (pasosFallo) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        "No se pudieron leer los pasos; hasta leerlos no se puede guardar.",
                        color = c.error, fontSize = 12.sp, modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    BotonContorno("Reintentar") { intentoPasos++ }
                }
            } else if (!pasosCargados) Text("Cargando pasos…", color = c.textoSuave, fontSize = 12.sp)
            f.pasos.forEachIndexed { i, p ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { CajaSelectorForm(nombreDe(p.pasoId) ?: "— Elegir servicio —") { eligiendoServicio = i } }
                    Spacer(Modifier.width(6.dp))
                    Quitar { f = f.copy(pasos = f.pasos.filterIndexed { j, _ -> j != i }) }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("a los", color = c.texto, fontSize = 12.sp)
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.width(70.dp)) { CampoNumero(p.dias, "7", decimal = false) { v -> f = f.copy(pasos = f.pasos.mapIndexed { j, x -> if (j == i) x.copy(dias = v.filter(Char::isDigit).take(3)) else x }) } }
                    Spacer(Modifier.width(4.dp))
                    Text("días hábiles · ${simboloActivo()}", color = c.texto, fontSize = 12.sp)
                    Spacer(Modifier.width(4.dp))
                    // El precio EN CADENA es independiente del precio suelto del servicio.
                    Box(Modifier.weight(1f)) { CampoNumero(p.precio, "0 (incluido)") { v -> f = f.copy(pasos = f.pasos.mapIndexed { j, x -> if (j == i) x.copy(precio = v.take(8)) else x }) } }
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .border(1.dp, c.lav, RoundedCornerShape(Sania.shape.sm.dp))
                    .clickable(enabled = pasosCargados) { f = f.copy(pasos = f.pasos + PasoServicioForm()) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text("+ Agregar paso", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            Ayuda(
                if (f.pasos.isNotEmpty()) "Al completar la atención que dispara (única atención, o la última sesión del plan), las citas de estos pasos se agendan solas en día que la clínica atiende."
                else "Ejemplo: trasplante capilar → lavado de costras a los 7 días hábiles + PRP a los 15.",
            )
        }

        // Protocolo de controles después de la intervención.
        Espacio()
        Caja {
            Text("🔁 Controles después (opcional)", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PROTOCOLOS_CONTROLES.forEach { (nombre, dias) ->
                    val puesto = dias == f.controlesDias.sorted()
                    ChipOpcion(nombre, puesto) { f = f.copy(controlesDias = if (puesto) emptyList() else dias) }
                }
            }
            if (f.controlesDias.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Vuelve a:", color = c.textoSuave, fontSize = 12.sp)
                    f.controlesDias.sorted().forEach { d ->
                        Row(
                            Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.chipBg)
                                .clickable { f = f.copy(controlesDias = f.controlesDias - d) }
                                .padding(horizontal = 9.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(etiquetaControl(d), color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(4.dp))
                            Text("✕", color = c.textoSuave, fontSize = 11.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(64.dp)) { CampoNumero(ctrlNum, "N°", decimal = false) { ctrlNum = it.filter(Char::isDigit).take(2) } }
                Spacer(Modifier.width(6.dp))
                listOf(1 to "días", 7 to "sem.", 30 to "meses", 365 to "años").forEach { (u, t) ->
                    ChipOpcion(t, ctrlUnidad == u) { ctrlUnidad = u }
                    Spacer(Modifier.width(4.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            BotonContorno("+ Agregar control", habilitado = (ctrlNum.toIntOrNull() ?: 0) > 0) {
                val n = ctrlNum.toIntOrNull() ?: 0
                if (n > 0) {
                    val dias = n * ctrlUnidad
                    if (dias !in f.controlesDias) f = f.copy(controlesDias = (f.controlesDias + dias).sorted())
                    ctrlNum = ""
                }
            }
            Ayuda(
                if (f.controlesDias.isNotEmpty()) "Al dar de alta se propone cada fecha sola, contada desde la intervención. Las fechas caen en días que la clínica atiende."
                else "Sin protocolo: los controles se cargan a mano en cada tratamiento.",
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Espacio() = Spacer(Modifier.height(Sania.dim.lg))

@Composable
private fun Ayuda(texto: String) {
    Text(texto, color = Sania.colors.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
}

@Composable
private fun Caja(contenido: @Composable ColumnScope.() -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.fondo)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(10.dp),
        content = contenido,
    )
}

@Composable
private fun CampoNumero(valor: String, placeholder: String, decimal: Boolean = true, onCambio: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = { v -> onCambio(if (decimal) v.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.') else v) },
        placeholder = { Text(placeholder, fontSize = 13.sp, color = Sania.colors.textoSuave) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Quitar(onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.errorBg)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
    ) { Text("✕", color = c.error, fontWeight = FontWeight.Bold) }
}

@Composable
private fun ChipOpcion(texto: String, sel: Boolean, colorSel: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) {
    val c = Sania.colors
    val fondoSel = colorSel ?: c.navy
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
            .background(if (sel) fondoSel else c.superficie)
            .border(1.dp, if (sel) fondoSel else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) { Text(texto, color = if (sel) c.sobreNavy else c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

/** Lista para elegir otro servicio (pasos encadenados y devolución). Con búsqueda. */
@Composable
private fun SelectorServicio(servicios: List<ServicioApp>, onElegir: (ServicioApp) -> Unit, onCerrar: () -> Unit) {
    val c = Sania.colors
    var q by remember { mutableStateOf("") }
    // Con buscador: diálogo que respeta el teclado (Android 16).
    pe.saniape.app.ui.AlertaConTeclado(
        onDismissRequest = onCerrar,
        containerColor = c.superficie,
        title = { Text("Elegir servicio", color = c.texto, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = q, onValueChange = { q = it }, placeholder = { Text("Buscar…", color = Sania.colors.textoSuave) }, singleLine = true,
                    colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    val lista = servicios.filter { q.isBlank() || it.nombre.contains(q.trim(), ignoreCase = true) }
                    if (lista.isEmpty()) Text("No hay otros servicios activos.", color = c.textoSuave, fontSize = 13.sp)
                    lista.forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                                .clickable { onElegir(s) }.padding(vertical = 10.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(s.nombre, color = c.texto, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text(dineroActivo(s.precio), color = c.textoSuave, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar", color = c.textoSuave) } },
    )
}
