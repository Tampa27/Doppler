package com.amg.dopplerultrasound // O tu paquete

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView // Para MaterialCardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.amg.dopplerultrasound.data.Paciente

// import com.bumptech.glide.Glide // Ejemplo si usas Glide

class PacienteAdapter(
    private val onItemClicked: (Paciente) -> Unit,
    private val onDeleteClicked: (Paciente) -> Unit
) : ListAdapter<Paciente, PacienteAdapter.PacienteViewHolder>(PacienteDiffCallback()) {

    private var selectedPosition = RecyclerView.NO_POSITION // -1, para rastrear la posición seleccionada

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PacienteViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.patient_layout, parent, false)
        return PacienteViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: PacienteViewHolder, position: Int) {
        val pacienteActual = getItem(position)
        holder.bind(pacienteActual, position == selectedPosition)

        holder.itemView.setOnClickListener {
            // Notificar al oyente del clic general
            onItemClicked(pacienteActual)

            // Actualizar la selección y notificar cambios para el resaltado visual
            if (selectedPosition != holder.adapterPosition) {
                val previousSelectedPosition = selectedPosition
                selectedPosition = holder.adapterPosition
                // Notificar al elemento previamente seleccionado para que se deseleccione
                if (previousSelectedPosition != RecyclerView.NO_POSITION) {
                    notifyItemChanged(previousSelectedPosition)
                }
                // Notificar al elemento actualmente seleccionado para que se seleccione
                notifyItemChanged(selectedPosition)
            }
        }

        holder.buttonEliminar.setOnClickListener {
            onDeleteClicked(pacienteActual)
        }
    }

    // Método para actualizar la selección externamente si es necesario
    fun setSelectedPosition(position: Int) {
        val previousSelectedPosition = selectedPosition
        selectedPosition = position
        if (previousSelectedPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(previousSelectedPosition)
        }
        if (selectedPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(selectedPosition)
        }
    }

    fun getSelectedPaciente(): Paciente? {
        return if (selectedPosition != RecyclerView.NO_POSITION && selectedPosition < itemCount) {
            getItem(selectedPosition)
        } else {
            null
        }
    }

    inner class PacienteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: CardView = itemView as CardView // El root es MaterialCardView
        private val fotoImageView: ImageView = itemView.findViewById(R.id.imageViewPacienteFoto)
        private val nombreTextView: TextView = itemView.findViewById(R.id.textViewPacienteNombre)
        private val edadTextView: TextView = itemView.findViewById(R.id.textViewPacienteEdad)
        private val sexoTextView: TextView = itemView.findViewById(R.id.textViewPacienteSexo)
        private val dnaTextView: TextView = itemView.findViewById(R.id.textViewPacienteDna)
        val buttonEliminar: ImageButton = itemView.findViewById(R.id.buttonEliminarPaciente)

        fun bind(paciente: Paciente, isSelected: Boolean) {
            nombreTextView.text = paciente.nombre
            edadTextView.text = "${paciente.edad} años"
            sexoTextView.text = paciente.sexo
            dnaTextView.text = if (paciente.dna.isNullOrEmpty()) "N/A" else paciente.dna

            // Resaltar si está seleccionado
            if (isSelected) {
                // Puedes usar un color de tu tema o un color definido
                cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.selected_item_background)) // Necesitas definir este color
                // Alternativamente, cambiar la elevación o el borde
                // cardView.strokeWidth = 4 // (necesitas definir app:strokeWidth en el XML y un color app:strokeColor)
                // cardView.strokeColor = ContextCompat.getColor(itemView.context, R.color.selected_item_border)
            } else {
                // Restaurar color original o predeterminado
                cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, android.R.color.background_light)) // Color por defecto de MaterialCardView
                // cardView.strokeWidth = 0
            }

            // Cargar imagen (lógica de Glide/Picasso aquí)
            fotoImageView.setImageResource(R.drawable.ic_launcher_foreground) // Placeholder
        }
    }

    // PacienteDiffCallback se mantiene igual
    class PacienteDiffCallback : DiffUtil.ItemCallback<Paciente>() {
        override fun areItemsTheSame(oldItem: Paciente, newItem: Paciente): Boolean {
            // Asume que si el nombre (o un ID único si lo tienes) es el mismo, es el mismo ítem
            return oldItem.nombre == newItem.nombre // O oldItem.id == newItem.id si Paciente tiene ID
        }

        override fun areContentsTheSame(oldItem: Paciente, newItem: Paciente): Boolean {
            return oldItem == newItem
        }
    }
}