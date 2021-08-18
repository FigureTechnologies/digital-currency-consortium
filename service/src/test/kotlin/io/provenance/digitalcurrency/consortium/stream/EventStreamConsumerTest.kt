package io.provenance.digitalcurrency.consortium.stream

// import com.google.protobuf.ByteString
// import io.mockk.every
// import io.mockk.mockkStatic
// import io.provenance.attribute.v1.Attribute
// import io.provenance.digitalcurrency.consortium.TestContainer
// import io.provenance.digitalcurrency.consortium.config.BankClientProperties
// import io.provenance.digitalcurrency.consortium.config.EventStreamProperties
// import io.provenance.digitalcurrency.consortium.config.ProvenanceProperties
// import io.provenance.digitalcurrency.consortium.config.ServiceProperties
// import io.provenance.digitalcurrency.consortium.domain.CoinMovementRecord
// import io.provenance.digitalcurrency.consortium.extension.toByteArray
// import io.provenance.digitalcurrency.consortium.pbclient.RpcClient
// import io.provenance.digitalcurrency.consortium.pbclient.api.rpc.BlockId
// import io.provenance.digitalcurrency.consortium.pbclient.api.rpc.BlockResponse
// import io.provenance.digitalcurrency.consortium.pbclient.api.rpc.PartSetHeader
// import io.provenance.digitalcurrency.consortium.pbclient.fetchBlock
// import io.provenance.digitalcurrency.consortium.randomTxHash
// import io.provenance.digitalcurrency.consortium.service.PbcService
// import org.jetbrains.exposed.sql.transactions.transaction
// import org.junit.jupiter.api.Assertions
// import org.junit.jupiter.api.BeforeAll
// import org.junit.jupiter.api.BeforeEach
// import org.junit.jupiter.api.Test
// import org.mockito.kotlin.any
// import org.mockito.kotlin.reset
// import org.mockito.kotlin.whenever
// import org.springframework.beans.factory.annotation.Autowired
// import org.springframework.boot.test.mock.mockito.MockBean
// import java.time.OffsetDateTime
// import java.util.UUID

// @TestContainer
// class EventStreamConsumerTest(
//     private val provenanceProperties: ProvenanceProperties,
//     private val serviceProperties: ServiceProperties
// ) {
//     @Autowired
//     private lateinit var eventStreamProperties: EventStreamProperties
//     @Autowired
//     private lateinit var bankClientProperties: BankClientProperties
//
//     @MockBean
//     private lateinit var eventStreamFactory: EventStreamFactory
//     @MockBean
//     private lateinit var pbcServiceMock: PbcService
//     @MockBean
//     private lateinit var rpcClientMock: RpcClient
//
//     class EventStreamConsumerWrapper(
//         eventStreamFactory: EventStreamFactory,
//         pbcService: PbcService,
//         rpcClient: RpcClient,
//         bankClientProperties: BankClientProperties,
//         eventStreamProperties: EventStreamProperties,
//         provenanceProperties: ProvenanceProperties,
//         serviceProperties: ServiceProperties
//     ) : EventStreamConsumer(
//         eventStreamFactory,
//         pbcService,
//         rpcClient,
//         bankClientProperties,
//         eventStreamProperties,
//         provenanceProperties,
//         serviceProperties
//     ) {
//
//         fun testHandleCoinMovementEvents(
//             burns: Burns = listOf(),
//             withdraws: Withdraws = listOf(),
//             markerTransfers: MarkerTransfers = listOf(),
//             mints: Mints = listOf()
//         ) {
//             super.handleEvents(0L, burns, withdraws, markerTransfers, mints)
//         }
//
//         fun testHandleEvents(
//             mints: Mints = listOf(),
//             burns: Burns = listOf(),
//             redemptions: Redemptions = listOf(),
//             transfers: Transfers = listOf()
//         ) {
//             super.handleEvents(0L, mints, burns, redemptions, transfers)
//         }
//
//     private lateinit var eventStreamConsumerWrapper: EventStreamConsumerWrapper
//
//     @BeforeEach
//     fun beforeEach() {
//         reset(eventStreamFactory)
//         reset(pbcServiceMock)
//         reset(rpcClientMock)
//
//         transaction { CoinMovementRecord.all().map { it.delete() } }
//     }
//
//     @BeforeAll
//     fun beforeAll() {
//         eventStreamConsumerWrapper = EventStreamConsumerWrapper(
//             eventStreamFactory,
//             pbcServiceMock,
//             rpcClientMock,
//             bankClientProperties,
//             eventStreamProperties,
//             provenanceProperties,
//             serviceProperties
//         )
//     }
//
//     @Test
//     fun `coinMovement - events without bank parties are ignored`() {
//         val blockTime = OffsetDateTime.now()
//         val blockResponse = BlockResponse(
//             block = Block(
//                 header = BlockHeader(0, blockTime.toString()),
//                 data = BlockData(emptyList()),
//             ),
//             blockId = BlockId("", PartSetHeader(0, ""))
//         )
//         val mint = Mint(
//             txHash = randomTxHash(),
//             toAddress = "toAddr",
//             administrator = "adminAddr",
//             coins = "100",
//             denom = "usdf.c",
//             height = 0,
//         )
//         val transfer = MarkerTransfer(
//             txHash = randomTxHash(),
//             toAddress = "toAddr",
//             fromAddress = "fromAddr",
//             amount = "100",
//             denom = "usdf.c",
//             height = 0,
//         )
//         val withdraw = Withdraw(
//             txHash = randomTxHash(),
//             toAddress = "toAddr",
//             administrator = "adminAddr",
//             coins = "100",
//             denom = "usdf.c",
//             height = 0,
//         )
//
//         whenever(pbcServiceMock.getAttributes(any())).thenReturn(emptyList())
//
//         mockkStatic(RpcClient::fetchBlock)
//         every { rpcClientMock.fetchBlock(0) } returns blockResponse
//
//         eventStreamConsumerWrapper.testHandleEvents(
//             mints = listOf(mint),
//             withdraws = listOf(withdraw),
//             markerTransfers = listOf(transfer),
//         )
//
//         Assertions.assertEquals(0, transaction { CoinMovementRecord.all().count() })
//     }
//
//     @Test
//     fun `coinMovement - mints, withraws, and transfers for bank parties are persisted`() {
//         val blockTime = OffsetDateTime.now()
//         val blockResponse = BlockResponse(
//             block = Block(
//                 header = BlockHeader(0, blockTime.toString()),
//                 data = BlockData(emptyList()),
//             ),
//             blockId = BlockId("", PartSetHeader(0, ""))
//         )
//         val mint = Mint(
//             txHash = randomTxHash(),
//             toAddress = "toAddr",
//             administrator = "adminAddr",
//             coins = "100",
//             denom = "usdf.c",
//             height = 0,
//         )
//         val transfer = MarkerTransfer(
//             txHash = randomTxHash(),
//             toAddress = "toAddr",
//             fromAddress = "fromAddr",
//             amount = "100",
//             denom = "usdf.c",
//             height = 0,
//         )
//         val withdraw = Withdraw(
//             txHash = randomTxHash(),
//             toAddress = "toAddr",
//             administrator = "adminAddr",
//             coins = "100",
//             denom = "usdf.c",
//             height = 0,
//         )
//
//         whenever(pbcServiceMock.getAttributes(any()))
//             .thenReturn(
//                 listOf(
//                     Attribute.newBuilder()
//                         .setName(bankClientProperties.kycTagName)
//                         .setValue(ByteString.copyFrom(UUID.randomUUID().toByteArray()))
//                         .build()
//                 )
//             )
//
//         mockkStatic(RpcClient::fetchBlock)
//         every { rpcClientMock.fetchBlock(0) } returns blockResponse
//
//         eventStreamConsumerWrapper.testHandleEvents(
//             mints = listOf(mint),
//             withdraws = listOf(withdraw),
//             markerTransfers = listOf(transfer),
//         )
//
//         Assertions.assertEquals(3, transaction { CoinMovementRecord.all().count() })
//     }
//
//     @Test
//     fun `coinMovement - block reentry is ignored`() {
//         val blockTime = OffsetDateTime.now()
//         val blockResponse = BlockResponse(
//             block = Block(
//                 header = BlockHeader(0, blockTime.toString()),
//                 data = BlockData(emptyList()),
//             ),
//             blockId = BlockId("", PartSetHeader(0, ""))
//         )
//         val mint = Mint(
//             txHash = randomTxHash(),
//             toAddress = "toAddr",
//             administrator = "adminAddr",
//             coins = "100",
//             denom = "usdf.c",
//             height = 0,
//         )
//         val transfer = MarkerTransfer(
//             txHash = randomTxHash(),
//             toAddress = "toAddr",
//             fromAddress = "fromAddr",
//             amount = "100",
//             denom = "usdf.c",
//             height = 0,
//         )
//         val withdraw = Withdraw(
//             txHash = randomTxHash(),
//             toAddress = "toAddr",
//             administrator = "adminAddr",
//             coins = "100",
//             denom = "usdf.c",
//             height = 0,
//         )
//
//         whenever(pbcServiceMock.getAttributes(any()))
//             .thenReturn(
//                 listOf(
//                     Attribute.newBuilder()
//                         .setName(bankClientProperties.kycTagName)
//                         .setValue(ByteString.copyFrom(UUID.randomUUID().toByteArray()))
//                         .build()
//                 )
//             )
//
//         mockkStatic(RpcClient::fetchBlock)
//         every { rpcClientMock.fetchBlock(0) } returns blockResponse
//
//         eventStreamConsumerWrapper.testHandleEvents(
//             mints = listOf(mint),
//             withdraws = listOf(withdraw),
//             markerTransfers = listOf(transfer),
//         )
//
//         Assertions.assertEquals(3, transaction { CoinMovementRecord.all().count() })
//
//         eventStreamConsumerWrapper.testHandleEvents(
//             mints = listOf(mint),
//             withdraws = listOf(withdraw),
//             markerTransfers = listOf(transfer),
//         )
//         eventStreamConsumerWrapper.testHandleEvents(
//             mints = listOf(mint),
//             withdraws = listOf(withdraw),
//             markerTransfers = listOf(transfer),
//         )
//         eventStreamConsumerWrapper.testHandleEvents(
//             mints = listOf(mint),
//             withdraws = listOf(withdraw),
//             markerTransfers = listOf(transfer),
//         )
//
//         Assertions.assertEquals(3, transaction { CoinMovementRecord.all().count() })
//     }
// }
//
