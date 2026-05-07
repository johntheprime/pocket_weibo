package com.pocketweibo.ui.screens.detail

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pocketweibo.ui.util.findActivity
import kotlin.math.abs
import kotlin.math.sqrt

private const val SHAKE_WARMUP_MS = 1_600L
private const val SHAKE_AFTER_TYPING_IDLE_MS = 1_200L
private const val SHAKE_COOLDOWN_MS = 3_000L
private const val LINEAR_SHAKE_THRESHOLD = 12.5f
private const val ACCEL_DELTA_THRESHOLD = 22f
private const val SHAKE_WINDOW_MS = 380L
private const val IMPULSES_NEEDED = 3

/**
 * While post detail is visible: deliberate shake schedules the configured default reminder.
 * Same motion heuristics as compose [com.pocketweibo.ui.screens.compose.ShakeToSendEffect].
 */
@Composable
fun PostDetailShakeToReminderEffect(
    canSchedule: Boolean,
    lastCommentEditedAtMark: Long,
    detailOpenedAtMark: Long,
    lastShakeReminderScheduledAtMark: Long,
    onShakeReminder: () -> Unit,
) {
    val context = LocalContext.current
    val canScheduleState by rememberUpdatedState(canSchedule)
    val lastCommentEditedAt by rememberUpdatedState(lastCommentEditedAtMark)
    val detailOpenedAt by rememberUpdatedState(detailOpenedAtMark)
    val lastShakeReminderAt by rememberUpdatedState(lastShakeReminderScheduledAtMark)
    val onShakeReminderState by rememberUpdatedState(onShakeReminder)

    DisposableEffect(context) {
        val activity = context.findActivity() as? ComponentActivity ?: return@DisposableEffect onDispose { }
        val sm = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val linear = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val sensor = linear ?: accel ?: return@DisposableEffect onDispose { }
        val useLinear = linear != null

        var lastX = 0f
        var lastY = 0f
        var lastZ = 0f
        var haveLastAccel = false
        var windowStart = 0L
        var impulsesInWindow = 0

        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

            override fun onSensorChanged(event: SensorEvent) {
                if (!canScheduleState) {
                    impulsesInWindow = 0
                    return
                }
                val now = SystemClock.elapsedRealtime()
                if (now - detailOpenedAt < SHAKE_WARMUP_MS) return
                if (now - lastCommentEditedAt < SHAKE_AFTER_TYPING_IDLE_MS) return
                if (lastShakeReminderAt != 0L && now - lastShakeReminderAt < SHAKE_COOLDOWN_MS) return

                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                val strong = if (useLinear) {
                    sqrt(x * x + y * y + z * z) > LINEAR_SHAKE_THRESHOLD
                } else {
                    if (!haveLastAccel) {
                        lastX = x
                        lastY = y
                        lastZ = z
                        haveLastAccel = true
                        return
                    }
                    val dx = abs(x - lastX)
                    val dy = abs(y - lastY)
                    val dz = abs(z - lastZ)
                    lastX = x
                    lastY = y
                    lastZ = z
                    (dx + dy + dz) > ACCEL_DELTA_THRESHOLD
                }

                if (!strong) return

                if (now - windowStart > SHAKE_WINDOW_MS) {
                    windowStart = now
                    impulsesInWindow = 1
                } else {
                    impulsesInWindow++
                }

                if (impulsesInWindow >= IMPULSES_NEEDED) {
                    impulsesInWindow = 0
                    windowStart = 0L
                    shakeFeedback(activity)
                    onShakeReminderState()
                }
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME ->
                    sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                Lifecycle.Event.ON_PAUSE ->
                    sm.unregisterListener(listener)
                else -> Unit
            }
        }
        activity.lifecycle.addObserver(observer)
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose {
            activity.lifecycle.removeObserver(observer)
            sm.unregisterListener(listener)
        }
    }
}

private fun shakeFeedback(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(VibratorManager::class.java)
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    } ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(28, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(28)
    }
}
