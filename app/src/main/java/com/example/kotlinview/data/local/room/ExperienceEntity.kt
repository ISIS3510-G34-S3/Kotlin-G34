package com.example.kotlinview.data.local.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.kotlinview.data.map.ExperienceDtoMap

@Entity(
    tableName = "experiences",
    indices = [
        Index("department"),
        Index("hostId"),
        Index(value = ["department", "hostVerified"]),
        Index("avgRating")
    ]
)
data class ExperienceEntity(
    @PrimaryKey val id: String,
    val title: String,
    val department: String,
    val avgRating: Double,
    val reviewsCount: Int,
    val hostVerified: Boolean,
    val hostId: String,
    val hostName: String?,
    val latitude: Double,
    val longitude: Double,
    val skillsToLearn: List<String>,
    val skillsToTeach: List<String>,
    val images: List<String>,
    val priceCOP: Long,
    val duration: Int
) {
    fun toDto(): com.example.kotlinview.data.map.ExperienceDtoMap =
        ExperienceDtoMap(
            id = id,
            title = title,
            department = department,
            avgRating = avgRating,
            reviewsCount = reviewsCount,
            hostVerified = hostVerified,
            hostId = hostId,
            hostName = hostName ?: "",
            latitude = latitude,
            longitude = longitude,
            skillsToLearn = skillsToLearn,
            skillsToTeach = skillsToTeach,
            images = images,
            priceCOP = priceCOP,
            duration = duration
        )

    companion object {
        fun fromDto(dto: ExperienceDtoMap): ExperienceEntity =
            ExperienceEntity(
                id = dto.id ?: "",
                title = dto.title ?: "",
                department = dto.department ?: "",
                avgRating = dto.avgRating ?: 0.0,
                reviewsCount = dto.reviewsCount ?: 0,
                hostVerified = dto.hostVerified ?: false,
                hostId = dto.hostId ?: "",
                hostName = dto.hostName,
                latitude = dto.latitude ?: 0.0,
                longitude = dto.longitude ?: 0.0,
                skillsToLearn = dto.skillsToLearn ?: emptyList(),
                skillsToTeach = dto.skillsToTeach ?: emptyList(),
                images = dto.images ?: emptyList(),
                priceCOP = dto.priceCOP ?: 0L,
                duration = dto.duration ?: 0
            )
    }
}
