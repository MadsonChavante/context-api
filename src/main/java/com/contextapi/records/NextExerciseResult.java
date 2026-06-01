package com.contextapi.records;

import com.contextapi.entities.Context;

public record NextExerciseResult(Context context, String promptPt, String expectedAnswerEn, String variationNote) {}