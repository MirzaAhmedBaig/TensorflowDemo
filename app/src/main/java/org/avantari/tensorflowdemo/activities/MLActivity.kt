package org.avantari.tensorflowdemo.activities

import android.graphics.*
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.annotation.RequiresApi
import android.util.Log
import android.view.View
import com.google.android.gms.tasks.OnFailureListener
import com.google.firebase.ml.common.modeldownload.FirebaseLocalModel
import com.google.firebase.ml.common.modeldownload.FirebaseModelManager
import com.google.firebase.ml.custom.*
import kotlinx.android.synthetic.main.activity_ml.*
import org.avantari.tensorflowdemo.R
import java.nio.ByteBuffer
import com.google.firebase.ml.common.FirebaseMLException
import android.text.method.TextKeyListener.clear
import android.util.Size
import com.google.firebase.ml.custom.FirebaseModelOutputs
import com.google.android.gms.tasks.Task
import com.google.firebase.ml.custom.FirebaseModelInputs
import kotlinx.android.synthetic.main.camera_connection_fragment.*
import org.avantari.tensorflowdemo.ImageUtils
import org.avantari.tensorflowdemo.LOGGER


class MLActivity : CameraActivity() {


    private var interpreter: FirebaseModelInterpreter? = null
    private var inputOutputOptions: FirebaseModelInputOutputOptions? = null

    private val TF_OD_API_INPUT_WIDTH = 512
    private val intValues = IntArray(TF_OD_API_INPUT_WIDTH * TF_OD_API_INPUT_WIDTH)

    private var computingSegmentation = false
    private var rgbFrameBitmap: Bitmap? = null
    private var croppedBitmap: Bitmap? = null

    private var frameToCropTransform: Matrix? = null
    private var cropToFrameTransform: Matrix? = null

    private var sensorOrientation: Int = 0

    override fun onPreviewSizeChosen(size: Size, rotation: Int) {
        Log.d(TAG,"onPreviewSizeChosen")

        loadML()

        previewWidth = size.width
        previewHeight = size.height

        val cropHeight = TF_OD_API_INPUT_WIDTH
        val cropWidth = TF_OD_API_INPUT_WIDTH

        sensorOrientation = rotation - getScreenOrientation()
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        croppedBitmap = Bitmap.createBitmap(cropWidth, cropHeight, Bitmap.Config.ARGB_8888)

        frameToCropTransform = ImageUtils.getTransformationMatrix(
            previewWidth, previewHeight,
            cropWidth, cropHeight,
            sensorOrientation, false
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

        runInBackground(
            Runnable {
                LOGGER.i(TAG, "Running segmention on image ${System.currentTimeMillis()}")

//                val result = detector?.segmentImage(croppedBitmap!!)
//                handleSegmentation(result!!)
                convertBitmapToByteBuffer(croppedBitmap!!)
                requestRender()
            })
    }


    private fun loadML() {
        val localSource = FirebaseLocalModel.Builder("my_local_model") // Assign a name to this model
            .setAssetFilePath("mobilenetv2_Linknet_BCE_ACC_IOU_divide_1_sigmoid_512_train_matte_Q.tflite")
            .build()
        FirebaseModelManager.getInstance().registerLocalModel(localSource)

        val options = FirebaseModelOptions.Builder()
            .setLocalModelName("my_local_model")
            .build()
        interpreter = FirebaseModelInterpreter.getInstance(options)

        inputOutputOptions = FirebaseModelInputOutputOptions.Builder()
            .setInputFormat(0, FirebaseModelDataType.FLOAT32, intArrayOf(1, 512, 512, 3))
            .setOutputFormat(0, FirebaseModelDataType.FLOAT32, intArrayOf(1, 512, 512, 1))
            .build()


        /*mask_image.setOnClickListener {
            convertBitmapToByteBuffer(BitmapFactory.decodeResource(resources, R.drawable.image))


        }*/
    }


    private fun convertBitmapToByteBuffer(scaledBitmap: Bitmap) {


//        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, TF_OD_API_INPUT_WIDTH, TF_OD_API_INPUT_WIDTH, true)

        scaledBitmap.getPixels(intValues, 0, scaledBitmap.width, 0, 0, scaledBitmap.width, scaledBitmap.height)


        val batchNum = 0
        val input = Array(1) { Array(TF_OD_API_INPUT_WIDTH) { Array(TF_OD_API_INPUT_WIDTH) { FloatArray(3) } } }
        var p = 0
        for (x in 0 until TF_OD_API_INPUT_WIDTH) {
            for (y in 0 until TF_OD_API_INPUT_WIDTH) {
                val pixel = intValues[p]
                // Normalize channel values to [-1.0, 1.0]. This requirement varies by
                // model. For example, some models might require values to be normalized
                // to the range [0.0, 1.0] instead.
                input[batchNum][x][y][0] = (Color.red(pixel)) / 255.0f
                input[batchNum][x][y][1] = (Color.green(pixel)) / 255.0f
                input[batchNum][x][y][2] = (Color.blue(pixel)) / 255.0f
                p++
            }
        }

        /*val inputs = FirebaseModelInputs.Builder()
            .add(input) // add() as many input arrays as your model requires
            .build()

        val startTime = System.currentTimeMillis()
        interpreter?.run(inputs, inputOutputOptions!!)?.addOnSuccessListener { result ->

            Log.d(TAG, "Total time : ${System.currentTimeMillis() - startTime}")

            val output = result!!.getOutput<Array<Array<Array<FloatArray>>>>(0)
            eval(output)

        }
            ?.addOnFailureListener {
                Log.d(TAG, "ERROR : ${it.stackTrace}")
            }*/



        try {
            val inputs = FirebaseModelInputs.Builder().add(input).build()
            // Here's where the magic happens!!
            val startTime = System.currentTimeMillis()
            interpreter?.run(inputs, inputOutputOptions!!)?.continueWith { task ->
                val output = task.result!!.getOutput<Array<Array<Array<FloatArray>>>>(0)
                Log.d(TAG, "Total time : ${System.currentTimeMillis() - startTime}")
                eval(output)
                null
            }
        } catch (e: FirebaseMLException) {
            e.printStackTrace()
        }

    }


    private fun eval(imgDataOut: Array<Array<Array<FloatArray>>>) {
        val bmp = Bitmap.createBitmap(TF_OD_API_INPUT_WIDTH, TF_OD_API_INPUT_WIDTH, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(bmp.height * bmp.width)

        var p = 0
        for (i in 0 until TF_OD_API_INPUT_WIDTH) {
            for (j in 0 until TF_OD_API_INPUT_WIDTH) {
                val pixel = intValues[p]
                val red: Int = (Color.red(pixel) * imgDataOut[0][i][j][0]).toInt()
                val green = (Color.green(pixel) * imgDataOut[0][i][j][0]).toInt()
                val blue = (Color.blue(pixel) * imgDataOut[0][i][j][0]).toInt()
                pixels[p] = Color.rgb(red, green, blue)
                p++

            }
        }
        bmp.setPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)

        /*runOnUiThread {
            result.setImageBitmap(bmp)
            Log.d(TAG, "Done")
        }*/

        runOnUiThread {
            imageView.setImageBitmap(bmp)
            Log.d(TAG, "Done")
            computingSegmentation = false


        }


    }
}
