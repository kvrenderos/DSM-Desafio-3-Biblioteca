package com.example.desafiobiblioteca

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.File

/** Locally drawn fixture for the image picker test; not an actual published book cover. */
fun coverFixture(directory: File): File {
    val image = Bitmap.createBitmap(480, 720, Bitmap.Config.ARGB_8888)
    Canvas(image).apply {
        drawColor(Color.parseColor("#F3E2C6"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2B2F35"); textSize = 38f; textAlign = Paint.Align.CENTER }
        drawText("EL PRINCIPITO", 240f, 110f, paint)
        paint.textSize = 18f
        drawText("PORTADA DE PRUEBA", 240f, 155f, paint)
        paint.color = Color.parseColor("#C7A1AD")
        drawCircle(240f, 365f, 110f, paint)
        paint.color = Color.parseColor("#79505E")
        drawCircle(240f, 340f, 24f, paint)
        drawRect(224f, 362f, 256f, 427f, paint)
        paint.color = Color.parseColor("#2B2F35"); paint.textSize = 19f
        drawText("Antoine de Saint-Exupéry", 240f, 625f, paint)
    }
    val file = File(directory, "portada-prueba.png")
    file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    image.recycle()
    return file
}
