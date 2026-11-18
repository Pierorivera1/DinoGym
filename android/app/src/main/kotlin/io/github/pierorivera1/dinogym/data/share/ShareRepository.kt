package io.github.pierorivera1.dinogym.data.share

import io.github.pierorivera1.dinogym.api.apis.SharesApi
import io.github.pierorivera1.dinogym.api.infrastructure.HttpResponse
import io.github.pierorivera1.dinogym.api.models.ShareCreated
import io.github.pierorivera1.dinogym.api.models.ShareSnapshot
import io.github.pierorivera1.dinogym.data.account.AccessTokenResult
import io.github.pierorivera1.dinogym.data.account.SessionManager
import io.github.pierorivera1.dinogym.data.local.DinoDatabase
import io.github.pierorivera1.dinogym.data.local.toDomain
import io.github.pierorivera1.dinogym.data.repository.ProgramInstaller
import io.github.pierorivera1.dinogym.domain.model.ExerciseTemplate
import io.github.pierorivera1.dinogym.domain.model.ProgramTemplate
import io.github.pierorivera1.dinogym.domain.model.RoutineItemTemplate
import io.github.pierorivera1.dinogym.domain.model.RoutineTemplate
import io.github.pierorivera1.dinogym.domain.rules.InstallRules
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** How publishing a share ended (decision 103). */
sealed interface PublishResult {
    data class Shared(val url: String) : PublishResult

    /** Sharing needs an account; the person is pointed to Settings › ACCOUNT. */
    data object SignedOut : PublishResult
    data object Offline : PublishResult

    /** The day's 20 shares are used up, or API Gateway's throttle answered. */
    data object TooMany : PublishResult

    /** A program with no routine in its cycle: nothing to share. */
    data object Empty : PublishResult

    /** Over the contract's limits (14 routines, 30 lines, 60 days, 64 KiB). */
    data object TooBig : PublishResult

    /** The routine or program was deleted meanwhile, or the server refused or failed. */
    data object Failed : PublishResult
}

/** How reading a share ended (decision 102). */
sealed interface FetchResult {
    data class Found(val content: SharedContent) : FetchResult
    data object NotFound : FetchResult
    data object Offline : FetchResult

    /** Made by a newer app, or a snapshot this app cannot read. */
    data object Unreadable : FetchResult
    data object Failed : FetchResult
}

/**
 * Shareable routines on the phone (task 23): builds a snapshot from Room and publishes it,
 * reads a share by id, and installs it through [ProgramInstaller] (decision 93), so an
 * import follows the same rules as a starter program.
 *
 * Shares are read from this build's own API, not from the link's host: debug builds read
 * staging, release builds prod (decision 99).
 */
@Singleton
class ShareRepository @Inject constructor(
    private val db: DinoDatabase,
    private val api: SharesApi,
    private val sessions: SessionManager,
    private val installer: ProgramInstaller,
) {

    suspend fun publishRoutine(routineId: String): PublishResult {
        val routine = routineTemplate(routineId) ?: return PublishResult.Failed
        if (ShareMapping.check(routine) != null) return PublishResult.TooBig
        return publish(ShareMapping.toSnapshot(routine))
    }

    suspend fun publishProgram(programId: String): PublishResult {
        val program = programTemplate(programId) ?: return PublishResult.Failed
        when (ShareMapping.check(program)) {
            ShareMapping.Problem.EMPTY -> return PublishResult.Empty
            ShareMapping.Problem.TOO_BIG -> return PublishResult.TooBig
            null -> Unit
        }
        return publish(ShareMapping.toSnapshot(program))
    }

    /**
     * Reads share [id] without an account. A 404 is [FetchResult.NotFound]; no connection is
     * [FetchResult.Offline], so the screen can say that importing needs one.
     */
    suspend fun fetch(id: String): FetchResult {
        val response = try {
            api.getShare(id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            return FetchResult.Offline
        }
        return when {
            response.status == NOT_FOUND -> FetchResult.NotFound
            !response.success -> FetchResult.Failed
            else -> try {
                ShareMapping.fromSnapshot(response.body().snapshot)
                    ?.let { FetchResult.Found(it) } ?: FetchResult.Unreadable
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
                // An enum value or field shape this version does not know.
                FetchResult.Unreadable
            }
        }
    }

    /** Copies a routine into the library of routines; returns its id. */
    suspend fun importRoutine(routine: RoutineTemplate): String = installer.installRoutine(routine)

    /** Copies a program with its routines; active only when none is (decision 48). Returns its id. */
    suspend fun importProgram(program: ProgramTemplate): String = installer.install(program)

    /** Whether a program is active now, so the preview can say if the import will become it. */
    suspend fun hasActiveProgram(): Boolean = db.programDao().getActive() != null

    /** For the preview: which of [exercises] match an active library exercise (decision 93). */
    suspend fun inLibrary(exercises: List<ExerciseTemplate>): Set<ExerciseTemplate> {
        val library = db.exerciseDao().getAll().map { it.toDomain() }
        return exercises.filterTo(mutableSetOf()) { InstallRules.match(library, it) != null }
    }

    private suspend fun publish(snapshot: ShareSnapshot): PublishResult {
        val first = when (val result = sessions.accessToken()) {
            is AccessTokenResult.Token -> result.value
            AccessTokenResult.Offline -> return PublishResult.Offline
            AccessTokenResult.SignedOut -> return PublishResult.SignedOut
        }
        var response = send(first, snapshot) ?: return PublishResult.Offline
        if (response.status == UNAUTHORIZED) {
            // The token was refused although it looked valid (decision 91's rule): refresh
            // once and try once more. At worst the first request was stored too, which only
            // leaves an unused link.
            val second = when (val result = sessions.accessToken(rejected = first)) {
                is AccessTokenResult.Token -> result.value
                AccessTokenResult.Offline -> return PublishResult.Offline
                AccessTokenResult.SignedOut -> return PublishResult.SignedOut
            }
            response = send(second, snapshot) ?: return PublishResult.Offline
        }
        return when {
            response.success -> response.body().url?.let { PublishResult.Shared(it) } ?: PublishResult.Failed
            response.status == UNAUTHORIZED -> PublishResult.SignedOut
            response.status == TOO_MANY_REQUESTS -> PublishResult.TooMany
            response.status == PAYLOAD_TOO_LARGE -> PublishResult.TooBig
            else -> PublishResult.Failed
        }
    }

    private suspend fun send(token: String, snapshot: ShareSnapshot): HttpResponse<ShareCreated>? = try {
        api.setBearerToken(token)
        api.createShare(snapshot)
    } catch (e: CancellationException) {
        throw e
    } catch (_: IOException) {
        null
    }

    private suspend fun routineTemplate(routineId: String): RoutineTemplate? {
        val routine = db.routineDao().getById(routineId) ?: return null
        val items = db.routineDao().getItems(routineId)
        val exercises = db.exerciseDao().getByIds(items.map { it.exerciseId }.distinct()).associateBy { it.id }
        return RoutineTemplate(
            name = routine.name,
            items = items.mapNotNull { item ->
                val exercise = exercises[item.exerciseId]?.toDomain() ?: return@mapNotNull null
                RoutineItemTemplate(
                    exercise = ExerciseTemplate(
                        name = exercise.name,
                        icon = exercise.icon,
                        kind = exercise.kind,
                        template = exercise.template,
                        weightEnabled = exercise.weightEnabled,
                    ),
                    sets = item.sets,
                    repMin = item.repMin,
                    repMax = item.repMax,
                    restSeconds = item.restSeconds,
                )
            },
        )
    }

    /**
     * The program as a template: its cycle, and each routine the cycle uses, once, in the
     * order it first appears. A step whose routine is gone becomes a rest day.
     */
    private suspend fun programTemplate(programId: String): ProgramTemplate? {
        val program = db.programDao().getById(programId)?.toDomain() ?: return null
        val steps = db.programDao().getCycle(programId)
        val routines = mutableListOf<RoutineTemplate>()
        val index = mutableMapOf<String, Int>()
        val cycle = steps.map { step ->
            val routineId = step.routineId ?: return@map null
            index[routineId] ?: routineTemplate(routineId)?.let { template ->
                routines += template
                (routines.size - 1).also { index[routineId] = it }
            }
        }
        return ProgramTemplate(name = program.name, type = program.type, routines = routines, cycle = cycle)
    }

    private companion object {
        const val UNAUTHORIZED = 401
        const val NOT_FOUND = 404
        const val PAYLOAD_TOO_LARGE = 413
        const val TOO_MANY_REQUESTS = 429
    }
}
