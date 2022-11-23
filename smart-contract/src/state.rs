use schemars::JsonSchema;
use serde::{Deserialize, Serialize};

use crate::error::ContractError;
use crate::msg::MigrateMsg;
use cosmwasm_std::{Addr, Decimal, DepsMut, Storage, Uint128};
use cosmwasm_storage::{singleton, singleton_read, ReadonlySingleton, Singleton};
use provwasm_std::ProvenanceQuery;
use semver::{Version, VersionReq};

#[allow(deprecated)]
pub static CONFIG_KEY: &[u8] = b"config";
pub static CONFIG_V2_KEY: &[u8] = b"configv2";

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
#[deprecated(since = "0.5.0")]
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

/// Configuration state for the dcc consortium contract.
#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub struct StateV2 {
    // The contract administrator account.
    pub admin: Addr,
    // The token denomination.
    pub denom: String,
    // Addresses that are authorized to transfer token by proxy.
    pub executors: Vec<String>,
}

#[allow(deprecated)]
impl From<State> for StateV2 {
    fn from(state: State) -> Self {
        StateV2 {
            admin: state.admin,
            denom: state.dcc_denom,
            executors: vec![],
        }
    }
}

#[allow(deprecated)]
pub fn migrate_state(
    deps: DepsMut<ProvenanceQuery>,
    current_version: Version,
    _msg: &MigrateMsg,
) -> Result<(), ContractError> {
    let store = deps.storage;
    // version support added in 0.5.0, all previous versions migrate to v2 of store data
    let upgrade_req = VersionReq::parse("<0.5.0")?;

    if upgrade_req.matches(&current_version) {
        let existing_state = legacy_config_read(store).load()?;
        legacy_config(store).remove(); // remove old state
        config(store).save(&existing_state.into())?;
    }

    Ok(())
}

pub fn config(storage: &mut dyn Storage) -> Singleton<StateV2> {
    singleton(storage, CONFIG_V2_KEY)
}

pub fn config_read(storage: &dyn Storage) -> ReadonlySingleton<StateV2> {
    singleton_read(storage, CONFIG_V2_KEY)
}

#[allow(deprecated)]
pub fn legacy_config(storage: &mut dyn Storage) -> Singleton<State> {
    singleton(storage, CONFIG_KEY)
}

#[allow(deprecated)]
pub fn legacy_config_read(storage: &dyn Storage) -> ReadonlySingleton<State> {
    singleton_read(storage, CONFIG_KEY)
}

#[cfg(test)]
mod tests {
    use cosmwasm_std::{Addr, Decimal, Uint128};
    use provwasm_mocks::mock_dependencies;
    use semver::Version;

    use crate::error::ContractError;
    use crate::msg::MigrateMsg;
    #[allow(deprecated)]
    use crate::state::{config_read, legacy_config, migrate_state, State, StateV2};

    #[test]
    #[allow(deprecated)]
    pub fn migrate_legacy_state_to_v2() -> Result<(), ContractError> {
        let mut deps = mock_dependencies(&[]);

        legacy_config(&mut deps.storage).save(&State {
            admin: Addr::unchecked("id"),
            quorum_pct: Decimal::percent(67),
            dcc_denom: "test.dcc".to_string(),
            vote_duration: Uint128::new(5000),
            kyc_attrs: vec!["test.kyc.pb".to_string(), "bank.kyc.pb".to_string()],
            admin_weight: Uint128::zero(),
        })?;

        let current_version = Version::parse("0.0.1")?;
        migrate_state(deps.as_mut(), current_version, &MigrateMsg {})?;

        let config_store = config_read(&deps.storage);
        let migrated_state = config_store.load()?;

        assert_eq!(
            migrated_state,
            StateV2 {
                admin: Addr::unchecked("id"),
                denom: "test.dcc".to_string(),
                executors: vec![],
            }
        );

        Ok(())
    }
}
