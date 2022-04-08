use cosmwasm_std::Uint128;
use schemars::JsonSchema;
use serde::{Deserialize, Serialize};

use crate::join_proposal::JoinProposalV2;
use crate::member::MemberV2;

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
pub struct InitMsg {
    // Let the dcc marker denom be configurable.
    pub denom: String,
    // The number of blocks proposal voting windows are open.
    pub vote_duration: Uint128,
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
        name: String,
        kyc_attr: String,
    },
    // Vote on a join proposal.
    Vote {
        id: String,
        choice: VoteChoice,
    },
    // Cancel join proposal
    Cancel {},
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
    // Set the kyc attribute for member.
    SetKyc {
        id: Option<String>, // If admin, can set the kyc attribute for another member id
        kyc_attr: String,
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
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub struct MigrateMsg {}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub struct Members {
    pub members: Vec<MemberV2>,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub struct JoinProposals {
    pub proposals: Vec<JoinProposalV2>,
}
