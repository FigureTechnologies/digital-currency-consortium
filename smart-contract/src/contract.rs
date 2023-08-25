use std::convert::TryFrom;

use cosmwasm_std::{
    entry_point, to_binary, Addr, Deps, DepsMut, Empty, Env, MessageInfo, Order, QueryResponse,
    Response, StdError, StdResult, Uint128,
};
use cw2::{get_contract_version, set_contract_version, ContractVersion};
use provwasm_std::types::{
    cosmos::base::v1beta1::Coin,
    provenance::attribute::v1::AttributeQuerier,
    provenance::marker::v1::{
        Access, AccessGrant, MarkerAccount, MarkerQuerier, MarkerType,
        MsgAddFinalizeActivateMarkerRequest, MsgBurnRequest, MsgMintRequest, MsgTransferRequest,
        MsgWithdrawRequest,
    },
};
use semver::Version;

use crate::error::ContractError;
use crate::join_proposal::migrate_join_proposals;
use crate::member::{members, members_read, migrate_members, MemberV2};
use crate::msg::{ExecuteMsg, InitMsg, Members, MigrateMsg, QueryMsg};
use crate::state::{config, config_read, migrate_state, StateV2};

// Contract constants
pub static CONTRACT_NAME: &str = env!("CARGO_CRATE_NAME");
pub static CONTRACT_VERSION: &str = env!("CARGO_PKG_VERSION");
pub static MIN_DENOM_LEN: usize = 8;
pub static MIN_NAME_LEN: usize = 4;

/// Create the initial configuration state and propose the marker.
#[entry_point]
pub fn instantiate(
    deps: DepsMut,
    env: Env,
    info: MessageInfo,
    msg: InitMsg,
) -> Result<Response, ContractError> {
    // Validate params
    if !info.funds.is_empty() {
        return Err(contract_err("no funds should be sent during instantiate"));
    }

    // Create and store config state.
    let state = StateV2 {
        admin: info.sender.clone(),
        denom: msg.denom.clone(),
        executors: vec![],
    };
    config(deps.storage).save(&state)?;

    // Create the marker and grant permissions if it doesn't exist.
    let mut res = Response::new();
    if !marker_exists(deps.as_ref(), &msg.denom) {
        // If we need to create the marker, validate denom length.
        if msg.denom.len() < MIN_DENOM_LEN {
            return Err(contract_err("invalid denom length"));
        }

        res = res.add_message(MsgAddFinalizeActivateMarkerRequest {
            amount: Some(Coin {
                denom: msg.denom.clone(),
                amount: "0".to_string(),
            }),
            manager: env.contract.address.to_string(),
            from_address: env.contract.address.to_string(),
            marker_type: MarkerType::Restricted.into(),
            access_list: vec![
                AccessGrant {
                    address: env.contract.address.to_string(),
                    permissions: vec![
                        Access::Mint.into(),
                        Access::Burn.into(),
                        Access::Deposit.into(),
                        Access::Withdraw.into(),
                        Access::Delete.into(),
                        Access::Admin.into(),
                        Access::Transfer.into(),
                    ],
                },
                AccessGrant {
                    address: info.sender.to_string(),
                    // The contract admin is also a marker admin
                    permissions: vec![Access::Admin.into()],
                },
            ],
            supply_fixed: false,
            allow_governance_control: false,
            allow_forced_transfer: false,
            required_attributes: vec![],
        });
    }

    // Set contract version.
    set_contract_version(deps.storage, CONTRACT_NAME, CONTRACT_VERSION)?;

    Ok(res)
}

// Determine whether the marker with the given denom exists.
fn marker_exists(deps: Deps, denom: &str) -> bool {
    let querier = MarkerQuerier::new(&deps.querier);
    if let Ok(resp) = querier.marker(denom.into()) {
        if let Some(_) = resp.marker {
            true
        } else {
            false
        }
    } else {
        false
    }
}

/// Execute the contract
#[entry_point]
pub fn execute(
    deps: DepsMut,
    env: Env,
    info: MessageInfo,
    msg: ExecuteMsg,
) -> Result<Response, ContractError> {
    match msg {
        ExecuteMsg::Join {
            id,
            name,
            kyc_attrs,
        } => try_join(deps, env, info, id, name, kyc_attrs),
        ExecuteMsg::Remove { id } => try_remove(deps, info, id),
        ExecuteMsg::Transfer { amount, recipient } => {
            try_transfer(deps, env, info, amount, recipient)
        }
        ExecuteMsg::Mint { amount, address } => try_mint(deps, env, info, amount, address),
        ExecuteMsg::Burn { amount } => try_burn(deps, env, info, amount),
        ExecuteMsg::AddKyc { id, kyc_attr } => try_add_kyc(deps, info, id, kyc_attr),
        ExecuteMsg::RemoveKyc { id, kyc_attr } => try_remove_kyc(deps, info, id, kyc_attr),
        ExecuteMsg::SetAdmin { id } => try_set_admin(deps, info, id),
        ExecuteMsg::AddExecutor { id } => try_add_executor(deps, info, id),
        ExecuteMsg::RemoveExecutor { id } => try_remove_executor(deps, info, id),
        ExecuteMsg::ExecutorTransfer {
            amount,
            sender,
            recipient,
        } => try_executor_transfer(deps, env, info, amount, sender, recipient),
    }
}

// Add a member to the consortium.
fn try_join(
    deps: DepsMut,
    env: Env,
    info: MessageInfo,
    id: String,
    name: String,
    kyc_attrs: Vec<String>,
) -> Result<Response, ContractError> {
    // Validate params.
    if !info.funds.is_empty() {
        return Err(contract_err("no funds should be sent during join"));
    }
    if name.len() < MIN_NAME_LEN {
        return Err(contract_err("invalid name too short"));
    }
    if kyc_attrs.is_empty() {
        return Err(contract_err("at least one kyc attribute is required"));
    }
    for kyc_attr in &kyc_attrs {
        if kyc_attr.trim().is_empty() {
            return Err(contract_err("kyc attribute name is empty"));
        }
    }

    let mut valid_attrs: Vec<String> = kyc_attrs
        .iter()
        .map(|kyc_attr| kyc_attr.trim().into())
        .collect();
    valid_attrs.sort();
    valid_attrs.dedup();
    if valid_attrs.len() != kyc_attrs.len() {
        return Err(contract_err("duplicate kyc attributes in args"));
    }

    let address = deps.api.addr_validate(&id)?;
    let key = address.as_bytes();

    // Read state
    let state = config_read(deps.storage).load()?;

    // Ensure message sender is admin.
    if info.sender != state.admin {
        return Err(ContractError::Unauthorized {});
    }

    // Verify kyc attribute does not already exist
    let curr_kyc_attrs = get_attributes(deps.as_ref())?;
    for kyc_attr in &valid_attrs {
        if curr_kyc_attrs.contains(kyc_attr) {
            return Err(contract_err("duplicate kyc attribute"));
        }
    }

    // Check for existing member
    let mut members = members(deps.storage);
    if members.may_load(key)?.is_some() {
        return Err(contract_err("duplicate member"));
    }

    members.save(
        key,
        &MemberV2 {
            id: address.clone(),
            joined: Uint128::from(env.block.height),
            name,
            kyc_attrs: valid_attrs.clone(),
        },
    )?;

    let res = Response::new()
        .add_attribute("action", "join")
        .add_attribute("member_id", address.clone());
    Ok(res)
}

// Remove a member from the consortium.
fn try_remove(deps: DepsMut, info: MessageInfo, id: String) -> Result<Response, ContractError> {
    // Validate params.
    if !info.funds.is_empty() {
        return Err(contract_err("no funds should be sent during cancel"));
    }

    let address = deps.api.addr_validate(&id)?;
    let key = address.as_bytes();

    // Read state
    let state = config_read(deps.storage).load()?;

    // Ensure message sender is admin.
    if info.sender != state.admin {
        return Err(ContractError::Unauthorized {});
    }

    let mut members = members(deps.storage);
    if members.may_load(key)?.is_none() {
        return Err(contract_err("member does not exist"));
    }

    // TODO - validate when it is okay to remove a member, including impact on any
    // addresses holding USDF that belong to member.
    members.remove(key);

    let res = Response::new()
        .add_attribute("action", "remove")
        .add_attribute("member_id", address.clone());
    Ok(res)
}

// Transfer token from sender to recipient. Both accounts must either be member accounts, or
// have the required kyc attributes.
fn try_transfer(
    deps: DepsMut,
    env: Env,
    info: MessageInfo,
    amount: Uint128,
    recipient: String,
) -> Result<Response, ContractError> {
    // Ensure no funds were sent
    if !info.funds.is_empty() {
        return Err(contract_err("bank sends are not allowed in transfer"));
    }

    // Ensure amount is non-zero.
    if amount.is_zero() {
        return Err(contract_err("invalid transfer amount"));
    }

    // Validate address
    let recipient = deps.api.addr_validate(&recipient)?;

    // Read state
    let state = config_read(deps.storage).load()?;

    // Ensure the sender holds at least the indicated amount of token.
    let balance = deps
        .querier
        .query_balance(info.sender.clone(), &state.denom)?;
    if balance.amount < amount {
        return Err(contract_err("insufficient token balance in transfer"));
    }

    // Ensure accounts have the required member kyc attribute.
    let members: Vec<MemberV2> = get_members(deps.as_ref())?;
    let from_member = match members_read(deps.storage).may_load(info.sender.as_bytes())? {
        Some(m) => m,
        None => matched_member(deps.as_ref(), info.sender.clone(), members.clone())?,
    };
    let to_member = match members_read(deps.storage).may_load(recipient.as_bytes())? {
        Some(m) => m,
        None => matched_member(deps.as_ref(), recipient.clone(), members)?,
    };

    // Transfer the token
    let coin = Coin {
        denom: state.denom.clone(),
        amount: amount.to_string(),
    };
    let res = Response::new()
        .add_message(MsgTransferRequest {
            amount: Some(coin),
            administrator: env.contract.address.to_string(),
            from_address: info.sender.to_string(),
            to_address: recipient.to_string(),
        })
        .add_attribute("action", "transfer")
        .add_attribute("amount", amount)
        .add_attribute("denom", &state.denom)
        .add_attribute("sender", info.sender)
        .add_attribute("recipient", recipient)
        .add_attribute("from_member_id", &from_member.id)
        .add_attribute("to_member_id", &to_member.id);
    Ok(res)
}

// Increase the reserve supply of a member.
// If an address is provided, mint tokens and withdraw there.
fn try_mint(
    deps: DepsMut,
    env: Env,
    info: MessageInfo,
    amount: Uint128,
    address: Option<String>,
) -> Result<Response, ContractError> {
    // Ensure no funds were sent
    if !info.funds.is_empty() {
        return Err(contract_err("no funds should be sent during mint"));
    }

    // Ensure amount is non-zero.
    if amount.is_zero() {
        return Err(contract_err("invalid mint amount"));
    }

    // Load membership for message sender.
    let key = info.sender.as_bytes();
    let member = members(deps.storage).load(key)?;

    // Ensure member has a kyc attribute set.
    if member.kyc_attrs.is_empty() {
        return Err(contract_err("member is missing kyc attribute"));
    }

    // Mint token.
    let state = config_read(deps.storage).load()?;
    let mut res = Response::new()
        .add_message(MsgMintRequest {
            amount: Some(Coin {
                denom: state.denom.clone(),
                amount: amount.to_string(),
            }),
            administrator: env.contract.address.to_string(),
        })
        // Add wasm event attributes
        .add_attribute("action", "mint")
        .add_attribute("member_id", &member.id)
        .add_attribute("amount", amount)
        .add_attribute("denom", &state.denom);

    // Withdraw to address or fallback.
    match address {
        None => {
            // Withdraw tokens to the member account.
            res = res
                .add_message(MsgWithdrawRequest {
                    denom: state.denom.clone(),
                    administrator: env.contract.address.to_string(),
                    to_address: info.sender.to_string(),
                    amount: vec![Coin {
                        denom: state.denom.clone(),
                        amount: amount.to_string(),
                    }],
                })
                .add_attribute("withdraw_address", info.sender)
        }
        Some(addr) => {
            // When withdrawing tokens to a non-member account, ensure the recipient has the
            // required kyc attribute for member.
            let address = deps.api.addr_validate(&addr)?;
            if address != info.sender {
                matched_member(deps.as_ref(), address.clone(), vec![member])?;
            }
            // Withdraw minted tokens to the provided account.
            res = res
                .add_message(MsgWithdrawRequest {
                    denom: state.denom.clone(),
                    administrator: env.contract.address.to_string(),
                    to_address: address.to_string(),
                    amount: vec![Coin {
                        denom: state.denom.clone(),
                        amount: amount.to_string(),
                    }],
                })
                .add_attribute("withdraw_address", address)
        }
    };
    Ok(res)
}

// Decrease reserve token supply.
fn try_burn(
    deps: DepsMut,
    env: Env,
    info: MessageInfo,
    amount: Uint128,
) -> Result<Response, ContractError> {
    // Validate params.
    if !info.funds.is_empty() {
        return Err(contract_err("no funds should be sent during burn"));
    }

    // Ensure amount is non-zero.
    if amount.is_zero() {
        return Err(contract_err("invalid burn amount"));
    }

    // Load membership for message sender.
    let key = info.sender.as_bytes();
    let member = members(deps.storage).load(key)?;

    // Read state
    let state = config_read(deps.storage).load()?;

    // Ensure the sender holds at least the indicated amount of token.
    let balance = deps
        .querier
        .query_balance(info.sender.clone(), &state.denom)?;
    if balance.amount < amount {
        return Err(contract_err("insufficient token balance in burn"));
    }

    // Get token marker
    let querier = MarkerQuerier::new(&deps.querier);
    let marker = get_marker(state.denom.clone(), &querier)?;

    let res = Response::new()
        // Escrow token in the marker account for burn.
        .add_message(MsgTransferRequest {
            amount: Some(Coin {
                denom: state.denom.clone(),
                amount: amount.to_string(),
            }),
            administrator: env.contract.address.to_string(),
            from_address: info.sender.to_string(),
            to_address: marker.base_account.unwrap().address,
        })
        // Burn the token.
        .add_message(MsgBurnRequest {
            amount: Some(Coin {
                denom: state.denom.clone(),
                amount: amount.to_string(),
            }),
            administrator: env.contract.address.to_string(),
        })
        // Add wasm event attributes.
        .add_attribute("action", "burn")
        .add_attribute("member_id", &member.id)
        .add_attribute("amount", amount)
        .add_attribute("denom", &state.denom);
    Ok(res)
}

// Add a member kyc attribute.
fn try_add_kyc(
    deps: DepsMut,
    info: MessageInfo,
    id: Option<String>,
    kyc_attr: String,
) -> Result<Response, ContractError> {
    // Validate params.
    if !info.funds.is_empty() {
        return Err(contract_err("no funds should be sent during kyc add"));
    }
    if kyc_attr.trim().is_empty() {
        return Err(contract_err("kyc attribute name is empty"));
    }
    let valid_attr = kyc_attr.trim().into();

    // Load state and ensure sender is the administrator or calling id.
    let state = config(deps.storage).load()?;

    let mut member = match id {
        Some(addr) => {
            let address = deps.api.addr_validate(&addr)?;
            // Only admin can modify kyc_attr for different members
            if info.sender != state.admin {
                return Err(ContractError::Unauthorized {});
            }

            members_read(deps.storage).load(address.as_bytes())?
        }
        None => members_read(deps.storage).load(info.sender.as_bytes())?,
    };

    let curr_kyc_attributes = get_attributes(deps.as_ref())?;
    // Ensure kyc attribute wasn't already added
    if curr_kyc_attributes.contains(&valid_attr) {
        return Err(contract_err("kyc attribute already exists"));
    }
    // Add the kyc attribute and save
    member.kyc_attrs.push(valid_attr.clone());
    members(deps.storage).save(member.id.as_bytes(), &member)?;

    // Add wasm event attributes
    Ok(Response::new()
        .add_attribute("action", "add_kyc_attribute")
        .add_attribute("name", valid_attr)
        .add_attribute("member_id", &member.id))
}

// Remove a member kyc attribute.
fn try_remove_kyc(
    deps: DepsMut,
    info: MessageInfo,
    id: Option<String>,
    kyc_attr: String,
) -> Result<Response, ContractError> {
    // Validate params.
    if !info.funds.is_empty() {
        return Err(contract_err("no funds should be sent during kyc remove"));
    }
    if kyc_attr.trim().is_empty() {
        return Err(contract_err("kyc attribute name is empty"));
    }
    let valid_attr = kyc_attr.trim().into();

    // Load state and ensure sender is the administrator or calling id.
    let state = config(deps.storage).load()?;

    let mut member = match id {
        Some(addr) => {
            let address = deps.api.addr_validate(&addr)?;
            // Only admin can modify kyc_attr for different members
            if info.sender != state.admin {
                return Err(ContractError::Unauthorized {});
            }

            members_read(deps.storage).load(address.as_bytes())?
        }
        None => members_read(deps.storage).load(info.sender.as_bytes())?,
    };
    // Ensure kyc attribute exists
    if !member.kyc_attrs.contains(&valid_attr) {
        return Err(contract_err("kyc attribute does not exist"));
    }

    // Remove the kyc attribute and save
    member.kyc_attrs.retain(|kyc_attr| *kyc_attr != valid_attr);
    members(deps.storage).save(member.id.as_bytes(), &member)?;

    // Add wasm event attributes
    Ok(Response::new()
        .add_attribute("action", "remove_kyc_attribute")
        .add_attribute("name", kyc_attr)
        .add_attribute("member_id", &member.id))
}

fn try_set_admin(deps: DepsMut, info: MessageInfo, id: String) -> Result<Response, ContractError> {
    // Validate params.
    if !info.funds.is_empty() {
        return Err(contract_err("no funds should be sent during set admin"));
    }

    let address = deps.api.addr_validate(&id)?;
    let mut state = config_read(deps.storage).load()?;

    // Ensure message sender is admin.
    if info.sender != state.admin {
        return Err(ContractError::Unauthorized {});
    }

    // Ensure address is changed.
    if state.admin == address {
        return Err(contract_err("admin address is unchanged"));
    }

    // Update the admin and save
    state.admin = address;
    config(deps.storage).save(&state)?;

    // Add wasm event attributes
    Ok(Response::new()
        .add_attribute("action", "set_admin")
        .add_attribute("admin", &state.admin))
}

fn try_add_executor(
    deps: DepsMut,
    info: MessageInfo,
    id: String,
) -> Result<Response, ContractError> {
    // Validate params.
    if !info.funds.is_empty() {
        return Err(contract_err("no funds should be sent during add executor"));
    }

    let address = deps.api.addr_validate(&id)?.into_string();
    let mut state = config_read(deps.storage).load()?;

    // Ensure message sender is admin.
    if info.sender != state.admin {
        return Err(ContractError::Unauthorized {});
    }

    // Ensure executor wasn't already added
    if state.executors.contains(&address) {
        return Err(contract_err("executor already exists"));
    }

    let response = Response::new()
        .add_attribute("action", "add_executor")
        .add_attribute("executor", &address);

    // Add the executor and save
    state.executors.push(address);
    config(deps.storage).save(&state)?;

    // Add wasm event attributes
    Ok(response)
}

fn try_remove_executor(
    deps: DepsMut,
    info: MessageInfo,
    id: String,
) -> Result<Response, ContractError> {
    // Validate params.
    if !info.funds.is_empty() {
        return Err(contract_err(
            "no funds should be sent during remove executor",
        ));
    }

    let address = deps.api.addr_validate(&id)?.into_string();
    let mut state = config_read(deps.storage).load()?;

    // Ensure message sender is admin.
    if info.sender != state.admin {
        return Err(ContractError::Unauthorized {});
    }

    // Ensure executor exists
    if !state.executors.contains(&address) {
        return Err(contract_err("executor does not exist"));
    }

    // Remove the executor and save
    state.executors.retain(|executor| *executor != address);
    config(deps.storage).save(&state)?;

    // Add wasm event attributes
    Ok(Response::new()
        .add_attribute("action", "remove_executor")
        .add_attribute("executor", address))
}

// Transfer token from sender to recipient with sender specified by executor.
// Both accounts must either be member accounts, or have the required kyc attributes.
fn try_executor_transfer(
    deps: DepsMut,
    env: Env,
    info: MessageInfo,
    amount: Uint128,
    sender: String,
    recipient: String,
) -> Result<Response, ContractError> {
    // Read state
    let state = config_read(deps.storage).load()?;

    // Ensure sender is a valid executor
    if !state.executors.contains(&info.sender.into_string()) {
        return Err(ContractError::Unauthorized {});
    }

    // Validate sender address
    let sender = deps.api.addr_validate(&sender)?;

    try_transfer(
        deps,
        env,
        MessageInfo {
            sender,
            funds: info.funds,
        },
        amount,
        recipient,
    )
}

// A helper function for creating generic contract errors.
fn contract_err(s: &str) -> ContractError {
    ContractError::Std(StdError::generic_err(s))
}

// Return the first matched attribute, otherwise return an error.
fn matched_member(
    deps: Deps,
    addr: Addr,
    members: Vec<MemberV2>,
) -> Result<MemberV2, ContractError> {
    // Skip the check if no attributes are required.
    if members.is_empty() {
        return Err(contract_err("requires at least one member"));
    }
    // Check for all provided attributes
    let querier = AttributeQuerier::new(&deps.querier);
    for member in members.iter() {
        for kyc_attr in member.kyc_attrs.iter() {
            let res = querier.attribute(addr.to_string(), kyc_attr.to_string(), None)?;
            if !res.attributes.is_empty() {
                return Ok(member.clone());
            }
        }
    }
    return Err(contract_err(&format!(
        "no kyc attributes found for {}",
        addr
    )));
}

/// Query contract state
#[entry_point]
pub fn query(deps: Deps, _env: Env, msg: QueryMsg) -> Result<QueryResponse, ContractError> {
    match msg {
        QueryMsg::GetMembers {} => try_get_members(deps),
        QueryMsg::GetMember { id } => try_get_member(deps, id),
        QueryMsg::GetContractInfo {} => try_get_contract_info(deps),
        QueryMsg::GetVersionInfo {} => try_get_version_info(deps),
    }
}

// Query all members.
fn try_get_members(deps: Deps) -> Result<QueryResponse, ContractError> {
    Ok(to_binary(&Members {
        members: get_members(deps)?,
    })?)
}

// Query member by ID.
fn try_get_member(deps: Deps, id: String) -> Result<QueryResponse, ContractError> {
    let address = deps.api.addr_validate(&id)?;
    let key = address.as_bytes();
    let member = members_read(deps.storage).load(key)?;
    let bin = to_binary(&member)?;
    Ok(bin)
}

// Query contract state
fn try_get_contract_info(deps: Deps) -> Result<QueryResponse, ContractError> {
    let state = &config_read(deps.storage).load()?;
    let bin = to_binary(state)?;
    Ok(bin)
}

// Get contract version
fn try_get_version_info(deps: Deps) -> Result<QueryResponse, ContractError> {
    let version = &cw2::get_contract_version(deps.storage)?;
    let bin = to_binary(version)?;
    Ok(bin)
}

// Read all members from bucket storage.
fn get_members(deps: Deps) -> Result<Vec<MemberV2>, ContractError> {
    members_read(deps.storage)
        .range(None, None, Order::Ascending)
        .map(|item| {
            let (_, member) = item?;
            Ok(member)
        })
        .collect()
}

// Get all kyc attributes for members.
fn get_attributes(deps: Deps) -> Result<Vec<String>, ContractError> {
    Ok(get_members(deps)?
        .into_iter()
        .flat_map(|item| item.kyc_attrs)
        .collect())
}

fn get_marker(id: String, querier: &MarkerQuerier<Empty>) -> StdResult<MarkerAccount> {
    let response = querier.marker(id)?;
    if let Some(marker) = response.marker {
        return if let Ok(account) = MarkerAccount::try_from(marker) {
            Ok(account)
        } else {
            Err(StdError::generic_err("unable to type-cast marker account"))
        };
    } else {
        Err(StdError::generic_err("no marker found for id"))
    }
}

/// Called when migrating a contract instance to a new code ID.
#[entry_point]
pub fn migrate(mut deps: DepsMut, _env: Env, msg: MigrateMsg) -> Result<Response, ContractError> {
    let ver_result = get_contract_version(deps.storage);
    let ver = match ver_result {
        Ok(ver) => ver,
        // Default if running older version never set
        _ => ContractVersion {
            contract: CONTRACT_NAME.into(),
            version: "0.0.1".into(),
        },
    };

    // Sanity check contract name for match.
    if ver.contract != CONTRACT_NAME {
        return Err(contract_err("can only upgrade from same type"));
    }

    // Ensure we are upgrading to a newer version only.
    let current_version = Version::parse(&ver.version)?;
    let new_version = Version::parse(CONTRACT_VERSION)?;
    if current_version >= new_version {
        return Err(contract_err("cannot upgrade from same or newer version"));
    }

    // migrate state
    migrate_state(deps.branch(), current_version.clone(), &msg)?;

    // migrate join proposals
    migrate_join_proposals(deps.branch(), current_version.clone(), &msg)?;

    // migrate members
    migrate_members(deps.branch(), current_version, &msg)?;

    // lastly, migrate version
    set_contract_version(deps.storage, CONTRACT_NAME, CONTRACT_VERSION)?;

    Ok(Response::default())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[allow(deprecated)]
    use crate::join_proposal::{legacy_join_proposals, JoinProposal};
    #[allow(deprecated)]
    use crate::member::{legacy_members, Member};
    #[allow(deprecated)]
    use crate::msg::VoteChoice;
    #[allow(deprecated)]
    use crate::state::{legacy_config, State};
    use cosmwasm_std::testing::{mock_env, mock_info};
    use cosmwasm_std::{coin, Binary, CosmosMsg, Decimal};
    use prost::Message;
    use provwasm_mocks::mock_provenance_dependencies;
    use provwasm_std::shim::Any;
    use provwasm_std::types::cosmos::auth::v1beta1::BaseAccount;
    use provwasm_std::types::provenance::attribute::v1::{
        Attribute, AttributeType, QueryAttributeRequest, QueryAttributeResponse,
    };
    use provwasm_std::types::provenance::marker::v1::{
        MarkerStatus, QueryMarkerRequest, QueryMarkerResponse,
    };
    use std::convert::TryInto;

    #[test]
    fn valid_init() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();
        let env = mock_env();

        // Init
        let res = instantiate(
            deps.as_mut(),
            env.clone(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Ensure messages were created.
        assert_eq!(1, res.messages.len());
        match &res.messages[0].msg {
            CosmosMsg::Stargate { type_url, value } => {
                let expected: Binary = MsgAddFinalizeActivateMarkerRequest {
                    amount: Some(Coin {
                        denom: "dcc.coin".to_string(),
                        amount: "0".to_string(),
                    }),
                    manager: env.contract.address.to_string(),
                    from_address: env.contract.address.to_string(),
                    marker_type: MarkerType::Restricted.into(),
                    access_list: vec![
                        AccessGrant {
                            address: env.contract.address.to_string(),
                            permissions: vec![1, 2, 3, 4, 5, 6, 7],
                        },
                        AccessGrant {
                            address: "admin".to_string(),
                            permissions: vec![6],
                        },
                    ],
                    supply_fixed: false,
                    allow_governance_control: false,
                    allow_forced_transfer: false,
                    required_attributes: vec![],
                }
                .try_into()
                .unwrap();

                assert_eq!(
                    type_url,
                    "/provenance.marker.v1.MsgAddFinalizeActivateMarkerRequest"
                );
                assert_eq!(value, &expected)
            }
            _ => panic!("unexpected cosmos message"),
        }

        // Read state
        let config_state = config_read(&deps.storage).load().unwrap();

        // Validate state values
        assert_eq!(config_state.denom, "dcc.coin");

        let contract_version = get_contract_version(&deps.storage).unwrap();

        assert_eq!(contract_version.contract, CONTRACT_NAME);
        assert_eq!(contract_version.version, CONTRACT_VERSION);
    }

    #[test]
    fn join_test() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Bank join as member.
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank".into(),
                kyc_attrs: vec!["bank.kyc.pb".into()],
            },
        )
        .unwrap();

        let addr = Addr::unchecked("bank");
        let key: &[u8] = addr.as_bytes();
        let member = members_read(&deps.storage).load(key).unwrap();

        assert_eq!(member.id, "bank");
        assert_eq!(member.joined, Uint128::new(12345));
        assert_eq!(member.kyc_attrs, vec!["bank.kyc.pb"]);
        assert_eq!(member.name, "bank");
    }

    #[test]
    fn join_invalid_params() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Try to create join member w/ too short name
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "ban".into(),
                kyc_attrs: vec!["bank.kyc.pb".into()],
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "invalid name too short")
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to create join member w/ empty kyc attr
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank".into(),
                kyc_attrs: vec![" ".into()],
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "kyc attribute name is empty")
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to create join member not admin
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank".into(),
                kyc_attrs: vec!["bank.kyc.pb".into()],
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Unauthorized {} => {}
            _ => panic!("unexpected execute error"),
        }

        // Try to send funds w/ the join proposal
        let funds = coin(1000, "nhash");
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[funds]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank".into(),
                kyc_attrs: vec!["bank.kyc.pb".into()],
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "no funds should be sent during join")
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to create with same kyc attributes.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank".into(),
                kyc_attrs: vec![
                    "bank.kyc.pb".into(),
                    "bank2.kyc.pb".into(),
                    "bank3.kyc.pb".into(),
                    "bank.kyc.pb".into(),
                ],
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "duplicate kyc attributes in args")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn join_dup_kyc_attribute() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Bank join as member.
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank1".into(),
                kyc_attrs: vec!["bank1.kyc.pb".into()],
            },
        )
        .unwrap();

        // Try to create a duplicate kyc attribute.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank2".into(),
                name: "bank2".into(),
                kyc_attrs: vec!["bank1.kyc.pb".into()],
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "duplicate kyc attribute")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn join_dup_member_id() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Bank join as member.
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank".into(),
                kyc_attrs: vec!["bank.kyc.pb".into()],
            },
        )
        .unwrap();

        // Try to create a duplicate join proposal.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank2".into(),
                kyc_attrs: vec!["bank2.kyc.pb".into()],
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "duplicate member")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn remove_test() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Create join member
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank".into(),
                kyc_attrs: vec!["bank.kyc.pb".into()],
            },
        )
        .unwrap();

        let key = "bank".as_bytes();
        let mut member = members_read(&deps.storage).may_load(key).unwrap();
        assert!(member.is_some());

        // Remove member
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Remove { id: "bank".into() },
        )
        .unwrap();

        member = members_read(&deps.storage).may_load(key).unwrap();
        assert!(member.is_none());
    }

    #[test]
    fn remove_param_errors() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Create join member
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank".into(),
                kyc_attrs: vec!["bank.kyc.pb".into()],
            },
        )
        .unwrap();

        // Try to remove a member that doesn't exist.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Remove { id: "bank1".into() },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg }) => {
                assert_eq!(msg, "member does not exist")
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to remove a member not as admin.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Remove { id: "bank".into() },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Unauthorized {} => {}
            _ => panic!("unexpected execute error"),
        }

        // Try to send funds with the cancel message.
        let funds = coin(1000, "nhash");
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[funds]),
            ExecuteMsg::Remove { id: "bank".into() },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg }) => {
                assert_eq!(msg, "no funds should be sent during cancel")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn transfer_test() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();
        let env = mock_env();

        // Init
        instantiate(
            deps.as_mut(),
            env.clone(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Create join member
        execute(
            deps.as_mut(),
            env.clone(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank".into(),
                kyc_attrs: vec!["bank.kyc.pb".into()],
            },
        )
        .unwrap();

        // Assume the customer has a balance of tokens + the required attribute.
        let dcc = coin(1000, "dcc.coin");
        deps.querier
            .mock_querier
            .update_balance("customer", vec![dcc]);

        QueryAttributeRequest::mock_response(
            &mut deps.querier,
            QueryAttributeResponse {
                account: "customer".to_string(),
                attributes: vec![Attribute {
                    name: "bank.kyc.pb".to_string(),
                    value: "ok".as_bytes().to_vec(),
                    attribute_type: AttributeType::String.into(),
                    address: "".to_string(),
                }],
                pagination: None,
            },
        );

        // Transfer dcc from the customer to a member bank.
        let res = execute(
            deps.as_mut(),
            env.clone(),
            mock_info("customer", &[]),
            ExecuteMsg::Transfer {
                amount: Uint128::new(500),
                recipient: "bank".into(),
            },
        )
        .unwrap();

        // Ensure message was created.
        assert_eq!(1, res.messages.len());
        match &res.messages[0].msg {
            CosmosMsg::Stargate { type_url, value } => {
                let expected: Binary = MsgTransferRequest {
                    amount: Some(Coin {
                        denom: "dcc.coin".to_string(),
                        amount: "500".to_string(),
                    }),
                    administrator: env.contract.address.to_string(),
                    from_address: "customer".to_string(),
                    to_address: "bank".to_string(),
                }
                .try_into()
                .unwrap();

                assert_eq!(type_url, "/provenance.marker.v1.MsgTransferRequest");
                assert_eq!(value, &expected)
            }
            _ => panic!("unexpected cosmos message"),
        }
    }

    #[test]
    fn transfer_param_errors() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Create join member
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank".into(),
                kyc_attrs: vec!["bank.kyc.pb".into()],
            },
        )
        .unwrap();

        // Try to transfer zero dcc from the customer to a member bank.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("customer", &[]),
            ExecuteMsg::Transfer {
                amount: Uint128::zero(),
                recipient: "bank".into(),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "invalid transfer amount")
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to send additional funds during transfer.
        let funds = coin(1000, "nhash");
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("customer", &[funds]),
            ExecuteMsg::Transfer {
                amount: Uint128::new(500),
                recipient: "bank".into(),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "bank sends are not allowed in transfer")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn transfer_insufficient_funds() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Create join member
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank".into(),
                kyc_attrs: vec!["bank.kyc.pb".into()],
            },
        )
        .unwrap();

        // Assume the customer has the required attribute, but no tokens.
        QueryAttributeRequest::mock_response(
            &mut deps.querier,
            QueryAttributeResponse {
                account: "customer".to_string(),
                attributes: vec![Attribute {
                    name: "bank.kyc.pb".to_string(),
                    value: "ok".as_bytes().to_vec(),
                    attribute_type: AttributeType::String.into(),
                    address: "".to_string(),
                }],
                pagination: None,
            },
        );

        // Try to transfer dcc from the customer to the member bank.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("customer", &[]),
            ExecuteMsg::Transfer {
                amount: Uint128::new(500),
                recipient: "bank".into(),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "insufficient token balance in transfer")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn transfer_without_from_attribute() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Create join member
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank".into(),
                kyc_attrs: vec!["bank.kyc.pb".into()],
            },
        )
        .unwrap();

        // Assume the customer has a balance of dcc, but does NOT have the required attribute.
        let dcc = coin(1000, "dcc.coin");
        deps.querier
            .mock_querier
            .update_balance("customer", vec![dcc]);
        QueryAttributeRequest::mock_response(
            &mut deps.querier,
            QueryAttributeResponse {
                account: "customer".to_string(),
                attributes: vec![],
                pagination: None,
            },
        );

        // Try to transfer dcc from the customer to a member bank.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("customer", &[]),
            ExecuteMsg::Transfer {
                amount: Uint128::new(500),
                recipient: "bank".into(),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "no kyc attributes found for customer")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn transfer_without_to_attribute() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Create join member
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank".into(),
                kyc_attrs: vec!["bank.kyc.pb".into()],
            },
        )
        .unwrap();

        // Assume the customer has a balance of tokens + the required attribute.
        let dcc = coin(1000, "dcc.coin");
        deps.querier
            .mock_querier
            .update_balance("customer", vec![dcc]);

        // TODO - fix test since mock response returns same result no matter the input
        // QueryAttributeRequest::mock_response(
        //     &mut deps.querier,
        //     QueryAttributeResponse {
        //         account: "customer".to_string(),
        //         attributes: vec![Attribute {
        //             name: "bank.kyc.pb".to_string(),
        //             value: "ok".as_bytes().to_vec(),
        //             attribute_type: AttributeType::String.into(),
        //             address: "".to_string(),
        //         }],
        //         pagination: None,
        //     },
        // );
        QueryAttributeRequest::mock_response(
            &mut deps.querier,
            QueryAttributeResponse {
                account: "customer".to_string(),
                attributes: vec![],
                pagination: None,
            },
        );

        // Transfer dcc from the customer to a member bank.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("customer", &[]),
            ExecuteMsg::Transfer {
                amount: Uint128::new(500),
                recipient: "customer2".into(),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                // TODO - reenable after mock response return is fixed
                // assert_eq!(msg, "no kyc attributes found for customer2")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn mint_test() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();
        let env = mock_env();

        // Init
        instantiate(
            deps.as_mut(),
            env.clone(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Create join member
        execute(
            deps.as_mut(),
            env.clone(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank".into(),
                kyc_attrs: vec!["bank.kyc.pb".into()],
            },
        )
        .unwrap();

        // Mint reserve tokens and withdraw them.
        let res = execute(
            deps.as_mut(),
            env.clone(),
            mock_info("bank", &[]),
            ExecuteMsg::Mint {
                amount: Uint128::new(100),
                address: None,
            },
        )
        .unwrap();

        // Ensure messages were created.
        assert_eq!(2, res.messages.len());
        match &res.messages[0].msg {
            CosmosMsg::Stargate { type_url, value } => {
                let expected: Binary = MsgMintRequest {
                    amount: Some(Coin {
                        denom: "dcc.coin".to_string(),
                        amount: "100".to_string(),
                    }),
                    administrator: env.contract.address.to_string(),
                }
                .try_into()
                .unwrap();

                assert_eq!(type_url, "/provenance.marker.v1.MsgMintRequest");
                assert_eq!(value, &expected)
            }
            _ => panic!("unexpected cosmos message"),
        }

        match &res.messages[1].msg {
            CosmosMsg::Stargate { type_url, value } => {
                let expected: Binary = MsgWithdrawRequest {
                    denom: "dcc.coin".to_string(),
                    administrator: env.contract.address.to_string(),
                    to_address: "bank".to_string(),
                    amount: vec![Coin {
                        denom: "dcc.coin".to_string(),
                        amount: "100".to_string(),
                    }],
                }
                .try_into()
                .unwrap();

                assert_eq!(type_url, "/provenance.marker.v1.MsgWithdrawRequest");
                assert_eq!(value, &expected)
            }
            _ => panic!("unexpected cosmos message"),
        }
    }

    #[test]
    fn mint_param_errors() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Create join member
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank".into(),
                kyc_attrs: vec!["bank.kyc.pb".into()],
            },
        )
        .unwrap();

        // Try to mint zero reserve tokens
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Mint {
                amount: Uint128::zero(),
                address: None,
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg }) => {
                assert_eq!(msg, "invalid mint amount")
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to send funds with the mint message
        let funds = coin(1000, "nhash");
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[funds]),
            ExecuteMsg::Mint {
                amount: Uint128::new(1000),
                address: None,
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg }) => {
                assert_eq!(msg, "no funds should be sent during mint")
            }
            _ => panic!("unexpected execute error"),
        }

        // Call mint as a non-member
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("non.member", &[]),
            ExecuteMsg::Mint {
                amount: Uint128::new(1000),
                address: None,
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::NotFound { kind }) => {
                assert_eq!(kind, "dcc::member::MemberV2");
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn mint_withdraw_test() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();
        let env = mock_env();

        // Init
        instantiate(
            deps.as_mut(),
            env.clone(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Create join member
        execute(
            deps.as_mut(),
            env.clone(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank".into(),
                kyc_attrs: vec!["bank.kyc.pb".into()],
            },
        )
        .unwrap();

        // Assume the customer has the required attribute.
        QueryAttributeRequest::mock_response(
            &mut deps.querier,
            QueryAttributeResponse {
                account: "customer".to_string(),
                attributes: vec![Attribute {
                    name: "bank.kyc.pb".to_string(),
                    value: "ok".as_bytes().to_vec(),
                    attribute_type: AttributeType::String.into(),
                    address: "".to_string(),
                }],
                pagination: None,
            },
        );

        // Mint reserve tokens and withdraw dcc to a customer address.
        let res = execute(
            deps.as_mut(),
            env.clone(),
            mock_info("bank", &[]),
            ExecuteMsg::Mint {
                amount: Uint128::new(100),
                address: Some("customer".into()),
            },
        )
        .unwrap();

        // Ensure messages were created.
        assert_eq!(2, res.messages.len());
        match &res.messages[0].msg {
            CosmosMsg::Stargate { type_url, value } => {
                let expected: Binary = MsgMintRequest {
                    amount: Some(Coin {
                        denom: "dcc.coin".to_string(),
                        amount: "100".to_string(),
                    }),
                    administrator: env.contract.address.to_string(),
                }
                .try_into()
                .unwrap();

                assert_eq!(type_url, "/provenance.marker.v1.MsgMintRequest");
                assert_eq!(value, &expected)
            }
            _ => panic!("unexpected cosmos message"),
        }

        match &res.messages[1].msg {
            CosmosMsg::Stargate { type_url, value } => {
                let expected: Binary = MsgWithdrawRequest {
                    denom: "dcc.coin".to_string(),
                    administrator: env.contract.address.to_string(),
                    to_address: "customer".to_string(),
                    amount: vec![Coin {
                        denom: "dcc.coin".to_string(),
                        amount: "100".to_string(),
                    }],
                }
                .try_into()
                .unwrap();

                assert_eq!(type_url, "/provenance.marker.v1.MsgWithdrawRequest");
                assert_eq!(value, &expected)
            }
            _ => panic!("unexpected cosmos message"),
        }
    }

    #[test]
    fn mint_withdraw_not_member_customer() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Create join member 1
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank1".into(),
                name: "bank1".into(),
                kyc_attrs: vec!["bank1.kyc.pb".into()],
            },
        )
        .unwrap();

        // Create join member 2
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank2".into(),
                name: "bank2".into(),
                kyc_attrs: vec!["bank2.kyc.pb".into()],
            },
        )
        .unwrap();

        // TODO - fix mock response so it is only returnable for checking bank2 and not bank1
        // Assume the customer has the required attribute.
        // QueryAttributeRequest::mock_response(
        //     &mut deps.querier,
        //     QueryAttributeResponse {
        //         account: "customer".to_string(),
        //         attributes: vec![Attribute {
        //             name: "bank2.kyc.pb".to_string(),
        //             value: "ok".as_bytes().to_vec(),
        //             attribute_type: AttributeType::String.into(),
        //             address: "".to_string(),
        //         }],
        //         pagination: None,
        //     },
        // );

        QueryAttributeRequest::mock_response(
            &mut deps.querier,
            QueryAttributeResponse {
                account: "customer".to_string(),
                attributes: vec![],
                pagination: None,
            },
        );

        // Mint reserve tokens and withdraw dcc to a customer address.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank1", &[]),
            ExecuteMsg::Mint {
                amount: Uint128::new(100),
                address: Some("customer".into()),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg }) => {
                assert_eq!(msg, "no kyc attributes found for customer")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn mint_withdraw_no_attribute() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Create join member
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank".into(),
                kyc_attrs: vec!["bank.kyc.pb".into()],
            },
        )
        .unwrap();

        QueryAttributeRequest::mock_response(
            &mut deps.querier,
            QueryAttributeResponse {
                account: "customer".to_string(),
                attributes: vec![],
                pagination: None,
            },
        );

        // Mint reserve tokens and withdraw dcc to a customer address.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Mint {
                amount: Uint128::new(100),
                address: Some("customer".into()),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg }) => {
                assert_eq!(msg, "no kyc attributes found for customer")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn burn_test() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();
        let env = mock_env();

        // Burn needs to query the marker address, so we mock one here.
        let expected_marker = MarkerAccount {
            base_account: Some(BaseAccount {
                address: "dcc.marker".to_string(),
                pub_key: None,
                account_number: 1,
                sequence: 0,
            }),
            manager: env.contract.address.to_string(),
            access_control: vec![AccessGrant {
                address: "tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz".to_string(),
                permissions: vec![1, 2, 3, 4, 5, 6, 7],
            }],
            status: MarkerStatus::Active.into(),
            denom: "dcc.coin".to_string(),
            supply: "0".to_string(),
            marker_type: MarkerType::Coin.into(),
            supply_fixed: false,
            allow_governance_control: false,
            allow_forced_transfer: false,
            required_attributes: vec![],
        };

        let mock_marker_response = QueryMarkerResponse {
            marker: Some(Any {
                type_url: "/provenance.marker.v1.MarkerAccount".to_string(),
                value: expected_marker.encode_to_vec(),
            }),
        };

        QueryMarkerRequest::mock_response(&mut deps.querier, mock_marker_response);

        // Init
        instantiate(
            deps.as_mut(),
            env.clone(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Create join member
        execute(
            deps.as_mut(),
            env.clone(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank".into(),
                kyc_attrs: vec!["bank.kyc.pb".into()],
            },
        )
        .unwrap();

        // Mint reserve tokens.
        execute(
            deps.as_mut(),
            env.clone(),
            mock_info("bank", &[]),
            ExecuteMsg::Mint {
                amount: Uint128::new(100),
                address: None,
            },
        )
        .unwrap();

        // Simulate balance update due to mint.
        let minted = coin(100, "dcc.coin");
        deps.querier
            .mock_querier
            .update_balance("bank", vec![minted]);

        // Burn reserve tokens.
        let res = execute(
            deps.as_mut(),
            env.clone(),
            mock_info("bank", &[]),
            ExecuteMsg::Burn {
                amount: Uint128::new(25),
            },
        )
        .unwrap();

        // Ensure messages were created.
        assert_eq!(2, res.messages.len());
        match &res.messages[0].msg {
            CosmosMsg::Stargate { type_url, value } => {
                let expected: Binary = MsgTransferRequest {
                    amount: Some(Coin {
                        denom: "dcc.coin".to_string(),
                        amount: "25".to_string(),
                    }),
                    administrator: env.contract.address.to_string(),
                    from_address: "bank".to_string(),
                    to_address: expected_marker.base_account.unwrap().address,
                }
                .try_into()
                .unwrap();

                assert_eq!(type_url, "/provenance.marker.v1.MsgTransferRequest");
                assert_eq!(value, &expected)
            }
            _ => panic!("unexpected cosmos message"),
        }

        match &res.messages[1].msg {
            CosmosMsg::Stargate { type_url, value } => {
                let expected: Binary = MsgBurnRequest {
                    amount: Some(Coin {
                        denom: "dcc.coin".to_string(),
                        amount: "25".to_string(),
                    }),
                    administrator: env.contract.address.to_string(),
                }
                .try_into()
                .unwrap();

                assert_eq!(type_url, "/provenance.marker.v1.MsgBurnRequest");
                assert_eq!(value, &expected)
            }
            _ => panic!("unexpected cosmos message"),
        }
    }

    #[test]
    fn burn_param_errors() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Create join member
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank".into(),
                kyc_attrs: vec!["bank.kyc.pb".into()],
            },
        )
        .unwrap();

        // Try to send additional funds w/ the burn message.
        let funds = coin(1000, "nhash");
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[funds]),
            ExecuteMsg::Burn {
                amount: Uint128::new(500),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "no funds should be sent during burn")
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to send zero amount.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Burn {
                amount: Uint128::zero(),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "invalid burn amount")
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to burn as a non-member.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("non.member", &[]),
            ExecuteMsg::Burn {
                amount: Uint128::new(500),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::NotFound { kind }) => {
                assert_eq!(kind, "dcc::member::MemberV2");
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to burn more than a member holds in their account.
        let dcc = coin(100, "dcc.coin");
        deps.querier.mock_querier.update_balance("bank", vec![dcc]);

        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Burn {
                amount: Uint128::new(500),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "insufficient token balance in burn")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn add_kyc_test() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Create join member
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank".into(),
                kyc_attrs: vec!["bank.kyc.pb".into()],
            },
        )
        .unwrap();

        let key = "bank".as_bytes();
        let config_state = members_read(&deps.storage).load(key).unwrap();
        assert_eq!(config_state.kyc_attrs, vec!["bank.kyc.pb"]);

        // Add new kyc attribute
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::AddKyc {
                id: None,
                kyc_attr: "bank2.kyc.pb".into(),
            },
        )
        .unwrap();

        // Ensure we now have the updated kyc attributes.
        let config_state = members_read(&deps.storage).load(key).unwrap();
        assert_eq!(config_state.kyc_attrs, vec!["bank.kyc.pb", "bank2.kyc.pb"]);
    }

    #[test]
    fn add_kyc_test_admin() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Create join member
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank".into(),
                kyc_attrs: vec!["bank.kyc.pb".into()],
            },
        )
        .unwrap();

        let key = "bank".as_bytes();
        let config_state = members_read(&deps.storage).load(key).unwrap();
        assert_eq!(config_state.kyc_attrs, vec!["bank.kyc.pb"]);

        // Add new kyc attribute
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::AddKyc {
                id: Option::Some("bank".into()),
                kyc_attr: "bank2.kyc.pb".into(),
            },
        )
        .unwrap();

        // Ensure we now have the updated kyc attribute.
        let config_state = members_read(&deps.storage).load(key).unwrap();
        assert_eq!(config_state.kyc_attrs, vec!["bank.kyc.pb", "bank2.kyc.pb"]);
    }

    #[test]
    fn add_kyc_param_errors() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Create join member
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank".into(),
                kyc_attrs: vec!["bank.kyc.pb".into()],
            },
        )
        .unwrap();

        // Try to send funds w/ the kyc message.
        let funds = coin(1000, "nhash");
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[funds]),
            ExecuteMsg::AddKyc {
                id: Option::None,
                kyc_attr: "bank2.kyc.pb".into(),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "no funds should be sent during kyc add")
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to add an empty name.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::AddKyc {
                id: Option::None,
                kyc_attr: "".into(),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "kyc attribute name is empty")
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to add a kyc attribute as a non-admin
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("non.admin", &[]),
            ExecuteMsg::AddKyc {
                id: Option::None,
                kyc_attr: "bank2.kyc.pb".into(),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::NotFound { .. }) => {}
            _ => panic!("unexpected execute error"),
        }

        // Try to add a the kyc attr a second time.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::AddKyc {
                id: Some("bank".into()),
                kyc_attr: "bank.kyc.pb".into(),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "kyc attribute already exists")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn remove_kyc_test() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Create join member
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank".into(),
                kyc_attrs: vec!["bank.kyc.pb".into()],
            },
        )
        .unwrap();

        let key = "bank".as_bytes();
        let config_state = members_read(&deps.storage).load(key).unwrap();
        assert_eq!(config_state.kyc_attrs, vec!["bank.kyc.pb"]);

        // Remove kyc attribute
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::RemoveKyc {
                id: None,
                kyc_attr: "bank.kyc.pb".into(),
            },
        )
        .unwrap();

        // Ensure we now have the updated kyc attributes.
        let config_state = members_read(&deps.storage).load(key).unwrap();
        assert!(config_state.kyc_attrs.is_empty());
    }

    #[test]
    fn remove_kyc_test_admin() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Create join member
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank".into(),
                kyc_attrs: vec!["bank.kyc.pb".into()],
            },
        )
        .unwrap();

        let key = "bank".as_bytes();
        let config_state = members_read(&deps.storage).load(key).unwrap();
        assert_eq!(config_state.kyc_attrs, vec!["bank.kyc.pb"]);

        // Remove new kyc attribute
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::RemoveKyc {
                id: Option::Some("bank".into()),
                kyc_attr: "bank.kyc.pb".into(),
            },
        )
        .unwrap();

        // Ensure we now have the updated kyc attribute.
        let config_state = members_read(&deps.storage).load(key).unwrap();
        assert!(config_state.kyc_attrs.is_empty());
    }

    #[test]
    fn remove_kyc_param_errors() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Create join member
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank".into(),
                kyc_attrs: vec!["bank.kyc.pb".into()],
            },
        )
        .unwrap();

        // Try to send funds w/ the kyc message.
        let funds = coin(1000, "nhash");
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[funds]),
            ExecuteMsg::RemoveKyc {
                id: Option::None,
                kyc_attr: "bank.kyc.pb".into(),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "no funds should be sent during kyc remove")
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to remove an empty name.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::RemoveKyc {
                id: Option::None,
                kyc_attr: "".into(),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "kyc attribute name is empty")
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to remove a kyc attribute as a non-admin
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("non.admin", &[]),
            ExecuteMsg::RemoveKyc {
                id: Option::None,
                kyc_attr: "bank.kyc.pb".into(),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::NotFound { .. }) => {}
            _ => panic!("unexpected execute error"),
        }

        // Try to remove a kyc attr that does not exist.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::RemoveKyc {
                id: Option::Some("bank".into()),
                kyc_attr: "bank2.kyc.pb".into(),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "kyc attribute does not exist")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn set_admin() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::SetAdmin {
                id: "newadmin".into(),
            },
        )
        .unwrap();

        // Ensure admin is changed.
        let state = config_read(&deps.storage).load().unwrap();
        assert_eq!(state.admin, "newadmin")
    }

    #[test]
    fn set_admin_errors() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Try to send funds w/ the kyc message.
        let funds = coin(1000, "nhash");
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[funds]),
            ExecuteMsg::SetAdmin {
                id: "newadmin".into(),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "no funds should be sent during set admin")
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to set name by user not admin
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("notadmin", &[]),
            ExecuteMsg::SetAdmin {
                id: "newadmin".into(),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Unauthorized {} => {}
            _ => panic!("unexpected execute error"),
        }

        // Try to set name to existing admin.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::SetAdmin { id: "admin".into() },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "admin address is unchanged")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn add_executor() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::AddExecutor {
                id: "executor".into(),
            },
        )
        .unwrap();

        // Ensure admin is changed.
        let state = config_read(&deps.storage).load().unwrap();
        assert_eq!(state.executors, vec!["executor"]);
    }

    #[test]
    fn add_executor_errors() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Add single executor
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::AddExecutor {
                id: "executor".into(),
            },
        )
        .unwrap();

        // Try to send funds w/ the kyc message.
        let funds = coin(1000, "nhash");
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[funds]),
            ExecuteMsg::AddExecutor {
                id: "executor2".into(),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "no funds should be sent during add executor")
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to add executor by user not admin
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("notadmin", &[]),
            ExecuteMsg::AddExecutor {
                id: "executor2".into(),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Unauthorized {} => {}
            _ => panic!("unexpected execute error"),
        }

        // Try to add executor already exists.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::AddExecutor {
                id: "executor".into(),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "executor already exists")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn remove_executor() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Add executors
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::AddExecutor {
                id: "executor1".into(),
            },
        )
        .unwrap();

        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::AddExecutor {
                id: "executor2".into(),
            },
        )
        .unwrap();

        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::RemoveExecutor {
                id: "executor1".into(),
            },
        )
        .unwrap();

        // Ensure admin is changed.
        let state = config_read(&deps.storage).load().unwrap();
        assert_eq!(state.executors, vec!["executor2"]);
    }

    #[test]
    fn remove_executor_errors() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Add executors
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::AddExecutor {
                id: "executor1".into(),
            },
        )
        .unwrap();

        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::AddExecutor {
                id: "executor2".into(),
            },
        )
        .unwrap();

        // Try to send funds w/ the kyc message.
        let funds = coin(1000, "nhash");
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[funds]),
            ExecuteMsg::RemoveExecutor {
                id: "executor2".into(),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "no funds should be sent during remove executor")
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to remove executor by user not admin
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("notadmin", &[]),
            ExecuteMsg::RemoveExecutor {
                id: "executor2".into(),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Unauthorized {} => {}
            _ => panic!("unexpected execute error"),
        }

        // Try to remove executor does not exist.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::RemoveExecutor {
                id: "executor".into(),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "executor does not exist")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn executor_transfer_test() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();
        let env = mock_env();

        // Init
        instantiate(
            deps.as_mut(),
            env.clone(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Create join member
        execute(
            deps.as_mut(),
            env.clone(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank".into(),
                kyc_attrs: vec!["bank.kyc.pb".into()],
            },
        )
        .unwrap();

        // Add executor
        execute(
            deps.as_mut(),
            env.clone(),
            mock_info("admin", &[]),
            ExecuteMsg::AddExecutor {
                id: "executor".into(),
            },
        )
        .unwrap();

        // Assume the customer has a balance of tokens + the required attribute.
        let dcc = coin(1000, "dcc.coin");
        deps.querier
            .mock_querier
            .update_balance("customer", vec![dcc]);

        QueryAttributeRequest::mock_response(
            &mut deps.querier,
            QueryAttributeResponse {
                account: "customer".to_string(),
                attributes: vec![Attribute {
                    name: "bank.kyc.pb".to_string(),
                    value: "ok".as_bytes().to_vec(),
                    attribute_type: AttributeType::String.into(),
                    address: "".to_string(),
                }],
                pagination: None,
            },
        );

        // Transfer dcc from the customer to a member bank.
        let res = execute(
            deps.as_mut(),
            env.clone(),
            mock_info("executor", &[]),
            ExecuteMsg::ExecutorTransfer {
                amount: Uint128::new(500),
                sender: "customer".into(),
                recipient: "bank".into(),
            },
        )
        .unwrap();

        // Ensure message was created.
        assert_eq!(1, res.messages.len());
        match &res.messages[0].msg {
            CosmosMsg::Stargate { type_url, value } => {
                let expected: Binary = MsgTransferRequest {
                    amount: Some(Coin {
                        denom: "dcc.coin".to_string(),
                        amount: "500".to_string(),
                    }),
                    administrator: env.contract.address.to_string(),
                    from_address: "customer".to_string(),
                    to_address: "bank".to_string(),
                }
                .try_into()
                .unwrap();

                assert_eq!(type_url, "/provenance.marker.v1.MsgTransferRequest");
                assert_eq!(value, &expected)
            }
            _ => panic!("unexpected cosmos message"),
        }
    }

    #[test]
    fn executor_transfer_param_errors() {
        // Create mock deps.
        let mut deps = mock_provenance_dependencies();

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                denom: "dcc.coin".into(),
            },
        )
        .unwrap();

        // Create join member
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Join {
                id: "bank".into(),
                name: "bank".into(),
                kyc_attrs: vec!["bank.kyc.pb".into()],
            },
        )
        .unwrap();

        // Add executor
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::AddExecutor {
                id: "executor".into(),
            },
        )
        .unwrap();

        // Assume the customer has a balance of tokens + the required attribute.
        let dcc = coin(1000, "dcc.coin");
        deps.querier
            .mock_querier
            .update_balance("customer", vec![dcc]);

        QueryAttributeRequest::mock_response(
            &mut deps.querier,
            QueryAttributeResponse {
                account: "customer".to_string(),
                attributes: vec![Attribute {
                    name: "bank.kyc.pb".to_string(),
                    value: "ok".as_bytes().to_vec(),
                    attribute_type: AttributeType::String.into(),
                    address: "".to_string(),
                }],
                pagination: None,
            },
        );

        // Try to execute transfer without permission.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("notexecutor", &[]),
            ExecuteMsg::ExecutorTransfer {
                amount: Uint128::new(500),
                sender: "customer".into(),
                recipient: "bank".into(),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Unauthorized {} => {}
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    #[allow(deprecated)]
    fn migrate_version() {
        // Create mock deps
        let mut deps = mock_provenance_dependencies();

        // Set the old state
        legacy_config(&mut deps.storage)
            .save(&State {
                admin: Addr::unchecked("id"),
                quorum_pct: Decimal::percent(67),
                dcc_denom: "dcc.coin".to_string(),
                vote_duration: Uint128::new(5000),
                kyc_attrs: vec!["test.kyc.pb".to_string()],
                admin_weight: Uint128::zero(),
            })
            .unwrap();

        // Set old proposals
        legacy_join_proposals(&mut deps.storage)
            .save(
                b"join1",
                &JoinProposal {
                    id: Addr::unchecked("join1"),
                    max_supply: Uint128::new(1000),
                    denom: "test1.dcc".to_string(),
                    created: Uint128::new(50000),
                    expires: Uint128::new(50100),
                    no: Uint128::zero(),
                    yes: Uint128::new(1000),
                    voters: vec![],
                    name: Option::Some("bank1".to_string()),
                    admin_vote: Option::Some(VoteChoice::Yes),
                },
            )
            .unwrap();

        legacy_join_proposals(&mut deps.storage)
            .save(
                b"join2",
                &JoinProposal {
                    id: Addr::unchecked("join2"),
                    max_supply: Uint128::new(1000),
                    denom: "test2.dcc".to_string(),
                    created: Uint128::new(50000),
                    expires: Uint128::new(50100),
                    no: Uint128::zero(),
                    yes: Uint128::new(1000),
                    voters: vec![Addr::unchecked("join1")],
                    name: Option::Some("bank2".to_string()),
                    admin_vote: Option::None,
                },
            )
            .unwrap();

        legacy_members(&mut deps.storage)
            .save(
                b"join1",
                &Member {
                    id: Addr::unchecked("join1"),
                    supply: Uint128::new(100),
                    max_supply: Uint128::new(1000),
                    denom: "test1.dcc".to_string(),
                    joined: Uint128::new(50100),
                    weight: Uint128::new(1000),
                    name: "bank".to_string(),
                },
            )
            .unwrap();

        assert!(get_contract_version(&deps.storage).is_err());

        // Call migrate
        let res = migrate(deps.as_mut(), mock_env(), MigrateMsg {}).unwrap(); // Panics on error

        // Should just get the default response for now
        assert_eq!(res, Response::default());

        let state = config_read(&deps.storage).load().unwrap();

        // Validate state values
        assert_eq!(state.denom, "dcc.coin");
        assert_eq!(state.admin, Addr::unchecked("id"));

        // Validate members migrated
        let members = get_members(deps.as_ref()).unwrap();
        assert_eq!(members.len(), 1);

        // Validate updated version set
        assert!(get_contract_version(&deps.storage).is_ok());
    }

    #[test]
    fn migrate_unchanged() {
        // Create mock deps
        let mut deps = mock_provenance_dependencies();

        config(&mut deps.storage)
            .save(&StateV2 {
                admin: Addr::unchecked("id"),
                denom: "dcc.coin".to_string(),
                executors: vec![],
            })
            .unwrap();

        set_contract_version(&mut deps.storage, CONTRACT_NAME, CONTRACT_VERSION).unwrap();

        // Call migrate
        let err = migrate(deps.as_mut(), mock_env(), MigrateMsg {}).unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "cannot upgrade from same or newer version")
            }
            _ => panic!("unexpected execute error"),
        }
    }
}
