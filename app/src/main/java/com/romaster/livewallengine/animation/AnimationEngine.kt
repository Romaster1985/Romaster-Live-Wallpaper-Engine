/*
 * Copyright 2026 Román Ignacio Romero (Romaster)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Nota: Este proyecto incluye ColorPickerView (skydoves) licenciado bajo Apache 2.0.
 */

package com.romaster.livewallengine.animation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import com.romaster.livewallengine.formula.FormulaEngine
import com.romaster.livewallengine.model.AnimationAction
import com.romaster.livewallengine.model.AnimationLayer
import com.romaster.livewallengine.model.AnimationReaction
import com.romaster.livewallengine.model.ClockHand
import com.romaster.livewallengine.model.ClockHandMotion
import java.util.Calendar
import com.romaster.livewallengine.model.WallpaperProject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Motor de animaciones.
 *
 * Velocidad (con aceleración > 0): perfil triangular simétrico
 *   v=0 → v=vmax en la mitad del recorrido → v=0 al final
 *   (como el gráfico v/t de un tiro vertical).
 *
 * Giroscopio: ángulo 0..359 en el plano de la pantalla,
 * medido con el acelerómetro desde la posición “en pie” (portrait).
 * 0 = vertical, ~90 = inclinación hacia un lado, ~270 = el otro.
 */
object AnimationEngine : SensorEventListener {

    data class LayerAnimState(
        var offsetXNorm: Float = 0f,
        var offsetYNorm: Float = 0f,
        var zoomMul: Float = 1f,
        var rotationDeg: Float = 0f,
        var alphaMul: Float = 1f
    )

    data class FrameInput(
        val deviceLocked: Boolean,
        val wallpaperVisible: Boolean,
        val homeScreenPage: Int = 0,
        val screenW: Int = 1080,
        val screenH: Int = 1920,
        val context: Context? = null
    )

    private enum class Phase {
        IDLE,
        FORWARD,
        HOLD,
        FINISH_THEN_SNAP,
        REVERSE
    }

    private class AnimRuntime {
        var progress: Float = 0f
        var phase: Phase = Phase.IDLE
        var lastReaction: Float = 0f
        var phaseStartMs: Long = 0L
        var phaseStartProgress: Float = 0f
        var loopDir: Int = 1
        var loopTimeMs: Float = 0f
    }

    private val runtime = mutableMapOf<String, AnimRuntime>()
    private val states = mutableMapOf<String, LayerAnimState>()

    /**
     * Ángulo de inclinación 0..359°.
     * 0 = teléfono en portrait “en pie”.
     * Aumenta al inclinar el borde superior hacia la **derecha** (sentido horario
     * mirando la pantalla): 90 ≈ landscape con el borde izquierdo abajo.
     * Hacia la **izquierda** (antihorario): 270 ≈ landscape opuesto.
     */
    @Volatile
    private var orientationDeg: Float = 0f

    /** Inclinación firmada -180..180 (derecha positiva). */
    @Volatile
    private var tiltSignedDeg: Float = 0f

    private var sensorManager: SensorManager? = null
    private var accelSensor: Sensor? = null
    private var registered = false

    @Volatile
    private var lastHomePage: Int = 0

    fun ensureSensors(context: Context) {
        if (registered) return
        val sm = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return
        sensorManager = sm
        // Solo acelerómetro: mide inclinación respecto a la gravedad de forma estable
        // y no mezcla yaw/compás como el rotation vector.
        accelSensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelSensor?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            registered = true
        }
    }

    fun releaseSensors() {
        if (!registered) return
        sensorManager?.unregisterListener(this)
        registered = false
        sensorManager = null
        accelSensor = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val ax = event.values[0]
        val ay = event.values[1]
        // Ángulo desde “en pie” (gravedad en +Y del dispositivo).
        // atan2(-ax, ay): inclinación del borde superior hacia la derecha → positivo.
        //   upright ≈ 0
        //   90° derecha (CW) ≈ +90
        //   90° izquierda (CCW) ≈ -90
        val signed = Math.toDegrees(atan2(-ax.toDouble(), ay.toDouble())).toFloat()
        tiltSignedDeg = signed
        // 0..359 con derecha = 0..180 y izquierda = 180..359
        orientationDeg = ((signed % 360f) + 360f) % 360f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun currentOrientationDeg(): Float = orientationDeg

    fun currentTiltSignedDeg(): Float = tiltSignedDeg

    fun setHomeScreenPage(page: Int) {
        lastHomePage = page
    }

    fun homeScreenPage(): Int = lastHomePage

    fun notifyWallpaperVisibility(visible: Boolean, project: WallpaperProject) {
        val now = SystemClock.elapsedRealtime()
        for (anim in project.animationLayers) {
            if (anim.reaction != AnimationReaction.VISIBILITY) continue
            val rt = runtime.getOrPut(anim.id) { AnimRuntime() }
            val r = if (visible) 1f else 0f
            stepBinary(anim, rt, r, now)
            rt.lastReaction = r
        }
    }

    fun update(project: WallpaperProject, input: FrameInput) {
        input.context?.let { ensureSensors(it) }
        lastHomePage = input.homeScreenPage
        states.clear()
        val now = SystemClock.elapsedRealtime()
        val sw = input.screenW.coerceAtLeast(1)
        val sh = input.screenH.coerceAtLeast(1)

        val activeIds = project.animationLayers.map { it.id }.toSet()
        runtime.keys.filter { it !in activeIds }.toList().forEach { runtime.remove(it) }

        for (anim in project.animationLayers) {
            if (anim.reaction == AnimationReaction.DISABLED) {
                runtime[anim.id] = AnimRuntime()
                continue
            }
            val rt = runtime.getOrPut(anim.id) { AnimRuntime() }
            val reaction = evaluateReaction(anim, input)

            when (anim.reaction) {
                AnimationReaction.LOOP -> {
                    // Rotación de reloj solo en bucle normal: ángulo = hora del sistema
                    if (anim.action == AnimationAction.ROTATE && anim.clockRotation) {
                        rt.progress = 1f
                        rt.phase = Phase.HOLD
                    } else {
                        stepLoop(anim, rt, now, reverse = false)
                    }
                }
                AnimationReaction.LOOP_REVERSE -> stepLoop(anim, rt, now, reverse = true)
                else -> stepBinary(anim, rt, reaction, now)
            }
            rt.lastReaction = reaction
            applyActionToStates(anim, rt.progress, sw, sh)
        }
    }

    fun stateFor(targetKey: String): LayerAnimState =
        states[targetKey] ?: LayerAnimState()

    fun stateForImage(imageId: String): LayerAnimState = stateFor("img:$imageId")

    fun stateForWidget(widgetId: String): LayerAnimState = stateFor("wdg:$widgetId")

    // -------------------------------------------------------------------------
    // Reacciones
    // -------------------------------------------------------------------------

    private fun evaluateReaction(anim: AnimationLayer, input: FrameInput): Float {
        return when (anim.reaction) {
            AnimationReaction.DISABLED -> 0f
            AnimationReaction.UNLOCK -> if (!input.deviceLocked) 1f else 0f
            AnimationReaction.VISIBILITY -> if (input.wallpaperVisible) 1f else 0f
            AnimationReaction.HOME_SCROLL ->
                if (input.homeScreenPage == anim.homeScreenIndex) 1f else 0f
            AnimationReaction.GYROSCOPE -> if (gyroMatches(anim.gyroDegrees)) 1f else 0f
            AnimationReaction.FORMULA -> evalFormula01(anim.formula, input.context)
            AnimationReaction.LOOP, AnimationReaction.LOOP_REVERSE -> 1f
        }
    }

    /**
     * Activa cuando el ángulo actual está cerca del objetivo (±[tol]°)
     * en el círculo 0..359. Así 90° y 270° son lados opuestos y no se confunden.
     */
    private fun gyroMatches(targetDeg: Int): Boolean {
        val t = ((targetDeg % 360) + 360) % 360
        val a = orientationDeg
        val tol = 18f
        val diff = abs(a - t)
        val circular = min(diff, 360f - diff)
        return circular <= tol
    }

    private fun evalFormula01(formula: String, context: Context?): Float {
        return try {
            val raw = FormulaEngine.evaluate(formula, context).trim()
            val n = raw.replace(',', '.').toFloatOrNull()
            when {
                n != null -> n.coerceIn(0f, 1f)
                raw.equals("true", true) || raw == "1" -> 1f
                else -> 0f
            }
        } catch (_: Exception) {
            0f
        }
    }

    // -------------------------------------------------------------------------
    // Progreso binario
    // -------------------------------------------------------------------------

    private fun stepBinary(anim: AnimationLayer, rt: AnimRuntime, reaction: Float, now: Long) {
        val r = reaction.coerceIn(0f, 1f)

        when (rt.phase) {
            Phase.IDLE -> {
                rt.progress = 0f
                if (r >= 0.5f) startForward(rt, now, from = 0f)
            }
            Phase.FORWARD -> {
                rt.progress = integrateForward(anim, rt, now)
                if (r < 0.5f) {
                    if (anim.reverseOnDeactivate) startReverse(rt, now)
                    else rt.phase = Phase.FINISH_THEN_SNAP
                } else if (rt.progress >= 0.999f) {
                    rt.progress = 1f
                    rt.phase = Phase.HOLD
                }
            }
            Phase.FINISH_THEN_SNAP -> {
                rt.progress = integrateForward(anim, rt, now)
                if (rt.progress >= 0.999f) {
                    if (r < 0.5f) {
                        rt.progress = 0f
                        rt.phase = Phase.IDLE
                    } else {
                        rt.progress = 1f
                        rt.phase = Phase.HOLD
                    }
                }
            }
            Phase.HOLD -> {
                rt.progress = 1f
                if (r < 0.5f) {
                    if (anim.reverseOnDeactivate) startReverse(rt, now)
                    else {
                        rt.progress = 0f
                        rt.phase = Phase.IDLE
                    }
                }
            }
            Phase.REVERSE -> {
                rt.progress = integrateReverse(anim, rt, now)
                if (r >= 0.5f) startForward(rt, now, from = rt.progress)
                else if (rt.progress <= 0.001f) {
                    rt.progress = 0f
                    rt.phase = Phase.IDLE
                }
            }
        }
    }

    private fun startForward(rt: AnimRuntime, now: Long, from: Float) {
        rt.phase = Phase.FORWARD
        rt.phaseStartMs = now
        rt.phaseStartProgress = from.coerceIn(0f, 1f)
        rt.progress = rt.phaseStartProgress
    }

    private fun startReverse(rt: AnimRuntime, now: Long) {
        rt.phase = Phase.REVERSE
        rt.phaseStartMs = now
        rt.phaseStartProgress = rt.progress.coerceIn(0f, 1f)
    }

    private fun integrateForward(anim: AnimationLayer, rt: AnimRuntime, now: Long): Float {
        val d = actionDistance(anim)
        val vmax = anim.speed.coerceAtLeast(0.001f)
        val useAccel = anim.acceleration > 1e-9f
        val elapsed = (now - rt.phaseStartMs).toFloat().coerceAtLeast(0f)
        val dRemain = d * (1f - rt.phaseStartProgress)
        if (dRemain <= 1e-4f) return 1f
        val distCovered = distanceCovered(elapsed, dRemain, vmax, useAccel)
        val deltaP = (distCovered / d).coerceIn(0f, 1f - rt.phaseStartProgress)
        return (rt.phaseStartProgress + deltaP).coerceIn(0f, 1f)
    }

    private fun integrateReverse(anim: AnimationLayer, rt: AnimRuntime, now: Long): Float {
        val d = actionDistance(anim)
        val vmax = anim.speed.coerceAtLeast(0.001f)
        val useAccel = anim.acceleration > 1e-9f
        val elapsed = (now - rt.phaseStartMs).toFloat().coerceAtLeast(0f)
        val dRemain = d * rt.phaseStartProgress
        if (dRemain <= 1e-4f) return 0f
        val distCovered = distanceCovered(elapsed, dRemain, vmax, useAccel)
        val deltaP = (distCovered / d).coerceIn(0f, rt.phaseStartProgress)
        return (rt.phaseStartProgress - deltaP).coerceIn(0f, 1f)
    }

    // -------------------------------------------------------------------------
    // Loop
    // -------------------------------------------------------------------------

    private fun stepLoop(anim: AnimationLayer, rt: AnimRuntime, now: Long, reverse: Boolean) {
        val d = actionDistance(anim).coerceAtLeast(0.001f)
        val vmax = anim.speed.coerceAtLeast(0.001f)
        val useAccel = anim.acceleration > 1e-9f
        if (rt.phaseStartMs == 0L) {
            rt.phaseStartMs = now
            rt.loopTimeMs = 0f
            rt.progress = 0f
            rt.loopDir = 1
            return
        }
        val dt = (now - rt.phaseStartMs).toFloat().coerceIn(0f, 50f)
        rt.phaseStartMs = now
        rt.loopTimeMs += dt
        val duration = totalDurationMs(d, vmax, useAccel).coerceAtLeast(1f)

        if (!reverse) {
            while (rt.loopTimeMs >= duration) rt.loopTimeMs -= duration
            rt.progress = (distanceCovered(rt.loopTimeMs, d, vmax, useAccel) / d).coerceIn(0f, 1f)
        } else {
            val period = duration * 2f
            while (rt.loopTimeMs >= period) rt.loopTimeMs -= period
            if (rt.loopTimeMs <= duration) {
                rt.progress = (distanceCovered(rt.loopTimeMs, d, vmax, useAccel) / d).coerceIn(0f, 1f)
                rt.loopDir = 1
            } else {
                val tBack = rt.loopTimeMs - duration
                rt.progress = 1f - (distanceCovered(tBack, d, vmax, useAccel) / d).coerceIn(0f, 1f)
                rt.loopDir = -1
            }
        }
        rt.phase = Phase.FORWARD
    }

    // -------------------------------------------------------------------------
    // Cinemática
    //
    // useAccel = false → velocidad constante = vmax
    // useAccel = true  → perfil triangular simétrico sobre la DISTANCIA:
    //   v=0 en s=0, v=vmax en s=D/2, v=0 en s=D
    //   a = vmax²/D  (de v² = 2 a (D/2))
    //   t_total = 2 D / vmax
    // -------------------------------------------------------------------------

    private fun totalDurationMs(distance: Float, vmax: Float, useAccel: Boolean): Float {
        if (distance <= 0f) return 1f
        if (!useAccel) return distance / vmax
        // Triangular: t_half = D/vmax, T = 2D/vmax
        return 2f * distance / vmax
    }

    /**
     * Distancia recorrida tras [elapsedMs] hacia [distance].
     * Con aceleración: pico de velocidad exactamente en la mitad del tramo.
     */
    private fun distanceCovered(
        elapsedMs: Float,
        distance: Float,
        vmax: Float,
        useAccel: Boolean
    ): Float {
        if (distance <= 0f || elapsedMs <= 0f) return 0f
        if (!useAccel) return min(distance, vmax * elapsedMs)

        // a = vmax² / D  →  v alcanza vmax justo en s = D/2
        // t_half = vmax / a = D / vmax
        val tHalf = distance / vmax
        val tTotal = 2f * tHalf
        val t = min(elapsedMs, tTotal)
        val a = (vmax * vmax) / distance // px/ms²

        return if (t <= tHalf) {
            // Aceleración: s = ½ a t²
            (0.5f * a * t * t).coerceIn(0f, distance)
        } else {
            // Desaceleración desde el punto medio
            val td = t - tHalf
            val sMid = distance * 0.5f
            // s = sMid + vmax*td - ½ a td²
            (sMid + vmax * td - 0.5f * a * td * td).coerceIn(0f, distance)
        }
    }

    /**
     * Ángulo de la manecilla en grados (0° = 12 en punto, sentido horario),
     * más el offset [AnimationLayer.rotationDegrees] (posición del 12:00).
     */
    private fun computeClockHandAngle(anim: AnimationLayer): Float {
        val cal = Calendar.getInstance()
        val h12 = cal.get(Calendar.HOUR) // 0..11
        val m = cal.get(Calendar.MINUTE)
        val s = cal.get(Calendar.SECOND)
        val ms = cal.get(Calendar.MILLISECOND)
        val continuous = anim.clockHandMotion == ClockHandMotion.CONTINUOUS

        val hand = when (anim.clockHand) {
            ClockHand.HOUR -> {
                // 12 h = 360° → 30°/h; en continuo también avanza con min/seg
                if (continuous) {
                    (h12 % 12) * 30f + m * 0.5f + s * (0.5f / 60f) + ms * (0.5f / 60_000f)
                } else {
                    (h12 % 12) * 30f
                }
            }
            ClockHand.MINUTE -> {
                // 60 min = 360° → 6°/min
                if (continuous) {
                    m * 6f + s * 0.1f + ms * 0.0001f
                } else {
                    m * 6f
                }
            }
            ClockHand.SECOND -> {
                // 60 s = 360° → 6°/s
                if (continuous) {
                    s * 6f + ms * 0.006f
                } else {
                    s * 6f
                }
            }
        }
        val offset = anim.rotationDegrees
        var a = (hand + offset) % 360f
        if (a < 0f) a += 360f
        return a
    }

    private fun actionDistance(anim: AnimationLayer): Float {

        return when (anim.action) {
            AnimationAction.TRANSLATE, AnimationAction.TRANSLATE_INVERSE ->
                anim.moveDistancePx.coerceAtLeast(0f)
            AnimationAction.ZOOM_IN, AnimationAction.ZOOM_OUT ->
                anim.zoomAmount.coerceAtLeast(0f)
            AnimationAction.ROTATE ->
                anim.rotationDegrees.coerceAtLeast(0f)
            AnimationAction.FADE_IN, AnimationAction.FADE_OUT ->
                // Para fade, "distancia" = duración en ms; vmax = speed en (ms de fade)/ms
                // Si speed es pequeño el fade dura mucho. Usamos fadeDurationMs como distancia
                // y speed como fracción: tratamos vmax de forma que T ≈ fadeDurationMs cuando
                // speed sea ~1 unidad/ms con distance=fadeDurationMs → T = D/v = fadeDurationMs.
                anim.fadeDurationMs.coerceAtLeast(1L).toFloat()
        }
    }

    private fun applyActionToStates(anim: AnimationLayer, p: Float, screenW: Int, screenH: Int) {
        val key = anim.targetKey
        val st = states.getOrPut(key) { LayerAnimState() }
        val t = p.coerceIn(0f, 1f)
        when (anim.action) {
            AnimationAction.TRANSLATE -> {
                val dist = anim.moveDistancePx
                val rad = anim.moveAngleDeg * (PI.toFloat() / 180f)
                st.offsetXNorm += sin(rad) * dist * t / screenW.toFloat()
                st.offsetYNorm += -cos(rad) * dist * t / screenH.toFloat()
            }
            AnimationAction.TRANSLATE_INVERSE -> {
                val dist = anim.moveDistancePx
                val rad = anim.moveAngleDeg * (PI.toFloat() / 180f)
                val inv = 1f - t
                st.offsetXNorm += sin(rad) * dist * inv / screenW.toFloat()
                st.offsetYNorm += -cos(rad) * dist * inv / screenH.toFloat()
            }
            AnimationAction.ZOOM_IN -> {
                st.zoomMul *= (1f + (anim.zoomAmount / 100f) * t).coerceAtLeast(0.05f)
            }
            AnimationAction.ZOOM_OUT -> {
                val factor = (anim.zoomAmount / 1000f).coerceIn(0f, 0.95f) * t
                st.zoomMul *= (1f - factor).coerceAtLeast(0.05f)
            }
            AnimationAction.FADE_OUT -> st.alphaMul *= (1f - t).coerceIn(0f, 1f)
            AnimationAction.FADE_IN -> st.alphaMul *= t.coerceIn(0f, 1f)
            AnimationAction.ROTATE -> {
                val sign = if (anim.rotationCounterClockwise) 1f else -1f
                if (anim.clockRotation) {
                    // Ángulo absoluto según manecilla + offset del 12:00
                    st.rotationDeg += sign * computeClockHandAngle(anim)
                } else {
                    st.rotationDeg += sign * anim.rotationDegrees * t
                }
            }
        }
    }
}
