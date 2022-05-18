use cosmwasm_std::StdError;
use semver::Error as SemverError;
use thiserror::Error;

#[derive(Error, Debug)]
pub enum ContractError {
    #[error("{0}")]
    Std(#[from] StdError),

    #[error("Unauthorized")]
    Unauthorized {},

    #[error("{0}")]
    SemverError(#[from] SemverError),
}
