package com.sakayori.music.viewModel

import androidx.lifecycle.viewModelScope
import com.sakayori.common.SELECTED_LANGUAGE
import com.sakayori.domain.data.model.mood.moodmoments.MoodsMomentObject
import com.sakayori.domain.manager.DataStoreManager
import com.sakayori.domain.repository.HomeRepository
import com.sakayori.domain.utils.Resource
import com.sakayori.logger.Logger
import com.sakayori.music.viewModel.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class MoodViewModel(
    dataStoreManager: DataStoreManager,
    private val homeRepository: HomeRepository,
) : BaseViewModel() {
    private val _moodsMomentObject: MutableStateFlow<MoodsMomentObject?> = MutableStateFlow(null)
    var moodsMomentObject: StateFlow<MoodsMomentObject?> = _moodsMomentObject
    val loading = MutableStateFlow<Boolean>(false)

    private var regionCode: String? = null
    private var language: String? = null

    init {
        regionCode = runBlocking(Dispatchers.IO) { dataStoreManager.location.first() }
        language = runBlocking(Dispatchers.IO) { dataStoreManager.getString(SELECTED_LANGUAGE).first() }
    }

    fun getMood(params: String) {
        loading.value = true
        viewModelScope.launch {
            homeRepository.getMoodData(params).collect { values ->
                Logger.w("MoodViewModel", "getMood: $values")
                when (values) {
                    is Resource.Success -> {
                        _moodsMomentObject.value = values.data
                    }

                    is Resource.Error -> {
                        _moodsMomentObject.value = null
                    }
                }
            }
            withContext(Dispatchers.Main) {
                loading.value = false
            }
        }
    }
}
