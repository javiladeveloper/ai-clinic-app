package pe.saniape.app.ui.clinica.ajustes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.staff.AjustesRepo
import pe.saniape.app.data.staff.urlPaginaClinica
import pe.saniape.app.ui.AlertaConTeclado
import pe.saniape.app.ui.Gestion
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.equipo.DibujoQr
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.reservar.recordarSolicitarUbicacion
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.data.staff.simboloMoneda

private fun JsonObject.clinica() = o("clinica") ?: JsonObject(emptyMap())
private fun JsonObject.plan() = o("plan") ?: JsonObject(emptyMap())
private fun JsonObject.cfg() = o("config") ?: JsonObject(emptyMap())
private fun JsonObject.catalogos() = o("catalogos") ?: JsonObject(emptyMap())

// ─── 📣 Tu página ────────────────────────────────────────────────────────────

/** El enlace público de la clínica: abrir, copiar, WhatsApp, QR y el botón para su web. */
@Composable
internal fun SeccionTuPagina(d: JsonObject, onVolver: () -> Unit) {
    val c = Sania.colors
    val acciones = recordarAcciones()
    val scope = rememberCoroutineScope()
    val cl = d.clinica()
    val slug = cl.t("slug")
    val url = urlPaginaClinica(slug)
    val nombre = cl.t("nombre")
    var textoBoton by remember { mutableStateOf("servicios") }
    val codigo = d.o("botonWeb")?.s(textoBoton)

    SubPantalla("Tu página", onVolver) {
        Tarjeta {
            Ayuda("Esta es tu página en internet: tus servicios, tu equipo, horarios, ubicación y reseñas. Compártela en tus redes, tu estado de WhatsApp o imprime el QR para tu recepción.")
            Text(url.removePrefix("https://"), color = c.navy, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.chipBg).padding(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Boton("Abrir ↗", primario = false, modifier = Modifier.weight(1f)) { acciones.abrirUrl(url); pe.saniape.app.ui.clinica.paginaCompartida(scope) }
                Boton("🔗 Copiar", primario = false, modifier = Modifier.weight(1f)) {
                    acciones.copiarTexto(url, "Mi página"); Toaster.exito("Enlace copiado"); pe.saniape.app.ui.clinica.paginaCompartida(scope)
                }
            }
            Boton("💬 Compartir por WhatsApp") {
                val texto = "¡Hola! 👋 Conoce $nombre: nuestros servicios, horarios y ubicación aquí:\n$url"
                acciones.abrirUrl("https://wa.me/?text=${pe.saniape.app.ui.urlEncode(texto)}")
                pe.saniape.app.ui.clinica.paginaCompartida(scope)
            }
        }
        Tarjeta("QR de tu página") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DibujoQr(url, Modifier.size(200.dp).clip(RoundedCornerShape(8.dp)))
            }
            Ayuda("Muéstralo o haz una captura para imprimirlo en tu recepción o vitrina: el paciente lo escanea y ve tu página.")
        }
        if (codigo != null) {
            Tarjeta("¿Tienes página web? Pon un botón") {
                Ayuda("Copia este código y pégalo en tu web (Wix, WordPress o la que uses, en un bloque de HTML). Tus pacientes llegan con un clic a tu página en Sania.")
                EtqForm("Texto del botón")
                ChipsEleccion(listOf("servicios" to "Ver servicios", "reservar" to "Reservar cita (si tienes reservas en línea)"), textoBoton) { textoBoton = it }
                Text(codigo, color = c.texto, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(c.fondo).border(1.dp, c.borde, RoundedCornerShape(8.dp)).padding(8.dp))
                Boton("📋 Copiar código", primario = false) { acciones.copiarTexto(codigo, "Código del botón"); Toaster.exito("Código copiado: pégalo en tu web") }
            }
        }
    }
}

// ─── 🎨 Diseño de la página ──────────────────────────────────────────────────

private val FONDOS = listOf("auto" to "El de la plantilla", "claro" to "Claro", "crema" to "Crema", "tinte" to "Teñido con tu color", "gris" to "Gris", "oscuro" to "Oscuro")
private val SECCIONES_PAGINA = listOf(
    "mostrarGaleria" to "Fotos de la clínica", "mostrarEquipo" to "Nuestro equipo",
    "mostrarVideo" to "Video de presentación", "mostrarResenas" to "Reseñas de pacientes",
)

/** Guarda diseño + portada juntos (como la web: es UNA columna `pagina_config` + portada + datos destacados). */
private suspend fun guardarPagina(pagina: JsonObject, portada: String, stats: List<Pair<String, String>>) =
    guardarAjuste {
        AjustesRepo.guardarSeccion("pagina", buildJsonObject {
            put("paginaConfig", pagina)
            put("fotoPortadaUrl", portada)
            put("stats", buildJsonArray { stats.forEach { (v, l) -> add(buildJsonObject { put("valor", v); put("label", l) }) } })
        })
    }

private fun statsDe(cl: JsonObject): List<Pair<String, String>> = cl.objetos("stats").map { it.t("valor") to it.t("label") }
    .let { l -> (0 until 3).map { l.getOrNull(it) ?: ("" to "") } }

/** Plantilla, fondo y qué secciones se muestran en la página pública (Premium). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeccionDiseno(d: JsonObject, onVolver: () -> Unit, onCambio: () -> Unit) {
    val c = Sania.colors
    val acciones = recordarAcciones()
    val scope = rememberCoroutineScope()
    val cl = d.clinica()
    val plantillas = d.catalogos().objetos("plantillasPagina")
    var cfg by remember { mutableStateOf(cl.o("paginaConfig") ?: JsonObject(emptyMap())) }
    var guardando by remember { mutableStateOf(false) }
    fun set(k: String, v: Any?) {
        cfg = JsonObject(cfg.toMutableMap().apply {
            put(k, when (v) { is Boolean -> JsonPrimitive(v); is String -> JsonPrimitive(v); else -> JsonNull })
        })
    }
    val plantilla = cfg.t("plantilla").ifBlank { "clasica" }
    val info = plantillas.firstOrNull { it.t("id") == plantilla }

    SubPantalla("Diseño de la página", onVolver, sinGuardar = !guardando && cfg != (cl.o("paginaConfig") ?: JsonObject(emptyMap()))) {
        if (!d.plan().b("pagina")) {
            CandadoPlan("El diseño de tu página es del plan Premium", "Elige una plantilla, el fondo y qué secciones se muestran para que tu página tenga identidad propia. Disponible desde el plan Premium.")
            return@SubPantalla
        }
        Tarjeta("Plantilla") {
            Ayuda("Cada una es una identidad completa: letra, formas, sombras y fondo. Tu color de marca se aplica a todas.")
            plantillas.forEach { p ->
                pe.saniape.app.ui.clinica.equipo.OpcionElegible(p.t("nombre"), detalle = p.t("paraQuien"), elegida = p.t("id") == plantilla) {
                    set("plantilla", p.t("id")); set("fondo", "auto")
                }
            }
            info?.let { Ayuda("${it.t("nombre")}: ${it.t("rasgo")}", c.texto) }
            d.s("urlVerPagina")?.let { u ->
                val base = if (u.startsWith("/")) "${Supabase.SITE_URL}$u" else u
                Text("Ver cómo queda ↗", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickableSimple {
                        acciones.abrirUrl("$base?plantilla=$plantilla&fondo=${cfg.t("fondo").ifBlank { "auto" }}")
                    }.padding(4.dp))
            }
        }
        Tarjeta("Fondo de la página") {
            val permitidos = info?.textos("fondosPermitidos")?.toSet() ?: emptySet()
            ChipsEleccion(FONDOS, cfg.t("fondo").ifBlank { "auto" }, deshabilitados = FONDOS.map { it.first }.filter { it != "auto" && it !in permitidos }.toSet()) { set("fondo", it) }
            Ayuda(if (cfg.t("fondo").ifBlank { "auto" } == "auto") "La plantilla ${info?.t("nombre") ?: ""} usa el fondo ${FONDOS.firstOrNull { it.first == info?.t("fondoPorDefecto") }?.second?.lowercase() ?: "claro"}."
                else "Los fondos apagados no combinan con esta plantilla.")
        }
        Tarjeta("Secciones que se muestran") {
            SECCIONES_PAGINA.forEach { (k, etq) -> FilaInterruptor(etq, null, cfg.b(k) || cfg[k] == null) { set(k, it) } }
            Ayuda("Una sección sin contenido no se muestra aunque esté marcada.")
        }
        Boton(if (guardando) "Guardando…" else "Guardar página", habilitado = !guardando) {
            guardando = true
            scope.launch {
                val r = guardarPagina(cfg, cl.t("fotoPortadaUrl"), statsDe(cl))
                guardando = false
                if (r.registrada) { Toaster.exito("Página guardada"); onCambio() }
            }
        }
    }
}

// ─── 🖼️ Portada ─────────────────────────────────────────────────────────────

private val ESTILOS = listOf(
    Triple("foto-degradado", "Foto con tu color encima", "La foto detrás, tu color fundido desde la izquierda y el texto sobre ella."),
    Triple("foto-limpia", "Foto limpia", "La foto entera sin nada encima. El nombre y el botón van en una franja de color debajo."),
    Triple("solo-color", "Solo color", "Sin foto: tu color de marca de fondo. Sobrio y siempre se ve bien."),
)
private val FOCOS = listOf("izquierda" to "Izquierda", "centro" to "Centro", "derecha" to "Derecha")
private val INTENSIDADES = listOf(
    Triple("suave", "Suave", "se ve casi toda la foto"), Triple("normal", "Normal", "equilibrado"),
    Triple("fuerte", "Fuerte", "tapa el lado izquierdo (para fotos con texto o mucho detalle ahí)"),
)

/** Foto de portada, título del banner y hasta tres datos destacados (Premium). */
@Composable
internal fun SeccionPortada(d: JsonObject, onVolver: () -> Unit, onCambio: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val cl = d.clinica()
    val cat = d.catalogos()
    var cfg by remember { mutableStateOf(cl.o("paginaConfig") ?: JsonObject(emptyMap())) }
    var portada by remember { mutableStateOf(cl.t("fotoPortadaUrl")) }
    val inicial = remember { statsDe(cl) }
    var stats by remember { mutableStateOf(inicial) }
    var subiendo by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    fun set(k: String, v: String?) {
        cfg = JsonObject(cfg.toMutableMap().apply { put(k, v?.let { JsonPrimitive(it) } ?: JsonNull) })
    }
    val elegir = recordarSubirImagen("portada", { subiendo = it }) { r ->
        r.s("url")?.let { portada = it; Toaster.info("Portada subida. Toca «Guardar página» para publicarla.") }
    }
    val topeValor = cat.i("topeValor") ?: 20
    val topeLabel = cat.i("topeLabel") ?: 40
    val tieneFoto = portada.isNotBlank()
    val estilo = cfg.t("heroEstilo").ifBlank { "foto-degradado" }

    // Hay algo distinto de lo guardado (incluida una portada ya subida pero no publicada).
    val sinGuardar = portada != cl.t("fotoPortadaUrl") || stats != statsDe(cl) || cfg != (cl.o("paginaConfig") ?: JsonObject(emptyMap()))
    SubPantalla("Portada", onVolver, sinGuardar = sinGuardar && !guardando) {
        if (!d.plan().b("pagina")) {
            CandadoPlan("La portada de tu página es del plan Premium", "Sube una foto de portada e indica tus datos más destacados para que tu página pública luzca profesional. Disponible desde el plan Premium.")
            return@SubPantalla
        }
        Ayuda("Lo primero que ve el paciente: una foto, tu nombre (o un título propio) y hasta tres datos destacados.")
        Tarjeta("Foto de portada") {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(c.fondo).border(1.dp, c.borde, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center) {
                if (tieneFoto) AsyncImage(model = portada, contentDescription = "Portada", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                else Text("🏞️", fontSize = 30.sp)
            }
            Boton(if (subiendo) "⏳ Subiendo…" else if (tieneFoto) "Cambiar portada" else "Subir foto de portada", habilitado = !subiendo, primario = false) { elegir() }
            if (tieneFoto) Text("Quitar portada", color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickableSimple { portada = "" }.padding(4.dp))
            Ayuda("Horizontal, de al menos 1600 px. Sin portada se usa una foto de instalaciones o un degradado de tu color de marca.")
        }
        Tarjeta("Datos destacados (opcional · hasta 3)") {
            val ejemplos = listOf("Ej. 10 años" to "de experiencia", "Ej. +500" to "pacientes atendidos", "Ej. 4.8 ★" to "calificación")
            stats.forEachIndexed { idx, (v, l) ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Campo(null, v, { nv -> stats = stats.toMutableList().also { it[idx] = nv to l } }, placeholder = ejemplos[idx].first, max = topeValor, modifier = Modifier.weight(0.42f))
                    Campo(null, l, { nl -> stats = stats.toMutableList().also { it[idx] = v to nl } }, placeholder = ejemplos[idx].second, max = topeLabel, modifier = Modifier.weight(0.58f))
                }
            }
            Ayuda("Se muestran como tarjetas sobre la portada.")
        }
        Tarjeta("Estilo del banner") {
            ESTILOS.forEach { (v, titulo, detalle) ->
                val habil = tieneFoto || v == "solo-color"
                pe.saniape.app.ui.clinica.equipo.OpcionElegible(titulo, detalle = if (habil) detalle else "$detalle (sube una foto para elegirlo)", elegida = estilo == v) {
                    if (habil) set("heroEstilo", v)
                }
            }
            if (tieneFoto && estilo != "solo-color") {
                EtqForm("¿Dónde está lo importante de la foto?")
                ChipsEleccion(FOCOS, cfg.t("heroFoco").ifBlank { "centro" }) { set("heroFoco", it) }
                Ayuda("Esa parte nunca se recorta, ni en celular.")
                if (estilo == "foto-degradado") {
                    EtqForm("Color sobre la foto")
                    val inten = cfg.t("heroIntensidad").ifBlank { "normal" }
                    ChipsEleccion(INTENSIDADES.map { it.first to it.second }, inten) { set("heroIntensidad", it) }
                    Ayuda(INTENSIDADES.firstOrNull { it.first == inten }?.third ?: "")
                }
            }
        }
        Tarjeta("Título del banner (opcional)") {
            Campo(null, cfg.s("eslogan") ?: "", { set("eslogan", it.ifBlank { null }) }, placeholder = "Ej. «Muévete sin dolor» — vacío: «${cl.t("nombre")}»", max = 80)
            Ayuda("Con un título propio, el nombre de la clínica pasa a una línea chica encima.")
        }
        Boton(if (guardando) "Guardando…" else "Guardar página", habilitado = !guardando && !subiendo) {
            guardando = true
            scope.launch {
                val r = guardarPagina(cfg, portada, stats)
                guardando = false
                if (r.registrada) { Toaster.exito("Página guardada"); onCambio() }
            }
        }
    }
}

// ─── 🧩 Contenido ───────────────────────────────────────────────────────────

/** Fotos de la clínica (hasta 8; la primera es la grande) y mostrar servicios/precios. Se guarda al tocar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeccionContenido(d: JsonObject, onVolver: () -> Unit, onCambio: () -> Unit) {
    val moneda = pe.saniape.app.ui.monedaUI()
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val cfg = d.cfg()
    val max = d.catalogos().i("maxFotos") ?: 8
    var fotos by remember { mutableStateOf(cfg.textos("fotosClinica")) }
    var servicios by remember { mutableStateOf(cfg.b("mostrarServicios")) }
    var precios by remember { mutableStateOf(cfg.b("mostrarPrecios")) }
    var subiendo by remember { mutableStateOf(false) }
    var quitar by remember { mutableStateOf<String?>(null) }
    var guardandoToggle by remember { mutableStateOf(false) }
    val elegir = recordarSubirImagen("foto-clinica", { subiendo = it }) { r ->
        fotos = r.textos("fotos").ifEmpty { fotos + listOfNotNull(r.s("url")) }
        Toaster.exito("Foto agregada a tu página"); onCambio()
    }
    fun guardarToggle(campo: String, v: Boolean, alFallar: () -> Unit) {
        guardandoToggle = true
        scope.launch {
            val r = try {
                guardarAjuste { AjustesRepo.guardarSeccion("contenido-publico", buildJsonObject { put(campo, v) }) }
            } finally { guardandoToggle = false }
            if (!r.registrada) alFallar() else onCambio()
        }
    }

    quitar?.let { url ->
        AlertaConTeclado(
            onDismissRequest = { quitar = null },
            title = { Text("¿Quitar esta foto?", fontWeight = FontWeight.Bold) },
            text = { Text("Deja de mostrarse en tu página.") },
            confirmButton = {
                TextButton(onClick = {
                    quitar = null
                    scope.launch {
                        val r = guardarAjuste(Gestion.ELIMINANDO) { AjustesRepo.quitarFotoClinica(url) }
                        if (r.registrada) { fotos = r.cuerpo?.textos("fotos") ?: fotos.filter { it != url }; Toaster.exito("Foto eliminada"); onCambio() }
                    }
                }) { Text("Quitar", color = c.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { quitar = null }) { Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }

    SubPantalla("Contenido", onVolver) {
        Ayuda("Lo que aparece debajo de la portada. Esto se guarda solo al cambiarlo.")
        Tarjeta("Fotos de tu clínica") {
            Ayuda("Recepción, consultorios, equipos, sala de espera. Dan confianza. Hasta $max fotos; la primera es la grande.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fotos.forEach { url ->
                    Box(Modifier.width(96.dp).aspectRatio(4f / 3f).clip(RoundedCornerShape(10.dp)).border(1.dp, c.borde, RoundedCornerShape(10.dp))) {
                        AsyncImage(model = url, contentDescription = "Foto de la clínica", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                        Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f)).clickableSimple { quitar = url },
                            contentAlignment = Alignment.Center) { Text("✕", color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp) }
                    }
                }
                if (fotos.size < max) {
                    Box(Modifier.width(96.dp).aspectRatio(4f / 3f).clip(RoundedCornerShape(10.dp)).border(1.5.dp, c.borde, RoundedCornerShape(10.dp))
                        .clickableSimple { if (!subiendo) elegir() }, contentAlignment = Alignment.Center) {
                        Text(if (subiendo) "⏳" else "＋\nAgregar", color = c.textoSuave, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
        Tarjeta("Servicios en la página") {
            FilaInterruptor("Mostrar mis servicios", "Lista tus servicios activos (nombre, categoría y descripción).", servicios, habilitado = !guardandoToggle) { v ->
                val antes = servicios; servicios = v; guardarToggle("mostrarServicios", v) { servicios = antes }
            }
            FilaInterruptor("Mostrar también los precios", "Se muestran como «Desde ${simboloMoneda(moneda)} X» (el precio por sesión de cada servicio).", precios, habilitado = servicios && !guardandoToggle) { v ->
                val antes = precios; precios = v; guardarToggle("mostrarPrecios", v) { precios = antes }
            }
        }
    }
}

// ─── 📍 Ubicación ───────────────────────────────────────────────────────────

/**
 * Ciudad, distrito, dirección (con buscador), referencia y GPS. El ajuste fino
 * del pin arrastrando sobre un mapa queda en la web (la app no tiene mapa
 * interactivo): acá se captura el GPS estando en la clínica o se elige la
 * dirección del buscador, que trae sus coordenadas.
 */
@Composable
internal fun SeccionUbicacion(d: JsonObject, onVolver: () -> Unit, onCambio: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = recordarAcciones()
    val cl = d.clinica()
    var ciudad by remember { mutableStateOf(cl.t("ciudad")) }
    var distrito by remember { mutableStateOf(cl.t("distrito")) }
    var direccion by remember { mutableStateOf(cl.t("direccion")) }
    var referencia by remember { mutableStateOf(cl.t("referencia")) }
    var lat by remember { mutableStateOf(cl.n("lat")) }
    var lng by remember { mutableStateOf(cl.n("lng")) }
    var capturando by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var elegirCiudad by remember { mutableStateOf(false) }
    var elegirDistrito by remember { mutableStateOf(false) }
    var buscar by remember { mutableStateOf(false) }

    // Al tener coordenadas: dirección/ciudad/distrito de ese punto (la regla de la web).
    fun ubicarPin(la: Double, ln: Double) {
        lat = la; lng = ln
        scope.launch {
            val u = AjustesRepo.ubicacionDePin(la, ln) ?: return@launch
            u.s("direccion")?.let { direccion = it }
            u.s("ciudad")?.let { ciudad = it }
            u.s("distrito")?.let { distrito = it }
        }
    }
    val pedirGps = recordarSolicitarUbicacion { p ->
        capturando = false
        if (p == null) Toaster.error("No se pudo obtener la ubicación. Activa el GPS y permite el acceso.")
        else {
            ubicarPin(kotlin.math.round(p.first * 1e7) / 1e7, kotlin.math.round(p.second * 1e7) / 1e7)
            Toaster.exito("Ubicación capturada")
        }
    }

    if (elegirCiudad) DialogoCiudad(onElegir = { v ->
        // Al cambiar de ciudad, el distrito anterior deja de tener sentido.
        if (v != ciudad) distrito = ""
        ciudad = v; elegirCiudad = false
    }, onCerrar = { elegirCiudad = false })
    if (elegirDistrito) DialogoDistrito(ciudad, onElegir = { distrito = it; elegirDistrito = false }, onCerrar = { elegirDistrito = false })
    if (buscar) DialogoBuscarDireccion(ciudad, lat, lng, onElegir = { item ->
        buscar = false
        direccion = item.t("direccion")
        item.s("ciudad")?.takeIf { it.isNotBlank() }?.let { ciudad = it }
        item.s("distrito")?.takeIf { it.isNotBlank() }?.let { distrito = it }
        lat = item.n("lat"); lng = item.n("lng")
        Toaster.exito("Ubicación y GPS completados")
    }, onCerrar = { buscar = false })

    SubPantalla("Ubicación de la clínica", onVolver) {
        if (!d.plan().b("pagina")) {
            CandadoPlan("Aparece en el mapa de Sania con el plan Premium", "Configura la ubicación de tu clínica para que los pacientes te encuentren en el mapa. Disponible desde el plan Premium.")
            return@SubPantalla
        }
        Ayuda("Para aparecer en el mapa de Sania y que los pacientes te encuentren. Captura la ubicación GPS estando en la clínica para que el mapa sea exacto.")
        if (ciudad.isBlank() || lat == null || lng == null) Aviso("⚠ Indica tu ciudad y captura tu GPS (o elige la dirección de la lista) para aparecer en el mapa.")
        Tarjeta {
            Selector("Ciudad *", ciudad, "Ej. Tacna") { elegirCiudad = true }
            Selector("Distrito", distrito, "Elige de la lista") { elegirDistrito = true }
            Selector("Dirección (busca y elige de la lista)", direccion, if (ciudad.isBlank()) "Primero indica tu ciudad" else "Ej. Av. Bolognesi 123", habilitado = ciudad.isNotBlank()) { buscar = true }
            Campo(null, direccion, { direccion = it }, placeholder = "O escríbela a mano")
            Campo("Referencia (cómo llegar)", referencia, { referencia = it }, multilinea = true, lineas = 2, max = 500,
                placeholder = "Ej. Edificio Torre Sur, 5to piso, oficina 505. Al costado del BCP.")
            Ayuda("Piso, oficina, edificio, puntos de referencia. Se muestra en el mapa y en la reserva.")
        }
        Tarjeta("GPS") {
            if (lat != null && lng != null) {
                Text("✓ GPS guardado (${formatoCoord(lat!!)}, ${formatoCoord(lng!!)})", color = c.ok, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Boton("🗺️ Ver en el mapa", primario = false, modifier = Modifier.weight(1f)) { acciones.abrirMapa(lat!!, lng!!, cl.t("nombre")) }
                    Boton(if (capturando) "📡 Capturando…" else "📍 Volver a capturar", primario = false, habilitado = !capturando, modifier = Modifier.weight(1f)) { capturando = true; pedirGps() }
                }
            } else {
                Boton(if (capturando) "📡 Capturando…" else "📍 Usar mi ubicación actual (GPS)", primario = false, habilitado = !capturando) { capturando = true; pedirGps() }
            }
            Text("🎯 Ajustar el pin arrastrándolo sobre el mapa ↗", color = c.navy, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickableSimple { acciones.abrirUrl("${Supabase.SITE_URL}/configuracion?tab=publica") }.padding(4.dp))
            Ayuda("El ajuste fino del pin se hace en la web (necesita un mapa interactivo).")
        }
        Boton(if (guardando) "Guardando…" else "Guardar ubicación", habilitado = !guardando) {
            if (lat == null || lng == null) { Toaster.error("Captura tu GPS o elige la dirección de la lista"); return@Boton }
            if (ciudad.isBlank()) { Toaster.error("Indica al menos la ciudad"); return@Boton }
            guardando = true
            scope.launch {
                val r = guardarAjuste {
                    AjustesRepo.guardarSeccion("ubicacion", buildJsonObject {
                        put("ciudad", ciudad); put("distrito", distrito); put("direccion", direccion); put("referencia", referencia)
                        put("lat", lat); put("lng", lng)
                    })
                }
                guardando = false
                if (r.registrada) { Toaster.exito("Ubicación guardada"); onCambio() }
            }
        }
    }
}

private fun formatoCoord(v: Double): String = (kotlin.math.round(v * 1e5) / 1e5).toString()

/** Las ciudades del catálogo de la web (196, con su región). Acepta texto propio. */
@Composable
internal fun DialogoCiudad(onElegir: (String) -> Unit, onCerrar: () -> Unit) {
    var lista by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    LaunchedEffect(Unit) {
        val (r, _) = AjustesRepo.leerObjeto("${AjustesRepo.BASE}/geo?ciudades=1")
        lista = r?.objetos("ciudades")?.map { it.t("ciudad") to "${it.t("ciudad")} · ${it.t("region")}" } ?: emptyList()
    }
    DialogoLista("Ciudad", lista ?: emptyList(), onElegir, onCerrar, permitirTextoPropio = true)
}

/** Los distritos de la ciudad elegida (si está en el catálogo). Acepta texto propio. */
@Composable
internal fun DialogoDistrito(ciudad: String, onElegir: (String) -> Unit, onCerrar: () -> Unit) {
    var lista by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(ciudad) {
        val (r, _) = AjustesRepo.leerObjeto("${AjustesRepo.BASE}/geo?ciudad=${pe.saniape.app.ui.urlEncode(ciudad)}")
        lista = r?.textos("distritos")?.map { it to it } ?: emptyList()
    }
    DialogoLista("Distrito", lista, onElegir, onCerrar, permitirTextoPropio = true)
}

/** Buscador de direcciones (el mismo de la web: /api/geocode, anclado a la ciudad o al GPS). */
@Composable
internal fun DialogoBuscarDireccion(ciudad: String, lat: Double?, lng: Double?, onElegir: (JsonObject) -> Unit, onCerrar: () -> Unit) {
    val c = Sania.colors
    var q by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var buscando by remember { mutableStateOf(false) }
    LaunchedEffect(q) {
        // Una búsqueda vieja que se cancela (al seguir escribiendo o borrar) no deja "Buscando…" pegado.
        buscando = false
        if (q.trim().length < 3) { items = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(450)
        buscando = true
        try {
            val cerca = if (lat != null && lng != null) "&lat=$lat&lng=$lng" else ""
            val (r, _) = AjustesRepo.leerObjeto("/api/geocode?q=${pe.saniape.app.ui.urlEncode(q.trim())}&ciudad=${pe.saniape.app.ui.urlEncode(ciudad)}$cerca")
            items = r?.objetos("items") ?: emptyList()
        } finally { buscando = false }
    }
    AlertaConTeclado(
        onDismissRequest = onCerrar,
        title = { Text("Buscar dirección", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column {
                Campo(null, q, { q = it }, placeholder = "Ej. Av. Bolognesi 123")
                Spacer(Modifier.height(8.dp))
                if (buscando) Text("Buscando…", color = c.textoSuave, fontSize = 12.sp)
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    items.forEach { it2 ->
                        Column(Modifier.fillMaxWidth().clickableSimple { onElegir(it2) }.padding(vertical = 8.dp)) {
                            Text(it2.t("direccion"), color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(listOfNotNull(it2.s("zona"), it2.s("distrito"), it2.s("ciudad")).filter { it.isNotBlank() }.joinToString(" · "),
                                color = c.textoSuave, fontSize = 11.5.sp)
                        }
                    }
                    if (!buscando && q.trim().length >= 3 && items.isEmpty()) Text("Sin resultados. Escríbela a mano y captura tu GPS.", color = c.textoSuave, fontSize = 12.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}

// ─── 🔗 Redes y contacto ────────────────────────────────────────────────────

@Composable
internal fun SeccionRedesContacto(d: JsonObject, onVolver: () -> Unit, onCambio: () -> Unit) {
    val scope = rememberCoroutineScope()
    val cl = d.clinica()
    var facebook by remember { mutableStateOf(cl.t("facebook")) }
    var instagram by remember { mutableStateOf(cl.t("instagram")) }
    var tiktok by remember { mutableStateOf(cl.t("tiktok")) }
    var sitio by remember { mutableStateOf(cl.t("sitioWeb")) }
    var whatsapp by remember { mutableStateOf(cl.t("whatsappContacto")) }
    var video by remember { mutableStateOf(cl.t("videoUrl")) }
    var guardando by remember { mutableStateOf(false) }

    SubPantalla("Redes y contacto", onVolver) {
        if (!d.plan().b("pagina")) {
            CandadoPlan("Tu página pública con redes y video es del plan Premium", "Muestra tus redes, WhatsApp y un video en tu propia página dentro de Sania, para que los pacientes te conozcan antes de reservar. Disponible desde el plan Premium.")
            return@SubPantalla
        }
        Ayuda("Se muestran al paciente en el directorio y en tu página de reserva. Dan confianza y un canal para conocerte. Todas opcionales.")
        Tarjeta {
            val url = KeyboardType.Uri
            Campo("📘 Facebook", facebook, { facebook = it }, placeholder = "facebook.com/tuclinica", teclado = url, mayusculas = KeyboardCapitalization.None)
            Campo("📸 Instagram", instagram, { instagram = it }, placeholder = "instagram.com/tuclinica", teclado = url, mayusculas = KeyboardCapitalization.None)
            Campo("🎵 TikTok", tiktok, { tiktok = it }, placeholder = "tiktok.com/@tuclinica", teclado = url, mayusculas = KeyboardCapitalization.None)
            Campo("🌐 Sitio web", sitio, { sitio = it }, placeholder = "www.tuclinica.com", teclado = url, mayusculas = KeyboardCapitalization.None)
            Campo("💬 WhatsApp de contacto", whatsapp, { whatsapp = it }, placeholder = "+51 999 999 999", teclado = KeyboardType.Phone)
            Campo("🎬 Video de presentación (YouTube o TikTok)", video, { video = it }, placeholder = "https://youtube.com/watch?v=…", teclado = url, mayusculas = KeyboardCapitalization.None)
            Ayuda("Pega el enlace de un video de YouTube o TikTok. Se mostrará en tu página pública para que los pacientes te conozcan.")
        }
        Boton(if (guardando) "Guardando…" else "Guardar redes", habilitado = !guardando) {
            guardando = true
            scope.launch {
                val r = guardarAjuste {
                    AjustesRepo.guardarSeccion("redes", buildJsonObject {
                        put("facebook", facebook); put("instagram", instagram); put("tiktok", tiktok)
                        put("sitioWeb", sitio); put("whatsappContacto", whatsapp); put("videoUrl", video)
                    })
                }
                guardando = false
                if (r.registrada) { Toaster.exito("Redes guardadas"); onCambio() }
            }
        }
    }
}

// ─── ⭐ Reseñas ──────────────────────────────────────────────────────────────

@Composable
internal fun SeccionResenas(d: JsonObject, onVolver: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var resenas by remember { mutableStateOf<List<JsonObject>?>(null) }
    var recarga by remember { mutableIntStateOf(0) }
    var ocupado by remember { mutableStateOf<String?>(null) }
    var errorResenas by remember { mutableStateOf<String?>(null) }
    val conPlan = d.plan().b("reservas")
    LaunchedEffect(recarga) {
        if (!conPlan) return@LaunchedEffect
        val (r, e) = AjustesRepo.leerObjeto("${AjustesRepo.BASE}/resenas")
        when {
            r != null -> { resenas = r.objetos("resenas"); errorResenas = null }
            // Un fallo no se muestra como "aún no tienes reseñas".
            resenas == null -> errorResenas = e ?: "No se pudieron cargar las reseñas"
            else -> Toaster.error(e ?: "No se pudieron cargar las reseñas")
        }
    }
    fun cambiar(id: String, estado: String) {
        ocupado = id
        scope.launch {
            val r = guardarAjuste(porDefecto = "No se pudo actualizar la reseña") {
                AjustesRepo.enviar(io.ktor.http.HttpMethod.Patch, "${AjustesRepo.BASE}/resenas", buildJsonObject { put("id", id); put("estado", estado) })
            }
            ocupado = null
            if (r.registrada) {
                Toaster.exito(when (estado) { "Aprobada" -> "Reseña aprobada — ya aparece en tu página"; "Rechazada" -> "Reseña ocultada de tu página"; else -> "Reseña actualizada" })
                recarga++
            }
        }
    }

    SubPantalla("Reseñas de pacientes", onVolver) {
        if (!conPlan) {
            CandadoPlan("Las reseñas son del plan Plus", "Recibe reseñas de tus pacientes tras su cita y elige cuáles mostrar en tu página pública dentro de Sania. Disponible en el plan Plus.")
            return@SubPantalla
        }
        val lista = resenas
        when {
            lista == null && errorResenas != null -> ErrorCarga(errorResenas) { errorResenas = null; recarga++ }
            lista == null -> pe.saniape.app.ui.CargandoLista()
            lista.isEmpty() -> pe.saniape.app.ui.clinica.EstadoVacio("⭐", "Aún no tienes reseñas", "Aparecerán aquí cuando tus pacientes las dejen tras su cita.")
            else -> {
                Ayuda("Solo las reseñas aprobadas aparecen en tu página pública.")
                val pend = lista.count { it.t("estado") == "Pendiente" }
                if (pend > 0) Aviso("⏳ $pend ${if (pend == 1) "pendiente de revisar" else "pendientes de revisar"}")
                else Aviso("✓ No tienes reseñas pendientes de revisar", "ok")
                lista.forEach { r ->
                    Tarjeta {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.t("paciente_nombre"), color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            val estado = r.t("estado")
                            pe.saniape.app.ui.clinica.equipo.Pastilla(estado,
                                when (estado) { "Aprobada" -> c.ok; "Rechazada" -> c.error; else -> c.pend },
                                when (estado) { "Aprobada" -> c.okBg; "Rechazada" -> c.errorBg; else -> c.pendBg })
                        }
                        val n = (r.n("calificacion") ?: 0.0).toInt().coerceIn(0, 5)
                        Text("★".repeat(n) + "☆".repeat(5 - n), color = c.pend, fontSize = 15.sp)
                        r.s("comentario")?.takeIf { it.isNotBlank() }?.let { Text(it, color = c.texto, fontSize = 13.sp) }
                        Text(fechaLima(r.t("created_at")), color = c.textoSuave, fontSize = 11.sp)
                        val id = r.t("id")
                        val libre = ocupado != id
                        when (r.t("estado")) {
                            "Pendiente" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Boton("✓ Aprobar", habilitado = libre, modifier = Modifier.weight(1f)) { cambiar(id, "Aprobada") }
                                Boton("✕ Rechazar", habilitado = libre, peligro = true, modifier = Modifier.weight(1f)) { cambiar(id, "Rechazada") }
                            }
                            "Aprobada" -> Boton("Ocultar de mi página", habilitado = libre, peligro = true) { cambiar(id, "Rechazada") }
                            else -> Boton("Mostrar en mi página", habilitado = libre) { cambiar(id, "Aprobada") }
                        }
                    }
                }
            }
        }
    }
}

// ─── 📱 App del paciente ────────────────────────────────────────────────────

/** Reservar, pagar y ver el saldo desde la app del paciente. Cada interruptor guarda al tocarlo. */
@Composable
internal fun SeccionAppPaciente(d: JsonObject, onVolver: () -> Unit, onCambio: () -> Unit) {
    val scope = rememberCoroutineScope()
    val cfg = d.cfg()
    val esAdmin = d.b("esAdmin")
    val tieneReservas = d.plan().b("reservas")
    var reservas by remember { mutableStateOf(d.clinica().b("reservasActivas")) }
    var pago by remember { mutableStateOf(cfg.b("pagoOnline")) }
    var saldo by remember { mutableStateOf(cfg.b("mostrarSaldo")) }
    var mpConectado by remember { mutableStateOf(false) }
    var ocupado by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val (r, _) = AjustesRepo.leerObjeto("/api/staff/mp/estado")
        mpConectado = r?.b("conectado") == true && r.b("vencido") != true
    }
    fun cambiar(opcion: String, v: Boolean, aplicar: (Boolean) -> Unit, aviso: String) {
        ocupado = opcion
        scope.launch {
            val r = guardarAjuste { AjustesRepo.guardarSeccion("app-paciente", buildJsonObject { put("opcion", opcion); put("activo", v) }) }
            ocupado = null
            if (r.registrada) { aplicar(v); Toaster.exito(aviso); onCambio() }
        }
    }

    SubPantalla("App del paciente", onVolver) {
        Ayuda("Lo que tus pacientes pueden hacer desde la app y su portal web. Todo empieza apagado: tú decides qué habilitar.")
        Tarjeta {
            FilaInterruptor("Reservar citas por la app",
                if (tieneReservas) "El paciente agenda su propia cita (tú la confirmas en la agenda)." else "Disponible en el plan Plus.",
                reservas, habilitado = esAdmin && tieneReservas && ocupado != "reservas") { v ->
                cambiar("reservas", v, { reservas = it }, if (v) "Tus pacientes ya pueden reservar citas" else "Reservas por la app apagadas")
            }
            FilaInterruptor("Pagar desde la app",
                if (mpConectado) "El paciente paga su saldo con tarjeta o Yape. Apagado: cobra en recepción." else "Primero conecta Mercado Pago en «Cobros por la app».",
                pago, habilitado = esAdmin && mpConectado && ocupado != "pagoOnline") { v ->
                cambiar("pagoOnline", v, { pago = it }, if (v) "Tus pacientes ya pueden pagar desde la app" else "Pago por la app apagado — cobras en recepción")
            }
            FilaInterruptor("Mostrar el saldo al paciente", "Cuánto costó, cuánto pagó y cuánto debe. Apagado: solo ve su tratamiento.",
                saldo, habilitado = esAdmin && ocupado != "mostrarSaldo") { v ->
                cambiar("mostrarSaldo", v, { saldo = it }, if (v) "El paciente verá su saldo" else "Saldo oculto para el paciente")
            }
        }
        if (!esAdmin) Ayuda("Solo el administrador puede cambiarlo.")
    }
}
