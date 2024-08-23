package com.App.lectormedidorelec

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.App.lectormedidorelec.ui.theme.LectorMedidorElecTheme
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.google.zxing.integration.android.IntentIntegrator
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream


class MainActivity : ComponentActivity() {
    private lateinit var barcodeResultLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        barcodeResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val intentResult = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
            if (intentResult != null && intentResult.contents != null) {
                val scannedCode = intentResult.contents
                currentCode = scannedCode
                Log.d("MainActivity", "Código escaneado: $scannedCode")
                Toast.makeText(this, "Código escaneado: $scannedCode", Toast.LENGTH_LONG).show()
            } else {
                Log.d("MainActivity", "Escaneo cancelado")
                Toast.makeText(this, "Escaneo cancelado", Toast.LENGTH_LONG).show()
            }
        }

        setContent {
            LectorMedidorElecTheme {
                MainScreen(
                    onScanClick = {
                        val integrator = IntentIntegrator(this)
                        barcodeResultLauncher.launch(integrator.createScanIntent())
                    }
                )
            }
        }
    }

    companion object {
        var currentCode: String? = null
    }
}

@Composable
fun MainScreen(onScanClick: () -> Unit) {
    val context = LocalContext.current
    var consumption by remember { mutableStateOf("") }
    var meterData by remember {
        mutableStateOf(loadMeterData(context))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onScanClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Escanear Código de Barras")
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = consumption,
            onValueChange = { consumption = it },
            label = { Text("Ingresar consumo eléctrico") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (MainActivity.currentCode != null && consumption.isNotBlank()) {
                    meterData = meterData + (MainActivity.currentCode!! to consumption)
                    saveMeterData(context, meterData)
                    Log.d("MainScreen", "Código y consumo agregados: ${MainActivity.currentCode} - $consumption")
                    MainActivity.currentCode = null
                    consumption = ""
                } else {
                    Log.d("MainScreen", "Falta código o consumo")
                    Toast.makeText(context, "Ingrese un código de barras y un consumo", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Listo")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Medidores Cargados:", style = MaterialTheme.typography.h6)
        LazyColumn(modifier = Modifier.fillMaxSize().weight(1f)) {
            items(meterData) { (meter, consumption) ->
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Text(text = meter, modifier = Modifier.weight(1f))
                    Text(text = consumption, modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (meterData.isNotEmpty()) {
                    meterData = meterData.dropLast(1)
                    saveMeterData(context, meterData)
                    Toast.makeText(context, "Último consumo eliminado", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "No hay consumos para eliminar", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Eliminar Último Consumo")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                meterData = emptyList()
                saveMeterData(context, meterData)
                Toast.makeText(context, "Medidores borrados", Toast.LENGTH_LONG).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Borrar")
        }
        Button(
            onClick = {
                saveDataToFiles(context, meterData)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Descargar a Archivos")
        }
    }
}

fun saveMeterData(context: Context, meterData: List<Pair<String, String>>) {
    val sharedPreferences = context.getSharedPreferences("MeterData", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    val gson = Gson()
    val json = gson.toJson(meterData)
    editor.putString("meterData", json)
    editor.apply()
}

fun loadMeterData(context: Context): List<Pair<String, String>> {
    val sharedPreferences = context.getSharedPreferences("MeterData", Context.MODE_PRIVATE)
    val gson = Gson()
    val json = sharedPreferences.getString("meterData", null)
    val type = object : TypeToken<List<Pair<String, String>>>() {}.type
    return if (json != null) {
        gson.fromJson(json, type)
    } else {
        emptyList()
    }
}

fun saveDataToFiles(context: Context, meterData: List<Pair<String, String>>) {
    val filename = "consumos.txt"
    val contentResolver = context.contentResolver

    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
        }
    }

    val uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)

    if (uri != null) {
        contentResolver.openOutputStream(uri)?.use { outputStream ->
            writeDataToStream(outputStream, meterData)
            Toast.makeText(context, "Archivo guardado en 'Archivos': ${filename}", Toast.LENGTH_LONG).show()
        }
    } else {
        Toast.makeText(context, "Error al guardar el archivo", Toast.LENGTH_LONG).show()
    }
}

    private fun writeDataToStream(outputStream: OutputStream, meterData: List<Pair<String, String>>) {
        outputStream.bufferedWriter().use { writer ->
            meterData.forEach { (meter, consumption) ->
                writer.write("$meter,$consumption\n")
            }
        }
    }

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    LectorMedidorElecTheme {
        MainScreen(onScanClick = {})
    }
}
