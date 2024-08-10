package com.example.lectormedidorelec

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
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
import com.example.lectormedidorelec.ui.theme.LectorMedidorElecTheme
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.google.zxing.integration.android.IntentIntegrator
import java.io.File
import java.io.FileOutputStream


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
                saveDataToFile(context, meterData)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Descargar a txt")
        }

        Spacer(modifier = Modifier.height(16.dp))


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

fun saveDataToFile(context: android.content.Context, meterData: List<Pair<String, String>>) {
    val downloadsDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val file = File(downloadsDirectory, "consumos.txt")
    FileOutputStream(file).use { fos ->
        meterData.forEach { (meter, consumption) ->
            fos.write("$meter,$consumption\n".toByteArray())
        }
    }
    Toast.makeText(context, "Archivo guardado en: ${file.absolutePath}", Toast.LENGTH_LONG).show()
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    LectorMedidorElecTheme {
        MainScreen(onScanClick = {})
    }
}
