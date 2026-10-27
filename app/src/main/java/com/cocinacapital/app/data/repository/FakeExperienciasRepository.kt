package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.model.Experiencia

/** Se inyectan las propuestas al probar; no convierte platos ni promociones en experiencias. */
class FakeExperienciasRepository(private val experiencias: List<Experiencia> = emptyList()) : ExperienciasRepository {
    override suspend fun listar() = experiencias.filter { it.activa && it.local?.id == it.localId }.sortedBy { it.titulo }
    override suspend fun getExperiencia(id: String) = listar().firstOrNull { it.id == id }
}
