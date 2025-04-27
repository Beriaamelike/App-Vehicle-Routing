package com.example.mapbox

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import java.util.Locale

object ColorUtils {
    fun getRouteColor(context: Context, index: Int): String {
        val colorResId = when (index % 4) {
            0 -> R.color.route_blue
            1 -> R.color.route_red
            2 -> R.color.route_green
            else -> R.color.route_yellow
        }

        val colorInt = ContextCompat.getColor(context, colorResId)
        return colorToRgbaString(colorInt)
    }

    private fun colorToRgbaString(color: Int): String {
        return String.format(
            Locale.US,  // Explicitly setting Locale
            "rgba(%d,%d,%d,%f)",
            Color.red(color),
            Color.green(color),
            Color.blue(color),
            Color.alpha(color) / 255f
        )
    }
}