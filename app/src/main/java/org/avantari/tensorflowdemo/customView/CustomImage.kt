package org.avantari.tensorflowdemo.customView

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View


/**
 * Created by Mirza Ahmed Baig on 2019-05-17.
 * Avantari Technologies
 * mirza@avantari.org
 */

class CustomImage : View {
    val bitmap: Bitmap? = null

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas?) {
        if (bitmap != null) {
            val matrix = Matrix()
            val multiplierX = width / bitmap.width.toFloat()
            matrix.postScale(multiplierX, multiplierX)
            matrix.postTranslate(0f, 0f)
            canvas?.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))

        }

        super.onDraw(canvas)

    }
}