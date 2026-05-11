package com.example.saizeriya.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.saizeriya.order.OrderPipeline
import com.example.saizeriya.order.PipelineResult
import com.example.saizeriya.order.PipelineState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class OrderViewModel(
    private val orderPipeline: OrderPipeline
) : ViewModel() {

    private val _uiState = MutableStateFlow<OrderUiState>(OrderUiState.Input)
    val uiState: StateFlow<OrderUiState> = _uiState

    val pipelineState: StateFlow<PipelineState> = orderPipeline.state

    fun startOrder(qrUrl: String, peopleCount: Int, lat: Double, lon: Double) {
        viewModelScope.launch {
            _uiState.value = OrderUiState.Processing
            val result = orderPipeline.execute(qrUrl, peopleCount, lat, lon)
            _uiState.value = when (result) {
                is PipelineResult.Success -> OrderUiState.Success(result)
                is PipelineResult.Error -> OrderUiState.Error(result.message)
            }
        }
    }

    fun reset() {
        _uiState.value = OrderUiState.Input
    }

    class Factory(private val orderPipeline: OrderPipeline) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(OrderViewModel::class.java)) {
                return OrderViewModel(orderPipeline) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

sealed class OrderUiState {
    data object Input : OrderUiState()
    data object Processing : OrderUiState()
    data class Success(val result: PipelineResult.Success) : OrderUiState()
    data class Error(val message: String) : OrderUiState()
}
