package com.sakayori.domain.data.model.home

import com.sakayori.domain.data.model.home.chart.Chart
import com.sakayori.domain.data.model.mood.Mood
import com.sakayori.domain.utils.Resource

data class HomeResponse(
    val homeItem: Resource<ArrayList<HomeItem>>,
    val exploreMood: Resource<Mood>,
    val exploreChart: Resource<Chart>,
)
