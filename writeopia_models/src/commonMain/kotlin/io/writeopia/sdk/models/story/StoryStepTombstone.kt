package io.writeopia.sdk.models.story

/**
 * A [StoryStep] deleted on this device that the backend has not been told about yet.
 *
 * A deleted step must never come back: while its tombstone is here, no save recreates the step,
 * and the next sync sends the deletion before anything else. Once the backend has it, the
 * tombstone is dropped here; the backend keeps its own so the other devices learn of it.
 *
 * @param deletedAt Epoch milliseconds of the deletion.
 */
data class StoryStepTombstone(
    val stepId: String,
    val documentId: String,
    val workspaceId: String,
    val deletedAt: Long
)
