package com.example.mycompose.shepherdai.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AnimalDao {
    @Query("SELECT * FROM animals ORDER BY tag") fun animals(): Flow<List<Animal>>
    @Query("SELECT * FROM animals") suspend fun animalsOnce(): List<Animal>
    @Query("SELECT * FROM animals WHERE id = :id") suspend fun animal(id: Long): Animal?
    @Query("SELECT COUNT(*) FROM animals WHERE tag = :tag") suspend fun tagCount(tag: String): Int
    @Insert suspend fun insert(a: Animal): Long
    @Update suspend fun update(a: Animal)
    @Delete suspend fun delete(a: Animal)

    @Insert suspend fun insertImage(i: AnimalImage)
    @Query("SELECT * FROM animal_images WHERE animalId = :id ORDER BY capturedAt DESC")
    fun images(id: Long): Flow<List<AnimalImage>>
    @Query("SELECT path FROM animal_images WHERE animalId = :id") suspend fun imagePaths(id: Long): List<String>

    @Insert suspend fun insertEmbedding(e: AnimalEmbedding)
    @Query("SELECT * FROM animal_embeddings") suspend fun allEmbeddings(): List<AnimalEmbedding>

    @Insert suspend fun insertSighting(s: Sighting)
    @Query("SELECT * FROM sightings WHERE animalId = :id ORDER BY timestamp DESC")
    fun sightings(id: Long): Flow<List<Sighting>>
}

@Database(
    entities = [Animal::class, AnimalImage::class, AnimalEmbedding::class, Sighting::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AnimalDao

    companion object {
        @Volatile private var inst: AppDatabase? = null
        fun get(ctx: Context): AppDatabase = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "shepherd.db")
                .build().also { inst = it }
        }
    }
}
