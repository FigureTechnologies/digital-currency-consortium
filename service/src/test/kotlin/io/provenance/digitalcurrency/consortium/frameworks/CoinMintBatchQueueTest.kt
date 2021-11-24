package io.provenance.digitalcurrency.consortium.frameworks

// class CoinMintBatchQueueTest : BaseIntegrationTest() {
//     @Autowired
//     lateinit var pbcServiceMock: PbcService
//
//     private lateinit var coinMintBatchQueue: CoinMintBatchQueue
//
//     @MockBean
//     private lateinit var coinMintServiceMock: CoinMintService
//
//     @BeforeEach
//     fun beforeEach() {
//         reset(pbcServiceMock)
//         reset(coinMintServiceMock)
//     }
//
//     @BeforeAll
//     fun beforeAll() {
//         val coroutineProperties = CoroutineProperties(
//             numWorkers = 1,
//             pollingDelayMs = 1000L,
//             batchSize = 25,
//             batchPollingDelayMs = 60000L
//         )
//
//         coinMintBatchQueue = CoinMintBatchQueue(
//             coroutineProperties,
//             coinMintServiceMock
//         )
//     }
//
//     @Test
//     fun `one coin mint record should try to mint`() {
//
//         val coinMintRecord: CoinMintRecord = transaction {
//             insertCoinMint()
//         }
//
//         val outcome: CoinMintBatchOutcome = coinMintBatchQueue.processMessages(
//             CoinMintBatchDirective(listOf(coinMintRecord.id.value))
//         )
//
//         verify(pbcServiceMock, never()).mintBatch(any())
//         verify(coinMintServiceMock).createEvent(any())
//
//         assertEquals(outcome.ids, listOf(coinMintRecord.id.value))
//     }
//
//     @Test
//     fun `over 25 coin mint records should only load 25`() {
//         val uuids: List<UUID> = transaction {
//             (1..30).map {
//                 insertCoinMint(address = it.toString()).id.value
//             }
//         }
//
//         runBlockingTest {
//             val directive = coinMintBatchQueue.loadMessages()
//             assert(directive.ids.containsAll(uuids.subList(0, 24)))
//             assertEquals(directive.ids.size, 25)
//         }
//     }
//
//     @Test
//     fun `coin mint records with varying status should only pull inserted`() {
//         transaction {
//             val coinMint1 = insertCoinMint(address = "1").also {
//                 CoinMintRecord.updateStatus(it.id.value, CoinMintStatus.COMPLETE)
//             }
//             val coinMint2 = insertCoinMint(address = "2").also {
//                 CoinMintRecord.updateStatus(it.id.value, CoinMintStatus.PENDING_MINT)
//             }
//             val coinMint3 = insertCoinMint(address = "3")
//             val coinMint4 = insertCoinMint(address = "4")
//
//             runBlockingTest {
//                 val directive = coinMintBatchQueue.loadMessages()
//                 assert(directive.ids.containsAll(listOf(coinMint3.id.value, coinMint4.id.value)))
//                 assert(!directive.ids.contains(coinMint1.id.value))
//                 assert(!directive.ids.contains(coinMint2.id.value))
//                 assertEquals(directive.ids.size, 2)
//             }
//         }
//     }
// }
