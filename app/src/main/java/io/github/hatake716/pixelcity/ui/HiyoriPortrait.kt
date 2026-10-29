package io.github.hatake716.pixelcity.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import io.github.hatake716.pixelcity.R

/** Shared artwork for the inauguration diary and the subsequent city stories. */
class HiyoriPortrait(private val context: Context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bitmap by lazy {
        checkNotNull(BitmapFactory.decodeResource(context.resources, R.drawable.hiyori_portrait,
            BitmapFactory.Options().apply { inScaled = false; inSampleSize = 2 }))
    }
    fun draw(canvas: Canvas, bounds: RectF) {
        if (!bounds.isEmpty) canvas.drawBitmap(bitmap, null, bounds, paint)
    }
}
