package com.pocketweibo.ui.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Best-effort city-level label from last known location + [Geocoder] (may use cached data; no network hard requirement).
 * Returns null if permission denied, no fix, or geocoding fails.
 */
@SuppressLint("MissingPermission")
suspend fun tryResolveApproxLocationLabel(context: Context): String? = withContext(Dispatchers.IO) {
    if (!hasLocationPermission(context)) return@withContext null
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        ?: return@withContext null
    if (!Geocoder.isPresent()) return@withContext null
    return@withContext try {
        @Suppress("DEPRECATION")
        Geocoder(context, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)
            ?.firstOrNull()
            ?.let { a ->
                a.locality?.takeIf { it.isNotBlank() }
                    ?: a.subAdminArea?.takeIf { it.isNotBlank() }
                    ?: a.adminArea?.takeIf { it.isNotBlank() }
            }
    } catch (_: Exception) {
        null
    }
}
