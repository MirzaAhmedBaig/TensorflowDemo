package org.avantari.tensorflowdemo.activities

import android.graphics.*
import android.util.Size
import android.widget.Toast
import kotlinx.android.synthetic.main.camera_connection_fragment.*
import org.avantari.tensorflowdemo.*
import org.avantari.tensorflowdemo.customView.OverlayTracker
import java.io.IOException

class DetectActivity : CameraActivity() {

    companion object {
        val TF_OD_API_INPUT_WIDTH = 512
        val TF_OD_API_INPUT_HEIGHT = 512
    }


    private var MAINTAIN_ASPECT: Boolean = false
    private var sensorOrientation: Int = 0


    private val TF_OD_API_MODEL_FILE = "mobilenetv2_Linknet_BCE_ACC_IOU_divide_1_sigmoid_512_train_matte_Q.tflite"
    private var detector: Segmentor? = null

    private var rgbFrameBitmap: Bitmap? = null
    private var croppedBitmap: Bitmap? = null

    private var frameToCropTransform: Matrix? = null
    private var cropToFrameTransform: Matrix? = null

    private var computingSegmentation = false

    private val SAVE_PREVIEW_BITMAP = false
    private var tracker: OverlayTracker? = null


    override fun onPreviewSizeChosen(size: Size, rotation: Int) {
        try {
            detector = TFTApi.create(
                assets,
                TF_OD_API_MODEL_FILE,
                TF_OD_API_INPUT_WIDTH,
                TF_OD_API_INPUT_HEIGHT
            )
            tracker = OverlayTracker(Size(TF_OD_API_INPUT_WIDTH, TF_OD_API_INPUT_HEIGHT))
        } catch (e: IOException) {
            e.printStackTrace()
            val toast = Toast.makeText(
                applicationContext, "Classifier could not be initialized", Toast.LENGTH_SHORT
            )
            toast.show()
            finish()
        }
        previewWidth = size.width
        previewHeight = size.height

        val cropHeight = TF_OD_API_INPUT_HEIGHT
        val cropWidth = TF_OD_API_INPUT_WIDTH

        sensorOrientation = rotation - getScreenOrientation()
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        croppedBitmap = Bitmap.createBitmap(cropWidth, cropHeight, Bitmap.Config.ARGB_8888)

        frameToCropTransform = ImageUtils.getTransformationMatrix(
            previewWidth, previewHeight,
            cropWidth, cropHeight,
            sensorOrientation, MAINTAIN_ASPECT
        )

        cropToFrameTransform = Matrix()
        frameToCropTransform?.invert(cropToFrameTransform)

    }

    override fun processImage() {

        if (computingSegmentation) {
            readyForNextImage()
            return
        }
        computingSegmentation = true

        rgbFrameBitmap?.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight)

        readyForNextImage()

        val canvas = Canvas(croppedBitmap)
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null)
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap!!)
        }

        runInBackground(
            Runnable {
                LOGGER.i(TAG, "Running segmention on image ${System.currentTimeMillis()}")

                val result = detector?.segmentImage(croppedBitmap!!)
                handleSegmentation(result!!)
                requestRender()
                computingSegmentation = false
            })
    }

    private var bmp: Bitmap? = null

    private var resultPixels: Array<Array<Array<FloatArray>>>? = null
    private var pixels: IntArray? = null
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
                val pixel = intValues[p]
                val red: Int = (Color.red(pixel) * resultPixels!![0][i][j][0]).toInt()
                val green = (Color.green(pixel) * resultPixels!![0][i][j][0]).toInt()
                val blue = (Color.blue(pixel) * resultPixels!![0][i][j][0]).toInt()
                pixels!![p] = Color.rgb(red, green, blue)
                p++

            }
        }


        bmp!!.setPixels(pixels, 0, bmp!!.width, 0, 0, bmp!!.width, bmp!!.height)
        runOnUiThread {
            imageView.setImageBitmap(bmp)

        }
    }
}
