package com.amg.dopplerultrasound // O tu paquete

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.amg.dopplerultrasound.data.Paciente

// import com.bumptech.glide.Glide // Ejemplo si usas Glide para imágenes

class PacienteAdapter(private val onItemClicked: (Paciente) -> Unit) :
    ListAdapter<Paciente, PacienteAdapter.PacienteViewHolder>(PacienteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PacienteViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.patient_layout, parent, false)
        return PacienteViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: PacienteViewHolder, position: Int) {
        val pacienteActual = getItem(position)
        holder.bind(pacienteActual)
        holder.itemView.setOnClickListener {
            onItemClicked(pacienteActual)
        }
    }

    inner class PacienteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val fotoImageView: ImageView = itemView.findViewById(R.id.imageViewPacienteFoto)
        private val nombreTextView: TextView = itemView.findViewById(R.id.textViewPacienteNombre)
        private val edadTextView: TextView = itemView.findViewById(R.id.textViewPacienteEdad)
        private val sexoTextView: TextView = itemView.findViewById(R.id.textViewPacienteSexo)
        private val dnaTextView: TextView = itemView.findViewById(R.id.textViewPacienteDna)

        fun bind(paciente: Paciente) {
            nombreTextView.text = paciente.nombre
            edadTextView.text = "${paciente.edad} años" // Formatear como desees
            sexoTextView.text = paciente.sexo
            dnaTextView.text = if (paciente.dna.isNullOrEmpty()) "N/A" else paciente.dna

            // Cargar la imagen del paciente usando Glide, Picasso, o Coil
            // Ejemplo con Glide (necesitarás agregar la dependencia de Glide y un placeholder):
            /*
            if (paciente.imagenes.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(paciente.imagenes[0]) // Cargar la primera imagen, por ejemplo
                    // .placeholder(R.drawable.ic_person_placeholder) // Un placeholder mientras carga
                    // .error(R.drawable.ic_person_placeholder)       // Un placeholder si hay error
                    .circleCrop() // Opcional: para hacerla circular
                    .into(fotoImageView)
            } else {
                 fotoImageView.setImageResource(R.drawable.ic_person_placeholder) // Imagen por defecto
            }
            */
            // Por ahora, una imagen de muestra si no usas Glide:
            fotoImageView.setImageResource(R.drawable.ic_launcher_foreground) // Reemplaza con un placeholder real
        }
    }

    class PacienteDiffCallback : DiffUtil.ItemCallback<Paciente>() {
        override fun areItemsTheSame(oldItem: Paciente, newItem: Paciente): Boolean {
            // Asume que si el nombre (o un ID único si lo tienes) es el mismo, es el mismo ítem
            return oldItem.nombre == newItem.nombre // O oldItem.id == newItem.id si Paciente tiene ID
        }

        override fun areContentsTheSame(oldItem: Paciente, newItem: Paciente): Boolean {
            return oldItem == newItem // Data class compara todos los campos
        }
    }
}