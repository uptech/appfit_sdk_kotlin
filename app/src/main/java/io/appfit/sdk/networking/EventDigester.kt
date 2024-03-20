package io.appfit.sdk.networking

import android.content.Context
import android.os.CountDownTimer
import io.appfit.sdk.AppFitEvent
import io.appfit.sdk.cache.AppFitCache
import io.appfit.sdk.cache.EventCache
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * EventDigester takes in events, and handles the caching, posting, and
 * retrying of failed events.
 */
@OptIn(DelicateCoroutinesApi::class)
internal class EventDigester(
    context: Context,
    apiKey: String
) {
    private val appFitCache = AppFitCache(context = context)
    private val cache = EventCache()
    private val apiClient = ApiClient(apiKey = apiKey)

    init {
        GlobalScope.launch {
            appFitCache.generateAnonymousId()
        }
        Executors.newSingleThreadScheduledExecutor().schedule({
            digestCachedEvents()
        }, 15, TimeUnit.MINUTES)
    }

    /**
     * Digests the event.
     *
     * This is used to digest the event and send it to the AppFit API.
     */
    fun digest(event: AppFitEvent) {
        // Digest the event
        GlobalScope.launch {
            val userId = appFitCache.getUserId()
            val anonymousId = appFitCache.getAnonymousId()
            val rawMetricEvent = createRawMetricEvent(event, userId, anonymousId)
            val result = apiClient.send(rawMetricEvent)
            when (result) {
                true -> {
                    // Remove the event from the cache
                    cache.remove(event)
                }
                false -> {
                    // Add the event to the cache
                    cache.add(event)
                }
            }
        }
    }

    /**
     * Identifies the user.
     *
     * This is used to identify the user in the AppFit API.
     */
    fun identify(userId: String?) {
        // Identify the user
        GlobalScope.launch {
            appFitCache.saveUserId(userId)
        }
    }

    /**
     * Digests the cached events.
     */
    private fun digestCachedEvents() {
        // Digest the cached events
        GlobalScope.launch {
            val userId = appFitCache.getUserId()
            val anonymousId = appFitCache.getAnonymousId()

            // Get the cached events
            val events = cache.events.map { event ->
                createRawMetricEvent(event, userId, anonymousId)
            }

            // Send the events to the AppFit API
            val result = apiClient.send(events)
            when (result) {
                true -> {
                    // Remove the events from the cache
                    cache.clear()
                }
                false -> {
                    // In this case we need to do nothing
                }
            }
        }
    }

    private fun createRawMetricEvent(event: AppFitEvent, userId: String?, anonymousId: String?): RawMetricEvent {
        return RawMetricEvent(
            occurredAt = event.date,
            eventSource = APPFIT_EVENT_SOURCE,
            payload = MetricEvent(
                eventId = event.id,
                name = event.name,
                properties = event.properties,
                systemProperties = null,
                userId = userId,
                anonymousId = anonymousId,
            )
        )
    }
}