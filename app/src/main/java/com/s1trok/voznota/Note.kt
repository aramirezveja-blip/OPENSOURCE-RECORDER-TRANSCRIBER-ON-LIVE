package com.s1trok.voznota

data class Note(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
)
