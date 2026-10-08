package pe.saniape.app.ui.clinica.ajustes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.staff.AjustesRepo
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.comprimirImagen
import pe.saniape.app.ui.recordarSelectorArchivo
import pe.saniape.app.ui.theme.Sania

/**
 * Abre el selector de imágenes, comprime como la web (logo 1024 px conservando
 * PNG/SVG chicos; portada 1920; fotos 1600) y sube al MISMO bucket/ruta que la
 * web (/api/staff/configuracion/imagen). [onSubida] recibe la respuesta.
 */
@Composable
internal fun recordarSubirImagen(tipo: String, onEstado: (Boolean) -> Unit, onSubida: (JsonObject) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return recordarSelectorArchivo { archivo ->
        if (archivo.mime?.startsWith("image/") != true) { Toaster.error("Elige una imagen (JPG o PNG)"); return@recordarSelectorArchivo }
        scope.launch {
            onEstado(true)
            try {
                // Un logo PNG/SVG liviano va tal cual (conserva la transparencia); el resto se achica.
                val esLogoLiviano = tipo == "logo" && archivo.bytes.size < 1_500_000 &&
                    (archivo.mime == "image/png" || archivo.mime == "image/svg+xml" || archivo.mime == "image/webp")
                val listo = when {
                    esLogoLiviano -> archivo
                    tipo == "logo" -> comprimirImagen(archivo, maxLado = 1024, calidad = 90)
                    tipo == "portada" -> comprimirImagen(archivo, maxLado = 1920, calidad = 75)
                    else -> comprimirImagen(archivo, maxLado = 1600, calidad = 72)
                }
                val r = guardarAjuste(porDefecto = "No se pudo subir la imagen") {
                    AjustesRepo.subirImagen(tipo, listo.nombre, listo.bytes, listo.mime)
                }
                if (r.registrada) r.cuerpo?.let(onSubida)
            } finally { onEstado(false) }
        }
    }
}

/** 🎨 Identidad de marca: nombre, logo, cómo llamas a tu equipo y a tus pacientes, colores. */
@Composable
internal fun SeccionMarca(d: JsonObject, onVolver: () -> Unit, onCambio: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val cl = d.o("clinica") ?: JsonObject(emptyMap())
    val cat = d.o("catalogos") ?: JsonObject(emptyMap())
    var nombre by remember { mutableStateOf(cl.t("nombre")) }
    var logo by remember { mutableStateOf(cl.t("logoUrl")) }
    var termProf by remember { mutableStateOf(cl.t("terminologiaProfesional")) }
    var termPac by remember { mutableStateOf(cl.t("terminologiaPaciente")) }
    var termPacPlural by remember { mutableStateOf(cl.t("terminologiaPacientePlural")) }
    var color by remember { mutableStateOf(cl.t("colorPrincipal")) }
    var colorMenu by remember { mutableStateOf(cl.t("colorSidebar")) }
    var subiendo by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    val colorDefecto = cat.s("colorDefecto") ?: "#2c3e7a"
    val personas = cat.objetos("personas")

    val elegirLogo = recordarSubirImagen("logo", { subiendo = it }) { r ->
        r.s("url")?.let { logo = it; Toaster.info("Logo subido. Toca «Aplicar marca» para guardarlo.") }
    }

    fun guardar() {
        if (nombre.isBlank()) { Toaster.error("Escribe el nombre de la clínica"); return }
        if (!esHex(color)) { Toaster.error("El color de botones no es válido (ej. #2c3e7a)"); return }
        if (colorMenu.isNotBlank() && !esHex(colorMenu)) { Toaster.error("El color del menú no es válido"); return }
        guardando = true
        scope.launch {
            val r = guardarAjuste {
                AjustesRepo.guardarSeccion("marca", buildJsonObject {
                    put("nombre", nombre.trim()); put("logoUrl", logo)
                    put("terminologiaProfesional", termProf); put("terminologiaPaciente", termPac)
                    put("terminologiaPacientePlural", termPacPlural)
                    put("colorPrincipal", color); put("colorSidebar", colorMenu)
                })
            }
            guardando = false
            if (r.registrada) { Toaster.exito("Marca aplicada"); onCambio() }
        }
    }

    SubPantalla("Identidad de marca", onVolver) {
        Tarjeta("🏥 Tu clínica") {
            Campo("Nombre de la clínica", nombre, { nombre = it }, max = 120)
            pe.saniape.app.ui.clinica.pacientes.EtqForm("Logo de la clínica")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(androidx.compose.ui.graphics.Color.White)
                        .border(1.dp, c.borde, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (logo.isNotBlank()) AsyncImage(model = logo, contentDescription = "Logo", contentScale = ContentScale.Fit, modifier = Modifier.size(56.dp))
                    else Text("🏢", fontSize = 24.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Boton(if (subiendo) "⏳ Subiendo…" else if (logo.isBlank()) "Subir logo" else "Cambiar imagen", habilitado = !subiendo, primario = false) { elegirLogo() }
                    if (logo.isNotBlank()) Text("Quitar logo", color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickableSimple { logo = "" }.padding(4.dp))
                }
            }
            Ayuda("PNG con fondo transparente, cuadrado o apaisado. Se achica a 1024 px.")
        }

        Tarjeta("🗣️ Cómo lo llamas") {
            pe.saniape.app.ui.clinica.pacientes.EtqForm("¿Cómo llamas a tu equipo clínico?")
            ChipsEleccion(cat.textos("terminologias").map { it to it }, termProf) { termProf = it }
            Ayuda("Con este nombre aparece la sección en el menú y en toda la app.")
            Spacer(Modifier.height(4.dp))
            pe.saniape.app.ui.clinica.pacientes.EtqForm("¿Cómo llamas a las personas que atiendes?")
            ChipsEleccion(personas.map { it.t("valor") to it.t("etiqueta") }, termPac) { v ->
                termPac = v
                termPacPlural = personas.firstOrNull { it.t("valor") == v }?.s("plural") ?: "${v}s"
            }
            Ayuda("Cambia «paciente» por la palabra de tu rubro en toda la app. Ej: el menú dirá «$termPacPlural».")
        }

        Tarjeta("🎨 Colores de tu marca") {
            pe.saniape.app.ui.clinica.pacientes.EtqForm("Botones y acentos")
            SelectorColor(color, { color = it })
            Text("Restablecer", color = c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickableSimple { color = colorDefecto }.padding(4.dp))
            Ayuda("En tema oscuro se aclara solo.")
            Spacer(Modifier.height(4.dp))
            pe.saniape.app.ui.clinica.pacientes.EtqForm("Menú lateral (web)")
            SelectorColor(colorMenu, { colorMenu = it }, permitirVacio = true)
            Ayuda("Solo en tema claro. Vacío = neutro.")
            Spacer(Modifier.height(4.dp))
            pe.saniape.app.ui.clinica.pacientes.EtqForm("Vista previa")
            VistaPreviaMarca(color, colorMenu)
        }

        Boton(if (guardando) "Aplicando…" else "Aplicar marca", habilitado = !guardando && !subiendo) { guardar() }
    }
}

/** Mini vista previa: el menú con su color y un botón con el de la marca. */
@Composable
private fun VistaPreviaMarca(color: String, colorMenu: String) {
    val c = Sania.colors
    val marca = colorDeHex(color) ?: c.navy
    val menu = colorDeHex(colorMenu) ?: c.fondo
    Row(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, c.borde, RoundedCornerShape(10.dp))) {
        Column(Modifier.weight(0.38f).fillMaxHeight().background(menu).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(marca))
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.textoSuave.copy(alpha = 0.5f)))
            Box(Modifier.fillMaxWidth(0.7f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.textoSuave.copy(alpha = 0.4f)))
        }
        Column(Modifier.weight(0.62f).fillMaxHeight().background(c.superficie).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth(0.65f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.borde))
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.borde))
            Spacer(Modifier.weight(1f))
            Box(Modifier.align(Alignment.End).clip(RoundedCornerShape(6.dp)).background(marca).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text("Botón", color = androidx.compose.ui.graphics.Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** clickable corto (los textos-enlace de las secciones). */
internal fun Modifier.clickableSimple(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
