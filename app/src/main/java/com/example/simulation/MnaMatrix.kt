package com.example.simulation

import kotlin.math.abs

/**
 * Reusable dense linear system solver Ax = b using Gaussian elimination with partial pivoting.
 * Uses primitive DoubleArrays to minimize GC pressure during 60fps simulation.
 */
class MnaMatrix(initialSize: Int = 16) {
    var size: Int = initialSize
        private set

    var a = DoubleArray(initialSize * initialSize)
        private set
    var b = DoubleArray(initialSize)
        private set
    var x = DoubleArray(initialSize)
        private set

    private var lu = DoubleArray(initialSize * initialSize)
    private var rhs = DoubleArray(initialSize)

    fun reset(newSize: Int) {
        size = newSize
        val neededSq = newSize * newSize
        if (a.size < neededSq) {
            a = DoubleArray(neededSq)
            lu = DoubleArray(neededSq)
        } else {
            a.fill(0.0, 0, neededSq)
        }
        if (b.size < newSize) {
            b = DoubleArray(newSize)
            x = DoubleArray(newSize)
            rhs = DoubleArray(newSize)
        } else {
            b.fill(0.0, 0, newSize)
        }
    }

    fun addA(row: Int, col: Int, value: Double) {
        if (row < 0 || col < 0 || row >= size || col >= size) return
        a[row * size + col] += value
    }

    fun addB(row: Int, value: Double) {
        if (row < 0 || row >= size) return
        b[row] += value
    }

    fun stampConductance(n1: Int, n2: Int, g: Double) {
        if (g == 0.0) return
        if (n1 > 0) addA(n1 - 1, n1 - 1, g)
        if (n2 > 0) addA(n2 - 1, n2 - 1, g)
        if (n1 > 0 && n2 > 0) {
            addA(n1 - 1, n2 - 1, -g)
            addA(n2 - 1, n1 - 1, -g)
        }
    }

    fun stampCurrentSource(nPlus: Int, nMinus: Int, currentIntoMinusFromPlus: Double) {
        // Passive convention: current I leaves nPlus and enters nMinus (or source pushes I from nMinus to nPlus)
        // Here: current I flows from nPlus -> nMinus externally, so leaves nPlus (-I in KCL RHS) and enters nMinus (+I)
        if (nPlus > 0) addB(nPlus - 1, -currentIntoMinusFromPlus)
        if (nMinus > 0) addB(nMinus - 1, currentIntoMinusFromPlus)
    }

    fun solve(): Boolean {
        val n = size
        if (n == 0) return true

        System.arraycopy(a, 0, lu, 0, n * n)
        System.arraycopy(b, 0, rhs, 0, n)

        for (k in 0 until n) {
            var pivotRow = k
            var maxVal = abs(lu[k * n + k])
            for (i in k + 1 until n) {
                val v = abs(lu[i * n + k])
                if (v > maxVal) {
                    maxVal = v
                    pivotRow = i
                }
            }

            if (pivotRow != k) {
                for (j in k until n) {
                    val tmp = lu[k * n + j]
                    lu[k * n + j] = lu[pivotRow * n + j]
                    lu[pivotRow * n + j] = tmp
                }
                val tmpB = rhs[k]
                rhs[k] = rhs[pivotRow]
                rhs[pivotRow] = tmpB
            }

            var pivot = lu[k * n + k]
            if (abs(pivot) < 1e-15) {
                // Tiny diagonal regularization for floating nodes
                pivot = if (pivot >= 0.0) 1e-12 else -1e-12
                lu[k * n + k] = pivot
            }

            val invPivot = 1.0 / pivot
            for (i in k + 1 until n) {
                val factor = lu[i * n + k] * invPivot
                if (factor != 0.0) {
                    for (j in k + 1 until n) {
                        lu[i * n + j] -= factor * lu[k * n + j]
                    }
                    rhs[i] -= factor * rhs[k]
                }
            }
        }

        for (i in n - 1 downTo 0) {
            var sum = rhs[i]
            for (j in i + 1 until n) {
                sum -= lu[i * n + j] * x[j]
            }
            val diag = lu[i * n + i]
            val res = if (abs(diag) < 1e-15) 0.0 else sum / diag
            if (res.isNaN() || res.isInfinite()) {
                x.fill(0.0, 0, n)
                return false
            }
            x[i] = res
        }
        return true
    }
}
