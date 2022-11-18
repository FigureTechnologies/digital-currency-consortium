# Digital Currency Consortium (DCC) Sale Smart Contact

This contract is used for the bilateral settlement of an asset against a DCC token on the Provenance Blockchain.

## Store the Sale Wasm

Store the optimized smart contract Wasm on-chain. This assumes you've copied `artifacts/dcc_sale.wasm`
to the provenance root dir (ie where the localnet was started from).

```bash
provenanced tx wasm store dcc_sale.wasm \
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

## Instantiate the Sale Smart Contract

Instantiate the contract with the following params:

- DCC Address: `tp14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s96lrg8`
- DCC Denom: `usdf.local`

```bash
provenanced tx wasm instantiate 2 '{"dcc_address":"tp14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s96lrg8","dcc_denom":"usdf.local"}' \
    --admin $(provenanced keys show -a node0 --keyring-backend test --home build/node0 --testnet) \
    --label dcc_sale_poc_v1 \
    --from node0 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

## Add Executor to DCC Smart Contract

Admin of DCC smart contract must add the smart contract address to list of allowable executors so that this instance can complete
sales by making transfer calls on behalf of buyer.

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

## Create Sale

Let's assume `user1` wants to sell 1 `hash` for 1,000 `usdf.local` to `user2`.

```bash
provenanced tx wasm execute \
    tp1nc5tatafv6eyq7llkr2gv50ff9e22mnf70qgjlv737ktmt4eswrqf06p2p \
    '{"create_sale":{"id":"b476aca6-bd69-4e54-b123-614b116321d3","price":{"amount":"1000","denom":"usdf.local"},"buyer":"tp1m4arun5y9jcwkatq2ey9wuftanm5ptzsg4ppfs"}}' \
    --amount '1000000000nhash' \
    --from user1 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

Query the sale proposal to see the order is now available and in a pending status.

```bash
provenanced query wasm contract-state smart tp1nc5tatafv6eyq7llkr2gv50ff9e22mnf70qgjlv737ktmt4eswrqf06p2p \
    '{"get_sale":{"id":"b476aca6-bd69-4e54-b123-614b116321d3"}}' \
    --ascii \
    -o json \
    --chain-id chain-local -t | jq
{
  "data": {
    "id": "b476aca6-bd69-4e54-b123-614b116321d3",
    "asset": {
      "denom": "nhash",
      "amount": "1000000000"
    },
    "owner": "tp10nnm70y8zc5m8yje5zx5canyqq639j3ph7mj8p",
    "buyer": "tp1m4arun5y9jcwkatq2ey9wuftanm5ptzsg4ppfs",
    "price": {
      "denom": "usdf.local",
      "amount": "1000"
    },
    "status": "pending"
  }
}
```

The 1 `hash` is also escrowed at the smart contract address.

```bash
provenanced q bank balances tp1nc5tatafv6eyq7llkr2gv50ff9e22mnf70qgjlv737ktmt4eswrqf06p2p -t -o json | jq
{
  "balances": [
    {
      "denom": "nhash",
      "amount": "1000000000"
    }
  ],
  "pagination": {
    "next_key": null,
    "total": "0"
  }
}
```

## Complete Sale

Buyer as `user2` can now complete the sale.

```bash
provenanced tx wasm execute \
    tp1nc5tatafv6eyq7llkr2gv50ff9e22mnf70qgjlv737ktmt4eswrqf06p2p \
    '{"complete_sale":{"id":"b476aca6-bd69-4e54-b123-614b116321d3"}}' \
    --from user2 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

The sale proposal should now be complete.

```bash
provenanced query wasm contract-state smart tp1nc5tatafv6eyq7llkr2gv50ff9e22mnf70qgjlv737ktmt4eswrqf06p2p \
    '{"get_sale":{"id":"b476aca6-bd69-4e54-b123-614b116321d3"}}' \
    --ascii \
    -o json \
    --chain-id chain-local -t | jq
{
  "data": {
    "id": "b476aca6-bd69-4e54-b123-614b116321d3",
    "asset": {
      "denom": "nhash",
      "amount": "1000000000"
    },
    "owner": "tp10nnm70y8zc5m8yje5zx5canyqq639j3ph7mj8p",
    "buyer": "tp1m4arun5y9jcwkatq2ey9wuftanm5ptzsg4ppfs",
    "price": {
      "denom": "usdf.local",
      "amount": "1000"
    },
    "status": "complete"
  }
}
```

The 1 `hash` is no longer escrowed at the smart contract address and the asset bilateral exchange is complete.

```bash
provenanced q bank balances tp1nc5tatafv6eyq7llkr2gv50ff9e22mnf70qgjlv737ktmt4eswrqf06p2p -t -o json | jq
{
  "balances": [],
  "pagination": {
    "next_key": null,
    "total": "0"
  }
}
```

## Cancel Sale

A seller can cancel a sale proposal at any time prior to sale completion.

Let's have `user1` create another sale proposal with a different `id`.

```bash
provenanced tx wasm execute \
    tp1nc5tatafv6eyq7llkr2gv50ff9e22mnf70qgjlv737ktmt4eswrqf06p2p \
    '{"create_sale":{"id":"81a669b9-2dc1-40c5-b293-0c9efad158c8","price":{"amount":"1000","denom":"usdf.local"},"buyer":"tp1m4arun5y9jcwkatq2ey9wuftanm5ptzsg4ppfs"}}' \
    --amount '1000000000nhash' \
    --from user1 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

Then, let's have `user1` cancel it.

```bash
provenanced tx wasm execute \
    tp1nc5tatafv6eyq7llkr2gv50ff9e22mnf70qgjlv737ktmt4eswrqf06p2p \
    '{"cancel_sale":{"id":"81a669b9-2dc1-40c5-b293-0c9efad158c8"}}' \
    --from user1 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet -o json | jq
```

The sale proposal should be canceled.

```bash
provenanced query wasm contract-state smart tp1nc5tatafv6eyq7llkr2gv50ff9e22mnf70qgjlv737ktmt4eswrqf06p2p \
    '{"get_sale":{"id":"81a669b9-2dc1-40c5-b293-0c9efad158c8"}}' \
    --ascii \
    -o json \
    --chain-id chain-local -t | jq
{
  "data": {
    "id": "81a669b9-2dc1-40c5-b293-0c9efad158c8",
    "asset": {
      "denom": "nhash",
      "amount": "1000000000"
    },
    "owner": "tp10nnm70y8zc5m8yje5zx5canyqq639j3ph7mj8p",
    "buyer": "tp1m4arun5y9jcwkatq2ey9wuftanm5ptzsg4ppfs",
    "price": {
      "denom": "usdf.local",
      "amount": "1000"
    },
    "status": "canceled"
  }
}
```

The 1 `hash` is no longer escrowed at the smart contract address and was returned to the seller.

```bash
provenanced q bank balances tp1nc5tatafv6eyq7llkr2gv50ff9e22mnf70qgjlv737ktmt4eswrqf06p2p -t -o json | jq
{
  "balances": [],
  "pagination": {
    "next_key": null,
    "total": "0"
  }
}
```

## Upgrade the Sale Wasm

If there are code modifications to the consortium wasm, the contract logic needs to be updated on chain. Perform
the step in the [Store the Sale Wasm](#-store-the-sale-wasm) section and then perform a migration of the wasm:

```bash
provenanced tx wasm migrate \
    tp1nc5tatafv6eyq7llkr2gv50ff9e22mnf70qgjlv737ktmt4eswrqf06p2p \
    3 \
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

Note in this example that `3` is the new code id that was the output of the store command. Replace that with whatever
code id is returned when you store the updated contract.
