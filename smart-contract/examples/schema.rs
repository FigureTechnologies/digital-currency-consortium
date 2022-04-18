use std::env::current_dir;
use std::fs::create_dir_all;

use cosmwasm_schema::{export_schema, remove_schemas, schema_for};

use dcc::member::MemberV2;
use dcc::msg::{ExecuteMsg, InitMsg, Members, QueryMsg};
use dcc::state::StateV2;

fn main() {
    let mut out_dir = current_dir().unwrap();
    out_dir.push("schema");
    create_dir_all(&out_dir).unwrap();
    remove_schemas(&out_dir).unwrap();

    export_schema(&schema_for!(InitMsg), &out_dir);
    export_schema(&schema_for!(ExecuteMsg), &out_dir);
    export_schema(&schema_for!(QueryMsg), &out_dir);
    export_schema(&schema_for!(StateV2), &out_dir);
    export_schema(&schema_for!(MemberV2), &out_dir);
    export_schema(&schema_for!(Members), &out_dir);
}
