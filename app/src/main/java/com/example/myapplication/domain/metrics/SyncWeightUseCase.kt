package com.example.myapplication.domain.metrics
class SyncWeightUseCase(
    private val repository: MetricsRepository
) {
    suspend fun execute(uid: String, dateStr: String): Result<Float> {
        return repository.getWeight(uid, dateStr)
    }
}
