package com.tan.gratify.expect.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
internal fun PhotoCropDialog(context: Context, uri: Uri, shape: PhotoCropShape, onCancel: () -> Unit, onSaved: (String) -> Unit) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var crop by remember { mutableStateOf<SquarePhotoCrop?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var viewport by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(uri, attempt) {
        failed = false
        try {
            val image = withContext(Dispatchers.IO) { decodeCropPhoto(context, uri) }
            bitmap = image
            crop = SquarePhotoCrop.centered(image.width, image.height)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    Dialog(onDismissRequest = { if (!saving) onCancel() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.padding(16.dp).widthIn(max = 520.dp).fillMaxWidth()) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.photo_crop_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(Res.string.photo_crop_hint), style = MaterialTheme.typography.bodyMedium)
                val image = bitmap
                if (image != null) {
                    val composeImage = remember(image) { image.asImageBitmap() }
                    Canvas(Modifier.fillMaxWidth().aspectRatio(1f).onSizeChanged { viewport = it.width.toFloat() }
                        .pointerInput(image, saving) {
                            if (!saving) detectTransformGestures { _, pan, zoom, _ ->
                                crop = crop?.move(image.width, image.height, pan.x, pan.y, viewport, zoom)
                            }
                        }) {
                        val box = crop!!.pixels(image.width, image.height)
                        drawImage(composeImage, srcOffset = IntOffset(box.x, box.y), srcSize = IntSize(box.side, box.side),
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
                        if (shape == PhotoCropShape.CIRCLE) {
                            val mask = Path().apply { fillType = PathFillType.EvenOdd; addRect(Rect(0f, 0f, size.width, size.height)); addOval(Rect(0f, 0f, size.width, size.height)) }
                            drawPath(mask, Color.Black.copy(alpha = .6f))
                            drawCircle(Color.White, radius = size.width / 2f - 1f, style = Stroke(2f))
                        } else {
                            drawRect(Color.White, style = Stroke(2f))
                            for (i in 1..2) {
                                drawLine(Color.White.copy(alpha = .45f), androidx.compose.ui.geometry.Offset(size.width * i / 3, 0f), androidx.compose.ui.geometry.Offset(size.width * i / 3, size.height))
                                drawLine(Color.White.copy(alpha = .45f), androidx.compose.ui.geometry.Offset(0f, size.height * i / 3), androidx.compose.ui.geometry.Offset(size.width, size.height * i / 3))
                            }
                        }
                    }
                    TextButton(onClick = { crop = SquarePhotoCrop.centered(image.width, image.height) }, enabled = !saving) {
                        Text(stringResource(Res.string.photo_crop_reset))
                    }
                } else if (!failed) CircularProgressIndicator()
                if (failed) {
                    Text(stringResource(Res.string.photo_crop_failed), color = MaterialTheme.colorScheme.error)
                    if (bitmap == null) TextButton(onClick = { attempt++ }) { Text(stringResource(Res.string.retry)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel, enabled = !saving) { Text(stringResource(Res.string.cancel)) }
                    Button(enabled = bitmap != null && !saving, onClick = {
                        saving = true; failed = false
                        val source = bitmap!!; val pixels = crop!!.pixels(source.width, source.height)
                        scope.launch {
                            try {
                                val output = withContext(Dispatchers.IO) { exportCropPhoto(context, source, pixels) }
                                onSaved(output)
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { failed = true }
                            finally { saving = false }
                        }
                    }) {
                        if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(stringResource(Res.string.photo_crop_use))
                    }
                }
            }
        }
    }
}

