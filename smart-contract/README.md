# Digital Currency Consortium (DCC) Smart Contact

This contract manages a consortium of banks that control the supply of a stablecoin marker on the
Provenance Blockchain.

## Assumptions

This README assumes you are familiar with writing and deploying smart contract to the
[provenance](https://docs.provenance.io/) blockchain.
See the `provwasm` [tutorial](https://github.com/provenance-io/provwasm/blob/main/docs/tutorial/01-overview.md)
for details.

## Blockchain Quickstart

Checkout provenance v1.11.0, install the `provenanced` command and start a 4-node localnet.

```bash
git clone https://github.com/provenance-io/provenance.git
cd provenance && git checkout v1.10.0
make install
make localnet-start
```

## Accounts

Accounts needs to be set up for example users and member banks.

User 1

```bash
provenanced keys add user1 \
    --home build/node0 --keyring-backend test --testnet --hd-path "44'/1'/0'/0/0" --output json | jq

{
  "name": "user1",
  "type": "local",
  "address": "tp10nnm70y8zc5m8yje5zx5canyqq639j3ph7mj8p",
  "pubkey": "tppub1addwnpepqf4feq9n484c6tvpcugkp0l78mffld8aphq8wqehx53pekcf2l5pkuajggq",
  "mnemonic": "seminar tape camp attract student make hollow pyramid obtain bamboo exit donate dish drip text foil news film assist access pride decline reason lonely"
}
```

User 2

```bash
provenanced keys add user2 \
    --home build/node0 --keyring-backend test --testnet --hd-path "44'/1'/0'/0/0" --output json | jq

{
  "name": "user2",
  "type": "local",
  "address": "tp1m4arun5y9jcwkatq2ey9wuftanm5ptzsg4ppfs",
  "pubkey": "tppub1addwnpepqgw8y7dpx4xmlaun5u55qrq4e05jtul6nu94afq3tvr7e8d4xx6ujzf79jz",
  "mnemonic": "immense ordinary august exclude loyal expire install tongue ski bounce sock buffalo range begin glory inch index float medal kid empty wheel badge find"
}
```

Bank 1

```bash
provenanced keys add bank1 \
    --home build/node0 --keyring-backend test --testnet --hd-path "44'/1'/0'/0/0" --output json | jq

{
  "name": "bank1",
  "type": "local",
  "address": "tp1fcfsfs847rneyaq93hz73m0wvudhktu9njtkfa",
  "pubkey": "tppub1addwnpepqv8g0u9s6rw5cp6540an0qj9h07r0wwy04ke76pmmyf2f5rj5m3qjex3mew",
  "mnemonic": "boy license night tide vanish alone stumble eye oak cabbage erosion route scan worry subject bench flee pottery patrol bomb butter veteran share arrange"
}
```

Bank 2

```bash
provenanced keys add bank2 \
    --home build/node0 --keyring-backend test --testnet --hd-path "44'/1'/0'/0/0" --output json | jq

{
  "name": "bank2",
  "type": "local",
  "address": "tp145r6nt64rw2rr58r80chp70ejdyqenszpg4d47",
  "pubkey": "tppub1addwnpepqte24nzxlwt5wg32fu5hwe7l3g2vq60034kdut0l3yp7g2wzq8d6zvpgan9",
  "mnemonic": "normal sting camera animal sport betray emerge inquiry excuse tornado much bean clown lawn present purse share dynamic punch oppose cheap quote over move"
}
```

If you want to use the addresses from this document, use the mnemonics above to restore the keys
locally.

For example:

```bash
provenanced keys add user1 --recover \
    --home build/node0 --keyring-backend test --testnet --hd-path "44'/1'/0'/0/0"
```

## Fee Payment

Fund the example accounts with `nhash` to pay network fees.

```bash
provenanced tx bank send \
    $(provenanced keys show -a node0 --home build/node0 --keyring-backend test --testnet) \
    $(provenanced keys show -a user1 --home build/node0 --keyring-backend test --testnet) \
    100000000000nhash \
    --from node0 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json  | jq
```

```bash
provenanced tx bank send \
    $(provenanced keys show -a node0 --home build/node0 --keyring-backend test --testnet) \
    $(provenanced keys show -a user2 --home build/node0 --keyring-backend test --testnet) \
    100000000000nhash \
    --from node0 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json  | jq
```

```bash
provenanced tx bank send \
    $(provenanced keys show -a node0 --home build/node0 --keyring-backend test --testnet) \
    $(provenanced keys show -a bank1 --home build/node0 --keyring-backend test --testnet) \
    100000000000nhash \
    --from node0 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json  | jq
```

```bash
provenanced tx bank send \
    $(provenanced keys show -a node0 --home build/node0 --keyring-backend test --testnet) \
    $(provenanced keys show -a bank2 --home build/node0 --keyring-backend test --testnet) \
    100000000000nhash \
    --from node0 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

## KYC Attributes

Add the unrestricted base name: `kyc.pb`.

```bash
provenanced tx name bind \
    "kyc" \
    $(provenanced keys show -a node0 --home build/node0 --keyring-backend test --testnet) \
    "pb" \
    --restrict=false \
    --from node0 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

Add the restricted name: `bank1.kyc.pb`.

```bash
provenanced tx name bind \
    "bank1" \
    $(provenanced keys show -a bank1 --home build/node0 --keyring-backend test --testnet) \
    "kyc.pb" \
    --from bank1 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

Add the restricted name: `bank2.kyc.pb`.

```bash
provenanced tx name bind \
    "bank2" \
    $(provenanced keys show -a bank2 --home build/node0 --keyring-backend test --testnet) \
    "kyc.pb" \
    --from bank2 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

Add a `bank1.kyc.pb` attribute to the `user1` account. This simulates `user1` going through the
KYC process for `bank1`.

```bash
provenanced tx attribute add \
    "bank1.kyc.pb" \
    $(provenanced keys show -a user1 --home build/node0 --keyring-backend test --testnet) \
    "string" \
    "ok" \
    --from bank1 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

Add a `bank2.kyc.pb`attribute to the `user2` account. This simulates `user2` going through the
KYC process for `bank2`.

```bash
provenanced tx attribute add \
    "bank2.kyc.pb" \
    $(provenanced keys show -a user2 --home build/node0 --keyring-backend test --testnet) \
    "json" \
    '{"status":"pass"}' \
    --from bank2 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

NOTE: The attribute value/type doesn't matter to the smart contract. It only checks for the
existence of the attribute on accounts.

## Store the Consortium Wasm

Store the optimized smart contract Wasm on-chain. This assumes you've copied `artifacts/dcc.wasm`
to the provenance root dir (ie where the localnet was started from).

```bash
provenanced tx wasm store dcc.wasm \
    --instantiate-only-address $(provenanced keys show -a node0 --keyring-backend test --home build/node0 --testnet) \
    --from node0 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 1.4 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

## Instantiate the Consortium

Instantiate the contract with the following params:

- Denom: `usdf.local`

```bash
provenanced tx wasm instantiate 1 '{"denom":"usdf.local"}' \
    --admin $(provenanced keys show -a node0 --keyring-backend test --home build/node0 --testnet) \
    --label dcc_poc_v1 \
    --from node0 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

At this point, we have an empty consortium.

## AuthZ Grants

Before we proceed, we need to add grants so the DCC smart contract has permission move restricted
marker tokens out of user/bank accounts.

Grant for bank 1

```bash
provenanced tx marker grant-authz \
    tp14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s96lrg8 \
    "transfer" \
    --transfer-limit 50000000usdf.local \
    --from bank1 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

Grant for bank 2

```bash
provenanced tx marker grant-authz \
    tp14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s96lrg8 \
    "transfer" \
    --transfer-limit 50000000usdf.local \
    --from bank2 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

Grant for user 1

```bash
provenanced tx marker grant-authz \
    tp14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s96lrg8 \
    "transfer" \
    --transfer-limit 50000000usdf.local \
    --from user1 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

Grant for user 2

```bash
provenanced tx marker grant-authz \
    tp14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s96lrg8 \
    "transfer" \
    --transfer-limit 50000000usdf.local \
    --from user2 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

With grants in place, we can now start adding members to the consortium.

## Bootstrap the Consortium

Right now, there are zero members in the consortium. We will need to bootstrap the first member
using the contract administrator.

First, have admin add `bank1` to the consortium.

```bash
provenanced tx wasm execute \
    tp14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s96lrg8 \
    '{"join":{"id":"tp1fcfsfs847rneyaq93hz73m0wvudhktu9njtkfa","name":"Bank 1","kyc_attrs":["bank1.kyc.pb"]}}' \
    --from node0 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

Query the members to see the single `bank1` is now available.

```bash
provenanced query wasm contract-state smart tp14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s96lrg8 \
   '{"get_members": {}}' \
   --ascii \
   -o json \
   --chain-id chain-local -t | jq
```

There is now a single member in the consortium.

## Add a Second Member

Then, have admin add `bank2` to the consortium.

```bash
provenanced tx wasm execute \
    tp14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s96lrg8 \
    '{"join":{"id":"tp145r6nt64rw2rr58r80chp70ejdyqenszpg4d47","name":"Bank 2","kyc_attrs":["bank2.kyc.pb"]}}' \
    --from node0 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

Query the members to see both `bank1` and `bank2` are now available.

```bash
provenanced query wasm contract-state smart tp14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s96lrg8 \
   '{"get_members": {}}' \
   --ascii \
   -o json \
   --chain-id chain-local -t | jq
```

## Mint

Let's assume `user1` has sent $100 to `bank1` and wants `usdf.local` in return.
The required tokens can be minted and withdrawn directly to the `user1` account.

```bash
provenanced tx wasm execute \
    tp14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s96lrg8 \
    '{"mint":{"amount":"10000","address":"tp10nnm70y8zc5m8yje5zx5canyqq639j3ph7mj8p"}}' \
    --from bank1 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

NOTE: you can get the address for `user1` with the following command:

```bash
provenanced keys show -a user1 --home build/node0 -t
```

You can now see `user1` holds `usdf.local`.

```bash
provenanced q bank balances tp10nnm70y8zc5m8yje5zx5canyqq639j3ph7mj8p -t -o json | jq
{
  "balances": [
    {
      "denom": "nhash",
      "amount": "100000000000"
    },
    {
      "denom": "usdf.local",
      "amount": "10000"
    }
  ],
  "pagination": {
    "next_key": null,
    "total": "0"
  }
}
```

## Transfer

Let's say `user1` owes `user2` $50. They can transfer the tokens through the smart contract.
NOTE: This is possible because both users have kyc attributes supported by the consortium.

```bash
provenanced tx wasm execute \
    tp14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s96lrg8 \
    '{"transfer":{"amount":"5000","recipient":"tp1m4arun5y9jcwkatq2ey9wuftanm5ptzsg4ppfs"}}' \
    --from user1 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

NOTE: you can get the address for `user2` with the following command:

```bash
provenanced keys show -a user2 --home build/node0 -t
```

You can now see `user2` holds `usdf.local`.

```bash
provenanced q bank balances tp1m4arun5y9jcwkatq2ey9wuftanm5ptzsg4ppfs -t -o json | jq
{
  "balances": [
    {
      "denom": "nhash",
      "amount": "100000000000"
    },
    {
      "denom": "usdf.local",
      "amount": "5000"
    }
  ],
  "pagination": {
    "next_key": null,
    "total": "0"
  }
}
```

## Redeem

Let's now say `user2` wants to redeem their tokens for cash/fiat at `bank2`. They first transfer
the tokens to `bank2`.

```bash
provenanced tx wasm execute \
    tp14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s96lrg8 \
    '{"transfer":{"amount":"5000","recipient":"tp145r6nt64rw2rr58r80chp70ejdyqenszpg4d47"}}' \
    --from user2 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

NOTE: you can get the address for `bank2` with the following command:

```bash
provenanced keys show -a bank2 --home build/node0 -t
```

You can now see that `bank2` holds $50 of `usdf.local`.

```bash
provenanced q bank balances tp145r6nt64rw2rr58r80chp70ejdyqenszpg4d47 -t -o json | jq
{
  "balances": [
    {
      "denom": "nhash",
      "amount": "98000000000"
    },
    {
      "denom": "usdf.local",
      "amount": "5000"
    }
  ],
  "pagination": {
    "next_key": null,
    "total": "0"
  }
}
```

The `usdf.local` can be burned by `bank2` or transferred to another address.

## Burn

Let's say `bank1` wants to reduce their supply of `usdf.local` held. Members can burn their tokens,
but only the amount they currently hold in their account.

So, before burn, `user1` redeems their `usdf.local` for cash/fiat by transferring those tokens
to `bank1`.

```bash
provenanced tx wasm execute \
    tp14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s96lrg8 \
    '{"transfer":{"amount":"5000","recipient":"tp1fcfsfs847rneyaq93hz73m0wvudhktu9njtkfa"}}' \
    --from user1 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

`bank1` can now burn its held `usdf.local`.

```bash
provenanced tx wasm execute \
    tp14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s96lrg8 \
    '{"burn":{"amount":"5000"}}' \
    --from bank1 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

The burned `usdf.local` is removed from circulation.

## Manage KYC attributes

Member or admin can add a kyc attribute to a member.

```bash
provenanced tx wasm execute \
    tp14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s96lrg8 \
    '{"add_kyc":{"kyc_attr":"bank1.omni.kyc.pb"}}' \
    --from bank1 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

Member or admin can remove a kyc attribute from a member.

```bash
provenanced tx wasm execute \
    tp14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s96lrg8 \
    '{"remove_kyc":{"kyc_attr":"bank1.omni.kyc.pb","id":"tp1fcfsfs847rneyaq93hz73m0wvudhktu9njtkfa"}}' \
    --from node0 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

## Upgrade the Consortium Wasm

If there are code modifications to the consortium wasm, the contract logic needs to be updated on chain. Perform
the step in the [Store the Consortium Wasm](#-store-the-consortium-wasm) section and then perform a migration of the wasm:

```bash
provenanced tx wasm migrate \
    tp14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s96lrg8 \
    2 \
    '{}' \
    --from node0 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet
```

Note in this example that `2` is the new code id that was the output of the store command. Replace that with whatever
code id is returned when you store the updated contract.

## Set Admin

Administrator can be reassigned to another key pair.

Change the administrator in smart contract state.

```bash
provenanced tx wasm execute \
    tp14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s96lrg8 \
    '{"set_admin":{"id":"tp1tqs43pw9ql44y24kx3sf9lzlanjafxydqx8ehf"}}' \
    --from node0 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

Change the smart contract administrator. This can be the same or a different key pair than smart contract state admin.

```bash
provenanced tx wasm set-contract-admin \
    tp14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s96lrg8 \
    tp1tqs43pw9ql44y24kx3sf9lzlanjafxydqx8ehf \
    --from node0 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

## Manage Executors

Admin can add executors to authorize other smart contracts to transfer coin on signer's behalf via smart contract to smart contract
requests.

```bash
provenanced tx wasm execute \
    tp14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s96lrg8 \
    '{"add_executor":{"id":"tp1nc5tatafv6eyq7llkr2gv50ff9e22mnf70qgjlv737ktmt4eswrqf06p2p"}}' \
    --from node0 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

Admin can also remove executor

```bash
provenanced tx wasm execute \
    tp14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s96lrg8 \
    '{"remove_executor":{"id":"tp1nc5tatafv6eyq7llkr2gv50ff9e22mnf70qgjlv737ktmt4eswrqf06p2p"}}' \
    --from node0 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```
