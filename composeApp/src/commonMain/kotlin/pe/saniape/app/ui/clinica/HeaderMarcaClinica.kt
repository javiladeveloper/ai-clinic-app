package pe.saniape.app.ui.clinica

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.ui.theme.Sania

/**
 * Barra de marca WHITE-LABEL compartida por todas las pantallas de clínica
 * (Inicio, Agenda, Pacientes, Más). Muestra:
 *  - El LOGO DE LA CLÍNICA activa si lo configuró (como el sidebar web); si no, la marca Sania.
 *  - El nombre de la clínica activa.
 *  - (Opcional) un indicador ▾ si el usuario pertenece a más de una clínica (multi-clínica):
 *    el cambio real se hace en "Más", pero el ▾ avisa que puede cambiar.
 *  - (Opcional) la campana de notificaciones a la derecha + el rol.
 *
 * El logo/nombre salen de [ctx], que YA refleja la clínica ACTIVA: al cambiar de clínica
 * en "Más" se recarga el contexto y este header pasa solo a la nueva marca. No recalcula nada.
 */
@Composable
fun HeaderMarcaClinica(
    ctx: ContextoStaff,
    modifier: Modifier = Modifier,
    // Si se pasa, muestra la campana con el badge de no leídas y la hace tocable.
    noLeidas: Int = 0,
    onCampana: (() -> Unit)? = null,
    // Si se pasa, muestra la lupa 🔍 (buscador global de paciente).
    onBuscar: (() -> Unit)? = null,
    // Si se pasa, muestra 💰 (acceso rápido a la caja de hoy).
    onCaja: (() -> Unit)? = null,
    // Si se pasa y hay más de una clínica, el bloque de marca es tocable (→ ir a cambiar).
    onCambiarClinica: (() -> Unit)? = null,
    // Muestra el rol a la derecha (ej. "Recepción", "Fisioterapeuta").
    mostrarRol: Boolean = true,
) {
    val c = Sania.colors
    val multiClinica = ctx.clinicas.size > 1

    Row(
        modifier
            .fillMaxWidth()
            .background(c.navyDark)
            .padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Bloque marca (logo + nombre). Tocable solo si es multi-clínica y hay handler.
        val marcaMod = if (multiClinica && onCambiarClinica != null) {
            Modifier.clip(RoundedCornerShape(8.dp)).clickable { onCambiarClinica() }.padding(4.dp)
        } else Modifier
        Row(verticalAlignment = Alignment.CenterVertically, modifier = marcaMod) {
            LogoMarcaChica(ctx, tam = LOGO_MARCA_TAM)
            Spacer(Modifier.width(10.dp))
            Text(ctx.clinicaNombre, color = c.sobreNavy, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (multiClinica && onCambiarClinica != null) {
                Spacer(Modifier.width(4.dp))
                Text("▾", color = c.sobreNavy.copy(alpha = 0.7f), fontSize = 14.sp)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Cola offline: si hay escrituras sin sincronizar (se registraron sin
            // señal), avisar que están guardadas y pendientes de subir.
            val pendientes by pe.saniape.app.data.offline.EstadoSync.pendientes.collectAsState()
            if (pendientes > 0L) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFFEF3C7))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text("🕐 $pendientes", color = Color(0xFF92400E), fontSize = 12.sp,
                        fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
            }
            if (onBuscar != null) {
                Text("🔍", fontSize = 18.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onBuscar() }.padding(6.dp))
                Spacer(Modifier.width(2.dp))
            }
            if (onCaja != null) {
                Text("💰", fontSize = 18.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onCaja() }.padding(6.dp))
                Spacer(Modifier.width(2.dp))
            }
            if (onCampana != null) {
                Box(
                    Modifier.clip(RoundedCornerShape(50)).clickable { onCampana() }.padding(6.dp),
                ) {
                    Text("🔔", fontSize = 18.sp)
                    if (noLeidas > 0) {
                        Box(
                            Modifier.align(Alignment.TopEnd)
                                .clip(RoundedCornerShape(50)).background(c.error)
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            Text(
                                if (noLeidas > 9) "9+" else noLeidas.toString(),
                                color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
            }
            if (mostrarRol) ctx.rol?.let {
                Text(it, color = c.sobreNavy.copy(alpha = 0.8f), fontSize = 12.sp)
            }
        }
    }
}

/** Tamaño del chip del logo de marca en las barras superiores (uniforme en toda la app). */
const val LOGO_MARCA_TAM = 44

/**
 * Logo de marca (el ícono) para las barras de las pantallas de clínica: el logo de la
 * clínica si lo configuró, si no la marca Sania. Se pinta a la izquierda del título para
 * que TODA la app se sienta de la clínica. El logo va sobre un chip blanco redondeado para
 * que se vea nítido aunque sea transparente/oscuro (como el avatar del sidebar web).
 */
@Composable
fun LogoMarcaChica(ctx: ContextoStaff, tam: Int = LOGO_MARCA_TAM) {
    if (!ctx.logoUrl.isNullOrBlank()) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.size(tam.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .padding(4.dp),   // margen mínimo: el logo casi llena el cuadro blanco
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = ctx.logoUrl,
                contentDescription = ctx.clinicaNombre,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size((tam - 8).dp),
            )
        }
    } else {
        pe.saniape.app.ui.LogoSania(size = (tam - 8).dp)
    }
}
