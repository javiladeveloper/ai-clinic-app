package pe.saniape.app.ui.clinica.finanzas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.ui.ManejarAtras
import pe.saniape.app.ui.clinica.ChipSede
import pe.saniape.app.ui.clinica.EstadoVacio
import pe.saniape.app.ui.theme.Sania

/**
 * «🧮 Cerrar mi caja» (T2 2026-10-24): quien cobra cierra el día sin pasar por
 * Finanzas — desde la agenda de hoy o desde «Caja de hoy». Es la pestaña de
 * cierre de siempre ([TabCierre], mismo endpoint /api/staff/caja/cierre), solo
 * para HOY. Sin el permiso `finanzas` el servidor responde con alcance
 * 'mi-caja' (sin el listado de movimientos) y no deja otro día; los ajustes de
 * efectivo siguen siendo de finanzas. Plan Premium, como el cierre de la web.
 */
@Composable
fun PantallaCerrarMiCaja(ctx: ContextoStaff, onCerrar: () -> Unit) {
    val c = Sania.colors
    ManejarAtras(activo = true, onAtras = onCerrar)
    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("← Volver", color = c.sobreNavy, fontSize = Sania.txt.pequeno,
                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable(onClick = onCerrar).padding(vertical = 2.dp))
                    Spacer(Modifier.height(2.dp))
                    Text("🧮 Cerrar la caja de hoy", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
                    // Multisede: la caja es de la sede activa (cada local cuenta su cajón).
                    ChipSede(Modifier.padding(top = 2.dp))
                }
            }
            if (!ctx.can("finanzas")) {
                Box(Modifier.fillMaxSize().padding(Sania.dim.lg)) {
                    EstadoVacio("🔒", "El cierre de caja es una función Premium",
                        "Cuenta el efectivo del día, compáralo con lo que registró Sania y deja constancia de quién cerró. Disponible desde el plan Premium.")
                }
                return@Column
            }
            Box(Modifier.weight(1f)) {
                TabCierre(puedeAjustar = ctx.puede("finanzas"), soloHoy = true, sinDetalle = !ctx.puede("finanzas"))
            }
        }
    }
}
