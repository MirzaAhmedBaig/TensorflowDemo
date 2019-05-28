package org.avantari.tensorflowdemo.fragments

import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.support.v4.app.Fragment
import android.util.Size
import android.util.SparseIntArray
import android.view.*
import org.avantari.tensorflowdemo.customView.AutoFitTextureView
import org.avantari.tensorflowdemo.ImageUtils
import org.avantari.tensorflowdemo.LOGGER
import org.avantari.tensorflowdemo.R
import org.avantari.tensorflowdemo.activities.DetectActivity.Companion.TF_OD_API_INPUT_HEIGHT
import org.avantari.tensorflowdemo.activities.DetectActivity.Companion.TF_OD_API_INPUT_WIDTH
import java.io.IOException

open class LegacyCameraConnectionFragment : Fragment() {
    private val TAG = LegacyCameraConnectionFragment::class.java.simpleName
    private var camera: Camera? = null

    private val desiredSize by lazy {
        Size(TF_OD_API_INPUT_WIDTH, TF_OD_API_INPUT_HEIGHT)
    }

    private var imageListener: Camera.PreviewCallback? = null
    fun setImageListener(imageListener: Camera.PreviewCallback) {
        this.imageListener = imageListener
    }

    /**
     * [android.view.TextureView.SurfaceTextureListener] handles several lifecycle events on a
     * [TextureView].
     */
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(
            texture: SurfaceTexture, width: Int, height: Int
        ) {

            val index = cameraId
            camera = Camera.open(index)

            try {
                val parameters = camera!!.parameters
                val focusModes = parameters.supportedFocusModes
                if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                }
                val cameraSizes = parameters.supportedPreviewSizes
                val sizes = arrayOfNulls<Size>(cameraSizes.size)
                var i = 0
                for (size in cameraSizes) {
                    sizes[i++] = Size(size.width, size.height)
                }
                val previewSize =
                    CameraConnectionFragment.chooseOptimalSize(
                        sizes, desiredSize.width, desiredSize.height
                    )
                parameters.setPreviewSize(previewSize.width, previewSize.height)
                camera!!.setDisplayOrientation(90)
                camera!!.parameters = parameters
                camera!!.setPreviewTexture(texture)
            } catch (exception: IOException) {
                camera!!.release()
            }

            camera!!.setPreviewCallbackWithBuffer(imageListener)
            val s = camera!!.parameters.previewSize
            camera!!.addCallbackBuffer(
                ByteArray(
                    ImageUtils.getYUVByteSize(
                        s.height,
                        s.width
                    )
                )
            )

            textureView!!.setAspectRatio(s.height, s.width)

            camera!!.startPreview()
        }

        override fun onSurfaceTextureSizeChanged(
            texture: SurfaceTexture, width: Int, height: Int
        ) {
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
    }

    /**
     * An [AutoFitTextureView] for camera preview.
     */
    private var textureView: AutoFitTextureView? = null

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private var backgroundThread: HandlerThread? = null

    private// No camera found
    val cameraId: Int
        get() {
            val ci = Camera.CameraInfo()
            for (i in 0 until Camera.getNumberOfCameras()) {
                Camera.getCameraInfo(i, ci)
                if (ci.facing == Camera.CameraInfo.CAMERA_FACING_BACK)
                    return i
            }
            return -1
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.camera_connection_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        textureView = view.findViewById(R.id.texture) as AutoFitTextureView
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).

        if (textureView!!.isAvailable) {
            camera!!.startPreview()
        } else {
            textureView!!.surfaceTextureListener = surfaceTextureListener
        }
    }

    override fun onPause() {
        stopCamera()
        stopBackgroundThread()
        super.onPause()
    }

    /**
     * Starts a background thread and its [Handler].
     */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground")
        backgroundThread!!.start()
    }

    /**
     * Stops the background thread and its [Handler].
     */
    private fun stopBackgroundThread() {
        backgroundThread!!.quitSafely()
        try {
            backgroundThread!!.join()
            backgroundThread = null
        } catch (e: InterruptedException) {
            LOGGER.e(TAG, "Exception! : $e")
        }

    }

    private fun stopCamera() {
        if (camera != null) {
            camera!!.stopPreview()
            camera!!.setPreviewCallback(null)
            camera!!.release()
            camera = null
        }
    }

    companion object {
        /**
         * Conversion from screen rotation to JPEG orientation.
         */
        private val ORIENTATIONS = SparseIntArray()

        init {

            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }
    }
}
