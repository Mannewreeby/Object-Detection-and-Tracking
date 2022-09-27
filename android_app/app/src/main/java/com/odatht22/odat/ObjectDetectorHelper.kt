
package com.odatht22.odat

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.tflite.client.TfLiteInitializationOptions
import com.google.android.gms.tflite.gpu.support.TfLiteGpu
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.gms.vision.TfLiteVision
import org.tensorflow.lite.task.gms.vision.detector.Detection
import org.tensorflow.lite.task.gms.vision.detector.ObjectDetector

class ObjectDetectorHelper(
    var threshold: Float = 0.5f,
    var numThreads: Int = 2,
    var maxResults: Int = 3,
    var currentDelegate: Int = 0,
    var currentModel: Int = 0,
    val context: Context,
    val objectDetectorListener: DetectorListener
) {

    private val TAG = "ObjectDetectionHelper"

    // For this example this needs to be a var so it can be reset on changes. If the ObjectDetector
    // will not change, a lazy val would be preferable.
    private var objectDetector: ObjectDetector? = null
    private var gpuSupported = false

    init {

        TfLiteGpu.isGpuDelegateAvailable(context).onSuccessTask { gpuAvailable: Boolean ->
            val optionsBuilder =
                TfLiteInitializationOptions.builder()
            if (gpuAvailable) {
                optionsBuilder.setEnableGpuDelegateSupport(true)
            }
            TfLiteVision.initialize(context, optionsBuilder.build())
        }.addOnSuccessListener {
            objectDetectorListener.onInitialized()
        }.addOnFailureListener{
            objectDetectorListener.onError("TfLiteVision failed to initialize: "
                    + it.message)
        }
    }

    fun clearObjectDetector() {
        objectDetector = null
    }

    fun isInitialized():Boolean{
        return TfLiteVision.isInitialized()
    }

    // Initialize the object detector using current settings on the
    // thread that is using it. CPU and NNAPI delegates can be used with detectors
    // that are created on the main thread and used on a background thread, but
    // the GPU delegate needs to be used on the thread that initialized the detector
    fun setupObjectDetector():String {
        if (!TfLiteVision.isInitialized()) {
            Log.e(TAG, "setupObjectDetector: TfLiteVision is not initialized yet")
            return "Error choosing model"
        }

        // Create the base options for the detector using specifies max results and score threshold
        val optionsBuilder =
            ObjectDetector.ObjectDetectorOptions.builder()
                .setScoreThreshold(threshold)
                .setMaxResults(maxResults)

        // Set general detection options, including number of used threads
        val baseOptionsBuilder = BaseOptions.builder().setNumThreads(numThreads)

        // Use the specified hardware for running the model. Default to CPU
        when (currentDelegate) {
            DELEGATE_CPU -> {
                // Default
            }
            DELEGATE_GPU -> {
                if (gpuSupported) {
                    baseOptionsBuilder.useGpu()
                } else {
                    objectDetectorListener.onError("GPU is not supported on this device")
                }
            }
            DELEGATE_NNAPI -> {
                baseOptionsBuilder.useNnapi()
            }
        }

        optionsBuilder.setBaseOptions(baseOptionsBuilder.build())

        // Connect model names to the companion object variables

        val modelName =
            when (currentModel) {
                MODEL_MOBILENETV1 -> "mobilenetv1.tflite"
                MODEL_EFFICIENTDETV0 -> "efficientdet-lite0.tflite"
                MODEL_EFFICIENTDETV1 -> "efficientdet-lite1.tflite"
                MODEL_YOLOV5 -> "lite-model_yolo-v5-tflite_tflite_model_1.tflite"
                else -> "efficientdet-lite0.tflite"
            }

        /*    val modelName = "efficientdet-lite0.tflite"*/

        try {
            objectDetector =
                ObjectDetector.createFromFileAndOptions(context, modelName, optionsBuilder.build())

        } catch (e: Exception) {
            objectDetectorListener.onError(
                "Object detector failed to initialize. See error logs for details"
            )
            Log.e(TAG, "TFLite failed to load model with error: " + e.message)
        }

        return modelName
    }

    fun detect(image: Bitmap) {
        if (!TfLiteVision.isInitialized()) {
            Log.e(TAG, "detect: TfLiteVision is not initialized yet")
            return
        }

        if (objectDetector == null) {
            setupObjectDetector()
        }

        // Inference time is the difference between the system time at the start and finish of the
        // process
        var inferenceTime = SystemClock.uptimeMillis()

        // Create preprocessor for the image.
        // See https://www.tensorflow.org/lite/inference_with_metadata/
        //            lite_support#imageprocessor_architecture
        val imageProcessor = ImageProcessor.Builder().build()

        // Preprocess the image and convert it into a TensorImage for detection.
        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(image))

        val results = objectDetector?.detect(tensorImage)
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime
        objectDetectorListener.onResults(
            results,
            inferenceTime,
            tensorImage.height,
            tensorImage.width)
    }


    // Functions that we set up in the main application loop -> MainActivity
    interface DetectorListener {
        fun onInitialized()
        fun onError(error: String)
        fun onResults(
            results: MutableList<Detection>?,
            inferenceTime: Long,
            imageHeight: Int,
            imageWidth: Int
        )
    }

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DELEGATE_NNAPI = 2
        const val MODEL_MOBILENETV1 = 0
        const val MODEL_EFFICIENTDETV0 = 1
        const val MODEL_EFFICIENTDETV1 = 2
        const val  MODEL_YOLOV5 = 3
    }
}