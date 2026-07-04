package pe.saniape.app.ui.clinica

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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.Preferencias
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.ModoRepo
import pe.saniape.app.data.staff.StaffContextoRepo
import pe.saniape.app.ui.theme.Sania

/**
 * Tab Más del staff: datos de la clínica/rol, selector de clínica (si multi-clínica),
 * ir a mi portal de paciente (si aplica) y cerrar sesión.
 */
@Composable
fun PantallaMasClinica(
    contexto: ContextoStaff,
    puedeIrAPortal: Boolean,
    onIrAPortal: () -> Unit,
    onCerrarSesion: () -> Unit,
    onAbrirSesiones: (() -> Unit)? = null,
    onAbrirCaja: (() -> Unit)? = null,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var ctx by remember { mutableStateOf(contexto) }
    var cambiando by remember { mutableStateOf(false) }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().background(c.navyDark)
                .padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg)) {
                Text("Más", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
            }

            Column(Modifier.fillMaxWidth().padding(Sania.dim.xl)) {
                // Datos de la clínica + rol + plan
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
                        .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
                        .padding(Sania.dim.lg),
                ) {
                    Text(ctx.clinicaNombre, color = c.texto, fontSize = Sania.txt.seccion, fontWeight = FontWeight.Bold)
                    Text(
                        listOfNotNull(ctx.nombre, ctx.rol).joinToString(" · ").ifBlank { "Staff" },
                        color = c.textoSuave, fontSize = Sania.txt.pequeno,
                    )
                    Spacer(Modifier.height(6.dp))
                    val plan = ctx.planEstado
                    val planTxt = "Plan ${ctx.plan ?: plan.efectivo}" +
                        (if (plan.vencido) " · vencido" else plan.diasRestantes?.let { " · $it días" } ?: "")
                    Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.chipBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text(planTxt, color = c.navy, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(Sania.dim.lg))

                // Módulos clínicos sin tab propio (Sesiones, Caja…)
                if (onAbrirSesiones != null || onAbrirCaja != null) {
                    Text("MÓDULOS", color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(Sania.dim.sm))
                    if (onAbrirSesiones != null) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                                .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                                .clickable { onAbrirSesiones() }.padding(Sania.dim.lg),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("🏃  Sesiones", color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.SemiBold)
                            Text("→", color = c.textoSuave, fontSize = Sania.txt.cuerpo)
                        }
                        Spacer(Modifier.height(Sania.dim.sm))
                    }
                    // 💰 Caja de hoy (con permiso de pagos): cuánto entró y por qué método.
                    if (onAbrirCaja != null) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                                .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                                .clickable { onAbrirCaja() }.padding(Sania.dim.lg),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("💰  Caja de hoy", color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.SemiBold)
                            Text("→", color = c.textoSuave, fontSize = Sania.txt.cuerpo)
                        }
                    }
                    Spacer(Modifier.height(Sania.dim.lg))
                }

                // Selector de clínica (si tiene más de una)
                if (ctx.clinicas.size > 1) {
                    Text("CAMBIAR DE CLÍNICA", color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(Sania.dim.sm))
                    ctx.clinicas.forEach { cl ->
                        val activa = cl.id == ctx.clinicaId
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(Sania.shape.sm.dp))
                                .background(if (activa) c.chipBg else c.superficie)
                                .border(1.dp, if (activa) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                                .clickable(enabled = !activa && !cambiando) {
                                    cambiando = true
                                    scope.launch {
                                        val ok = ModoRepo.cambiar("clinica", cl.id)
                                        if (ok) {
                                            // Recargar contexto con la nueva clínica activa.
                                            when (val r = StaffContextoRepo.cargar()) {
                                                is StaffContextoRepo.Resultado.Ok -> ctx = r.contexto
                                                else -> {}
                                            }
                                        }
                                        cambiando = false
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("🏥 ${cl.nombre}", color = if (activa) c.navy else c.texto,
                                fontSize = Sania.txt.cuerpo, fontWeight = if (activa) FontWeight.Bold else FontWeight.Normal)
                            if (activa) Text("✓", color = c.navy, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (cambiando) {
                        Spacer(Modifier.height(Sania.dim.sm))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp,
                                modifier = Modifier.height(16.dp))
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Text("Cambiando…", color = c.textoSuave, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(Sania.dim.lg))
                }

                // Ir a mi portal de paciente
                if (puedeIrAPortal) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .background(c.chipBg).border(1.dp, c.navy, RoundedCornerShape(Sania.shape.sm.dp))
                            .clickable { onIrAPortal() }.padding(Sania.dim.lg),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("🧑 Ir a mi portal de paciente", color = c.navy, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold)
                        Text("→", color = c.navy, fontSize = Sania.txt.cuerpo)
                    }
                    Spacer(Modifier.height(Sania.dim.md))
                }

                // Cerrar sesión
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                        .clickable {
                            StaffContextoRepo.limpiar()
                            Preferencias.setModoActivo(null)
                            onCerrarSesion()
                        }.padding(Sania.dim.lg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Cerrar sesión", color = c.error, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.SemiBold)
                    Text("→", color = c.error, fontSize = Sania.txt.cuerpo)
                }
                Spacer(Modifier.height(Sania.dim.xl))
            }
        }
    }
}