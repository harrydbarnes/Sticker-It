package com.stickerit.app.domain

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallClient
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the optional Google Play services module used by ML Kit subject
 * segmentation. Installation is best-effort; the editor still reports the
 * normal ML Kit error if a device cannot download or provide the module.
 */
@Singleton
class SegmentationModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "SegmentationModel"
        private const val MAX_SUPPORTED_ANDROID_API_FOR_ML_KIT = 35
    }

    private val moduleInstallClient: ModuleInstallClient by lazy {
        ModuleInstall.getClient(context.applicationContext)
    }

    /**
     * The same configured client is used for both ModuleInstallClient checks
     * and actual editor inference, avoiding duplicate model clients.
     */
    val subjectSegmenter: SubjectSegmenter by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableForegroundConfidenceMask()
                .enableMultipleSubjects(
                    SubjectSegmenterOptions.SubjectResultOptions.Builder()
                        .enableConfidenceMask()
                        .build()
                )
                .build()
        )
    }

    /**
     * Checks whether the ML Kit module is already available and requests a
     * background install when it is not. This returns immediately and never
     * blocks the first screen while Google Play services handles the request.
     *
     * The Android 16+ path deliberately skips this client because the editor
     * uses its MediaPipe fallback there and must not initialize the ML Kit beta
     * native graph known to crash on those releases.
     */
    fun prefetchIfNeeded() {
        if (!isMlKitPathSupported()) return

        checkAvailability { available ->
            when (available) {
                true -> Log.d(TAG, "ML Kit subject segmentation module is available")
                false -> requestInstall()
                null -> Unit
            }
        }
    }

    /**
     * Reports module availability asynchronously. A null result means that
     * the check is not applicable to the MediaPipe-only Android 16+ path or
     * Google Play services could not answer it, rather than claiming that the
     * model is definitely unavailable.
     */
    fun checkAvailability(onResult: (Boolean?) -> Unit) {
        if (!isMlKitPathSupported()) {
            onResult(null)
            return
        }

        try {
            moduleInstallClient.areModulesAvailable(subjectSegmenter)
                .addOnSuccessListener { response ->
                    onResult(response.areModulesAvailable())
                }
                .addOnFailureListener { error ->
                    Log.w(TAG, "Could not check ML Kit subject segmentation availability", error)
                    onResult(null)
                }
        } catch (error: Exception) {
            Log.w(TAG, "Could not start ML Kit subject segmentation availability check", error)
            onResult(null)
        }
    }

    private fun requestInstall() {
        try {
            val request = ModuleInstallRequest.newBuilder()
                .addApi(subjectSegmenter)
                .build()

            moduleInstallClient.installModules(request)
                .addOnSuccessListener { response ->
                    if (response.areModulesAlreadyInstalled()) {
                        Log.d(TAG, "ML Kit subject segmentation module was already installed")
                    } else {
                        Log.d(TAG, "ML Kit subject segmentation install request accepted")
                    }
                }
                .addOnFailureListener { error ->
                    Log.w(TAG, "Could not request ML Kit subject segmentation module", error)
                }
        } catch (error: Exception) {
            Log.w(TAG, "Could not start ML Kit subject segmentation module request", error)
        }
    }

    private fun isMlKitPathSupported(): Boolean =
        Build.VERSION.SDK_INT <= MAX_SUPPORTED_ANDROID_API_FOR_ML_KIT
}
