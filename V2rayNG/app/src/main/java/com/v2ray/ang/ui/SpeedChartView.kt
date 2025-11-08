package com.v2ray.ang.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.v2ray.ang.R
import java.util.LinkedList

class SpeedChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val uploadData = LinkedList<Float>()
    private val downloadData = LinkedList<Float>()
    private val maxDataPoints = 50
    private var maxSpeed = 1024f * 1024f // 1 MB/s as default max

    private val uploadPaint = Paint().apply {
        color = Color.parseColor("#FF5722")
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val downloadPaint = Paint().apply {
        color = Color.parseColor("#4CAF50")
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val gridPaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 1f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val uploadPath = Path()
    private val downloadPath = Path()

    init {
        // Initialize with zeros
        for (i in 0 until maxDataPoints) {
            uploadData.add(0f)
            downloadData.add(0f)
        }
    }

    fun addData(uploadSpeed: Long, downloadSpeed: Long) {
        if (uploadData.size >= maxDataPoints) {
            uploadData.removeFirst()
            downloadData.removeFirst()
        }

        uploadData.add(uploadSpeed.toFloat())
        downloadData.add(downloadSpeed.toFloat())

        // Update max speed for scaling
        val currentMax = maxOf(uploadSpeed.toFloat(), downloadSpeed.toFloat())
        if (currentMax > maxSpeed) {
            maxSpeed = currentMax
        } else if (currentMax < maxSpeed / 2) {
            // Gradually decrease max if current speeds are much lower
            maxSpeed = maxSpeed * 0.9f
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val padding = 20f

        // Draw grid
        for (i in 0..4) {
            val y = padding + (height - 2 * padding) * i / 4
            canvas.drawLine(padding, y, width - padding, y, gridPaint)
        }

        if (uploadData.isEmpty() || downloadData.isEmpty()) return

        val stepX = (width - 2 * padding) / (maxDataPoints - 1)

        // Draw upload line
        uploadPath.reset()
        uploadData.forEachIndexed { index, speed ->
            val x = padding + stepX * index
            val y = height - padding - (speed / maxSpeed) * (height - 2 * padding)
            if (index == 0) {
                uploadPath.moveTo(x, y)
            } else {
                uploadPath.lineTo(x, y)
            }
        }
        canvas.drawPath(uploadPath, uploadPaint)

        // Draw download line
        downloadPath.reset()
        downloadData.forEachIndexed { index, speed ->
            val x = padding + stepX * index
            val y = height - padding - (speed / maxSpeed) * (height - 2 * padding)
            if (index == 0) {
                downloadPath.moveTo(x, y)
            } else {
                downloadPath.lineTo(x, y)
            }
        }
        canvas.drawPath(downloadPath, downloadPaint)
    }

    fun clear() {
        uploadData.clear()
        downloadData.clear()
        for (i in 0 until maxDataPoints) {
            uploadData.add(0f)
            downloadData.add(0f)
        }
        maxSpeed = 1024f * 1024f
        invalidate()
    }
}
