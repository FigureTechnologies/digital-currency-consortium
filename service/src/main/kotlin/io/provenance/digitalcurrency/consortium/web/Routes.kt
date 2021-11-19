package io.provenance.digitalcurrency.consortium.web

const val API_V1 = "/api/v1"

const val REGISTRATION = "/registrations"
const val MINT = "/mints"
const val MEMBER = "/members"
const val PROPOSAL = "/proposals"
const val ACCEPT = "/accepts"
const val BALANCE = "/balances"
const val GAS = "/gas"
const val GRANT = "/grants"

const val REGISTRATION_V1 = "$API_V1$REGISTRATION"
const val MINT_V1 = "$API_V1$MINT"

const val MEMBER_V1 = "$API_V1$MEMBER"
const val ACCEPTS_V1 = "$API_V1$PROPOSAL$ACCEPT"
const val GRANTS_V1 = "$API_V1$GRANT"
const val BALANCES_V1 = "$API_V1$BALANCE"
const val GAS_BALANCE_V1 = "$API_V1$BALANCE$GAS"
