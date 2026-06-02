package com.frodo.fighttracker

data class RunRecord(
    val runid: String,
    val date: String,
    val duration: String,
    val gold: Long,
    val orns: Long,
    val exp: Long,
    val shards: Long
)