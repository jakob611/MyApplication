package com.example.myapplication.domain

/**
 * KMP-friendly Logger.
 * V prihodnosti bo to povezano z io.github.aakira:napier ali Kermit.
 */
object Logger {
    fun d(tag: String, message: String) {
        // Trenutno preusmerimo na println ali standardni system out, da izkljućimo android.util.Log odvisnost.
        println("DEBUG [$tag]: $message")
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        println("ERROR [$tag]: $message")
        throwable?.printStackTrace()
    }

    fun w(tag: String, message: String) {
        println("WARN [$tag]: $message")
    }

    fun i(tag: String, message: String) {
        println("INFO [$tag]: $message")
    }
}

