package com.example.desafiobiblioteca

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Copies the selected image into private storage, independent of the gallery URI. */
class CoverStore(private val context: Context, uid: String) {
    private val owner = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }
    private val directory = File(context.filesDir, "covers/$owner")

    private fun sample(width: Int, height: Int, maxEdge: Int): Int {
        var size = 1
        while (width / size > maxEdge || height / size > maxEdge) size *= 2
        return size
    }
    fun file(path: String?): File? {
        if (path == null || !path.matches(Regex("covers/$owner/[a-f0-9-]+\\.jpg"))) return null
        return File(context.filesDir, path).takeIf { it.isFile }
    }
    fun read(path: String?, maxEdge: Int = 500): Bitmap? {
        val file = file(path) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample(bounds.outWidth, bounds.outHeight, maxEdge)
        })
    }
    fun importImage(uri: Uri): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "El archivo seleccionado no es una imagen compatible." }
        val options = BitmapFactory.Options().apply { inSampleSize = sample(bounds.outWidth, bounds.outHeight, 1200) }
        val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("No se pudo leer la imagen.")
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
            }
        }
        val normalized = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (normalized !== decoded) decoded.recycle()
        check(directory.isDirectory || directory.mkdirs()) { "No se pudo preparar el almacenamiento de portadas." }
        val result = File(directory, "${UUID.randomUUID()}.jpg")
        try {
            result.outputStream().use { check(normalized.compress(Bitmap.CompressFormat.JPEG, 88, it)) }
            return "covers/$owner/${result.name}"
        } catch (error: Exception) { result.delete(); throw error }
        finally { normalized.recycle() }
    }
    fun remove(path: String?) { file(path)?.delete() }
}
