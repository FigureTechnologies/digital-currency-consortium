package io.provenance.digitalcurrency.consortium.web

const val API_V1 = "/api/v1"

const val REGISTRATION = "/registrations"
const val MINT = "/mints"
const val CONSORTIUM = "/consortium"
const val CONSORTIUM_JOIN = "$CONSORTIUM/joins"
const val CONSORTIUM_VOTE = "$CONSORTIUM/votes"
const val CONSORTIUM_ACCEPT = "$CONSORTIUM/accepts"

const val REGISTRATION_V1 = "$API_V1$REGISTRATION"
const val MINT_V1 = "$API_V1$MINT"
const val CONSORTIUM_JOIN_V1 = "$API_V1$CONSORTIUM_JOIN"
const val CONSORTIUM_VOTE_V1 = "$API_V1$CONSORTIUM_VOTE"
const val CONSORTIUM_ACCEPT_V1 = "$API_V1$CONSORTIUM_ACCEPT"

// TODO this is the bank interface, need to let them define it
const val FIAT_DEPOSIT = "$API_V1/fiat/deposits"
