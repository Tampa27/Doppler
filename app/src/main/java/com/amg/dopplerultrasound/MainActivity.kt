package com.amg.dopplerultrasound

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.amg.dopplerultrasound.data.Paciente
import com.amg.dopplerultrasound.data.PacienteDao
import com.weiner.recordaudio.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.log
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.properties.Delegates
import androidx.core.graphics.createBitmap
import com.google.android.material.textfield.TextInputEditText
import kotlin.collections.copyOf
import kotlin.math.PI

class MainActivity : AppCompatActivity() {

    companion object {
        var MAX_ARRAY_SIZE = 120//200
    }


    private lateinit var data: Array<DoubleArray>
    private var currentIndex = 0
    private var frequenciesCount by Delegates.notNull<Int>()
    private var ip = 0.00
    private var qm = 0.00
    private var maxDb = 60
    private var gain = 1.0
    private val maxFactor = 3.0
    private var Fs = 11025
    private var grayScale:Boolean = true
    private val f0 = 8000000.0 // Frecuencia de operacion del transductor
     // Velocidad del ultrasonido en sangre cm/s
    private val theta = 60.0 //Angulo de inclinacion entre el haz ultrasonico y la direccion del flujo sanguineo

    private val textHeight = 30
    private lateinit var imageView: ImageView
    private lateinit var seekdB:SeekBar
    private lateinit var checkEnvol:CheckBox
    private lateinit var ipText: TextView
    private lateinit var dBText: TextView
    private lateinit var linearLayout: LinearLayout
    private lateinit var patientText: TextView
    private lateinit var gainSeek: SeekBar
    private lateinit var gainText: TextView
    private lateinit var diameterEdit: TextInputEditText
    private lateinit var qmText: TextView

    private lateinit var unit:String
    private var windowSize = 256//512
    private var samplesBetweenWindows = 0
    private val textWidth = 60
    private val c = 154000.0
    private val K = 0.9071
    private var diametro = 0.0
    private val A_comp = 32.0 // Compensacion de las altas frecuencias
    private var isRunning:Boolean = false
    private var isLoaded:Boolean = false
    val overlap = 0.5 // 50%
    private lateinit var bmp: Bitmap
    private var ipSize:Int=0
    private val fmList = mutableListOf<Double>()
    private val fmaxList = mutableListOf<Double>()

    private var yAnt = 0.0f

    private var patientSelected = 0
    private lateinit var patient:Paciente
    lateinit var database: AppDatabase
    lateinit var pacienteDao: PacienteDao
    private lateinit var pacientes: List<Paciente>


    private lateinit var sharedPreferences: SharedPreferences


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else
        {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }

        linearLayout = findViewById(R.id.loading_layout)
        database = AppDatabase.getDatabase(applicationContext) // O el contexto apropiado
        pacienteDao = database.pacienteDao()
        cargarPacientes()

        imageView = findViewById(R.id.imageView)
        ipText = findViewById(R.id.ipText)
        dBText = findViewById(R.id.dbText)
        seekdB = findViewById(R.id.umbralSeek)
        gainSeek = findViewById(R.id.gainSeek)
        gainText = findViewById(R.id.gainText)
        checkEnvol = findViewById(R.id.checkEnvol)
        patientText = findViewById(R.id.pacienteText)
        diameterEdit = findViewById(R.id.diametro)
        qmText = findViewById(R.id.qmText)


        seekdB.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                maxDb = progress
                dBText.text = "Umbral: $maxDb dB"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}

        })
        gainSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                gain = progress / 10.0
                gainText.text = "Ganancia: ${String.format("%.1f", gain)}"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })
        imageView.setOnClickListener {
            if (!isRunning) {
                isRunning = true
                recordAudioWithPermissions()
            } else {
                isRunning = false
            }
        }
        imageView.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (imageView.width > 0 && imageView.height > 0) {
                        imageView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                        // Las dimensiones ya están disponibles (>0)
                        Log.d("ImageView", "Ancho: ${imageView.width}, Alto: ${imageView.height}")
                        bmp = createBitmap(imageView.width, imageView.height)
                        bmp.eraseColor(Color.BLACK)
                    }
                }
            }
        )
        diameterEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                try {
                    diametro = s.toString().toDouble()
                }
                catch (e:Exception){diametro = 5.0}
            }

            override fun afterTextChanged(s: Editable?) {
            }

        })
        loadPreferences()

        Toast.makeText(
            applicationContext,
            "Toca la pantalla para comenzar o detener el procesamiento",
            //"Click on screen to start/stop audio capture",
            Toast.LENGTH_LONG
        ).show()

        Handler(Looper.getMainLooper()).postDelayed({
            isRunning = true
            recordAudioWithPermissions()
        }, 1000)
    }

    private fun loadPreferences() {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        ipSize = (1f/(windowSize.toFloat()/Fs)).toInt()
        unit = sharedPreferences.getString("units","cm/s")!!
        Fs = sharedPreferences.getString("fs","11025")!!.toInt()
        windowSize = sharedPreferences.getString("window","512")!!.toInt()
        //val samplesBetweenWindows = (windowSize * overlap).toInt()
        val time = sharedPreferences.getString("time","4")!!.toInt()+1
        val ms = (windowSize.toFloat()/Fs)
        grayScale = sharedPreferences.getBoolean("gray",true)
        //MAX_ARRAY_SIZE = ((time.toFloat()/ms)).toInt()
        try {
            diametro = diameterEdit.text.toString().toDouble()
        }
        catch (e:Exception){diametro = 5.0}
        samplesBetweenWindows = (windowSize * overlap).toInt()
        patientSelected = sharedPreferences.getInt("patient",0)
    //cargarPacientes()
    }

    private fun cargarPacientes(){
        lifecycleScope.launch {
            linearLayout.visibility = View.VISIBLE
            try {
                pacientes = pacienteDao.getTodosLosPacientes().first()
                patient = pacientes[patientSelected]
                patientText.text = "Paciente: ${patient.nombre}"
            }
            catch (e:Exception){}
            finally {
                linearLayout.visibility = View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Registrar listener para cambios en preferencias
        loadPreferences()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        /*if (hasFocus) {
            bmp = createBitmap(imageView.width, imageView.height)
            bmp.eraseColor(Color.BLACK)
        }*/
    }

    @SuppressLint("MissingPermission")
    fun recordAudio() {
        val RECORDER_SAMPLERATE = Fs//8000
        val RECORDER_CHANNELS: Int = AudioFormat.CHANNEL_IN_MONO
        val RECORDER_AUDIO_ENCODING: Int = AudioFormat.ENCODING_PCM_16BIT
        val butterSize = max(AudioRecord.getMinBufferSize(
            RECORDER_SAMPLERATE,
            RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING
        ),windowSize)

        var currentPosition = 0
        val buffer = ShortArray(butterSize)
        frequenciesCount = windowSize//getMinimalPowerOf2(butterSize)
        data = Array(MAX_ARRAY_SIZE) {
            DoubleArray(frequenciesCount) { 0.0 }
        }
        val x = DoubleArray(frequenciesCount)
        var y = DoubleArray(frequenciesCount)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            RECORDER_SAMPLERATE, RECORDER_CHANNELS,
            RECORDER_AUDIO_ENCODING, frequenciesCount
        )

        /*val audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            RECORDER_SAMPLERATE,
            AudioFormat.CHANNEL_OUT_MONO,
            RECORDER_AUDIO_ENCODING,
            frequenciesCount,
            AudioTrack.MODE_STREAM
        )*/

        val fft = FFT(frequenciesCount)
        lifecycleScope.launch (Dispatchers.Default){
        //thread {
            recorder.startRecording()
            //audioTrack.play()
            //audioTrack.setVolume(AudioTrack.getMaxVolume())
            while (isRunning) {
                // gets the voice output from microphone to byte format
                val bytesRead = recorder.read(buffer, 0, frequenciesCount)
                /*if (bytesRead > 0) {
                    audioTrack.write(buffer, 0, bytesRead)
                }*/
                currentPosition += samplesBetweenWindows

                for (fr in 0 until frequenciesCount) {
                    x[fr] = buffer[fr] * 1.0
                    y[fr] = 0.0
                }

                //val xWindowed = x.copyOf()
                //fft.hanningWindow(xWindowed)
                //currentPosition += samplesBetweenWindows
                fft.process(x, y)
                //val threshold = calculateNoiseThreshold(y)
                //y = applyThreshold(y, threshold)
                for (fr in 0 until frequenciesCount) {
                    val mag_abs = Math.abs(y[fr])
                    data[currentIndex][fr] = mag_abs
                }
                //rectaCompens(data[currentIndex])
                val fmed = fMeans(data[currentIndex])
                val bw = bandWidth(data[currentIndex],fmed)
                val fmax = fmed+bw
                if (fmList.size < ipSize){
                    fmList.add(fmed.toDouble())
                    fmaxList.add(fmax.toDouble())
                }
                else{
                    if (currentIndex % ipSize == 0){
                        val fmm = fmList.sum()/fmList.size
                        //val fmax = fmList.max()
                        val fmax = fmaxList.sum()/fmaxList.size
                        val fmin = fmList.min()
                        ip = if (fmm > 0.0000001) (fmax - fmin) / fmm else 0.0
                        fmList.removeAt(0)
                        fmList.add(fmed.toDouble())
                        fmaxList.removeAt(0)
                        fmaxList.add(fmax.toDouble())
                        val fkH = fmm*(Fs/windowSize)
                        val vel = getVelocidad(fkH)
                        qm = PI*(diametro/20)*(diametro/20)*vel*60
                    }
                }

                //updateGlobalMinMax()
                withContext(Dispatchers.Main) {
                    render()
                }
                currentIndex = if (currentIndex == (MAX_ARRAY_SIZE - 1)) 0 else currentIndex + 1
            }
            recorder.stop()
            //audioTrack.stop()
        }
    }

    private fun render() {
        if (this::bmp.isInitialized && bmp.width > 0 && bmp.height > 0) {
            val c = Canvas(bmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rectWidth = 1.0f * (bmp.width) / MAX_ARRAY_SIZE
            val rectHeight = 1.0f * bmp.height / (frequenciesCount / 2.0f)
            val halfOfFrequencies = frequenciesCount / 2
            paint.color = Color.WHITE
            paint.textSize = 30f
            for (i in 0..Fs / 2 step 1000) {
                val yt = bmp.height * (1f - i.toFloat() / (Fs / 2))
                if (i < (Fs / 2 - 1000)) {
                    if (unit == "cm/s") {
                        var vel = getVelocidad(i.toDouble())
                        vel = Math.round(vel / 10.0) * 10.0
                        c.drawText(" ${String.format("%.0f", vel)}", 0f, yt - textHeight, paint)
                    } else {
                        c.drawText(" " + i / 1000, 0f, yt - textHeight, paint)
                    }
                } else {
                    paint.textSize = 25f
                    c.drawText(unit, 0f, yt, paint)
                }
            }
            val max_time = ((windowSize.toFloat() / Fs) * MAX_ARRAY_SIZE).toInt() + 1
            for (i in 0 until max_time) {
                val xt = (bmp.width - textHeight) * (1f - (i.toFloat() / max_time))
                c.drawText(" " + i + "s", xt, bmp.height.toFloat(), paint)
            }
            repeat(MAX_ARRAY_SIZE) { x ->
                val index =
                    if (currentIndex + x + 1 < MAX_ARRAY_SIZE) currentIndex + x + 1 else currentIndex + x + 1 - MAX_ARRAY_SIZE
                val min = data[index].min()
                val max = data[index].max()
                val limit = Math.pow(10.0,maxDb*maxFactor/20)
                /*val maxPox = data[index].withIndex()
                .filter { it.index < halfOfFrequencies } // Filtrar elementos hasta la mitad
                .maxByOrNull { it.value }*/
                for (y in 0 until halfOfFrequencies) {
                    val magdB = 20 * log(data[index][y], 10.0)
                    if (magdB < maxDb)
                        paint.color = if (grayScale)
                            getGrayByValueSimple(0.0, min, max)
                                    else
                            getColorByValue2(0.0, min, max)
                    else {
                        val v = if (data[index][y] > limit) limit else data[index][y]
                        val value = v*gain
                        paint.color = if (grayScale)
                            getGrayByValueSimple(value, min, max)
                        else
                            getColorByValue2(value, min, max)
                    }
                    c.drawRect(
                        x * (rectWidth) + textWidth,
                        (halfOfFrequencies - y) * rectHeight - textHeight,
                        (x + 1) * (rectWidth) + textWidth,
                        (halfOfFrequencies - y + 1) * rectHeight - textHeight,
                        paint
                    )
                }

                if (checkEnvol.isChecked) {
                    paint.color = Color.YELLOW
                    paint.strokeWidth = 3f
                    val fMedia = fMeans(data[index])
                    val yAct =
                        (halfOfFrequencies - fMedia/*maxPox!!.index*/) * rectHeight - textHeight
                    c.drawLine(
                        x * (rectWidth) + textWidth,
                        yAnt,
                        (x + 1) * (rectWidth) + textWidth,
                        yAct,
                        paint
                    )
                    yAnt = yAct
                }
            }
            runOnUiThread {
                imageView.setImageBitmap(bmp)
                ipText.text = String.format("%.1f", ip)
                qmText.text = "Qm:${qm.toInt()}mL/min"
                imageView.invalidate()
            }
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted ) {

                recordAudio()
            } else {

            }
        }

    @RequiresApi(Build.VERSION_CODES.M)
    fun recordAudioWithPermissions() {

        when {
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                recordAudio()
            }

            shouldShowRequestPermissionRationale(android.Manifest.permission.RECORD_AUDIO) -> {
                Toast.makeText(
                    applicationContext,
                    "",
                    Toast.LENGTH_SHORT
                ).show()
            }

            else -> requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun fMeans(freqMags:DoubleArray):Int{
        val halfOfFrequencies = frequenciesCount/2
        var fMedia = 0
        val Pi = freqMags.foldIndexed(0.0){
            index,sum,value -> if(index < halfOfFrequencies) sum+value*value else sum
        }
        val fiPi = freqMags.foldIndexed(0.0){
            index,sum,value -> if(index < halfOfFrequencies) sum+(value*value*index) else sum
        }
        fMedia = (fiPi/Pi).toInt()
        return fMedia;
    }

    private fun bandWidth(freqMags:DoubleArray,fMedia:Int):Int{
        val halfOfFrequencies = frequenciesCount/2
        var bw = 0
        val Pi = freqMags.foldIndexed(0.0){
                index,sum,value -> if(index < halfOfFrequencies) sum+value*value else sum
        }
        val numerador  = freqMags.foldIndexed(0.0){
                index,sum,value -> if(index < halfOfFrequencies) sum+(value*value*(index-fMedia)*(index-fMedia)) else sum
        }
        bw = sqrt(numerador/Pi).toInt()
        return bw
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == R.id.action_play) {
            isRunning = true
        } else if (itemId == R.id.action_pause) {
            isRunning = false
        }
        else if (itemId == R.id.action_settings) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        else if (itemId == R.id.action_patient) {
            startActivity(Intent(this, PatientsActivity::class.java))
        }
        else if (itemId == R.id.action_screenshot){
            val bitmap = getBitmapFromUiView(imageView)
            //function call, pass the bitmap to save it
            saveBitmapImage(bitmap)
        }
        return super.onOptionsItemSelected(item)
    }

    fun getVelocidad(frecuencia:Double):Double{
        return (c/f0)*frecuencia
    }

    fun rectaCompens(freqMags:DoubleArray){
        val halfOfFrequencies = frequenciesCount/2
        for (i in 0 until halfOfFrequencies){
            if(i > A_comp)
                freqMags[i] = freqMags[i]*(i/A_comp)
        }
    }

    /**Get Bitmap from any UI View
     * @param view any UI view to get Bitmap of
     * @return returnedBitmap the bitmap of the required UI View */
    private fun getBitmapFromUiView(view: View?): Bitmap {
        //Define a bitmap with the same size as the view
        val returnedBitmap = Bitmap.createBitmap(view!!.width, view.height, Bitmap.Config.ARGB_8888)
        //Bind a canvas to it
        val canvas = Canvas(returnedBitmap)
        //Get the view's background
        val bgDrawable = view.background
        if (bgDrawable != null) {
            //has background drawable, then draw it on the canvas
            bgDrawable.draw(canvas)
        } else {
            //does not have background drawable, then draw white background on the canvas
            canvas.drawColor(Color.WHITE)
        }
        // draw the view on the canvas
        view.draw(canvas)

        //return the bitmap
        return returnedBitmap
    }


    // En tu clase MainActivity

    private var minValGlobal: Double = 0.0
    private var maxValGlobal: Double = 100000.0 // Evitar división por cero al inicio si todo es 0

    /**
     * Calcula el mínimo y máximo valor global del dataset 'data'.
     * Se debe llamar cuando los datos relevantes han sido actualizados.
     */
    private fun updateGlobalMinMax() {
        if (data.isEmpty() || data[0].isEmpty()) {
            minValGlobal = 0.0
            maxValGlobal = 1.0
            return
        }

        var currentMin = Double.MAX_VALUE
        var currentMax = Double.MIN_VALUE

        // Iterar sobre todo el buffer 'data'
        // Como 'data' es una ventana circular, todas sus entradas son potencialmente válidas.
        for (i in data.indices) { // Itera sobre cada columna de tiempo
            for (j in 0 until frequenciesCount / 2) { // Itera sobre las frecuencias relevantes
                val value = data[i][j]
                if (value < currentMin) {
                    currentMin = value
                }
                if (value > currentMax) {
                    currentMax = value
                }
            }
        }

        minValGlobal = if (currentMin == Double.MAX_VALUE) 0.0 else currentMin
        maxValGlobal = if (currentMax == Double.MIN_VALUE) 1.0 else currentMax

        // Asegurar que maxValGlobal no sea igual a minValGlobal para evitar división por cero en la normalización
        if (maxValGlobal == minValGlobal) {
            maxValGlobal = minValGlobal + 1.0 // o alguna otra pequeña delta, o manejarlo en la función de color
        }
        // Log para depuración (opcional)
        // Log.d("GlobalMinMax", "Min: $minValGlobal, Max: $maxValGlobal")
    }

    /**Save Bitmap To Gallery
     * @param bitmap The bitmap to be saved in Storage/Gallery*/
    private fun saveBitmapImage(bitmap: Bitmap) {
        val timestamp = System.currentTimeMillis()

        //Tell the media scanner about the new file so that it is immediately available to the user.
        val values = ContentValues()
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        values.put(MediaStore.Images.Media.DATE_ADDED, timestamp)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
            values.put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "Pictures/" + getString(R.string.app_name)
            )
            values.put(MediaStore.Images.Media.IS_PENDING, true)
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                try {
                    val outputStream = contentResolver.openOutputStream(uri)
                    if (outputStream != null) {
                        try {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                            outputStream.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "saveBitmapImage: ", e)
                        }
                    }
                    values.put(MediaStore.Images.Media.IS_PENDING, false)
                    contentResolver.update(uri, values, null, null)

                    Toast.makeText(this, "Saved...", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e(TAG, "saveBitmapImage: ", e)
                }
            }
        } else {
            val imageFileFolder = File(
                Environment.getExternalStorageDirectory()
                    .toString() + '/' + getString(R.string.app_name)
            )
            if (!imageFileFolder.exists()) {
                imageFileFolder.mkdirs()
            }
            val mImageName = "$timestamp.png"
            val imageFile = File(imageFileFolder, mImageName)
            try {
                val outputStream: OutputStream = FileOutputStream(imageFile)
                try {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    outputStream.close()
                } catch (e: Exception) {
                    Log.e(TAG, "saveBitmapImage: ", e)
                }
                values.put(MediaStore.Images.Media.DATA, imageFile.absolutePath)
                contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

                Toast.makeText(this, "Saved...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "saveBitmapImage: ", e)
            }
        }
    }
}
fun getColorByValue(value: Double, min: Double, max: Double): Int {
    if (min == 0.0 && max == 0.0) {
        return Color.rgb(0, 0, 0)
    }
    val normalizedValue = (value - min) / (max - min)
    val freq = Math.PI * 2 / 1.5
    val red = ((sin(freq * normalizedValue + 0) * 127 + 128) * normalizedValue).roundToInt()
    val green = ((sin(freq * normalizedValue + 2) * 127 + 128) * normalizedValue).roundToInt()
    val blue = ((sin(freq * normalizedValue + 4) * 127 + 128) * normalizedValue).roundToInt()

    /*val freq = Math.PI.toFloat() * 2/1.5 // Frecuencia para cubrir 0-1

    val red = ((cos(freq * normalizedValue) * 127.5 + 127.5) * normalizedValue).roundToInt()
    val green = (sin(freq * normalizedValue) * 127.5 * normalizedValue + 127.5 * normalizedValue).roundToInt()
    val blue = ((sin(freq * normalizedValue - Math.PI.toFloat()) * 127.5 + 127.5) * normalizedValue).roundToInt()

     */
    return Color.rgb(red, green, blue)
}
// Para HSL si lo eliges después

fun getColorByValue2(value: Double, min: Double, max: Double): Int {
    if (min == max) {
        return Color.rgb(0, 0, 0)
    }
    var t = (value - min) / (max - min)
    if (t < 0.0) t = 0.0
    if (t > 1.0) t = 1.0

    val r: Int
    val g: Int
    val b: Int

    when {
        t <= 0.2 -> { // Negro a Azul
            val u = t / 0.2
            r = 0
            g = 0
            b = (u * 255).toInt()
        }
        t <= 0.4 -> { // Azul a Verde
            val u = (t - 0.2) / 0.2
            r = 0
            g = (u * 255).toInt()
            b = (255 - u * 255).toInt()
        }
        /*t <= 0.6 -> { // Verde a Amarillo
            val u = (t - 0.4) / 0.2
            r = (u * 255).toInt()
            g = 255
            b = 0
        }
        t <= 0.8 -> { // Amarillo a Naranja
            val u = (t - 0.6) / 0.2
            r = 255
            g = (255 - u * 90).toInt() // 255 -> 165
            b = 0
        }*/
        else -> { // Naranja a Rojo
            val u = (t - 0.8) / 0.2
            r = 255
            g = (165 - u * 165).toInt() // 165 -> 0
            b = 0
        }
    }

    return Color.rgb(r, g, b)
}

fun getColorByValue1(value: Double, min: Double, max: Double): Int {
    if (min == 0.0 && max == 0.0) {
        return Color.rgb(0, 0, 0) // Negro si no hay rango
    }
    if (value < min) return Color.rgb(0,0,0) // O algún color para valores fuera de rango inferior
    if (value > max) return Color.rgb(255,255,255) // O algún color para valores fuera de rango superior


    // Normalizar el valor entre 0 y 1
    // Si max == min (pero no ambos cero), normalizar a 0.5 o 1 para evitar división por cero
    // y darle un color consistente. O puedes devolver un color fijo.
    val normalizedValue = if (max - min == 0.0) 0.5 else (value - min) / (max - min)

    // Ajusta la frecuencia para determinar qué tan rápido cambian los colores
    // Un valor más alto significa cambios de color más rápidos a medida que normalizedValue aumenta.
    val freq = (PI * 2).toFloat() // Una onda completa a lo largo del rango normalizado

    // Amplitud y desplazamiento para asegurar que los colores estén en el rango 0-255
    // y sean brillantes.
    // La amplitud es 127.5, el desplazamiento es 127.5. Suma = 255.
    // (sin(angle) * 127.5 + 127.5) dará un rango de 0 a 255.

    // Desfasar las ondas para diferentes colores
    // Puedes experimentar con estos desfases para diferentes paletas de colores
    val redPhase = 0f
    val greenPhase = (2 * PI / 3).toFloat() // Desfase de 120 grados
    val bluePhase = (4 * PI / 3).toFloat()  // Desfase de 240 grados

    var red = (sin(freq * normalizedValue + redPhase) * 127.5 + 127.5).roundToInt()
    var green = (sin(freq * normalizedValue + greenPhase) * 127.5 + 127.5).roundToInt()
    var blue = (sin(freq * normalizedValue + bluePhase) * 127.5 + 127.5).roundToInt()

    // Asegurarse de que los valores estén dentro del rango [0, 255]
    red = red.coerceIn(0, 255)
    green = green.coerceIn(0, 255)
    blue = blue.coerceIn(0, 255)

    return Color.rgb(red, green, blue)
}

fun getMinimalPowerOf2(n: Int): Int {
    return Math.pow(2.0, Math.floor(log2(n.toDouble()))).toInt()
}

fun calculateNoiseThreshold(buffer: DoubleArray): Double {
    // Convertir a valores absolutos
    val magnitudes = buffer.map { Math.abs(it.toDouble()) }

    // Calcular media y desviación estándar
    val mean = magnitudes.average()
    val stdDev = sqrt(magnitudes.map { (it - mean) * (it - mean) }.average())

    // Umbral: media + 1.5 * desviación estándar (ajustable)
    return mean + 1.5 * stdDev
}

fun calculateNoiseThreshold1(buffer: DoubleArray): Double {
    val sorted = buffer.map { Math.abs(it.toDouble()) }.sorted()
    val percentileIndex = (sorted.size * 0.95).toInt()
    return sorted[percentileIndex]
}

fun applyThreshold(fftMagnitudes: DoubleArray, threshold: Double): DoubleArray {
    return fftMagnitudes.map { if (it < threshold) 0.0 else it }.toDoubleArray()
}

private fun getColorByMagnitudeIntensity(
    value: Double,
    min: Double, // Ahora será minValGlobal
    max: Double, // Ahora será maxValGlobal
    targetHue: Float = 0f
): Int {
    // Si min y max son iguales (y no debería ocurrir si updateGlobalMinMax lo maneja)
    if (max == min) {
        // Devuelve un color base con intensidad media-baja si hay valor, o negro.
        // La idea es que si todos los valores en el buffer son iguales (y no cero), se muestren.
        val baseIntensity = if (value > 0.0) 0.1f else 0.0f
        return ColorUtils.HSLToColor(floatArrayOf(targetHue, 0.95f, baseIntensity))
    }

    val normalizedMagnitude = ((value - min) / (max - min)).coerceIn(0.0, 1.0).toFloat()
    val saturation = 0.95f
    val minBrightness = 0.05f
    val maxBrightnessRange = 0.95f
    val brightness = (minBrightness + normalizedMagnitude * maxBrightnessRange).coerceIn(minBrightness, 1.0f)

    return ColorUtils.HSLToColor(floatArrayOf(targetHue, saturation, brightness))
}



fun getColorByMagnitudeIntensityWithHueShift(
    value: Double,
    min: Double,
    max: Double
): Int {
    if (max == min) {
        val baseIntensity = if (value > 0.0) 0.1f else 0.0f
        val baseHue = 240f // Azul por defecto para este caso
        return ColorUtils.HSLToColor(floatArrayOf(baseHue, 0.8f, baseIntensity))
    }

    val normalizedMagnitude = ((value - min) / (max - min)).coerceIn(0.0, 1.0).toFloat()

    // --- Variación del Matiz (Hue) ---
    // Define el matiz para la magnitud MÍNIMA (normalizedMagnitude = 0.0)
    val hueAtMinMagnitude = 240f // Ejemplo: Azul

    // Define el matiz para la magnitud MÁXIMA (normalizedMagnitude = 1.0)
    // Este será tu "rojo" específico.
    // Ejemplos:
    // val hueAtMaxMagnitude = 350f // Un rojo carmesí / magenta-rojizo
    val hueAtMaxMagnitude = 15f  // Un rojo anaranjado

    // Interpolar linealmente el matiz entre hueAtMinMagnitude y hueAtMaxMagnitude
    var hue: Float
    if (hueAtMaxMagnitude >= hueAtMinMagnitude) {
        // Caso simple: el matiz aumenta con la magnitud
        hue = hueAtMinMagnitude + (hueAtMaxMagnitude - hueAtMinMagnitude) * normalizedMagnitude
    } else {
        // Caso donde el matiz "cruza" el punto 0/360 (ej. de azul 240 a rojo 15)
        // Necesitamos interpolar por el camino más corto en el círculo cromático.
        // O, si quieres que siempre vaya en una dirección (ej. siempre aumentando),
        // puedes ajustar hueAtMaxMagnitude sumándole 360 si es menor que hueAtMinMagnitude
        // para asegurar que la interpolación sea "hacia adelante".

        // Opción 1: Camino más corto (puede cambiar de dirección)
        // val diff = hueAtMaxMagnitude - hueAtMinMagnitude
        // if (abs(diff) > 180) { // Si el camino directo es más largo que cruzar por 0/360
        //     if (diff > 0) {
        //         hue = hueAtMinMagnitude + (diff - 360) * normalizedMagnitude
        //     } else {
        //         hue = hueAtMinMagnitude + (diff + 360) * normalizedMagnitude
        //     }
        // } else {
        //     hue = hueAtMinMagnitude + diff * normalizedMagnitude
        // }
        // hue = (hue + 360f) % 360f // Asegurar que esté en [0, 360)

        // Opción 2: Siempre interpolar en la dirección que "aumenta" el matiz,
        // cruzando por 0/360 si es necesario.
        // Ej: de Azul (240) -> Violeta (270) -> Magenta (300) -> Rojo (0/360) -> Rojo Naranja (15)
        // Queremos ir de 240 a (15 + 360) = 375 para que la interpolación sea lineal hacia arriba.
        val targetMaxHue = if (hueAtMaxMagnitude < hueAtMinMagnitude) hueAtMaxMagnitude + 360f else hueAtMaxMagnitude
        hue = hueAtMinMagnitude + (targetMaxHue - hueAtMinMagnitude) * normalizedMagnitude
        hue %= 360f // Asegurar que el resultado final esté en el rango [0, 360)
    }


    // --- Saturación ---
    val saturation = (0.6f + normalizedMagnitude * 0.4f).coerceIn(0.6f, 1.0f) // Ej: de 0.6 a 1.0

    // --- Brillo/Valor ---
    val minBrightness = 0.15f // Un poco más visible para los valores más bajos
    val maxBrightnessRange = 0.85f // (1.0f - minBrightness)
    val brightness = (minBrightness + (normalizedMagnitude * normalizedMagnitude) * maxBrightnessRange).coerceIn(minBrightness, 1.0f)

    return ColorUtils.HSLToColor(floatArrayOf(hue, saturation, brightness))
}

fun getGrayByValueSimple(value: Double, min: Double, max: Double): Int {
    if (min == max) {
        return Color.rgb(0, 0, 0)
    }
    val t = ((value - min) / (max - min)).coerceIn(0.0, 1.0)
    val gray = (t * 255).toInt()
    return Color.rgb(gray, gray, gray)
}


fun getColorForSpectrogram(
    value: Double,
    minVal: Double, // Renombrado para claridad
    maxVal: Double  // Renombrado para claridad
): Int {
    if (maxVal == minVal) {
        return if (value > 0.0) Color.rgb(20, 0, 0) else Color.BLACK // Un rojo muy oscuro o negro
    }

    val normalizedMagnitude = ((value - minVal) / (maxVal - minVal)).coerceIn(0.0, 1.0).toFloat()

    // --- Definir umbrales para la transición de color ---
    // Estos valores están en el rango [0, 1] de normalizedMagnitude. Ajusta según tus preferencias.
    val thresholdBlackEnd = 0.05f   // Por debajo de esto, es prácticamente negro
    val thresholdBlueStart = thresholdBlackEnd
    val thresholdBlueEnd = 0.35f    // Rango para tonos azules
    val thresholdGreenStart = thresholdBlueEnd
    val thresholdGreenEnd = 0.70f   // Rango para tonos verdes
    val thresholdRedStart = thresholdGreenEnd
    // El rojo va desde thresholdRedEnd hasta 1.0

    var hue: Float
    var saturation: Float
    var brightness: Float

    when {
        // Rango 1: Negro
        normalizedMagnitude < thresholdBlackEnd -> {
            hue = 0f // No importa mucho para el negro
            saturation = 0f
            brightness = 0f
        }

        // Rango 2: Transición a Azul
        normalizedMagnitude < thresholdBlueEnd -> {
            // Interpolar dentro de este segmento [thresholdBlueStart, thresholdBlueEnd]
            val segmentNormalized = (normalizedMagnitude - thresholdBlueStart) / (thresholdBlueEnd - thresholdBlueStart)

            hue = 240f // Azul
            saturation = (0.7f + segmentNormalized * 0.3f).coerceIn(0.7f, 1.0f) // Aumenta la saturación
            brightness = (segmentNormalized * 0.8f).coerceIn(0.0f, 0.8f) // Aumenta el brillo desde casi 0
        }

        // Rango 3: Transición a Verde
        normalizedMagnitude < thresholdGreenEnd -> {
            // Interpolar dentro de este segmento [thresholdGreenStart, thresholdGreenEnd]
            val segmentNormalized = (normalizedMagnitude - thresholdGreenStart) / (thresholdGreenEnd - thresholdGreenStart)

            // Interpolar matiz de Azul (240) a Verde (120)
            val hueBlue = 240f
            val hueGreen = 120f
            hue = hueBlue + (hueGreen - hueBlue) * segmentNormalized

            saturation = 1.0f // Saturación completa
            brightness = (0.6f + segmentNormalized * 0.4f).coerceIn(0.6f, 1.0f) // Mantener brillo alto, aumentando ligeramente
        }

        // Rango 4: Transición a Rojo (y más intenso)
        else -> { // normalizedMagnitude >= thresholdRedStart (que es thresholdGreenEnd)
            // Interpolar dentro de este segmento [thresholdRedStart, 1.0]
            val segmentNormalized = (normalizedMagnitude - thresholdRedStart) / (1.0f - thresholdRedStart)

            // Interpolar matiz de Verde (120) a Rojo (0)
            val hueGreen = 120f
            val hueRed = 0f // o 360f si prefieres para la interpolación

            // Si interpolamos de 120 (verde) a 0 (rojo), necesitamos que el matiz decrezca.
            // Para asegurar que vaya en la dirección correcta (ej., verde -> amarillo -> naranja -> rojo),
            // podemos tratar el rojo como 0.
            // O, si interpolamos de 120 a 360(rojo), el matiz aumenta, lo que no queremos aquí.
            // Mejor interpolar de 120 a 0.
            hue = hueGreen + (hueRed - hueGreen) * segmentNormalized
            if (hue < 0) hue += 360f // Asegurar que el matiz sea positivo

            saturation = 1.0f
            brightness = (0.7f + segmentNormalized * 0.3f).coerceIn(0.7f, 1.0f) // Brillo máximo para el rojo más intenso
        }
    }

    return ColorUtils.HSLToColor(floatArrayOf(hue, saturation, brightness))
}

fun getColorByValueHSV(value: Double, min: Double, max: Double): Int {
    if (min == 0.0 && max == 0.0) {
        return Color.rgb(0, 0, 0)
    }
    if (value < min) return Color.rgb(0,0,0)
    if (value > max) return Color.rgb(255,255,255) // O un color de saturación máxima

    val normalizedValue = if (max - min == 0.0) 0.5 else (value - min) / (max - min)

    // Matiz (Hue): Recorre el espectro de colores (0-360 grados).
    // Mapea normalizedValue (0-1) a un rango de matiz, por ejemplo, de rojo (0) a magenta/rojo (300-360)
    // para evitar un salto brusco de rojo a rojo si usas 0-360 directamente.
    // O puedes usar de 0 a 240 (rojo a azul) para un espectro tipo arcoíris sin pasar por magenta.
    val hue = (normalizedValue * 240f).toFloat() // Ejemplo: Rojo (0) -> Amarillo (60) -> Verde (120) -> Cian (180) -> Azul (240)

    // Saturación (Saturation): Qué tan vivo es el color (0=gris, 1=color puro).
    // Para colores brillantes, mantenlo alto.
    val saturation = 0.9f // Puedes hacerlo 1.0f para máxima saturación.

    // Valor/Brillo (Value/Brightness): Qué tan claro u oscuro es el color (0=negro, 1=color brillante).
    // Para colores brillantes, mantenlo alto.
    val brightness = 1.0f

    // Convertir de HSV a RGB
    // Color.HSVToColor espera un array de 3 floats: {hue, saturation, value}
    return ColorUtils.HSLToColor(floatArrayOf(hue, saturation, brightness)) // Nota: HSLToColor también funciona bien aquí.
    // Para HSV puro sería Color.HSVToColor, pero
    // HSLToColor es de androidx.core.graphics.ColorUtils y más común.
    // Si usas Color.HSVToColor (android.graphics.Color),
    // los parámetros son (alpha, hsvArray).
}

// Si prefieres usar android.graphics.Color.HSVToColor:
fun getColorByValuePureHSV(value: Double, min: Double, max: Double): Int {
    if (min == 0.0 && max == 0.0) {
        return Color.rgb(0, 0, 0)
    }
    if (value < min) return Color.rgb(0,0,0)
    if (value > max) return Color.rgb(255,255,255)

    val normalizedValue = if (max - min == 0.0) 0.5 else (value - min) / (max - min)

    val hue = (normalizedValue * 240f).toFloat()
    val saturation = 0.9f
    val brightness = 1.0f

    // android.graphics.Color.HSVToColor
    return Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
}