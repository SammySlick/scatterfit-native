package com.scatterbrain.scatterfit.ui

fun isTomorrowCardVisible(hour: Int): Boolean = hour >= 19 || hour < 8
