package com.amg.dopplerultrasound

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.amg.dopplerultrasound.data.Paciente
import com.amg.dopplerultrasound.data.PacienteDao
import com.amg.dopplerultrasound.databinding.ActivityPatientsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.weiner.recordaudio.AppDatabase

class PatientsActivity : AppCompatActivity() {

    lateinit var database: AppDatabase
    lateinit var pacienteDao: PacienteDao
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patients)

//        database = AppDatabase.getDatabase(applicationContext) // O el contexto apropiado
  //      pacienteDao = database.pacienteDao()

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.patients_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == R.id.action_add) {
            mostrarDialogoAgregarPaciente()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun mostrarDialogoAgregarPaciente() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_patient, null)

        val editTextNombre = dialogView.findViewById<EditText>(R.id.editTextNombre)
        val editTextEdad = dialogView.findViewById<EditText>(R.id.editTextEdad)
        val radioGroupSexo = dialogView.findViewById<RadioGroup>(R.id.radioGroupSexo)
        val editTextDna = dialogView.findViewById<EditText>(R.id.editTextDna)
        val buttonCancelar = dialogView.findViewById<Button>(R.id.buttonCancelar)
        val buttonGuardar = dialogView.findViewById<Button>(R.id.buttonGuardar)
        val dialog = MaterialAlertDialogBuilder(this) // Usar MaterialAlertDialogBuilder para estilo Material
            .setView(dialogView)
            .setTitle("Nuevo Paciente") // También puedes quitar el TextView del layout y usar este título
            .setCancelable(false) // Opcional: para que no se cierre al tocar fuera
            //.setPositiveButton("Guardar", null) // Se sobrescribe el listener más abajo para validación
            //.setNegativeButton("Cancelar", null)
            .create()

        buttonGuardar.setOnClickListener {
            val nombre = editTextNombre.text.toString().trim()
            val edadStr = editTextEdad.text.toString().trim()
            val dna = editTextDna.text.toString().trim()

            val selectedSexoId = radioGroupSexo.checkedRadioButtonId
            val radioButtonSelected = dialogView.findViewById<RadioButton>(selectedSexoId)
            val sexo = radioButtonSelected?.text?.toString() ?: "" // Obtener el texto del RadioButton seleccionado

            var isValid = true

            if (nombre.isEmpty()) {
                editTextNombre.error = "El nombre es obligatorio"
                isValid = false
            } else {
                editTextNombre.error = null
            }

            if (dna.isEmpty()) {
                editTextDna.error = "El CI es obligatorio"
                isValid = false
            } else {
                editTextDna.error = null
            }

            var edad = 0
            if (edadStr.isEmpty()) {
                editTextEdad.error = "La edad es obligatoria"
                isValid = false
            } else {
                try {
                    edad = edadStr.toInt()
                    if (edad <= 0) {
                        editTextEdad.error = "La edad debe ser mayor a 0"
                        isValid = false
                    } else {
                        //editTextEdad.error = null
                    }
                } catch (e: NumberFormatException) {
                    editTextEdad.error = "Introduce un número válido para la edad"
                    isValid = false
                }
            }

            if (sexo.isEmpty()) {
                // Deberías manejar esto, aunque un RadioButton usualmente está preseleccionado
                // Podrías mostrar un Toast o marcar el RadioGroup de alguna forma
                isValid = false
            }

            if (isValid) {
                // Crear el objeto Paciente (inicialmente con listas vacías para imágenes/audios)
                val nuevoPaciente = Paciente(
                    // id se autogenerará por Room
                    nombre = nombre,
                    edad = edad,
                    sexo = sexo,
                    dna = dna, // Si está vacío, se guardará como string vacío
                    imagenes = mutableListOf(), // Manejar la adición de imágenes/audios después
                    audios = mutableListOf()
                )

                // Aquí llamas a tu método del ViewModel o directamente al DAO para guardar el paciente
                // Ejemplo (necesitarás un CoroutineScope):
                // lifecycleScope.launch {
                //     pacienteDao.insertarPaciente(nuevoPaciente)
                //     // Mostrar un Toast o mensaje de éxito
                //     Toast.makeText(this@PatientsActivity, "Paciente guardado", Toast.LENGTH_SHORT).show()
                // }

                println("Paciente a guardar: $nuevoPaciente") // Para depuración
                dialog.dismiss() // Cerrar el diálogo después de guardar
            }
        }

        buttonCancelar.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

}