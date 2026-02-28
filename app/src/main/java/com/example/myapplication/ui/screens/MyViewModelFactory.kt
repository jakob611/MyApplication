package com.example.myapplication.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MyViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BodyOverviewViewmodel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BodyOverviewViewmodel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}