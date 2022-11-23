use cosmwasm_std::StdError;
use thiserror::Error;

#[derive(Error, Debug)]
pub enum ContractError {
    #[error("Invalid fields: {fields:?}")]
    InvalidFields { fields: Vec<String> },

    #[error("{0}")]
    Std(#[from] StdError),

    #[error("Unauthorized: {error:?}")]
    Unauthorized { error: String },

    #[error("Unsupported Marker Type: {error:?}")]
    UnsupportedMarkerType { error: String },
}

impl From<ContractError> for StdError {
    fn from(error: ContractError) -> Self {
        StdError::GenericErr {
            msg: error.to_string(),
        }
    }
}

// A helper function for creating generic contract errors.
pub fn contract_err(s: &str) -> ContractError {
    ContractError::Std(StdError::generic_err(s))
}
