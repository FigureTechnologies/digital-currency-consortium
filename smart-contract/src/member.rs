use schemars::JsonSchema;
use serde::{Deserialize, Serialize};

use crate::error::ContractError;
use crate::msg::MigrateMsg;
use crate::version_info::{version_info_read, VersionInfo};
use cosmwasm_std::{Addr, DepsMut, Order, Storage, Uint128};
use cosmwasm_storage::{bucket, bucket_read, Bucket, ReadonlyBucket};
use provwasm_std::ProvenanceQuery;
use semver::{Version, VersionReq};

pub static MEMBER_KEY: &[u8] = b"member";
pub static MEMBER_V2_KEY: &[u8] = b"memberv2";

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
#[deprecated(since = "0.5.0")]
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

/// Member state.
#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub struct MemberV2 {
    // The member ID (also the member address).
    pub id: Addr,
    // TODO - determine if we still need to keep track of supply or limit max supply
    // The block height membership was accepted.
    pub joined: Uint128,
    // The name of the member (or just the address if not provided in the join proposal).
    pub name: String,
    // KYC attributes required for holding dcc tokens.
    pub kyc_attrs: Vec<String>,
}

#[allow(deprecated)]
impl From<Member> for MemberV2 {
    fn from(member: Member) -> Self {
        MemberV2 {
            id: member.id,
            joined: member.joined,
            name: member.name,
            kyc_attrs: Vec::new(),
        }
    }
}

#[allow(deprecated)]
pub fn migrate_members(
    deps: DepsMut<ProvenanceQuery>,
    _msg: &MigrateMsg,
) -> Result<(), ContractError> {
    let store = deps.storage;
    let version_info = version_info_read(store)
        .may_load()?
        .unwrap_or(VersionInfo::default());
    let current_version = Version::parse(&version_info.version)?;
    // version support added in 0.5.0, all previous versions migrate to v2 of store data
    let upgrade_req = VersionReq::parse("<0.5.0")?;

    if upgrade_req.matches(&current_version) {
        let existing_member_ids: Vec<Vec<u8>> = legacy_members_read(store)
            .range(None, None, Order::Ascending)
            .map(|item| {
                let (member_key, _) = item.unwrap();
                member_key
            })
            .collect();

        for existing_member_id in existing_member_ids {
            let existing_member = legacy_members_read(store).load(&existing_member_id)?;
            members(store).save(&existing_member_id, &existing_member.into())?
        }
    }

    Ok(())
}

pub fn legacy_members(storage: &mut dyn Storage) -> Bucket<Member> {
    bucket(storage, MEMBER_KEY)
}

pub fn legacy_members_read(storage: &dyn Storage) -> ReadonlyBucket<Member> {
    bucket_read(storage, MEMBER_KEY)
}

pub fn members(storage: &mut dyn Storage) -> Bucket<MemberV2> {
    bucket(storage, MEMBER_V2_KEY)
}

pub fn members_read(storage: &dyn Storage) -> ReadonlyBucket<MemberV2> {
    bucket_read(storage, MEMBER_V2_KEY)
}

#[cfg(test)]
mod tests {
    use cosmwasm_std::{Addr, Uint128};
    use provwasm_mocks::mock_dependencies;

    use crate::error::ContractError;
    #[allow(deprecated)]
    use crate::member::{legacy_members, migrate_members, Member, MemberV2};
    use crate::msg::MigrateMsg;

    #[test]
    #[allow(deprecated)]
    pub fn migrate_legacy_proposal_to_v2() -> Result<(), ContractError> {
        let mut deps = mock_dependencies(&[]);

        legacy_members(&mut deps.storage).save(
            b"id",
            &Member {
                id: Addr::unchecked("id"),
                supply: Uint128::new(100),
                max_supply: Uint128::new(1000),
                denom: "test.dcc".to_string(),
                joined: Uint128::new(50100),
                weight: Uint128::new(1000),
                name: "bank".to_string(),
            },
        )?;

        migrate_members(deps.as_mut(), &MigrateMsg {})?;

        let member_store = members_read(&deps.storage);
        let migrated_member = member_store.load(b"id")?;

        assert_eq!(
            migrated_member,
            MemberV2 {
                id: Addr::unchecked("id"),
                joined: Uint128::new(50100),
                name: "bank".to_string(),
                kyc_attrs: Vec::new(),
            }
        );

        Ok(())
    }
}
