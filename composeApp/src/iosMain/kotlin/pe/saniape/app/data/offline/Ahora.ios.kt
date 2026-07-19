package pe.saniape.app.data.offline

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

internal actual fun ahoraMs(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
