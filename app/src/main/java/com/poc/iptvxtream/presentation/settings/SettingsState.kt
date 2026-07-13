package com.poc.iptvxtream.presentation.settings

import com.poc.iptvxtream.data.local.storage.CategorySorting
import com.poc.iptvxtream.data.local.storage.SyncFrequency

data class SettingsState(
    val tvSorting: CategorySorting = CategorySorting.DEFAULT,
    val vodSorting: CategorySorting = CategorySorting.DEFAULT,
    val seriesSorting: CategorySorting = CategorySorting.DEFAULT,
    val syncFrequency: SyncFrequency = SyncFrequency.DISABLED
)
