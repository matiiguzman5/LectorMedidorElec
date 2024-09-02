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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.AlertDialog
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
    var manualBarcode by remember { mutableStateOf("") } // Estado para el ingreso manual del código de barras
    var showDialog by remember { mutableStateOf(false) } // Para el diálogo de eliminar el último consumo
    var showDeleteAllDialog by remember { mutableStateOf(false) } // Para el diálogo de eliminar todos los consumos
    var deleteConfirmationText by remember { mutableStateOf("") } // Texto que el usuario debe ingresar para confirmar
    val focusRequesterConsumption = remember { FocusRequester() }


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
            value = manualBarcode,
            onValueChange = { manualBarcode = it },
            label = { Text("Ingresar código de barras manualmente") },
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = androidx.compose.ui.graphics.Color(0xFFFFCDD2),
                focusedIndicatorColor = androidx.compose.ui.graphics.Color(0xFFC62828),
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color(0xFFB71C1C)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))
        
        Text(text =  "Ingreso de Consumo", style = MaterialTheme.typography.h6)

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = consumption,
            onValueChange = { consumption = it },
            label = { Text("Ingresar consumo eléctrico") },
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequesterConsumption),
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = androidx.compose.ui.graphics.Color(0xFFBBDEFB), // Azul claro (Color de fondo)
                focusedIndicatorColor = androidx.compose.ui.graphics.Color(0xFF1976D2), // Azul oscuro (Color del borde cuando está enfocado)
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color(0xFF0D47A1)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val barcodeToCheck = if (manualBarcode.isNotBlank()) manualBarcode else MainActivity.currentCode
                if (barcodeToCheck != null && consumption.isNotBlank()) {
                    val exists = meterData.any { it.first == barcodeToCheck }
                    if (!exists) {
                        meterData = meterData + (barcodeToCheck to consumption)
                        saveMeterData(context, meterData)
                        Log.d("MainScreen", "Código y consumo agregados: $barcodeToCheck - $consumption")
                        manualBarcode = ""
                        MainActivity.currentCode = null
                        consumption = ""
                    } else {
                        Toast.makeText(context, "Este medidor ya fue agregado.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Log.d("MainScreen", "Falta código o consumo")
                    Toast.makeText(context, "Ingrese un código de barras y un consumo", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Medidores Cargados:", style = MaterialTheme.typography.h6)
        LazyColumn(modifier = Modifier
            .fillMaxSize()
            .weight(1f)) {
            items(meterData) { (meter, consumption) ->
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)) {
                    Text(text = meter, modifier = Modifier.weight(1f))
                    Text(text = consumption, modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                showDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Eliminar Último Consumo")
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(text = "Confirmación") },
                text = { Text("¿Estás seguro de que quieres borrar el último consumo?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (meterData.isNotEmpty()) {
                                meterData = meterData.dropLast(1)
                                saveMeterData(context, meterData)
                                Toast.makeText(context, "Último consumo eliminado", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "No hay consumos para eliminar", Toast.LENGTH_LONG).show()
                            }
                            showDialog = false
                        }
                    ) {
                        Text("Sí")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDialog = false }
                    ) {
                        Text("No")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                showDeleteAllDialog = true // Mostrar el diálogo de confirmación para eliminar todos los consumos
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Borrar")
        }

        if (showDeleteAllDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteAllDialog = false },
                title = { Text(text = "Confirmación") },
                text = {
                    Column {
                        Text("Para eliminar todos los consumos, escribe 'BORRAR' abajo:")
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = deleteConfirmationText,
                            onValueChange = { deleteConfirmationText = it },
                            label = { Text("Escribe BORRAR") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (deleteConfirmationText == "BORRAR") {
                                meterData = emptyList()
                                saveMeterData(context, meterData)
                                Toast.makeText(context, "Todos los medidores han sido borrados", Toast.LENGTH_LONG).show()
                                deleteConfirmationText = "" // Reiniciar el texto del campo
                                showDeleteAllDialog = false
                            } else {
                                Toast.makeText(context, "Texto incorrecto. Escribe 'BORRAR' para confirmar.", Toast.LENGTH_LONG).show()
                            }
                        }
                    ) {
                        Text("Confirmar")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            deleteConfirmationText = "" // Reiniciar el texto del campo
                            showDeleteAllDialog = false
                        }
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

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
