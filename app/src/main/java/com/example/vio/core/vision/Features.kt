package com.example.vio.core.vision

import com.example.vio.core.model.FeatureSet
import com.example.vio.core.model.Keypoint
import com.example.vio.core.model.MapFormatException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

object DescriptorLayout {
    const val GRID_COLS = 6
    const val GRID_ROWS = 4
    const val BINS = 8
    const val GLOBAL_BYTES = GRID_COLS * GRID_ROWS * BINS
    const val BRIEF_BYTES = 32
    const val BRIEF_BITS = BRIEF_BYTES * 8
    const val MAX_KEYPOINTS = 48
}

object DescriptorMath {
    fun hamming(left: ByteArray, right: ByteArray): Int {
        if (left.size != right.size) return Int.MAX_VALUE / 4
        var count = 0
        for (index in left.indices) {
            count += Integer.bitCount((left[index].toInt() xor right[index].toInt()) and 0xFF)
        }
        return count
    }

    fun similarity(left: ByteArray, right: ByteArray): Float {
        if (left.size != right.size || left.isEmpty()) return 0f
        var distance = 0
        for (index in left.indices) {
            distance += abs((left[index].toInt() and 0xFF) - (right[index].toInt() and 0xFF))
        }
        val maxDistance = left.size * 15
        return (1f - distance.toFloat() / maxDistance.toFloat()).coerceIn(0f, 1f)
    }
}

object FeatureExtractor {
    private const val PROCESS_WIDTH = 160

    fun extract(source: ImageBuffer): FeatureSet {
        val scaled = if (source.width > PROCESS_WIDTH) source.downscaleMaxWidth(PROCESS_WIDTH) else source
        val image = scaled.neutralize()
        val global = globalDescriptor(image)
        val points = interestPoints(image)
        val keypoints = points.map { (x, y) ->
            val angle = orientation(image, x, y)
            Keypoint(x.toFloat(), y.toFloat(), angle, brief(image, x, y, angle))
        }
        return FeatureSet(image.width, image.height, global, keypoints)
    }

    private fun globalDescriptor(image: ImageBuffer): ByteArray {
        val cols = DescriptorLayout.GRID_COLS
        val rows = DescriptorLayout.GRID_ROWS
        val bins = DescriptorLayout.BINS
        val hist = FloatArray(cols * rows * bins)
        val cellWidth = image.width.toFloat() / cols
        val cellHeight = image.height.toFloat() / rows
        for (y in 1 until image.height - 1) {
            for (x in 1 until image.width - 1) {
                val gx = image.get(x + 1, y) - image.get(x - 1, y)
                val gy = image.get(x, y + 1) - image.get(x, y - 1)
                val magnitude = hypot(gx.toFloat(), gy.toFloat())
                if (magnitude < 12f) continue
                var angle = atan2(gy.toFloat(), gx.toFloat())
                if (angle < 0f) angle += PI.toFloat()
                val bin = ((angle / PI.toFloat()) * bins).toInt().coerceIn(0, bins - 1)
                val cellX = (x / cellWidth).toInt().coerceIn(0, cols - 1)
                val cellY = (y / cellHeight).toInt().coerceIn(0, rows - 1)
                hist[(cellY * cols + cellX) * bins + bin] += magnitude
            }
        }
        val out = ByteArray(DescriptorLayout.GLOBAL_BYTES)
        val cells = cols * rows
        for (cell in 0 until cells) {
            val base = cell * bins
            var total = 0f
            for (bin in 0 until bins) total += hist[base + bin]
            for (bin in 0 until bins) {
                val normalized = if (total < 1e-3f) 0f else hist[base + bin] / total
                out[base + bin] = (normalized * 15f).roundToInt().coerceIn(0, 15).toByte()
            }
        }
        return out
    }

    private fun interestPoints(image: ImageBuffer): List<Pair<Int, Int>> {
        val found = ArrayList<Pair<Int, Int>>()
        var y = 16
        while (y < image.height - 16) {
            var x = 16
            while (x < image.width - 16) {
                val gx = image.get(x + 1, y) - image.get(x - 1, y)
                val gy = image.get(x, y + 1) - image.get(x, y - 1)
                if (hypot(gx.toFloat(), gy.toFloat()) >= 20f) found += x to y
                x += 12
            }
            y += 12
        }
        if (found.size <= DescriptorLayout.MAX_KEYPOINTS) return found
        val stride = found.size.toFloat() / DescriptorLayout.MAX_KEYPOINTS
        val kept = ArrayList<Pair<Int, Int>>(DescriptorLayout.MAX_KEYPOINTS)
        var cursor = 0f
        while (kept.size < DescriptorLayout.MAX_KEYPOINTS && cursor < found.size) {
            kept += found[cursor.toInt()]
            cursor += stride
        }
        return kept
    }

    private fun orientation(image: ImageBuffer, x: Int, y: Int): Float {
        var momentX = 0.0
        var momentY = 0.0
        for (dy in -8..8) {
            for (dx in -8..8) {
                val value = image.get(x + dx, y + dy).toDouble()
                momentX += dx * value
                momentY += dy * value
            }
        }
        if (abs(momentX) + abs(momentY) < 1.0) return 0f
        return atan2(momentY, momentX).toFloat()
    }

    private fun brief(image: ImageBuffer, x: Int, y: Int, angle: Float): ByteArray {
        val cosA = cos(angle)
        val sinA = sin(angle)
        val out = ByteArray(DescriptorLayout.BRIEF_BYTES)
        for (bit in 0 until DescriptorLayout.BRIEF_BITS) {
            val x1 = ((bit * 17 + 3) % 21) - 10
            val y1 = ((bit * 29 + 11) % 21) - 10
            val x2 = ((bit * 13 + 7) % 21) - 10
            val y2 = ((bit * 23 + 19) % 21) - 10
            val rx1 = x + (cosA * x1 - sinA * y1).roundToInt()
            val ry1 = y + (sinA * x1 + cosA * y1).roundToInt()
            val rx2 = x + (cosA * x2 - sinA * y2).roundToInt()
            val ry2 = y + (sinA * x2 + cosA * y2).roundToInt()
            if (image.get(rx1, ry1) < image.get(rx2, ry2)) {
                val index = bit ushr 3
                out[index] = (out[index].toInt() or (1 shl (bit and 7))).toByte()
            }
        }
        return out
    }
}

object FeatureCodec {
    fun encode(features: FeatureSet): ByteArray {
        val global = features.globalDescriptor
        if (global.size != DescriptorLayout.GLOBAL_BYTES) {
            throw MapFormatException("Unexpected global descriptor length ${global.size}")
        }
        var size = 4 + 1 + 4 + 4 + 4 + global.size + 4
        for (keypoint in features.keypoints) {
            if (keypoint.descriptor.size != DescriptorLayout.BRIEF_BYTES) {
                throw MapFormatException("Unexpected keypoint descriptor length")
            }
            size += 12 + keypoint.descriptor.size
        }
        val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(byteArrayOf('V'.code.toByte(), 'I'.code.toByte(), 'O'.code.toByte(), 'F'.code.toByte()))
        buffer.put(1)
        buffer.putInt(features.width)
        buffer.putInt(features.height)
        buffer.putInt(global.size)
        buffer.put(global)
        buffer.putInt(features.keypoints.size)
        for (keypoint in features.keypoints) {
            buffer.putFloat(keypoint.x)
            buffer.putFloat(keypoint.y)
            buffer.putFloat(keypoint.angle)
            buffer.put(keypoint.descriptor)
        }
        return buffer.array()
    }

    fun decode(bytes: ByteArray): FeatureSet {
        try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(4)
            buffer.get(magic)
            val expected = byteArrayOf('V'.code.toByte(), 'I'.code.toByte(), 'O'.code.toByte(), 'F'.code.toByte())
            if (!magic.contentEquals(expected)) {
                throw MapFormatException("A descriptor file is not a VIO feature blob")
            }
            val version = buffer.get().toInt() and 0xFF
            if (version != 1) throw MapFormatException("Unsupported descriptor version $version")
            val width = buffer.int
            val height = buffer.int
            val globalLength = buffer.int
            if (globalLength != DescriptorLayout.GLOBAL_BYTES) {
                throw MapFormatException("Unexpected global descriptor length")
            }
            val global = ByteArray(globalLength)
            buffer.get(global)
            val count = buffer.int
            if (count !in 0..500) throw MapFormatException("Unreasonable keypoint count")
            val keypoints = ArrayList<Keypoint>(count)
            for (index in 0 until count) {
                val x = buffer.float
                val y = buffer.float
                val angle = buffer.float
                val descriptor = ByteArray(DescriptorLayout.BRIEF_BYTES)
                buffer.get(descriptor)
                keypoints += Keypoint(x, y, angle, descriptor)
            }
            return FeatureSet(width, height, global, keypoints)
        } catch (error: MapFormatException) {
            throw error
        } catch (error: Exception) {
            throw MapFormatException(error.message ?: "Could not read a descriptor file")
        }
    }
}
