package com.juice.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.juice.core.category.Category

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Place in the order of categories; lower comes first, Main is 0. */
    val position: Int,
    /** Accent ARGB of the category's juice from the palette; null until chosen. */
    val colour: Int? = null,
)

fun CategoryEntity.toCategory() = Category(id, name, colour)
