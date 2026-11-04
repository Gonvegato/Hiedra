package com.gonzalo.hiedra

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Dibuja el icono (con la máscara circular y la de cuadrado redondeado de Android) para poder verlo: build/outputs/icono/. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class IconoTest {
    @Test fun dibujaElIcono() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val d = ContextCompat.getDrawable(ctx, R.mipmap.ic_launcher)!!
        val lado = 432
        File("build/outputs/icono").mkdirs()
        for ((nombre, forma) in listOf("circulo" to 0, "cuadrado" to 1)) {
            val bm = Bitmap.createBitmap(lado * 3 / 2, lado * 3 / 2, Bitmap.Config.ARGB_8888)
            val c = Canvas(bm)
            c.drawColor(0xFF3A3A3A.toInt())
            val x0 = (bm.width - lado) / 2
            c.save()
            // Android recorta el icono adaptativo con la máscara de cada móvil: aquí, un círculo o un cuadrado redondeado
            val p = Path().apply { if (forma == 0) addCircle(bm.width / 2f, bm.height / 2f, lado / 2f, Path.Direction.CW) else addRoundRect(x0.toFloat(), x0.toFloat(), (x0 + lado).toFloat(), (x0 + lado).toFloat(), lado * 0.28f, lado * 0.28f, Path.Direction.CW) }
            c.clipPath(p)
            d.setBounds(x0, x0, x0 + lado, x0 + lado); d.draw(c)
            c.restore()
            File("build/outputs/icono/icono_$nombre.png").outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
