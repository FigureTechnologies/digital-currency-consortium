use cosmwasm_std::Uint128;
use schemars::JsonSchema;
use serde::{Deserialize, Serialize};

use crate::member::MemberV2;

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
pub struct InitMsg {
    // Let the dcc marker denom be configurable.
    pub denom: String,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
#[deprecated(since = "0.5.0")]
pub enum VoteChoice {
    Yes,
    No,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub enum ExecuteMsg {
    // Create a proposal to join the consortium.
    Join {
        id: String,
        name: String,
        kyc_attrs: Vec<String>,
    },
    Remove {
        id: String,
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
    // Add a kyc attribute for member.
    AddKyc {
        id: Option<String>, // If admin, can set the kyc attribute for another member id
        kyc_attr: String,
    },
    // Remove a kyc attribute for member.
    RemoveKyc {
        id: Option<String>, // If admin, can set the kyc attribute for another member id
        kyc_attr: String,
    },
    // Set the smart contract admin address.
    SetAdmin {
        id: String,
    },
    // Add an executor to state.
    AddExecutor {
        id: String,
    },
    // Remove an executor to state.
    RemoveExecutor {
        id: String,
    },
    // Transfer dcc by executor.
    ExecutorTransfer {
        amount: Uint128,
        sender: String,
        recipient: String,
    },
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub enum QueryMsg {
    // Query all members.
    GetMembers {},
    // Query a member by ID.
    GetMember { id: String },
    // Get contract state data.
    GetContractInfo {},
    // Get contract version data.
    GetVersionInfo {},
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub struct MigrateMsg {}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub struct Members {
    pub members: Vec<MemberV2>,
}
