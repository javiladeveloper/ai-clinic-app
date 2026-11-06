package pe.saniape.app.data

/**
 * ¿La tienda de esta plataforma permite enlazar a pagar el plan de Sania FUERA de la app?
 *
 *  - Android (Google Play): sí — "Ver planes", "Mejorar plan", etc. abren saniape.com/suscripcion.
 *  - iOS (App Store): NO. La guideline 3.1.1 / 3.1.3 prohíbe botones, enlaces o llamados a
 *    comprar una suscripción digital por fuera del In-App Purchase. La app puede MOSTRAR el
 *    plan vigente (solo lectura), pero no invitar a pagarlo; la clínica lo gestiona en la web.
 *
 * Ojo: esto es SOLO para el plan de Sania (software). El pago del paciente por servicios
 * presenciales (Mercado Pago, Yape, efectivo) es un bien físico y sí se permite en iOS.
 */
expect val permiteEnlacesDePago: Boolean
