package pe.saniape.app.ui.clinica.finanzas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.Finanzas
import pe.saniape.app.data.staff.FinanzasRepo
import pe.saniape.app.data.staff.MovimientoKardex
import pe.saniape.app.data.staff.PeriodoFinanzas
import pe.saniape.app.data.staff.SedeActiva
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.soles
import pe.saniape.app.tutoriales.tourAncla
import pe.saniape.app.ui.Gestion
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.CargandoLista
import pe.saniape.app.ui.clinica.EstadoVacio
import pe.saniape.app.ui.clinica.pacientes.ChipsMetodoPago
import pe.saniape.app.ui.clinica.pacientes.DialogoFecha
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.TarjetaForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.clinica.pacientes.rememberMetodoPagoInicial
import pe.saniape.app.ui.conIndicador
import pe.saniape.app.ui.theme.Sania

/**
 * Pestaña "Caja y movimientos" (el kardex de /finanzas): periodo o rango, totales,
 * ingresos por método, gráfico, y la lista con filtros. Registrar va por
 * /api/staff/movimiento/registrar; corregir/borrar/cambiar método por los
 * endpoints de siempre — solo Admin, solo manuales (el método, también en los del
 * sistema). Las reglas las decide el servidor; acá solo se muestran los botones.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TabCaja(ctx: ContextoStaff, categorias: CategoriasFin) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    // "Hoy" se recalcula en cada carga: la app puede quedar abierta de un día para otro.
    var hoy by remember { mutableStateOf(LocalDate.parse(hoyClinicaIso())) }
    var periodo by remember { mutableStateOf(PeriodoFinanzas.Mes) }
    var rangoManual by remember { mutableStateOf<Pair<String, String>?>(null) }
    var desdeBorrador by remember { mutableStateOf<String?>(null) }
    var hastaBorrador by remember { mutableStateOf<String?>(null) }
    var eligiendo by remember { mutableStateOf<String?>(null) }   // "desde" | "hasta"
    var movs by remember { mutableStateOf<List<MovimientoKardex>?>(null) }
    var tope by remember { mutableStateOf(false) }
    var fallo by remember { mutableStateOf<String?>(null) }
    var recarga by remember { mutableIntStateOf(0) }
    var verTipo by remember { mutableStateOf<String?>(null) }
    var verMetodo by remember { mutableStateOf<String?>(null) }
    var verCategoria by remember { mutableStateOf<String?>(null) }
    var visibles by remember { mutableIntStateOf(Finanzas.POR_PAGINA) }
    var formulario by remember { mutableStateOf<MovimientoKardex?>(null) }
    var registrando by remember { mutableStateOf(false) }
    var metodoDe by remember { mutableStateOf<MovimientoKardex?>(null) }
    var borrarDe by remember { mutableStateOf<MovimientoKardex?>(null) }

    val sede by SedeActiva.estado.collectAsState()
    // Al volver la app al frente también se recarga (y se recalcula "hoy").
    LaunchedEffect(periodo, rangoManual, sede.filtro, recarga, pe.saniape.app.ui.Reanudacion.contador) {
        fallo = null
        hoy = LocalDate.parse(hoyClinicaIso())
        val r = Finanzas.rango(periodo, hoy, rangoManual)
        runCatching { FinanzasRepo.kardex(r) }
            .onSuccess { (lista, alcanzado) -> movs = lista; tope = alcanzado }
            .onFailure {
                if (it is kotlin.coroutines.cancellation.CancellationException) throw it
                fallo = "No se pudo cargar la caja. Revisa tu conexión."
                // Con datos ya en pantalla no se ve el aviso de error: que no parezca actualizado.
                if (movs != null) Toaster.error("No se pudo actualizar la caja. Revisa tu conexión.")
            }
        visibles = Finanzas.POR_PAGINA
    }

    fun aplicarRango(d: String?, h: String?) {
        desdeBorrador = d; hastaBorrador = h
        rangoManual = if (d != null && h != null) Finanzas.rango(periodo, hoy, d to h).let { it.desde!! to it.hasta!! } else null
    }

    eligiendo?.let { cual ->
        DialogoFecha(
            inicial = if (cual == "desde") desdeBorrador else hastaBorrador,
            onElegir = { f -> if (cual == "desde") aplicarRango(f, hastaBorrador) else aplicarRango(desdeBorrador, f) },
            onCerrar = { eligiendo = null },
        )
    }

    val etiquetaPeriodo = rangoManual?.let { "${fechaCortaFin(it.first)} — ${fechaCortaFin(it.second)}" } ?: periodo.etiqueta
    val lista = movs
    when {
        lista == null && fallo != null -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fallo!!, color = c.textoSuave, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                BotonFin("Reintentar") { recarga++ }
            }
        }
        lista == null -> CargandoLista(filas = 6, conAvatar = true)
        else -> {
            // Totales y lista: hasta hoy (con periodo); el gráfico, todo lo pedido (como la web).
            val vigentes = Finanzas.hastaHoy(lista, hoy, rangoManual)
            val resumen = Finanzas.resumen(vigentes)
            val listaVista = Finanzas.filtrar(vigentes, verTipo, verMetodo, verCategoria)
            val catsEgreso = Finanzas.categoriasEgreso(vigentes)
            val metodos = Finanzas.metodosUsados(vigentes)
            val barras = remember(lista, periodo, rangoManual, hoy) { Finanzas.barras(lista, periodo, hoy, rangoManual) }
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = Sania.dim.lg),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                // Periodo + rango manual
                item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PeriodoFinanzas.entries.forEach { p ->
                            ChipFin(p.etiqueta, activo = rangoManual == null && periodo == p) {
                                periodo = p; desdeBorrador = null; hastaBorrador = null; rangoManual = null
                            }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.weight(1f)) { CajaSelectorForm("Desde: ${desdeBorrador?.let { fechaCortaFin(it) } ?: "—"}") { eligiendo = "desde" } }
                        Box(Modifier.weight(1f)) { CajaSelectorForm("Hasta: ${hastaBorrador?.let { fechaCortaFin(it) } ?: "—"}") { eligiendo = "hasta" } }
                        if (desdeBorrador != null || hastaBorrador != null) {
                            Box(
                                Modifier.size(36.dp).clip(RoundedCornerShape(Sania.shape.sm.dp)).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                                    .clickable { aplicarRango(null, null) },
                                contentAlignment = Alignment.Center,
                            ) { Text("×", color = c.textoSuave, fontSize = 18.sp) }
                        }
                    }
                }
                if (tope) item {
                    AvisoFin("⚠ Se muestran los ${Finanzas.TOPE_MOVIMIENTOS} movimientos más recientes. Elige un periodo o un rango para ver los totales completos.", c.pend, c.pendBg)
                }
                // Totales del periodo (no cambian con los filtros de la lista)
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CifraFin("Ingresos", resumen.ingresos, c.ok, Modifier.weight(1f))
                        CifraFin("Egresos", resumen.egresos, c.error, Modifier.weight(1f))
                        CifraFin("Balance", resumen.balance, if (resumen.balance < 0) c.error else c.navy, Modifier.weight(1f))
                    }
                }
                if (resumen.porMetodo.isNotEmpty()) item {
                    TarjetaFin(Modifier.tourAncla("finanzas.por_metodo")) {
                        RotuloFin("Ingresos por método", modifier = Modifier.padding(bottom = 8.dp))
                        resumen.porMetodo.forEach { (met, monto) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${iconoMetodoFin(met)}  $met", color = c.texto, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                if (resumen.ingresos > 0) Text("${(monto / resumen.ingresos * 100).toInt()}%  ", color = c.textoSuave, fontSize = 11.sp)
                                Text(soles(monto), color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                item {
                    TarjetaFin {
                        val sub = when {
                            rangoManual != null -> "del rango"
                            periodo == PeriodoFinanzas.Anio || periodo == PeriodoFinanzas.Total -> "últimos 12 meses"
                            periodo == PeriodoFinanzas.Mes -> "mes en curso"
                            periodo == PeriodoFinanzas.Semana -> "últimos 7 días"
                            else -> "últimos 3 días"
                        }
                        RotuloFin("📊 Balance ($sub)", modifier = Modifier.padding(bottom = 8.dp))
                        if (barras.none { it.ingresos > 0 || it.egresos > 0 }) {
                            Text("Sin movimientos en este periodo.", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(vertical = 18.dp))
                        } else GraficoBalance(barras)
                    }
                }
                // Encabezado de la lista + registrar
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RotuloFin("📋 Movimientos ($etiquetaPeriodo)", modifier = Modifier.weight(1f))
                        BotonFin("+ Registrar", Modifier.tourAncla("finanzas.registrar")) { formulario = null; registrando = true }
                    }
                }
                // Filtros de la lista
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(null to "Todo", "Ingreso" to "Ingresos", "Egreso" to "Egresos").forEach { (v, etq) ->
                            ChipFin(etq, verTipo == v) {
                                verTipo = v; visibles = Finanzas.POR_PAGINA
                                if (v == "Egreso") verMetodo = null
                                if (v == "Ingreso") verCategoria = null
                            }
                        }
                        // Categorías de egreso: DALU paga comisiones a diario y necesita ver solo esas.
                        if (verTipo != "Ingreso") catsEgreso.forEach { cat ->
                            ChipFin(cat, verCategoria == cat) {
                                val quitar = verCategoria == cat
                                verCategoria = if (quitar) null else cat
                                if (!quitar) { verTipo = "Egreso"; verMetodo = null }
                                visibles = Finanzas.POR_PAGINA
                            }
                        }
                        // Los egresos no llevan método: el filtro se oculta al ver solo egresos.
                        if (verTipo != "Egreso" && metodos.isNotEmpty()) {
                            ChipFin("Todos los métodos", verMetodo == null) { verMetodo = null }
                            metodos.forEach { met ->
                                ChipFin(met, verMetodo == met) { verMetodo = met; verTipo = "Ingreso"; verCategoria = null; visibles = Finanzas.POR_PAGINA }
                            }
                        }
                    }
                }
                if (verTipo != null || verMetodo != null || verCategoria != null) item {
                    val neto = Finanzas.neto(listaVista)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.fondo)
                            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${listaVista.size} ${if (listaVista.size == 1) "movimiento" else "movimientos"}" + (verMetodo?.let { " en $it" } ?: ""),
                            color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.weight(1f),
                        )
                        Text((if (neto < 0) "− " else "+ ") + soles(kotlin.math.abs(neto)), color = if (neto < 0) c.error else c.ok,
                            fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (listaVista.isEmpty()) item {
                    EstadoVacio("💸", "No hay movimientos en este periodo", "Prueba con un periodo más amplio o registra un movimiento.")
                }
                items(listaVista.take(visibles), key = { it.id }) { m ->
                    FilaMovimiento(
                        m = m,
                        esAdmin = ctx.esAdmin,
                        onEditar = { formulario = m; registrando = true },
                        onBorrar = { borrarDe = m },
                        onMetodo = { metodoDe = m },
                    )
                }
                if (listaVista.size > visibles) item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text("Ver más (${listaVista.size - visibles} restantes)", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { visibles += Finanzas.POR_PAGINA }.padding(8.dp))
                    }
                }
                item { Spacer(Modifier.height(Sania.dim.xxl)) }
            }
        }
    }

    if (registrando) {
        DialogoMovimiento(
            editando = formulario,
            sugerencias = { tipo ->
                val base = if (tipo == "Egreso") categorias.egreso else categorias.ingreso
                (base + FinanzasRepo.categoriasUsadas(lista.orEmpty(), tipo)).distinct()
            },
            onCancelar = { registrando = false },
            onGuardar = { tipo, categoria, descripcion, monto, metodo, comprobante, fecha ->
                val editando = formulario
                registrando = false
                scope.launch {
                    val r = conIndicador(Gestion.GUARDANDO) {
                        if (editando != null) FinanzasRepo.editar(editando.id, tipo, categoria, descripcion, monto, fecha ?: editando.fecha, comprobante)
                        else FinanzasRepo.registrar(tipo, categoria, descripcion, monto, metodo, comprobante)
                    }
                    if (!r.ok) { Toaster.error(r.error ?: "No se pudo guardar"); return@launch }
                    if (editando != null) Toaster.exito("Movimiento corregido")
                    else {
                        val cerrada = (r.cuerpo?.get("cajaCerrada") as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"
                        if (cerrada) Toaster.info("Registrado. La caja de hoy ya estaba cerrada: actualiza el cierre.")
                        else Toaster.exito("$tipo de ${soles(monto)} registrado")
                    }
                    recarga++
                }
            },
        )
    }
    metodoDe?.let { m ->
        DialogoMetodo(m, onCancelar = { metodoDe = null }) { nuevo ->
            metodoDe = null
            scope.launch {
                val r = conIndicador(Gestion.GUARDANDO) { FinanzasRepo.cambiarMetodo(m.id, nuevo) }
                if (r.ok) { Toaster.exito("Ahora figura como $nuevo"); recarga++ } else Toaster.error(r.error ?: "No se pudo corregir")
            }
        }
    }
    borrarDe?.let { m ->
        ConfirmarFin(
            titulo = "¿Eliminar este movimiento?",
            detalle = "\"${m.descripcion ?: "Movimiento"}\" de ${soles(m.monto)}. Queda registrado en la auditoría quién lo eliminó y qué decía.",
            textoAccion = "Eliminar",
            onCancelar = { borrarDe = null },
        ) {
            borrarDe = null
            scope.launch {
                val r = conIndicador(Gestion.ELIMINANDO) { FinanzasRepo.borrar(m.id) }
                if (r.ok) { Toaster.exito("Movimiento eliminado"); recarga++ } else Toaster.error(r.error ?: "No se pudo eliminar")
            }
        }
    }
}

@Composable
private fun FilaMovimiento(m: MovimientoKardex, esAdmin: Boolean, onEditar: () -> Unit, onBorrar: () -> Unit, onMetodo: () -> Unit) {
    val c = Sania.colors
    val esIn = m.esIngreso
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(if (esIn) c.okBg else c.errorBg),
            contentAlignment = Alignment.Center,
        ) { Text(if (esIn) "+" else "−", color = if (esIn) c.ok else c.error, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(m.descripcion?.takeIf { it.isNotBlank() } ?: "Movimiento sin descripción", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            val sub = listOfNotNull(fechaCortaFin(m.fecha), m.categoria.takeIf { it.isNotBlank() }, m.metodoPago, Finanzas.refVisible(m)?.let { "Ref: $it" })
            Text(sub.joinToString(" · "), color = c.textoSuave, fontSize = 11.sp)
            val quien = listOfNotNull(m.pacienteNombre?.let { "🧑 $it" }, m.autor?.let { "registró: $it" })
            if (quien.isNotEmpty()) Text(quien.joinToString(" · "), color = c.textoSuave, fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text((if (esIn) "+ " else "− ") + soles(m.monto), color = if (esIn) c.ok else c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            // Corregir: solo Admin. Manuales → ✏️ 🗑; los del sistema → solo el método (💳) y 🔒 dice dónde se corrigen.
            if (esAdmin) Row {
                if (Finanzas.esManual(m)) {
                    // El método también se corrige solo, sin abrir la edición completa.
                    AccionFila("💳", onMetodo)
                    AccionFila("✏️", onEditar)
                    AccionFila("🗑", onBorrar)
                } else {
                    AccionFila("💳", onMetodo)
                    AccionFila("🔒") { Toaster.info(Finanzas.dondeSeCorrige(m) ?: "") }
                }
            }
        }
    }
}

@Composable
private fun AccionFila(icono: String, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(icono, fontSize = 15.sp)
    }
}

/**
 * Registrar (o, si [editando], corregir) un movimiento MANUAL. Paridad con el
 * modal de /finanzas: tipo, categoría (con sugerencias), descripción, monto,
 * comprobante; al corregir también la fecha. El método se pide al registrar: un
 * gasto en efectivo sale del cajón y así lo descuenta el arqueo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogoMovimiento(
    editando: MovimientoKardex?,
    sugerencias: (String) -> List<String>,
    onCancelar: () -> Unit,
    onGuardar: (tipo: String, categoria: String, descripcion: String, monto: Double, metodo: String?, comprobante: String?, fecha: String?) -> Unit,
) {
    val c = Sania.colors
    var tipo by remember { mutableStateOf(editando?.tipo ?: "Egreso") }
    var categoria by remember { mutableStateOf(editando?.categoria ?: "") }
    var descripcion by remember { mutableStateOf(editando?.descripcion ?: "") }
    var monto by remember { mutableStateOf(editando?.monto?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var comprobante by remember { mutableStateOf(editando?.comprobante ?: "") }
    var fecha by remember { mutableStateOf(editando?.fecha) }
    var eligiendoFecha by remember { mutableStateOf(false) }
    var metodo by rememberMetodoPagoInicial(null)
    val montoNum = Finanzas.parsearMonto(monto)
    val valido = (montoNum ?: 0.0) > 0 && descripcion.isNotBlank() && categoria.isNotBlank()

    if (eligiendoFecha) DialogoFecha(inicial = fecha, onElegir = { fecha = it }, onCerrar = { eligiendoFecha = false })

    DialogoForm(
        titulo = if (editando != null) "Corregir movimiento" else "Registrar movimiento",
        subtitulo = if (editando != null) "Solo movimientos registrados a mano" else "Entra a la caja de hoy",
        textoAccion = if (editando != null) "Guardar cambios" else "✓ Registrar",
        accionHabilitada = valido,
        onCancelar = onCancelar,
        onAccion = {
            onGuardar(tipo, categoria.trim(), descripcion.trim(), montoNum ?: return@DialogoForm,
                if (editando == null) metodo else null, comprobante.trim().ifBlank { null }, fecha)
        },
    ) {
        TarjetaForm(titulo = "Movimiento", icono = "🧾") {
            EtqForm("Tipo")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Egreso" to "− Egreso (gasto)", "Ingreso" to "+ Ingreso").forEach { (v, etq) ->
                    val activo = tipo == v
                    val col = if (v == "Ingreso") c.ok else c.error
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .background(if (activo) col.copy(alpha = 0.15f) else c.superficie)
                            .border(1.5.dp, if (activo) col else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                            .clickable { if (tipo != v) { tipo = v; categoria = "" } }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(etq, color = if (activo) col else c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }
            if (tipo == "Ingreso") Text("💡 Los pagos de pacientes se registran solos (ficha y sesiones). Usa esto para otros ingresos.",
                color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(10.dp))
            EtqForm("Categoría *")
            OutlinedTextField(
                colors = coloresCampoForm(), value = categoria, onValueChange = { categoria = it.take(80) },
                placeholder = { Text(if (tipo == "Ingreso") "Ej. Venta de productos" else "Ej. Insumos, Alquiler…", color = c.textoSuave) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            val sug = sugerencias(tipo).take(10)
            if (sug.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sug.forEach { s -> ChipFin(s, categoria == s) { categoria = s } }
                }
            }
            Spacer(Modifier.height(10.dp))
            EtqForm("Descripción / concepto *")
            OutlinedTextField(
                colors = coloresCampoForm(), value = descripcion, onValueChange = { descripcion = it.take(300) },
                placeholder = { Text("Ej. Compra de 500 agujas", color = c.textoSuave) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    EtqForm("Monto (S/) *")
                    OutlinedTextField(
                        colors = coloresCampoForm(), value = monto,
                        onValueChange = { monto = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(Modifier.weight(1f)) {
                    EtqForm("N° comprobante")
                    OutlinedTextField(
                        colors = coloresCampoForm(), value = comprobante, onValueChange = { comprobante = it.take(60) },
                        placeholder = { Text("F001-492", color = c.textoSuave) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (editando == null) {
                Spacer(Modifier.height(10.dp))
                EtqForm("¿Con qué se pagó?")
                ChipsMetodoPago(metodo) { metodo = it }
            } else {
                Spacer(Modifier.height(10.dp))
                EtqForm("Fecha")
                CajaSelectorForm(fecha?.let { fechaCortaFin(it) + " (" + it + ")" } ?: "—") { eligiendoFecha = true }
                Text("No se puede tocar un día con la caja ya cerrada: en ese caso, registra un movimiento de ajuste de hoy.",
                    color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** "¿Con qué se pagó?": solo cambia el método, para que el arqueo cuadre. */
@Composable
private fun DialogoMetodo(m: MovimientoKardex, onCancelar: () -> Unit, onGuardar: (String) -> Unit) {
    val c = Sania.colors
    var metodo by remember { mutableStateOf(m.metodoPago ?: "") }
    DialogoForm(
        titulo = "¿Con qué se pagó?",
        subtitulo = "${m.descripcion ?: "Movimiento"} · ${soles(m.monto)} · ${fechaCortaFin(m.fecha)}",
        textoAccion = "Corregir",
        accionHabilitada = metodo.isNotBlank() && metodo != m.metodoPago,
        onCancelar = onCancelar,
        onAccion = { onGuardar(metodo) },
    ) {
        TarjetaForm(titulo = "Método de pago", icono = "💳") {
            ChipsMetodoPago(metodo) { metodo = it }
            Text("Solo cambia con qué se pagó. El monto y la fecha quedan igual.", color = c.textoSuave, fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}
