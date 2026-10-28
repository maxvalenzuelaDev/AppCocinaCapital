package com.cocinacapital.app.data.notificaciones

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cocinacapital.app.MainActivity
import com.cocinacapital.app.R
import com.cocinacapital.app.data.model.LocalGastronomico
import com.cocinacapital.app.data.model.Promocion
import com.cocinacapital.app.data.model.elegirPromocionParaAviso
import com.cocinacapital.app.data.remote.SupabaseProvider
import com.cocinacapital.app.di.AppContainer
import com.cocinacapital.app.util.distanciaKm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Avisos de promociones (HU-22, RF-15). Con "Promociones" activado en Perfil, cada hora se revisa si
 * hay una promoción afín que se pueda usar ahora y se avisa con una notificación local que abre su
 * detalle. Respeta "Solo cerca de mí" y "Solo de mis cocinas preferidas" y no repite promociones.
 */
object AvisosPromociones {
    const val EXTRA_PROMOCION = "com.cocinacapital.app.extra.PROMOCION"
    private const val CANAL = "promociones"
    private const val TRABAJO_PERIODICO = "avisos_promociones"
    private const val TRABAJO_AHORA = "avisos_promociones_ahora"
    private const val ARCHIVO_AVISADAS = "avisos_promociones"

    private val conRed = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Al activar los avisos: revisa una vez ahora y luego cada hora. */
    fun activar(context: Context) {
        val trabajos = WorkManager.getInstance(context)
        trabajos.enqueueUniquePeriodicWork(
            TRABAJO_PERIODICO,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RevisarPromocionesWorker>(1, TimeUnit.HOURS).setConstraints(conRed).build()
        )
        trabajos.enqueueUniqueWork(
            TRABAJO_AHORA,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RevisarPromocionesWorker>().setConstraints(conRed).build()
        )
    }

    fun desactivar(context: Context) {
        WorkManager.getInstance(context).run {
            cancelUniqueWork(TRABAJO_PERIODICO)
            cancelUniqueWork(TRABAJO_AHORA)
        }
    }

    /** Android 13+ exige el permiso; antes basta con que la persona no haya bloqueado las notificaciones. */
    fun puedeNotificar(context: Context): Boolean {
        val permiso = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permiso && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    internal fun yaAvisadas(context: Context, usuarioId: String): Set<String> =
        context.getSharedPreferences(ARCHIVO_AVISADAS, Context.MODE_PRIVATE)
            .getStringSet("avisadas_$usuarioId", emptySet()).orEmpty()

    private fun recordar(context: Context, usuarioId: String, promocionId: String) {
        val archivo = context.getSharedPreferences(ARCHIVO_AVISADAS, Context.MODE_PRIVATE)
        archivo.edit().putStringSet("avisadas_$usuarioId", yaAvisadas(context, usuarioId) + promocionId).apply()
    }

    internal fun mostrar(context: Context, usuarioId: String, promocion: Promocion, local: LocalGastronomico) {
        crearCanal(context)
        val abrir = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_PROMOCION, promocion.id)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val alTocar = PendingIntent.getActivity(
            context, promocion.id.hashCode(), abrir, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val aviso = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setColor(ContextCompat.getColor(context, R.color.marca_terracota))
            .setContentTitle(context.getString(R.string.aviso_titulo, promocion.descuentoPorcentaje, local.nombre))
            .setContentText(context.getString(R.string.aviso_texto, promocion.titulo))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.aviso_texto, promocion.titulo)))
            .setContentIntent(alTocar)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(promocion.id.hashCode(), aviso)
            recordar(context, usuarioId, promocion.id)
        } catch (_: SecurityException) {
            // Se revocó el permiso justo ahora: no se avisa ni se marca como avisada.
        }
    }

    private fun crearCanal(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val canal = NotificationChannel(CANAL, context.getString(R.string.aviso_canal), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.aviso_canal_descripcion) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }
}

/** Revisa las promociones vigentes y avisa la mejor, si corresponde. */
class RevisarPromocionesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!SupabaseProvider.isConfigured || !AvisosPromociones.puedeNotificar(applicationContext)) return Result.success()
        // La sesión se restaura desde el almacenamiento del teléfono; sin sesión no hay a quién avisar.
        val usuario = withTimeoutOrNull(ESPERA_SESION_MS) { AppContainer.perfilSesion.usuario.first { it != null } }
            ?: return Result.success()
        val preferencias = AppContainer.preferenciasRepository.observar().first()
        if (!preferencias.notificaciones) return Result.success()
        return try {
            val catalogo = AppContainer.homeRepository
            val posicion = AppContainer.ubicacionRepository.posicion.first()
            val locales = catalogo.getLocales().map { local ->
                val lat = local.latitud
                val lon = local.longitud
                local.copy(distanciaKm = if (posicion != null && lat != null && lon != null)
                    distanciaKm(posicion.latitud, posicion.longitud, lat, lon) else null)
            }
            val elegida = elegirPromocionParaAviso(
                catalogo.getPromocionesVigentes(), locales, preferencias, Instant.now(),
                AvisosPromociones.yaAvisadas(applicationContext, usuario.id)
            )
            if (elegida != null) {
                val local = locales.first { it.id == elegida.localId }
                AvisosPromociones.mostrar(applicationContext, usuario.id, elegida, local)
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sin red o error del servidor: se vuelve a intentar en la próxima revisión.
            Result.success()
        }
    }

    private companion object {
        const val ESPERA_SESION_MS = 10_000L
    }
}
