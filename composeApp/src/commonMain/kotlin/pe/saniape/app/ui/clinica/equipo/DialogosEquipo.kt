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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import pe.saniape.app.data.offline.ResultadoEscritura
import pe.saniape.app.data.staff.EnlaceInvitacion
import pe.saniape.app.data.staff.EquipoDatos
import pe.saniape.app.data.staff.EquipoRepo
import pe.saniape.app.data.staff.MiembroEquipo
import pe.saniape.app.data.staff.ReglasEquipo
import pe.saniape.app.data.staff.RolEquipo
import pe.saniape.app.ui.Gestion
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.conIndicador
import pe.saniape.app.ui.theme.Sania

/** Ejecuta una escritura con el indicador global; si el servidor la rechaza, muestra su mensaje. */
internal suspend fun escribir(
    gestion: Gestion = Gestion.GUARDANDO,
    porDefecto: String = "No se pudo guardar. Intenta de nuevo.",
    bloque: suspend () -> ResultadoEscritura,
): ResultadoEscritura {
    val r = conIndicador(gestion) { bloque() }
    if (!r.registrada) Toaster.error(r.rechazo?.error ?: porDefecto)
    return r
}

/** Selector de rol: los roles de la clínica + "Administrador (Acceso total)", como la web. */
@Composable
private fun SelectorRol(roles: List<RolEquipo>, elegido: String, onElegir: (String) -> Unit) {
    roles.forEach { r ->
        OpcionElegible(
            r.nombre,
            detalle = listOfNotNull(r.descripcion?.takeIf { it.isNotBlank() }, if (r.atiendePacientes) "🩺 atiende pacientes" else null)
                .joinToString(" · ").ifBlank { null },
            elegida = elegido == r.id,
        ) { onElegir(r.id) }
    }
    OpcionElegible("Administrador", detalle = "Acceso total", elegida = elegido == ReglasEquipo.ROL_ADMIN) {
        onElegir(ReglasEquipo.ROL_ADMIN)
    }
}

/** Vista previa ✓/✕ de los permisos de un rol (solo lectura: el rol es la fuente). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PermisosDeRol(datos: EquipoDatos, permisos: Map<String, Boolean>) {
    val c = Sania.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        datos.permisosMeta.forEach { p ->
            val tiene = permisos[p.clave] == true
            Text(
                "${if (tiene) "✓" else "✕"} ${p.label}",
                color = if (tiene) c.ok else c.textoSuave.copy(alpha = 0.6f),
                fontSize = 12.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ── Invitar ─────────────────────────────────────────────────────────────────

/** "+ Añadir miembro": crea su cuenta y le manda su clave temporal por correo. */
@Composable
fun DialogoInvitar(datos: EquipoDatos, onCerrar: () -> Unit, onHecho: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var rolId by remember { mutableStateOf(datos.roles.firstOrNull()?.id ?: ReglasEquipo.ROL_ADMIN) }
    val invitables = remember(datos) { ReglasEquipo.personalInvitable(datos.terapeutas) }
    var personalId by remember { mutableStateOf<String?>(null) }
    var nombre by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun enviar() {
        if (enviando) return
        if (nombre.isBlank()) { error = "Escribe el nombre."; return }
        if (!ReglasEquipo.emailValido(email)) { error = "El correo no parece válido. Revísalo e intenta de nuevo."; return }
        error = null
        enviando = true
        scope.launch {
            val r = try {
                conIndicador(Gestion.GUARDANDO) { EquipoRepo.invitar(email, nombre, rolId, personalId) }
            } finally { enviando = false }
            if (r.registrada) {
                val aviso = (r.cuerpo?.get("warning") as? JsonPrimitive)?.contentOrNull
                if (aviso != null) Toaster.info(aviso)
                else Toaster.exito("Miembro añadido. Le enviamos un correo con sus credenciales de acceso.")
                onHecho()
            } else {
                // En el formulario, para que no se pierda lo escrito.
                error = r.rechazo?.error ?: "No se pudo enviar la invitación."
            }
        }
    }

    DialogoForm(
        titulo = "Añadir miembro",
        subtitulo = "Recibirá un correo con su clave temporal",
        textoAccion = if (enviando) "Enviando…" else "Enviar invitación",
        accionHabilitada = !enviando,
        onCancelar = { if (!enviando) onCerrar() },
        onAccion = { enviar() },
    ) {
        error?.let {
            Text("⚠ $it", color = c.error, fontSize = 13.sp, modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.errorBg).padding(10.dp))
            Spacer(Modifier.height(Sania.dim.md))
        }
        EtqForm("Rol en el sistema")
        SelectorRol(datos.roles, rolId) { rolId = it }

        if (invitables.isNotEmpty()) {
            Spacer(Modifier.height(Sania.dim.md))
            EtqForm("¿Es alguien de tu personal?")
            OpcionElegible("✍ Escribir un nombre nuevo", elegida = personalId == null) { personalId = null; nombre = "" }
            invitables.forEach { t ->
                OpcionElegible(t.nombre, detalle = t.email, elegida = personalId == t.id) {
                    personalId = t.id
                    nombre = t.nombre
                    t.email?.takeIf { it.isNotBlank() }?.let { email = it }
                }
            }
            Text(
                if (personalId != null) "✓ Se le dará acceso a su registro existente: conserva su agenda e historial."
                else "Elige a alguien de tu personal para darle acceso sin reescribir su nombre, o escribe uno nuevo abajo.",
                color = c.textoSuave, fontSize = Sania.txt.mini,
            )
        }

        if (personalId == null) {
            Spacer(Modifier.height(Sania.dim.md))
            EtqForm("Nombre completo")
            OutlinedTextField(
                value = nombre, onValueChange = { nombre = it }, singleLine = true,
                placeholder = { Text("Ej. Dr. Juan Pérez") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(Sania.dim.md))
        EtqForm("Correo electrónico (para entrar)")
        OutlinedTextField(
            value = email, onValueChange = { email = it }, singleLine = true,
            placeholder = { Text("juan@clinica.com") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
        )
        Text("Al ingresar por primera vez deberá crear su propia contraseña.", color = c.textoSuave, fontSize = Sania.txt.mini,
            modifier = Modifier.padding(top = 4.dp))

        Spacer(Modifier.height(Sania.dim.md))
        EtqForm("Permisos del rol")
        val rol = datos.roles.firstOrNull { it.id == rolId }
        if (rolId == ReglasEquipo.ROL_ADMIN || rol == null) {
            Text("🔓 Los administradores tienen acceso total a todas las secciones.", color = c.textoSuave, fontSize = 13.sp)
        } else {
            PermisosDeRol(datos, rol.permisos)
        }
    }
}

// ── Editar miembro ──────────────────────────────────────────────────────────

/** Nombre, correo (vacío = no cambiar), rol y —con multisede— sedes donde trabaja. */
@Composable
fun DialogoEditarMiembro(
    m: MiembroEquipo,
    datos: EquipoDatos,
    multiSede: Boolean,
    onCerrar: () -> Unit,
    onHecho: () -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var nombre by remember { mutableStateOf(m.nombre) }
    var email by remember { mutableStateOf("") }
    var rolId by remember { mutableStateOf(if (m.esAdmin) ReglasEquipo.ROL_ADMIN else m.rolId ?: ReglasEquipo.ROL_ADMIN) }
    var sedes by remember { mutableStateOf(m.sedesPermitidas) }
    var guardando by remember { mutableStateOf(false) }
    val sedesActivas = remember(datos) { datos.sedes.filter { it.estado == "Activa" } }
    val ofreceSedes = ReglasEquipo.ofreceSedes(multiSede, datos.esAdmin, rolId)

    fun guardar() {
        if (guardando) return
        if (nombre.isBlank()) { Toaster.error("El nombre es obligatorio"); return }
        if (email.isNotBlank() && !ReglasEquipo.emailValido(email)) {
            Toaster.error("El correo no parece válido. Revísalo e intenta de nuevo."); return
        }
        // Sedes: se valida antes de guardar nada (no dejar la mitad aplicada), como la web.
        val cambiaSedes = ofreceSedes && ReglasEquipo.cambiaronSedes(m.sedesPermitidas, sedes)
        if (cambiaSedes && sedes != null && sedes!!.isEmpty()) {
            Toaster.error("Elige al menos una sede o marca \"Todas las sedes\""); return
        }
        guardando = true
        scope.launch {
            try {
                val r = escribir { EquipoRepo.editar(m.id, nombre, rolId, email) }
                if (!r.registrada) return@launch
                if (cambiaSedes) {
                    val rs = escribir { EquipoRepo.guardarSedes(m.id, sedes) }
                    if (!rs.registrada) { onHecho(); return@launch }
                }
                Toaster.exito("Datos de ${nombre.trim()} actualizados")
                onHecho()
            } finally { guardando = false }
        }
    }

    DialogoForm(
        titulo = "Editar a ${m.nombre}",
        subtitulo = null,
        textoAccion = if (guardando) "Guardando…" else "Guardar cambios",
        accionHabilitada = !guardando,
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = { guardar() },
    ) {
        EtqForm("Nombre completo *")
        OutlinedTextField(
            value = nombre, onValueChange = { nombre = it }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Sania.dim.md))
        EtqForm("Correo electrónico (para entrar)")
        OutlinedTextField(
            value = email, onValueChange = { email = it }, singleLine = true,
            placeholder = { Text("Dejar vacío para no cambiar") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
        )
        Text("Si escribes un correo nuevo, lo usará para iniciar sesión (su contraseña no cambia).",
            color = c.textoSuave, fontSize = Sania.txt.mini, modifier = Modifier.padding(top = 4.dp))

        Spacer(Modifier.height(Sania.dim.md))
        EtqForm("Rol en el sistema")
        SelectorRol(datos.roles, rolId) { rolId = it }
        if (ReglasEquipo.dejaDeSerAdmin(m, rolId)) {
            Text("⚠ Dejará de ser administrador. Debe quedar al menos un administrador en la clínica.",
                color = c.pend, fontSize = Sania.txt.mini)
        }

        if (multiSede && datos.esAdmin) {
            Spacer(Modifier.height(Sania.dim.md))
            EtqForm("Sedes donde trabaja")
            if (rolId == ReglasEquipo.ROL_ADMIN) {
                Text("El administrador trabaja en todas las sedes y puede ver todas juntas.", color = c.textoSuave, fontSize = 13.sp)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(true to "Todas las sedes", false to "Solo algunas").forEach { (todas, txt) ->
                        val activo = if (todas) sedes == null else sedes != null
                        val forma = RoundedCornerShape(Sania.shape.sm.dp)
                        Box(
                            Modifier.weight(1f).clip(forma).background(if (activo) c.chipBg else c.superficie)
                                .border(1.5.dp, if (activo) c.navy else c.borde, forma)
                                .clickable { sedes = if (todas) null else (sedes ?: emptyList()) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(txt, color = if (activo) c.navy else c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                val elegidas = sedes
                if (elegidas != null) {
                    Spacer(Modifier.height(8.dp))
                    sedesActivas.forEach { s ->
                        val marcada = s.id in elegidas
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                                .clickable { sedes = if (marcada) elegidas - s.id else elegidas + s.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = marcada, onCheckedChange = { sedes = if (marcada) elegidas - s.id else elegidas + s.id },
                                colors = CheckboxDefaults.colors(checkedColor = c.navy))
                            Text(s.nombre, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (s.esPrincipal) Pastilla("Principal", c.navy, c.chipBg)
                        }
                    }
                    Text("Solo verá la agenda y la caja de estas sedes. Con una sola, entra directo a ella sin elegir.",
                        color = c.textoSuave, fontSize = Sania.txt.mini)
                }
            }
        }
    }
}

// ── Permisos individuales ───────────────────────────────────────────────────

/** Ajustes que PISAN los permisos del rol. "Restablecer al rol" los borra. */
@Composable
fun DialogoPermisos(m: MiembroEquipo, datos: EquipoDatos, onCerrar: () -> Unit, onHecho: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var permisos by remember { mutableStateOf(datos.permisosMeta.associate { it.clave to (m.permisos[it.clave] == true) }) }
    var guardando by remember { mutableStateOf(false) }

    fun guardar(valor: Map<String, Boolean>?) {
        if (guardando) return
        guardando = true
        scope.launch {
            try {
                val r = escribir { EquipoRepo.guardarPermisos(m.id, valor) }
                if (r.registrada) {
                    Toaster.exito(if (valor == null) "${m.nombre} usa de nuevo los permisos de su rol" else "Permisos de ${m.nombre} actualizados")
                    onHecho()
                }
            } finally { guardando = false }
        }
    }

    DialogoForm(
        titulo = "Permisos de ${m.nombre}",
        subtitulo = "Ajustes individuales sobre su rol (${m.rolNombre})",
        textoAccion = if (guardando) "Guardando…" else "Guardar permisos",
        accionHabilitada = !guardando,
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = { guardar(permisos) },
    ) {
        Text(
            "Estos ajustes pisan los permisos del rol. Si cambias el rol después, se mantienen hasta que los restablezcas.",
            color = c.textoSuave, fontSize = 13.sp,
        )
        Spacer(Modifier.height(Sania.dim.md))
        datos.permisosMeta.forEach { p ->
            val marcado = permisos[p.clave] == true
            val forma = RoundedCornerShape(Sania.shape.sm.dp)
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(forma).background(c.superficie)
                    .border(1.dp, c.borde, forma).clickable { permisos = permisos + (p.clave to !marcado) }
                    .padding(end = 10.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = marcado, onCheckedChange = { permisos = permisos + (p.clave to it) },
                    colors = CheckboxDefaults.colors(checkedColor = c.navy))
                Column(Modifier.weight(1f)) {
                    Text(p.label, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (p.descripcion.isNotBlank()) Text(p.descripcion, color = c.textoSuave, fontSize = Sania.txt.mini)
                }
            }
        }
        Spacer(Modifier.height(Sania.dim.sm))
        val forma = RoundedCornerShape(Sania.shape.sm.dp)
        Box(
            Modifier.fillMaxWidth().clip(forma).border(1.dp, c.borde, forma)
                .clickable(enabled = !guardando) { guardar(null) }.padding(vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) { Text("↺ Restablecer al rol", color = c.navy, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        Text("Quita los ajustes individuales: vuelve a usar los permisos del rol.", color = c.textoSuave,
            fontSize = Sania.txt.mini, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
    }
}

// ── Vincular con personal ───────────────────────────────────────────────────

/** Vincula su cuenta a su registro de personal: al entrar ve SU agenda. */
@Composable
fun DialogoVincular(m: MiembroEquipo, datos: EquipoDatos, onCerrar: () -> Unit, onHecho: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val libres = remember(datos) { ReglasEquipo.personalVinculable(datos.terapeutas) }
    var elegido by remember { mutableStateOf(if (libres.isEmpty()) "nuevo" else "") }
    var guardando by remember { mutableStateOf(false) }

    DialogoForm(
        titulo = "Vincular a ${m.nombre}",
        subtitulo = "Con su registro de personal",
        textoAccion = if (guardando) "Vinculando…" else "Vincular",
        accionHabilitada = !guardando && elegido.isNotEmpty(),
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = {
            if (!guardando && elegido.isNotEmpty()) {
                guardando = true
                scope.launch {
                    try {
                        val r = escribir(porDefecto = "No se pudo vincular. Puede que ese registro ya esté vinculado a otra cuenta.") {
                            EquipoRepo.vincular(m.id, elegido)
                        }
                        if (r.registrada) {
                            Toaster.exito("${m.nombre} quedó vinculado a su registro de personal")
                            onHecho()
                        }
                    } finally { guardando = false }
                }
            }
        },
    ) {
        Text("Vincúlalo con su registro de personal para que, al iniciar sesión, vea su propia agenda y sesiones.",
            color = c.textoSuave, fontSize = 13.sp)
        Spacer(Modifier.height(Sania.dim.md))
        EtqForm("Registro de personal")
        libres.forEach { t ->
            OpcionElegible(t.nombre, detalle = if (t.estado != "Activo") t.estado else null, elegida = elegido == t.id) { elegido = t.id }
        }
        OpcionElegible("＋ Crear nuevo registro de personal (${m.nombre})", elegida = elegido == "nuevo") { elegido = "nuevo" }
        if (elegido == "nuevo") {
            Text("Se creará un registro nuevo con su nombre y el horario por defecto. Después puedes completar su ficha.",
                color = c.textoSuave, fontSize = Sania.txt.mini)
        }
    }
}

// ── Invitar por enlace / QR ─────────────────────────────────────────────────

/**
 * Gemelo de InvitarPorEnlace (web): eliges el rol, se genera un enlace (72 h, un
 * solo uso) y la persona llena sus datos desde su celular. Se cierra solo cuando
 * alguien se registra.
 */
@Composable
fun DialogoEnlace(datos: EquipoDatos, onCerrar: () -> Unit, onRegistrado: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = pe.saniape.app.ui.recordarAcciones()
    var rolId by remember { mutableStateOf(ReglasEquipo.rolPorDefectoEnlace(datos.roles) ?: "") }
    var enlace by remember { mutableStateOf<EnlaceInvitacion?>(null) }
    var generando by remember { mutableStateOf(false) }

    // Espera el registro: pregunta cada 3 s mientras el diálogo está abierto.
    LaunchedEffect(enlace?.token) {
        val tk = enlace?.token ?: return@LaunchedEffect
        while (true) {
            delay(3_000)
            val estado = EquipoRepo.estadoEnlace(tk) ?: continue
            if (estado.first) {
                Toaster.exito("${estado.second?.let { "$it ya" } ?: "Ya"} se registró en tu equipo ✓")
                onRegistrado()
                return@LaunchedEffect
            }
        }
    }

    val e = enlace
    DialogoForm(
        titulo = "🔗 Invitar por enlace o QR",
        subtitulo = "El enlace vale 3 días y sirve una sola vez",
        textoAccion = when { e != null -> "Listo"; generando -> "Generando…"; else -> "Generar enlace" },
        accionHabilitada = e != null || (!generando && rolId.isNotEmpty()),
        textoCancelar = "Cerrar",
        onCancelar = onCerrar,
        onAccion = {
            if (e != null) onCerrar()
            else if (!generando && rolId.isNotEmpty()) {
                generando = true
                scope.launch {
                    val (nuevo, error) = try {
                        conIndicador(Gestion.GUARDANDO) { EquipoRepo.generarEnlace(rolId) }
                    } finally { generando = false }
                    if (nuevo != null) enlace = nuevo else Toaster.error(error ?: "No se pudo generar el enlace")
                }
            }
        },
    ) {
        if (e == null) {
            Text(
                "Le pasas el enlace (o le muestras el QR) y la persona llena sus datos, su foto y su clave desde su celular. Entra con el rol que elijas.",
                color = c.textoSuave, fontSize = 13.sp,
            )
            Spacer(Modifier.height(Sania.dim.md))
            EtqForm("Entra como")
            if (datos.roles.isEmpty()) {
                Text("La clínica aún no tiene roles configurados.", color = c.textoSuave, fontSize = 13.sp)
            }
            datos.roles.forEach { r ->
                OpcionElegible(r.nombre, detalle = if (r.atiendePacientes) "🩺 Tendrá su propia agenda y aparecerá al agendar citas" else null,
                    elegida = rolId == r.id) { rolId = r.id }
            }
        } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                DibujoQr(e.url, Modifier.widthIn(max = 260.dp).fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)))
                Spacer(Modifier.height(Sania.dim.md))
                Text(e.url, color = c.textoSuave, fontSize = 12.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(Sania.dim.md))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonChico("🔗 Copiar") { acciones.copiarTexto(e.url, "Enlace de invitación"); Toaster.exito("Enlace copiado") }
                    BotonChico("📤 Compartir") {
                        acciones.compartirTexto("Regístrate en nuestro equipo de Sania con este enlace: ${e.url}", "Invitar al equipo")
                    }
                }
                Spacer(Modifier.height(8.dp))
                BotonChico("📱 Mandar por WhatsApp") {
                    val texto = "Regístrate en nuestro equipo de Sania con este enlace: ${e.url}"
                    acciones.abrirUrl("https://wa.me/?text=" + codificarUrl(texto))
                }
                Spacer(Modifier.height(Sania.dim.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp, modifier = Modifier.width(14.dp).height(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Esperando el registro… se cierra solo.", color = c.textoSuave, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun BotonChico(texto: String, onClick: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.pill.dp)
    Box(
        Modifier.clip(forma).background(c.superficie).border(1.dp, c.borde, forma)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
    ) { Text(texto, color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

/** encodeURIComponent mínimo (UTF-8) para el texto de wa.me. */
internal fun codificarUrl(s: String): String = buildString {
    for (b in s.encodeToByteArray()) {
        val ch = (b.toInt() and 0xFF)
        val c = ch.toChar()
        if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in "-_.!~*'()") append(c)
        else {
            append('%')
            append("0123456789ABCDEF"[ch shr 4])
            append("0123456789ABCDEF"[ch and 0xF])
        }
    }
}
