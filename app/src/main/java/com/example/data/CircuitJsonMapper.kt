package com.example.data

import com.example.model.CircuitSchematic
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object CircuitJsonMapper {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(CircuitSchematic::class.java).indent("  ")

    fun toJson(schematic: CircuitSchematic): String {
        return adapter.toJson(schematic)
    }

    fun fromJson(json: String): Result<CircuitSchematic> = runCatching {
        adapter.fromJson(json) ?: error("Invalid circuit JSON")
    }
}
