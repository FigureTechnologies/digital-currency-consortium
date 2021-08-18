package io.provenance.digitalcurrency.consortium.stream

import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.lifecycle.LifecycleRegistry
import io.provenance.digitalcurrency.consortium.pbclient.RpcClient

class EventStreamStaleException(message: String) : Throwable(message)

class EventStreamFactory(private val rpcClient: RpcClient, private val eventStreamBuilder: Scarlet.Builder) {

    fun getStream(eventTypes: List<String>, startHeight: Long, observer: EventStreamResponseObserver<EventBatch>): RpcEventStream {
        val lifecycle = LifecycleRegistry(0L)

        return RpcEventStream(
            eventTypes,
            startHeight,
            observer,
            lifecycle,
            eventStreamBuilder.lifecycle(lifecycle).build().create(),
            rpcClient
        )
    }
}
