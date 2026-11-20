package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CircuitEntity::class], version = 1, exportSchema = false)
abstract class CircuitDatabase : RoomDatabase() {
    abstract fun circuitDao(): CircuitDao

    companion object {
        @Volatile
        private var INSTANCE: CircuitDatabase? = null

        fun getInstance(context: Context): CircuitDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CircuitDatabase::class.java,
                    "voltlab_circuits.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
