package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class PositionedLine(val text: String, val top: Int, val left: Int)

/**
 * ML Kit 给出的文本块顺序不保证从上到下（下方的小标题、右侧的价格常排在前面），
 * 而解析规则依赖行的上下文，所以按屏幕位置重排
 */
fun linesInReadingOrder(lines: List<PositionedLine>): String =
    lines.sortedWith(compareBy({ it.top }, { it.left })).joinToString("\n") { it.text }

private fun Text.inReadingOrder(): String = linesInReadingOrder(
    textBlocks.flatMap { it.lines }.map { line ->
        PositionedLine(line.text, line.boundingBox?.top ?: 0, line.boundingBox?.left ?: 0)
    }
)

object OcrManager {

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    /**
     * 通过图片 Uri 进行本地 ML Kit 文字识别
     */
    suspend fun recognizeTextFromUri(context: Context, imageUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            try {
                val inputImage = InputImage.fromFilePath(context, imageUri)
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        continuation.resume(Result.success(visionText.inReadingOrder()))
                    }
                    .addOnFailureListener { exception ->
                        continuation.resume(Result.failure(exception))
                    }
            } catch (e: Exception) {
                continuation.resume(Result.failure(e))
            }
        }
    }

    /**
     * 通过 Bitmap 进行本地 ML Kit 文字识别
     */
    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): Result<String> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        continuation.resume(Result.success(visionText.inReadingOrder()))
                    }
                    .addOnFailureListener { exception ->
                        continuation.resume(Result.failure(exception))
                    }
            } catch (e: Exception) {
                continuation.resume(Result.failure(e))
            }
        }
    }
}
