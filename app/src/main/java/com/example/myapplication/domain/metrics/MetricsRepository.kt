package com.example.myapplication.domain.metrics
interface MetricsRepository {
    suspend fun saveWeight(uid: String, weightKg: Float, dateStr: String): Result<Unit>
    suspend fun getWeight(uid: String, dateStr: String): Result<Float>
}
