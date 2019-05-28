package org.avantari.tensorflowdemo

import android.graphics.Bitmap
import java.util.*


/**
 * Created by Mirza Ahmed Baig on 2019-05-15.
 * Avantari Technologies
 * mirza@avantari.org
 */

interface Segmentor {
    /**
     * An immutable result returned by a Classifier describing what was recognized.
     */
    class Segmentation(
        /**
         * A unique identifier for what has been recognized. Specific to the class, not the instance of
         * the object.
         */
        val pixels: Array<Array<Array<FloatArray>>>,
        val intValues: IntArray,
        val width: Int, val height: Int
    )

    fun segmentImage(bitmap: Bitmap): Segmentation
}