package com.scena.campushub.ui

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** 极简后台执行器：单线程串行执行，结果回主线程。 */
object Bg {
    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun <T> run(work: () -> T, onDone: (Result<T>) -> Unit) {
        exec.execute {
            val r = try {
                Result.success(work())
            } catch (t: Throwable) {
                Result.failure(t)
            }
            main.post { onDone(r) }
        }
    }
}
