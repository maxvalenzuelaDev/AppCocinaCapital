package com.cocinacapital.app.data.location

import com.cocinacapital.app.data.auth.LoginSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/** Una renovación temporal de Supabase no equivale a cerrar o cambiar la cuenta. */
internal fun Flow<LoginSession>.identidadesUbicacion(): Flow<String?> =
    filter { it is LoginSession.SignedIn || it == LoginSession.SignedOut }
        .map { (it as? LoginSession.SignedIn)?.user?.id }
        .distinctUntilChanged()
