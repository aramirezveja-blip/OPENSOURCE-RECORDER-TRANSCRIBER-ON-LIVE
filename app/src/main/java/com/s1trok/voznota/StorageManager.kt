package com.s1trok.voznota

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Almacenamiento privado (filesDir), escritura atómica y fuera del hilo principal. */
class StorageManager(context: Context) {

    private val file = AtomicFile(File(context.applicationContext.filesDir, "notes.json"))
    private val mutex = Mutex()

    suspend fun load(): List<Note> = withContext(Dispatchers.IO) {
        mutex.withLock { read() }
    }

    suspend fun add(note: Note): List<Note> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = listOf(note) + read()
            write(updated)
            updated
        }
    }

    suspend fun delete(id: Long): List<Note> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = read().filterNot { it.id == id }
            write(updated)
            updated
        }
    }

    private fun read(): List<Note> = try {
        if (!file.baseFile.exists()) emptyList()
        else {
            val arr = JSONArray(String(file.readFully(), Charsets.UTF_8))
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Note(o.getLong("id"), o.getString("text"), o.getLong("createdAt"))
            }
        }
    } catch (e: Exception) {
        emptyList() // archivo corrupto: no crashear
    }

    private fun write(notes: List<Note>) {
        val arr = JSONArray()
        notes.forEach {
            arr.put(JSONObject().put("id", it.id).put("text", it.text).put("createdAt", it.createdAt))
        }
        val out = file.startWrite()
        try {
            out.write(arr.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }
}
