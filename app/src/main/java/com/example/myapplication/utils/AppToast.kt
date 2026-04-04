package com.example.myapplication.utils
import android.content.Context
import android.widget.Toast
object AppToast {
    fun showSuccess(context: Context, message: String) {
        Toast.makeText(context, "✅ $message", Toast.LENGTH_SHORT).show()
    }
    fun showError(context: Context, message: String) {
        Toast.makeText(context, "❌ $message", Toast.LENGTH_LONG).show()
    }
    fun showInfo(context: Context, message: String) {
        Toast.makeText(context, "ℹ️ $message", Toast.LENGTH_SHORT).show()
    }
    fun showWarning(context: Context, message: String) {
        Toast.makeText(context, "⚠️ $message", Toast.LENGTH_SHORT).show()
    }
}
