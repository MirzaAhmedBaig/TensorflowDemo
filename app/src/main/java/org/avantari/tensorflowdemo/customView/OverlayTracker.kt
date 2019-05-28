package org.avantari.tensorflowdemo.customView

import android.graphics.*
import android.util.Size
import org.avantari.tensorflowdemo.Segmentor


/**
 * Created by Mirza Ahmed Baig on 2019-05-18.
 * Avantari Technologies
 * mirza@avantari.org
 */

class OverlayTracker(private val previewSize: Size) {

    private var bmp: Bitmap? = null

    private var resultPixels: Array<Array<Array<FloatArray>>>? = null
    private var pixels: IntArray? = null

    @Synchronized
    fun trackResults(result: Segmentor.Segmentation) {
        processResults(result)
    }

    @Synchronized
    fun draw(canvas: Canvas) {
        if (bmp != null) {
            val matrix = Matrix()
            val multiplierX = canvas.width / bmp!!.width.toFloat()
            val multiplierY = multiplierX * previewSize.width.toFloat() / previewSize.height.toFloat()
            matrix.postScale(multiplierX, multiplierY)
            matrix.postTranslate(0f, 0f)
            canvas.drawBitmap(bmp!!, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        }
    }

    private fun processResults(
        result: Segmentor.Segmentation
    ) {
        handleSegmentation(result)
    }

    private fun handleSegmentation(potential: Segmentor.Segmentation) {
        // very tricky part
        val width = potential.width
        val height = potential.height
        if (bmp == null) {
            bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
        if (pixels == null) {
            pixels = IntArray(bmp!!.height * bmp!!.width)
        }
        resultPixels = potential.pixels


        val intValues = potential.intValues

        val TF_OD_API_INPUT_WIDTH = bmp!!.width
        var p = 0
        for (i in 0 until TF_OD_API_INPUT_WIDTH) {
            for (j in 0 until TF_OD_API_INPUT_WIDTH) {
                val pixel = intValues[j * TF_OD_API_INPUT_WIDTH + i]
                val red: Int = (Color.red(pixel) * resultPixels!![0][i][j][0]).toInt()
                val green = (Color.green(pixel) * resultPixels!![0][i][j][0]).toInt()
                val blue = (Color.blue(pixel) * resultPixels!![0][i][j][0]).toInt()

                if (Color.rgb(red, green, blue) == Color.BLACK) {
                    pixels!![j * bmp!!.width + i] = Color.rgb(red, green, blue)

                } else {
                    pixels!![j * bmp!!.width + i] = intValues[j * TF_OD_API_INPUT_WIDTH + i]
                }

            }
        }

        bmp!!.setPixels(pixels, 0, bmp!!.width, 0, 0, bmp!!.width, bmp!!.height)
    }
}
