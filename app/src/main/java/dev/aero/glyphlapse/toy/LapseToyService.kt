package dev.aero.glyphlapse.toy

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.nothing.ketchum.GlyphMatrixManager
import dev.aero.glyphlapse.Config
import dev.aero.glyphlapse.engine.LapseEngine
import dev.aero.glyphlapse.render.LapseRenderer

/**
 * Glyph Toy « compteur temporel » — seul module dépendant du GlyphMatrixSDK.
 * Repos : 1 tick/s aligné sur la seconde. 30 fps pendant les animations
 * (slide de format, format Cycle, arrivée). Appui long = format suivant.
 */
class LapseToyService : GlyphMatrixService("GlyphLapse") {

    private val engine = LapseEngine()
    private val renderer = LapseRenderer()
    private val frameHandler = Handler(Looper.getMainLooper())
    private var running = false
    private lateinit var prefs: SharedPreferences

    private val vibrator: Vibrator by lazy {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    }

    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { p, _ ->
            Config.applyTo(p, engine)
            if (running) renderFrame() // reflet immédiat d'un changement dans l'app
        }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val animating = renderFrame()
            // aligné sur la frontière de seconde au repos : l'anneau avance pile au tic
            val delay = if (animating) FRAME_MS
            else (1000L - System.currentTimeMillis() % 1000L).coerceAtLeast(FRAME_MS)
            frameHandler.postDelayed(this, delay)
        }
    }

    override fun performOnServiceConnected(
        context: Context,
        glyphMatrixManager: GlyphMatrixManager,
    ) {
        prefs = Config.prefs(context)
        Config.applyTo(prefs, engine)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        running = true
        frameHandler.post(tick)
    }

    override fun performOnServiceDisconnected(context: Context) {
        running = false
        frameHandler.removeCallbacksAndMessages(null)
        if (::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onTouchPointLongPress() {
        engine.cycleFormat(now())
        // persiste pour que l'app affiche le format courant (listener no-op : même valeur)
        prefs.edit().putString(Config.KEY_FORMAT, engine.format.name).apply()
        if (running) renderFrame()
    }

    override fun onAodUpdate() {
        // AOD : rendu statique sans anneau/sablier, le système cadence les updates
        val snap = engine.update(System.currentTimeMillis(), now())
        push(renderer.render(snap, includeSeconds = false))
    }

    private fun now(): Double = System.nanoTime() / 1e9

    /** Rend une frame et renvoie true si une animation est en cours (→ 30 fps). */
    private fun renderFrame(): Boolean {
        val snap = engine.update(System.currentTimeMillis(), now())
        engine.drainEvents().forEach { event ->
            when (event) {
                LapseEngine.Event.FormatChanged ->
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))

                LapseEngine.Event.Arrived ->
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 90, 60, 90, 60, 220, 80, 350), -1
                        )
                    )
            }
        }
        push(renderer.render(snap))
        return snap.animating
    }

    private fun push(brightness: IntArray) {
        // setMatrixFrame attend des luminosités 0..4095 : le renderer sort du 0..255,
        // on applique le même ×16 que GlyphMatrixUtils (BRIGHTNESS_MULTIPLIER)
        for (i in frameBuf.indices) frameBuf[i] = brightness[i] * BRIGHTNESS_MULTIPLIER
        matrix?.setMatrixFrame(frameBuf)
    }

    private val frameBuf = IntArray(25 * 25)

    private companion object {
        const val FRAME_MS = 33L // ~30 fps pendant les animations
        const val BRIGHTNESS_MULTIPLIER = 16
    }
}
