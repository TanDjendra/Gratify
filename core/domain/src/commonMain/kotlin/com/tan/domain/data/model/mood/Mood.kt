package com.tan.domain.data.model.mood

@kotlinx.serialization.Serializable
data class Mood(
    val genres: ArrayList<Genre>,
    val moodsMoments: ArrayList<MoodsMoment>,
)
