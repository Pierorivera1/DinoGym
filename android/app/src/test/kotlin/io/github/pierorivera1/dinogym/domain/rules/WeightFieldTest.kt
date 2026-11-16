package io.github.pierorivera1.dinogym.domain.rules

import io.github.pierorivera1.dinogym.domain.model.ProgramType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightFieldTest {

    @Test
    fun gymAlwaysShowsTheWeightField() {
        assertTrue(WeightField.isShown(ProgramType.GYM, exerciseWeightEnabled = false))
        assertTrue(WeightField.isShown(ProgramType.GYM, exerciseWeightEnabled = true))
    }

    @Test
    fun calisthenicsShowsItOnlyWhenTheExerciseHasItOn() {
        assertFalse(WeightField.isShown(ProgramType.CALISTHENICS, exerciseWeightEnabled = false))
        assertTrue(WeightField.isShown(ProgramType.CALISTHENICS, exerciseWeightEnabled = true))
    }
}
