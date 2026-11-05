package com.gonzalo.hiedra

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle

/** Sin pantalla: la usan los accesos directos del móvil («Pokémon Esmeralda» en tu escritorio) para lanzar un juego derecho. */
class LanzarActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Biblioteca.cargar(this); Catalogo.cargar(this); Ajustes.cargar(this); NubeP.cargar(this)
        val j = intent.getStringExtra("id")?.let { Biblioteca.por(it) }
        if (j == null) { Lanzar.aviso(this, "Ese juego ya no está en Hiedra"); finish() }
        else if (j.enPc || (j.enOtroDispositivo && !Lanzar.esInterno(Lanzar.elegir(this, j)))) {
            // Un juego del PC (se elige dónde jugar) o con la ROM en otro móvil (se baja de tu kDrive): dentro de la app
            startActivity(Intent(this, MainActivity::class.java).putExtra("ficha", j.id).putExtra("jugar", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        } else Lanzar.lanzar(this, j) { finish() }?.let { Lanzar.aviso(this, it) }
    }

    companion object {
        const val ACCION = "com.gonzalo.hiedra.JUGAR"
        fun intent(c: Context, id: String) = Intent(c, LanzarActivity::class.java).setAction(ACCION).putExtra("id", id)
    }
}
