use crate::error::ContractError;
use cosmwasm_std::{DepsMut, Storage};
use cosmwasm_storage::{singleton, singleton_read, ReadonlySingleton, Singleton};
use provwasm_std::ProvenanceQuery;
use schemars::JsonSchema;
use serde::{Deserialize, Serialize};

pub const CRATE_NAME: &str = env!("CARGO_CRATE_NAME");
pub const PACKAGE_VERSION: &str = env!("CARGO_PKG_VERSION");
pub static VERSION_INFO_KEY: &[u8] = b"version_info";

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, JsonSchema)]
pub struct VersionInfo {
    pub definition: String,
    pub version: String,
}

impl Default for VersionInfo {
    fn default() -> Self {
        VersionInfo {
            definition: CRATE_NAME.to_string(),
            // Any version prior to semver will need migrated
            version: "0.0.0".to_string(),
        }
    }
}

pub fn version_info(storage: &mut dyn Storage) -> Singleton<VersionInfo> {
    singleton(storage, VERSION_INFO_KEY)
}

pub fn version_info_read(storage: &dyn Storage) -> ReadonlySingleton<VersionInfo> {
    singleton_read(storage, VERSION_INFO_KEY)
}

pub fn migrate_version_info(deps: DepsMut<ProvenanceQuery>) -> Result<VersionInfo, ContractError> {
    let state = VersionInfo {
        definition: CRATE_NAME.to_string(),
        version: PACKAGE_VERSION.to_string(),
    };

    version_info(deps.storage).save(&state)?;

    Ok(state)
}

#[cfg(test)]
mod tests {
    use crate::error::ContractError;
    use crate::version_info::{migrate_version_info, version_info_read};
    use provwasm_mocks::mock_dependencies;

    #[test]
    pub fn migrate_version_info_to_existing() -> Result<(), ContractError> {
        let mut deps = mock_dependencies(&[]);

        migrate_version_info(deps.as_mut())?;

        let read_version = version_info_read(&deps.storage).load()?;

        assert_eq!(read_version.definition, "dcc");
        // println!("definition {} version {}", read_version.definition, read_version.version);

        Ok(())
    }
}
