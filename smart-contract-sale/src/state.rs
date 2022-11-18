use cosmwasm_std::{Addr, Coin, Storage};
use cosmwasm_storage::{
    bucket, bucket_read, singleton, singleton_read, Bucket, ReadonlyBucket, ReadonlySingleton,
    Singleton,
};
use schemars::JsonSchema;
use serde::{Deserialize, Serialize};
use std::fmt;

pub static CONFIG_KEY: &[u8] = b"config";
pub static NAMESPACE_SALE: &[u8] = b"sale";

#[derive(Serialize, Deserialize, Clone, Debug, Eq, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub struct State {
    pub admin: Addr,
    pub dcc_address: Addr,
    pub dcc_denom: String,
}

#[derive(Serialize, Deserialize, Clone, Debug, Eq, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub struct Sale {
    pub id: String,
    pub asset: Coin,
    pub owner: Addr,
    pub buyer: Addr,
    pub price: Coin,
    pub status: Status,
}

#[derive(Serialize, Deserialize, Clone, Debug, Eq, PartialEq, JsonSchema)]
#[serde(rename_all = "snake_case")]
pub enum Status {
    Pending,
    Canceled,
    Complete,
}

impl fmt::Display for Status {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "{:?}", self)
    }
}

pub fn config(storage: &mut dyn Storage) -> Singleton<State> {
    singleton(storage, CONFIG_KEY)
}

pub fn config_read(storage: &dyn Storage) -> ReadonlySingleton<State> {
    singleton_read(storage, CONFIG_KEY)
}

pub fn get_sale_storage(storage: &mut dyn Storage) -> Bucket<Sale> {
    bucket(storage, NAMESPACE_SALE)
}

pub fn get_sale_storage_read(storage: &dyn Storage) -> ReadonlyBucket<Sale> {
    bucket_read(storage, NAMESPACE_SALE)
}
