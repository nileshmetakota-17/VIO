package com.example.vio.core.vision

import com.example.vio.core.model.FeatureSet
import com.example.vio.core.model.Keypoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random

data class GeometricResult(
    val matchCount: Int,
    val inlierCount: Int,
    val inlierRatio: Float,
    val rotationDegrees: Float?,
    val consistent: Boolean,
) {
    companion object {
        val NONE = GeometricResult(0, 0, 0f, null, false)
    }
}

class GeometricVerifier {
    fun verify(query: FeatureSet, reference: FeatureSet): GeometricResult {
        if (query.keypoints.size < 4 || reference.keypoints.size < 4) return GeometricResult.NONE
        return ransac(matchMutual(query.keypoints, reference.keypoints))
    }

    private fun matchMutual(query: List<Keypoint>, reference: List<Keypoint>): List<Pair<Keypoint, Keypoint>> {
        val pairs = ArrayList<Pair<Keypoint, Keypoint>>()
        val used = HashSet<Int>()
        for (left in query) {
            var bestIndex = -1
            var bestDistance = Int.MAX_VALUE
            for (index in reference.indices) {
                if (index in used) continue
                val distance = DescriptorMath.hamming(left.descriptor, reference[index].descriptor)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestIndex = index
                }
            }
            if (bestIndex < 0 || bestDistance > 78) continue
            val right = reference[bestIndex]
            var backDistance = Int.MAX_VALUE
            var mutual = false
            for (other in query) {
                val distance = DescriptorMath.hamming(right.descriptor, other.descriptor)
                if (distance < backDistance || (distance == backDistance && other === left)) {
                    backDistance = distance
                    mutual = other === left
                }
            }
            if (mutual) {
                used += bestIndex
                pairs += left to right
            }
        }
        return pairs
    }

    private fun ransac(pairs: List<Pair<Keypoint, Keypoint>>): GeometricResult {
        if (pairs.size < 4) {
            return GeometricResult(pairs.size, 0, 0f, null, false)
        }
        val random = Random(0xC0FFEE)
        var bestInliers = 0
        var bestModel: DoubleArray? = null
        repeat(80) {
            val sample = sampleFour(pairs.size, random) ?: return@repeat
            val src = ArrayList<Pair<Double, Double>>(4)
            val dst = ArrayList<Pair<Double, Double>>(4)
            for (index in sample) {
                val pair = pairs[index]
                src += pair.first.x.toDouble() to pair.first.y.toDouble()
                dst += pair.second.x.toDouble() to pair.second.y.toDouble()
            }
            val model = estimateHomography(src, dst) ?: return@repeat
            val inliers = countInliers(model, pairs)
            if (inliers > bestInliers) {
                bestInliers = inliers
                bestModel = model
            }
        }
        val ratio = bestInliers.toFloat() / pairs.size.toFloat()
        val rotation = bestModel?.let { rotationDegrees(it) }
        val consistent = bestInliers >= MIN_INLIERS && ratio >= MIN_INLIER_RATIO
        return GeometricResult(
            matchCount = pairs.size,
            inlierCount = bestInliers,
            inlierRatio = ratio,
            rotationDegrees = if (consistent) rotation else null,
            consistent = consistent,
        )
    }

    private fun countInliers(model: DoubleArray, pairs: List<Pair<Keypoint, Keypoint>>): Int {
        var count = 0
        for (pair in pairs) {
            val projected = applyHomography(model, pair.first.x.toDouble(), pair.first.y.toDouble()) ?: continue
            val error = hypot(projected.first - pair.second.x, projected.second - pair.second.y)
            if (error <= INLIER_PIXELS) count++
        }
        return count
    }

    private fun sampleFour(size: Int, random: Random): IntArray? {
        if (size < 4) return null
        val picked = IntArray(4)
        val used = HashSet<Int>()
        var filled = 0
        var guard = 0
        while (filled < 4 && guard < 40) {
            val value = random.nextInt(size)
            guard++
            if (used.add(value)) picked[filled++] = value
        }
        return if (filled == 4) picked else null
    }

    companion object {
        const val MIN_INLIERS = 8
        const val MIN_INLIER_RATIO = 0.25f
        private const val INLIER_PIXELS = 4.0
    }
}

internal fun estimateHomography(
    src: List<Pair<Double, Double>>,
    dst: List<Pair<Double, Double>>,
): DoubleArray? {
    if (src.size != 4 || dst.size != 4) return null
    val srcNorm = normalizePoints(src) ?: return null
    val dstNorm = normalizePoints(dst) ?: return null
    val matrix = Array(8) { DoubleArray(8) }
    val values = DoubleArray(8)
    for (index in 0 until 4) {
        val x = srcNorm.points[index].first
        val y = srcNorm.points[index].second
        val u = dstNorm.points[index].first
        val v = dstNorm.points[index].second
        val row = index * 2
        matrix[row][0] = x
        matrix[row][1] = y
        matrix[row][2] = 1.0
        matrix[row][6] = -u * x
        matrix[row][7] = -u * y
        values[row] = u
        matrix[row + 1][3] = x
        matrix[row + 1][4] = y
        matrix[row + 1][5] = 1.0
        matrix[row + 1][6] = -v * x
        matrix[row + 1][7] = -v * y
        values[row + 1] = v
    }
    val solved = solveLinear(matrix, values) ?: return null
    val normalized = doubleArrayOf(
        solved[0], solved[1], solved[2],
        solved[3], solved[4], solved[5],
        solved[6], solved[7], 1.0,
    )
    val full = multiply(invertSimilarity(dstNorm.transform), multiply(normalized, srcNorm.transform))
    val scale = full[8]
    if (abs(scale) < 1e-12) return null
    for (index in full.indices) full[index] /= scale
    if (full.any { !it.isFinite() }) return null
    return full
}

internal fun applyHomography(model: DoubleArray, x: Double, y: Double): Pair<Double, Double>? {
    val weight = model[6] * x + model[7] * y + model[8]
    if (abs(weight) < 1e-12) return null
    val px = (model[0] * x + model[1] * y + model[2]) / weight
    val py = (model[3] * x + model[4] * y + model[5]) / weight
    if (!px.isFinite() || !py.isFinite()) return null
    return px to py
}

internal fun solveLinear(aIn: Array<DoubleArray>, bIn: DoubleArray): DoubleArray? {
    val n = bIn.size
    val a = Array(n) { row ->
        DoubleArray(n + 1) { col -> if (col == n) bIn[row] else aIn[row][col] }
    }
    for (col in 0 until n) {
        var pivot = col
        var best = abs(a[col][col])
        for (row in col + 1 until n) {
            val value = abs(a[row][col])
            if (value > best) {
                best = value
                pivot = row
            }
        }
        if (best < 1e-10) return null
        if (pivot != col) {
            val swap = a[col]
            a[col] = a[pivot]
            a[pivot] = swap
        }
        val diagonal = a[col][col]
        for (cursor in col..n) a[col][cursor] /= diagonal
        for (row in 0 until n) {
            if (row == col) continue
            val factor = a[row][col]
            if (factor == 0.0) continue
            for (cursor in col..n) a[row][cursor] -= factor * a[col][cursor]
        }
    }
    return DoubleArray(n) { a[it][n] }
}

private data class NormalizedPoints(
    val points: List<Pair<Double, Double>>,
    val transform: DoubleArray,
)

private fun normalizePoints(points: List<Pair<Double, Double>>): NormalizedPoints? {
    var meanX = 0.0
    var meanY = 0.0
    for (point in points) {
        meanX += point.first
        meanY += point.second
    }
    meanX /= points.size
    meanY /= points.size
    var meanDistance = 0.0
    for (point in points) meanDistance += hypot(point.first - meanX, point.second - meanY)
    meanDistance /= points.size
    if (meanDistance < 1e-6) return null
    val scale = sqrt(2.0) / meanDistance
    val mapped = points.map { ((it.first - meanX) * scale) to ((it.second - meanY) * scale) }
    val transform = doubleArrayOf(
        scale, 0.0, -scale * meanX,
        0.0, scale, -scale * meanY,
        0.0, 0.0, 1.0,
    )
    return NormalizedPoints(mapped, transform)
}

private fun invertSimilarity(transform: DoubleArray): DoubleArray {
    val scale = transform[0]
    val tx = transform[2]
    val ty = transform[5]
    val inverseScale = 1.0 / scale
    return doubleArrayOf(
        inverseScale, 0.0, -tx * inverseScale,
        0.0, inverseScale, -ty * inverseScale,
        0.0, 0.0, 1.0,
    )
}

private fun multiply(left: DoubleArray, right: DoubleArray): DoubleArray {
    val out = DoubleArray(9)
    for (row in 0..2) {
        for (col in 0..2) {
            var sum = 0.0
            for (k in 0..2) sum += left[row * 3 + k] * right[k * 3 + col]
            out[row * 3 + col] = sum
        }
    }
    return out
}

private fun rotationDegrees(model: DoubleArray): Float? {
    val determinant = model[0] * model[4] - model[1] * model[3]
    if (determinant <= 0.2) return null
    return wrapDegrees(Math.toDegrees(atan2(model[3], model[0])))
}

internal fun wrapDegrees(degrees: Double): Float {
    var value = degrees % 360.0
    if (value > 180.0) value -= 360.0
    if (value <= -180.0) value += 360.0
    return value.toFloat()
}
