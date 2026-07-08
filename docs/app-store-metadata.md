# App Store — Metadata para enviar a revisión (Sania iOS)

Contenido listo para copiar/pegar en App Store Connect → Sania → Distribución.
Idioma: Español (México). Ajusta a tu gusto.

## Datos base
- **Política de privacidad (URL):** https://saniape.com/privacidad
- **URL de soporte:** https://saniape.com  (o una de contacto, ej. https://saniape.com/contacto)
- **Categoría:** Medicina (Principal). Secundaria opcional: Salud y forma física.
- **Cuenta demo (notas de revisión):** admin_test@admin.com / Admin-Test-2026 (Clinica Test)

## Nombre / Subtítulo
- **Nombre:** `Sania` (ya está)
- **Subtítulo (≤30):** `Gestión clínica y citas`

## Texto promocional (≤170)
```
Agenda, pacientes, historias clínicas y pagos en un solo lugar. Y para pacientes: reserva citas en clínicas cercanas desde tu celular.
```

## Palabras clave (≤100, separadas por comas)
```
clinica,citas,agenda,salud,medico,fisioterapia,reserva,pacientes,historia clinica,consultorio,turnos
```

## Descripción
```
Sania es la app para gestionar tu clínica o consultorio y para que tus pacientes reserven en línea.

PARA CLÍNICAS Y PROFESIONALES
• Agenda de citas clara y en tiempo real.
• Fichas de pacientes con historia clínica y evolución.
• Registro de sesiones, pagos y caja del día.
• Servicios, tratamientos y equipo.
• Todo sincronizado con tu panel web de Sania.

PARA PACIENTES
• Encuentra clínicas cercanas en el mapa.
• Reserva tu cita en pocos toques.
• Consulta tus próximas citas.

Inicia sesión como clínica con tu usuario y contraseña, o como paciente con Apple o Google.

Sania cumple la Ley N° 29733 de Protección de Datos Personales (Perú).
Política de privacidad: https://saniape.com/privacidad
```

## Notas para la revisión (App Review Information → Notes)
```
Sania es una herramienta de GESTIÓN clínica y reserva de citas (agenda, fichas de
pacientes, pagos). NO es un dispositivo médico ni ofrece diagnóstico o tratamiento;
los datos clínicos los ingresa y administra cada clínica sobre sus propios pacientes.

La app tiene DOS accesos en la pantalla de login:

1) CLÍNICAS / STAFF → botón "Acceso clínicas" → usuario y contraseña.
   Cuenta demo para revisión:
     Usuario: admin_test@admin.com
     Contraseña: Admin-Test-2026
   (Da acceso al panel de una clínica de prueba: agenda, pacientes, pagos.)

2) PACIENTES → "Portal del paciente" → "Iniciar sesión con Apple" o "Continuar con Google".
   Se puede probar con Sign in with Apple usando su propia cuenta.

Nota: la app consume el backend de Sania (saniape.com). La cuenta demo ya tiene datos de ejemplo.
Contacto: hola@saniape.com
```

## Cuestionario de privacidad (App Privacy) — qué declarar
Sania SÍ recopila datos, vinculados a la identidad, para funcionamiento (NO para tracking/ads):
- **Información de contacto:** correo, nombre (para la cuenta).
- **Datos de salud:** historias clínicas / datos de pacientes (dato sensible — se recopila).
- **Contenido del usuario:** fotos (fotos clínicas / evolutivas).
- **Ubicación:** aproximada/precisa (para "clínicas cercanas"; solo si el usuario la concede).
- **Identificadores:** ID de usuario.
- **Diagnósticos/uso:** opcional según lo que realmente se registre.
Marcar todos como "vinculados al usuario" y uso = "Funcionalidad de la app". NO marcar "Rastreo".
(Verificar contra https://saniape.com/privacidad.)

## Capturas de pantalla (las sacas del simulador/iPhone)
- **iPhone 6.9"** (iPhone 17 Pro Max, 1320×2868) — OBLIGATORIAS, mínimo 1, hasta 10.
- **iPad** — solo si mantienes soporte iPad (el target es "1,2" = iPhone+iPad). Si NO quieres
  preparar capturas de iPad, cambia TARGETED_DEVICE_FAMILY a "1" (solo iPhone) y evitas eso.
- Pantallas sugeridas: intro/login, portal del paciente (mapa de clínicas), reserva de cita,
  panel de clínica (agenda), ficha de paciente.
- Cómo capturar en el simulador: ⌘S guarda una captura del simulador (o Archivo → Guardar captura).

## Checklist para "Añadir a revisión"
- [ ] Subtítulo, keywords, descripción, texto promocional
- [ ] Categoría (Medicina)
- [ ] URL de política de privacidad + URL de soporte
- [ ] Cuestionario de App Privacy
- [ ] Clasificación de edad (cuestionario)
- [ ] Capturas iPhone 6.9" (y iPad si aplica)
- [ ] Build 2 seleccionado en la versión
- [ ] Notas de revisión con cuenta demo
- [ ] Precio y disponibilidad (Gratis, países)
- [ ] Enviar a revisión
```
