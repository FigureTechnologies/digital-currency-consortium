package io.provenance.digitalcurrency.consortium.pbclient

import cosmos.tx.v1beta1.ServiceGrpc
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.provenance.digitalcurrency.consortium.pbclient.grpc.Accounts
import io.provenance.digitalcurrency.consortium.pbclient.grpc.Attributes
import io.provenance.digitalcurrency.consortium.pbclient.grpc.Blocks
import io.provenance.digitalcurrency.consortium.pbclient.grpc.Markers
import io.provenance.digitalcurrency.consortium.pbclient.grpc.Transactions
import java.io.Closeable
import java.net.URI
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class ChannelOpts(
    val inboundMessageSize: Int,
    val idleTimeout: Pair<Long, TimeUnit>,
    val keepAliveTime: Pair<Long, TimeUnit>,
    val keepAliveTimeout: Pair<Long, TimeUnit>,
    val executor: ExecutorService
)

open class ContextClient(
    channelUri: URI,
    opts: ChannelOpts = defaultChannelProperties,
    channelConfigLambda: (NettyChannelBuilder) -> Unit
) : Closeable {

    companion object {
        val defaultChannelProperties = ChannelOpts(
            inboundMessageSize = 40 * 1024 * 1024, // ~ 20 MB
            idleTimeout = 5L to TimeUnit.MINUTES,
            keepAliveTime = 60L to TimeUnit.SECONDS, // ~ 12 pbc block cuts
            keepAliveTimeout = 20L to TimeUnit.SECONDS,
            executor = Executors.newFixedThreadPool(8)
        )
    }

    private val channel = NettyChannelBuilder.forAddress(channelUri.host, channelUri.port)
        .also {
            if (channelUri.scheme == "grpcs") {
                it.useTransportSecurity()
            } else {
                it.usePlaintext()
            }
        }
        .executor(opts.executor)
        .maxInboundMessageSize(opts.inboundMessageSize)
        .idleTimeout(opts.idleTimeout.first, opts.idleTimeout.second)
        .keepAliveTime(opts.keepAliveTime.first, opts.keepAliveTime.second)
        .keepAliveTimeout(opts.keepAliveTimeout.first, opts.keepAliveTimeout.second)
        .also { builder -> channelConfigLambda(builder) }
        .build()

    val accounts = Accounts(channel)
    val attributes = Attributes(channel)
    val markers = Markers(channel)
    val transactions = Transactions(channel)
    val blocks = Blocks(channel)

    internal val cosmosService = ServiceGrpc.newBlockingStub(channel)

    override fun close() {
        channel.shutdown()
    }
}
