package io.provenance.digitalcurrency.consortium.stream

import com.tinder.scarlet.WebSocket
import com.tinder.scarlet.ws.Receive
import com.tinder.scarlet.ws.Send
import io.reactivex.Flowable

interface EventStreamService {

    @Receive
    fun observeWebSocketEvent(): Flowable<WebSocket.Event>

    @Send
    fun subscribe(subscribe: Subscribe)

    @Receive
    fun streamEvents(): Flowable<RpcResponse<Result>>
}
