package com.example.myapplication.domain.auth

object AuthProvider {
    private var repository: AuthRepository? = null

    fun provide(repo: AuthRepository) {
        repository = repo
    }

    fun get(): AuthRepository {
        return repository ?: throw IllegalStateException("AuthRepository not initialized")
    }
}
