package com.hillariousstartups.btcdca

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

interface StateRepository {
    fun load(): StrategyState
    fun save(state: StrategyState)
}

class FileStateRepository(
    private val path: Path,
) : StateRepository {
    private val mapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())

    override fun load(): StrategyState {
        if (!path.exists()) return StrategyState()
        val bytes = Files.readAllBytes(path)
        if (bytes.isEmpty()) return StrategyState()
        return mapper.readValue(bytes)
    }

    override fun save(state: StrategyState) {
        Files.createDirectories(path.parent)
        val json = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(state)
        Files.write(path, json)
    }
}
