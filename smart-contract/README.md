# Digital Currency Consortium (DCC) Smart Contact

## Status

This is a prototype and not ready for use on any network.

## Blockchain Setup

Checkout provenance v1.5.0, clear all existing state, install the `provenanced` command,
and start a 4-node localnet.

```bash
git checkout v1.5.0
make clean
make install
make localnet-start
```

## Accounts

Accounts needs to be set up for example users and member banks.

User 1

```bash
provenanced keys add user1 \
    --home build/node0 --keyring-backend test --testnet --hd-path "44'/1'/0'/0/0" --output json | jq
```

User 2

```bash
provenanced keys add user2 \
    --home build/node0 --keyring-backend test --testnet --hd-path "44'/1'/0'/0/0" --output json | jq
```

Bank 1

```bash
provenanced keys add bank1 \
    --home build/node0 --keyring-backend test --testnet --hd-path "44'/1'/0'/0/0" --output json | jq
```

Bank 2

```bash
provenanced keys add bank2 \
    --home build/node0 --keyring-backend test --testnet --hd-path "44'/1'/0'/0/0" --output json | jq
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
    --gas auto \
    --fees 200000000nhash \
    --broadcast-mode block \
    --yes \
    --testnet | jq
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
    --gas auto \
    --fees 200000000nhash \
    --broadcast-mode block \
    --yes \
    --testnet | jq
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
    --gas auto \
    --fees 200000000nhash \
    --broadcast-mode block \
    --yes \
    --testnet | jq
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
    --gas auto \
    --fees 200000000nhash \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

## Set up a shell consortium

Store the optimized smart contract Wasm on-chain. This assumes you've copied `artifacts/dcc.wasm`
to the provenance root dir (ie where the localnet was started from).

```bash
provenanced tx wasm store dcc.wasm \
    --source "https://github.com/provenance-io/digital-currency-consortium/smart-contract" \
    --builder "cosmwasm/rust-optimizer:0.11.5" \
    --instantiate-only-address $(provenanced keys show -a node0 --keyring-backend test --home build/node0 --testnet) \
    --from node0 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto \
    --fees 4000000000nhash \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

Instantiate the contract with the following params:

- Denom: `centiusdx`
- Quorum Percent: `10%`
- Vote Duration: `1000 blocks`
- KYC Attributes: `None` (TODO: Use kyc attributes in this example)

```bash
provenanced tx wasm instantiate 1 '{"dcc_denom":"centiusdx","quorum_pct":"0.1","vote_duration":"1000","kyc_attrs":[]}' \
    --admin $(provenanced keys show -a node0 --keyring-backend test --home build/node0 --testnet) \
    --label dcc_poc_v1 \
    --from node0 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto \
    --fees 500000000nhash \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

At this point, we have an empty consortium. We can now start adding members.

## Bootstrap the consortium

Create a proposal to join the consortium as `bank1`.

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"join":{"denom":"bank1.coin","max_supply":"50000000"}}' \
    --from bank1 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto \
    --fees 500000000nhash \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

Vote 'yes' as the admin user (required to onboard the first bank since there are no members yet).

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"vote":{"id":"tp1ye6ts5ffmvldy9a983el6uxcaw97aj0q4xl8yx","choice":"yes"}}' \
    --from node0 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto \
    --fees 500000000nhash \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

Accept membership as `bank1` (without minting any bank tokens).

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"accept":{}}' \
    --from bank1 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto \
    --fees 500000000nhash \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

## Add a second member bank

Create a proposal to join the consortium as `bank2`.

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"join":{"denom":"bank2.coin","max_supply":"50000000"}}' \
    --from bank2 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto \
    --fees 600000000nhash \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

Vote 'yes' as the existing member (`bank1`).

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"vote":{"id":"tp13p0rg6haf92al0s48fvvkls80y7r3f6hc6kpzg","choice":"yes"}}' \
    --from bank1 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto \
    --fees 500000000nhash \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

Accept membership as `bank2`

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"accept":{}}' \
    --from bank2 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto \
    --fees 500000000nhash \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

## Mint

Let's assume `user1` has sent $100 to `bank1` and wants `centiusdx` in return.
The required tokens can be minted and withdrawn directly to the `user1` account.

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"mint":{"amount":"10000","address":"tp1f3gv2yk8pfxg7nk2l7fu7275cjykey7k722chd"}}' \
    --from bank1 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto \
    --fees 500000000nhash \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

NOTE: you can get the address for `user1` with the following command:

```bash
provenanced keys show -a user1 --home build/node0 -t
```

You can now see `user1` holds `centiusdx`.

```bash
provenanced q bank balances tp1f3gv2yk8pfxg7nk2l7fu7275cjykey7k722chd -t -o json | jq

{
  "balances": [
    {
      "denom": "centiusdx",
      "amount": "10000"
    },
    {
      "denom": "nhash",
      "amount": "100000000000"
    }
  ],
  "pagination": {
    "next_key": null,
    "total": "0"
  }
}

```

You can also see that the minted reserve tokens were escrowed in the marker for `bank1`

```bash
provenanced q marker escrow "bank1.coin" -t -o json | jq

{
  "escrow": [
    {
      "denom": "bank1.coin",
      "amount": "10000"
    }
  ]
}
```

## Transfer

Let's say `user1` owes `user2` $50. They can transfer the tokens through the smart contract.
NOTE: There are no kyc attribute checks performed in this example (TODO).

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"transfer":{"amount":"5000","recipient":"tp1tw62cdtzvl3xq6343thh8zqfhy4l3jkjgejzsq"}}' \
    --from user1 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto \
    --fees 600000000nhash \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

NOTE: you can get the address for `user2` with the following command:

```bash
provenanced keys show -a user2 --home build/node0 -t
```

## Redeem

Let's now say `user2` wants to redeem their tokens for cash/fiat at `bank2`. They first transfer
the tokens to `bank2`.

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"transfer":{"amount":"5000","recipient":"tp13p0rg6haf92al0s48fvvkls80y7r3f6hc6kpzg"}}' \
    --from user2 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto \
    --fees 600000000nhash \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

NOTE: you can get the address for `bank2` with the following command:

```bash
provenanced keys show -a bank2 --home build/node0 -t
```

`bank2` can then redeem the tokens with the smart contract and receive `bank1.coin` tokens in
return (ie the only available bank reserves in escrow).

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"redeem":{"amount":"5000","reserve_denom":"bank1.coin"}}' \
    --from bank2 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto \
    --fees 500000000nhash \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

You can now see that `bank2` holds reserve tokens from `bank1`

```bash
provenanced q bank balances tp13p0rg6haf92al0s48fvvkls80y7r3f6hc6kpzg -t -o json | jq

{
  "balances": [
    {
      "denom": "bank1.coin",
      "amount": "5000"
    },
    {
      "denom": "centiusdx",
      "amount": "0"
    },
    {
      "denom": "nhash",
      "amount": "97900000000"
    }
  ],
  "pagination": {
    "next_key": null,
    "total": "0"
  }
}
```

Now, `bank2` can deliver the cash/fiat to `user2` (off chain process). In addition, `bank2` can
request the debt from `bank1` be paid (again, off chain). They can also sit on the reserve tokens
and swap them for `centiusdx` when another customer provides cash/fiat.

## Swap

Let's say some other user requests $25 from `bank2`. They can then swap the `bank1.coin` they hold
back out for `centiusdx`.

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"swap":{"amount":"2500","denom":"bank1.coin"}}' \
    --from bank2 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto \
    --fees 500000000nhash \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

You can now see that `bank2` holds the newly minted `centiusdx` in addition to the remaining reserve
tokens from `bank1`.

```bash
provenanced q bank balances tp13p0rg6haf92al0s48fvvkls80y7r3f6hc6kpzg -t -o json | jq

{
  "balances": [
    {
      "denom": "bank1.coin",
      "amount": "2500"
    },
    {
      "denom": "centiusdx",
      "amount": "2500"
    },
    {
      "denom": "nhash",
      "amount": "97400000000"
    }
  ],
  "pagination": {
    "next_key": null,
    "total": "0"
  }
}
```

The `centiusdx` could now be sent to the user.

## Burn

Let's say `bank1` wants to reduce their supply of reserve tokens. Members can burn their tokens,
but only the amount they currently hold in their account.

So, before burn, `user1` redeems their `centiusdx` for cash/fiat by transferring those tokens
to `bank1`.

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"transfer":{"amount":"5000","recipient":"tp1ye6ts5ffmvldy9a983el6uxcaw97aj0q4xl8yx"}}' \
    --from user1 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto \
    --fees 600000000nhash \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

They redeem for `bank1.coin` against the smart contract.

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"redeem":{"amount":"5000","reserve_denom":"bank1.coin"}}' \
    --from bank1 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto \
    --fees 500000000nhash \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

The reserve tokens are now held in the `bank1` member account. After delivering the cash/fiat to
`user1` (off chain), they can burn the reserve tokens.

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"burn":{"amount":"5000"}}' \
    --from bank1 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto \
    --fees 500000000nhash \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

Members can't burn another member's tokens. For example, `bank2` cannot burn the `bank1` tokens
they currently hold.
