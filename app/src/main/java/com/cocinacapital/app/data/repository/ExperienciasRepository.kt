package com.cocinacapital.app.data.repository

import com.cocinacapital.app.data.model.Experiencia

interface ExperienciasRepository {
    suspend fun listar(): List<Experiencia>
    suspend fun getExperiencia(id: String): Experiencia?
}
