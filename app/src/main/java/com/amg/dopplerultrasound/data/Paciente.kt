package com.amg.dopplerultrasound.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pacientes")
data class Paciente(
    @PrimaryKey(autoGenerate = true) // Define la clave primaria y la autogenera
    var id: Long = 0,
    val nombre: String,
    val edad: Int,
    val sexo: String,
    val dna:String,
    val imagenes:MutableList<String>,
    val audios:MutableList<String>
)
