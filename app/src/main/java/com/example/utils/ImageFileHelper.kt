package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object ImageFileHelper {

    suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileNamePrefix: String = "SnapScan_Edit"): Result<Uri> =
        withContext(Dispatchers.IO) {
            try {
                val timestamp = System.currentTimeMillis()
                val filename = "${fileNamePrefix}_$timestamp.png"

                var fos: OutputStream? = null
                var imageUri: Uri? = null

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/SnapScanAI")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }

                    val resolver = context.contentResolver
                    val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    imageUri = resolver.insert(collection, contentValues)

                    if (imageUri != null) {
                        fos = resolver.openOutputStream(imageUri)
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos!!)
                        fos.close()

                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(imageUri, contentValues, null, null)
                    }
                } else {
                    val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val snapScanDir = File(imagesDir, "SnapScanAI").apply { if (!exists()) mkdirs() }
                    val imageFile = File(snapScanDir, filename)
                    fos = FileOutputStream(imageFile)
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    fos.close()

                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DATA, imageFile.absolutePath)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    }
                    imageUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                }

                if (imageUri != null) {
                    Result.success(imageUri)
                } else {
                    Result.failure(Exception("Failed to create MediaStore entry"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun shareBitmap(context: Context, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        try {
            val cachePath = File(context.cacheDir, "shared_images")
            cachePath.mkdirs()
            val file = File(cachePath, "SnapScan_Share_${System.currentTimeMillis()}.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, "Edited with SnapScan AI")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Share edited photo via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error sharing image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
