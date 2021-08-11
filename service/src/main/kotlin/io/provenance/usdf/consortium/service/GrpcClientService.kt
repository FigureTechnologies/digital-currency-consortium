package io.provenance.usdf.consortium.service

import io.provenance.usdf.consortium.pbclient.GrpcClient
import io.provenance.usdf.consortium.pbclient.GrpcClientOpts
import org.springframework.stereotype.Component
import javax.annotation.PreDestroy

@Component
class GrpcClientService(private val grpcClientOpts: GrpcClientOpts) {

    private val defaultClient = GrpcClient(grpcClientOpts)

    fun new(opts: GrpcClientOpts = grpcClientOpts): GrpcClient =
        if (grpcClientOpts == grpcClientOpts) defaultClient else GrpcClient(opts)

    @PreDestroy
    fun destroy() {
        defaultClient.close()
    }
}
