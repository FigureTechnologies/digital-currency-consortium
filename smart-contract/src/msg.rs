use cosmwasm_std::{Addr, Decimal, Uint128};
use schemars::JsonSchema;
use serde::{Deserialize, Serialize};

use crate::state::{JoinProposal, Member};

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
pub struct InitMsg {
    // Let the dcc marker denom be configurable.
    pub dcc_denom: String,
    // The percentage of 'yes' votes required to join the consortium.
    pub quorum_pct: Decimal,
    // The number of blocks proposal voting windows are open.
    pub vote_duration: Uint128,
    // KYC attributes required (any of) to hold dcc tokens.
    pub kyc_attrs: Vec<String>,
    // Let the admin weight be configurable for testing.
    pub admin_weight: Option<Uint128>,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub enum VoteChoice {
    Yes,
    No,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub enum ExecuteMsg {
    // Create a proposal to join the consortium.
    Join {
        denom: String,
        max_supply: Uint128,
        name: Option<String>,
    },
    // Vote on a join proposal.
    Vote {
        id: String,
        choice: VoteChoice,
    },
    // Accept join proposal (fails unless quorum is reached)
    Accept {
        mint_amount: Option<Uint128>,
    },
    // Cancel join proposal
    Cancel {},
    // Redeem dcc tokens for reserve tokens.
    Redeem {
        amount: Uint128,
        reserve_denom: Option<String>, // If provided, redeem for this denom exclusively.
    },
    // Swap reserve tokens for dcc tokens.
    Swap {
        amount: Uint128,
        denom: String,
        address: Option<String>, // If provided, withdraw dcc tokens here pending kyc checks
    },
    // Transfer dcc.
    Transfer {
        amount: Uint128,
        recipient: String,
    },
    // Increase a member's supply of reserve tokens.
    Mint {
        amount: Uint128,
        address: Option<String>, // If provided, withdraw dcc tokens here pending kyc checks
    },
    // Decrease a member's supply of reserve tokens.
    Burn {
        amount: Uint128,
    },
    // Add a kyc attribute.
    AddKyc {
        name: String,
    },
    // Remove a kyc attribute.
    RemoveKyc {
        name: String,
    },
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub enum QueryMsg {
    // Query all join proposals.
    GetJoinProposals {},
    // Query all members.
    GetMembers {},
    // Query a join proposal by ID.
    GetJoinProposal { id: String },
    // Query a member by ID.
    GetMember { id: String },
    // Query available reserve balances for redemption.
    GetBalances {},
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub struct MigrateMsg {}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub struct Members {
    pub members: Vec<Member>,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub struct JoinProposals {
    pub proposals: Vec<JoinProposal>,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub struct Balance {
    pub address: Addr,
    pub denom: String,
    pub amount: Uint128,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub struct Balances {
    pub balances: Vec<Balance>,
}
