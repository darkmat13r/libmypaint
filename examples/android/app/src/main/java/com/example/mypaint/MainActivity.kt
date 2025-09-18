package com.example.mypaint

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MyPaintScreen()
            }
        }
    }
}

@Composable
private fun MyPaintScreen() {
    val context = LocalContext.current

    // Load brush preset once when the Composable enters composition
    LaunchedEffect(Unit) {
        try {
            context.assets.open("pencil_soft_2b.myb").use { input ->
                val json = input.bufferedReader().readText()
                val ok = MyPaintBridge().loadBrushFromString(json)
                if (!ok) {
                    android.util.Log.w("MainActivity", "Failed to load brush preset from assets")
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "No .myb preset found in assets: ${e.message}")
        }
    }

    // Load preview bitmap from assets with fallback to base64
    val previewBitmap by remember {
        mutableStateOf(runCatching {
            context.assets.open("2B_pencil_prev.png").use { input ->
                BitmapFactory.decodeStream(input)
            }
        }.getOrElse {
            try {
                context.assets.open("pencil_soft_2b.png.b64").use { input ->
                    val b64 = input.bufferedReader().readText()
                    val data = Base64.decode(b64.trim(), Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(data, 0, data.size)
                }
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "No brush preview found in assets: ${e.message}")
                null
            }
        })
    }

    var drawingView by remember { mutableStateOf<DrawingView?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Drawing area fills available space
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    DrawingView(ctx).also { dv ->
                        drawingView = dv
                    }
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap!!.asImageBitmap(),
                    contentDescription = "Brush preview",
                    modifier = Modifier.size(72.dp)
                )
            } else {
                Text("Brush preview unavailable")
            }

            Button(onClick = { drawingView?.clear() }) {
                Text("Clear")
            }
        }
    }
}
