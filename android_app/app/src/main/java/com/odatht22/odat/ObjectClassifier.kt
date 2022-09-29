/**package com.odatht22.odat

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class ObjectClassifier(private val context: Context) {

    private val executorService: ExecutorService = Executors.newCachedThreadPool()


    private var interpreter: Interpreter? = null

    private var inputImageWidth: Int = 0 // will be inferred from TF Lite model.
    private var inputImageHeight: Int = 0 // will be inferred from TF Lite model.
    private var modelInputSize: Int = 0 // will be inferred from TF Lite model.




    private fun initializeInterpreter(){

        val assetManager = context.assets
        val model = loadModelFile(assetManager, "detect.tflite")
        val interpreter = Interpreter(model)
        val inputShape = interpreter.getInputTensor(0).shape()
        this.inputImageWidth = inputShape[1]
        this.inputImageHeight = inputShape[2]
        this.modelInputSize = FLOAT_TYPE_SIZE * this.inputImageHeight * this.inputImageWidth * PIXEL_SIZE
        this.interpreter = interpreter

    }

    fun classifyAsync(bitmap: Bitmap): Task<String> {
        val task = TaskCompletionSource<String>()
        executorService.execute {
            val result = classify(bitmap)
            task.setResult(result)

            Log.i(TAG, "Result on inference: $result")
        }


        return task.task
    }

    @Throws(IOException::class)
    private fun loadModelFile(assetManager: AssetManager, filename: String): ByteBuffer {
        val fileDescriptor = assetManager.openFd(filename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }


    fun close() {
        executorService.execute {
            this.interpreter?.close()


            Log.d(TAG, "Closed TFLite interpreter.")
        }
    }

    private fun classify(bitmap: Bitmap): String {

        val resizedImage =
            Bitmap.createScaledBitmap(bitmap, this.inputImageWidth, this.inputImageHeight, true)
        val byteBuffer = convertBitmapToByteBuffer(resizedImage)

        val output = Array(1) { FloatArray(91) }
        interpreter?.run(byteBuffer, output)
        val result = output[1]
        val maxIndex = result.indices.maxBy { result[it] } ?: -1

        return "Prediction Result: %d\nConfidence: %2f".format(maxIndex, result[maxIndex])


    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(modelInputSize)
        byteBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputImageWidth * inputImageHeight)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (pixelValue in pixels) {
            val r = (pixelValue shr 16 and 0xFF)
            val g = (pixelValue shr 8 and 0xFF)
            val b = (pixelValue and 0xFF)

            // Convert RGB to grayscale and normalize pixel value to [0..1].
            val normalizedPixelValue = (r + g + b) / 3.0f / 255.0f
            byteBuffer.putFloat(normalizedPixelValue)
        }

        return byteBuffer
    }

    companion object {
        private const val TAG = "ObjectClassifier"

        private const val FLOAT_TYPE_SIZE = 4
        private const val PIXEL_SIZE = 3

        private const val OUTPUT_CLASSES_COUNT = 10
    }

}

 */