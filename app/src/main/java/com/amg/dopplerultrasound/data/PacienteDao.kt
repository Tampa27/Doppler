package com.amg.dopplerultrasound.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow // Para observabilidad con Coroutines

@Dao
interface PacienteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) // Si insertas un paciente con un ID existente, lo reemplaza
    suspend fun insertarPaciente(paciente: Paciente) // `suspend` para usar con Coroutines

    @Query("SELECT * FROM pacientes WHERE dna = :pacienteDna")
    suspend fun getPacientePorDNA(pacienteDna: String): Paciente?

    @Query("SELECT * FROM pacientes ORDER BY nombre ASC")
    fun getTodosLosPacientes(): Flow<List<Paciente>> // Flow para observar cambios en la lista

    @Query("DELETE FROM pacientes WHERE dna = :pacienteDna")
    suspend fun eliminarPacientePorDna(pacienteDna: String)

    // Puedes añadir más métodos según tus necesidades (actualizar, buscar por nombre, etc.)
}