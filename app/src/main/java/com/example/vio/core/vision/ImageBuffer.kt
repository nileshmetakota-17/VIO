package com.example.vio.core.vision

import com.example.vio.core.model.QualityReport
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class ImageBuffer(
    val width: Int,
    val height: Int,
    val pixels: ByteArray,
) {
    init {
        require(width > 0 && height > 0)
        require(pixels.size == width * height)
    }

    fun get(x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0
        return pixels[y * width + x].toInt() and 0xFF
    }

    fun downscaleMaxWidth(maxWidth: Int): ImageBuffer {
        if (width <= maxWidth) return this
        val newWidth = maxWidth
        val newHeight = max(1, (height.toLong() * newWidth / width).toInt())
        val out = ByteArray(newWidth * newHeight)
        for (y in 0 until newHeight) {
            val sy0 = y * height / newHeight
            val sy1 = min(height, max(sy0 + 1, (y + 1) * height / newHeight))
            for (x in 0 until newWidth) {
                val sx0 = x * width / newWidth
                val sx1 = min(width, max(sx0 + 1, (x + 1) * width / newWidth))
                var sum = 0
                var count = 0
                for (sy in sy0 until sy1) {
                    val row = sy * width
                    for (sx in sx0 until sx1) {
                        sum += pixels[row + sx].toInt() and 0xFF
                        count++
                    }
                }
                out[y * newWidth + x] = (sum / count).toByte()
            }
        }
        return ImageBuffer(newWidth, newHeight, out)
    }

    fun neutralize(): ImageBuffer {
        val radius = (min(width, height) / 12).coerceIn(2, 12)
        val stride = width + 1
        val integral = LongArray(stride * (height + 1))
        for (y in 1..height) {
            var row = 0L
            val source = (y - 1) * width
            for (x in 1..width) {
                row += pixels[source + x - 1].toInt() and 0xFF
                integral[y * stride + x] = integral[(y - 1) * stride + x] + row
            }
        }
        val residual = IntArray(pixels.size)
        for (y in 0 until height) {
            val y0 = (y - radius).coerceAtLeast(0)
            val y1 = (y + radius + 1).coerceAtMost(height)
            for (x in 0 until width) {
                val x0 = (x - radius).coerceAtLeast(0)
                val x1 = (x + radius + 1).coerceAtMost(width)
                val count = (x1 - x0) * (y1 - y0)
                val sum = integral[y1 * stride + x1] - integral[y0 * stride + x1] -
                    integral[y1 * stride + x0] + integral[y0 * stride + x0]
                residual[y * width + x] = (pixels[y * width + x].toInt() and 0xFF) - (sum / count).toInt()
            }
        }
        val sorted = residual.copyOf()
        sorted.sort()
        val low = sorted[(sorted.size * 0.05f).toInt()]
        val high = sorted[(sorted.size * 0.95f).toInt().coerceAtMost(sorted.lastIndex)]
        val span = (high - low).coerceAtLeast(1)
        val out = ByteArray(pixels.size)
        for (index in residual.indices) {
            out[index] = (((residual[index] - low) * 208) / span + 24).coerceIn(0, 255).toByte()
        }
        return ImageBuffer(width, height, out)
    }

    fun brighten(delta: Int): ImageBuffer {
        val out = ByteArray(pixels.size)
        for (index in pixels.indices) {
            val value = (pixels[index].toInt() and 0xFF) + delta
            out[index] = value.coerceIn(0, 255).toByte()
        }
        return ImageBuffer(width, height, out)
    }

    fun translate(dx: Int, dy: Int): ImageBuffer {
        val out = ByteArray(pixels.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val sx = x - dx
                val sy = y - dy
                out[y * width + x] = if (sx in 0 until width && sy in 0 until height) {
                    pixels[sy * width + sx]
                } else {
                    pixels[y * width + x]
                }
            }
        }
        return ImageBuffer(width, height, out)
    }
}

object ImageQuality {
    const val MIN_BRIGHTNESS = 18f
    const val MAX_BRIGHTNESS = 240f
    const val MIN_BLUR_VARIANCE = 20f
    const val MIN_TEXTURE = 6f

    fun assess(image: ImageBuffer): QualityReport {
        val sample = if (image.width > 160) image.downscaleMaxWidth(160) else image
        var sum = 0.0
        for (pixel in sample.pixels) sum += pixel.toInt() and 0xFF
        val brightness = (sum / sample.pixels.size).toFloat()

        var lapSum = 0.0
        var lapSq = 0.0
        var gradSum = 0.0
        var count = 0
        for (y in 1 until sample.height - 1) {
            for (x in 1 until sample.width - 1) {
                val center = sample.get(x, y)
                val lap = 4 * center - sample.get(x - 1, y) - sample.get(x + 1, y) -
                    sample.get(x, y - 1) - sample.get(x, y + 1)
                lapSum += lap
                lapSq += lap.toDouble() * lap
                gradSum += abs(sample.get(x + 1, y) - sample.get(x - 1, y)) +
                    abs(sample.get(x, y + 1) - sample.get(x, y - 1))
                count++
            }
        }
        val meanLap = lapSum / count
        val blurVariance = (lapSq / count - meanLap * meanLap).toFloat()
        val texture = (gradSum / count).toFloat()
        val reasons = ArrayList<String>(3)
        if (brightness < MIN_BRIGHTNESS) reasons += "The frame is too dark."
        if (brightness > MAX_BRIGHTNESS) reasons += "The frame is blown out."
        if (blurVariance < MIN_BLUR_VARIANCE) reasons += "The frame is blurry or has almost no edges."
        if (texture < MIN_TEXTURE) reasons += "The scene does not have enough texture."
        return QualityReport(
            usable = reasons.isEmpty(),
            brightness = brightness,
            blurVariance = blurVariance,
            texture = texture,
            reasons = reasons,
        )
    }
}

object SyntheticScenes {
    fun verticalBars(
        width: Int,
        height: Int,
        period: Int,
        phase: Int = 0,
        low: Int = 28,
        high: Int = 220,
    ): ImageBuffer = pattern(width, height) { x, _ ->
        if (((x + phase) / period) % 2 == 0) high else low
    }

    fun checker(width: Int, height: Int, cell: Int, low: Int = 24, high: Int = 220): ImageBuffer =
        pattern(width, height) { x, y ->
            if ((x / cell + y / cell) % 2 == 0) high else low
        }

    fun rings(
        width: Int,
        height: Int,
        spacing: Int,
        low: Int = 30,
        high: Int = 210,
    ): ImageBuffer = pattern(width, height) { x, y ->
        val radius = hypot((x - width / 2f).toDouble(), (y - height / 2f).toDouble())
        if ((radius.toInt() / spacing) % 2 == 0) high else low
    }

    fun diagonal(width: Int, height: Int, period: Int, low: Int = 20, high: Int = 210): ImageBuffer =
        pattern(width, height) { x, y ->
            if (((x + y) / period) % 2 == 0) high else low
        }

    fun flat(width: Int, height: Int, value: Int): ImageBuffer =
        ImageBuffer(width, height, ByteArray(width * height) { value.coerceIn(0, 255).toByte() })

    private fun pattern(width: Int, height: Int, pixel: (Int, Int) -> Int): ImageBuffer {
        val pixels = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = pixel(x, y).coerceIn(0, 255).toByte()
            }
        }
        return ImageBuffer(width, height, pixels)
    }
}
