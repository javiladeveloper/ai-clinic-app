package pe.saniape.app.ui.clinica.presupuestos

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.Presupuesto
import pe.saniape.app.data.staff.PresupuestosRepo
import pe.saniape.app.data.staff.diaEnZona
import pe.saniape.app.data.staff.diasDesdeEnvio
import pe.saniape.app.data.staff.enlaceWhatsAppSede
import pe.saniape.app.data.staff.fechaLarga
import pe.saniape.app.data.staff.formatearDinero
import pe.saniape.app.data.staff.formatearTotalesPorMoneda
import pe.saniape.app.data.staff.grupoPendiente
import pe.saniape.app.data.staff.hoyEnIso
import pe.saniape.app.data.staff.paisDeSede
import pe.saniape.app.data.staff.puedeArmarPresupuesto
import pe.saniape.app.data.staff.telefonoParaAvisos
import pe.saniape.app.data.staff.textoDiasEnvio
import pe.saniape.app.data.staff.textoRecordatorioWhatsApp
import pe.saniape.app.data.staff.tocaRecordar
import pe.saniape.app.data.staff.totalesDe
import pe.saniape.app.data.staff.totalesPorMoneda
import pe.saniape.app.data.staff.zonaDeSede
import pe.saniape.app.ui.CargandoLista
import pe.saniape.app.ui.ManejarAtras
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania

private data class FilaPendiente(
    val p: Presupuesto,
    val hoy: String,
    val pais: String?,
    val grupo: String,
    val dEnvio: Int?,
    val dRec: Int?,
    val total: Double,
)

private data class GrupoUi(val k: String, val titulo: String, val ayuda: String)

private val GRUPOS = listOf(
    GrupoUi("aceptados", "✓ Aceptados sin tratamientos", "Crea sus tratamientos y agenda la primera sesión."),
    GrupoUi("esperan", "⏳ Esperan respuesta", "Un recordatorio amable a los 2–3 días convierte más."),
    GrupoUi("vencidos", "⌛ Vencidos", "Recuérdale: el mensaje le ofrece actualizarlo."),
)

/**
 * 🧾 Presupuestos pendientes de la clínica (Más → Presupuestos), gemelo de
 * PresupuestosPendientes.tsx: aceptados sin tratamientos (→ la ficha), los que
 * esperan respuesta con los días desde el envío y "📱 Recordar por WhatsApp"
 * (resaltado cuando toca: ≥ 2 días y sin recordar en los últimos 2) y los
 * vencidos. Totales por moneda (nunca se suman monedas).
 * Plan Básico o vencido: el candado con "Mejorar plan".
 */
@Composable
fun PantallaPresupuestosPendientes(
    ctx: ContextoStaff,
    onSalir: () -> Unit,
    /** Abre la ficha del paciente en la pestaña 🧾. */
    onAbrirFicha: (pacienteId: String) -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = recordarAcciones()
    ManejarAtras(activo = true) { onSalir() }
    val planOk = ctx.can("presupuestos")
    var datos by remember { mutableStateOf<List<Presupuesto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var bloqueado by remember { mutableStateOf(false) }
    var recarga by remember { mutableIntStateOf(0) }
    var ocupado by remember { mutableStateOf(false) }
    // "Recordado" lo marca quien puede escribir (sesiones o pagos). Sin eso, el WhatsApp igual se abre.
    val puedeEscribir = puedeArmarPresupuesto { ctx.puede(it) }

    LaunchedEffect(recarga, planOk) {
        if (!planOk) return@LaunchedEffect
        val r = PresupuestosRepo.pendientes()
        when {
            r.items != null -> { datos = r.items; error = null }
            r.codigo == "PLAN_REQUERIDO" -> bloqueado = true
            datos == null -> error = r.error
            else -> Toaster.error(r.error ?: "No se pudieron actualizar")
        }
    }

    val filas = datos.orEmpty().mapNotNull { p ->
        val sede = p.sedeId ?: p.paciente?.sedeId
        val zona = ctx.zonaDeSede(sede)
        val hoy = hoyEnIso(zona)
        val grupo = grupoPendiente(p, hoy) ?: return@mapNotNull null
        FilaPendiente(
            p = p, hoy = hoy, pais = ctx.paisDeSede(sede), grupo = grupo,
            dEnvio = diasDesdeEnvio(diaEnZona(p.enviadoAt, zona), hoy), dRec = diasDesdeEnvio(diaEnZona(p.recordadoAt, zona), hoy),
            total = totalesDe(p).total,
        )
    }

    fun recordar(f: FilaPendiente) {
        val pac = f.p.paciente
        val tel = telefonoParaAvisos(pac?.telefono, pac?.apoderadoTelefono, pac?.apoderadoRecibeAvisos == true)
        val url = enlaceWhatsAppSede(tel, textoRecordatorioWhatsApp(f.p, f.hoy, ctx.clinicaNombre, pac?.nombre), f.pais)
        if (url == null) { Toaster.error("El paciente no tiene un celular válido"); return }
        acciones.abrirUrl(url)
        if (!puedeEscribir || ocupado) return
        ocupado = true
        scope.launch {
            PresupuestosRepo.accion(f.p.id, "recordado")
            ocupado = false
            recarga++
        }
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("← Más", color = c.sobreNavy, fontSize = Sania.txt.pequeno,
                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable(role = Role.Button) { onSalir() }.padding(vertical = 2.dp))
                    Spacer(Modifier.height(2.dp))
                    Text("🧾 Presupuestos pendientes", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            val lista = datos
            when {
                !planOk || bloqueado -> Column(Modifier.fillMaxSize().padding(Sania.dim.lg)) {
                    CandadoPresupuestos { acciones.abrirUrl("${Supabase.SITE_URL}/suscripcion") }
                }
                lista == null && error != null -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error ?: "", color = c.textoSuave, fontSize = 13.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(10.dp))
                        BotonPresu("Reintentar", lleno = true) { error = null; recarga++ }
                    }
                }
                lista == null -> CargandoLista()
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Sania.dim.lg),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (filas.isEmpty()) {
                        TarjetaPresu {
                            Text("No hay presupuestos pendientes. Cuando envíes uno desde la ficha (🧾 Presupuestos), aquí verás quién no ha respondido.",
                                color = c.textoSuave, fontSize = 13.sp)
                        }
                    } else {
                        val abiertos = totalesPorMoneda(filas.filter { it.grupo != "aceptados" }.map { it.total to it.p.moneda })
                        if (abiertos.isNotEmpty()) {
                            Text("${formatearTotalesPorMoneda(abiertos)} en presupuestos sin respuesta.", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        GRUPOS.forEach { g ->
                            val del = filas.filter { it.grupo == g.k }
                            if (del.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(g.titulo, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.width(6.dp))
                                        val (fg, bg) = when (g.k) { "aceptados" -> c.ok to c.okBg; "vencidos" -> c.pend to c.pendBg; else -> c.info to c.infoBg }
                                        PildoraPresu(del.size.toString(), fg, bg)
                                    }
                                    Text(g.ayuda, color = c.textoSuave, fontSize = 11.sp)
                                    del.forEach { f -> androidx.compose.runtime.key(f.p.id) { FilaPresupuestoPendiente(f, onAbrirFicha, ::recordar) } }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(Sania.dim.xxl))
                }
            }
        }
    }
}

@Composable
private fun FilaPresupuestoPendiente(f: FilaPendiente, onAbrirFicha: (String) -> Unit, onRecordar: (FilaPendiente) -> Unit) {
    val c = Sania.colors
    TarjetaPresu {
        Text(
            f.p.paciente?.nombre ?: "Paciente", color = c.navy, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button) { onAbrirFicha(f.p.pacienteId) }.padding(vertical = 2.dp),
        )
        Text(
            listOfNotNull(
                "${f.p.titulo} · ${formatearDinero(f.total, f.p.moneda)}",
                if (f.grupo == "aceptados") (if (f.p.aceptadoCanal == "portal") "aceptó desde su app" else "aceptó en la clínica")
                else f.dEnvio?.let { "enviado ${textoDiasEnvio(it)}" },
                if (f.grupo == "vencidos") "venció el ${fechaLarga(f.p.vigenciaHasta)}" else null,
                if (f.dRec != null && f.grupo != "aceptados") "recordado ${textoDiasEnvio(f.dRec)}" else null,
            ).joinToString(" · "),
            color = c.textoSuave, fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        if (f.grupo == "aceptados") {
            BotonPresu("🩺 Crear tratamientos", ok = true, modifier = Modifier.fillMaxWidth()) { onAbrirFicha(f.p.pacienteId) }
        } else {
            BotonPresu("📱 Recordar por WhatsApp", lleno = tocaRecordar(f.dEnvio, f.dRec), modifier = Modifier.fillMaxWidth()) { onRecordar(f) }
        }
    }
}
