use crate::error::ContractError;
use cosmwasm_std::{Coin, Uint128};
use schemars::JsonSchema;
use serde::{Deserialize, Serialize};
use uuid::Uuid;

pub trait Validate {
    fn validate(&self) -> Result<(), ContractError>;
}

#[derive(Serialize, Deserialize, Clone, Debug, Eq, PartialEq, JsonSchema)]
pub struct InitMsg {
    pub dcc_address: String,
    pub dcc_denom: String,
}

/// Simple validation of InitMsg data
///
/// ### Example
///
/// ```rust
/// use dcc_sale::msg::{InitMsg, Validate};
/// pub fn instantiate(msg: InitMsg) {
///
///     let result = msg.validate();
/// }
/// ```
impl Validate for InitMsg {
    fn validate(&self) -> Result<(), ContractError> {
        let mut invalid_fields: Vec<&str> = vec![];

        if self.dcc_address.is_empty() {
            invalid_fields.push("dcc_address");
        }

        if self.dcc_denom.is_empty() {
            invalid_fields.push("dcc_denom");
        }

        match invalid_fields.len() {
            0 => Ok(()),
            _ => Err(ContractError::InvalidFields {
                fields: invalid_fields.into_iter().map(|item| item.into()).collect(),
            }),
        }
    }
}

#[derive(Serialize, Deserialize, Clone, Debug, Eq, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub struct MigrateMsg {}

#[derive(Serialize, Deserialize, Clone, Debug, Eq, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub enum ExecuteMsg {
    CreateSale {
        id: String,
        price: Coin,
        buyer: String,
    },
    CompleteSale {
        id: String,
    },
    CancelSale {
        id: String,
    },
}

impl Validate for ExecuteMsg {
    /// Simple validation of ExecuteMsg data
    ///
    /// ### Example
    ///
    /// ```rust
    /// use dcc_sale::msg::{ExecuteMsg, Validate};
    ///
    /// pub fn execute(msg: ExecuteMsg) {
    ///     let result = msg.validate();
    ///     todo!()
    /// }
    /// ```
    fn validate(&self) -> Result<(), ContractError> {
        let mut invalid_fields: Vec<&str> = vec![];

        match self {
            ExecuteMsg::CreateSale { id, price, buyer } => {
                if Uuid::parse_str(id).is_err() {
                    invalid_fields.push("id");
                }

                if price.denom.is_empty() {
                    invalid_fields.push("price_denom");
                }

                if price.amount <= Uint128::zero() {
                    invalid_fields.push("price_amount");
                }

                if buyer.is_empty() {
                    invalid_fields.push("buyer");
                }
            }
            ExecuteMsg::CompleteSale { id } => {
                if Uuid::parse_str(id).is_err() {
                    invalid_fields.push("id");
                }
            }
            ExecuteMsg::CancelSale { id } => {
                if Uuid::parse_str(id).is_err() {
                    invalid_fields.push("id");
                }
            }
        }

        match invalid_fields.len() {
            0 => Ok(()),
            _ => Err(ContractError::InvalidFields {
                fields: invalid_fields.into_iter().map(|item| item.into()).collect(),
            }),
        }
    }
}

#[derive(Serialize, Deserialize, Clone, Debug, Eq, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub enum DCCWasmMsg {
    ExecutorTransfer {
        amount: Uint128,
        sender: String,
        recipient: String,
    },
}

#[derive(Serialize, Deserialize, Clone, Debug, Eq, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub enum QueryMsg {
    GetSale { id: String },
    GetContractInfo {},
    GetVersionInfo {},
}

impl Validate for QueryMsg {
    /// Simple validation of ExecuteMsg data
    ///
    /// ### Example
    ///
    /// ```rust
    /// use dcc_sale::msg::{QueryMsg, Validate};
    ///
    /// pub fn execute(msg: QueryMsg) {
    ///     let result = msg.validate();
    ///     todo!()
    /// }
    /// ```
    fn validate(&self) -> Result<(), ContractError> {
        let mut invalid_fields: Vec<&str> = vec![];

        match self {
            QueryMsg::GetSale { id } => {
                if Uuid::parse_str(id).is_err() {
                    invalid_fields.push("id");
                }
            }
            QueryMsg::GetContractInfo {} => {}
            QueryMsg::GetVersionInfo {} => {}
        }

        match invalid_fields.len() {
            0 => Ok(()),
            _ => Err(ContractError::InvalidFields {
                fields: invalid_fields.into_iter().map(|item| item.into()).collect(),
            }),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::msg::ExecuteMsg;

    #[test]
    fn validate_init() {
        let invalid_init_msg = InitMsg {
            dcc_address: "".into(),
            dcc_denom: "".into(),
        };
        let validate_response = invalid_init_msg.validate();

        match validate_response {
            Ok(..) => panic!("expected error but was ok"),
            Err(error) => match error {
                ContractError::InvalidFields { fields } => {
                    assert_eq!(2, fields.len());
                    assert!(fields.contains(&"dcc_address".into()));
                    assert!(fields.contains(&"dcc_denom".into()));
                }
                error => panic!("unexpected error: {:?}", error),
            },
        }
    }

    #[test]
    fn validate_execute_create_sale() {
        let invalid_create_sale_msg = ExecuteMsg::CreateSale {
            id: "gfewa".into(),
            buyer: "".into(),
            price: Coin {
                amount: Uint128::zero(),
                denom: "".into(),
            },
        };

        let validate_response = invalid_create_sale_msg.validate();

        match validate_response {
            Ok(..) => panic!("expected error but was ok"),
            Err(error) => match error {
                ContractError::InvalidFields { fields } => {
                    assert_eq!(4, fields.len());
                    assert!(fields.contains(&"id".into()));
                    assert!(fields.contains(&"buyer".into()));
                    assert!(fields.contains(&"price_denom".into()));
                    assert!(fields.contains(&"price_amount".into()));
                }
                error => panic!("unexpected error: {:?}", error),
            },
        }
    }

    #[test]
    fn validate_execute_complete_sale() {
        let invalid_complete_sale_msg = ExecuteMsg::CompleteSale { id: "asdf".into() };
        let validate_response = invalid_complete_sale_msg.validate();

        match validate_response {
            Ok(..) => panic!("expected error but was ok"),
            Err(error) => match error {
                ContractError::InvalidFields { fields } => {
                    assert_eq!(1, fields.len());
                    assert!(fields.contains(&"id".into()));
                }
                error => panic!("unexpected error: {:?}", error),
            },
        }
    }

    #[test]
    fn validate_execute_cancel_sale() {
        let invalid_cancel_sale_msg = ExecuteMsg::CancelSale { id: "asdf".into() };
        let validate_response = invalid_cancel_sale_msg.validate();

        match validate_response {
            Ok(..) => panic!("expected error but was ok"),
            Err(error) => match error {
                ContractError::InvalidFields { fields } => {
                    assert_eq!(1, fields.len());
                    assert!(fields.contains(&"id".into()));
                }
                error => panic!("unexpected error: {:?}", error),
            },
        }
    }

    #[test]
    fn validate_query_get_sale() {
        let invalid_query_sale_msg = QueryMsg::GetSale { id: "asdf".into() };
        let validate_response = invalid_query_sale_msg.validate();

        match validate_response {
            Ok(..) => panic!("expected error but was ok"),
            Err(error) => match error {
                ContractError::InvalidFields { fields } => {
                    assert_eq!(1, fields.len());
                    assert!(fields.contains(&"id".into()));
                }
                error => panic!("unexpected error: {:?}", error),
            },
        }
    }
}
