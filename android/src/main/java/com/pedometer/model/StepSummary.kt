package com.pedometer.model

data class StepSummary(
  val startTime: Long,
  val endTime: Long,
  val stepCount: Int
) {
  init {
    require(endTime >= startTime) { "End time must be after or equal to start time" }
    require(stepCount >= 0) { "Step count cannot be negative" }
  }
}
