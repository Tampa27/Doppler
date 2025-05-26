package com.weiner.recordaudio

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.amg.dopplerultrasound.data.Paciente
import com.amg.dopplerultrasound.data.Converters
import com.amg.dopplerultrasound.data.PacienteDao

@Database(entities = [Paciente::class], version = 1, exportSchema = false) // Incrementa la versión si cambias el esquema
@TypeConverters(Converters::class) // Registra tus TypeConverters
abstract class AppDatabase : RoomDatabase() {

    abstract fun pacienteDao(): PacienteDao

    companion object {
        @Volatile // Asegura que el valor de INSTANCE sea siempre actualizado y visible para todos los hilos
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "paciente_database" // Nombre del archivo de la base de datos
                )
                    // .fallbackToDestructiveMigration() // Si no quieres manejar migraciones complejas (para desarrollo)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}