package org.avantari.tensorflowdemo

import android.util.Log


/**
 * Created by Mirza Ahmed Baig on 2019-05-15.
 * Avantari Technologies
 * mirza@avantari.org
 */
object LOGGER {
    var isLoggingEnabled = true
    fun d(TAG: String, msg: String) {
        if (isLoggingEnabled)
            Log.d(TAG, msg)
    }

    fun e(TAG: String, msg: String) {
        if (isLoggingEnabled)
            Log.e(TAG, msg)

    }
    fun i(TAG: String, msg: String) {
        if (isLoggingEnabled)
            Log.i(TAG, msg)
    }

    fun w(TAG: String, msg: String) {
        if (isLoggingEnabled)
            Log.w(TAG, msg)
    }
}