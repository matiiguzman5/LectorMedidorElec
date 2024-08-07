package com.example.lectormedidorelec

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.lectormedidorelec.ui.theme.LectorMedidorElecTheme
import com.google.zxing.integration.android.IntentIntegrator


class MainActivity : ComponentActivity() {
    private lateinit var barcodeResultLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        barcodeResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val intentResult = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
            if (intentResult != null) {
                if (intentResult.contents != null) {
                    val scannedCode = intentResult.contents
                    Toast.makeText(this, "Código escaneado: $scannedCode", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Escaneo cancelado", Toast.LENGTH_LONG).show()
                }
            }
        }

        setContent {
            LectorMedidorElecTheme {
                MainScreen(onScanClick = {
                    val integrator = IntentIntegrator(this)
                    barcodeResultLauncher.launch(integrator.createScanIntent())
                })
            }
        }
    }
}

@Composable
fun MainScreen(onScanClick: () -> Unit) {
    var consumption by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextField(
            value = consumption,
            onValueChange = { consumption = it },
            label = { Text("Ingresar consumo eléctrico") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onScanClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Escanear Código de Barras")
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
