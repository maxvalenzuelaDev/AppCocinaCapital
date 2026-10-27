package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.auth.SesionDePrueba
import com.cocinacapital.app.data.auth.SesionDePrueba.Companion.LUIS
import com.cocinacapital.app.data.local.FavoritosDaoEnMemoria
import com.cocinacapital.app.data.model.Favorito
import com.cocinacapital.app.data.model.TipoFavorito
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class FavoritosRepositoryTest {
    private val dao = FavoritosDaoEnMemoria()
    private val sesion = SesionDePrueba()
    private val repo = FavoritosRepository(dao, sesion)

    private val sushi = Favorito("l5", TipoFavorito.LOCAL, "Sushi Muelle", "Sushi · Valparaíso")
    private val cafe = Favorito("l2", TipoFavorito.LOCAL, "Café Cerro Alegre", "Cafetería · Valparaíso")
    private val promoCafe = Favorito("l2", TipoFavorito.PROMOCION, "Café + kuchen", "Café Cerro Alegre")

    @Test fun `alternar agrega un favorito y al repetirlo lo quita`() = runBlocking {
        repo.alternar(sushi)
        assertEquals(listOf(sushi), repo.observar().first())
        repo.alternar(sushi)
        assertTrue(repo.observar().first().isEmpty())
    }

    @Test fun `la lista viene ordenada por nombre`() = runBlocking {
        repo.alternar(sushi)
        repo.alternar(cafe)
        assertEquals(listOf("Café Cerro Alegre", "Sushi Muelle"), repo.observar().first().map { it.nombre })
    }

    @Test fun `un local y una promocion con el mismo id son favoritos distintos`() = runBlocking {
        repo.alternar(cafe)
        repo.alternar(promoCafe)
        assertEquals(setOf("l2"), repo.observarIds(TipoFavorito.LOCAL).first())
        assertEquals(setOf("l2"), repo.observarIds(TipoFavorito.PROMOCION).first())
        repo.eliminar(promoCafe)
        assertEquals(listOf(cafe), repo.observar().first())
    }

    @Test fun `los favoritos de una cuenta no se ven en otra`() = runBlocking {
        repo.alternar(sushi)
        sesion.usuario.value = LUIS
        assertTrue(repo.observar().first().isEmpty())
        repo.alternar(cafe)
        assertEquals(listOf(cafe), repo.observar().first())
        sesion.usuario.value = SesionDePrueba.ANA
        assertEquals(listOf(sushi), repo.observar().first())
    }
}
