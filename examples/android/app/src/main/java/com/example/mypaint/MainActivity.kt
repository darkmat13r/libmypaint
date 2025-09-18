package com.example.mypaint

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog

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

    data class BrushItem(val displayName: String, val presetPath: String, val previewPath: String?)

    fun discoverBrushes(): List<BrushItem> {
        val out = mutableListOf<BrushItem>()
        fun scan(dir: String) {
            val children = try { context.assets.list(dir) } catch (_: Exception) { null } ?: return
            for (child in children) {
                val path = if (dir.isEmpty()) child else "$dir/$child"
                val sub = try { context.assets.list(path) } catch (_: Exception) { null }
                if (sub != null && sub.isNotEmpty()) {
                    scan(path)
                } else {
                    if (path.endsWith(".myb")) {
                        val baseDir = dir
                        val nameNoExt = child.substringBeforeLast('.')
                        val previewCandidate = if (baseDir.isNotEmpty()) "$baseDir/${nameNoExt}_prev.png" else "${nameNoExt}_prev.png"
                        val preview = try {
                            context.assets.open(previewCandidate).close()
                            previewCandidate
                        } catch (_: Exception) { null }
                        out.add(BrushItem(displayName = nameNoExt, presetPath = path, previewPath = preview))
                    }
                }
            }
        }
        scan("brushes")
        return out.sortedBy { it.displayName.lowercase() }
    }

    var brushes by remember { mutableStateOf<List<BrushItem>>(emptyList()) }
    var currentPreview by remember { mutableStateOf(android.graphics.Bitmap.createBitmap(1,1, android.graphics.Bitmap.Config.ARGB_8888)) }
    var hasPreview by remember { mutableStateOf(false) }
    var showBrushPicker by remember { mutableStateOf(false) }

    var drawingView by remember { mutableStateOf<GLDrawingView?>(null) }
    var brushSize by remember { mutableFloatStateOf(8f) }
    var showColorPicker by remember { mutableStateOf(false) }
    var red by remember { mutableFloatStateOf(0f) }
    var green by remember { mutableFloatStateOf(0.7f) }
    var blue by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(Unit) {
        brushes = discoverBrushes()
        // Load initial brush: prefer first discovered
        val initial = brushes.firstOrNull()
        if (initial != null) {
            try {
                context.assets.open(initial.presetPath).bufferedReader().use { br ->
                    drawingView?.loadBrushFromString(br.readText())
                }
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Failed to load initial brush: ${e.message}")
            }
            // Load preview
            val bmp = try {
                initial.previewPath?.let { p ->
                    context.assets.open(p).use { input -> BitmapFactory.decodeStream(input) }
                }
            } catch (_: Exception) { null }
            if (bmp != null) { currentPreview = bmp; hasPreview = true } else { hasPreview = false }
        } else {
            // Fallback to legacy assets if no brushes found
            try {
                context.assets.open("pencil_soft_2b.myb").use { input ->
                    val json = input.bufferedReader().readText()
                    MyPaintBridge().loadBrushFromString(json)
                }
                val bmp = runCatching {
                    context.assets.open("2B_pencil_prev.png").use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
                if (bmp != null) { currentPreview = bmp; hasPreview = true }
            } catch (_: Exception) {}
        }
    }

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
                    GLDrawingView(ctx).also { dv ->
                        drawingView = dv
                        dv.setBrushSize(brushSize)
                        dv.setColor(red, green, blue)
                    }
                }
            )
        }

        // Controls row: brush preview (click to change), size slider, clear button
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasPreview) {
                        Image(
                            bitmap = currentPreview.asImageBitmap(),
                            contentDescription = "Current brush",
                            modifier = Modifier.size(56.dp).clickable { showBrushPicker = true }
                        )
                    } else {
                        Box(modifier = Modifier.size(56.dp).clickable { showBrushPicker = true }) {
                            Text("Pick brush", modifier = Modifier.align(Alignment.Center))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    // Color chip
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(red, green, blue), shape = CircleShape)
                            .clickable { showColorPicker = true }
                    )
                }

                Button(onClick = { drawingView?.clear() }) {
                    Text("Clear")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Size ${(brushSize).toInt()} px", modifier = Modifier.width(100.dp))
                Slider(
                    value = brushSize,
                    onValueChange = { v ->
                        brushSize = v
                        drawingView?.setBrushSize(v)
                    },
                    valueRange = 1f..100f,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (showBrushPicker) {
            Dialog(onDismissRequest = { showBrushPicker = false }) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("Select Brush", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 96.dp), modifier = Modifier.heightIn(max = 360.dp)) {
                        items(brushes) { item ->
                            Column(
                                modifier = Modifier.padding(8.dp).clickable {
                                    // Load brush
                                    try {
                                        context.assets.open(item.presetPath).bufferedReader().use { br ->
                                            drawingView?.loadBrushFromString(br.readText())
                                        }
                                        val bmp = try {
                                            item.previewPath?.let { p ->
                                                context.assets.open(p).use { BitmapFactory.decodeStream(it) }
                                            }
                                        } catch (_: Exception) { null }
                                        if (bmp != null) { currentPreview = bmp; hasPreview = true } else { hasPreview = false }
                                    } catch (e: Exception) {
                                        android.util.Log.w("MainActivity", "Failed to load brush ${item.displayName}: ${e.message}")
                                    }
                                    showBrushPicker = false
                                }
                            ) {
                                val bmp = remember(item.previewPath) {
                                    try {
                                        item.previewPath?.let { p ->
                                            context.assets.open(p).use { BitmapFactory.decodeStream(it) }
                                        }
                                    } catch (_: Exception) { null }
                                }
                                if (bmp != null) {
                                    Image(bitmap = bmp.asImageBitmap(), contentDescription = item.displayName, modifier = Modifier.size(72.dp))
                                } else {
                                    Box(Modifier.size(72.dp)) { Text("No prev", modifier = Modifier.align(Alignment.Center)) }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(item.displayName, maxLines = 1)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showBrushPicker = false }, modifier = Modifier.align(Alignment.End)) { Text("Close") }
                }
            }
        }

        if (showColorPicker) {
            Dialog(onDismissRequest = { showColorPicker = false }) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Pick Color", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(red, green, blue), shape = CircleShape)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("#%02X%02X%02X".format((red*255).toInt().coerceIn(0,255), (green*255).toInt().coerceIn(0,255), (blue*255).toInt().coerceIn(0,255)))
                    }
                    Spacer(Modifier.height(12.dp))

                    ColorWheel(
                        modifier = Modifier.fillMaxWidth(),
                        initialColor = Color(red, green, blue),
                        showValueSlider = true
                    ) { c ->
                        red = c.red
                        green = c.green
                        blue = c.blue
                        drawingView?.setColor(red, green, blue)
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(onClick = { showColorPicker = false }) { Text("Done") }
                    }
                }
            }
        }
    }
}
