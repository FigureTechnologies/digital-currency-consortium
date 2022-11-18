use cosmwasm_std::{entry_point, DepsMut, Env, Response};
use cw2::set_contract_version;
use provwasm_std::ProvenanceQuery;

use crate::contract::{CRATE_NAME, PACKAGE_VERSION};
use crate::error::ContractError;
use crate::msg::MigrateMsg;

#[entry_point]
pub fn migrate(
    deps: DepsMut<ProvenanceQuery>,
    _env: Env,
    _msg: MigrateMsg,
) -> Result<Response, ContractError> {
    set_contract_version(deps.storage, CRATE_NAME, PACKAGE_VERSION)?;
    Ok(Response::default())
}

#[cfg(test)]
mod tests {
    use cosmwasm_std::testing::mock_env;
    use provwasm_mocks::mock_dependencies;

    use super::*;

    #[test]
    fn migrate_test() {
        let mut deps = mock_dependencies(&[]);

        let result = cw2::set_contract_version(deps.as_mut().storage, CRATE_NAME, "v0");
        match result {
            Ok(..) => {}
            Err(error) => panic!("unexpected error: {:?}", error),
        }

        let migrate_response = migrate(deps.as_mut(), mock_env(), MigrateMsg {});

        // verify migrate response
        match migrate_response {
            Ok(..) => {
                let version_info = cw2::get_contract_version(&deps.storage).unwrap();

                assert_eq!(PACKAGE_VERSION, version_info.version);
                assert_eq!(CRATE_NAME, version_info.contract);
            }
            error => panic!("failed to initialize: {:?}", error),
        }
    }
}
