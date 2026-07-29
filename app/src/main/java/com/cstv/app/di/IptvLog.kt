package com.cstv.app.di

import kotlin.jvm.Volatile

object IptvLog {
    @Volatile
    var logListener: ((level: String, tag: String, message: String, throwable: Throwable?) -> Unit)? = null

    fun d(tag: String, message: String) {
        try {
            android.util.Log.d(tag, message)
        } catch (e: RuntimeException) {
            println("[$tag] $message")
        }
        logListener?.invoke("D", tag, message, null)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        try {
            android.util.Log.e(tag, message, throwable)
        } catch (e: RuntimeException) {
            println("[$tag] $message")
            throwable?.printStackTrace()
        }
        logListener?.invoke("E", tag, message, throwable)
    }

    fun w(tag: String, message: String) {
        try {
            android.util.Log.w(tag, message)
        } catch (e: RuntimeException) {
            println("[$tag] $message")
        }
        logListener?.invoke("W", tag, message, null)
    }
}
