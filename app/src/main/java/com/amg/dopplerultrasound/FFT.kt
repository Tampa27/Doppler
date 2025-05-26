package com.amg.dopplerultrasound

class FFT(private val frequenciesCount: Int) {
    private val m = (Math.log(frequenciesCount.toDouble()) / Math.log(2.0)).toInt()

    // Lookup tables. Only need to recompute when size of FFT changes.
    private var cos: DoubleArray
    private var sin: DoubleArray

    init {
        // Make sure n is a power of 2
        if (frequenciesCount != 1 shl m) throw RuntimeException("FFT length must be power of 2")

        // precompute tables
        cos = DoubleArray(frequenciesCount / 2)
        sin = DoubleArray(frequenciesCount / 2)
        for (i in 0 until frequenciesCount / 2) {
            cos[i] = Math.cos(-2 * Math.PI * i / frequenciesCount)
            sin[i] = Math.sin(-2 * Math.PI * i / frequenciesCount)
        }
    }

    fun process(x: DoubleArray, y: DoubleArray) {
        var j: Int
        var k: Int
        var n1: Int
        var a: Int
        var c: Double
        var s: Double
        var t1: Double
        var t2: Double

        // Bit-reverse
        j = 0
        var n2 = frequenciesCount / 2
        var i = 1
        while (i < frequenciesCount - 1) {
            n1 = n2
            while (j >= n1) {
                j -= n1
                n1 /= 2
            }
            j += n1
            if (i < j) {
                t1 = x[i]
                x[i] = x[j]
                x[j] = t1
                t1 = y[i]
                y[i] = y[j]
                y[j] = t1
            }
            i++
        }

        // FFT
        n2 = 1
        i = 0
        while (i < m) {
            n1 = n2
            n2 += n2
            a = 0
            j = 0
            while (j < n1) {
                c = cos[a]
                s = sin[a]
                a += 1 shl m - i - 1
                k = j
                while (k < frequenciesCount) {
                    t1 = c * x[k + n1] - s * y[k + n1]
                    t2 = s * x[k + n1] + c * y[k + n1]
                    x[k + n1] = x[k] - t1
                    y[k + n1] = y[k] - t2
                    x[k] = x[k] + t1
                    y[k] = y[k] + t2
                    k += n2
                }
                j++
            }
            i++
        }
    }

    fun hanningWindow(x: DoubleArray, y: DoubleArray){

    }

    fun hammingWindow(){

    }
}
