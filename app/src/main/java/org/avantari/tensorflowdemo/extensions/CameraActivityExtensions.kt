package org.avantari.tensorflowdemo.extensions

import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.media.Image
import android.os.Build
import android.widget.Toast
import org.avantari.tensorflowdemo.LOGGER
import org.avantari.tensorflowdemo.activities.CameraActivity


/**
 * Created by Mirza Ahmed Baig on 2019-05-15.
 * Avantari Technologies
 * mirza@avantari.org
 */

fun CameraActivity.isHardwareLevelSupported(
    characteristics: CameraCharacteristics, requiredLevel: Int
): Boolean {
    val deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)!!
    return if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
        requiredLevel == deviceLevel
    } else requiredLevel <= deviceLevel
    // deviceLevel is not LEGACY, can use numerical sort
}

fun CameraActivity.hasPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(
            PERMISSION_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

fun CameraActivity.requestPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA) || shouldShowRequestPermissionRationale(
                PERMISSION_STORAGE
            )
        ) {
            Toast.makeText(
                this,
                "Camera AND storage permission are required for this demo", Toast.LENGTH_LONG
            ).show()
        }
        requestPermissions(arrayOf(PERMISSION_CAMERA, PERMISSION_STORAGE), PERMISSIONS_REQUEST)
    }
}

fun CameraActivity.fillBytes(planes: Array<Image.Plane>, yuvBytes: Array<ByteArray?>) {
    // Because of the variable row stride it's not possible to know in
    // advance the actual necessary dimensions of the yuv planes.
    for (i in planes.indices) {
        val buffer = planes[i].buffer
        if (yuvBytes[i] == null) {
            LOGGER.d(TAG, "Initializing buffer $i at size ${buffer.capacity()}")
            yuvBytes[i] = ByteArray(buffer.capacity())
        }
        buffer.get(yuvBytes[i])
    }
}