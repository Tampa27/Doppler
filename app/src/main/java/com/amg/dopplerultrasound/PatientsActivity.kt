package com.amg.dopplerultrasound

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.amg.dopplerultrasound.data.Paciente
import com.amg.dopplerultrasound.data.PacienteDao
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.weiner.recordaudio.AppDatabase
import kotlinx.coroutines.launch

class PatientsActivity : AppCompatActivity() {

    lateinit var database: AppDatabase
    lateinit var pacienteDao: PacienteDao

    private lateinit var recyclerView: RecyclerView
    private lateinit var pacienteAdapter: PacienteAdapter
    private val listaDePacientes = mutableListOf<Paciente>() // Lista de ejemplo, idealmente desde ViewModel/Room
    private var pacienteSeleccionado: Paciente? = null // Para rastrear el paciente seleccionado
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patients)

        database = AppDatabase.getDatabase(applicationContext) // O el contexto apropiado
        pacienteDao = database.pacienteDao()

        recyclerView = findViewById(R.id.recyclerViewPacientes)
        val fabAddPatient: FloatingActionButton = findViewById(R.id.fabAddPatient)

        // Configurar el Adapter
        pacienteAdapter = PacienteAdapter(
            onItemClicked = { paciente ->
                //pacienteSeleccionado = paciente // Actualiza el paciente seleccionado en la Activity
                Toast.makeText(this, "Seleccionado: ${paciente.nombre}", Toast.LENGTH_SHORT).show()
                // Aquí puedes habilitar otros botones o acciones basadas en la selección
            },
            onDeleteClicked = { paciente ->
                confirmarEliminacionPaciente(paciente)
            }
        )

        // Configurar el RecyclerView
        recyclerView.adapter = pacienteAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Cargar datos de ejemplo (o desde tu ViewModel/Base de Datos)
        //cargarPacientesDeEjemplo() // Reemplaza esto con tu lógica de carga de datos real
        cargarPacientes()

        fabAddPatient.setOnClickListener {
            mostrarDialogoAgregarPaciente()
        }

    }

    private fun confirmarEliminacionPaciente(paciente: Paciente) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Eliminar Paciente")
            .setMessage("¿Estás seguro de que quieres eliminar a ${paciente.nombre}?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                eliminarPaciente(paciente)
            }
            .show()
    }

    private fun eliminarPaciente(paciente: Paciente) {
        // --- Aquí normalmente interactuarías con tu ViewModel y Room para eliminar ---


        lifecycleScope.launch {
            pacienteDao.eliminarPacientePorDna(paciente.dna)
            // Mostrar un Toast o mensaje de éxito
            Toast.makeText(this@PatientsActivity, "Paciente eliminado", Toast.LENGTH_SHORT).show()
        }
        //listaDePacientes.removeAt(indice)
        //pacienteAdapter.submitList(listaDePacientes.toList()) // Actualiza el RecyclerView

        // Si el paciente eliminado era el seleccionado, deseleccionar
        if (pacienteSeleccionado == paciente) {
            pacienteSeleccionado = null
        // Opcional: podrías querer limpiar la selección visual en el adapter también
        // pacienteAdapter.setSelectedPosition(RecyclerView.NO_POSITION)
        // Aunque al remover el ítem, el adapter se reajustará
        }

    }

    private fun cargarPacientes(){
        lifecycleScope.launch {
            val pacientes = pacienteDao.getTodosLosPacientes()
            pacientes.collect{
                pacienteAdapter.submitList(it)
            }
        }
    }

    private fun cargarPacientesDeEjemplo() {
        // Simulación: En una app real, esto vendría de una base de datos (Room) o una API
        listaDePacientes.add(Paciente(id = 1,"Ana Pérez", 30, "Femenino", "ATGC...", mutableListOf(),
            mutableListOf()
        ))
        listaDePacientes.add(Paciente(id = 2,"Carlos Rodríguez", 25, "Masculino", "CGTA...", mutableListOf(),
            mutableListOf()
        ))
        listaDePacientes.add(Paciente(id = 3,"Laura Gómez", 22, "Femenino", "TTAC...", mutableListOf(),
            mutableListOf()
        ))
        // Notificar al adapter. Con ListAdapter, simplemente envías la nueva lista.
        pacienteAdapter.submitList(listaDePacientes.toList()) // Envía una copia para DiffUtil
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

                //listaDePacientes.add(nuevoPaciente) // Añade a la lista de ejemplo
                //pacienteAdapter.submitList(listaDePacientes.toList()) // Actualiza el RecyclerView
                // Aquí llamas a tu método del ViewModel o directamente al DAO para guardar el paciente
                // Ejemplo (necesitarás un CoroutineScope):
                 lifecycleScope.launch {
                     pacienteDao.insertarPaciente(nuevoPaciente)
                     // Mostrar un Toast o mensaje de éxito
                     Toast.makeText(this@PatientsActivity, "Paciente guardado", Toast.LENGTH_SHORT).show()
                 }

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