package io.provenance.digitalcurrency.consortium

import io.provenance.digitalcurrency.consortium.domain.AddressRegistrationRecord
import java.util.UUID

// import cosmos.bank.v1beta1.Tx
// import cosmos.base.abci.v1beta1.Abci
// import cosmos.base.v1beta1.CoinOuterClass
// import cosmos.tx.v1beta1.ServiceOuterClass
// import cosmos.tx.v1beta1.TxOuterClass
// import io.p8e.blockchain.proto.MarkerProtos
// import io.provenance.marker.v1.MsgTransferRequest
// import io.provenance.marker.v1.MsgWithdrawRequest
// import org.jetbrains.exposed.sql.deleteAll
// import org.jetbrains.exposed.sql.transactions.transaction
// import org.junit.jupiter.api.AfterAll
// import java.math.BigInteger
// import java.time.OffsetDateTime
// import java.util.UUID
//
abstract class DatabaseTest {
//     @AfterAll
//     fun afterAll() {
//         transaction {
//             // PendingTransferTable.deleteAll()
//         }
//     }
//
//     val DEFAULT_AMOUNT = "50000".toBigInteger()
//     val VALID_DENOM = "comnicoin1"
//     val VALID_RECIPIENT = "validrecipient"
//     val VALID_REFERENCE_UUID = "59F88EF0-EAF2-4EF7-85C7-F019A55B37BC"
//     val SENDER = "testsender"
//
//     fun generateTransferEvent(txHash: String, recipient: String = VALID_RECIPIENT) = Transfer(
//         sender = SENDER,
//         recipient = recipient,
//         amount = DEFAULT_AMOUNT.toString(),
//         height = 1L,
//         txHash = txHash
//     )
//
//     fun generateTransactionMsgSendResponse(
//         txHash: String,
//         denom: String = VALID_DENOM,
//         amount: BigInteger = DEFAULT_AMOUNT,
//         memo: String = VALID_REFERENCE_UUID,
//         txResponse: Abci.TxResponse = Abci.TxResponse.newBuilder()
//             .setHeight(1)
//             .setTxhash(txHash)
//             .setRawLog("")
//             .setTimestamp(OffsetDateTime.now().toString())
//             .build()
//     ): ServiceOuterClass.GetTxResponse = ServiceOuterClass.GetTxResponse.newBuilder()
//         .setTxResponse(txResponse)
//         .setTx(
//             TxOuterClass.Tx.newBuilder()
//                 .setBody(
//                     TxOuterClass.TxBody.newBuilder()
//                         .setMemo(memo)
//                         .addAllMessages(
//                             listOf(
//                                 Tx.MsgSend.newBuilder()
//                                     .addAllAmount(listOf(coinBuilder(denom = denom, amount = amount.toString())))
//                                     .setToAddress(VALID_RECIPIENT)
//                                     .build()
//                                     .toAny()
//                             )
//                         )
//                 )
//         )
//         .build()
//
//     fun generateTransactionMsgWithdrawResponse(
//         txHash: String,
//         memo: String = VALID_REFERENCE_UUID,
//         txResponse: Abci.TxResponse = Abci.TxResponse.newBuilder()
//             .setHeight(1)
//             .setTxhash(txHash)
//             .setRawLog("")
//             .setTimestamp(OffsetDateTime.now().toString())
//             .build()
//     ): ServiceOuterClass.GetTxResponse = ServiceOuterClass.GetTxResponse.newBuilder()
//         .setTxResponse(txResponse)
//         .setTx(
//             TxOuterClass.Tx.newBuilder()
//                 .setBody(
//                     TxOuterClass.TxBody.newBuilder()
//                         .setMemo(memo)
//                         .addAllMessages(
//                             listOf(
//                                 MsgWithdrawRequest.newBuilder()
//                                     .addAllAmount(listOf(coinBuilder()))
//                                     .setToAddress(VALID_RECIPIENT)
//                                     .build()
//                                     .toAny()
//                             )
//                         )
//                 )
//         )
//         .build()
//
//     fun generateTransactionNonMsgSendResponse(
//         txHash: String,
//         memo: String = VALID_REFERENCE_UUID,
//         txResponse: Abci.TxResponse = Abci.TxResponse.newBuilder()
//             .setHeight(1)
//             .setTxhash(txHash)
//             .setRawLog("")
//             .setTimestamp(OffsetDateTime.now().toString())
//             .build()
//     ): ServiceOuterClass.GetTxResponse = ServiceOuterClass.GetTxResponse.newBuilder()
//         .setTxResponse(txResponse)
//         .setTx(
//             TxOuterClass.Tx.newBuilder()
//                 .setBody(
//                     TxOuterClass.TxBody.newBuilder()
//                         .setMemo(memo)
//                         .addAllMessages(listOf(MarkerProtos.MintRequest.getDefaultInstance().toAny()))
//                 )
//         )
//         .build()
//
//     fun generateMultiMsgSendTransferResponse(
//         txHash: String,
//         denom: String = VALID_DENOM,
//         amount: BigInteger = DEFAULT_AMOUNT,
//         memo: String = VALID_REFERENCE_UUID,
//         txResponse: Abci.TxResponse = Abci.TxResponse.newBuilder()
//             .setHeight(1)
//             .setTxhash(txHash)
//             .setRawLog("")
//             .setTimestamp(OffsetDateTime.now().toString())
//             .build()
//     ): ServiceOuterClass.GetTxResponse = ServiceOuterClass.GetTxResponse.newBuilder()
//         .setTxResponse(txResponse)
//         .setTx(
//             TxOuterClass.Tx.newBuilder()
//                 .setBody(
//                     TxOuterClass.TxBody.newBuilder()
//                         .setMemo(memo)
//                         .addAllMessages(
//                             listOf(
//                                 Tx.MsgSend.newBuilder()
//                                     .addAllAmount(listOf(coinBuilder(denom = denom, amount = amount.toString())))
//                                     .setToAddress(VALID_RECIPIENT)
//                                     .build()
//                                     .toAny(),
//                                 MsgTransferRequest.getDefaultInstance().toAny()
//                             )
//                         )
//                 )
//         )
//         .build()
//
//     fun generateMultiMsgSendResponse(
//         txHash: String,
//         denom: String = VALID_DENOM,
//         amount: BigInteger = DEFAULT_AMOUNT,
//         memo: String = VALID_REFERENCE_UUID,
//         txResponse: Abci.TxResponse = Abci.TxResponse.newBuilder()
//             .setHeight(1)
//             .setTxhash(txHash)
//             .setRawLog("")
//             .setTimestamp(OffsetDateTime.now().toString())
//             .build()
//     ): ServiceOuterClass.GetTxResponse = ServiceOuterClass.GetTxResponse.newBuilder()
//         .setTxResponse(txResponse)
//         .setTx(
//             TxOuterClass.Tx.newBuilder()
//                 .setBody(
//                     TxOuterClass.TxBody.newBuilder()
//                         .setMemo(memo)
//                         .addAllMessages(
//                             listOf(
//                                 Tx.MsgSend.newBuilder()
//                                     .addAllAmount(listOf(coinBuilder(denom = denom, amount = amount.toString())))
//                                     .setToAddress(VALID_RECIPIENT)
//                                     .build()
//                                     .toAny(),
//                                 Tx.MsgSend.newBuilder()
//                                     .addAllAmount(listOf(coinBuilder(denom = denom, amount = amount.toString())))
//                                     .setToAddress(VALID_RECIPIENT)
//                                     .build()
//                                     .toAny()
//                             )
//                         )
//                 )
//         )
//         .build()
//
//     fun insertPendingTransferEvent(txHash: String, recipient: String = VALID_RECIPIENT) = transaction {
//         PendingTransferRecord.insert(
//             sender = SENDER,
//             recipient = recipient,
//             amountWithDenom = DEFAULT_AMOUNT.toString(),
//             blockHeight = 1L,
//             txHash = txHash
//         )
//     }
//
//     fun insertCoinReceipt(
//         memo: String = VALID_REFERENCE_UUID,
//         coinAmount: BigInteger = DEFAULT_AMOUNT,
//         status: CoinReceiptStatus = CoinReceiptStatus.INSERTED
//     ) = transaction {
//         CoinReceiptRecord.insert(
//             fromAddress = SENDER,
//             toAddress = VALID_RECIPIENT,
//             memo = memo,
//             denom = VALID_DENOM,
//             coinAmount = coinAmount
//         ).also {
//             it.updateStatus(status)
//         }
//     }
//
//     fun insertCoinRedemption(
//         uuid: UUID = UUID.randomUUID(),
//         referenceUuid: UUID = UUID.fromString(VALID_REFERENCE_UUID),
//         coinAmount: BigInteger = DEFAULT_AMOUNT,
//         status: CoinRedemptionStatus = CoinRedemptionStatus.INSERTED,
//         bankAccountUuid: UUID
//     ) = transaction {
//         CoinRedemptionRecord.insert(
//             uuid = uuid,
//             referenceUuid = referenceUuid,
//             bankAccountUuid = bankAccountUuid,
//             coinAmount = coinAmount
//         ).also { it.status = status }
//     }
//
//     fun insertCoinMint(
//         uuid: UUID = UUID.randomUUID(),
//         fromBankAccountUuid: UUID,
//         toBankAccountUuid: UUID,
//         coinAmount: BigInteger = DEFAULT_AMOUNT,
//         status: CoinMintStatus = CoinMintStatus.PENDING_MINT
//     ) = transaction {
//         CoinMintRecord.insert(
//             uuid = uuid,
//             fromBankAccountUuid = fromBankAccountUuid,
//             toBankAccountUuid = toBankAccountUuid,
//             coinAmount = coinAmount
//         ).updateStatus(status)
//     }
//
//     fun coinBuilder(denom: String = VALID_DENOM, amount: String = DEFAULT_AMOUNT.toString()): CoinOuterClass.Coin =
//         CoinOuterClass.Coin.newBuilder()
//             .setAmount(amount)
//             .setDenom(denom)
//             .build()
// }

    fun insertRegisteredAddress(bankAccountUuid: UUID, address: String) =
        AddressRegistrationRecord.insert(
            uuid = UUID.randomUUID(),
            bankAccountUuid = bankAccountUuid,
            address = address
        )
}
