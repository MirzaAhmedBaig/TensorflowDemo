package org.avantari.tensorflowdemo.activities

import android.Manifest
import android.content.Context
import android.hardware.Camera
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.ImageReader
import android.os.*
import android.support.annotation.RequiresApi
import android.support.v7.app.AppCompatActivity
import android.util.Size
import android.view.Surface
import org.avantari.tensorflowdemo.*
import org.avantari.tensorflowdemo.extensions.fillBytes
import org.avantari.tensorflowdemo.extensions.hasPermission
import org.avantari.tensorflowdemo.extensions.isHardwareLevelSupported
import org.avantari.tensorflowdemo.extensions.requestPermission
import org.avantari.tensorflowdemo.fragments.CameraConnectionFragment
import org.avantari.tensorflowdemo.fragments.LegacyCameraConnectionFragment


/**
 * Created by Mirza Ahmed Baig on 2019-05-15.
 * Avantari Technologies
 * mirza@avantari.org
 */

abstract class CameraActivity : AppCompatActivity(), ImageReader.OnImageAvailableListener, Camera.PreviewCallback {

    private var useCamera2API: Boolean = false
    val TAG = CameraActivity::class.java.simpleName

    val PERMISSIONS_REQUEST = 1
    val PERMISSION_CAMERA = Manifest.permission.CAMERA
    val PERMISSION_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE

    protected var previewWidth = 0
    protected var previewHeight = 0

    private var handler: Handler? = null
    private var handlerThread: HandlerThread? = null
    private var imageConverter: Runnable? = null
    private var postInferenceCallback: Runnable? = null

    private var isProcessingFrame = false
    private var rgbBytes: IntArray? = null
    private val yuvBytes = arrayOfNulls<ByteArray>(3)
    private var yRowStride: Int = 0

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        if (hasPermission()) {
            setFragment()
        } else {
            requestPermission()
        }

    }


    @Synchronized
    override fun onResume() {
        super.onResume()
        handlerThread = HandlerThread("front")
        handlerThread?.start()
        handler = Handler(handlerThread?.looper)
    }

    @Synchronized
    override fun onPause() {

        /*if (!isFinishing) {
            LOGGER.d(TAG, "Requesting finish")
            finish()
        }*/

        handlerThread?.quitSafely()
        try {
            handlerThread?.join()
            handlerThread = null
            handler = null
        } catch (e: InterruptedException) {
            LOGGER.e(TAG, "Exception!")
        }

        super.onPause()
    }

    protected fun getRgbBytes(): IntArray {
        imageConverter?.run()
        return rgbBytes!!
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun setFragment() {
        val cameraId = chooseCamera()
        val fragment = if (useCamera2API) {
            LOGGER.d(TAG, "Use Camera 2 API")
            val camera2Fragment = CameraConnectionFragment().apply {
                setImageListener(this@CameraActivity)
                setCameraConnectionListener(object : CameraConnectionFragment.ConnectionCallback {
                    override fun onPreviewSizeChosen(size: Size, cameraRotation: Int) {
                        previewHeight = size.height
                        previewWidth = size.width
                        this@CameraActivity.onPreviewSizeChosen(size, cameraRotation)
                    }

                })
            }

            camera2Fragment.setCamera(cameraId!!)
            camera2Fragment
        } else {
            LegacyCameraConnectionFragment().apply {
                setImageListener(this@CameraActivity)
            }
        }

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
    }


    @RequiresApi(Build.VERSION_CODES.M)
    private fun chooseCamera(): String? {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                /*if (facing != null || facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue
                }*/

                val fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue
                useCamera2API = facing == CameraCharacteristics.LENS_FACING_EXTERNAL || isHardwareLevelSupported(
                    characteristics,
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
                )
                LOGGER.i(TAG, "Camera API lv2: %s$useCamera2API")
                if (facing == CameraCharacteristics.LENS_FACING_FRONT)
                    return cameraId
            }
        } catch (e: CameraAccessException) {
            LOGGER.e(TAG, "Not allowed to access camera $e")
        }
        return null
    }


    override fun onImageAvailable(reader: ImageReader?) {

        //We need wait until we have some size from onPreviewSizeChosen
        if (previewWidth == 0 || previewHeight == 0) {
            return
        }
        if (rgbBytes == null) {
            rgbBytes = IntArray(previewWidth * previewHeight)
        }
        try {
            val image = reader?.acquireLatestImage() ?: return

            if (isProcessingFrame) {
                image.close()
                return
            }
            isProcessingFrame = true

            val planes = image.planes
            fillBytes(planes, yuvBytes)
            yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride

            imageConverter = Runnable {
                ImageUtils.convertYUV420ToARGB8888(
                    yuvBytes[0]!!,
                    yuvBytes[1]!!,
                    yuvBytes[2]!!,
                    previewWidth,
                    previewHeight,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    rgbBytes!!
                )
            }

            postInferenceCallback = Runnable {
                image.close()
                isProcessingFrame = false
            }

            processImage()
        } catch (e: Exception) {
            LOGGER.e(TAG, "Exception! : $e")

            return
        }


    }

    override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {

        if (isProcessingFrame) {
            LOGGER.w(TAG, "Dropping frame!")
            return
        }

        try {
            // Initialize the storage bitmaps once when the resolution is known.
            if (rgbBytes == null) {
                val previewSize = camera?.parameters!!.previewSize
                previewWidth = previewSize.width
                previewHeight = previewSize.height

                LOGGER.i(TAG, "Preview Size ($previewWidth, $previewWidth), Orientation 90")

                rgbBytes = IntArray(previewWidth * previewHeight)
                onPreviewSizeChosen(Size(previewSize.width, previewSize.height), 90)
            }
        } catch (e: Exception) {
            LOGGER.e(TAG, "Exception! : $e")
            return
        }


        isProcessingFrame = true
        yuvBytes[0] = data
        yRowStride = previewWidth

        imageConverter =
            Runnable {
                ImageUtils.convertYUV420SPToARGB8888(
                    data!!,
                    previewWidth,
                    previewHeight,
                    rgbBytes!!
                )
            }

        postInferenceCallback = Runnable {
            camera?.addCallbackBuffer(data)
            isProcessingFrame = false
        }
        processImage()

    }

    protected fun readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback?.run()
        }
    }

    protected fun getScreenOrientation(): Int {
        return when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_270 -> 270
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_90 -> 90
            else -> 0
        }
    }

    @Synchronized
    protected fun runInBackground(r: Runnable) {
        if (handler != null) {
            handler!!.post(r)
        }
    }

    fun requestRender() {
        /*val overlay = findViewById(R.id.debug_overlay) as OverlayView
        if (overlay != null) {
            overlay!!.postInvalidate()

        }*/
    }

    protected abstract fun processImage()
    protected abstract fun onPreviewSizeChosen(size: Size, rotation: Int)
}