package pe.saniape.app.ui.clinica.equipo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.EquipoDatos
import pe.saniape.app.data.staff.EquipoRepo
import pe.saniape.app.data.staff.MiembroEquipo
import pe.saniape.app.data.staff.ReglasEquipo
import pe.saniape.app.data.staff.RolEquipo
import pe.saniape.app.ui.AlertaConTeclado
import pe.saniape.app.ui.CargandoLista
import pe.saniape.app.ui.Gestion
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.theme.Sania

/**
 * 👥 Equipo y accesos (Más → Administración), gemelo de la web /equipo: quién
 * entra al sistema de la clínica, con qué rol, qué permisos individuales tiene,
 * en qué sedes trabaja y si está vinculado a su registro de personal.
 *
 * Se abre con permiso "equipo" (lo decide el padre). Las acciones de escritura
 * solo las ve el Admin; igual las valida el servidor (/api/staff/equipo/…).
 * Los roles se ven como referencia: crearlos o editarlos sigue apagado en la
 * plataforma (ROLES_PERSONALIZADOS_HABILITADOS), igual que en la web.
 */
@Composable
fun PantallaEquipo(ctx: ContextoStaff, onSalir: () -> Unit) = pe.saniape.app.tutoriales.PantallaTutorial("Equipo") {
    val c = Sania.colors
    var datos by remember { mutableStateOf<EquipoDatos?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var recarga by remember { mutableIntStateOf(0) }
    var detalleId by remember { mutableStateOf<String?>(null) }
    var invitando by remember { mutableStateOf(false) }
    var enlace by remember { mutableStateOf(false) }

    LaunchedEffect(ctx.clinicaId, recarga) {
        val (d, e) = EquipoRepo.cargar()
        if (d != null) { datos = d; error = null } else if (datos == null) error = e
        else Toaster.error(e ?: "No se pudo actualizar el equipo")
    }

    val d = datos
    if (d != null && invitando) DialogoInvitar(d, onCerrar = { invitando = false }, onHecho = { invitando = false; recarga++ })
    if (d != null && enlace) DialogoEnlace(d, onCerrar = { enlace = false }, onRegistrado = { enlace = false; recarga++ })

    // Ficha del miembro (encima de la lista).
    val miembro = d?.miembros?.firstOrNull { it.id == detalleId }
    if (d != null && miembro != null) {
        FichaMiembro(
            m = miembro, datos = d, multiSede = ctx.multiSede,
            onVolver = { detalleId = null },
            onCambio = { recarga++ },
            onRevocado = { detalleId = null; recarga++ },
        )
    } else Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Cabecera("← Más", "Equipo y accesos", onSalir) {
                pe.saniape.app.ui.tutoriales.BotonAyuda("Equipo")
            }
            when {
                d == null && error != null -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error ?: "", color = c.textoSuave, fontSize = 13.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(Sania.dim.md))
                        Box(
                            Modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.navy)
                                .clickable { error = null; recarga++ }.padding(horizontal = 20.dp, vertical = 10.dp),
                        ) { Text("Reintentar", color = c.sobreNavy, fontWeight = FontWeight.Bold) }
                    }
                }
                d == null -> CargandoLista()
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Sania.dim.lg)) {
                    if (d.esAdmin) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BotonCabecera("+ Añadir miembro", primario = true, modifier = Modifier.weight(1f)) { invitando = true }
                            BotonCabecera("🔗 Por enlace o QR", primario = false, modifier = Modifier.weight(1f)) { enlace = true }
                        }
                        Spacer(Modifier.height(Sania.dim.lg))
                    }
                    Text("MIEMBROS DEL EQUIPO", color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
                    Text("Personas con acceso al sistema de la clínica.", color = c.textoSuave, fontSize = 12.sp)
                    Spacer(Modifier.height(Sania.dim.sm))
                    d.miembros.forEach { m -> FilaMiembro(m, d, ctx.multiSede) { detalleId = m.id } }

                    Spacer(Modifier.height(Sania.dim.xl))
                    Text("ROLES DE LA CLÍNICA", color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
                    Text("Cada rol define qué secciones pueden ver y usar los miembros que lo tienen.", color = c.textoSuave, fontSize = 12.sp)
                    Spacer(Modifier.height(Sania.dim.sm))
                    if (d.roles.isEmpty()) {
                        Text("Aún no hay roles configurados para esta clínica.", color = c.textoSuave, fontSize = 13.sp)
                    }
                    d.roles.forEach { r -> FilaRol(r, d) }
                    if (!d.rolesPersonalizados) {
                        Text(
                            "Los roles son fijos: para cambiar lo que puede hacer una persona, usa sus permisos individuales.",
                            color = c.textoSuave, fontSize = Sania.txt.mini, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = Sania.dim.md),
                        )
                    }
                    Spacer(Modifier.height(Sania.dim.xl))
                }
            }
        }
    }
}

@Composable
private fun Cabecera(volver: String, titulo: String, onVolver: () -> Unit, extra: @Composable () -> Unit = {}) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                volver, color = c.sobreNavy, fontSize = Sania.txt.pequeno,
                modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable { onVolver() }.padding(vertical = 2.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(titulo, color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        extra()
    }
}

@Composable
private fun BotonCabecera(texto: String, primario: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.md.dp)
    Box(
        modifier.clip(forma).background(if (primario) c.navy else c.superficie)
            .border(1.dp, if (primario) c.navy else c.borde, forma)
            .clickable(onClick = onClick).padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = if (primario) c.sobreNavy else c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

/** Badges del miembro: rol, "Acceso total", vinculado, sedes, permisos ajustados (como la web). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BadgesMiembro(m: MiembroEquipo, d: EquipoDatos, multiSede: Boolean) {
    val c = Sania.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (m.esAdmin) {
            Pastilla("Admin", c.purple, c.purpleBg)
            Text("Acceso total", color = c.textoSuave, fontSize = Sania.txt.mini)
        } else Pastilla(m.rolNombre, c.teal, c.tealBg)
        if (m.terapeuta != null) Pastilla("🩺 Vinculado", c.teal, c.tealBg)
        if (multiSede && !m.esAdmin) Pastilla("🏢 " + ReglasEquipo.resumenSedes(m.sedesPermitidas, d.sedes), c.navy, c.chipBg)
        if (!m.esAdmin && m.override != null) Pastilla("✎ permisos ajustados", c.pend, c.pendBg)
    }
}

@Composable
private fun FilaMiembro(m: MiembroEquipo, d: EquipoDatos, multiSede: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.md.dp)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(forma).background(c.superficie)
            .border(1.dp, c.borde, forma).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Inicial(m.nombre)
        Spacer(Modifier.width(Sania.dim.md))
        Column(Modifier.weight(1f)) {
            Text(m.nombre + if (m.esYo) " (tú)" else "", color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            BadgesMiembro(m, d, multiSede)
        }
        Text("→", color = c.textoSuave, fontSize = Sania.txt.cuerpo)
    }
}

@Composable
private fun FilaRol(r: RolEquipo, d: EquipoDatos) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.md.dp)
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(forma).background(c.superficie)
            .border(1.dp, c.borde, forma).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.nombre, color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            if (r.atiendePacientes) { Pastilla("🩺 atiende pacientes", c.teal, c.tealBg); Spacer(Modifier.width(8.dp)) }
            Text(if (r.miembros == 0) "Sin miembros" else if (r.miembros == 1) "1 miembro" else "${r.miembros} miembros",
                color = c.textoSuave, fontSize = Sania.txt.mini)
        }
        r.descripcion?.takeIf { it.isNotBlank() }?.let { Text(it, color = c.textoSuave, fontSize = 12.sp) }
        Spacer(Modifier.height(6.dp))
        PermisosDeRol(d, r.permisos)
    }
}

// ── Ficha del miembro ───────────────────────────────────────────────────────

@Composable
private fun FichaMiembro(
    m: MiembroEquipo,
    datos: EquipoDatos,
    multiSede: Boolean,
    onVolver: () -> Unit,
    onCambio: () -> Unit,
    onRevocado: () -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acc = ReglasEquipo.acciones(m, datos.esAdmin)
    var editando by remember { mutableStateOf(false) }
    var permisos by remember { mutableStateOf(false) }
    var vinculando by remember { mutableStateOf(false) }
    var confirmar by remember { mutableStateOf<String?>(null) }   // "reenviar" | "revocar" | "desvincular"
    var ocupado by remember { mutableStateOf(false) }

    if (editando) DialogoEditarMiembro(m, datos, multiSede, onCerrar = { editando = false }, onHecho = { editando = false; onCambio() })
    if (permisos) DialogoPermisos(m, datos, onCerrar = { permisos = false }, onHecho = { permisos = false; onCambio() })
    if (vinculando) DialogoVincular(m, datos, onCerrar = { vinculando = false }, onHecho = { vinculando = false; onCambio() })

    fun ejecutar(tipo: String) {
        if (ocupado) return
        ocupado = true
        confirmar = null
        scope.launch {
            try {
                when (tipo) {
                    "reenviar" -> {
                        val r = escribir { EquipoRepo.reenviar(m.id) }
                        if (r.registrada) {
                            val email = (r.cuerpo?.get("email") as? JsonPrimitive)?.contentOrNull
                            Toaster.exito(if (email != null) "Credenciales reenviadas a $email" else "Credenciales reenviadas")
                        }
                    }
                    "revocar" -> {
                        val r = escribir(Gestion.ELIMINANDO) { EquipoRepo.revocar(m.id) }
                        if (r.registrada) {
                            // Cuenta personal (paciente del portal, otra clínica): solo pierde esta clínica.
                            val borrada = (r.cuerpo?.get("cuentaBorrada") as? JsonPrimitive)?.contentOrNull != "false"
                            Toaster.exito(if (borrada) "Acceso de ${m.nombre} revocado" else "${m.nombre} ya no tiene acceso a la clínica (su cuenta personal se conserva)")
                            onRevocado()
                        }
                    }
                    "desvincular" -> {
                        val r = escribir { EquipoRepo.desvincular(m.id) }
                        if (r.registrada) { Toaster.exito("${m.nombre} desvinculado"); onCambio() }
                    }
                }
            } finally { ocupado = false }
        }
    }

    confirmar?.let { tipo ->
        val (titulo, texto, boton) = when (tipo) {
            "reenviar" -> Triple(
                "¿Reenviar credenciales a ${m.nombre}?",
                "Se generará una clave temporal nueva (la actual dejará de funcionar) y se le enviará por correo.",
                "Reenviar",
            )
            "desvincular" -> Triple(
                "¿Desvincular a ${m.nombre} de \"${m.terapeuta?.nombre ?: ""}\"?",
                "El registro de personal se conserva, solo se quita el vínculo con su cuenta.",
                "Desvincular",
            )
            else -> Triple(
                "Revocar acceso",
                "Se eliminará el acceso de ${m.nombre}. Su registro de personal (si tiene) se conserva con su historial, solo pierde el login. Esta acción no se puede deshacer.",
                "Sí, revocar acceso",
            )
        }
        AlertaConTeclado(
            onDismissRequest = { confirmar = null },
            title = { Text(titulo, fontWeight = FontWeight.Bold) },
            text = { Text(texto, color = if (tipo == "revocar") c.error else c.texto) },
            confirmButton = {
                TextButton(onClick = { ejecutar(tipo) }) {
                    Text(boton, color = if (tipo == "revocar") c.error else c.navy, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmar = null }) { Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Cabecera("← Equipo", m.nombre, onVolver)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Sania.dim.lg)) {
                val forma = RoundedCornerShape(Sania.shape.md.dp)
                Row(
                    Modifier.fillMaxWidth().clip(forma).background(c.superficie).border(1.dp, c.borde, forma).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Inicial(m.nombre, tam = 48)
                    Spacer(Modifier.width(Sania.dim.md))
                    Column(Modifier.weight(1f)) {
                        Text(m.nombre + if (m.esYo) " (tú)" else "", color = c.texto, fontSize = Sania.txt.seccion, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        BadgesMiembro(m, datos, multiSede)
                        m.terapeuta?.let {
                            Text("Registro de personal: ${it.nombre}", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }

                Spacer(Modifier.height(Sania.dim.lg))
                Text("QUÉ PUEDE HACER", color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Column(Modifier.fillMaxWidth().clip(forma).background(c.superficie).border(1.dp, c.borde, forma).padding(14.dp)) {
                    if (m.esAdmin) Text("🔓 Los administradores tienen acceso total a todas las secciones.", color = c.textoSuave, fontSize = 13.sp)
                    else {
                        PermisosDeRol(datos, m.permisos)
                        if (m.override != null) {
                            Text("Tiene ajustes individuales que pisan los permisos de su rol.", color = c.pend, fontSize = Sania.txt.mini,
                                modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }

                if (acc.alguna) {
                    Spacer(Modifier.height(Sania.dim.lg))
                    Text("ACCIONES", color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    if (acc.editar) FilaAccion(if (multiSede && !m.esAdmin) "✏  Editar nombre, correo, rol y sedes" else "✏  Editar nombre, correo y rol") { editando = true }
                    if (acc.permisos) FilaAccion("🔑  Permisos individuales") { permisos = true }
                    if (acc.vincular) FilaAccion("🩺  Vincular con personal") { vinculando = true }
                    if (acc.desvincular) FilaAccion("🔗  Desvincular de personal", habilitada = !ocupado) { confirmar = "desvincular" }
                    if (acc.reenviar) FilaAccion("✉  Reenviar credenciales", habilitada = !ocupado) { confirmar = "reenviar" }
                    if (acc.revocar) FilaAccion("🗑  Revocar acceso", peligro = true, habilitada = !ocupado) { confirmar = "revocar" }
                } else if (!datos.esAdmin) {
                    Text(
                        "Solo el administrador de la clínica puede cambiar accesos.",
                        color = c.textoSuave, fontSize = Sania.txt.mini, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = Sania.dim.lg),
                    )
                }
                Spacer(Modifier.height(Sania.dim.xl))
            }
        }
    }
}
