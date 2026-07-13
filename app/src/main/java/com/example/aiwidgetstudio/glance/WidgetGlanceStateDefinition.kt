package com.example.aiwidgetstudio.glance

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.glance.state.GlanceStateDefinition
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class WidgetGlanceState(
    val widgetId: String = "",
    val stateJson: String = "{}"
)

object WidgetGlanceStateDefinition : GlanceStateDefinition<WidgetGlanceState> {

    private val stores = ConcurrentHashMap<String, DataStore<WidgetGlanceState>>()

    override suspend fun getDataStore(context: Context, fileKey: String): DataStore<WidgetGlanceState> {
        return stores.getOrPut(fileKey) {
            DataStoreFactory.create(
                serializer = WidgetGlanceStateSerializer,
                produceFile = { getLocation(context, fileKey) }
            )
        }
    }

    override fun getLocation(context: Context, fileKey: String): File =
        File(context.filesDir, "glance_widget_state_$fileKey.json")
}

private object WidgetGlanceStateSerializer : Serializer<WidgetGlanceState> {
    override val defaultValue = WidgetGlanceState()

    override suspend fun readFrom(input: InputStream): WidgetGlanceState = try {
        Json.decodeFromString(WidgetGlanceState.serializer(), input.readBytes().decodeToString())
    } catch (e: Exception) {
        throw CorruptionException("Cannot read glance state", e)
    }

    override suspend fun writeTo(t: WidgetGlanceState, output: OutputStream) {
        output.write(Json.encodeToString(WidgetGlanceState.serializer(), t).encodeToByteArray())
    }
}
