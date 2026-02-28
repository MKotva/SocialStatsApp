package com.example.socialstasts.helpers

/** Normalized result of a media pick operation used by the create-post form. */
data class PickedMedia(
    val mediaType: String,  // "VIDEO" / "IMAGE"
    val mediaUri: String,   // App-local file URI
    val displayName: String // Original or generated display name for UI
)