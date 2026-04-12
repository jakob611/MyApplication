package com.example.myapplication.domain.metrics
class SaveWeightUseCase(
    private val repository: MetricsRepository
) {
    suspend fun execute(uid: String, weightKg: Float, dateStr: String): Result<Unit> {
        return repository.saveWeight(uid, weightKg, dateStr)
    }
}
