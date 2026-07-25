package dev.aero.glyphlapse

import android.content.Context
import android.content.SharedPreferences
import dev.aero.glyphlapse.engine.LapseEngine

/**
 * Persistance de la configuration (SharedPreferences), partagée entre
 * l'app de configuration et le service toy — qui écoute les changements.
 */
object Config {
    const val PREFS = "glyphlapse"
    const val KEY_REF = "ref_epoch_millis"
    const val KEY_FORMAT = "format"
    const val KEY_SECONDS = "seconds_mode"
    const val KEY_SAVED = "saved_dates"

    /** Nombre maximum de dates favorites conservées. */
    const val SAVED_MAX = 5

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Dates favorites : liste d'epoch millis, persistée en CSV (usage app uniquement). */
    fun savedDates(prefs: SharedPreferences): List<Long> =
        prefs.getString(KEY_SAVED, null)
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
            ?: emptyList()

    fun setSavedDates(prefs: SharedPreferences, dates: List<Long>) {
        prefs.edit()
            .putString(KEY_SAVED, dates.joinToString(",") { it.toString() })
            .apply()
    }

    /** Idempotent : ne touche que ce qui a changé (préserve slides/arrivée en cours). */
    fun applyTo(prefs: SharedPreferences, engine: LapseEngine) {
        val ref = prefs.getLong(KEY_REF, engine.refMillis)
        if (ref != engine.refMillis) engine.setRef(ref)
        val format = runCatching {
            LapseEngine.Format.valueOf(prefs.getString(KEY_FORMAT, null) ?: "")
        }.getOrDefault(LapseEngine.Format.DETAIL)
        if (format != engine.format) engine.setFormatQuiet(format)
        engine.secondsMode = runCatching {
            LapseEngine.SecondsMode.valueOf(prefs.getString(KEY_SECONDS, null) ?: "")
        }.getOrDefault(LapseEngine.SecondsMode.RING)
    }
}
