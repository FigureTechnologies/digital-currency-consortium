use std::env::current_dir;
use std::fs::create_dir_all;

use cosmwasm_schema::{export_schema, remove_schemas, schema_for};

use dcc::msg::{Balances, ExecuteMsg, InitMsg, JoinProposals, Members, QueryMsg};
use dcc::state::{JoinProposal, Member, State};

fn main() {
    let mut out_dir = current_dir().unwrap();
    out_dir.push("schema");
    create_dir_all(&out_dir).unwrap();
    remove_schemas(&out_dir).unwrap();

    export_schema(&schema_for!(InitMsg), &out_dir);
    export_schema(&schema_for!(ExecuteMsg), &out_dir);
    export_schema(&schema_for!(QueryMsg), &out_dir);
    export_schema(&schema_for!(State), &out_dir);
    export_schema(&schema_for!(JoinProposal), &out_dir);
    export_schema(&schema_for!(JoinProposals), &out_dir);
    export_schema(&schema_for!(Member), &out_dir);
    export_schema(&schema_for!(Members), &out_dir);
    export_schema(&schema_for!(Balances), &out_dir);
}
