package pe.saniape.app.ui.clinica.ajustes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.JsonObject
import pe.saniape.app.data.staff.AjustesRepo
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.ui.CargandoLista
import pe.saniape.app.ui.ManejarAtras
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.theme.Sania

/**
 * ⚙️ Ajustes de la clínica (Más → Administración), gemelo NATIVO de la web
 * /configuracion. La lista agrupa las secciones igual que las pestañas de la
 * web ("Mi clínica", "Lo que ve el paciente", "Mensajes y cobros", "Cómo
 * trabajamos"); cada sección abre su sub-pantalla con su propio atrás.
 *
 * Se abre con permiso "ajustes" (lo decide el padre). Los candados de plan y
 * de Admin son los mismos de la web, y el servidor los vuelve a validar
 * (/api/staff/configuracion/…). [onHuboCambios] avisa EN CUANTO algo se
 * guarda, para que el padre recargue el contexto (nombre, colores,
 * terminología, módulos…) al cerrar Ajustes por cualquier vía: la cabecera,
 * el atrás del sistema o un tab.
 */
@Composable
fun PantallaAjustes(ctx: ContextoStaff, onSalir: () -> Unit, onHuboCambios: () -> Unit) = pe.saniape.app.tutoriales.PantallaTutorial("Ajustes") {
    val c = Sania.colors
    var datos by remember { mutableStateOf<JsonObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var recarga by remember { mutableIntStateOf(0) }
    var seccion by remember { mutableStateOf<String?>(null) }
    // Vive acá (no en la lista): al volver de una sección, la lista sigue donde estaba.
    val scrollLista = rememberScrollState()

    LaunchedEffect(ctx.clinicaId, recarga) {
        val (d, e) = AjustesRepo.cargar()
        if (d != null) { datos = d; error = null } else if (datos == null) error = e
        else Toaster.error(e ?: "No se pudieron actualizar los ajustes")
    }

    val cambio: () -> Unit = { onHuboCambios(); recarga++ }
    // Atrás del sistema: desde una sección vuelve a la lista; desde la lista,
    // cierra Ajustes (y el padre recarga el contexto si hubo cambios).
    ManejarAtras(activo = true) { if (seccion != null) seccion = null else onSalir() }

    val d = datos
    val sec = seccion
    if (d != null && sec != null) {
        val volver = { seccion = null }
        when (sec) {
            "marca" -> SeccionMarca(d, volver, cambio)
            "tu-pagina" -> SeccionTuPagina(d, volver)
            "diseno" -> SeccionDiseno(d, volver, cambio)
            "portada" -> SeccionPortada(d, volver, cambio)
            "contenido" -> SeccionContenido(d, volver, cambio)
            "ubicacion" -> SeccionUbicacion(d, volver, cambio)
            "redes" -> SeccionRedesContacto(d, volver, cambio)
            "resenas" -> SeccionResenas(d, volver)
            "consentimientos" -> SeccionConsentimientos(d, volver)
            "informe-psico" -> SeccionInformePsico(d, volver)
            "historia-clinica" -> SeccionHistoriaClinica(d, volver)
            "app-paciente" -> SeccionAppPaciente(d, volver, cambio)
            "tus-redes" -> SeccionTusRedes(d, ctx, volver)
            "cobros" -> SeccionCobrosOnline(d, volver, cambio)
            "notificaciones" -> SeccionNotificaciones(d, volver, cambio)
            "flujo" -> SeccionFlujo(d, volver, cambio)
            "hallazgos" -> SeccionHallazgos(volver)
            "modulos" -> SeccionModulos(d, volver, cambio)
            "sedes" -> SeccionSedes(d, ctx, volver, cambio)
            "campos" -> SeccionCamposPaciente(d, volver, cambio)
            "metodos-pago" -> SeccionMetodosPago(volver)
            "cobro-consulta" -> SeccionCobroConsulta(d, volver, cambio)
            "equipamiento" -> SeccionEquipamiento(d, volver)
            "mostrador" -> SeccionMostrador(d, volver, cambio)
            "categorias" -> SeccionCategorias(volver)
            "imagenes" -> SeccionImagenesClinicas(d, volver, cambio)
            "comercial" -> SeccionComercial(d, volver, cambio)
            "horarios" -> SeccionHorarios(d, volver, cambio)
            "simultaneas" -> SeccionSimultaneas(d, volver, cambio)
            else -> LaunchedEffect(sec) { seccion = null }
        }
        return@PantallaTutorial
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            CabeceraAjustes("← Más", "Ajustes de la clínica", { onSalir() })
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
                else -> ListaSecciones(d, ctx, scrollLista) { seccion = it }
            }
        }
    }
}

/** Una fila de la lista: clave de la sección, título y un detalle de su estado. */
private data class FilaSeccion(val clave: String, val titulo: String, val detalle: String? = null)

@Composable
private fun ListaSecciones(d: JsonObject, ctx: ContextoStaff, scroll: ScrollState, abrir: (String) -> Unit) {
    val c = Sania.colors
    val cl = d.o("clinica") ?: JsonObject(emptyMap())
    val plan = d.o("plan") ?: JsonObject(emptyMap())
    val cfg = d.o("config") ?: JsonObject(emptyMap())
    val conPagina = plan.b("pagina")
    val candadoPremium = "🔒 Desde el plan Premium"
    val tieneEspecialidades = d.objetos("especialidades").isNotEmpty()
    val dentalSinEsp = !tieneEspecialidades && (d.o("dental")?.b("solo") == true)

    val grupos = listOf(
        "MI CLÍNICA" to listOf(
            FilaSeccion("marca", "🎨  Identidad de marca", "${cl.s("nombre") ?: ""} · logo, colores y cómo llamas a tu equipo"),
        ),
        "LO QUE VE EL PACIENTE" to buildList {
            if (!cl.s("slug").isNullOrBlank()) add(FilaSeccion("tu-pagina", "📣  Tu página", "Compártela: enlace, QR y botón para tu web"))
            add(FilaSeccion("diseno", "🎨  Diseño de la página", if (conPagina) "Plantilla, fondo y secciones" else candadoPremium))
            add(FilaSeccion("portada", "🖼️  Portada", if (conPagina) "Foto, título y datos destacados" else candadoPremium))
            add(FilaSeccion("contenido", "🧩  Contenido", "Fotos de tu clínica y servicios en la página"))
            add(FilaSeccion("ubicacion", "📍  Ubicación de la clínica", if (!conPagina) candadoPremium else if (cl.s("ciudad").isNullOrBlank() || cl.n("lat") == null) "⚠ Falta para aparecer en el mapa" else listOfNotNull(cl.s("distrito")?.takeIf { it.isNotBlank() }, cl.s("ciudad")).joinToString(", ")))
            add(FilaSeccion("redes", "🔗  Redes y contacto", if (conPagina) "Facebook, Instagram, TikTok, WhatsApp y video" else candadoPremium))
            add(FilaSeccion("resenas", "⭐  Reseñas de pacientes", if (plan.b("reservas")) "Aprueba cuáles se muestran" else "🔒 Plan Plus"))
            add(FilaSeccion("consentimientos", "✍  Compromiso y consentimiento", "Lo que el paciente lee y firma antes de empezar"))
            if (d.b("informePsico")) add(FilaSeccion("informe-psico", "🧠  Plantilla del informe psicológico", "Secciones, encabezado y pie"))
            if (d.b("historiaMedica")) add(FilaSeccion("historia-clinica", "📄  Historia clínica y consentimiento informado", "IPRESS y plantillas por procedimiento"))
            add(FilaSeccion("app-paciente", "📱  App del paciente", "Reservar, pagar y ver su saldo"))
        },
        "MENSAJES Y COBROS" to listOf(
            FilaSeccion("tus-redes", "💬  Tus redes", "WhatsApp, Instagram, Facebook y TikTok"),
            FilaSeccion("cobros", "💳  Cobros por la app", "Mercado Pago"),
            FilaSeccion("notificaciones", "🔔  Recordatorios por WhatsApp",
                if (!plan.b("whatsapp")) "🔒 Plan Plus" else if (cfg.b("recordatoriosActivo")) "Encendidos" else "Apagados"),
        ),
        "CÓMO TRABAJAMOS" to buildList {
            add(FilaSeccion("flujo", "🔀  Flujo de atención", "Cómo empieza la atención, cómo se llama cada paso y cuánto dura"))
            if (dentalSinEsp) add(FilaSeccion("hallazgos", "🦷  Hallazgos del odontograma", "Colores y procedimiento sugerido"))
            add(FilaSeccion("modulos", "🩺  Módulos clínicos", "Atención médica, triaje y recetas"))
            add(FilaSeccion("sedes", "🏢  Sedes / Locales", if (cl.b("multisedeActivada")) "Varias sedes" else "Un solo local"))
            add(FilaSeccion("campos", "🗂️  Campos personalizados del paciente"))
            add(FilaSeccion("metodos-pago", "💳  Métodos de pago"))
            add(FilaSeccion("cobro-consulta", "💰  Cobro de la consulta", if (cfg.b("cobroExplicito")) "Lo cobra recepción o el administrador" else "Se asume pagada al terminar"))
            add(FilaSeccion("equipamiento", "⚙️  Equipamiento", "Las máquinas que conoce el asistente"))
            add(FilaSeccion("mostrador", "🛎️  Preguntas de mostrador", "Seguros, estacionamiento, pagos…"))
            add(FilaSeccion("categorias", "🏷️  Categorías de finanzas"))
            if (plan.b("fotosEvolutivas")) add(FilaSeccion("imagenes", "🖼️  Imágenes clínicas", "Fotos evolutivas y tipos de imagen"))
            add(FilaSeccion("comercial", "🏢  Perfil comercial y facturación"))
            add(FilaSeccion("horarios", "⏰  Horarios de atención"))
            add(FilaSeccion("simultaneas", "👥  Pacientes a la misma hora",
                cfg.s("maxSimultaneas")?.let { "Máximo $it por profesional" } ?: "Sin límite"))
        },
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(Sania.dim.lg),
        verticalArrangement = Arrangement.spacedBy(Sania.dim.sm),
    ) {
        if (!ctx.esAdmin) {
            Aviso("Algunas opciones solo las cambia el administrador de la clínica.", "info")
        }
        grupos.forEach { (titulo, filas) ->
            Spacer(Modifier.height(Sania.dim.sm))
            Text(titulo, color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
            filas.forEach { f -> FilaNav(f.titulo, f.detalle) { abrir(f.clave) } }
        }
        Spacer(Modifier.height(Sania.dim.xxl))
    }
}
