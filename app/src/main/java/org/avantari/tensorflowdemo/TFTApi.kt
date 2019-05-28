package org.avantari.tensorflowdemo

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel


/**
 * Created by Mirza Ahmed Baig on 2019-05-14.
 * Avantari Technologies
 * mirza@avantari.org
 */

class TFTApi : Segmentor {
    private val IMAGE_STD: Float = 255f
    // Config values.
    private var inputWidth: Int = 0
    private var inputHeight: Int = 0

    private var intValues: IntArray? = null
    private var pixelClasses: Array<Array<Array<FloatArray>>>? = null
    private var imgData: ByteBuffer? = null

    private var tfLite: Interpreter? = null

    private val tfliteOptions by lazy {
        Interpreter.Options().apply {
            setNumThreads(1)
//            setAllowFp16PrecisionForFp32(true)
//            setUseNNAPI(true)
//            addDelegate(GpuDelegate())

        }
    }


    override fun segmentImage(bitmap: Bitmap): Segmentor.Segmentation {
        if (imgData != null) {
            imgData!!.rewind()
        }

        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var p = 0
        for (i in 0 until inputWidth) {
            for (j in 0 until inputHeight) {
                val pixel = intValues!![p++]
                imgData?.putFloat((Color.red(pixel)) / IMAGE_STD)
                imgData?.putFloat((Color.green(pixel)) / IMAGE_STD)
                imgData?.putFloat((Color.blue(pixel)) / IMAGE_STD)

            }

        }

        val startTime = System.currentTimeMillis()
        tfLite!!.run(imgData!!, pixelClasses!!)
        Log.d("TAG", "Total time ${System.currentTimeMillis() - startTime}")

        return Segmentor.Segmentation(
            pixelClasses!!,
            intValues!!,
            inputWidth, inputHeight
        )
    }


    companion object {
        /** Memory-map the model file in Assets.  */
        @Throws(IOException::class)
        private fun loadModelFile(assets: AssetManager, modelFilename: String): MappedByteBuffer {
            val fileDescriptor = assets.openFd(modelFilename)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        }

        @Throws(IOException::class)
        fun create(
            assetManager: AssetManager,
            modelFilename: String,
            inputWidth: Int,
            inputHeight: Int
        ): Segmentor {
            val d = TFTApi()

            d.inputWidth = inputWidth
            d.inputHeight = inputHeight

            try {
                d.tfLite = Interpreter(loadModelFile(assetManager, modelFilename), d.tfliteOptions)
            } catch (e: Exception) {
                throw RuntimeException(e)
            }

            // Pre-allocate buffers.
            d.imgData = ByteBuffer.allocateDirect(d.inputWidth * d.inputHeight * 3 * 4)
            d.imgData!!.order(ByteOrder.nativeOrder())
            d.intValues = IntArray(d.inputWidth * d.inputHeight)
            d.pixelClasses = Array(1) { Array(d.inputWidth) { Array(d.inputHeight) { FloatArray(1) } } }
            return d
        }

    }


}
