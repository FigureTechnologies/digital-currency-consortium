UNAME_M = $(shell uname -m)
ARCH = ''
ifeq ($(UNAME_M),arm64)
	ARCH='-arm64'
endif

.PHONY: all
all: fmt build test lint schema

.PHONY: fmt
fmt:
	@cargo fmt --all -- --check

.PHONY: build
build:
	@cargo wasm

.PHONY: test
test:
	@RUST_BACKTRACE=1 cargo unit-test

.PHONY: lint
lint:
	@cargo clippy

.PHONY: schema
schema:
	@cargo schema

.PHONY: clean
clean:
	@cargo clean

.PHONY: check
check:
	@cargo check

.PHONY: optimize
optimize:
	@docker run --rm -v $(CURDIR):/code \
		--mount type=volume,source=dcc_cache,target=/code/target \
		--mount type=volume,source=registry_cache,target=/usr/local/cargo/registry \
		cosmwasm/rust-optimizer$(ARCH):0.12.12
