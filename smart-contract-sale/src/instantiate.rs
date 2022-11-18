use crate::contract::{CRATE_NAME, PACKAGE_VERSION};
use crate::error::contract_err;
use crate::msg::{InitMsg, Validate};
use crate::state::{config, State};
use crate::ContractError;
use cosmwasm_std::{attr, entry_point, DepsMut, Env, MessageInfo, Response};
use cw2::set_contract_version;
use provwasm_std::{ProvenanceMsg, ProvenanceQuery};

/// Create the initial configuration state
#[entry_point]
pub fn instantiate(
    deps: DepsMut<ProvenanceQuery>,
    _env: Env,
    info: MessageInfo,
    msg: InitMsg,
) -> Result<Response<ProvenanceMsg>, ContractError> {
    msg.validate()?;

    // validate params
    if !info.funds.is_empty() {
        return Err(contract_err("funds should not be sent during instantiate"));
    }

    // create and store config state
    let contract_info = State {
        admin: info.sender.clone(),
        dcc_denom: msg.dcc_denom.clone(),
        dcc_address: deps.api.addr_validate(&msg.dcc_address)?,
    };

    config(deps.storage).save(&contract_info)?;

    set_contract_version(deps.storage, CRATE_NAME, PACKAGE_VERSION)?;

    // build response
    Ok(Response::new().add_attributes(vec![
        attr("action", "init"),
        attr("admin", info.sender.into_string()),
        attr("dcc_address", msg.dcc_address),
        attr("dcc_denom", msg.dcc_denom),
    ]))
}

#[cfg(test)]
mod tests {
    use super::*;
    use cosmwasm_std::testing::{mock_env, mock_info};
    use cosmwasm_std::Addr;
    use provwasm_mocks::mock_dependencies;

    #[test]
    fn proper_initialization() {
        let mut deps = mock_dependencies(&[]);

        let init_message = InitMsg {
            dcc_address: "addr".into(),
            dcc_denom: "usdf.c".into(),
        };
        let info = mock_info("contract_admin", &[]);
        let init_response = instantiate(
            deps.as_mut(),
            mock_env(),
            info.clone(),
            init_message.clone(),
        );

        // verify initialize response
        match init_response {
            Ok(init_response) => {
                assert_eq!(init_response.messages.len(), 0);
                assert_eq!(init_response.attributes.len(), 4);

                let expected_state = State {
                    admin: info.sender.into(),
                    dcc_address: Addr::unchecked(init_message.dcc_address),
                    dcc_denom: "usdf.c".into(),
                };

                assert_eq!(init_response.attributes[0], attr("action", "init"));
                assert_eq!(
                    init_response.attributes[1],
                    attr("admin", expected_state.admin)
                );
                assert_eq!(
                    init_response.attributes[2],
                    attr("dcc_address", expected_state.dcc_address.into_string())
                );
                assert_eq!(
                    init_response.attributes[3],
                    attr("dcc_denom", expected_state.dcc_denom)
                );

                let version_info = cw2::get_contract_version(&deps.storage).unwrap();

                assert_eq!(PACKAGE_VERSION, version_info.version);
                assert_eq!(CRATE_NAME, version_info.contract);
            }
            error => panic!("failed to initialize: {:?}", error),
        }
    }
}
