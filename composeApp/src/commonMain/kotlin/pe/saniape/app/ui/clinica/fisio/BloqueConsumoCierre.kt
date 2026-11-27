package pe.saniape.app.ui.clinica.fisio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.InventarioCierre
import pe.saniape.app.data.staff.InventarioRepo
import pe.saniape.app.data.staff.ProductoConsumoApp
import pe.saniape.app.data.staff.itemsDeConsumo
import pe.saniape.app.data.staff.recetaDeServicioApp
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.theme.Sania

/**
 * 🧰 «¿Se usó algo del inventario?» en el cierre de una sesión (inventario
 * clínico, 26/11/2026; gemelo de ConsumoSesion de la web).
 *
 * Un solo estado para toda la app: solo hay un cierre de sesión abierto a la
 * vez, y así los tres cierres (agenda, ficha, sesiones) lo comparten sin
 * enhebrar parámetros. Se vacía al abrir un cierre y al registrar.
 * [activo] lo pone la app con el contexto de la clínica (el agregado).
 */
object ConsumoCierre {
    var activo by mutableStateOf(false)
    var abierto by mutableStateOf(false)
    /** Productos y recetas; null = aún no se leyó. */
    var datos by mutableStateOf<InventarioCierre?>(null)
    val productos: List<ProductoConsumoApp>? get() = datos?.productos
    /** productoId → cantidad escrita. */
    val cantidades = mutableStateMapOf<String, String>()
    /** Lo anotado vino de la receta del servicio (se dice, para que lo corrijan si fue distinto). */
    var deReceta by mutableStateOf(false)

    fun reiniciar() { cantidades.clear(); abierto = false; deReceta = false }

    /**
     * La receta del servicio viene puesta, UNA vez por cierre que se abre y solo si
     * nadie escribió todavía: quien la borra o la cambia no la ve volver.
     */
    fun proponer(procedimientoId: String?) {
        if (cantidades.isNotEmpty()) return
        val receta = recetaDeServicioApp(datos, procedimientoId)
        if (receta.isEmpty()) return
        cantidades.putAll(receta); abierto = true; deReceta = true
    }
}

private val alcanceConsumo = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Tras completar: descuenta lo anotado (el servidor elige el consultorio del
 * profesional ese día, o el almacén). Nunca frena el cierre; si falla, avisa.
 */
fun consumoTrasCompletar(ok: Boolean, citaId: String?, sesionId: String?) {
    val items = itemsDeConsumo(ConsumoCierre.cantidades.toMap())
    ConsumoCierre.reiniciar()
    if (!ok || !ConsumoCierre.activo || items.isEmpty()) return
    alcanceConsumo.launch {
        val error = InventarioRepo.registrarConsumo(citaId, sesionId, items)
        if (error != null) Toaster.error("La sesión se completó, pero el inventario no se descontó ($error). Regístralo en la web: Productos → Inventario clínico.")
    }
}

/**
 * El bloque plegado. [clave] = la atención que se cierra (al cambiar, se vacía).
 * [procedimientoId] = el servicio de la sesión: si tiene receta, lo habitual viene
 * ya puesto y el bloque abierto (etapa 2, como la web); el servidor no descuenta
 * dos veces si el mismo cierre se guarda de nuevo.
 */
@Composable
fun BloqueConsumoCierre(clave: String?, procedimientoId: String? = null) {
    if (!ConsumoCierre.activo) return
    LaunchedEffect(clave, procedimientoId) {
        ConsumoCierre.reiniciar()
        if (procedimientoId.isNullOrBlank()) return@LaunchedEffect
        // Con lo que ya se tiene se propone al instante; luego se refresca (las recetas cambian en la web).
        ConsumoCierre.proponer(procedimientoId)
        val frescos = InventarioRepo.datosCierre()
        if (frescos != null) { ConsumoCierre.datos = frescos; if (!ConsumoCierre.deReceta) ConsumoCierre.proponer(procedimientoId) }
    }
    LaunchedEffect(ConsumoCierre.abierto) {
        // Sin red no se queda en «Cargando…»: se dice que no hay productos.
        if (ConsumoCierre.abierto && ConsumoCierre.datos == null) ConsumoCierre.datos = InventarioRepo.datosCierre() ?: InventarioCierre(emptyList())
    }
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.md.dp)
    val usados = itemsDeConsumo(ConsumoCierre.cantidades.toMap()).size
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(forma).border(1.5.dp, c.borde, forma).background(c.superficie)) {
        Row(
            Modifier.fillMaxWidth().semantics { role = Role.Button }
                .clickable { ConsumoCierre.abierto = !ConsumoCierre.abierto }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🧰 ¿Se usó algo del inventario?", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (usados > 0) "$usados anotado${if (usados == 1) "" else "s"}" else "(opcional)", color = if (usados > 0) c.ok else c.textoSuave, fontSize = 12.sp)
            Spacer(Modifier.width(6.dp))
            Text(if (ConsumoCierre.abierto) "▴" else "▾", color = c.navy)
        }
        if (ConsumoCierre.abierto) {
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
                if (ConsumoCierre.deReceta) {
                    Text("Lo habitual de este servicio; corrige si fue distinto.", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
                val lista = ConsumoCierre.productos
                when {
                    lista == null -> Text("Cargando productos…", color = c.textoSuave, fontSize = 13.sp)
                    lista.isEmpty() -> Text("No hay productos de consumo cargados (en la web: Productos → Inventario clínico).", color = c.textoSuave, fontSize = 13.sp)
                    else -> lista.forEach { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(p.nombre, color = c.texto, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            OutlinedTextField(
                                colors = pe.saniape.app.ui.clinica.pacientes.coloresCampoForm(),
                                value = ConsumoCierre.cantidades[p.id].orEmpty(),
                                onValueChange = { t -> ConsumoCierre.cantidades[p.id] = t.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(8) },
                                placeholder = { Text("0", color = c.textoSuave) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(96.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(p.unidad, color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.width(48.dp))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Se descuenta del consultorio donde atiendes hoy (o del almacén) al completar.", color = c.textoSuave, fontSize = 11.sp)
            }
        }
    }
}
