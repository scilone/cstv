package com.cstv.app.di

object IptvLog {
    fun d(tag: String, message: String) {
        try {
            android.util.Log.d(tag, message)
        } catch (e: RuntimeException) {
            println("[$tag] $message")
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        try {
            android.util.Log.e(tag, message, throwable)
        } catch (e: RuntimeException) {
            println("[$tag] $message")
            throwable?.printStackTrace()
        }
    }

    fun w(tag: String, message: String) {
        try {
            android.util.Log.w(tag, message)
        } catch (e: RuntimeException) {
            println("[$tag] $message")
        }
    }
}
