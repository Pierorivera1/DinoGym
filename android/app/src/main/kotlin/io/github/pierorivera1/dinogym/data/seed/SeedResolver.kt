package io.github.pierorivera1.dinogym.data.seed

import io.github.pierorivera1.dinogym.domain.model.ExerciseTemplate
import io.github.pierorivera1.dinogym.domain.model.ProgramTemplate
import io.github.pierorivera1.dinogym.domain.model.RoutineItemTemplate
import io.github.pierorivera1.dinogym.domain.model.RoutineTemplate

/**
 * Turns a [SeedProgram] into a [ProgramTemplate] in the phone's language, for
 * [io.github.pierorivera1.dinogym.data.repository.ProgramInstaller]. [text] resolves a key
 * (SeedStrings in the app, a plain map in tests).
 */
object SeedResolver {
    fun resolve(seed: SeedProgram, text: (SeedText) -> String): ProgramTemplate {
        val exercises = seed.exercises.associate { e ->
            e.name to ExerciseTemplate(
                name = text(e.name),
                icon = e.icon,
                kind = e.kind,
                template = e.template?.let(text),
            )
        }
        val routineIndex = seed.routines.withIndex().associate { (i, r) -> r.name to i }
        return ProgramTemplate(
            name = text(seed.name),
            type = seed.type,
            routines = seed.routines.map { r ->
                RoutineTemplate(
                    name = text(r.name),
                    items = r.items.map { item ->
                        RoutineItemTemplate(
                            exercise = exercises.getValue(item.exercise),
                            sets = item.sets,
                            repMin = item.reps,
                            repMax = item.repMax,
                            restSeconds = item.restSeconds,
                        )
                    },
                )
            },
            cycle = seed.cycle.map { name -> name?.let { routineIndex.getValue(it) } },
        )
    }
}
