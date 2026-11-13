package com.example.mycompose.shepherdai.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "animals", indices = [Index(value = ["tag"], unique = true)])
data class Animal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tag: String,
    val name: String,
    val type: String, // SHEEP | GOAT
    val gender: String, // FEMALE | MALE
    val approxAge: String,
    val color: String,
    val notes: String,
    val registeredAt: Long,
    val active: Boolean = true
)

@Entity(
    tableName = "animal_images",
    foreignKeys = [ForeignKey(Animal::class, ["id"], ["animalId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("animalId")]
)
data class AnimalImage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val animalId: Long,
    val path: String,
    val capturedAt: Long,
    val verified: Boolean = true
)

@Entity(
    tableName = "animal_embeddings",
    foreignKeys = [ForeignKey(Animal::class, ["id"], ["animalId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("animalId")]
)
data class AnimalEmbedding(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val animalId: Long,
    val modelVersion: String,
    val data: ByteArray,
    val createdAt: Long,
    val verified: Boolean = true
)

@Entity(
    tableName = "sightings",
    foreignKeys = [ForeignKey(Animal::class, ["id"], ["animalId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("animalId")]
)
data class Sighting(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val animalId: Long,
    val timestamp: Long,
    val confidence: Float,
    val imagePath: String
)
