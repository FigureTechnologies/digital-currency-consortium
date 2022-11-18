use cosmwasm_std::{
    attr, entry_point, to_binary, BankMsg, Binary, Coin, Deps, DepsMut, Env, MessageInfo, Response,
    StdResult, WasmMsg,
};
use cw2::get_contract_version;
use provwasm_std::{Marker, MarkerType, ProvenanceMsg, ProvenanceQuerier, ProvenanceQuery};

use crate::error::{contract_err, ContractError};
use crate::msg::{DCCWasmMsg, ExecuteMsg, QueryMsg, Validate};
use crate::state::{config_read, get_sale_storage, get_sale_storage_read, Sale, Status};

pub const CRATE_NAME: &str = env!("CARGO_CRATE_NAME");
pub const PACKAGE_VERSION: &str = env!("CARGO_PKG_VERSION");

// smart contract execute entrypoint
#[entry_point]
pub fn execute(
    deps: DepsMut<ProvenanceQuery>,
    _env: Env,
    info: MessageInfo,
    msg: ExecuteMsg,
) -> Result<Response<ProvenanceMsg>, ContractError> {
    msg.validate()?;

    match msg {
        ExecuteMsg::CreateSale { id, buyer, price } => create_sale(deps, info, id, buyer, price),
        ExecuteMsg::CompleteSale { id } => complete_sale(deps, info, id),
        ExecuteMsg::CancelSale { id } => cancel_sale(deps, info, id),
    }
}

fn create_sale(
    deps: DepsMut<ProvenanceQuery>,
    info: MessageInfo,
    id: String,
    buyer: String,
    price: Coin,
) -> Result<Response<ProvenanceMsg>, ContractError> {
    // validate params
    if info.funds.is_empty() {
        return Err(contract_err("funds should be sent during sale creation"));
    }

    // ensure only single asset is sent
    if info.funds.len() > 1 {
        return Err(contract_err(
            "only one fund should be sent during sale creation",
        ));
    }

    if buyer == info.sender {
        return Err(contract_err("owner cannot also be the buyer"));
    }

    // only unrestricted markers are supported
    let asset = info.funds.first().unwrap();
    let is_unrestricted = matches!(
        ProvenanceQuerier::new(&deps.querier).get_marker_by_denom(asset.denom.clone()),
        Ok(Marker {
            marker_type: MarkerType::Coin,
            ..
        })
    );
    if !is_unrestricted {
        return Err(ContractError::UnsupportedMarkerType {
            error: String::from("fund marker type must be unrestricted"),
        });
    }

    // ensure price denom is the dcc denom
    let state = config_read(deps.storage).load()?;
    if price.denom != state.dcc_denom {
        return Err(contract_err(&format!(
            "price denom must be {}",
            state.dcc_denom
        )));
    }

    // dupe check
    if get_sale_storage_read(deps.storage)
        .may_load(id.as_bytes())?
        .is_some()
    {
        return Err(contract_err(&format!("sale with id {} already exists", id)));
    }

    let sale = Sale {
        id,
        asset: asset.clone(),
        owner: info.sender,
        buyer: deps.api.addr_validate(&buyer)?,
        price,
        status: Status::Pending,
    };

    get_sale_storage(deps.storage).save(sale.id.as_bytes(), &sale)?;

    let response = Response::new().add_attributes(vec![
        attr("action", "create_sale"),
        attr("owner", sale.owner),
        attr("buyer", sale.buyer),
        attr("asset_denom", sale.asset.denom),
        attr("asset_amount", sale.asset.amount),
        attr("price_denom", sale.price.denom),
        attr("price_amount", sale.price.amount),
    ]);

    Ok(response)
}

fn complete_sale(
    deps: DepsMut<ProvenanceQuery>,
    info: MessageInfo,
    id: String,
) -> Result<Response<ProvenanceMsg>, ContractError> {
    // validate params
    if !info.funds.is_empty() {
        return Err(contract_err(
            "no funds should be sent during sale completion",
        ));
    }

    // ensure existence
    let mut sale = get_sale_storage_read(deps.storage)
        .load(id.as_bytes())
        .map_err(ContractError::Std)?;

    // ensure pending
    if sale.status != Status::Pending {
        return Err(contract_err(&format!(
            "sale is in invalid state {}",
            sale.status,
        )));
    }

    // ensure buyer is sender
    if sale.buyer != info.sender {
        return Err(ContractError::Unauthorized {
            error: String::from("only buyer can complete sale"),
        });
    }

    let state = config_read(deps.storage).load()?;
    let mut response = Response::new();

    // transfer price in dcc token to seller
    response = response.add_message(WasmMsg::Execute {
        contract_addr: state.dcc_address.into_string(),
        msg: to_binary(&DCCWasmMsg::ExecutorTransfer {
            amount: sale.price.amount,
            sender: info.sender.into_string(),
            recipient: sale.owner.clone().into_string(),
        })?,
        funds: vec![],
    });

    // transfer asset to buyer
    response = response.add_message(BankMsg::Send {
        to_address: sale.owner.to_string(),
        amount: vec![sale.asset.clone()],
    });

    // update status to complete
    sale.status = Status::Complete;
    get_sale_storage(deps.storage).save(id.as_bytes(), &sale)?;

    response = response.add_attributes(vec![
        attr("action", "complete_sale"),
        attr("owner", sale.owner),
        attr("buyer", sale.buyer),
        attr("asset_denom", sale.asset.denom),
        attr("asset_amount", sale.asset.amount),
        attr("price_denom", sale.price.denom),
        attr("price_amount", sale.price.amount),
    ]);

    Ok(response)
}

fn cancel_sale(
    deps: DepsMut<ProvenanceQuery>,
    info: MessageInfo,
    id: String,
) -> Result<Response<ProvenanceMsg>, ContractError> {
    // validate params
    if !info.funds.is_empty() {
        return Err(contract_err(
            "no funds should be sent during sale cancellation",
        ));
    }

    // ensure existence
    let mut sale = get_sale_storage_read(deps.storage)
        .load(id.as_bytes())
        .map_err(ContractError::Std)?;

    // ensure pending
    if sale.status != Status::Pending {
        return Err(contract_err(&format!(
            "sale is in invalid state {}",
            sale.status,
        )));
    }

    // ensure owner is sender
    if sale.owner != info.sender {
        return Err(ContractError::Unauthorized {
            error: String::from("only owner can cancel sale"),
        });
    }

    let mut response = Response::new();

    // return asset back to owner
    response = response.add_message(BankMsg::Send {
        to_address: sale.owner.to_string(),
        amount: vec![sale.asset.clone()],
    });

    // update status to canceled
    sale.status = Status::Canceled;
    get_sale_storage(deps.storage).save(id.as_bytes(), &sale)?;

    response = response.add_attributes(vec![
        attr("action", "cancel_sale"),
        attr("owner", &sale.owner),
        attr("buyer", &sale.buyer),
        attr("asset_denom", &sale.asset.denom),
        attr("asset_amount", &sale.asset.amount.to_string()),
        attr("price_denom", &sale.price.denom),
        attr("price_amount", &sale.price.amount.to_string()),
    ]);

    Ok(response)
}

#[entry_point]
pub fn query(deps: Deps<ProvenanceQuery>, _env: Env, msg: QueryMsg) -> StdResult<Binary> {
    msg.validate()?;

    match msg {
        QueryMsg::GetSale { id } => {
            to_binary(&get_sale_storage_read(deps.storage).load(id.as_bytes())?)
        }
        QueryMsg::GetContractInfo {} => to_binary(&config_read(deps.storage).load()?),
        QueryMsg::GetVersionInfo {} => to_binary(&get_contract_version(deps.storage)?),
    }
}

// unit tests
#[cfg(test)]
mod tests {
    use crate::contract::execute;
    use crate::msg::ExecuteMsg;
    use crate::state::{config, get_sale_storage, get_sale_storage_read, Sale, State, Status};
    use crate::ContractError;
    use cosmwasm_std::testing::{mock_env, mock_info};
    use cosmwasm_std::{attr, from_binary, Addr, Binary, Coin, StdError, Storage, Uint128};
    use provwasm_mocks::mock_dependencies;
    use provwasm_std::Marker;

    const ID: &str = "1bd75dc7-5c15-4ef8-9d8c-de9c106d1fdc";
    const ADMIN_ADDRESS: &str = "tp1343puvn0uvzrmxkwzkx62z9eay56ag9y49f3af";
    const OWNER_ADDRESS: &str = "tp1vxyyagf9p7se5rhh9vxs39tswr0srckusjq585";
    const BUYER_ADDRESS: &str = "tp1gjuyjcgxhmv4d003ja576dmzfeu5pjjex7h5k3";
    const ASSET_DENOM: &str = "ibc/loan1";
    const DCC_DENOM: &str = "usdf.c";
    const DCC_ADDRESS: &str = "tp1u9t9ung76wzw88jhr0pa5szvl6ystdfeey2mrm";

    #[test]
    fn create_sale_success() {
        let mut deps = mock_dependencies(&[]);

        setup_test_base(
            &mut deps.storage,
            &State {
                admin: Addr::unchecked(ADMIN_ADDRESS),
                dcc_address: Addr::unchecked(DCC_ADDRESS),
                dcc_denom: DCC_DENOM.into(),
            },
        );

        let restricted_marker = setup_restricted_marker();
        let unrestricted_marker: Marker = setup_unrestricted_marker();
        deps.querier
            .with_markers(vec![restricted_marker, unrestricted_marker]);

        let asset = Coin {
            amount: Uint128::new(1),
            denom: ASSET_DENOM.into(),
        };

        let sender_info = mock_info(OWNER_ADDRESS, &[asset.clone()]);

        let price = Coin {
            amount: Uint128::new(100),
            denom: DCC_DENOM.into(),
        };

        let create_msg = ExecuteMsg::CreateSale {
            id: ID.into(),
            price: price.clone(),
            buyer: BUYER_ADDRESS.into(),
        };

        let create_response = execute(deps.as_mut(), mock_env(), sender_info.clone(), create_msg);

        match create_response {
            Ok(response) => {
                assert_eq!(response.attributes.len(), 7);

                assert_eq!(response.attributes[0], attr("action", "create_sale"));
                assert_eq!(response.attributes[1], attr("owner", OWNER_ADDRESS));
                assert_eq!(response.attributes[2], attr("buyer", BUYER_ADDRESS));
                assert_eq!(
                    response.attributes[3],
                    attr("asset_denom", asset.clone().denom)
                );
                assert_eq!(
                    response.attributes[4],
                    attr("asset_amount", asset.clone().amount)
                );
                assert_eq!(
                    response.attributes[5],
                    attr("price_denom", price.clone().denom)
                );
                assert_eq!(
                    response.attributes[6],
                    attr("price_amount", price.clone().amount)
                );
            }
            Err(error) => {
                panic!("failed to create asset sale: {:?}", error)
            }
        }

        match get_sale_storage_read(&deps.storage).load(ID.as_bytes()) {
            Ok(sale) => {
                assert_eq!(
                    Sale {
                        id: ID.into(),
                        asset,
                        owner: Addr::unchecked(OWNER_ADDRESS),
                        buyer: Addr::unchecked(BUYER_ADDRESS),
                        price,
                        status: Status::Pending,
                    },
                    sale
                );
            }
            _ => {
                panic!("sale not found in storage")
            }
        }
    }

    #[test]
    fn create_sale_invalid_params() {
        let mut deps = mock_dependencies(&[]);

        setup_test_base(
            &mut deps.storage,
            &State {
                admin: Addr::unchecked(ADMIN_ADDRESS),
                dcc_address: Addr::unchecked(DCC_ADDRESS),
                dcc_denom: DCC_DENOM.into(),
            },
        );

        let restricted_marker = setup_restricted_marker();
        let unrestricted_marker: Marker = setup_unrestricted_marker();
        deps.querier
            .with_markers(vec![restricted_marker, unrestricted_marker]);

        let asset = Coin {
            amount: Uint128::new(1),
            denom: ASSET_DENOM.into(),
        };

        let price = Coin {
            amount: Uint128::new(100),
            denom: DCC_DENOM.into(),
        };

        // try to create sale without funds
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info(OWNER_ADDRESS, &[]),
            ExecuteMsg::CreateSale {
                id: ID.into(),
                price: price.clone(),
                buyer: BUYER_ADDRESS.into(),
            },
        )
        .unwrap_err();

        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "funds should be sent during sale creation")
            }
            _ => panic!("unexpected execute error"),
        }

        let funds = vec![asset.clone(), price.clone()];

        // try to create sale with multiple funds
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info(OWNER_ADDRESS, &funds),
            ExecuteMsg::CreateSale {
                id: ID.into(),
                price: price.clone(),
                buyer: BUYER_ADDRESS.into(),
            },
        )
        .unwrap_err();

        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "only one fund should be sent during sale creation")
            }
            _ => panic!("unexpected execute error"),
        }

        let funds = vec![Coin {
            amount: Uint128::new(1),
            denom: DCC_DENOM.into(),
        }];

        // try to create sale with same buyer as owner
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info(OWNER_ADDRESS, &funds),
            ExecuteMsg::CreateSale {
                id: ID.into(),
                price: price.clone(),
                buyer: OWNER_ADDRESS.into(),
            },
        )
        .unwrap_err();

        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "owner cannot also be the buyer")
            }
            _ => panic!("unexpected execute error"),
        }

        // try to create sale with invalid restricted token
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info(OWNER_ADDRESS, &funds),
            ExecuteMsg::CreateSale {
                id: ID.into(),
                price: price.clone(),
                buyer: BUYER_ADDRESS.into(),
            },
        )
        .unwrap_err();

        match err {
            ContractError::UnsupportedMarkerType { error } => {
                assert_eq!(error, "fund marker type must be unrestricted")
            }
            _ => panic!("unexpected execute error"),
        }

        let funds = vec![asset.clone()];

        // try to create sale with wrong dcc denom price
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info(OWNER_ADDRESS, &funds),
            ExecuteMsg::CreateSale {
                id: ID.into(),
                price: Coin {
                    amount: Uint128::new(1),
                    denom: "somethingelse".into(),
                },
                buyer: BUYER_ADDRESS.into(),
            },
        )
        .unwrap_err();

        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, format!("price denom must be {}", DCC_DENOM))
            }
            _ => panic!("unexpected execute error"),
        }

        setup_sale_base(
            &mut deps.storage,
            &Sale {
                id: ID.into(),
                asset: Coin {
                    amount: Uint128::new(1),
                    denom: ASSET_DENOM.into(),
                },
                owner: Addr::unchecked(OWNER_ADDRESS),
                buyer: Addr::unchecked(BUYER_ADDRESS),
                price: price.clone(),
                status: Status::Pending,
            },
        );

        // try to create sale duplicate id
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info(OWNER_ADDRESS, &funds),
            ExecuteMsg::CreateSale {
                id: ID.into(),
                price: price.clone(),
                buyer: BUYER_ADDRESS.into(),
            },
        )
        .unwrap_err();

        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, format!("sale with id {} already exists", ID))
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn cancel_sale_success() {
        let mut deps = mock_dependencies(&[]);

        setup_test_base(
            &mut deps.storage,
            &State {
                admin: Addr::unchecked(ADMIN_ADDRESS),
                dcc_address: Addr::unchecked(DCC_ADDRESS),
                dcc_denom: DCC_DENOM.into(),
            },
        );

        let asset = Coin {
            amount: Uint128::new(1),
            denom: ASSET_DENOM.into(),
        };

        let price = Coin {
            amount: Uint128::new(100),
            denom: DCC_DENOM.into(),
        };

        setup_sale_base(
            &mut deps.storage,
            &Sale {
                id: ID.into(),
                asset: asset.clone(),
                owner: Addr::unchecked(OWNER_ADDRESS),
                buyer: Addr::unchecked(BUYER_ADDRESS),
                price: price.clone(),
                status: Status::Pending,
            },
        );

        let sender_info = mock_info(OWNER_ADDRESS, &[]);
        let cancel_msg = ExecuteMsg::CancelSale { id: ID.into() };
        let cancel_response = execute(deps.as_mut(), mock_env(), sender_info.clone(), cancel_msg);

        match cancel_response {
            Ok(response) => {
                assert_eq!(response.attributes.len(), 7);

                assert_eq!(response.attributes[0], attr("action", "cancel_sale"));
                assert_eq!(response.attributes[1], attr("owner", OWNER_ADDRESS));
                assert_eq!(response.attributes[2], attr("buyer", BUYER_ADDRESS));
                assert_eq!(
                    response.attributes[3],
                    attr("asset_denom", asset.clone().denom)
                );
                assert_eq!(
                    response.attributes[4],
                    attr("asset_amount", asset.clone().amount)
                );
                assert_eq!(
                    response.attributes[5],
                    attr("price_denom", price.clone().denom)
                );
                assert_eq!(
                    response.attributes[6],
                    attr("price_amount", price.clone().amount)
                );
            }
            Err(error) => {
                panic!("failed to cancel asset sale: {:?}", error)
            }
        }

        match get_sale_storage_read(&deps.storage).load(ID.as_bytes()) {
            Ok(sale) => {
                assert_eq!(
                    Sale {
                        id: ID.into(),
                        asset,
                        owner: Addr::unchecked(OWNER_ADDRESS),
                        buyer: Addr::unchecked(BUYER_ADDRESS),
                        price,
                        status: Status::Canceled,
                    },
                    sale
                );
            }
            _ => {
                panic!("sale not found in storage")
            }
        }
    }

    #[test]
    fn cancel_sale_invalid_params() {
        let mut deps = mock_dependencies(&[]);

        setup_test_base(
            &mut deps.storage,
            &State {
                admin: Addr::unchecked(ADMIN_ADDRESS),
                dcc_address: Addr::unchecked(DCC_ADDRESS),
                dcc_denom: DCC_DENOM.into(),
            },
        );

        let asset = Coin {
            amount: Uint128::new(1),
            denom: ASSET_DENOM.into(),
        };

        let price = Coin {
            amount: Uint128::new(100),
            denom: DCC_DENOM.into(),
        };

        let id2 = "91c5ce26-0ef5-4163-9786-2d8f81eb9f56";

        setup_sale_base(
            &mut deps.storage,
            &Sale {
                id: ID.into(),
                asset: asset.clone(),
                owner: Addr::unchecked(OWNER_ADDRESS),
                buyer: Addr::unchecked(BUYER_ADDRESS),
                price: price.clone(),
                status: Status::Pending,
            },
        );

        setup_sale_base(
            &mut deps.storage,
            &Sale {
                id: id2.into(),
                asset: asset.clone(),
                owner: Addr::unchecked(OWNER_ADDRESS),
                buyer: Addr::unchecked(BUYER_ADDRESS),
                price: price.clone(),
                status: Status::Canceled,
            },
        );

        // try to cancel sale with funds
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info(OWNER_ADDRESS, &vec![asset.clone()]),
            ExecuteMsg::CancelSale { id: ID.into() },
        )
        .unwrap_err();

        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "no funds should be sent during sale cancellation")
            }
            _ => panic!("unexpected execute error"),
        }

        // try to cancel sale with not found ID
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info(OWNER_ADDRESS, &vec![]),
            ExecuteMsg::CancelSale {
                id: "b183d578-418f-4edd-b23d-9219d408178c".into(),
            },
        )
        .unwrap_err();

        match err {
            ContractError::Std(StdError::NotFound { .. }) => {}
            _ => panic!("unexpected execute error"),
        }

        // try to cancel sale in not pending state
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info(OWNER_ADDRESS, &vec![]),
            ExecuteMsg::CancelSale { id: id2.into() },
        )
        .unwrap_err();

        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "sale is in invalid state Canceled")
            }
            _ => panic!("unexpected execute error"),
        }

        // try to cancel sale not authorized
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info(BUYER_ADDRESS, &vec![]),
            ExecuteMsg::CancelSale { id: ID.into() },
        )
        .unwrap_err();

        match err {
            ContractError::Unauthorized { error } => {
                assert_eq!(error, "only owner can cancel sale")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn complete_sale_success() {
        let mut deps = mock_dependencies(&[]);

        setup_test_base(
            &mut deps.storage,
            &State {
                admin: Addr::unchecked(ADMIN_ADDRESS),
                dcc_address: Addr::unchecked(DCC_ADDRESS),
                dcc_denom: DCC_DENOM.into(),
            },
        );

        let asset = Coin {
            amount: Uint128::new(1),
            denom: ASSET_DENOM.into(),
        };

        let price = Coin {
            amount: Uint128::new(100),
            denom: DCC_DENOM.into(),
        };

        setup_sale_base(
            &mut deps.storage,
            &Sale {
                id: ID.into(),
                asset: asset.clone(),
                owner: Addr::unchecked(OWNER_ADDRESS),
                buyer: Addr::unchecked(BUYER_ADDRESS),
                price: price.clone(),
                status: Status::Pending,
            },
        );

        let sender_info = mock_info(BUYER_ADDRESS, &[]);
        let complete_msg = ExecuteMsg::CompleteSale { id: ID.into() };
        let complete_response =
            execute(deps.as_mut(), mock_env(), sender_info.clone(), complete_msg);

        match complete_response {
            Ok(response) => {
                assert_eq!(response.attributes.len(), 7);

                assert_eq!(response.attributes[0], attr("action", "complete_sale"));
                assert_eq!(response.attributes[1], attr("owner", OWNER_ADDRESS));
                assert_eq!(response.attributes[2], attr("buyer", BUYER_ADDRESS));
                assert_eq!(
                    response.attributes[3],
                    attr("asset_denom", asset.clone().denom)
                );
                assert_eq!(
                    response.attributes[4],
                    attr("asset_amount", asset.clone().amount)
                );
                assert_eq!(
                    response.attributes[5],
                    attr("price_denom", price.clone().denom)
                );
                assert_eq!(
                    response.attributes[6],
                    attr("price_amount", price.clone().amount)
                );
            }
            Err(error) => {
                panic!("failed to complete asset sale: {:?}", error)
            }
        }

        match get_sale_storage_read(&deps.storage).load(ID.as_bytes()) {
            Ok(sale) => {
                assert_eq!(
                    Sale {
                        id: ID.into(),
                        asset,
                        owner: Addr::unchecked(OWNER_ADDRESS),
                        buyer: Addr::unchecked(BUYER_ADDRESS),
                        price,
                        status: Status::Complete,
                    },
                    sale
                );
            }
            _ => {
                panic!("sale not found in storage")
            }
        }
    }

    #[test]
    fn complete_sale_invalid_params() {
        let mut deps = mock_dependencies(&[]);

        setup_test_base(
            &mut deps.storage,
            &State {
                admin: Addr::unchecked(ADMIN_ADDRESS),
                dcc_address: Addr::unchecked(DCC_ADDRESS),
                dcc_denom: DCC_DENOM.into(),
            },
        );

        let asset = Coin {
            amount: Uint128::new(1),
            denom: ASSET_DENOM.into(),
        };

        let price = Coin {
            amount: Uint128::new(100),
            denom: DCC_DENOM.into(),
        };

        let id2 = "91c5ce26-0ef5-4163-9786-2d8f81eb9f56";

        setup_sale_base(
            &mut deps.storage,
            &Sale {
                id: ID.into(),
                asset: asset.clone(),
                owner: Addr::unchecked(OWNER_ADDRESS),
                buyer: Addr::unchecked(BUYER_ADDRESS),
                price: price.clone(),
                status: Status::Pending,
            },
        );

        setup_sale_base(
            &mut deps.storage,
            &Sale {
                id: id2.into(),
                asset: asset.clone(),
                owner: Addr::unchecked(OWNER_ADDRESS),
                buyer: Addr::unchecked(BUYER_ADDRESS),
                price: price.clone(),
                status: Status::Canceled,
            },
        );

        // try to complete sale with funds
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info(OWNER_ADDRESS, &vec![asset.clone()]),
            ExecuteMsg::CompleteSale { id: ID.into() },
        )
        .unwrap_err();

        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "no funds should be sent during sale completion")
            }
            _ => panic!("unexpected execute error"),
        }

        // try to complete sale with not found ID
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info(OWNER_ADDRESS, &vec![]),
            ExecuteMsg::CompleteSale {
                id: "b183d578-418f-4edd-b23d-9219d408178c".into(),
            },
        )
        .unwrap_err();

        match err {
            ContractError::Std(StdError::NotFound { .. }) => {}
            _ => panic!("unexpected execute error"),
        }

        // try to complete sale in not pending state
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info(OWNER_ADDRESS, &vec![]),
            ExecuteMsg::CompleteSale { id: id2.into() },
        )
        .unwrap_err();

        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "sale is in invalid state Canceled")
            }
            _ => panic!("unexpected execute error"),
        }

        // try to complete sale not authorized
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info(OWNER_ADDRESS, &vec![]),
            ExecuteMsg::CompleteSale { id: ID.into() },
        )
        .unwrap_err();

        match err {
            ContractError::Unauthorized { error } => {
                assert_eq!(error, "only buyer can complete sale")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    fn setup_restricted_marker() -> Marker {
        let marker_json = b"{
              \"address\": \"tp1u9t9ung76wzw88jhr0pa5szvl6ystdfeey2mrm\",
              \"coins\": [
                {
                  \"denom\": \"usdf.c\",
                  \"amount\": \"1000\"
                }
              ],
              \"account_number\": 20,
              \"sequence\": 0,
              \"permissions\": [
                {
                  \"permissions\": [
                    \"burn\",
                    \"delete\",
                    \"deposit\",
                    \"admin\",
                    \"mint\",
                    \"withdraw\"
                  ],
                  \"address\": \"tp19gqz690xl92uyl8teeuljtxypmkhg5wu8zdryq\"
                }
              ],
              \"status\": \"active\",
              \"denom\": \"usdf.c\",
              \"total_supply\": \"1000\",
              \"marker_type\": \"restricted\",
              \"supply_fixed\": false
            }";

        return from_binary(&Binary::from(marker_json)).unwrap();
    }

    fn setup_unrestricted_marker() -> Marker {
        let marker_json = b"{
              \"address\": \"tp1l330sxue4suxz9dhc40e2pns0ymrytf8uz4squ\",
              \"coins\": [
                {
                  \"denom\": \"ibc/loan1\",
                  \"amount\": \"1000\"
                }
              ],
              \"account_number\": 10,
              \"sequence\": 0,
              \"permissions\": [
                {
                  \"permissions\": [
                    \"burn\",
                    \"delete\",
                    \"deposit\",
                    \"admin\",
                    \"mint\",
                    \"withdraw\"
                  ],
                  \"address\": \"tp13pnzut8zdjaqht7aqe7kk4ww5zfq04jzlytnmu\"
                }
              ],
              \"status\": \"active\",
              \"denom\": \"ibc/loan1\",
              \"total_supply\": \"1000\",
              \"marker_type\": \"coin\",
              \"supply_fixed\": false
            }";

        return from_binary(&Binary::from(marker_json)).unwrap();
    }

    fn setup_sale_base(storage: &mut dyn Storage, sale: &Sale) {
        if let Err(error) = get_sale_storage(storage).save(sale.id.as_bytes(), &sale) {
            panic!("unexpected error: {:?}", error)
        }
    }

    fn setup_test_base(storage: &mut dyn Storage, state: &State) {
        if let Err(error) = config(storage).save(&state) {
            panic!("unexpected error: {:?}", error)
        }
    }
}
