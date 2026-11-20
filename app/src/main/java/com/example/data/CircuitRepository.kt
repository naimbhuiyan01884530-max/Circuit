package com.example.data

import com.example.model.CircuitSchematic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class SavedCircuitSummary(
    val id: String,
    val name: String,
    val description: String,
    val componentCount: Int,
    val isPreset: Boolean,
    val updatedAt: Long,
    val schematicJson: String
)

class CircuitRepository(private val dao: CircuitDao) {

    val allCircuits: Flow<List<SavedCircuitSummary>> = dao.observeAllCircuits().map { list ->
        list.map { entity ->
            SavedCircuitSummary(
                id = entity.id,
                name = entity.name,
                description = entity.description,
                componentCount = entity.componentCount,
                isPreset = entity.isPreset,
                updatedAt = entity.updatedAt,
                schematicJson = entity.schematicJson
            )
        }
    }

    suspend fun ensurePresetsSeeded() {
        if (dao.count() == 0) {
            val now = System.currentTimeMillis()
            val entities = PresetCircuits.allPresets().mapIndexed { index, schematic ->
                CircuitEntity(
                    id = schematic.id,
                    name = schematic.name,
                    description = schematic.description,
                    schematicJson = CircuitJsonMapper.toJson(schematic),
                    componentCount = schematic.components.size,
                    isPreset = true,
                    updatedAt = now - index * 1000L
                )
            }
            dao.upsertAll(entities)
        }
    }

    suspend fun saveCircuit(schematic: CircuitSchematic, isPreset: Boolean = false) {
        val entity = CircuitEntity(
            id = schematic.id,
            name = schematic.name.ifBlank { "Untitled Circuit" },
            description = schematic.description,
            schematicJson = CircuitJsonMapper.toJson(schematic),
            componentCount = schematic.components.size,
            isPreset = isPreset,
            updatedAt = System.currentTimeMillis()
        )
        dao.upsertCircuit(entity)
    }

    suspend fun loadCircuit(id: String): CircuitSchematic? {
        val entity = dao.getCircuitById(id) ?: return null
        return CircuitJsonMapper.fromJson(entity.schematicJson).getOrNull()
    }

    suspend fun deleteCircuit(id: String) {
        dao.deleteById(id)
    }

    suspend fun importFromJson(json: String): Result<CircuitSchematic> {
        val parsed = CircuitJsonMapper.fromJson(json)
        parsed.onSuccess { schematic ->
            val cloned = schematic.copy(
                id = java.util.UUID.randomUUID().toString(),
                name = if (schematic.name.isBlank()) "Imported Circuit" else schematic.name
            )
            saveCircuit(cloned, isPreset = false)
        }
        return parsed
    }
}
