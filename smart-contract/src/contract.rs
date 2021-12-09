use cosmwasm_std::{
    to_binary, Addr, Decimal, Deps, DepsMut, Env, MessageInfo, Order, QueryResponse, Response,
    StdError, Uint128,
};
use provwasm_std::{
    activate_marker, burn_marker_supply, cancel_marker, create_marker, destroy_marker,
    finalize_marker, grant_marker_access, mint_marker_supply, transfer_marker_coins,
    withdraw_coins, MarkerAccess, MarkerType, ProvenanceMsg, ProvenanceQuerier,
};

use crate::error::ContractError;
use crate::msg::{
    Balance, Balances, ExecuteMsg, InitMsg, JoinProposals, Members, MigrateMsg, QueryMsg,
    VoteChoice,
};
use crate::state::{
    config, config_read, join_proposals, join_proposals_read, members, members_read, JoinProposal,
    Member, State,
};

// Contract constants
pub static MIN_DENOM_LEN: usize = 8;

/// Create the initial configuration state and propose the dcc marker.
pub fn instantiate(
    deps: DepsMut,
    env: Env,
    info: MessageInfo,
    msg: InitMsg,
) -> Result<Response<ProvenanceMsg>, ContractError> {
    // Validate params
    if !info.funds.is_empty() {
        return Err(contract_err("no funds should be sent during instantiate"));
    }
    if msg.quorum_pct.is_zero() || msg.quorum_pct > Decimal::percent(100) {
        return Err(contract_err("invalid quorum percent"));
    }
    if msg.vote_duration.is_zero() {
        return Err(contract_err("invalid vote duration"));
    }

    // Remove any kyc attribute dups
    let mut kyc_attrs = msg.kyc_attrs;
    kyc_attrs.sort();
    kyc_attrs.dedup();

    // Create and store config state.
    let state = State {
        admin: info.sender.clone(),
        quorum_pct: msg.quorum_pct,
        dcc_denom: msg.dcc_denom.clone(),
        vote_duration: msg.vote_duration,
        kyc_attrs,
        admin_weight: msg.admin_weight.unwrap_or_else(|| Uint128::new(1)),
    };
    config(deps.storage).save(&state)?;

    // Create the dcc marker and grant permissions if it doesn't exist.
    let mut res = Response::new();
    if !marker_exists(deps.as_ref(), &msg.dcc_denom) {
        // If we need to create the marker, validate denom length.
        if msg.dcc_denom.len() < MIN_DENOM_LEN {
            return Err(contract_err("invalid denom length"));
        }
        res = res
            .add_message(create_marker(
                0,
                msg.dcc_denom.clone(),
                MarkerType::Restricted,
            )?)
            .add_message(grant_marker_access(
                &msg.dcc_denom,
                env.contract.address,
                MarkerAccess::all(),
            )?)
            .add_message(grant_marker_access(
                &msg.dcc_denom,
                info.sender,
                vec![MarkerAccess::Admin], // The contract admin is also a dcc marker admin
            )?)
            .add_message(finalize_marker(&msg.dcc_denom)?)
            .add_message(activate_marker(&msg.dcc_denom)?);
    }
    Ok(res)
}

// Determine whether the marker with the given denom exists.
fn marker_exists(deps: Deps, denom: &str) -> bool {
    let querier = ProvenanceQuerier::new(&deps.querier);
    querier.get_marker_by_denom(denom).is_ok()
}

/// Execute the contract
pub fn execute(
    deps: DepsMut,
    env: Env,
    info: MessageInfo,
    msg: ExecuteMsg,
) -> Result<Response<ProvenanceMsg>, ContractError> {
    match msg {
        ExecuteMsg::Join {
            denom,
            max_supply,
            name,
        } => try_join(deps, env, info, denom, max_supply, name),
        ExecuteMsg::Vote { id, choice } => try_vote(deps, env, info, id, choice),
        ExecuteMsg::Accept { mint_amount } => try_accept(deps, env, info, mint_amount),
        ExecuteMsg::Cancel {} => try_cancel(deps, info),
        ExecuteMsg::Redeem {
            amount,
            reserve_denom,
        } => try_redeem(deps, info, amount, reserve_denom),
        ExecuteMsg::RedeemAndBurn { amount } => try_redeem_and_burn(deps, info, amount),
        ExecuteMsg::Swap {
            amount,
            denom,
            address,
        } => try_swap(deps, info, amount, denom, address),
        ExecuteMsg::Transfer { amount, recipient } => try_transfer(deps, info, amount, recipient),
        ExecuteMsg::Mint { amount, address } => try_mint(deps, info, amount, address),
        ExecuteMsg::Burn { amount } => try_burn(deps, info, amount),
        ExecuteMsg::AddKyc { name } => try_add_kyc(deps, info, name),
        ExecuteMsg::RemoveKyc { name } => try_remove_kyc(deps, info, name),
    }
}

// Add a proposal to join the consortium that must be voted on by existing members.
fn try_join(
    deps: DepsMut,
    env: Env,
    info: MessageInfo,
    denom: String,
    max_supply: Uint128,
    name: Option<String>,
) -> Result<Response<ProvenanceMsg>, ContractError> {
    // Validate params.
    if !info.funds.is_empty() {
        return Err(contract_err("no funds should be sent during join"));
    }
    if denom.len() < MIN_DENOM_LEN {
        return Err(contract_err("invalid proposal denom"));
    }
    if max_supply.is_zero() {
        return Err(contract_err("max_supply must be greater than zero"));
    }

    // Read state
    let state = config_read(deps.storage).load()?;

    // Check for existing join request
    let key = info.sender.as_bytes();
    let mut proposals = join_proposals(deps.storage);
    if proposals.may_load(key)?.is_some() {
        return Err(contract_err("duplicate proposal"));
    }

    // Persist a join proposal.
    proposals.save(
        key,
        &JoinProposal {
            id: info.sender.clone(),
            max_supply,
            denom: denom.clone(),
            created: Uint128::from(env.block.height),
            expires: Uint128::from(env.block.height) + state.vote_duration,
            no: Uint128::zero(),
            yes: Uint128::zero(),
            voters: vec![],
            name,
        },
    )?;

    // Propose the member restricted marker and grant this contract exclusive access.
    let res = Response::new()
        .add_message(create_marker(0, &denom, MarkerType::Restricted)?)
        .add_message(grant_marker_access(
            &denom,
            env.contract.address,
            MarkerAccess::all(),
        )?)
        .add_attribute("action", "join")
        .add_attribute("join_proposal_id", info.sender.to_string());
    Ok(res)
}

// Vote 'yes' or 'no' on a proposal to join the consortium.
fn try_vote(
    deps: DepsMut,
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

    // Read member config
    let member = members_read(deps.storage).may_load(info.sender.as_bytes())?;

    // Ensure message sender is a consortium member or admin.
    if member.is_none() && info.sender != state.admin {
        return Err(ContractError::Unauthorized {});
    }

    // Lookup join proposal for address.
    let mut proposals = join_proposals(deps.storage);
    let mut proposal: JoinProposal = proposals.load(key)?;

    // Ensure voting window is open.
    if Uint128::from(env.block.height) >= proposal.expires {
        return Err(contract_err("voting window has closed"));
    }

    // Ensure message sender has not already voted.
    if proposal.voters.contains(&info.sender) {
        return Err(contract_err("member has already voted"));
    }

    // Esure member joined before the proposal was created and determine weight.
    let weight = if let Some(m) = member {
        if m.joined >= proposal.created {
            return Err(ContractError::Unauthorized {});
        }
        m.weight
    } else {
        state.admin_weight
    };

    // Add weight to 'yes' or 'no' total in join request.
    match choice {
        VoteChoice::Yes => {
            proposal.yes += weight;
        }
        VoteChoice::No => {
            proposal.no += weight;
        }
    };

    // Add message sender to join request 'voted' vector.
    proposal.voters.push(info.sender);

    // Save join request state.
    proposals.save(key, &proposal)?;

    // Add wasm event attributes
    let res = Response::new()
        .add_attribute("action", "vote")
        .add_attribute("join_proposal_id", id);
    Ok(res)
}

// Proposers must explicitly accept membership once voted into the consortium. Note: a proposer
// does not have to wait for the voting window to close before accepting - they only need a quroum
// of 'yes' votes.
fn try_accept(
    deps: DepsMut,
    env: Env,
    info: MessageInfo,
    mint_amount: Option<Uint128>,
) -> Result<Response<ProvenanceMsg>, ContractError> {
    // Validate params.
    if !info.funds.is_empty() {
        return Err(contract_err("no funds should be sent during accept"));
    }

    // Read state
    let state = config_read(deps.storage).load()?;

    // Ensure proposal exists
    let key = info.sender.as_bytes();
    let proposal = join_proposals_read(deps.storage).load(key)?;

    // Ensure quorum percentage has been reached for 'yes' votes.
    let mut total_weight: u128 = get_members(deps.as_ref())?
        .iter()
        .filter(|m| m.joined < proposal.created) // members from before the proposal was created.
        .map(|m| m.weight.u128())
        .sum();
    total_weight += state.admin_weight.u128();

    let pct = Decimal::from_ratio(proposal.yes, total_weight);
    deps.api.debug(&format!(
        "pct: {} | quorum {}",
        pct.to_string(),
        state.quorum_pct.to_string()
    ));

    if pct < state.quorum_pct {
        return Err(contract_err("no membership quorum"));
    }

    // Block membership if duplicate votes are detected in the join proposal.
    let mut votes = proposal.voters.clone();
    votes.sort();
    votes.dedup();
    if votes.len() < proposal.voters.len() {
        return Err(contract_err("membership blocked: duplicate vote(s) found"));
    }

    // Calculate member vote weight.
    let weight = calculate_weight(proposal.max_supply.u128());

    // Determine whether we're minting any reserve tokens
    let mut supply = Uint128::zero();
    if let Some(ma) = mint_amount {
        if ma > proposal.max_supply {
            return Err(contract_err("cannot mint more than max_supply"));
        }
        supply = ma;
    }

    // Persist member.
    members(deps.storage).save(
        key,
        &Member {
            id: info.sender.clone(),
            denom: proposal.denom.clone(),
            joined: Uint128::from(env.block.height),
            supply,
            max_supply: proposal.max_supply,
            weight: Uint128::new(weight),
            name: proposal.name.unwrap_or_else(|| info.sender.to_string()),
        },
    )?;

    // Finalize and activate the reserve marker.
    let mut res = Response::new()
        .add_message(finalize_marker(&proposal.denom)?)
        .add_message(activate_marker(&proposal.denom)?);

    // If non-zero, mint the reserve token supply and withdraw to the member account.
    if !supply.is_zero() {
        res = res
            .add_message(mint_marker_supply(supply.u128(), &proposal.denom)?)
            .add_message(withdraw_coins(
                &proposal.denom,
                supply.u128(),
                &proposal.denom,
                info.sender,
            )?);
    }

    // Add wasm event attributes
    Ok(res
        .add_attribute("action", "accept")
        .add_attribute("join_proposal_id", &proposal.id))
}

// Proposers can choose to cancel as long as they haven't already accepted.
fn try_cancel(deps: DepsMut, info: MessageInfo) -> Result<Response<ProvenanceMsg>, ContractError> {
    // Validate params.
    if !info.funds.is_empty() {
        return Err(contract_err("no funds should be sent during cancel"));
    }

    // Ensure message sender has not accepted (ie is not already a member).
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
        .add_message(cancel_marker(&proposal.denom)?)
        .add_message(destroy_marker(&proposal.denom)?)
        .add_attribute("action", "cancel")
        .add_attribute("join_proposal_id", &proposal.id);
    Ok(res)
}

// Redeem dcc tokens for member reserve tokens.
fn try_redeem(
    deps: DepsMut,
    info: MessageInfo,
    amount: Uint128,
    reserve_denom: Option<String>,
) -> Result<Response<ProvenanceMsg>, ContractError> {
    // Ensure no funds were sent.
    if !info.funds.is_empty() {
        return Err(contract_err("no funds should be sent during dcc redemption"));
    }

    // Validate params
    if amount.is_zero() {
        return Err(contract_err("invalid redeem amount"));
    }

    // Read state
    let state = config_read(deps.storage).load()?;

    // Ensure message sender is a member.
    let member = members_read(deps.storage).load(info.sender.as_bytes())?;

    // Ensure reserve_denom is always the bank reserve token.
    // Note: originally we supported a specified reserve denom to support other bank reserve tokens.
    let reserve_denom = reserve_denom.unwrap_or_else(|| member.denom.clone());
    if reserve_denom != member.denom {
        return Err(contract_err("unsupported reserve denom"));
    }

    // Ensure member holds at least the requested amount of dcc tokens.
    let balance = deps.querier.query_balance(&member.id, &state.dcc_denom)?;
    if balance.amount < amount {
        return Err(contract_err("insufficient dcc token balance"));
    }

    // Ensure marker holds at least the request amount of reserve token.
    let querier = ProvenanceQuerier::new(&deps.querier);
    let member_marker = querier.get_marker_by_denom(&reserve_denom)?;
    let reserve_balance = member_marker
        .coins
        .into_iter()
        .find(|c| c.denom == reserve_denom)
        .map(|c| c.amount)
        .unwrap_or_else(Uint128::zero);
    if reserve_balance < amount {
        return Err(contract_err("insufficient reserve token balance"));
    }

    // Get dcc marker
    let dcc_marker = querier.get_marker_by_denom(&state.dcc_denom)?;

    // Escrow dcc in the marker account for burn.
    let res = Response::new()
        .add_message(transfer_marker_coins(
            amount.u128(),
            &state.dcc_denom,
            dcc_marker.address,
            info.sender.clone(),
        )?)
        // Burn the dcc tokens.
        .add_message(burn_marker_supply(amount.u128(), &state.dcc_denom)?)
        // Withdraw reserve token from marker to member.
        .add_message(withdraw_coins(
            &reserve_denom,
            amount.u128(),
            &reserve_denom,
            info.sender.clone(),
        )?)
        .add_attribute("action", "redeem")
        .add_attribute("member_id", info.sender)
        .add_attribute("denom", &state.dcc_denom)
        .add_attribute("amount", amount)
        .add_attribute("reserve_denom", &reserve_denom);

    Ok(res)
}

fn try_redeem_and_burn(
    deps: DepsMut,
    info: MessageInfo,
    amount: Uint128,
) -> Result<Response<ProvenanceMsg>, ContractError> {
    // Ensure no funds were sent.
    if !info.funds.is_empty() {
        return Err(contract_err("no funds should be sent during dcc redemption"));
    }

    // Validate params
    if amount.is_zero() {
        return Err(contract_err("invalid redeem and burn amount"));
    }

    // Read state
    let state = config_read(deps.storage).load()?;

    // Ensure message sender is a member.
    let mut member = members_read(deps.storage).load(info.sender.as_bytes())?;

    // Ensure member holds at least the requested amount of dcc tokens.
    let balance = deps.querier.query_balance(&member.id, &state.dcc_denom)?;
    if balance.amount < amount {
        return Err(contract_err("insufficient dcc token balance"));
    }

    // Ensure marker holds at least the request amount of reserve token.
    let querier = ProvenanceQuerier::new(&deps.querier);
    let member_marker = querier.get_marker_by_denom(&member.denom)?;
    let reserve_balance = member_marker
        .coins
        .into_iter()
        .find(|c| c.denom == member.denom)
        .map(|c| c.amount)
        .unwrap_or_else(Uint128::zero);
    if reserve_balance < amount {
        return Err(contract_err("insufficient reserve token balance"));
    }

    // Update reserve supply and re-calculate weight for member.
    let key = info.sender.as_bytes();
    member.supply = Uint128::new(member.supply.u128() - amount.u128());
    members(deps.storage).save(key, &member)?;

    // Get dcc marker
    let dcc_marker = querier.get_marker_by_denom(&state.dcc_denom)?;

    let res = Response::new()
        // Escrow dcc in the marker account for burn.
        .add_message(transfer_marker_coins(
            amount.u128(),
            &state.dcc_denom,
            dcc_marker.address,
            info.sender.clone(),
        )?)
        // Burn the dcc token.
        .add_message(burn_marker_supply(amount.u128(), &state.dcc_denom)?)
        // Burn the reserve token.
        .add_message(burn_marker_supply(amount.u128(), &member.denom)?)
        // Add wasm event attributes.
        .add_attribute("action", "redeem_and_burn")
        .add_attribute("member_id", info.sender)
        .add_attribute("amount", amount)
        .add_attribute("denom", &state.dcc_denom)
        .add_attribute("reserve_denom", &member.denom);

    Ok(res)
}

// Swap member reserve tokens for dcc tokens.
fn try_swap(
    deps: DepsMut,
    info: MessageInfo,
    amount: Uint128,
    denom: String,
    address: Option<String>,
) -> Result<Response<ProvenanceMsg>, ContractError> {
    // Ensure no funds were sent.
    if !info.funds.is_empty() {
        return Err(contract_err("no funds should be sent during reserve swap"));
    }

    // Ensure amount is non-zero.
    if amount.is_zero() {
        return Err(contract_err("invalid reserve amount"));
    }

    // Read state
    let state = config_read(deps.storage).load()?;

    // Ensure message sender is a member.
    if members_read(deps.storage)
        .may_load(info.sender.as_bytes())?
        .is_none()
    {
        return Err(ContractError::Unauthorized {});
    }

    // Ensure amount is non-zero and denom is supported by the consortium.
    if !get_members(deps.as_ref())?.iter().any(|m| m.denom == denom) {
        return Err(contract_err("invalid reserve denom"));
    }

    // Ensure member holds at least the indicated amount.
    let balance = deps.querier.query_balance(info.sender.clone(), &denom)?;
    if balance.amount < amount {
        return Err(contract_err("insufficient reserve amount in swap"));
    }

    // Transfer reserve token from member to marker. Note: This will eventually require an authz
    // call be made first to give the SC permission to move the tokens out of the member account.
    let querier = ProvenanceQuerier::new(&deps.querier);
    let mut res = Response::new()
        .add_message(transfer_marker_coins(
            amount.u128(),
            &denom,
            querier.get_marker_by_denom(&denom)?.address,
            info.sender.clone(),
        )?)
        .add_message(mint_marker_supply(amount.u128(), &state.dcc_denom)?); // Mint the required dcc.

    // Determine where to withdraw the dcc tokens.
    let mut withdraw_address = info.sender.clone();
    if let Some(a) = address {
        withdraw_address = deps.api.addr_validate(&a)?;
        if withdraw_address != info.sender {
            ensure_attributes(deps.as_ref(), withdraw_address.clone(), state.kyc_attrs)?;
        }
    }

    // Withdraw dcc from marker to sender.
    res = res
        .add_message(withdraw_coins(
            &state.dcc_denom,
            amount.u128(),
            &state.dcc_denom,
            withdraw_address.clone(),
        )?)
        .add_attribute("action", "swap") // Add wasm event attributes
        .add_attribute("member_id", info.sender)
        .add_attribute("amount", amount)
        .add_attribute("denom", &denom)
        .add_attribute("withdraw_address", withdraw_address);
    Ok(res)
}

// Transfer dcc from sender to recipient. Both accounts must either be member accounts, or
// have the required kyc attributes.
fn try_transfer(
    deps: DepsMut,
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
        .query_balance(info.sender.clone(), &state.dcc_denom)?;
    if balance.amount < amount {
        return Err(contract_err("insufficient dcc balance in transfer"));
    }

    // Ensure accounts have the required kyc attrs if they aren't members.
    let members = members_read(deps.storage);
    if members.may_load(recipient.as_bytes())?.is_none() {
        ensure_attributes(deps.as_ref(), recipient.clone(), state.kyc_attrs.clone())?;
    }
    if members.may_load(info.sender.as_bytes())?.is_none() {
        ensure_attributes(deps.as_ref(), info.sender.clone(), state.kyc_attrs)?;
    }

    // Transfer the dcc
    let res = Response::new()
        .add_message(transfer_marker_coins(
            amount.u128(),
            &state.dcc_denom,
            recipient.clone(),
            info.sender.clone(),
        )?)
        .add_attribute("action", "transfer")
        .add_attribute("amount", amount)
        .add_attribute("denom", &state.dcc_denom)
        .add_attribute("sender", info.sender)
        .add_attribute("recipient", recipient);
    Ok(res)
}

// Increase the reserve supply of a member.
// If an address is provided, mint dcc tokens and withdraw there.
fn try_mint(
    deps: DepsMut,
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
    let mut member = members(deps.storage).load(key)?;

    // Validate that amount doesn't push supply over max
    if amount + member.supply > member.max_supply {
        return Err(contract_err("max supply exceeded"));
    }

    // Update reserve supply.
    member.supply += amount;
    members(deps.storage).save(key, &member)?;

    // Mint reserve
    let mut res = Response::new().add_message(mint_marker_supply(amount.u128(), &member.denom)?);

    // Mint dcc if the withdraw address was provided.
    let state = config_read(deps.storage).load()?;
    if address.is_some() {
        res = res.add_message(mint_marker_supply(amount.u128(), &state.dcc_denom)?);
    }

    // Add wasm event attributes
    res = res
        .add_attribute("action", "mint")
        .add_attribute("member_id", &member.id)
        .add_attribute("amount", amount)
        .add_attribute("denom", &member.denom);

    // Withdraw either dcc or reserve token.
    match address {
        None => {
            // Withdraw minted reserve tokens to the member account.
            res = res
                .add_message(withdraw_coins(
                    &member.denom,
                    amount.u128(),
                    &member.denom,
                    info.sender.clone(),
                )?)
                .add_attribute("withdraw_denom", &member.denom)
                .add_attribute("withdraw_address", info.sender);
        }
        Some(a) => {
            // When withdrawing dcc tokens to a non-member account, ensure the recipient has the
            // required kyc attributes.
            let address = deps.api.addr_validate(&a)?;
            if address != info.sender {
                ensure_attributes(deps.as_ref(), address.clone(), state.kyc_attrs)?;
            }
            // Withdraw minted dcc tokens to the provided account.
            res = res
                .add_message(withdraw_coins(
                    &state.dcc_denom,
                    amount.u128(),
                    &state.dcc_denom,
                    address.clone(),
                )?)
                .add_attribute("withdraw_denom", &state.dcc_denom)
                .add_attribute("withdraw_address", address);
        }
    };
    Ok(res)
}

// Decrease reserve token supply.
fn try_burn(
    deps: DepsMut,
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
    let mut member = members(deps.storage).load(key)?;

    // Ensure sender holds at least the provided balance
    let balance = deps
        .querier
        .query_balance(info.sender.clone(), &member.denom)?;
    if balance.amount < amount || amount > member.supply {
        return Err(contract_err("insufficient funds in burn"));
    }

    // Update reserve supply and re-calculate weight for member.
    member.supply = Uint128::new(member.supply.u128() - amount.u128());
    members(deps.storage).save(key, &member)?;

    // Get marker address using denom.
    let querier = ProvenanceQuerier::new(&deps.querier);
    let member_marker = querier.get_marker_by_denom(&member.denom)?;

    // Transfer reserve token from member to marker. Note: This will eventually require an authz
    // call be made first to give the SC permission to move the tokens out of the member account.
    let res = Response::new()
        .add_message(transfer_marker_coins(
            amount.u128(),
            &member.denom,
            member_marker.address,
            info.sender.clone(),
        )?)
        .add_message(burn_marker_supply(amount.u128(), &member.denom)?) // Burn reserve
        .add_attribute("action", "burn")
        .add_attribute("member_id", &member.id)
        .add_attribute("amount", amount)
        .add_attribute("denom", &member.denom);
    Ok(res)
}

// Add a new kyc attribute.
fn try_add_kyc(
    deps: DepsMut,
    info: MessageInfo,
    name: String,
) -> Result<Response<ProvenanceMsg>, ContractError> {
    // Validate params.
    if !info.funds.is_empty() {
        return Err(contract_err("no funds should be sent during kyc add"));
    }
    if name.trim().is_empty() {
        return Err(contract_err("kyc attribute name is empty"));
    }

    // Load state and ensure sender is the administrator.
    let mut state = config(deps.storage).load()?;
    if info.sender != state.admin {
        return Err(ContractError::Unauthorized {});
    }

    // Ensure kyc attribute wasn't already added
    if state.kyc_attrs.contains(&name) {
        return Err(contract_err("kyc attribute already exists"));
    }

    // Add the kyc attribute and save
    state.kyc_attrs.push(name.clone());
    config(deps.storage).save(&state)?;

    // Add wasm event attributes
    Ok(Response::new()
        .add_attribute("action", "add_kyc_attribute")
        .add_attribute("name", name))
}

// Remove an existing kyc attribute.
fn try_remove_kyc(
    deps: DepsMut,
    info: MessageInfo,
    name: String,
) -> Result<Response<ProvenanceMsg>, ContractError> {
    // Validate params.
    if !info.funds.is_empty() {
        return Err(contract_err("no funds should be sent during kyc remove"));
    }
    if name.trim().is_empty() {
        return Err(contract_err("kyc attribute name is empty"));
    }

    // Load state and ensure sender is the administrator.
    let mut state = config(deps.storage).load()?;
    if info.sender != state.admin {
        return Err(ContractError::Unauthorized {});
    }

    // Ensure kyc attribute wasn't already added
    if !state.kyc_attrs.contains(&name) {
        return Err(contract_err("kyc attribute does not exist"));
    }

    // Remove the kyc attribute and save
    state.kyc_attrs.retain(|n| *n != name);
    config(deps.storage).save(&state)?;

    // Add wasm event attributes
    Ok(Response::new()
        .add_attribute("action", "remove_kyc_attribute")
        .add_attribute("name", name))
}

// A helper function for creating generic contract errors.
fn contract_err(s: &str) -> ContractError {
    ContractError::Std(StdError::generic_err(s))
}

// Return an error if the given address doesn't have any of the required attributes.
fn ensure_attributes(deps: Deps, addr: Addr, attrs: Vec<String>) -> Result<(), ContractError> {
    // Skip the check if no attributes are required.
    if attrs.is_empty() {
        return Ok(());
    }
    // Check for all provided attributes
    let querier = ProvenanceQuerier::new(&deps.querier);
    for name in attrs.iter() {
        let res = querier.get_attributes(addr.clone(), Some(name))?;
        if !res.attributes.is_empty() {
            return Ok(());
        }
    }
    Err(contract_err(&format!(
        "no kyc attributes found for {}",
        addr
    )))
}

// Calculate the weight for a member based on reserve max supply.
fn calculate_weight(max_supply: u128) -> u128 {
    // Just return the truncated usd equivalent for now...
    max_supply / 100
}

/// Query contract state
pub fn query(deps: Deps, _env: Env, msg: QueryMsg) -> Result<QueryResponse, ContractError> {
    match msg {
        QueryMsg::GetJoinProposals {} => try_get_join_proposals(deps),
        QueryMsg::GetMembers {} => try_get_members(deps),
        QueryMsg::GetJoinProposal { id } => try_get_join_proposal(deps, id),
        QueryMsg::GetMember { id } => try_get_member(deps, id),
        QueryMsg::GetBalances {} => try_get_balances(deps),
    }
}

// Query all join proposals.
fn try_get_join_proposals(deps: Deps) -> Result<QueryResponse, ContractError> {
    Ok(to_binary(&JoinProposals {
        proposals: get_join_proposals(deps)?,
    })?)
}

// Query all members.
fn try_get_members(deps: Deps) -> Result<QueryResponse, ContractError> {
    Ok(to_binary(&Members {
        members: get_members(deps)?,
    })?)
}

// Query join proposal by ID.
fn try_get_join_proposal(deps: Deps, id: String) -> Result<QueryResponse, ContractError> {
    let address = deps.api.addr_validate(&id)?;
    let key = address.as_bytes();
    let proposal = join_proposals_read(deps.storage).load(key)?;
    let bin = to_binary(&proposal)?;
    Ok(bin)
}

// Query member by ID.
fn try_get_member(deps: Deps, id: String) -> Result<QueryResponse, ContractError> {
    let address = deps.api.addr_validate(&id)?;
    let key = address.as_bytes();
    let member = members_read(deps.storage).load(key)?;
    let bin = to_binary(&member)?;
    Ok(bin)
}

// Query swap-able member balances.
fn try_get_balances(deps: Deps) -> Result<QueryResponse, ContractError> {
    let balances = get_balances(deps)?;
    let bin = to_binary(&balances)?;
    Ok(bin)
}

// Read all members from bucket storage.
fn get_members(deps: Deps) -> Result<Vec<Member>, ContractError> {
    members_read(deps.storage)
        .range(None, None, Order::Ascending)
        .map(|item| {
            let (_, member) = item?;
            Ok(member)
        })
        .collect()
}

// Read all join proposals from bucket storage.
fn get_join_proposals(deps: Deps) -> Result<Vec<JoinProposal>, ContractError> {
    join_proposals_read(deps.storage)
        .range(None, None, Order::Ascending)
        .map(|item| {
            let (_, proposal) = item?;
            Ok(proposal)
        })
        .collect()
}

// Get reserve balances in escrow (swap-able) for all members.
fn get_balances(deps: Deps) -> Result<Balances, ContractError> {
    let mut balances: Vec<Balance> = Vec::new();
    let querier = ProvenanceQuerier::new(&deps.querier);
    for member in get_members(deps)? {
        let marker = querier.get_marker_by_denom(&member.denom)?;
        balances.push(Balance {
            address: marker.address,
            denom: member.denom.clone(),
            amount: marker
                .coins
                .into_iter()
                .find(|c| c.denom == member.denom)
                .map(|c| c.amount)
                .unwrap_or_else(Uint128::zero),
        });
    }
    Ok(Balances { balances })
}

/// Called when migrating a contract instance to a new code ID.
pub fn migrate(_deps: DepsMut, _env: Env, _msg: MigrateMsg) -> Result<Response, ContractError> {
    // Do nothing
    Ok(Response::default())
}

#[cfg(test)]
mod tests {
    use super::*;
    use cosmwasm_std::testing::{mock_env, mock_info};
    use cosmwasm_std::{coin, from_binary};
    use provwasm_mocks::{mock_dependencies, must_read_binary_file};
    use provwasm_std::Marker;

    #[test]
    fn test_weight_orders_of_magnitude() {
        assert_eq!(calculate_weight(0), 0);
        assert_eq!(calculate_weight(1), 0);
        assert_eq!(calculate_weight(10), 0);
        assert_eq!(calculate_weight(100), 1);
        assert_eq!(calculate_weight(1000), 10);
        assert_eq!(calculate_weight(10000), 100);
        assert_eq!(calculate_weight(100000), 1000);
        assert_eq!(calculate_weight(1000000), 10000); // $10,000.00
        assert_eq!(calculate_weight(2000000), 20000); // $20,000.00
        assert_eq!(calculate_weight(10000000), 100000); // $100,000.00
        assert_eq!(calculate_weight(100000000), 1000000); // $1,000,000.00
        assert_eq!(calculate_weight(1000000000), 10000000); // $10,000,000.00
        assert_eq!(calculate_weight(10000000000), 100000000); // $100,000,000.00
        assert_eq!(calculate_weight(100000000000), 1000000000); // $1,000,000,000.00
        assert_eq!(calculate_weight(100000000011), 1000000000); // $1,000,000,000.11 (truncated)
    }

    #[test]
    fn valid_init() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Init
        let res = instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(100),
                kyc_attrs: vec![],
                admin_weight: None,
            },
        )
        .unwrap();

        // Ensure messages were created.
        // TODO: validate marker messages (create, grant(2), finalize, activate)...
        assert_eq!(5, res.messages.len());

        // Read state
        let config_state = config_read(&deps.storage).load().unwrap();

        // Validate state values
        assert_eq!(config_state.dcc_denom, "dcc.coin");
        assert_eq!(config_state.quorum_pct, Decimal::percent(67));
        assert_eq!(config_state.vote_duration, Uint128::new(100));
    }

    #[test]
    fn join_test() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(10),
                kyc_attrs: vec![],
                admin_weight: None,
            },
        )
        .unwrap();

        // Create a valid join proposal.
        let res = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(1_000_000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Ensure messages were created.
        // TODO: validate marker messages (create, grant)...
        assert_eq!(2, res.messages.len());

        let addr = Addr::unchecked("bank");
        let key: &[u8] = addr.as_bytes();
        let proposal = join_proposals_read(&deps.storage).load(key).unwrap();

        assert_eq!(proposal.denom, "bank.coin");
        assert_eq!(proposal.max_supply, Uint128::new(1_000_000));
        assert_eq!(proposal.created, Uint128::new(12345));
        assert_eq!(proposal.expires, Uint128::new(12345 + 10));
        assert_eq!(proposal.yes, Uint128::zero());
        assert_eq!(proposal.no, Uint128::zero());
        assert!(proposal.voters.is_empty());
    }

    #[test]
    fn join_invalid_params() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(10),
                kyc_attrs: vec![],
                admin_weight: None,
            },
        )
        .unwrap();

        // Try to create join proposal w/ zero max supply
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::zero(),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "max_supply must be greater than zero")
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to create join proposal w/ an empty denom string
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(1_000_000),
                denom: "".into(),
                name: None,
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "invalid proposal denom")
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to send funds w/ the join proposal
        let funds = coin(1000, "nhash");
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[funds]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(1_000_000),
                denom: "bank.coin".into(),
                name: None,
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
    }

    #[test]
    fn join_dup_proposal() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(10),
                kyc_attrs: vec![],
                admin_weight: None,
            },
        )
        .unwrap();

        // Create a valid join proposal.
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(1_000_000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Try to create a duplicate join proposal.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(1_000_000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "duplicate proposal")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn vote_yes() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(10),
                kyc_attrs: vec![],
                admin_weight: None,
            },
        )
        .unwrap();

        // Create join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(1_000_000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Vote yes as 'admin'
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Vote {
                id: "bank".into(),
                choice: VoteChoice::Yes,
            },
        )
        .unwrap();

        let addr = Addr::unchecked("bank");
        let key: &[u8] = addr.as_bytes();
        let proposal = join_proposals_read(&deps.storage).load(key).unwrap();

        // Assert the admin vote weight was added to the 'yes' total.
        assert_eq!(proposal.yes, Uint128::new(1));
        assert_eq!(proposal.no, Uint128::zero());
        assert_eq!(proposal.voters, vec![Addr::unchecked("admin")]);
    }

    #[test]
    fn vote_no() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(10),
                kyc_attrs: vec![],
                admin_weight: None,
            },
        )
        .unwrap();

        // Create join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(1_000_000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Vote no as 'admin'
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Vote {
                id: "bank".into(),
                choice: VoteChoice::No,
            },
        )
        .unwrap();

        let addr = Addr::unchecked("bank");
        let key: &[u8] = addr.as_bytes();
        let proposal = join_proposals_read(&deps.storage).load(key).unwrap();

        // Assert the admin vote weight was added to the 'no' total.
        assert_eq!(proposal.yes, Uint128::zero());
        assert_eq!(proposal.no, Uint128::new(1));
        assert_eq!(proposal.voters, vec![Addr::unchecked("admin")]);
    }

    #[test]
    fn vote_invalid_params() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(1),
                kyc_attrs: vec![],
                admin_weight: None,
            },
        )
        .unwrap();

        // Create join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(1_000_000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Try to vote with an unauthorized account.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("unauthorized", &[]),
            ExecuteMsg::Vote {
                id: "bank".into(),
                choice: VoteChoice::Yes,
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Unauthorized {} => {}
            _ => panic!("unexpected execute error"),
        }

        // Try to vote on an invalid proposal ID.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Vote {
                id: "".into(),
                choice: VoteChoice::Yes,
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "Invalid input: human address too short")
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to send funds w/ the vote
        let funds = coin(1000, "nhash");
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[funds]),
            ExecuteMsg::Vote {
                id: "bank".into(),
                choice: VoteChoice::Yes,
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "no funds should be sent during vote")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn vote_unauthorized_after_join() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(1000),
                kyc_attrs: vec![],
                admin_weight: None,
            },
        )
        .unwrap();

        // Create join proposal for bank1
        let mut env = mock_env();
        env.block.height += 1; // next block
        execute(
            deps.as_mut(),
            env,
            mock_info("bank1", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(1_000_000),
                denom: "bank1.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Create join proposal for bank2
        let mut env = mock_env();
        env.block.height += 2; // next block
        execute(
            deps.as_mut(),
            env,
            mock_info("bank2", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(2_000_000),
                denom: "bank2.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Vote yes as admin for bank1
        let mut env = mock_env();
        env.block.height += 3; // next block
        execute(
            deps.as_mut(),
            env,
            mock_info("admin", &[]),
            ExecuteMsg::Vote {
                id: "bank1".into(),
                choice: VoteChoice::Yes,
            },
        )
        .unwrap();

        // Accept membership as bank1
        let mut env = mock_env();
        env.block.height += 4; // next block
        execute(
            deps.as_mut(),
            env,
            mock_info("bank1", &[]),
            ExecuteMsg::Accept { mint_amount: None },
        )
        .unwrap();

        // Try to vote no as bank1 for the bank2 proposal
        let mut env = mock_env();
        env.block.height += 5; // next block
        let err = execute(
            deps.as_mut(),
            env,
            mock_info("bank1", &[]),
            ExecuteMsg::Vote {
                id: "bank2".into(),
                choice: VoteChoice::No,
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
    fn vote_window_closed() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(10),
                kyc_attrs: vec![],
                admin_weight: None,
            },
        )
        .unwrap();

        // Create join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(1_000_000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Move the block height beyond proposal expiration.
        let mut env = mock_env();
        env.block.height += 100;

        // Try to vote.
        let err = execute(
            deps.as_mut(),
            env,
            mock_info("admin", &[]),
            ExecuteMsg::Vote {
                id: "bank".into(),
                choice: VoteChoice::Yes,
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "voting window has closed")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn vote_twice_error() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(1),
                kyc_attrs: vec![],
                admin_weight: None,
            },
        )
        .unwrap();

        // Create join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(1_000_000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Vote yes
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Vote {
                id: "bank".into(),
                choice: VoteChoice::Yes,
            },
        )
        .unwrap();

        // Try to vote a second time.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Vote {
                id: "bank".into(),
                choice: VoteChoice::No,
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "member has already voted")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn accept_test() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(10),
                kyc_attrs: vec![],
                admin_weight: None,
            },
        )
        .unwrap();

        // Create join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(100000000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Vote yes as 'admin'
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Vote {
                id: "bank".into(),
                choice: VoteChoice::Yes,
            },
        )
        .unwrap();

        // Accept join proposal
        let res = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Accept { mint_amount: None },
        )
        .unwrap();

        // Ensure messages were created.
        // TODO: validate marker messages (finalize, activate)...
        assert_eq!(2, res.messages.len());

        let addr = Addr::unchecked("bank");
        let key: &[u8] = addr.as_bytes();
        let member = members_read(&deps.storage).load(key).unwrap();

        assert_eq!(member.id, addr);
        assert_eq!(member.denom, "bank.coin");
        assert_eq!(member.supply, Uint128::zero());
        assert_eq!(member.max_supply, Uint128::new(100000000));
        assert_eq!(member.weight, Uint128::new(1000000));
    }

    #[test]
    fn accept_with_mint_test() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(10),
                kyc_attrs: vec![],
                admin_weight: None,
            },
        )
        .unwrap();

        // Create join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(100000000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Vote yes as 'admin'
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Vote {
                id: "bank".into(),
                choice: VoteChoice::Yes,
            },
        )
        .unwrap();

        // Accept join proposal; minting the max supply
        let res = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Accept {
                mint_amount: Some(Uint128::new(100000000)),
            },
        )
        .unwrap();

        // Ensure messages were created.
        // TODO: validate marker messages (finalize, activate, mint, withdraw)...
        assert_eq!(4, res.messages.len());

        let addr = Addr::unchecked("bank");
        let key: &[u8] = addr.as_bytes();
        let member = members_read(&deps.storage).load(key).unwrap();

        assert_eq!(member.id, addr);
        assert_eq!(member.denom, "bank.coin");
        assert_eq!(member.supply, Uint128::new(100000000));
        assert_eq!(member.max_supply, Uint128::new(100000000));
        assert_eq!(member.weight, Uint128::new(1000000));
    }

    #[test]
    fn accept_param_errors() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(10),
                kyc_attrs: vec![],
                admin_weight: None,
            },
        )
        .unwrap();

        // Create join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(1_000_000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Try to accept a join proposal that has no quorum (no yes votes).
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Accept { mint_amount: None },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "no membership quorum")
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to accept a join proposal that doesn't exist.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank1", &[]),
            ExecuteMsg::Accept { mint_amount: None },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::NotFound { .. }) => {}
            _ => panic!("unexpected execute error"),
        }

        // Try to send funds with the accept message.
        let funds = coin(1000, "nhash");
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[funds]),
            ExecuteMsg::Accept { mint_amount: None },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "no funds should be sent during accept")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn cancel_test() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(10),
                kyc_attrs: vec![],
                admin_weight: None,
            },
        )
        .unwrap();

        // Create join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(1_000_000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Vote no as 'admin'
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Vote {
                id: "bank".into(),
                choice: VoteChoice::No,
            },
        )
        .unwrap();

        // Cancel join proposal
        let res = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Cancel {},
        )
        .unwrap();

        // Ensure messages were created.
        // TODO: validate marker messages (cancel, destroy)...
        assert_eq!(2, res.messages.len());
    }

    #[test]
    fn cancel_param_errors() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(10),
                kyc_attrs: vec![],
                admin_weight: None,
            },
        )
        .unwrap();

        // Create join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(1_000_000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Try to cancel a join proposal that doesn't exist.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank1", &[]),
            ExecuteMsg::Cancel {},
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::NotFound { .. }) => {}
            _ => panic!("unexpected execute error"),
        }

        // Try to send funds with the cancel message.
        let funds = coin(1000, "nhash");
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[funds]),
            ExecuteMsg::Cancel {},
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "no funds should be sent during cancel")
            }
            _ => panic!("unexpected execute error"),
        }

        // Vote yes as 'admin'
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Vote {
                id: "bank".into(),
                choice: VoteChoice::Yes,
            },
        )
        .unwrap();

        // Accept join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Accept { mint_amount: None },
        )
        .unwrap();

        // Try to cancel the accepted join proposal
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Cancel {},
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "membership already accepted")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn transfer_test() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(10),
                kyc_attrs: vec!["bank.kyc".into()],
                admin_weight: None,
            },
        )
        .unwrap();

        // Create join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(1_000_000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Vote yes as 'admin'
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Vote {
                id: "bank".into(),
                choice: VoteChoice::Yes,
            },
        )
        .unwrap();

        // Accept join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Accept { mint_amount: None },
        )
        .unwrap();

        // Assume the customer has a balance of dcc tokens + the required attribute.
        let dcc = coin(1000, "dcc.coin");
        deps.querier
            .base
            .update_balance(Addr::unchecked("customer"), vec![dcc]);
        deps.querier
            .with_attributes("customer", &[("bank.kyc", "ok", "string")]);

        // Transfer dcc from the customer to a member bank.
        let res = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("customer", &[]),
            ExecuteMsg::Transfer {
                amount: Uint128::new(500),
                recipient: "bank".into(),
            },
        )
        .unwrap();

        // Ensure message was created.
        // TODO: validate marker transfer message...
        assert_eq!(1, res.messages.len());
    }

    #[test]
    fn transfer_param_errors() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(10),
                kyc_attrs: vec!["bank.kyc".into()],
                admin_weight: None,
            },
        )
        .unwrap();

        // Create join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(1_000_000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Vote yes as 'admin'
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Vote {
                id: "bank".into(),
                choice: VoteChoice::Yes,
            },
        )
        .unwrap();

        // Accept join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Accept { mint_amount: None },
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
                assert_eq!(msg, "bank sends are not allowed in dcc transfer")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn transfer_insufficient_funds() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(10),
                kyc_attrs: vec!["bank.kyc".into()],
                admin_weight: None,
            },
        )
        .unwrap();

        // Create join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(1_000_000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Vote yes as 'admin'
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Vote {
                id: "bank".into(),
                choice: VoteChoice::Yes,
            },
        )
        .unwrap();

        // Accept join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Accept { mint_amount: None },
        )
        .unwrap();

        // Assume the customer has the required attribute, but no dcc tokens.
        deps.querier
            .with_attributes("customer", &[("bank.kyc", "ok", "string")]);

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
                assert_eq!(msg, "insufficient dcc balance in transfer")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn transfer_without_attributes() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(10),
                kyc_attrs: vec!["bank.kyc".into()],
                admin_weight: None,
            },
        )
        .unwrap();

        // Create join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(1_000_000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Vote yes as 'admin'
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Vote {
                id: "bank".into(),
                choice: VoteChoice::Yes,
            },
        )
        .unwrap();

        // Accept join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Accept { mint_amount: None },
        )
        .unwrap();

        // Assume the customer has a balance of dcc, but does NOT have the required attribute.
        let dcc = coin(1000, "dcc.coin");
        deps.querier
            .base
            .update_balance(Addr::unchecked("customer"), vec![dcc]);

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
    fn mint_test() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(10),
                kyc_attrs: vec![],
                admin_weight: None,
            },
        )
        .unwrap();

        // Create join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(1_000_000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Vote yes as 'admin'
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Vote {
                id: "bank".into(),
                choice: VoteChoice::Yes,
            },
        )
        .unwrap();

        // Accept join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Accept { mint_amount: None },
        )
        .unwrap();

        let addr = Addr::unchecked("bank");
        let key: &[u8] = addr.as_bytes();
        let member = members_read(&deps.storage).load(key).unwrap();

        // Ensure supply is zero
        assert_eq!(member.supply, Uint128::zero());

        // Mint reserve tokens and withdraw them.
        let res = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Mint {
                amount: Uint128::new(100),
                address: None,
            },
        )
        .unwrap();

        // Ensure messages were created.
        // TODO: validate marker messages (mint reserve, withdraw reserve)...
        assert_eq!(2, res.messages.len());

        // Ensure supply was updated
        let member = members_read(&deps.storage).load(key).unwrap();
        assert_eq!(member.supply, Uint128::new(100));
    }

    #[test]
    fn mint_param_errors() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(10),
                admin_weight: None,
                kyc_attrs: vec![],
            },
        )
        .unwrap();

        // Create join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(1_000_000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Vote yes as 'admin'
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Vote {
                id: "bank".into(),
                choice: VoteChoice::Yes,
            },
        )
        .unwrap();

        // Accept join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Accept { mint_amount: None },
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

        // Try to mint > max_supply reserve tokens
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Mint {
                amount: Uint128::new(1_000_001),
                address: None,
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg }) => {
                assert_eq!(msg, "max supply exceeded")
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
                assert_eq!(kind, "dcc::state::Member");
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn mint_withdraw_dcc_test() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(10),
                kyc_attrs: vec![],
                admin_weight: None,
            },
        )
        .unwrap();

        // Create join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(1_000_000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Vote yes as 'admin'
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Vote {
                id: "bank".into(),
                choice: VoteChoice::Yes,
            },
        )
        .unwrap();

        // Accept join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Accept { mint_amount: None },
        )
        .unwrap();

        let addr = Addr::unchecked("bank");
        let key: &[u8] = addr.as_bytes();
        let member = members_read(&deps.storage).load(key).unwrap();

        // Ensure supply is zero
        assert_eq!(member.supply, Uint128::zero());

        // Mint reserve tokens and withdraw dcc to a customer address.
        let res = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Mint {
                amount: Uint128::new(100),
                address: Some("customer".into()),
            },
        )
        .unwrap();

        // Ensure messages were created.
        // TODO: validate marker messages (mint reserve, mint dcc, withdraw dcc)...
        assert_eq!(3, res.messages.len());

        // Ensure supply was updated
        let member = members_read(&deps.storage).load(key).unwrap();
        assert_eq!(member.supply, Uint128::new(100));
    }

    #[test]
    fn burn_test() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Burn needs to query the marker address, so we mock one here
        let bin = must_read_binary_file("testdata/bank.json");
        let marker: Marker = from_binary(&bin).unwrap();
        deps.querier.with_markers(vec![marker]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(10),
                kyc_attrs: vec![],
                admin_weight: None,
            },
        )
        .unwrap();

        // Create join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(1_000_000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Vote yes as 'admin'
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Vote {
                id: "bank".into(),
                choice: VoteChoice::Yes,
            },
        )
        .unwrap();

        // Accept join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Accept { mint_amount: None },
        )
        .unwrap();

        // Mint reserve tokens.
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Mint {
                amount: Uint128::new(100),
                address: None,
            },
        )
        .unwrap();

        // Simulate balance update due to mint.
        let addr = Addr::unchecked("bank");
        let minted = coin(100, "bank.coin");
        deps.querier.base.update_balance(addr.clone(), vec![minted]);

        // Burn reserve tokens.
        let res = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Burn {
                amount: Uint128::new(25),
            },
        )
        .unwrap();

        // Ensure messages were created.
        // TODO: validate marker messages (transfer, burn)...
        assert_eq!(2, res.messages.len());

        // Ensure supply was reduced as expected.
        let key: &[u8] = addr.as_bytes();
        let member = members_read(&deps.storage).load(key).unwrap();
        assert_eq!(member.supply, Uint128::new(75));
    }

    #[test]
    fn burn_param_errors() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(10),
                kyc_attrs: vec![],
                admin_weight: None,
            },
        )
        .unwrap();

        // Create join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(1_000_000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Vote yes as 'admin'
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Vote {
                id: "bank".into(),
                choice: VoteChoice::Yes,
            },
        )
        .unwrap();

        // Accept join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Accept { mint_amount: None },
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
                assert_eq!(kind, "dcc::state::Member");
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to burn more than a member holds in their account.
        let dcc = coin(100, "dcc.coin");
        deps.querier
            .base
            .update_balance(Addr::unchecked("bank"), vec![dcc]);
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
                assert_eq!(msg, "insufficient funds in burn")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn swap_test() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Swap needs to query the marker address, so we mock one here
        let bin = must_read_binary_file("testdata/bank.json");
        let marker: Marker = from_binary(&bin).unwrap();
        deps.querier.with_markers(vec![marker]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(10),
                kyc_attrs: vec![],
                admin_weight: None,
            },
        )
        .unwrap();

        // Create join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(100_000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Vote yes as 'admin'
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Vote {
                id: "bank".into(),
                choice: VoteChoice::Yes,
            },
        )
        .unwrap();

        // Accept join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Accept { mint_amount: None },
        )
        .unwrap();

        // Simulate balance update due to mint.
        let addr = Addr::unchecked("bank");
        let minted = coin(10_000, "bank.coin");
        deps.querier.base.update_balance(addr.clone(), vec![minted]);

        // Swap reserve tokens for dcc tokens
        let res = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Swap {
                amount: Uint128::new(5000),
                denom: "bank.coin".into(),
                address: None,
            },
        )
        .unwrap();

        // Ensure messages were created.
        // TODO: validate marker messages (transfer, mint dcc, withdraw dcc)...
        assert_eq!(3, res.messages.len());
    }

    #[test]
    fn swap_param_errors() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Swap needs to query the marker address, so we mock one here
        let bin = must_read_binary_file("testdata/bank.json");
        let marker: Marker = from_binary(&bin).unwrap();
        deps.querier.with_markers(vec![marker]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(10),
                kyc_attrs: vec![],
                admin_weight: None,
            },
        )
        .unwrap();

        // Create join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(100_000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Vote yes as 'admin'
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Vote {
                id: "bank".into(),
                choice: VoteChoice::Yes,
            },
        )
        .unwrap();

        // Accept join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Accept { mint_amount: None },
        )
        .unwrap();

        // Try to send funds w/ the swap message.
        let funds = coin(1000, "nhash");
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[funds]),
            ExecuteMsg::Swap {
                amount: Uint128::new(5000),
                denom: "bank.coin".into(),
                address: None,
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "no funds should be sent during reserve swap")
            }
            _ => panic!("unexpected execute error"),
        }

        // Try so swap with amount of zero.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Swap {
                amount: Uint128::zero(),
                denom: "bank.coin".into(),
                address: None,
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "invalid reserve amount")
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to swap where message sender is not a member.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("non.member", &[]),
            ExecuteMsg::Swap {
                amount: Uint128::new(5000),
                denom: "bank.coin".into(),
                address: None,
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Unauthorized {} => {}
            _ => panic!("unexpected execute error"),
        }

        // Try to swap a denom that is not supported by the consortium.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Swap {
                amount: Uint128::new(5000),
                denom: "unsupported.coin".into(),
                address: None,
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "invalid reserve denom")
            }
            _ => panic!("unexpected execute error"),
        }

        // Simulate balance update due to mint.
        let addr = Addr::unchecked("bank");
        let minted = coin(1000, "bank.coin");
        deps.querier.base.update_balance(addr.clone(), vec![minted]);

        // Try to swap when a member holds less than the indicated amount.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Swap {
                amount: Uint128::new(5000),
                denom: "bank.coin".into(),
                address: None,
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "insufficient reserve amount in swap")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn redeem_test() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Redeem needs to query the dcc marker address, so we mock it here
        let bin = must_read_binary_file("testdata/dcc.json");
        let dcc_marker: Marker = from_binary(&bin).unwrap();
        let bin = must_read_binary_file("testdata/bank.json");
        let bank_marker: Marker = from_binary(&bin).unwrap();
        deps.querier.with_markers(vec![dcc_marker, bank_marker]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(10),
                kyc_attrs: vec![],
                admin_weight: None,
            },
        )
        .unwrap();

        // Create join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(100_000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Vote yes as 'admin'
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Vote {
                id: "bank".into(),
                choice: VoteChoice::Yes,
            },
        )
        .unwrap();

        // Accept join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Accept { mint_amount: None },
        )
        .unwrap();

        // Simulate a mint(5,000) first, so bank has dcc
        let addr = Addr::unchecked("bank");
        let dcc = coin(5000, "dcc.coin");
        deps.querier.base.update_balance(addr.clone(), vec![dcc]);

        // Redeem dcc for reserve tokens
        let res = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Redeem {
                amount: Uint128::new(2500),
                reserve_denom: Some("bank.coin".into()),
            },
        )
        .unwrap();

        // Ensure messages were created.
        // TODO: validate marker messages (transfer dcc, burn dcc, withdraw reserve)...
        assert_eq!(3, res.messages.len());
    }

    #[test]
    fn redeem_param_errors() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Redeem needs to query the dcc marker address, so we mock it here
        let bin = must_read_binary_file("testdata/dcc.json");
        let marker: Marker = from_binary(&bin).unwrap();
        deps.querier.with_markers(vec![marker]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(10),
                kyc_attrs: vec![],
                admin_weight: None,
            },
        )
        .unwrap();

        // Create join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(100_000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Vote yes as 'admin'
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Vote {
                id: "bank".into(),
                choice: VoteChoice::Yes,
            },
        )
        .unwrap();

        // Accept join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Accept { mint_amount: None },
        )
        .unwrap();

        // Try to send funds w/ redeem message.
        let funds = coin(1000, "nhash");
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[funds]),
            ExecuteMsg::Redeem {
                amount: Uint128::new(2500),
                reserve_denom: Some("bank.coin".into()),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "no funds should be sent during dcc redemption")
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to redeem with an amount of zero.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Redeem {
                amount: Uint128::zero(),
                reserve_denom: Some("bank.coin".into()),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "invalid redeem amount")
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to redeem from a non-member account
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("non.member", &[]),
            ExecuteMsg::Redeem {
                amount: Uint128::new(2500),
                reserve_denom: Some("bank.coin".into()),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::NotFound { kind }) => {
                assert_eq!(kind, "dcc::state::Member")
            }
            _ => panic!("unexpected execute error"),
        }

        // Simulate balance update due to swap.
        let addr = Addr::unchecked("bank");
        let swapped = coin(1000, "dcc.coin");
        deps.querier
            .base
            .update_balance(addr.clone(), vec![swapped]);

        // Try to redeem more dcc tokens than the member holds.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Redeem {
                amount: Uint128::new(2500),
                reserve_denom: Some("bank.coin".into()),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "insufficient dcc token balance")
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to redeem for a denom that is not supported by the consortium.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Redeem {
                amount: Uint128::new(500),
                reserve_denom: Some("unsupported.coin".into()),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "unsupported reserve denom")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn redeem_and_burn_test() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Redeem needs to query the dcc marker address, so we mock it here
        let bin = must_read_binary_file("testdata/dcc.json");
        let dcc_marker: Marker = from_binary(&bin).unwrap();
        let bin = must_read_binary_file("testdata/bank.json");
        let bank_marker: Marker = from_binary(&bin).unwrap();
        deps.querier.with_markers(vec![dcc_marker, bank_marker]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(10),
                kyc_attrs: vec![],
                admin_weight: None,
            },
        )
        .unwrap();

        // Create join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(1_000_000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Vote yes as 'admin'
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Vote {
                id: "bank".into(),
                choice: VoteChoice::Yes,
            },
        )
        .unwrap();

        // Accept join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Accept { mint_amount: None },
        )
        .unwrap();

        // Mint reserve tokens.
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Mint {
                amount: Uint128::new(100),
                address: Some("bank".into()),
            },
        )
        .unwrap();

        let addr = Addr::unchecked("bank");
        let key: &[u8] = addr.as_bytes();
        let member = members_read(&deps.storage).load(key).unwrap();
        assert_eq!(member.supply, Uint128::new(100));

        // Simulate a mint(100) first, so bank has dcc
        let dcc = coin(100, "dcc.coin");
        deps.querier.base.update_balance(addr.clone(), vec![dcc]);

        // Redeem and burn dcc and reserve tokens
        let res = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::RedeemAndBurn {
                amount: Uint128::new(25),
            },
        )
        .unwrap();

        // Ensure messages were created.
        // TODO: validate marker messages (transfer dcc, burn dcc, burn reserve)...
        assert_eq!(3, res.messages.len());

        // Ensure supply was reduced as expected.
        let member = members_read(&deps.storage).load(key).unwrap();
        assert_eq!(member.supply, Uint128::new(75));
    }

    #[test]
    fn redeem_and_burn_param_errors() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Redeem needs to query the dcc marker address, so we mock it here
        let bin = must_read_binary_file("testdata/dcc.json");
        let dcc_marker: Marker = from_binary(&bin).unwrap();
        let bin = must_read_binary_file("testdata/bank.json");
        let bank_marker: Marker = from_binary(&bin).unwrap();
        deps.querier.with_markers(vec![dcc_marker, bank_marker]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(10),
                kyc_attrs: vec![],
                admin_weight: None,
            },
        )
        .unwrap();

        // Create join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Join {
                max_supply: Uint128::new(1_000_000),
                denom: "bank.coin".into(),
                name: None,
            },
        )
        .unwrap();

        // Vote yes as 'admin'
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::Vote {
                id: "bank".into(),
                choice: VoteChoice::Yes,
            },
        )
        .unwrap();

        // Accept join proposal
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::Accept { mint_amount: None },
        )
        .unwrap();

        // Try to send funds w/ redeem and burn message.
        let funds = coin(1000, "nhash");
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[funds]),
            ExecuteMsg::RedeemAndBurn {
                amount: Uint128::new(2500),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "no funds should be sent during dcc redemption")
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to redeem and burn with an amount of zero.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::RedeemAndBurn {
                amount: Uint128::zero(),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "invalid redeem and burn amount")
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to redeem and burn from a non-member account
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("non.member", &[]),
            ExecuteMsg::RedeemAndBurn {
                amount: Uint128::new(2500),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::NotFound { kind }) => {
                assert_eq!(kind, "dcc::state::Member")
            }
            _ => panic!("unexpected execute error"),
        }

        // Simulate balance update due to swap.
        let addr = Addr::unchecked("bank");
        let swapped = coin(1000, "dcc.coin");
        deps.querier
            .base
            .update_balance(addr.clone(), vec![swapped]);

        // Try to redeem more dcc tokens than the member holds.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::RedeemAndBurn {
                amount: Uint128::new(2500),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "insufficient dcc token balance")
            }
            _ => panic!("unexpected execute error"),
        }

        let swapped = coin(10_000, "dcc.coin");
        deps.querier
            .base
            .update_balance(addr.clone(), vec![swapped]);

        // Try to redeem more dcc tokens than backed by bank token.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("bank", &[]),
            ExecuteMsg::RedeemAndBurn {
                amount: Uint128::new(6000),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "insufficient reserve token balance")
            }
            _ => panic!("unexpected execute error"),
        }
    }

    #[test]
    fn add_kyc_test() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(100),
                kyc_attrs: vec![],
                admin_weight: None,
            },
        )
        .unwrap();

        // Ensure we don't have any kyc attributes.
        let config_state = config_read(&deps.storage).load().unwrap();
        assert!(config_state.kyc_attrs.is_empty());

        // Add a kyc attribute
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::AddKyc {
                name: "bank.kyc".into(),
            },
        )
        .unwrap();

        // Ensure we now have have one kyc attribute.
        let config_state = config_read(&deps.storage).load().unwrap();
        assert_eq!(config_state.kyc_attrs.len(), 1);
    }

    #[test]
    fn add_kyc_param_errors() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(100),
                kyc_attrs: vec![],
                admin_weight: None,
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
                name: "bank.kyc".into(),
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
            ExecuteMsg::AddKyc { name: "".into() },
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
                name: "bank.kyc".into(),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Unauthorized {} => {}
            _ => panic!("unexpected execute error"),
        }

        // Add an attribute.
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::AddKyc {
                name: "bank.kyc".into(),
            },
        )
        .unwrap();

        // Try to add a the kyc attr a second time.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::AddKyc {
                name: "bank.kyc".into(),
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
        let mut deps = mock_dependencies(&[]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(100),
                kyc_attrs: vec!["bank.kyc".into()],
                admin_weight: None,
            },
        )
        .unwrap();

        // Ensure we have one kyc attribute.
        let config_state = config_read(&deps.storage).load().unwrap();
        assert_eq!(config_state.kyc_attrs.len(), 1);

        // Remove the kyc attribute
        execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::RemoveKyc {
                name: "bank.kyc".into(),
            },
        )
        .unwrap();

        // Ensure we now have have zero kyc attributes.
        let config_state = config_read(&deps.storage).load().unwrap();
        assert!(config_state.kyc_attrs.is_empty());
    }

    #[test]
    fn remove_kyc_param_errors() {
        // Create mock deps.
        let mut deps = mock_dependencies(&[]);

        // Init
        instantiate(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            InitMsg {
                dcc_denom: "dcc.coin".into(),
                quorum_pct: Decimal::percent(67),
                vote_duration: Uint128::new(100),
                kyc_attrs: vec!["bank.kyc".into()],
                admin_weight: None,
            },
        )
        .unwrap();

        // Try to send additional funds w/ the remove kyc message.
        let funds = coin(1000, "nhash");
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[funds]),
            ExecuteMsg::RemoveKyc {
                name: "bank.kyc".into(),
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

        // Try to remove an empty attribute
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::RemoveKyc { name: "".into() },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Std(StdError::GenericErr { msg, .. }) => {
                assert_eq!(msg, "kyc attribute name is empty")
            }
            _ => panic!("unexpected execute error"),
        }

        // Try to remove a kyc attribute as a non-administrator.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("non.admin", &[]),
            ExecuteMsg::RemoveKyc {
                name: "bank.kyc".into(),
            },
        )
        .unwrap_err();

        // Ensure the expected error was returned.
        match err {
            ContractError::Unauthorized {} => {}
            _ => panic!("unexpected execute error"),
        }

        // Try to remove a kyc attribute that doesn't exist.
        let err = execute(
            deps.as_mut(),
            mock_env(),
            mock_info("admin", &[]),
            ExecuteMsg::RemoveKyc {
                name: "bank.kyc.dne".into(),
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
    fn migrate_test() {
        // Create mock deps
        let mut deps = mock_dependencies(&[]);

        // Call migrate
        let res = migrate(deps.as_mut(), mock_env(), MigrateMsg {}).unwrap(); // Panics on error

        // Should just get the default response for now
        assert_eq!(res, Response::default());
    }
}
