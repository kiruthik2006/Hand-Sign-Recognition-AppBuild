package com.google.mediapipe.examples.handlandmarker
import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import android.util.Log

class GestureClassifierHelper(context: Context) {

    private val labels: List<String>
    private val interpreter: Interpreter

    init {
        interpreter = Interpreter(loadModelFile(context, "gesture_model.tflite"))
        labels = context.assets.open("labels.txt").bufferedReader().readLines()
    }

    private fun loadModelFile(context: Context, fileName: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(fileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    fun classify(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Pair<String, Float> {
        if (landmarks.size != 21 || labels.isEmpty()) return "Invalid Input" to 0f

        val input = FloatArray(63)
        for ((i, lm) in landmarks.withIndex()) {
            input[i * 3] = lm.x()
            input[i * 3 + 1] = lm.y()
            input[i * 3 + 2] = lm.z()
        }

        val output = Array(1) { FloatArray(labels.size) }

        return try {
            Log.d("GestureDebug", "Input shape: ${input.size}")
            Log.d("GestureDebug", "First 3 values: ${input[0]}, ${input[1]}, ${input[2]}")

            interpreter.run(input, output)

            Log.d("GestureDebug", "Output size: ${output[0].size}")
            val maxIndex = output[0].indices.maxByOrNull { output[0][it] } ?: -1
            Log.d("GestureDebug", "Max index: $maxIndex")

            if (maxIndex !in labels.indices) "Unknown" to 0f
            else labels[maxIndex] to output[0][maxIndex]
        } catch (e: Exception) {
            Log.e("GestureDebug", "Exception during classification: ${e.message}")
            e.printStackTrace()
            "Error" to 0f
        }
    }
}