# Digital Currency Consortium (DCC) Smart Contact

## Status

Alpha: this smart contract has been verified on a Provenance localnet.

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
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
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
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
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
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet | jq
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
    --testnet | jq
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
    --testnet | jq
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
    --testnet | jq
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
    --testnet | jq
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
    --testnet | jq
```

NOTE: The attribute value/type doesn't matter to the smart contract. It only checks for the
existence of the attribute on accounts.

## Store the Consortium Wasm

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
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

## Instantiate the Consortium

Instantiate the contract with the following params:

- Denom: `usdf.local`
- Quorum Percent: `1%`
- Vote Duration: `10000 blocks`
- KYC Attributes: [`bank1.kyc.pb`, `bank2.kyc.pb`]

```bash
provenanced tx wasm instantiate 1 '{"dcc_denom":"usdf.local","quorum_pct":"0.01","vote_duration":"10000","kyc_attrs":["bank1.kyc.pb","bank2.kyc.pb"]}' \
    --admin $(provenanced keys show -a node0 --keyring-backend test --home build/node0 --testnet) \
    --label dcc_poc_v1 \
    --from node0 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

At this point, we have an empty consortium. We can now start adding members.

## Bootstrap the Consortium

Right now, there are zero members in the consortium. We will need to bootstrap the first member
using the contract administrator.

First, create a proposal to join the consortium as `bank1`.

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"join":{"denom":"bank1.coin","max_supply":"50000000","name":"Bank 1"}}' \
    --from bank1 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

Query the join proposals to get the proposal id to vote on

```bash
provenanced query wasm contract-state smart tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
   '{"get_join_proposals": {}}' \
   --ascii \
   -o json \
   --chain-id chain-local -t | jq
```

Vote 'yes' as the admin user.

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"vote":{"id":"tp1fcfsfs847rneyaq93hz73m0wvudhktu9njtkfa","choice":"yes"}}' \
    --from node0 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
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
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

There is now a single voting member in the consortium.

## Add a Second Member

Create a proposal to join the consortium as `bank2`.

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"join":{"denom":"bank2.coin","max_supply":"50000000","name":"Bank 2"}}' \
    --from bank2 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

Vote 'yes' as the existing member, `bank1`.

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"vote":{"id":"tp145r6nt64rw2rr58r80chp70ejdyqenszpg4d47","choice":"yes"}}' \
    --from bank1 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
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
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

Query to get the members' state.

```bash
provenanced query wasm contract-state smart tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
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
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"mint":{"amount":"10000","address":"tp10nnm70y8zc5m8yje5zx5canyqq639j3ph7mj8p"}}' \
    --from bank1 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet | jq
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
NOTE: This is possible because both users have kyc attributes supported by the consortium.

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"transfer":{"amount":"5000","recipient":"tp1m4arun5y9jcwkatq2ey9wuftanm5ptzsg4ppfs"}}' \
    --from user1 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet | jq
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
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"transfer":{"amount":"5000","recipient":"tp145r6nt64rw2rr58r80chp70ejdyqenszpg4d47"}}' \
    --from user2 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

NOTE: you can get the address for `bank2` with the following command:

```bash
provenanced keys show -a bank2 --home build/node0 -t
```

Then, `bank2` can then redeem the `usdf.local` tokens with the smart contract.

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"redeem":{"amount":"5000"}}' \
    --from bank2 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

You can now see that `bank2` holds reserve tokens minted by `bank1`, since these were the only
reserve tokens available for redemption.

```bash
provenanced q bank balances tp145r6nt64rw2rr58r80chp70ejdyqenszpg4d47 -t -o json | jq
{
  "balances": [
    {
      "denom": "bank1.coin",
      "amount": "5000"
    },
    {
      "denom": "nhash",
      "amount": "97500000000"
    },
    {
      "denom": "usdf.local",
      "amount": "0"
    }
  ],
  "pagination": {
    "next_key": null,
    "total": "0"
  }
}
```

Now, `bank2` can deliver the cash/fiat to `user2` (off chain process). In addition, `bank2` can
request the debt from `bank1` be paid (TODO: member transfer). They can also sit on the reserve
tokens and swap them for `usdf.local` when another user provides cash/fiat.

## Swap

Let's say `user2` now wants $25 worth of tokens back from `bank2`. The bank can then swap the
`bank1.coin` they hold back out for `usdf.local`, minting and withdrawing the tokens directly to
`user2`.

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"swap":{"amount":"2500","denom":"bank1.coin","address":"tp1m4arun5y9jcwkatq2ey9wuftanm5ptzsg4ppfs"}}' \
    --from bank2 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

You can now see that `user2` holds the minted `usdf.local`.

```bash
provenanced q bank balances tp1m4arun5y9jcwkatq2ey9wuftanm5ptzsg4ppfs -t -o json | jq
{
  "balances": [
    {
      "denom": "nhash",
      "amount": "99000000000"
    },
    {
      "denom": "usdf.local",
      "amount": "2500"
    }
  ],
  "pagination": {
    "next_key": null,
    "total": "0"
  }
}
```

## Burn

Let's say `bank1` wants to reduce their supply of reserve tokens. Members can burn their tokens,
but only the amount they currently hold in their account.

So, before burn, `user1` redeems their `usdf.local` for cash/fiat by transferring those tokens
to `bank1`.

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"transfer":{"amount":"5000","recipient":"tp1fcfsfs847rneyaq93hz73m0wvudhktu9njtkfa"}}' \
    --from user1 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
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
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
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
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

NOTE: Members can't burn another member's tokens. For example, `bank2` cannot burn the `bank1`
tokens they currently hold.

## Manage KYC attributes

To add an attribute

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"add_kyc":{"name":"bank3.kyc.pb"}}' \
    --from node0 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

To remove the attribute

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"remove_kyc":{"name":"bank3.kyc.pb"}}' \
    --from node0 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

## Cancel Join Proposal

Let's say a new member, `bank3` wants to join.

```bash
provenanced keys add bank3 \
    --home build/node0 --keyring-backend test --testnet --hd-path "44'/1'/0'/0/0" --output json | jq

{
  "name": "bank3",
  "type": "local",
  "address": "tp1zl388azlallp5rygath0kmpz6w2agpampukfc3",
  "pubkey": "tppub1addwnpepqfc3mt4yatqer39yqg4t3xqnhrl3mn5yr2uya49dqr0hp5fuhp82g4g6lev",
  "mnemonic": "pond rebuild kick kitten taxi region burst people sadness man news young evil lemon decrease vault always daring dignity either van mandate celery taste"
}
```

NOTE: Again, the mnemonic above can be used to recover the `bank3` keys, for use below.

Let's give `bank3` hash for network fees.

```bash
provenanced tx bank send \
    $(provenanced keys show -a node0 --home build/node0 --keyring-backend test --testnet) \
    $(provenanced keys show -a bank3 --home build/node0 --keyring-backend test --testnet) \
    100000000000nhash \
    --from node0 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

They then add a proposal to join.

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"join":{"denom":"bank3.coin","max_supply":"100000000"}}' \
    --from bank3 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

But, both existing members vote 'no' because the `max_supply` is too big (shrug). This eliminates
any chance of `bank3` being able to join.

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"vote":{"id":"tp1zl388azlallp5rygath0kmpz6w2agpampukfc3","choice":"no"}}' \
    --from bank1 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"vote":{"id":"tp1zl388azlallp5rygath0kmpz6w2agpampukfc3","choice":"no"}}' \
    --from bank2 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

The proposal can be queried to see the 'no' votes.

```bash
provenanced query wasm contract-state smart tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
   '{"get_join_proposal": {"id":"tp1zl388azlallp5rygath0kmpz6w2agpampukfc3"}}' \
   --ascii \
   -o json \
   --chain-id chain-local -t | jq

{
  "data": {
    "id": "tp1zl388azlallp5rygath0kmpz6w2agpampukfc3",
    "max_supply": "100000000",
    "denom": "bank3.coin",
    "created": "165",
    "expires": "10165",
    "no": "1000000",
    "yes": "0",
    "voters": [
      "tp1fcfsfs847rneyaq93hz73m0wvudhktu9njtkfa",
      "tp145r6nt64rw2rr58r80chp70ejdyqenszpg4d47"
    ]
  }
}
```

The rejected proposal can then be cancelled by `bank3`

```bash
provenanced tx wasm execute \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    '{"cancel":{}}' \
    --from bank3 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto --gas-prices 1905nhash --gas-adjustment 2 \
    --broadcast-mode block \
    --yes \
    --testnet | jq
```

## Upgrade the Consortium Wasm

If there are code modications to the consortium wasm, the contract logic needs to be updated on chain. Perform
the step in the [Store the Consortium Wasm](#-store-the-consortium-wasm) section and then perform a migration of the wasm:

```bash
provenanced tx wasm migrate \
    tp18vd8fpwxzck93qlwghaj6arh4p7c5n89x8kskz \
    2 \
    '{}' \
    --from node0 \
    --keyring-backend test \
    --home build/node0 \
    --chain-id chain-local \
    --gas auto \
    --fees 500000000nhash \
    --broadcast-mode block \
    --yes \
    --testnet

```

Note in this example that `2` is the new code id that was the output of the store command. Replace that with whatever
code id is returned when you store the updated contract.