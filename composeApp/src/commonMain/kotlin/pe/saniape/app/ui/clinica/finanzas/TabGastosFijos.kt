package pe.saniape.app.ui.clinica.finanzas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.Finanzas
import pe.saniape.app.data.staff.FinanzasRepo
import pe.saniape.app.data.staff.GastoFijo
import pe.saniape.app.data.staff.soles
import pe.saniape.app.ui.CargandoLista
import pe.saniape.app.ui.Gestion
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.EstadoVacio
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoFecha
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.TarjetaForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.conIndicador
import pe.saniape.app.ui.theme.Sania

/**
 * Pestaña "Gastos fijos" (la tarjeta 🔁 de /finanzas): alquiler, sueldos,
 * membresías… Entran solos a la caja el día que tocan (cron gastos-fijos, al
 * mediodía); acá se ven, se crean, se editan y se borran por
 * /api/staff/gastos-recurrentes (misma lógica que la web). Lo que se paga una
 * sola vez va con "Registrar movimiento" en la pestaña Caja.
 */
@Composable
internal fun TabGastosFijos(
    datos: List<GastoFijo>?,
    categorias: List<String>,
    fallo: String?,
    onRecargar: () -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var editando by remember { mutableStateOf<GastoFijo?>(null) }
    var creando by remember { mutableStateOf(false) }
    var borrarDe by remember { mutableStateOf<GastoFijo?>(null) }
    var verUnicos by remember { mutableStateOf(false) }

    when {
        datos == null && fallo != null -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fallo, color = c.textoSuave, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                BotonFin("Reintentar", onClick = onRecargar)
            }
        }
        datos == null -> CargandoLista(filas = 4, conAvatar = false)
        else -> {
            val visibles = datos.filter { !it.unicoCerrado }
            val unicos = datos.filter { it.unicoCerrado }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = Sania.dim.lg), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Spacer(Modifier.height(4.dp)) }
                item {
                    TarjetaFin {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RotuloFin("🔁 Gastos fijos", modifier = Modifier.weight(1f))
                            BotonFin("+ Nuevo") { creando = true }
                        }
                        Text("Lo que se repite: alquiler, sueldos, membresía, servicios. Entra solo a la caja el día que toca, al mediodía. Lo que pagas una sola vez va en Registrar movimiento (pestaña Caja).",
                            color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                if (visibles.isEmpty() && unicos.isEmpty()) item {
                    EstadoVacio("🔁", "Aún no tienes gastos fijos", "Agrega los fijos de tu clínica para llevarlos al día.")
                }
                items(visibles, key = { it.id }) { g ->
                    TarjetaFin(Modifier.alpha(if (g.activo) 1f else 0.5f), borde = if (g.pagado) c.ok else null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(g.nombre, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(soles(g.monto), color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(6.dp))
                                    Text(g.frecuencia, color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.fondo).padding(horizontal = 7.dp, vertical = 2.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(g.categoria, color = c.textoSuave, fontSize = 11.sp, maxLines = 1)
                                }
                                Spacer(Modifier.height(4.dp))
                                // Todas las tarjetas hablan del MISMO idioma: el estado del gasto en la caja.
                                when {
                                    g.pagado -> Text("✓ En caja", color = c.ok, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    g.proximo != null -> Text("Entra el ${fechaCortaFin(g.proximo)}", color = c.textoSuave, fontSize = 12.sp)
                                    // Sin próxima fecha y sin registrar: algo falló al crearlo.
                                    else -> BotonFin("⚠ Registrar ahora", color = c.ok) {
                                        scope.launch {
                                            val r = conIndicador(Gestion.GUARDANDO) { FinanzasRepo.registrarGasto(g.id) }
                                            if (r.ok) { Toaster.exito("${g.nombre} registrado como egreso"); onRecargar() } else Toaster.error(r.error ?: "No se pudo registrar")
                                        }
                                    }
                                }
                            }
                            IconoAccion("✏️") { editando = g }
                            IconoAccion("🗑") { borrarDe = g }
                        }
                    }
                }
                if (unicos.isNotEmpty()) item {
                    TarjetaFin {
                        Text((if (verUnicos) "▾ " else "▸ ") + "${unicos.size} gasto${if (unicos.size == 1) "" else "s"} de pago único ya registrado${if (unicos.size == 1) "" else "s"}",
                            color = c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().clickable { verUnicos = !verUnicos }.padding(vertical = 4.dp))
                        if (verUnicos) {
                            unicos.forEach { g ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(g.nombre, color = c.texto, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                    Text(soles(g.monto), color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("  ✓ en caja", color = c.ok, fontSize = 11.sp)
                                    IconoAccion("🗑") { borrarDe = g }
                                }
                            }
                            Text("Ya salieron de la caja y no vuelven a tocar. Siguen en los movimientos del mes en que se pagaron.",
                                color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                item { Spacer(Modifier.height(Sania.dim.xxl)) }
            }
        }
    }

    if (creando || editando != null) {
        DialogoGasto(
            gasto = editando,
            categorias = categorias,
            onCancelar = { creando = false; editando = null },
        ) { nombre, categoria, monto, frecuencia, dia, fechaUnica ->
            val g = editando
            creando = false; editando = null
            scope.launch {
                val r = conIndicador(Gestion.GUARDANDO) {
                    if (g != null) FinanzasRepo.editarGasto(g.id, nombre, categoria, monto, frecuencia, dia, fechaUnica)
                    else FinanzasRepo.crearGasto(nombre, categoria, monto, frecuencia, dia)
                }
                if (!r.ok) { Toaster.error(r.error ?: "No se pudo guardar el gasto"); return@launch }
                val enCaja = (r.cuerpo?.get("enCaja") as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"
                val errCaja = (r.cuerpo?.get("errorCaja") as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it != "null" }
                when {
                    errCaja != null -> Toaster.error("Gasto creado, pero NO se registró en la caja: $errCaja")
                    g != null -> Toaster.exito("Gasto actualizado")
                    enCaja -> Toaster.exito("Gasto creado y registrado en la caja (hoy es su día de pago)")
                    else -> Toaster.exito("Gasto creado")
                }
                onRecargar()
            }
        }
    }
    borrarDe?.let { g ->
        ConfirmarFin(
            titulo = "¿Eliminar \"${g.nombre}\"?",
            detalle = "Los pagos ya registrados en la caja se conservan.",
            textoAccion = "Eliminar",
            onCancelar = { borrarDe = null },
        ) {
            borrarDe = null
            scope.launch {
                val r = conIndicador(Gestion.ELIMINANDO) { FinanzasRepo.borrarGasto(g.id) }
                if (r.ok) { Toaster.exito("Gasto fijo eliminado"); onRecargar() } else Toaster.error(r.error ?: "No se pudo eliminar")
            }
        }
    }
}

@Composable
private fun IconoAccion(icono: String, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(icono, fontSize = 15.sp)
    }
}

/**
 * Nuevo / editar gasto fijo. "Único" ya no se ofrece al crear (va con Registrar
 * movimiento); se sigue mostrando en los que ya lo tienen. Si el gasto ya generó
 * egresos, la frecuencia queda congelada: es la clave del comprobante y cambiarla
 * lo volvería a cobrar (el servidor también lo impide).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogoGasto(
    gasto: GastoFijo?,
    categorias: List<String>,
    onCancelar: () -> Unit,
    onGuardar: (nombre: String, categoria: String, monto: Double, frecuencia: String, dia: Int?, fechaUnica: String?) -> Unit,
) {
    val c = Sania.colors
    var nombre by remember { mutableStateOf(gasto?.nombre ?: "") }
    var monto by remember { mutableStateOf(gasto?.monto?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var frecuencia by remember { mutableStateOf(gasto?.frecuencia ?: "Mensual") }
    var categoria by remember { mutableStateOf(gasto?.categoria ?: "Gasto fijo") }
    var dia by remember { mutableStateOf(gasto?.diaCobro?.toString() ?: "") }
    var fechaUnica by remember { mutableStateOf(gasto?.fechaUnica) }
    var eligiendoFecha by remember { mutableStateOf(false) }
    val congelada = gasto?.yaEnCaja == true
    val montoNum = Finanzas.parsearMonto(monto)
    val diaNum = dia.toIntOrNull()
    val diaValido = dia.isBlank() || (diaNum != null && diaNum in 1..31)
    val valido = nombre.isNotBlank() && (montoNum ?: 0.0) > 0 && diaValido

    if (eligiendoFecha) DialogoFecha(inicial = fechaUnica, onElegir = { fechaUnica = it }, onCerrar = { eligiendoFecha = false })

    DialogoForm(
        titulo = if (gasto != null) "Editar gasto fijo" else "Nuevo gasto fijo",
        subtitulo = "Entra solo a la caja el día que toca",
        textoAccion = if (gasto != null) "Guardar cambios" else "Crear gasto",
        accionHabilitada = valido,
        onCancelar = onCancelar,
        onAccion = {
            onGuardar(nombre.trim(), categoria.ifBlank { "Gasto fijo" }, montoNum ?: return@DialogoForm, frecuencia,
                if (frecuencia == "Único") null else diaNum, if (frecuencia == "Único") fechaUnica else null)
        },
    ) {
        TarjetaForm(titulo = "Gasto", icono = "🔁") {
            EtqForm("Nombre del gasto *")
            OutlinedTextField(
                colors = coloresCampoForm(), value = nombre, onValueChange = { nombre = it.take(120) },
                placeholder = { Text("Ej. Alquiler del local, Sueldo…", color = c.textoSuave) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            EtqForm("Monto (S/) *")
            OutlinedTextField(
                colors = coloresCampoForm(), value = monto,
                onValueChange = { monto = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            EtqForm("Frecuencia")
            val opciones = buildList {
                addAll(listOf("Semanal", "Quincenal", "Mensual", "Anual"))
                if (frecuencia == "Único") add("Único")
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                opciones.forEach { f -> ChipFin(if (f == "Único") "Pago único" else f, frecuencia == f, habilitado = !congelada) { frecuencia = f } }
            }
            Text(
                if (congelada) "No se puede cambiar: este gasto ya tiene egresos en la caja y cambiarlo lo volvería a cobrar. Si ahora se paga distinto, crea uno nuevo."
                else "¿Un gasto que pagas una sola vez? Ese va con Registrar movimiento.",
                color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(10.dp))
            if (frecuencia == "Único") {
                EtqForm("Fecha del gasto")
                CajaSelectorForm(fechaUnica?.let { fechaCortaFin(it) } ?: "Elegir fecha") { eligiendoFecha = true }
            } else {
                EtqForm("Día de cobro (opcional)")
                OutlinedTextField(
                    colors = coloresCampoForm(), value = dia,
                    onValueChange = { dia = it.filter { ch -> ch.isDigit() }.take(2) },
                    placeholder = { Text("Ej. 5", color = c.textoSuave) }, singleLine = true, isError = !diaValido,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (frecuencia == "Quincenal") Text("Quincenal: entra el 15 y el último día del mes.", color = c.textoSuave, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp))
                if (frecuencia == "Semanal") Text("Semanal: entra cada lunes.", color = c.textoSuave, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(10.dp))
            EtqForm("Categoría")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Si ya tenía una categoría fuera de la lista (texto libre viejo), se conserva.
                (listOfNotNull(categoria.takeIf { it.isNotBlank() && it !in categorias }) + categorias).forEach { cat ->
                    ChipFin(cat, categoria == cat) { categoria = cat }
                }
            }
        }
    }
}
