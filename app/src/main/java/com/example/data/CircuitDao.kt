package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "circuits")
data class CircuitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val schematicJson: String,
    val componentCount: Int,
    val isPreset: Boolean,
    val updatedAt: Long
)

@Dao
interface CircuitDao {
    @Query("SELECT * FROM circuits ORDER BY isPreset ASC, updatedAt DESC")
    fun observeAllCircuits(): Flow<List<CircuitEntity>>

    @Query("SELECT * FROM circuits WHERE id = :id LIMIT 1")
    suspend fun getCircuitById(id: String): CircuitEntity?

    @Query("SELECT COUNT(*) FROM circuits")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCircuit(circuit: CircuitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(circuits: List<CircuitEntity>)

    @Query("DELETE FROM circuits WHERE id = :id")
    suspend fun deleteById(id: String)
}
