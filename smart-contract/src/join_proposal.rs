use schemars::JsonSchema;
use serde::{Deserialize, Serialize};

use crate::error::ContractError;
use crate::msg::{MigrateMsg, VoteChoice};
use crate::version_info::version_info_read;
use cosmwasm_std::{Addr, DepsMut, Order, Storage, Uint128};
use cosmwasm_storage::{bucket, bucket_read, Bucket, ReadonlyBucket};
use provwasm_std::ProvenanceQuery;
use semver::{Version, VersionReq};

pub static JOIN_PROPOSAL_KEY: &[u8] = b"proposal";
pub static JOIN_PROPOSAL_V2_KEY: &[u8] = b"proposalv2";

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
#[deprecated(since = "0.5.0")]
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
    // Admin vote, which supersedes yes/no by members.
    pub admin_vote: Option<VoteChoice>,
}

/// Join proposal state.
#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub struct JoinProposalV2 {
    // The proposal ID (also the proposed member address).
    pub id: Addr,
    // The block height the proposal was created at.
    pub created: Uint128,
    // The block height the voting window closes.
    pub expires: Uint128,
    // The name of the proposed member.
    pub name: String,
    // Admin vote, which supersedes yes/no by members.
    pub admin_vote: Option<VoteChoice>,
    // KYC attributes required for holding dcc tokens.
    pub kyc_attr: Option<String>,
}

#[allow(deprecated)]
impl From<JoinProposal> for JoinProposalV2 {
    fn from(join_proposal: JoinProposal) -> Self {
        JoinProposalV2 {
            id: join_proposal.id,
            created: join_proposal.created,
            expires: join_proposal.expires,
            name: join_proposal.name.unwrap_or_default(),
            admin_vote: join_proposal.admin_vote,
            kyc_attr: Option::None,
        }
    }
}

#[allow(deprecated)]
pub fn migrate_join_proposals(
    deps: DepsMut<ProvenanceQuery>,
    _msg: &MigrateMsg,
) -> Result<(), ContractError> {
    let store = deps.storage;
    let version_info = version_info_read(store).may_load()?.unwrap_or_default();
    let current_version = Version::parse(&version_info.version)?;
    // version support added in 0.5.0, all previous versions migrate to v2 of store data
    let upgrade_req = VersionReq::parse("<0.5.0")?;

    if upgrade_req.matches(&current_version) {
        let existing_join_proposal_ids: Vec<Vec<u8>> = legacy_join_proposals_read(store)
            .range(None, None, Order::Ascending)
            .map(|item| {
                let (join_proposal_key, _) = item.unwrap();
                join_proposal_key
            })
            .collect();

        for existing_join_proposal_id in existing_join_proposal_ids {
            let existing_join_proposal =
                legacy_join_proposals_read(store).load(&existing_join_proposal_id)?;
            join_proposals(store)
                .save(&existing_join_proposal_id, &existing_join_proposal.into())?
        }
    }

    Ok(())
}

#[allow(deprecated)]
pub fn legacy_join_proposals(storage: &mut dyn Storage) -> Bucket<JoinProposal> {
    bucket(storage, JOIN_PROPOSAL_KEY)
}

#[allow(deprecated)]
pub fn legacy_join_proposals_read(storage: &dyn Storage) -> ReadonlyBucket<JoinProposal> {
    bucket_read(storage, JOIN_PROPOSAL_KEY)
}

pub fn join_proposals(storage: &mut dyn Storage) -> Bucket<JoinProposalV2> {
    bucket(storage, JOIN_PROPOSAL_V2_KEY)
}

pub fn join_proposals_read(storage: &dyn Storage) -> ReadonlyBucket<JoinProposalV2> {
    bucket_read(storage, JOIN_PROPOSAL_V2_KEY)
}

#[cfg(test)]
mod tests {
    use cosmwasm_std::{Addr, Uint128};
    use provwasm_mocks::mock_dependencies;

    use crate::error::ContractError;
    #[allow(deprecated)]
    use crate::join_proposal::{
        join_proposals, join_proposals_read, legacy_join_proposals, migrate_join_proposals,
        JoinProposal, JoinProposalV2,
    };
    use crate::msg::MigrateMsg;

    #[test]
    #[allow(deprecated)]
    pub fn migrate_legacy_proposal_to_v2() -> Result<(), ContractError> {
        let mut deps = mock_dependencies(&[]);

        legacy_join_proposals(&mut deps.storage).save(
            b"id",
            &JoinProposal {
                id: Addr::unchecked("id"),
                max_supply: Uint128::new(1000),
                denom: "test.dcc".to_string(),
                created: Uint128::new(50000),
                expires: Uint128::new(50100),
                no: Uint128::zero(),
                yes: Uint128::new(1000),
                voters: vec![Addr::unchecked("id2"), Addr::unchecked("id3")],
                name: Option::Some("bank".to_string()),
                admin_vote: Option::None,
            },
        )?;

        migrate_join_proposals(deps.as_mut(), &MigrateMsg {})?;

        let join_store = join_proposals_read(&deps.storage);
        let migrated_join_proposal = join_store.load(b"id")?;

        assert_eq!(
            migrated_join_proposal,
            JoinProposalV2 {
                id: Addr::unchecked("id"),
                created: Uint128::new(50000),
                expires: Uint128::new(50100),
                name: "bank".to_string(),
                admin_vote: Option::None,
                kyc_attr: Option::None,
            }
        );

        Ok(())
    }
}
