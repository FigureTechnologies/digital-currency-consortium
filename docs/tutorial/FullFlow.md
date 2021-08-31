# Mint/Burn Process Flow
This document describes the steps needed to perform the full process 
flow in your local environment to test minting/redeeming/burning of digital currency. 
These are the steps from a business perspective:

1. An end user registers for a bank account at your bank; it is associated with an address/wallet. You need to register
   that address with the DCC middleware.
2. The end user sends fiat to their bank account that you registered in step 1. You need to tell the DCC middleware
   to mint coin to that address. The DCC calls a smart contract that mints both the bank's denom and the digital currency 
   denom, and transfers the digital currency denom to the end user's address. The bank's denom stays in the reserve.
3. The end user wants to redeem coin for fiat. The smart contract transfers the digital currency denom to the bank's 
   member id. The DCC middleware detects the transfer and redeems the digital currency denom through a smart contract. 
   The smart contract burns the digital currency denom and transfers the bank's denom from the reserve to the bank. 
   The DCC middleware calls a smart contract to burn the bank's denom and then calls the bank middleware to tell 
   it to send fiat to the end user's bank account registered in step 1.
   
## Process Details (Corresponding to the steps above)
### 1. Create Key and Register
From your provenance container:
```
provenanced keys add user1 --testnet --hd-path "44'/1'/0'/0/0'" --output json
```

This should be persisted in your local storage because you will need to pass the DCC middleware a UUID associated with the 
address. This UUID will be used for all future mint/burn operations.

```
curl -i \
   -XPOST \
   -H"Content-type: application/json" \
   -H"Accepts: application/json" \
   http://localhost:8080/digital-currency-consortium/api/v1/registrations \
    -d '{ "bankAccountUuid": "YOUR_PERSISTED_UUID", "blockchainAddress": "ADDRESS_FROM_STEP_1" }'
```

### 2. Mint to the address
Pass in a uniquely generated UUID; this is used for tracking purposes. You'll reference the bank account uuid that
you registered the address to in step 1. The amount is max 12 digits before the decimal point and 2 digits after the
decimal point.

```
curl -i \
   -XPOST \
   -H"Content-type: application/json" \
   -H"Accepts: application/json" \
   http://localhost:8080/digital-currency-consortium/api/v1/mints \
    -d '{ "uuid": "A_UNIQUE_TRACKING_ID", "bankAccountUuid": "YOUR_PERSISTED_UUID", "amount": "2000.00" }'
```

When this completes, you'll receive a call back to your URL `/nycb/api/v1/mints/complete/{uuid}` with the `uuid` value
you passed in to the request.

### 3. As the end user, request to get fiat (redeem coin)
From your provenance container:
```
provenanced tx wasm execute \
     tp15qkf84uddc0zjpkjrn64rln75w40plrtcq4kp3 \
     '{"transfer":{"amount":"10000","recipient":"YOUR_BANK_ADDRESS"}}' \
     --from user1  \
     --chain-id pio-testnet-1 \
     --node=tcp://rpc-0.test.provenance.io:26657 \
     --gas auto \
     --fees 300000000nhash \
     --broadcast-mode block \
     --yes \
     --testnet
```

If this step fails, it will likely be due to lack of gas and you'll need to ask someone to send gas to the address 
generated in step 1.

Once this request is successful, the redeem and burn happen hopefully within 10 seconds, and you will receive a 
call back to your URL `/nycb/api/v1/fiat/deposits` to transfer the funds to the end user's account.