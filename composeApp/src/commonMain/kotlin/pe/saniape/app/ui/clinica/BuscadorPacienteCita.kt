package pe.saniape.app.ui.clinica

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.offline.ahoraMs
import pe.saniape.app.data.offline.nuevaIdemKey
import pe.saniape.app.data.staff.AgendaRepo
import pe.saniape.app.data.staff.BuscarPacienteRepo
import pe.saniape.app.data.staff.CoincidenciaPaciente
import pe.saniape.app.data.staff.EntradaBusqueda
import pe.saniape.app.data.staff.IntentoAlta
import pe.saniape.app.data.staff.LocalTerminologiaPaciente
import pe.saniape.app.data.staff.ReactivarRepo
import pe.saniape.app.data.staff.RefNombre
import pe.saniape.app.data.staff.RespuestaBusqueda
import pe.saniape.app.data.staff.claveDeIntento
import pe.saniape.app.data.staff.clasificarEntrada
import pe.saniape.app.data.staff.coincidePorDocumento
import pe.saniape.app.data.staff.conservarIntento
import pe.saniape.app.data.staff.datosHomonimo
import pe.saniape.app.data.staff.nombreParaAlta
import pe.saniape.app.data.staff.parecidosA
import pe.saniape.app.data.staff.rankearPorNombre
import pe.saniape.app.data.staff.REINTENTO_ENDPOINT_BUSCAR_MS
import pe.saniape.app.data.staff.endpointBuscarDisponible
import pe.saniape.app.data.staff.revisionParecidosVigente
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.theme.Sania

/** Cómo quedó elegido un paciente nuevo para la cita (decide si se ofrece completar datos). */
enum class AltaEnCita { POR_DOCUMENTO, SIN_DNI, REACTIVADO }

/**
 * ¿El servidor tiene GET /api/staff/paciente/buscar? Un 404 sin `codigo`
 * (backend viejo) lo apaga por [REINTENTO_ENDPOINT_BUSCAR_MS]: mientras, se
 * busca a la antigua (lista precargada + consulta directa con la RLS) y los
 * parecidos son locales. Pasado ese tiempo se vuelve a probar (el deploy llega).
 */
private object EndpointBuscar {
    private var apagadoHastaMs = 0L
    val disponible: Boolean get() = endpointBuscarDisponible(apagadoHastaMs, ahoraMs())
    fun apagar() { apagadoHastaMs = ahoraMs() + REINTENTO_ENDPOINT_BUSCAR_MS }
}

/** Una ficha del "¿Es alguno de estos?": activa (se elige) o de baja (se reactiva). */
private data class FilaParecida(val ref: RefNombre, val info: CoincidenciaPaciente?, val baja: Boolean)

/**
 * Selector de paciente de la nueva cita — gemelo de `components/ui/BuscadorPaciente.tsx`.
 * Un solo campo acepta DNI o NOMBRE ([clasificarEntrada]):
 *  - Documento → el flujo de siempre: filtra lo precargado + el servidor y, si
 *    no está, alta por documento ([PanelAltaRapida]: RENIEC o nombre a mano).
 *  - Nombre → coincidencias ORDENADAS ([rankearPorNombre]) con lo que distingue
 *    homónimos (DNI y teléfono enmascarados, edad, última cita) desde
 *    GET /api/staff/paciente/buscar, con pausa y cancelando la anterior. Al
 *    final, "＋ Registrar «Nombre» sin DNI": antes pregunta "¿Es alguno de
 *    estos?" si hay fichas parecidas (también de baja), sin bloquear.
 */
@Composable
internal fun SelectorPacienteBuscable(
    items: List<RefNombre>, elegido: RefNombre?, onElegir: (RefNombre) -> Unit, termino: String = "paciente",
    /** ¿Se ofrece registrar al paciente que no está? (ver `altaRapidaPermitida`) */
    altaRapida: Boolean = false,
    /** País de la sede de la cita: RENIEC solo en Perú. */
    pais: String? = null,
    /** `puede("datos_personales")`: reactivar y ver DNI/teléfono (enmascarados). */
    verDatosPersonales: Boolean = false,
    /** Paciente recién registrado (o reactivado): queda elegido. */
    onRegistrado: (RefNombre, AltaEnCita) -> Unit = { r, _ -> onElegir(r) },
) {
    val c = Sania.colors
    val tpl = LocalTerminologiaPaciente.current
    val scope = rememberCoroutineScope()
    // El elegido VIGENTE (no el del momento en que arrancó una corrutina).
    val elegidoActual by rememberUpdatedState(elegido)
    var abierto by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // Lo que el servidor sabe de cada coincidencia por nombre (máscaras, edad, última cita).
    var info by remember { mutableStateOf<Map<String, CoincidenciaPaciente>>(emptyMap()) }
    // "¿Es alguno de estos?" antes de registrar sin DNI.
    var porNombre by remember { mutableStateOf<Pair<String, List<FilaParecida>>?>(null) }
    var revisando by remember { mutableStateOf(false) }
    var creando by remember { mutableStateOf(false) }
    var reactivandoId by remember { mutableStateOf<String?>(null) }
    var errorAlta by remember { mutableStateOf<String?>(null) }
    // Alta por nombre en curso: su clave se reusa SOLO para reintentar esa alta.
    var intento by remember { mutableStateOf<IntentoAlta?>(null) }

    fun cerrar() { abierto = false; query = ""; porNombre = null; errorAlta = null }

    fun elegir(r: RefNombre) { onElegir(r); cerrar() }

    /** Alta SOLO con el nombre (sin DNI). Anti doble toque: [creando] + clave de idempotencia. */
    fun registrarNombre(nombre: String) {
        if (creando) return
        creando = true
        errorAlta = null
        val it0 = claveDeIntento(intento, nombre) { nuevaIdemKey() }
        intento = it0
        scope.launch {
            val (r, status) = BuscarPacienteRepo.registrarSinDni(nombre, it0.clave)
            creando = false
            if (!r.ok || r.id == null) {
                // Un "no" del servidor (4xx) no se reintenta con esa clave; un corte o 5xx sí.
                if (!conservarIntento(status)) intento = null
                errorAlta = r.error ?: "No se pudo registrar."
                return@launch
            }
            // Alta confirmada: la próxima (aunque sea un homónimo) lleva clave nueva.
            intento = null
            Toaster.exito("${tpl.Paciente} registrado")
            onRegistrado(RefNombre(r.id, nombre, null, sedeId = r.sedeId), AltaEnCita.SIN_DNI)
            cerrar()
        }
    }

    fun reactivarYElegir(f: FilaParecida) {
        if (reactivandoId != null || !verDatosPersonales) return
        reactivandoId = f.ref.id
        scope.launch {
            val r = ReactivarRepo.reactivar(f.ref.id)
            reactivandoId = null
            if (!r.ok || r.id == null) { errorAlta = r.error ?: "No se pudo reactivar"; return@launch }
            Toaster.exito("Ficha reactivada")
            onRegistrado(RefNombre(r.id, f.ref.nombre, null, sedeId = f.ref.sedeId), AltaEnCita.REACTIVADO)
            cerrar()
        }
    }

    Column {
        SelectorBoton(elegido?.nombre ?: "Buscar $termino por nombre o DNI…") {
            // Registrando: no se cierra ni se reinicia bajo el alta en curso.
            if (revisando || creando) return@SelectorBoton
            abierto = !abierto
            if (abierto) { query = ""; porNombre = null; errorAlta = null }
        }
        if (!abierto) return@Column
        Column(Modifier.fillMaxWidth().padding(top = 4.dp)
            .clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .padding(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    // Al cambiar el texto, el aviso de parecidos y el error ya no corresponden.
                    porNombre = null; errorAlta = null
                },
                placeholder = { Text("Nombre o DNI…", color = c.textoSuave) },
                // Revisando parecidos o registrando: el texto no cambia bajo esa decisión.
                enabled = !revisando && !creando,
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = pe.saniape.app.ui.clinica.pacientes.coloresCampoForm(),
            )
            Spacer(Modifier.height(6.dp))

            val entrada = remember(query) { clasificarEntrada(query) }
            val porDocumento = entrada is EntradaBusqueda.Documento
            // Resultados del servidor para la búsqueda actual (null = aún no / sin señal).
            var remotos by remember { mutableStateOf<Pair<String, List<RefNombre>>?>(null) }
            var buscandoRemoto by remember { mutableStateOf(false) }
            // La última búsqueda que el servidor ya contestó (o que falló sin señal).
            // El alta por documento espera a esto: ofrecer "registrar" antes de saber si
            // está entre los que no se precargaron sería abrir la puerta a un duplicado.
            var consultado by remember { mutableStateOf<String?>(null) }
            // LaunchedEffect(query): al seguir escribiendo se cancela la pausa y, si
            // ya salió, la petición anterior. Nunca llegan resultados viejos.
            LaunchedEffect(query) {
                val q = query.trim()
                if (q.length < 2) { remotos = null; buscandoRemoto = false; return@LaunchedEffect }
                kotlinx.coroutines.delay(300)   // pausa: no una consulta por tecla
                buscandoRemoto = true
                val e = clasificarEntrada(q)
                var lista: List<RefNombre>? = null
                if (e is EntradaBusqueda.Nombre && EndpointBuscar.disponible) {
                    when (val r = BuscarPacienteRepo.buscar(q, limite = 12)) {
                        is RespuestaBusqueda.Ok -> {
                            info = info + r.resultados.associateBy { it.id }
                            lista = r.resultados.map { RefNombre(it.id, it.nombre, null, sedeId = it.sedeId) }
                        }
                        RespuestaBusqueda.NoDisponible -> EndpointBuscar.apagar()
                        is RespuestaBusqueda.Error -> Unit
                    }
                }
                // Documento (el flujo de siempre) o servidor sin el endpoint: consulta
                // directa con la RLS del staff. Sin señal, se queda con lo local.
                if (lista == null) lista = try { AgendaRepo.buscarPacientes(q) }
                    catch (ex: CancellationException) { throw ex } catch (_: Exception) { null }
                buscandoRemoto = false
                if (lista != null) remotos = q to lista
                consultado = q
            }
            val filtrados = remember(query, items, remotos) {
                val q = query.trim()
                val delServidor = remotos?.takeIf { it.first == q }?.second.orEmpty()
                val todos = (items + delServidor).distinctBy { it.id }
                when (val e = clasificarEntrada(q)) {
                    EntradaBusqueda.Vacio -> items.take(30)
                    // Documento: el flujo de siempre (misma regla que la lista de pacientes).
                    is EntradaBusqueda.Documento -> todos
                        .filter { pe.saniape.app.ui.clinica.pacientes.coincideBusqueda(it.nombre, it.dni, null, q) }
                        .take(40)
                    is EntradaBusqueda.Nombre -> {
                        val nombres = rankearPorNombre(todos, e.palabras, 40) { it.nombre }
                        // Un pasaporte a medio escribir ("AB12") parece nombre: también el documento.
                        if (q.none { it in '0'..'9' }) nombres
                        else (todos.filter { coincidePorDocumento(it.dni, q) && it !in nombres } + nombres).take(40)
                    }
                }
            }
            // Registrar SIN DNI: lo escrito es un nombre válido (nombre y apellido).
            // Se ofrece SIEMPRE al final, también con coincidencias ("si no coincide o no sabemos").
            val nombreAlta = if (altaRapida && entrada is EntradaBusqueda.Nombre) nombreParaAlta(query) else null
            val faltaApellido = altaRapida && entrada is EntradaBusqueda.Nombre && nombreAlta == null &&
                filtrados.isEmpty() && query.none { it in '0'..'9' } && entrada.palabras.joinToString("").length >= 3

            /** Antes de crear: ¿hay fichas con un nombre muy parecido? (activas y de baja). */
            fun iniciarAltaPorNombre(nombre: String) {
                if (creando || revisando) return
                revisando = true
                errorAlta = null
                val conocidos = (items + remotos?.second.orEmpty()).distinctBy { it.id }
                // Lo que había al tocar "registrar": si cambia mientras se consulta, se descarta.
                val consultaAlIniciar = query
                val elegidoAlIniciar = elegido?.id
                scope.launch {
                    val locales = parecidosA(conocidos, nombre) { it.nombre }
                        .map { FilaParecida(it.paciente, info[it.paciente.id], baja = false) }
                    // El servidor ve TODA la clínica (la lista precargada no) y las fichas
                    // de baja. Si no responde (o es viejo), solo lo local.
                    val r = if (EndpointBuscar.disponible) BuscarPacienteRepo.parecidos(nombre) else null
                    if (r == RespuestaBusqueda.NoDisponible) EndpointBuscar.apagar()
                    val delServidor = (r as? RespuestaBusqueda.Ok)?.resultados.orEmpty().map {
                        FilaParecida(RefNombre(it.id, it.nombre, null, sedeId = it.sedeId), it, baja = it.deBaja)
                    }
                    if (r is RespuestaBusqueda.Ok) info = info + r.resultados.filter { !it.deBaja }.associateBy { it.id }
                    val vistos = delServidor.map { it.ref.id }.toSet()
                    val lista = (delServidor + locales.filter { it.ref.id !in vistos }).take(6)
                    revisando = false
                    if (!revisionParecidosVigente(consultaAlIniciar, query, abierto, elegidoAlIniciar, elegidoActual?.id)) return@launch
                    if (lista.isNotEmpty()) porNombre = nombre to lista else registrarNombre(nombre)
                }
            }

            Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                val pn = porNombre
                // Documento que no está en la clínica → alta al vuelo (como la web).
                val docNuevo = if (altaRapida && porDocumento && !buscandoRemoto && consultado == query.trim())
                    pe.saniape.app.data.staff.documentoParaAltaRapida(query, filtrados.isNotEmpty()) else null
                when {
                    pn != null -> PanelParecidos(
                        nombre = pn.first, filas = pn.second, verDatos = verDatosPersonales,
                        creando = creando, reactivandoId = reactivandoId, error = errorAlta,
                        onElegir = { f -> if (f.baja) reactivarYElegir(f) else elegir(f.ref) },
                        onRegistrar = { registrarNombre(pn.first) },
                        onVolver = { porNombre = null; errorAlta = null },
                    )
                    docNuevo != null -> PanelAltaRapida(
                        documento = docNuevo, pais = pais, puedeReactivar = verDatosPersonales,
                        onListo = { nuevo, creado ->
                            onRegistrado(nuevo, if (creado) AltaEnCita.POR_DOCUMENTO else AltaEnCita.REACTIVADO)
                            cerrar()
                        },
                    )
                    else -> {
                        if (filtrados.isEmpty()) {
                            Text(
                                when {
                                    query.isBlank() -> "Escribe para buscar."
                                    buscandoRemoto || (altaRapida && porDocumento && query.trim().length >= 2 && consultado != query.trim()) -> "Buscando…"
                                    else -> "Sin coincidencias para “${query.trim()}”."
                                },
                                color = c.textoSuave, fontSize = Sania.txt.pequeno,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                            )
                        }
                        filtrados.forEach { item ->
                            FilaPaciente(
                                item.nombre, datosHomonimo(verDatosPersonales, porDocumento, item.dni, info[item.id]),
                                onClick = if (revisando || creando) null else ({ elegir(item) }),
                            )
                        }
                        if (nombreAlta != null) {
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                                    .clickable(enabled = !creando && !revisando, role = Role.Button) { iniciarAltaPorNombre(nombreAlta) }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                            ) {
                                if (creando || revisando) {
                                    Text(if (revisando) "Revisando fichas parecidas…" else "Registrando…",
                                        color = c.textoSuave, fontSize = 13.sp)
                                } else {
                                    Text("＋ Registrar «$nombreAlta» sin DNI", color = c.ok, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        (if (filtrados.isNotEmpty()) "¿No es ninguno, o no sabes su DNI? " else "") +
                                            "Queda registrado solo con el nombre; el DNI y lo demás se completan después.",
                                        color = c.textoSuave, fontSize = 11.sp,
                                    )
                                }
                            }
                            errorAlta?.let {
                                Text(it, color = c.error, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                        if (faltaApellido) {
                            Text("Para registrarlo sin DNI, escribe su nombre y apellido.",
                                color = c.textoSuave, fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Una coincidencia: el nombre y, debajo, lo que distingue a un homónimo. */
@Composable
private fun FilaPaciente(nombre: String, datos: String, extra: (@Composable () -> Unit)? = null, onClick: (() -> Unit)?) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button) { onClick() } else Modifier)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(nombre, color = c.texto, fontSize = Sania.txt.cuerpo, modifier = Modifier.weight(1f, fill = false))
            extra?.let { Spacer(Modifier.width(6.dp)); it() }
        }
        if (datos.isNotEmpty()) Text(datos, color = c.textoSuave, fontSize = 11.sp)
    }
}

/** "¿Es alguno de estos?" — las parecidas (las de baja, marcadas) y "No, es otra persona". */
@Composable
private fun PanelParecidos(
    nombre: String, filas: List<FilaParecida>, verDatos: Boolean,
    creando: Boolean, reactivandoId: String?, error: String?,
    onElegir: (FilaParecida) -> Unit, onRegistrar: () -> Unit, onVolver: () -> Unit,
) {
    val c = Sania.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
        Text("¿Es alguno de estos?", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(
            (if (filas.size == 1) "Hay una ficha" else "Hay ${filas.size} fichas") +
                " con un nombre parecido a «$nombre». Si es " + (if (filas.size == 1) "ella" else "una de ellas") +
                ", elígela: así no queda registrado dos veces.",
            color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))) {
            filas.forEach { f ->
                val bloqueada = f.baja && !verDatos
                val datos = datosHomonimo(verDatos, false, f.ref.dni, f.info).let { d ->
                    if (!f.baja) d
                    else listOf(d, when {
                        bloqueada -> "Reactívalo desde su ficha."
                        reactivandoId == f.ref.id -> "Reactivando…"
                        else -> "Elígelo para reactivar su ficha (con su historial)."
                    }).filter { it.isNotEmpty() }.joinToString("\n")
                }
                FilaPaciente(
                    f.ref.nombre, datos,
                    extra = if (f.baja) ({
                        Text("de baja", color = c.pend, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(c.pendBg)
                                .padding(horizontal = 6.dp, vertical = 1.dp))
                    }) else null,
                    onClick = if (bloqueada || creando || reactivandoId != null) null else ({ onElegir(f) }),
                )
                Spacer(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
            }
            Text(
                if (creando) "Registrando…" else "＋ No, es otra persona: registrar «$nombre»",
                color = if (creando) c.textoSuave else c.ok, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().clickable(enabled = !creando && reactivandoId == null, role = Role.Button) { onRegistrar() }
                    .padding(horizontal = 10.dp, vertical = 11.dp),
            )
        }
        // Objetivo táctil de 48 dp.
        Box(
            Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                .clickable(role = Role.Button) { onVolver() }.padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text("← Volver a la búsqueda", color = c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        error?.let { Text(it, color = c.error, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }
    }
}
