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
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.log
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.properties.Delegates

class MainActivity : AppCompatActivity() {

    companion object {
        var MAX_ARRAY_SIZE = 100//200
    }


    private lateinit var data: Array<DoubleArray>
    private var currentIndex = 0
    private var frequenciesCount by Delegates.notNull<Int>()
    private var ip = 0.00
    private var maxDb = 60
    private var Fs = 11025
    private val f0 = 8000000.0 // Frecuencia de operacion del transductor
     // Velocidad del ultrasonido en sangre cm/s
    private val theta = 60.0 //Angulo de inclinacion entre el haz ultrasonico y la direccion del flujo sanguineo

    private val textHeight = 30
    private lateinit var imageView: ImageView
    private lateinit var seekdB:SeekBar
    private lateinit var checkEnvol:CheckBox
    private lateinit var ipText: TextView
    private lateinit var dBText: TextView

    private lateinit var unit:String
    private var windowSize = 512
    private val textWidth = 60
    private val c = 154000.0
    private var isRunning:Boolean = false

    private lateinit var bmp: Bitmap
    private var ipSize:Int=0
    private val fmList = mutableListOf<Double>()

    private val xAnt = 0
    private var yAnt = 0.0f



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

        imageView = findViewById(R.id.imageView)
        ipText = findViewById(R.id.ipText)
        dBText = findViewById(R.id.dbText)
        seekdB = findViewById(R.id.umbralSeek)
        checkEnvol = findViewById(R.id.checkEnvol)
        seekdB.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                maxDb = progress
                dBText.text = "Umbral: $maxDb dB"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}

        })
        imageView.setOnClickListener {
            if (!isRunning) {
                isRunning = true
                recordAudioWithPermissions()
            } else {
                isRunning = false
            }
        }

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
        }, 2000)
    }

    private fun loadPreferences() {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        ipSize = (1f/(windowSize.toFloat()/Fs)).toInt()
        unit = sharedPreferences.getString("units","cm/s")!!
        Fs = sharedPreferences.getString("fs","11025")!!.toInt()
        windowSize = sharedPreferences.getString("window","512")!!.toInt()
        val time = sharedPreferences.getString("time","4")!!.toInt()+1
        val ms = (windowSize.toFloat()/Fs)
        MAX_ARRAY_SIZE = ((1/ms)*time).toInt()
    }


    override fun onResume() {
        super.onResume()
        // Registrar listener para cambios en preferencias
        loadPreferences()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        bmp = Bitmap.createBitmap(imageView.width, imageView.height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.BLACK)
    }

    @SuppressLint("MissingPermission")
    fun recordAudio() {
        val RECORDER_SAMPLERATE = Fs//8000
        val RECORDER_CHANNELS: Int = AudioFormat.CHANNEL_IN_MONO
        val RECORDER_AUDIO_ENCODING: Int = AudioFormat.ENCODING_PCM_16BIT
        val butterSize = AudioRecord.getMinBufferSize(
            RECORDER_SAMPLERATE,
            RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING
        )
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

        thread {
            recorder.startRecording()
            //audioTrack.play()
            //audioTrack.setVolume(AudioTrack.getMaxVolume())
            while (isRunning) {
                // gets the voice output from microphone to byte format
                val bytesRead = recorder.read(buffer, 0, frequenciesCount)
                /*if (bytesRead > 0) {
                    audioTrack.write(buffer, 0, bytesRead)
                }*/
                for (fr in 0 until frequenciesCount) {
                    x[fr] = buffer[fr] * 1.0
                    y[fr] = 0.0
                }
                fft.process(x, y)
                //val threshold = calculateNoiseThreshold(y)
                //y = applyThreshold(y, threshold)
                for (fr in 0 until frequenciesCount) {
                    val mag_abs = Math.abs(y[fr])
                    data[currentIndex][fr] = mag_abs
                }
                /*val yabs = y.map { it.absoluteValue }
                val fmax = yabs.max()
                val fmin = yabs.min()
                val sum = yabs.sum()*/
                val fmed = fMeans(data[currentIndex])

                if (fmList.size < ipSize){
                    fmList.add(fmed.toDouble())
                }
                else{
                    if (currentIndex % ipSize == 0){
                        val fmm = fmList.sum()/fmList.size
                        val fmax = fmList.max()
                        val fmin = fmList.min()
                        ip = if (fmm > 0.0000001) (fmax - fmin) / fmm else 0.0
                        fmList.removeAt(0)
                        fmList.add(fmed.toDouble())
                    }
                }

                render()
                currentIndex = if (currentIndex == (MAX_ARRAY_SIZE - 1)) 0 else currentIndex + 1
            }
            recorder.stop()
            //audioTrack.stop()
        }
    }

    private fun render() {
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
                if(unit == "cm/s") {
                    var vel = getVelocidad(i.toDouble())
                    vel = Math.round(vel / 10.0) * 10.0
                    c.drawText(" ${String.format("%.0f", vel)}", 0f, yt - textHeight, paint)
                }
                else{
                    c.drawText(" "+i/1000, 0f, yt - textHeight, paint)
                }
            }
            else {
                paint.textSize = 25f
                c.drawText(unit, 0f, yt, paint)
            }
        }
        val max_time = ((windowSize.toFloat()/Fs)* MAX_ARRAY_SIZE).toInt()+1
        for(i in 0 until max_time){
            val xt = (bmp.width-textHeight) * (1f - (i.toFloat() / max_time))
            c.drawText(" " + i + "s", xt, bmp.height.toFloat(), paint)
        }
        repeat(MAX_ARRAY_SIZE) { x ->
            val index =
                if (currentIndex + x + 1 < MAX_ARRAY_SIZE) currentIndex + x + 1 else currentIndex + x + 1 - MAX_ARRAY_SIZE
            val min = data[index].min()
            val max = data[index].max()
            /*val maxPox = data[index].withIndex()
                .filter { it.index < halfOfFrequencies } // Filtrar elementos hasta la mitad
                .maxByOrNull { it.value }*/
            for (y in 0 until halfOfFrequencies) {
                val magdB = 20* log(data[index][y],10.0)
                if (magdB < maxDb)
                    paint.color = getColorByValue(0.0, min, max)
                else
                    paint.color = getColorByValue(data[index][y], min, max)
                c.drawRect(
                    x * (rectWidth)+textWidth ,
                    (halfOfFrequencies - y) * rectHeight-textHeight,
                    (x + 1) * (rectWidth) +textWidth,
                    (halfOfFrequencies - y + 1) * rectHeight-textHeight,
                    paint
                )
            }

            if (checkEnvol.isChecked) {
                paint.color = Color.WHITE
                paint.strokeWidth = 2f
                val fMedia = fMeans(data[index])
                val yAct = (halfOfFrequencies - fMedia/*maxPox!!.index*/) * rectHeight - textHeight
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
            imageView.invalidate()
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {

                recordAudio()
            } else {

                /*Toast.makeText(
                    applicationContext,
                    "Приложению нужно разрешение для записи звука",
                    Toast.LENGTH_SHORT
                ).show()*/
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
    /*val freq = Math.PI * 2 / 1.5
    val red = ((sin(freq * normalizedValue + 0) * 127 + 128) * normalizedValue).roundToInt()
    val green = ((sin(freq * normalizedValue + 2) * 127 + 128) * normalizedValue).roundToInt()
    val blue = ((sin(freq * normalizedValue + 4) * 127 + 128) * normalizedValue).roundToInt()
    */
    val freq = Math.PI.toFloat() * 2/1.5 // Frecuencia para cubrir 0-1

    val red = ((cos(freq * normalizedValue) * 127.5 + 127.5) * normalizedValue).roundToInt()
    val green = (sin(freq * normalizedValue) * 127.5 * normalizedValue + 127.5 * normalizedValue).roundToInt()
    val blue = ((sin(freq * normalizedValue - Math.PI.toFloat()) * 127.5 + 127.5) * normalizedValue).roundToInt()

    return Color.rgb(blue, green, red)
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