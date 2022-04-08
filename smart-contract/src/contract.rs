use cosmwasm_std::{
    entry_point, to_binary, Addr, Deps, DepsMut, Env, MessageInfo, Order, QueryResponse, Response,
    StdError, Uint128,
};
use provwasm_std::{
    activate_marker, burn_marker_supply, create_marker, finalize_marker, grant_marker_access,
    mint_marker_supply, transfer_marker_coins, withdraw_coins, MarkerAccess, MarkerType,
    ProvenanceMsg, ProvenanceQuerier, ProvenanceQuery,
};

use crate::error::ContractError;
use crate::join_proposal::{
    join_proposals, join_proposals_read, migrate_join_proposals, JoinProposalV2,
};
use crate::member::{members, members_read, migrate_members, MemberV2};
use crate::msg::{ExecuteMsg, InitMsg, JoinProposals, Members, MigrateMsg, QueryMsg, VoteChoice};
use crate::state::{config, config_read, migrate_state, StateV2};
use crate::version_info::migrate_version_info;

// Contract constants
pub static MIN_DENOM_LEN: usize = 8;

/// Create the initial configuration state and propose the dcc marker.
#[entry_point]
pub fn instantiate(
    deps: DepsMut<ProvenanceQuery>,
    env: Env,
    info: MessageInfo,
    msg: InitMsg,
) -> Result<Response<ProvenanceMsg>, ContractError> {
    // Validate params
    if !info.funds.is_empty() {
        return Err(contract_err("no funds should be sent during instantiate"));
    }
    if msg.vote_duration.is_zero() {
        return Err(contract_err("invalid vote duration"));
    }

    // Create and store config state.
    let state = StateV2 {
        admin: info.sender.clone(),
        denom: msg.denom.clone(),
        vote_duration: msg.vote_duration,
    };
    config(deps.storage).save(&state)?;

    // Create the dcc marker and grant permissions if it doesn't exist.
    // TODO - gas limit makes this impossible to use on testnet
    let mut res = Response::new();
    if !marker_exists(deps.as_ref(), &msg.denom) {
        // If we need to create the marker, validate denom length.
        if msg.denom.len() < MIN_DENOM_LEN {
            return Err(contract_err("invalid denom length"));
        }
        res = res
            .add_message(create_marker(0, msg.denom.clone(), MarkerType::Restricted)?)
            .add_message(grant_marker_access(
                &msg.denom,
                env.contract.address,
                MarkerAccess::all(),
            )?)
            .add_message(grant_marker_access(
                &msg.denom,
                info.sender,
                vec![MarkerAccess::Admin], // The contract admin is also a dcc marker admin
            )?)
            .add_message(finalize_marker(&msg.denom)?)
            .add_message(activate_marker(&msg.denom)?);
    }
    Ok(res)
}

// Determine whether the marker with the given denom exists.
fn marker_exists(deps: Deps<ProvenanceQuery>, denom: &str) -> bool {
    let querier = ProvenanceQuerier::new(&deps.querier);
    querier.get_marker_by_denom(denom).is_ok()
}

/// Execute the contract
#[entry_point]
pub fn execute(
    deps: DepsMut<ProvenanceQuery>,
    env: Env,
    info: MessageInfo,
    msg: ExecuteMsg,
) -> Result<Response<ProvenanceMsg>, ContractError> {
    match msg {
        ExecuteMsg::Join { name, kyc_attr } => try_join(deps, env, info, name, kyc_attr),
        ExecuteMsg::Vote { id, choice } => try_vote(deps, env, info, id, choice),
        ExecuteMsg::Cancel {} => try_cancel(deps, info),
        ExecuteMsg::Transfer { amount, recipient } => try_transfer(deps, info, amount, recipient),
        ExecuteMsg::Mint { amount, address } => try_mint(deps, info, amount, address),
        ExecuteMsg::Burn { amount } => try_burn(deps, info, amount),
        ExecuteMsg::SetKyc { id, kyc_attr } => try_set_kyc(deps, info, id, kyc_attr),
    }
}

// Add a proposal to join the consortium that must be voted on by existing members.
fn try_join(
    deps: DepsMut<ProvenanceQuery>,
    env: Env,
    info: MessageInfo,
    name: String,
    kyc_attr: String,
) -> Result<Response<ProvenanceMsg>, ContractError> {
    // Validate params.
    if !info.funds.is_empty() {
        return Err(contract_err("no funds should be sent during join"));
    }

    // Read state
    let state = config_read(deps.storage).load()?;

    // Verify kyc attribute does not already exist
    let kyc_attrs = get_all_attributes(deps.as_ref())?;
    if kyc_attrs.contains(&kyc_attr) {
        return Err(contract_err("kyc attribute already exists"));
    }

    // Check for existing join request
    let key = info.sender.as_bytes();
    let mut proposals = join_proposals(deps.storage);
    if proposals.may_load(key)?.is_some() {
        return Err(contract_err("duplicate proposal"));
    }

    // Persist a join proposal.
    proposals.save(
        key,
        &JoinProposalV2 {
            id: info.sender.clone(),
            created: Uint128::from(env.block.height),
            expires: Uint128::from(env.block.height) + state.vote_duration,
            name,
            admin_vote: Option::None,
            kyc_attr: Option::Some(kyc_attr.clone()),
        },
    )?;

    let res = Response::new()
        .add_attribute("action", "join")
        .add_attribute("kyc_attr", kyc_attr)
        .add_attribute("join_proposal_id", info.sender.to_string());
    Ok(res)
}

// Vote 'yes' or 'no' on a proposal to join the consortium.
fn try_vote(
    deps: DepsMut<ProvenanceQuery>,
    env: Env,
    info: MessageInfo,
    id: String,
    choice: VoteChoice,
) -> Result<Response<ProvenanceMsg>, ContractError> {
    // Validate params.
    if !info.funds.is_empty() {
        return Err(contract_err("no funds should be sent during vote"));
    }
    let address = deps.api.addr_validate(&id)?;
    let key = address.as_bytes();

    // Read state
    let state = config_read(deps.storage).load()?;

    // Ensure message sender is admin.
    if info.sender != state.admin {
        return Err(ContractError::Unauthorized {});
    }

    // Lookup join proposal for address.
    let mut proposals = join_proposals(deps.storage);
    let mut proposal: JoinProposalV2 = proposals.load(key)?;

    // Ensure voting window is open.
    if Uint128::from(env.block.height) >= proposal.expires {
        return Err(contract_err("voting window has closed"));
    }

    // Ensure admin has not already voted.
    if proposal.admin_vote.is_some() {
        return Err(contract_err("admin has already voted"));
    }

    proposal.admin_vote = Some(choice);

    // Save join request state.
    proposals.save(key, &proposal)?;

    // Persist member.
    members(deps.storage).save(
        key,
        &MemberV2 {
            id: info.sender,
            joined: Uint128::from(env.block.height),
            name: proposal.name,
            kyc_attr: proposal.kyc_attr,
        },
    )?;

    // Add wasm event attributes
    let res = Response::new()
        .add_attribute("action", "vote")
        .add_attribute("join_proposal_id", id);
    Ok(res)
}

// Proposers can choose to cancel as long as they haven't already accepted.
fn try_cancel(
    deps: DepsMut<ProvenanceQuery>,
    info: MessageInfo,
) -> Result<Response<ProvenanceMsg>, ContractError> {
    // Validate params.
    if !info.funds.is_empty() {
        return Err(contract_err("no funds should be sent during cancel"));
    }

    // Ensure message sender is not already a member.
    let key = info.sender.as_bytes();
    if members_read(deps.storage).may_load(key)?.is_some() {
        return Err(contract_err("membership already accepted"));
    }

    // Lookup join proposal for message sender.
    let proposal = join_proposals_read(deps.storage).load(key)?;

    // Delete join proposal.
    let mut proposals = join_proposals(deps.storage);
    proposals.remove(key);

    // Cancel and destroy the reserve marker.
    let res = Response::new()
        .add_attribute("action", "cancel")
        .add_attribute("join_proposal_id", &proposal.id);
    Ok(res)
}

// Transfer dcc from sender to recipient. Both accounts must either be member accounts, or
// have the required kyc attributes.
fn try_transfer(
    deps: DepsMut<ProvenanceQuery>,
    info: MessageInfo,
    amount: Uint128,
    recipient: String,
) -> Result<Response<ProvenanceMsg>, ContractError> {
    // Ensure no funds were sent
    if !info.funds.is_empty() {
        return Err(contract_err("bank sends are not allowed in dcc transfer"));
    }

    // Ensure amount is non-zero.
    if amount.is_zero() {
        return Err(contract_err("invalid transfer amount"));
    }

    // Validate address
    let recipient = deps.api.addr_validate(&recipient)?;

    // Read state
    let state = config_read(deps.storage).load()?;

    // Ensure the sender holds at least the indicated amount of dcc.
    let balance = deps
        .querier
        .query_balance(info.sender.clone(), &state.denom)?;
    if balance.amount < amount {
        return Err(contract_err("insufficient dcc balance in transfer"));
    }

    // Ensure accounts have the required kyc attrs if they aren't members.
    let kyc_attrs: Vec<String> = get_attributes(deps.as_ref())?;
    let from_attr = matched_attribute(deps.as_ref(), info.sender.clone(), kyc_attrs.clone())?;
    let to_attr = matched_attribute(deps.as_ref(), recipient.clone(), kyc_attrs)?;

    // Transfer the dcc
    let res = Response::new()
        .add_message(transfer_marker_coins(
            amount.u128(),
            &state.denom,
            recipient.clone(),
            info.sender.clone(),
        )?)
        .add_attribute("action", "transfer")
        .add_attribute("amount", amount)
        .add_attribute("denom", &state.denom)
        .add_attribute("sender", info.sender)
        .add_attribute("recipient", recipient)
        .add_attribute("from_attr", from_attr)
        .add_attribute("to_attr", to_attr);
    Ok(res)
}

// Increase the reserve supply of a member.
// If an address is provided, mint dcc tokens and withdraw there.
fn try_mint(
    deps: DepsMut<ProvenanceQuery>,
    info: MessageInfo,
    amount: Uint128,
    address: Option<String>,
) -> Result<Response<ProvenanceMsg>, ContractError> {
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
    if member.kyc_attr.is_none() {
        return Err(contract_err("member is missing kyc attribute"));
    }
    let member_attr = member.kyc_attr.unwrap();

    // Mint dcc token.
    let state = config_read(deps.storage).load()?;
    let mut res = Response::new()
        .add_message(mint_marker_supply(amount.u128(), &state.denom)?)
        // Add wasm event attributes
        .add_attribute("action", "mint")
        .add_attribute("member_id", &member.id)
        .add_attribute("amount", amount)
        .add_attribute("from_attr", member_attr.clone());

    // Withdraw to address or fallback.
    match address {
        None => {
            // Withdraw dcc tokens to the member account.
            res = res
                .add_message(withdraw_coins(
                    &state.denom,
                    amount.u128(),
                    &state.denom,
                    info.sender.clone(),
                )?)
                .add_attribute("withdraw_denom", &state.denom)
                .add_attribute("withdraw_address", info.sender)
                .add_attribute("to_attr", member_attr);
        }
        Some(a) => {
            // When withdrawing dcc tokens to a non-member account, ensure the recipient has the
            // required kyc attribute for member.
            let address = deps.api.addr_validate(&a)?;
            let to_attr = if address != info.sender {
                ensure_attribute(deps.as_ref(), address.clone(), vec![member_attr])?
            } else {
                member_attr
            };
            // Withdraw minted dcc tokens to the provided account.
            res = res
                .add_message(withdraw_coins(
                    &state.denom,
                    amount.u128(),
                    &state.denom,
                    address.clone(),
                )?)
                .add_attribute("withdraw_denom", &state.denom)
                .add_attribute("withdraw_address", address)
                .add_attribute("to_attr", to_attr);
        }
    };
    Ok(res)
}

// Decrease reserve token supply.
fn try_burn(
    deps: DepsMut<ProvenanceQuery>,
    info: MessageInfo,
    amount: Uint128,
) -> Result<Response<ProvenanceMsg>, ContractError> {
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

    // Ensure the sender holds at least the indicated amount of dcc.
    let balance = deps
        .querier
        .query_balance(info.sender.clone(), &state.denom)?;
    if balance.amount < amount {
        return Err(contract_err("insufficient dcc balance in burn"));
    }

    // Get dcc marker
    let querier = ProvenanceQuerier::new(&deps.querier);
    let dcc_marker = querier.get_marker_by_denom(&state.denom)?;

    let res = Response::new()
        // Escrow dcc in the marker account for burn.
        .add_message(transfer_marker_coins(
            amount.u128(),
            &state.denom,
            dcc_marker.address,
            info.sender.clone(),
        )?)
        // Burn the dcc token.
        .add_message(burn_marker_supply(amount.u128(), &state.denom)?)
        // Add wasm event attributes.// Burn dcc token
        .add_attribute("action", "burn")
        .add_attribute("member_id", &member.id)
        .add_attribute("amount", amount)
        .add_attribute("denom", &state.denom);
    Ok(res)
}

// Set a member kyc attribute.
fn try_set_kyc(
    deps: DepsMut<ProvenanceQuery>,
    info: MessageInfo,
    id: Option<String>,
    kyc_attr: String,
) -> Result<Response<ProvenanceMsg>, ContractError> {
    // Validate params.
    if !info.funds.is_empty() {
        return Err(contract_err("no funds should be sent during kyc add"));
    }
    if kyc_attr.trim().is_empty() {
        return Err(contract_err("kyc attribute name is empty"));
    }

    // Load state and ensure sender is the administrator.
    let state = config(deps.storage).load()?;

    let mut member = match id {
        Some(addr) => {
            // Only admin can modify kyc_attr for different members
            if info.sender != state.admin {
                return Err(ContractError::Unauthorized {});
            }

            let address = deps.api.addr_validate(&addr)?;
            members_read(deps.storage).load(address.as_bytes())?
        }
        None => members_read(deps.storage).load(info.sender.as_bytes())?,
    };

    // Ensure kyc attribute is different
    if member.kyc_attr == Some(kyc_attr.clone()) {
        return Err(contract_err("kyc attribute is unchanged"));
    }

    // Add the kyc attribute and save
    member.kyc_attr = Some(kyc_attr.clone());
    members(deps.storage).save(member.id.as_bytes(), &member)?;

    // Add wasm event attributes
    Ok(Response::new()
        .add_attribute("action", "set_kyc_attribute")
        .add_attribute("kyc_attr", kyc_attr)
        .add_attribute("member_id", &member.id))
}

// A helper function for creating generic contract errors.
fn contract_err(s: &str) -> ContractError {
    ContractError::Std(StdError::generic_err(s))
}

// Return the member or first matched attribute of an address, otherwise return an error.
fn matched_attribute(
    deps: Deps<ProvenanceQuery>,
    addr: Addr,
    attrs: Vec<String>,
) -> Result<String, ContractError> {
    // Skip the check if no attributes are required.
    if attrs.is_empty() {
        return Err(contract_err("requires at least one kyc attribute"));
    }
    // Check if member before checking provided attributes.
    let member = members_read(deps.storage).may_load(addr.as_bytes())?;
    match member {
        Some(m) => {
            if m.kyc_attr.is_none() {
                return Err(contract_err("no member kyc_attr found"));
            }
            let kyc_attr = m.kyc_attr.unwrap();
            for name in attrs.iter() {
                if *name == kyc_attr {
                    return Ok(kyc_attr);
                }
            }
            Err(contract_err(&format!(
                "no kyc attributes found for {}",
                addr
            )))
        }
        None => ensure_attribute(deps, addr, attrs),
    }
}

// Return the first matched attribute, otherwise return an error.
fn ensure_attribute(
    deps: Deps<ProvenanceQuery>,
    addr: Addr,
    attrs: Vec<String>,
) -> Result<String, ContractError> {
    // Skip the check if no attributes are required.
    if attrs.is_empty() {
        return Err(contract_err("requires at least one kyc attribute"));
    }
    // Check for all provided attributes
    let querier = ProvenanceQuerier::new(&deps.querier);
    for name in attrs.iter() {
        let res = querier.get_attributes(addr.clone(), Some(name))?;
        if !res.attributes.is_empty() {
            return Ok(name.to_string());
        }
    }
    return Err(contract_err(&format!(
        "no kyc attributes found for {}",
        addr
    )));
}

/// Query contract state
#[entry_point]
pub fn query(
    deps: Deps<ProvenanceQuery>,
    _env: Env,
    msg: QueryMsg,
) -> Result<QueryResponse, ContractError> {
    match msg {
        QueryMsg::GetJoinProposals {} => try_get_join_proposals(deps),
        QueryMsg::GetMembers {} => try_get_members(deps),
        QueryMsg::GetJoinProposal { id } => try_get_join_proposal(deps, id),
        QueryMsg::GetMember { id } => try_get_member(deps, id),
    }
}

// Query all join proposals.
fn try_get_join_proposals(deps: Deps<ProvenanceQuery>) -> Result<QueryResponse, ContractError> {
    Ok(to_binary(&JoinProposals {
        proposals: get_join_proposals(deps)?,
    })?)
}

// Query all members.
fn try_get_members(deps: Deps<ProvenanceQuery>) -> Result<QueryResponse, ContractError> {
    Ok(to_binary(&Members {
        members: get_members(deps)?,
    })?)
}

// Query join proposal by ID.
fn try_get_join_proposal(
    deps: Deps<ProvenanceQuery>,
    id: String,
) -> Result<QueryResponse, ContractError> {
    let address = deps.api.addr_validate(&id)?;
    let key = address.as_bytes();
    let proposal = join_proposals_read(deps.storage).load(key)?;
    let bin = to_binary(&proposal)?;
    Ok(bin)
}

// Query member by ID.
fn try_get_member(deps: Deps<ProvenanceQuery>, id: String) -> Result<QueryResponse, ContractError> {
    let address = deps.api.addr_validate(&id)?;
    let key = address.as_bytes();
    let member = members_read(deps.storage).load(key)?;
    let bin = to_binary(&member)?;
    Ok(bin)
}

// Read all members from bucket storage.
fn get_members(deps: Deps<ProvenanceQuery>) -> Result<Vec<MemberV2>, ContractError> {
    members_read(deps.storage)
        .range(None, None, Order::Ascending)
        .map(|item| {
            let (_, member) = item?;
            Ok(member)
        })
        .collect()
}

// Read all join proposals from bucket storage.
fn get_join_proposals(deps: Deps<ProvenanceQuery>) -> Result<Vec<JoinProposalV2>, ContractError> {
    join_proposals_read(deps.storage)
        .range(None, None, Order::Ascending)
        .map(|item| {
            let (_, proposal) = item?;
            Ok(proposal)
        })
        .collect()
}

// Get all kyc attributes for members.
fn get_attributes(deps: Deps<ProvenanceQuery>) -> Result<Vec<String>, ContractError> {
    Ok(get_members(deps)?
        .into_iter()
        .filter_map(|item| item.kyc_attr)
        .collect())
}

// Get all kyc attributes for proposals and members.
fn get_all_attributes(deps: Deps<ProvenanceQuery>) -> Result<Vec<String>, ContractError> {
    let mut kyc_attrs: Vec<String> = get_join_proposals(deps)?
        .into_iter()
        .filter_map(|item| item.kyc_attr)
        .collect();
    kyc_attrs.append(get_attributes(deps)?.as_mut());

    Ok(kyc_attrs)
}

/// Called when migrating a contract instance to a new code ID.
#[entry_point]
pub fn migrate(
    mut deps: DepsMut<ProvenanceQuery>,
    _env: Env,
    msg: MigrateMsg,
) -> Result<Response, ContractError> {
    // migrate state
    migrate_state(deps.branch(), &msg)?;

    // migrate join proposals
    migrate_join_proposals(deps.branch(), &msg)?;

    // migrate members
    migrate_members(deps.branch(), &msg)?;

    // lastly, migrate version_info
    migrate_version_info(deps.branch())?;

    Ok(Response::default())
}

#[cfg(test)]
mod tests {
    // use super::*;
    // use cosmwasm_std::testing::{mock_env, mock_info};
    // use cosmwasm_std::{coin, from_binary};
    // use provwasm_mocks::{mock_dependencies, must_read_binary_file};
    // use provwasm_std::Marker;
    //
    // #[test]
    // fn valid_init() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     let res = instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(100),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Ensure messages were created.
    //     // TODO: validate marker messages (create, grant(2), finalize, activate)...
    //     assert_eq!(5, res.messages.len());
    //
    //     // Read state
    //     let config_state = config_read(&deps.storage).load().unwrap();
    //
    //     // Validate state values
    //     assert_eq!(config_state.denom, "dcc.coin");
    //     assert_eq!(config_state.quorum_pct, Decimal::percent(67));
    //     assert_eq!(config_state.vote_duration, Uint128::new(100));
    // }
    //
    // #[test]
    // fn join_test() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(10),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create a valid join proposal.
    //     let res = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Ensure messages were created.
    //     // TODO: validate marker messages (create, grant)...
    //     assert_eq!(2, res.messages.len());
    //
    //     let addr = Addr::unchecked("bank");
    //     let key: &[u8] = addr.as_bytes();
    //     let proposal = join_proposals_read(&deps.storage).load(key).unwrap();
    //
    //     assert_eq!(proposal.denom, "bank.coin");
    //     assert_eq!(proposal.max_supply, Uint128::new(1_000_000));
    //     assert_eq!(proposal.created, Uint128::new(12345));
    //     assert_eq!(proposal.expires, Uint128::new(12345 + 10));
    //     assert_eq!(proposal.yes, Uint128::zero());
    //     assert_eq!(proposal.no, Uint128::zero());
    //     assert!(proposal.voters.is_empty());
    // }
    //
    // #[test]
    // fn join_invalid_params() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(10),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Try to create join proposal w/ zero max supply
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::zero(),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "max_supply must be greater than zero")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Try to create join proposal w/ an empty denom string
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "invalid proposal denom")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Try to send funds w/ the join proposal
    //     let funds = coin(1000, "nhash");
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[funds]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "no funds should be sent during join")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    // }
    //
    // #[test]
    // fn join_dup_proposal() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(10),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create a valid join proposal.
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Try to create a duplicate join proposal.
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "duplicate proposal")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    // }
    //
    // #[test]
    // fn vote_yes_admin() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(10),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Vote yes as 'admin'
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap();
    //
    //     let addr = Addr::unchecked("bank");
    //     let key: &[u8] = addr.as_bytes();
    //     let proposal = join_proposals_read(&deps.storage).load(key).unwrap();
    //
    //     // Assert the admin vote weight was added to the 'yes' total.
    //     assert_eq!(proposal.yes, Uint128::zero());
    //     assert_eq!(proposal.no, Uint128::zero());
    //     assert_eq!(proposal.voters.len(), 0);
    //     assert_eq!(proposal.admin_vote, Some(VoteChoice::Yes));
    // }
    //
    // #[test]
    // fn vote_yes_member() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(1000),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal for bank1
    //     let mut env = mock_env();
    //     env.block.height += 1; // next block
    //     execute(
    //         deps.as_mut(),
    //         env,
    //         mock_info("bank1", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank1.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Vote yes as admin for bank1
    //     let mut env = mock_env();
    //     env.block.height += 2; // next block
    //     execute(
    //         deps.as_mut(),
    //         env,
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank1".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Accept membership as bank1
    //     let mut env = mock_env();
    //     env.block.height += 3; // next block
    //     execute(
    //         deps.as_mut(),
    //         env,
    //         mock_info("bank1", &[]),
    //         ExecuteMsg::Accept { mint_amount: None },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal for bank2
    //     let mut env = mock_env();
    //     env.block.height += 4; // next block
    //     execute(
    //         deps.as_mut(),
    //         env,
    //         mock_info("bank2", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(2_000_000),
    //             denom: "bank2.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Try to vote yes as bank1 for the bank2 proposal
    //     let mut env = mock_env();
    //     env.block.height += 5; // next block
    //     execute(
    //         deps.as_mut(),
    //         env,
    //         mock_info("bank1", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank2".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap();
    //
    //     let addr = Addr::unchecked("bank2");
    //     let key: &[u8] = addr.as_bytes();
    //     let proposal = join_proposals_read(&deps.storage).load(key).unwrap();
    //
    //     // Assert the admin vote weight was added to the 'yes' total.
    //     assert_eq!(proposal.yes, Uint128::new(10_000));
    //     assert_eq!(proposal.no, Uint128::zero());
    //     assert_eq!(proposal.voters, vec![Addr::unchecked("bank1")]);
    //     assert_eq!(proposal.admin_vote, None);
    // }
    //
    // #[test]
    // fn vote_no_admin() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(10),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Vote no as 'admin'
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank".into(),
    //             choice: VoteChoice::No,
    //         },
    //     )
    //     .unwrap();
    //
    //     let addr = Addr::unchecked("bank");
    //     let key: &[u8] = addr.as_bytes();
    //     let proposal = join_proposals_read(&deps.storage).load(key).unwrap();
    //
    //     // Assert the admin vote sets the admin vote choice.
    //     assert_eq!(proposal.yes, Uint128::zero());
    //     assert_eq!(proposal.no, Uint128::zero());
    //     assert_eq!(proposal.voters.len(), 0);
    //     assert_eq!(proposal.admin_vote, Some(VoteChoice::No));
    // }
    //
    // #[test]
    // fn vote_no_member() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(1000),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal for bank1
    //     let mut env = mock_env();
    //     env.block.height += 1; // next block
    //     execute(
    //         deps.as_mut(),
    //         env,
    //         mock_info("bank1", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank1.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Vote yes as admin for bank1
    //     let mut env = mock_env();
    //     env.block.height += 2; // next block
    //     execute(
    //         deps.as_mut(),
    //         env,
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank1".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Accept membership as bank1
    //     let mut env = mock_env();
    //     env.block.height += 3; // next block
    //     execute(
    //         deps.as_mut(),
    //         env,
    //         mock_info("bank1", &[]),
    //         ExecuteMsg::Accept { mint_amount: None },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal for bank2
    //     let mut env = mock_env();
    //     env.block.height += 4; // next block
    //     execute(
    //         deps.as_mut(),
    //         env,
    //         mock_info("bank2", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(2_000_000),
    //             denom: "bank2.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Try to vote yes as bank1 for the bank2 proposal
    //     let mut env = mock_env();
    //     env.block.height += 5; // next block
    //     execute(
    //         deps.as_mut(),
    //         env,
    //         mock_info("bank1", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank2".into(),
    //             choice: VoteChoice::No,
    //         },
    //     )
    //     .unwrap();
    //
    //     let addr = Addr::unchecked("bank2");
    //     let key: &[u8] = addr.as_bytes();
    //     let proposal = join_proposals_read(&deps.storage).load(key).unwrap();
    //
    //     // Assert the admin vote weight was added to the 'yes' total.
    //     assert_eq!(proposal.yes, Uint128::zero());
    //     assert_eq!(proposal.no, Uint128::new(10_000));
    //     assert_eq!(proposal.voters, vec![Addr::unchecked("bank1")]);
    //     assert_eq!(proposal.admin_vote, None);
    // }
    //
    // #[test]
    // fn vote_invalid_params() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(1),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Try to vote with an unauthorized account.
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("unauthorized", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Unauthorized {} => {}
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Try to vote on an invalid proposal ID.
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "Invalid input: human address too short")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Try to send funds w/ the vote
    //     let funds = coin(1000, "nhash");
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[funds]),
    //         ExecuteMsg::Vote {
    //             id: "bank".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "no funds should be sent during vote")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    // }
    //
    // #[test]
    // fn vote_unauthorized_after_join() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(1000),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal for bank1
    //     let mut env = mock_env();
    //     env.block.height += 1; // next block
    //     execute(
    //         deps.as_mut(),
    //         env,
    //         mock_info("bank1", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank1.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal for bank2
    //     let mut env = mock_env();
    //     env.block.height += 2; // next block
    //     execute(
    //         deps.as_mut(),
    //         env,
    //         mock_info("bank2", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(2_000_000),
    //             denom: "bank2.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Vote yes as admin for bank1
    //     let mut env = mock_env();
    //     env.block.height += 3; // next block
    //     execute(
    //         deps.as_mut(),
    //         env,
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank1".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Accept membership as bank1
    //     let mut env = mock_env();
    //     env.block.height += 4; // next block
    //     execute(
    //         deps.as_mut(),
    //         env,
    //         mock_info("bank1", &[]),
    //         ExecuteMsg::Accept { mint_amount: None },
    //     )
    //     .unwrap();
    //
    //     // Try to vote no as bank1 for the bank2 proposal
    //     let mut env = mock_env();
    //     env.block.height += 5; // next block
    //     let err = execute(
    //         deps.as_mut(),
    //         env,
    //         mock_info("bank1", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank2".into(),
    //             choice: VoteChoice::No,
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Unauthorized {} => {}
    //         _ => panic!("unexpected execute error"),
    //     }
    // }
    //
    // #[test]
    // fn vote_window_closed() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(10),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Move the block height beyond proposal expiration.
    //     let mut env = mock_env();
    //     env.block.height += 100;
    //
    //     // Try to vote.
    //     let err = execute(
    //         deps.as_mut(),
    //         env,
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "voting window has closed")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    // }
    //
    // #[test]
    // fn vote_twice_admin_error() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(1),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Vote yes
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Try to vote a second time.
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank".into(),
    //             choice: VoteChoice::No,
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "admin has already voted")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    // }
    //
    // #[test]
    // fn vote_twice_member_error() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(1000),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal for bank1
    //     let mut env = mock_env();
    //     env.block.height += 1; // next block
    //     execute(
    //         deps.as_mut(),
    //         env,
    //         mock_info("bank1", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank1.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Vote yes as admin for bank1
    //     let mut env = mock_env();
    //     env.block.height += 2; // next block
    //     execute(
    //         deps.as_mut(),
    //         env,
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank1".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Accept membership as bank1
    //     let mut env = mock_env();
    //     env.block.height += 3; // next block
    //     execute(
    //         deps.as_mut(),
    //         env,
    //         mock_info("bank1", &[]),
    //         ExecuteMsg::Accept { mint_amount: None },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal for bank2
    //     let mut env = mock_env();
    //     env.block.height += 4; // next block
    //     execute(
    //         deps.as_mut(),
    //         env,
    //         mock_info("bank2", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(2_000_000),
    //             denom: "bank2.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Try to vote yes as bank1 for the bank2 proposal
    //     let mut env = mock_env();
    //     env.block.height += 5; // next block
    //     execute(
    //         deps.as_mut(),
    //         env,
    //         mock_info("bank1", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank2".into(),
    //             choice: VoteChoice::No,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Try to vote a second time.
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank1", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank2".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "member has already voted")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    // }
    //
    // #[test]
    // fn accept_test() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(10),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(100000000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Vote yes as 'admin'
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Accept join proposal
    //     let res = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Accept { mint_amount: None },
    //     )
    //     .unwrap();
    //
    //     // Ensure messages were created.
    //     // TODO: validate marker messages (finalize, activate)...
    //     assert_eq!(2, res.messages.len());
    //
    //     let addr = Addr::unchecked("bank");
    //     let key: &[u8] = addr.as_bytes();
    //     let member = members_read(&deps.storage).load(key).unwrap();
    //
    //     assert_eq!(member.id, addr);
    //     assert_eq!(member.denom, "bank.coin");
    //     assert_eq!(member.supply, Uint128::zero());
    //     assert_eq!(member.max_supply, Uint128::new(100000000));
    //     assert_eq!(member.weight, Uint128::new(1000000));
    // }
    //
    // #[test]
    // fn accept_member_test() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(1000),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal for bank1
    //     let mut env = mock_env();
    //     env.block.height += 1; // next block
    //     execute(
    //         deps.as_mut(),
    //         env,
    //         mock_info("bank1", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank1.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Vote yes as admin for bank1
    //     let mut env = mock_env();
    //     env.block.height += 2; // next block
    //     execute(
    //         deps.as_mut(),
    //         env,
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank1".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Accept membership as bank1
    //     let mut env = mock_env();
    //     env.block.height += 3; // next block
    //     execute(
    //         deps.as_mut(),
    //         env,
    //         mock_info("bank1", &[]),
    //         ExecuteMsg::Accept { mint_amount: None },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal for bank2
    //     let mut env = mock_env();
    //     env.block.height += 4; // next block
    //     execute(
    //         deps.as_mut(),
    //         env,
    //         mock_info("bank2", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(2_000_000),
    //             denom: "bank2.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Try to vote yes as bank1 for the bank2 proposal
    //     let mut env = mock_env();
    //     env.block.height += 5; // next block
    //     execute(
    //         deps.as_mut(),
    //         env,
    //         mock_info("bank1", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank2".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Accept join proposal
    //     let res = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank2", &[]),
    //         ExecuteMsg::Accept { mint_amount: None },
    //     )
    //     .unwrap();
    //
    //     // Ensure messages were created.
    //     // TODO: validate marker messages (finalize, activate)...
    //     assert_eq!(2, res.messages.len());
    //
    //     let addr = Addr::unchecked("bank2");
    //     let key: &[u8] = addr.as_bytes();
    //     let member = members_read(&deps.storage).load(key).unwrap();
    //
    //     assert_eq!(member.id, addr);
    //     assert_eq!(member.denom, "bank2.coin");
    //     assert_eq!(member.supply, Uint128::zero());
    //     assert_eq!(member.max_supply, Uint128::new(2_000_000));
    //     assert_eq!(member.weight, Uint128::new(20_000));
    // }
    //
    // #[test]
    // fn accept_with_mint_test() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(10),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(100000000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Vote yes as 'admin'
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Accept join proposal; minting the max supply
    //     let res = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Accept {
    //             mint_amount: Some(Uint128::new(100000000)),
    //         },
    //     )
    //     .unwrap();
    //
    //     // Ensure messages were created.
    //     // TODO: validate marker messages (finalize, activate, mint, withdraw)...
    //     assert_eq!(4, res.messages.len());
    //
    //     let addr = Addr::unchecked("bank");
    //     let key: &[u8] = addr.as_bytes();
    //     let member = members_read(&deps.storage).load(key).unwrap();
    //
    //     assert_eq!(member.id, addr);
    //     assert_eq!(member.denom, "bank.coin");
    //     assert_eq!(member.supply, Uint128::new(100000000));
    //     assert_eq!(member.max_supply, Uint128::new(100000000));
    //     assert_eq!(member.weight, Uint128::new(1000000));
    // }
    //
    // #[test]
    // fn accept_param_errors() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(10),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Try to accept a join proposal that has no quorum (no yes votes).
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Accept { mint_amount: None },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "no membership quorum")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Try to accept a join proposal that doesn't exist.
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank1", &[]),
    //         ExecuteMsg::Accept { mint_amount: None },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::NotFound { .. }) => {}
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Try to send funds with the accept message.
    //     let funds = coin(1000, "nhash");
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[funds]),
    //         ExecuteMsg::Accept { mint_amount: None },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "no funds should be sent during accept")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    // }
    //
    // #[test]
    // fn cancel_test() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(10),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Vote no as 'admin'
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank".into(),
    //             choice: VoteChoice::No,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Cancel join proposal
    //     let res = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Cancel {},
    //     )
    //     .unwrap();
    //
    //     // Ensure messages were created.
    //     // TODO: validate marker messages (cancel, destroy)...
    //     assert_eq!(2, res.messages.len());
    // }
    //
    // #[test]
    // fn cancel_param_errors() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(10),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Try to cancel a join proposal that doesn't exist.
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank1", &[]),
    //         ExecuteMsg::Cancel {},
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::NotFound { .. }) => {}
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Try to send funds with the cancel message.
    //     let funds = coin(1000, "nhash");
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[funds]),
    //         ExecuteMsg::Cancel {},
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "no funds should be sent during cancel")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Vote yes as 'admin'
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Accept join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Accept { mint_amount: None },
    //     )
    //     .unwrap();
    //
    //     // Try to cancel the accepted join proposal
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Cancel {},
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "membership already accepted")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    // }
    //
    // #[test]
    // fn transfer_test() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(10),
    //             kyc_attrs: vec!["bank.kyc".into()],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Vote yes as 'admin'
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Accept join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Accept { mint_amount: None },
    //     )
    //     .unwrap();
    //
    //     // Assume the customer has a balance of dcc tokens + the required attribute.
    //     let dcc = coin(1000, "dcc.coin");
    //     deps.querier
    //         .base
    //         .update_balance(Addr::unchecked("customer"), vec![dcc]);
    //     deps.querier
    //         .with_attributes("customer", &[("bank.kyc", "ok", "string")]);
    //
    //     // Transfer dcc from the customer to a member bank.
    //     let res = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("customer", &[]),
    //         ExecuteMsg::Transfer {
    //             amount: Uint128::new(500),
    //             recipient: "bank".into(),
    //         },
    //     )
    //     .unwrap();
    //
    //     // Ensure message was created.
    //     // TODO: validate marker transfer message...
    //     assert_eq!(1, res.messages.len());
    // }
    //
    // #[test]
    // fn transfer_param_errors() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(10),
    //             kyc_attrs: vec!["bank.kyc".into()],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Vote yes as 'admin'
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Accept join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Accept { mint_amount: None },
    //     )
    //     .unwrap();
    //
    //     // Try to transfer zero dcc from the customer to a member bank.
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("customer", &[]),
    //         ExecuteMsg::Transfer {
    //             amount: Uint128::zero(),
    //             recipient: "bank".into(),
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "invalid transfer amount")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Try to send additional funds during transfer.
    //     let funds = coin(1000, "nhash");
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("customer", &[funds]),
    //         ExecuteMsg::Transfer {
    //             amount: Uint128::new(500),
    //             recipient: "bank".into(),
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "bank sends are not allowed in dcc transfer")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    // }
    //
    // #[test]
    // fn transfer_insufficient_funds() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(10),
    //             kyc_attrs: vec!["bank.kyc".into()],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Vote yes as 'admin'
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Accept join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Accept { mint_amount: None },
    //     )
    //     .unwrap();
    //
    //     // Assume the customer has the required attribute, but no dcc tokens.
    //     deps.querier
    //         .with_attributes("customer", &[("bank.kyc", "ok", "string")]);
    //
    //     // Try to transfer dcc from the customer to the member bank.
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("customer", &[]),
    //         ExecuteMsg::Transfer {
    //             amount: Uint128::new(500),
    //             recipient: "bank".into(),
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "insufficient dcc balance in transfer")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    // }
    //
    // #[test]
    // fn transfer_without_attributes() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(10),
    //             kyc_attrs: vec!["bank.kyc".into()],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Vote yes as 'admin'
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Accept join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Accept { mint_amount: None },
    //     )
    //     .unwrap();
    //
    //     // Assume the customer has a balance of dcc, but does NOT have the required attribute.
    //     let dcc = coin(1000, "dcc.coin");
    //     deps.querier
    //         .base
    //         .update_balance(Addr::unchecked("customer"), vec![dcc]);
    //
    //     // Try to transfer dcc from the customer to a member bank.
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("customer", &[]),
    //         ExecuteMsg::Transfer {
    //             amount: Uint128::new(500),
    //             recipient: "bank".into(),
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "no kyc attributes found for customer")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    // }
    //
    // #[test]
    // fn mint_test() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(10),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Vote yes as 'admin'
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Accept join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Accept { mint_amount: None },
    //     )
    //     .unwrap();
    //
    //     let addr = Addr::unchecked("bank");
    //     let key: &[u8] = addr.as_bytes();
    //     let member = members_read(&deps.storage).load(key).unwrap();
    //
    //     // Ensure supply is zero
    //     assert_eq!(member.supply, Uint128::zero());
    //
    //     // Mint reserve tokens and withdraw them.
    //     let res = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Mint {
    //             amount: Uint128::new(100),
    //             address: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Ensure messages were created.
    //     // TODO: validate marker messages (mint reserve, withdraw reserve)...
    //     assert_eq!(2, res.messages.len());
    //
    //     // Ensure supply was updated
    //     let member = members_read(&deps.storage).load(key).unwrap();
    //     assert_eq!(member.supply, Uint128::new(100));
    // }
    //
    // #[test]
    // fn mint_param_errors() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(10),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Vote yes as 'admin'
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Accept join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Accept { mint_amount: None },
    //     )
    //     .unwrap();
    //
    //     // Try to mint zero reserve tokens
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Mint {
    //             amount: Uint128::zero(),
    //             address: None,
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg }) => {
    //             assert_eq!(msg, "invalid mint amount")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Try to mint > max_supply reserve tokens
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Mint {
    //             amount: Uint128::new(1_000_001),
    //             address: None,
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg }) => {
    //             assert_eq!(msg, "max supply exceeded")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Try to send funds with the mint message
    //     let funds = coin(1000, "nhash");
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[funds]),
    //         ExecuteMsg::Mint {
    //             amount: Uint128::new(1000),
    //             address: None,
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg }) => {
    //             assert_eq!(msg, "no funds should be sent during mint")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Call mint as a non-member
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("non.member", &[]),
    //         ExecuteMsg::Mint {
    //             amount: Uint128::new(1000),
    //             address: None,
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::NotFound { kind }) => {
    //             assert_eq!(kind, "dcc::state::Member");
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    // }
    //
    // #[test]
    // fn mint_withdraw_dcc_test() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(10),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Vote yes as 'admin'
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Accept join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Accept { mint_amount: None },
    //     )
    //     .unwrap();
    //
    //     let addr = Addr::unchecked("bank");
    //     let key: &[u8] = addr.as_bytes();
    //     let member = members_read(&deps.storage).load(key).unwrap();
    //
    //     // Ensure supply is zero
    //     assert_eq!(member.supply, Uint128::zero());
    //
    //     // Mint reserve tokens and withdraw dcc to a customer address.
    //     let res = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Mint {
    //             amount: Uint128::new(100),
    //             address: Some("customer".into()),
    //         },
    //     )
    //     .unwrap();
    //
    //     // Ensure messages were created.
    //     // TODO: validate marker messages (mint reserve, mint dcc, withdraw dcc)...
    //     assert_eq!(3, res.messages.len());
    //
    //     // Ensure supply was updated
    //     let member = members_read(&deps.storage).load(key).unwrap();
    //     assert_eq!(member.supply, Uint128::new(100));
    // }
    //
    // #[test]
    // fn burn_test() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Burn needs to query the marker address, so we mock one here
    //     let bin = must_read_binary_file("testdata/bank.json");
    //     let marker: Marker = from_binary(&bin).unwrap();
    //     deps.querier.with_markers(vec![marker]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(10),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Vote yes as 'admin'
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Accept join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Accept { mint_amount: None },
    //     )
    //     .unwrap();
    //
    //     // Mint reserve tokens.
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Mint {
    //             amount: Uint128::new(100),
    //             address: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Simulate balance update due to mint.
    //     let addr = Addr::unchecked("bank");
    //     let minted = coin(100, "bank.coin");
    //     deps.querier.base.update_balance(addr.clone(), vec![minted]);
    //
    //     // Burn reserve tokens.
    //     let res = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Burn {
    //             amount: Uint128::new(25),
    //         },
    //     )
    //     .unwrap();
    //
    //     // Ensure messages were created.
    //     // TODO: validate marker messages (transfer, burn)...
    //     assert_eq!(2, res.messages.len());
    //
    //     // Ensure supply was reduced as expected.
    //     let key: &[u8] = addr.as_bytes();
    //     let member = members_read(&deps.storage).load(key).unwrap();
    //     assert_eq!(member.supply, Uint128::new(75));
    // }
    //
    // #[test]
    // fn burn_param_errors() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(10),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Vote yes as 'admin'
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Accept join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Accept { mint_amount: None },
    //     )
    //     .unwrap();
    //
    //     // Try to send additional funds w/ the burn message.
    //     let funds = coin(1000, "nhash");
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[funds]),
    //         ExecuteMsg::Burn {
    //             amount: Uint128::new(500),
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "no funds should be sent during burn")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Try to send zero amount.
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Burn {
    //             amount: Uint128::zero(),
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "invalid burn amount")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Try to burn as a non-member.
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("non.member", &[]),
    //         ExecuteMsg::Burn {
    //             amount: Uint128::new(500),
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::NotFound { kind }) => {
    //             assert_eq!(kind, "dcc::state::Member");
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Try to burn more than a member holds in their account.
    //     let dcc = coin(100, "dcc.coin");
    //     deps.querier
    //         .base
    //         .update_balance(Addr::unchecked("bank"), vec![dcc]);
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Burn {
    //             amount: Uint128::new(500),
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "insufficient funds in burn")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    // }
    //
    // #[test]
    // fn swap_test() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Swap needs to query the marker address, so we mock one here
    //     let bin = must_read_binary_file("testdata/bank.json");
    //     let marker: Marker = from_binary(&bin).unwrap();
    //     deps.querier.with_markers(vec![marker]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(10),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(100_000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Vote yes as 'admin'
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Accept join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Accept { mint_amount: None },
    //     )
    //     .unwrap();
    //
    //     // Simulate balance update due to mint.
    //     let addr = Addr::unchecked("bank");
    //     let minted = coin(10_000, "bank.coin");
    //     deps.querier.base.update_balance(addr.clone(), vec![minted]);
    //
    //     // Swap reserve tokens for dcc tokens
    //     let res = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Swap {
    //             amount: Uint128::new(5000),
    //             denom: "bank.coin".into(),
    //             address: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Ensure messages were created.
    //     // TODO: validate marker messages (transfer, mint dcc, withdraw dcc)...
    //     assert_eq!(3, res.messages.len());
    // }
    //
    // #[test]
    // fn swap_param_errors() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Swap needs to query the marker address, so we mock one here
    //     let bin = must_read_binary_file("testdata/bank.json");
    //     let marker: Marker = from_binary(&bin).unwrap();
    //     deps.querier.with_markers(vec![marker]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(10),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(100_000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Vote yes as 'admin'
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Accept join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Accept { mint_amount: None },
    //     )
    //     .unwrap();
    //
    //     // Try to send funds w/ the swap message.
    //     let funds = coin(1000, "nhash");
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[funds]),
    //         ExecuteMsg::Swap {
    //             amount: Uint128::new(5000),
    //             denom: "bank.coin".into(),
    //             address: None,
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "no funds should be sent during reserve swap")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Try so swap with amount of zero.
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Swap {
    //             amount: Uint128::zero(),
    //             denom: "bank.coin".into(),
    //             address: None,
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "invalid reserve amount")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Try to swap where message sender is not a member.
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("non.member", &[]),
    //         ExecuteMsg::Swap {
    //             amount: Uint128::new(5000),
    //             denom: "bank.coin".into(),
    //             address: None,
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Unauthorized {} => {}
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Try to swap a denom that is not supported by the consortium.
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Swap {
    //             amount: Uint128::new(5000),
    //             denom: "unsupported.coin".into(),
    //             address: None,
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "invalid reserve denom")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Simulate balance update due to mint.
    //     let addr = Addr::unchecked("bank");
    //     let minted = coin(1000, "bank.coin");
    //     deps.querier.base.update_balance(addr.clone(), vec![minted]);
    //
    //     // Try to swap when a member holds less than the indicated amount.
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Swap {
    //             amount: Uint128::new(5000),
    //             denom: "bank.coin".into(),
    //             address: None,
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "insufficient reserve amount in swap")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    // }
    //
    // #[test]
    // fn redeem_test() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Redeem needs to query the dcc marker address, so we mock it here
    //     let bin = must_read_binary_file("testdata/dcc.json");
    //     let dcc_marker: Marker = from_binary(&bin).unwrap();
    //     let bin = must_read_binary_file("testdata/bank.json");
    //     let bank_marker: Marker = from_binary(&bin).unwrap();
    //     deps.querier.with_markers(vec![dcc_marker, bank_marker]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(10),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(100_000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Vote yes as 'admin'
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Accept join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Accept { mint_amount: None },
    //     )
    //     .unwrap();
    //
    //     // Simulate a mint(5,000) first, so bank has dcc
    //     let addr = Addr::unchecked("bank");
    //     let dcc = coin(5000, "dcc.coin");
    //     deps.querier.base.update_balance(addr.clone(), vec![dcc]);
    //
    //     // Redeem dcc for reserve tokens
    //     let res = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Redeem {
    //             amount: Uint128::new(2500),
    //             reserve_denom: Some("bank.coin".into()),
    //         },
    //     )
    //     .unwrap();
    //
    //     // Ensure messages were created.
    //     // TODO: validate marker messages (transfer dcc, burn dcc, withdraw reserve)...
    //     assert_eq!(3, res.messages.len());
    // }
    //
    // #[test]
    // fn redeem_param_errors() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Redeem needs to query the dcc marker address, so we mock it here
    //     let bin = must_read_binary_file("testdata/dcc.json");
    //     let marker: Marker = from_binary(&bin).unwrap();
    //     deps.querier.with_markers(vec![marker]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(10),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(100_000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Vote yes as 'admin'
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Accept join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Accept { mint_amount: None },
    //     )
    //     .unwrap();
    //
    //     // Try to send funds w/ redeem message.
    //     let funds = coin(1000, "nhash");
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[funds]),
    //         ExecuteMsg::Redeem {
    //             amount: Uint128::new(2500),
    //             reserve_denom: Some("bank.coin".into()),
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "no funds should be sent during dcc redemption")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Try to redeem with an amount of zero.
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Redeem {
    //             amount: Uint128::zero(),
    //             reserve_denom: Some("bank.coin".into()),
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "invalid redeem amount")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Try to redeem from a non-member account
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("non.member", &[]),
    //         ExecuteMsg::Redeem {
    //             amount: Uint128::new(2500),
    //             reserve_denom: Some("bank.coin".into()),
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::NotFound { kind }) => {
    //             assert_eq!(kind, "dcc::state::Member")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Simulate balance update due to swap.
    //     let addr = Addr::unchecked("bank");
    //     let swapped = coin(1000, "dcc.coin");
    //     deps.querier
    //         .base
    //         .update_balance(addr.clone(), vec![swapped]);
    //
    //     // Try to redeem more dcc tokens than the member holds.
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Redeem {
    //             amount: Uint128::new(2500),
    //             reserve_denom: Some("bank.coin".into()),
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "insufficient dcc token balance")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Try to redeem for a denom that is not supported by the consortium.
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Redeem {
    //             amount: Uint128::new(500),
    //             reserve_denom: Some("unsupported.coin".into()),
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "unsupported reserve denom")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    // }
    //
    // #[test]
    // fn redeem_and_burn_test() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Redeem needs to query the dcc marker address, so we mock it here
    //     let bin = must_read_binary_file("testdata/dcc.json");
    //     let dcc_marker: Marker = from_binary(&bin).unwrap();
    //     let bin = must_read_binary_file("testdata/bank.json");
    //     let bank_marker: Marker = from_binary(&bin).unwrap();
    //     deps.querier.with_markers(vec![dcc_marker, bank_marker]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(10),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Vote yes as 'admin'
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Accept join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Accept { mint_amount: None },
    //     )
    //     .unwrap();
    //
    //     // Mint reserve tokens.
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Mint {
    //             amount: Uint128::new(100),
    //             address: Some("bank".into()),
    //         },
    //     )
    //     .unwrap();
    //
    //     let addr = Addr::unchecked("bank");
    //     let key: &[u8] = addr.as_bytes();
    //     let member = members_read(&deps.storage).load(key).unwrap();
    //     assert_eq!(member.supply, Uint128::new(100));
    //
    //     // Simulate a mint(100) first, so bank has dcc
    //     let dcc = coin(100, "dcc.coin");
    //     deps.querier.base.update_balance(addr.clone(), vec![dcc]);
    //
    //     // Redeem and burn dcc and reserve tokens
    //     let res = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::RedeemAndBurn {
    //             amount: Uint128::new(25),
    //         },
    //     )
    //     .unwrap();
    //
    //     // Ensure messages were created.
    //     // TODO: validate marker messages (transfer dcc, burn dcc, burn reserve)...
    //     assert_eq!(3, res.messages.len());
    //
    //     // Ensure supply was reduced as expected.
    //     let member = members_read(&deps.storage).load(key).unwrap();
    //     assert_eq!(member.supply, Uint128::new(75));
    // }
    //
    // #[test]
    // fn redeem_and_burn_param_errors() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Redeem needs to query the dcc marker address, so we mock it here
    //     let bin = must_read_binary_file("testdata/dcc.json");
    //     let dcc_marker: Marker = from_binary(&bin).unwrap();
    //     let bin = must_read_binary_file("testdata/bank.json");
    //     let bank_marker: Marker = from_binary(&bin).unwrap();
    //     deps.querier.with_markers(vec![dcc_marker, bank_marker]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(10),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Create join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Join {
    //             max_supply: Uint128::new(1_000_000),
    //             denom: "bank.coin".into(),
    //             name: None,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Vote yes as 'admin'
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::Vote {
    //             id: "bank".into(),
    //             choice: VoteChoice::Yes,
    //         },
    //     )
    //     .unwrap();
    //
    //     // Accept join proposal
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::Accept { mint_amount: None },
    //     )
    //     .unwrap();
    //
    //     // Try to send funds w/ redeem and burn message.
    //     let funds = coin(1000, "nhash");
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[funds]),
    //         ExecuteMsg::RedeemAndBurn {
    //             amount: Uint128::new(2500),
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "no funds should be sent during dcc redemption")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Try to redeem and burn with an amount of zero.
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::RedeemAndBurn {
    //             amount: Uint128::zero(),
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "invalid redeem and burn amount")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Try to redeem and burn from a non-member account
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("non.member", &[]),
    //         ExecuteMsg::RedeemAndBurn {
    //             amount: Uint128::new(2500),
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::NotFound { kind }) => {
    //             assert_eq!(kind, "dcc::state::Member")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Simulate balance update due to swap.
    //     let addr = Addr::unchecked("bank");
    //     let swapped = coin(1000, "dcc.coin");
    //     deps.querier
    //         .base
    //         .update_balance(addr.clone(), vec![swapped]);
    //
    //     // Try to redeem more dcc tokens than the member holds.
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::RedeemAndBurn {
    //             amount: Uint128::new(2500),
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "insufficient dcc token balance")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     let swapped = coin(10_000, "dcc.coin");
    //     deps.querier
    //         .base
    //         .update_balance(addr.clone(), vec![swapped]);
    //
    //     // Try to redeem more dcc tokens than backed by bank token.
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("bank", &[]),
    //         ExecuteMsg::RedeemAndBurn {
    //             amount: Uint128::new(6000),
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "insufficient reserve token balance")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    // }
    //
    // #[test]
    // fn add_kyc_test() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(100),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Ensure we don't have any kyc attributes.
    //     let config_state = config_read(&deps.storage).load().unwrap();
    //     assert!(config_state.kyc_attrs.is_empty());
    //
    //     // Add a kyc attribute
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::AddKyc {
    //             name: "bank.kyc".into(),
    //         },
    //     )
    //     .unwrap();
    //
    //     // Ensure we now have have one kyc attribute.
    //     let config_state = config_read(&deps.storage).load().unwrap();
    //     assert_eq!(config_state.kyc_attrs.len(), 1);
    // }
    //
    // #[test]
    // fn add_kyc_param_errors() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(100),
    //             kyc_attrs: vec![],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Try to send funds w/ the kyc message.
    //     let funds = coin(1000, "nhash");
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[funds]),
    //         ExecuteMsg::AddKyc {
    //             name: "bank.kyc".into(),
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "no funds should be sent during kyc add")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Try to add an empty name.
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::AddKyc { name: "".into() },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "kyc attribute name is empty")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Try to add a kyc attribute as a non-admin
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("non.admin", &[]),
    //         ExecuteMsg::AddKyc {
    //             name: "bank.kyc".into(),
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Unauthorized {} => {}
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Add an attribute.
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::AddKyc {
    //             name: "bank.kyc".into(),
    //         },
    //     )
    //     .unwrap();
    //
    //     // Try to add a the kyc attr a second time.
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::AddKyc {
    //             name: "bank.kyc".into(),
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "kyc attribute already exists")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    // }
    //
    // #[test]
    // fn remove_kyc_test() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(100),
    //             kyc_attrs: vec!["bank.kyc".into()],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Ensure we have one kyc attribute.
    //     let config_state = config_read(&deps.storage).load().unwrap();
    //     assert_eq!(config_state.kyc_attrs.len(), 1);
    //
    //     // Remove the kyc attribute
    //     execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::RemoveKyc {
    //             name: "bank.kyc".into(),
    //         },
    //     )
    //     .unwrap();
    //
    //     // Ensure we now have have zero kyc attributes.
    //     let config_state = config_read(&deps.storage).load().unwrap();
    //     assert!(config_state.kyc_attrs.is_empty());
    // }
    //
    // #[test]
    // fn remove_kyc_param_errors() {
    //     // Create mock deps.
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Init
    //     instantiate(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         InitMsg {
    //             denom: "dcc.coin".into(),
    //             quorum_pct: Decimal::percent(67),
    //             vote_duration: Uint128::new(100),
    //             kyc_attrs: vec!["bank.kyc".into()],
    //         },
    //     )
    //     .unwrap();
    //
    //     // Try to send additional funds w/ the remove kyc message.
    //     let funds = coin(1000, "nhash");
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[funds]),
    //         ExecuteMsg::RemoveKyc {
    //             name: "bank.kyc".into(),
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "no funds should be sent during kyc remove")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Try to remove an empty attribute
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::RemoveKyc { name: "".into() },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "kyc attribute name is empty")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Try to remove a kyc attribute as a non-administrator.
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("non.admin", &[]),
    //         ExecuteMsg::RemoveKyc {
    //             name: "bank.kyc".into(),
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Unauthorized {} => {}
    //         _ => panic!("unexpected execute error"),
    //     }
    //
    //     // Try to remove a kyc attribute that doesn't exist.
    //     let err = execute(
    //         deps.as_mut(),
    //         mock_env(),
    //         mock_info("admin", &[]),
    //         ExecuteMsg::RemoveKyc {
    //             name: "bank.kyc.dne".into(),
    //         },
    //     )
    //     .unwrap_err();
    //
    //     // Ensure the expected error was returned.
    //     match err {
    //         ContractError::Std(StdError::GenericErr { msg, .. }) => {
    //             assert_eq!(msg, "kyc attribute does not exist")
    //         }
    //         _ => panic!("unexpected execute error"),
    //     }
    // }
    //
    // #[test]
    // fn migrate_test() {
    //     // Create mock deps
    //     let mut deps = mock_dependencies(&[]);
    //
    //     // Call migrate
    //     let res = migrate(deps.as_mut(), mock_env(), MigrateMsg {}).unwrap(); // Panics on error
    //
    //     // Should just get the default response for now
    //     assert_eq!(res, Response::default());
    // }
}
