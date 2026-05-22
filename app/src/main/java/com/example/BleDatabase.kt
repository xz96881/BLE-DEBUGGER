package com.example

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Database
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "command_presets")
data class CommandPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val payload: String,
    val isHex: Boolean
) {
    fun toCommandPreset(): CommandPreset = CommandPreset(label = label, payload = payload, isHex = isHex)
}

@Dao
interface CommandPresetDao {
    @Query("SELECT * FROM command_presets ORDER BY id ASC")
    fun getAllPresets(): Flow<List<CommandPresetEntity>>

    @Insert
    suspend fun insertPreset(preset: CommandPresetEntity)

    @Delete
    suspend fun deletePreset(preset: CommandPresetEntity)

    @Query("DELETE FROM command_presets WHERE label = :label AND payload = :payload AND isHex = :isHex")
    suspend fun deletePresetByFields(label: String, payload: String, isHex: Boolean)
}

@Database(entities = [CommandPresetEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun commandPresetDao(): CommandPresetDao
}
