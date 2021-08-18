use schemars::JsonSchema;
use serde::{Deserialize, Serialize};

use cosmwasm_std::{Addr, Decimal, Storage, Uint128};
use cosmwasm_storage::{
    bucket, bucket_read, singleton, singleton_read, Bucket, ReadonlyBucket, ReadonlySingleton,
    Singleton,
};

pub static CONFIG_KEY: &[u8] = b"config";
pub static JOIN_PROPOSAL_KEY: &[u8] = b"proposal";
pub static MEMBER_KEY: &[u8] = b"member";

/// Configuration state for the dcc consortium contract.
#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub struct State {
    // The contract administrator account.
    pub admin: Addr,
    // The percentage of 'yes' votes required to join the consortium.
    pub quorum_pct: Decimal,
    // The dcc token denomination.
    pub dcc_denom: String,
    // The number of blocks proposal voting windows are open.
    pub vote_duration: Uint128,
    // KYC attributes required for holding dcc tokens.
    pub kyc_attrs: Vec<String>,
    // The weight the admin account has for voting.
    pub admin_weight: Uint128,
}

/// Join proposal state.
#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub struct JoinProposal {
    // The proposal ID (also the proposed member address).
    pub id: Addr,
    // The max supply of reserve token.
    pub max_supply: Uint128,
    // The denom of the proposed marker.
    pub denom: String,
    // The block height the proposal was created at.
    pub created: Uint128,
    // The block height the voting window closes.
    pub expires: Uint128,
    // The sum of weights of members that voted 'no'.
    pub no: Uint128,
    // The sum of the weights of members that voted 'yes'.
    pub yes: Uint128,
    // The addresses of members that have voted.
    pub voters: Vec<Addr>,
    // The name of the proposed member (optional).
    pub name: Option<String>,
}

/// Member state.
#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub struct Member {
    // The member ID (also the member address).
    pub id: Addr,
    // The current supply of reserve token.
    pub supply: Uint128,
    // The max supply of reserve token.
    pub max_supply: Uint128,
    // The denom of the member's backing marker.
    pub denom: String,
    // The block height membership was accepted.
    pub joined: Uint128,
    // The member's voting weight.
    pub weight: Uint128,
    // The name of the member (or just the address if not provided in the join proposal).
    pub name: String,
}

pub fn config(storage: &mut dyn Storage) -> Singleton<State> {
    singleton(storage, CONFIG_KEY)
}

pub fn config_read(storage: &dyn Storage) -> ReadonlySingleton<State> {
    singleton_read(storage, CONFIG_KEY)
}

pub fn join_proposals(storage: &mut dyn Storage) -> Bucket<JoinProposal> {
    bucket(storage, JOIN_PROPOSAL_KEY)
}

pub fn join_proposals_read(storage: &dyn Storage) -> ReadonlyBucket<JoinProposal> {
    bucket_read(storage, JOIN_PROPOSAL_KEY)
}

pub fn members(storage: &mut dyn Storage) -> Bucket<Member> {
    bucket(storage, MEMBER_KEY)
}

pub fn members_read(storage: &dyn Storage) -> ReadonlyBucket<Member> {
    bucket_read(storage, MEMBER_KEY)
}
