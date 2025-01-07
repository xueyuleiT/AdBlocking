package com.ps.gkd.data

import kotlinx.serialization.Serializable

@Serializable
data class TakePositionEvent(val snapshotId: Long, var position: RawSubscription.Position)
