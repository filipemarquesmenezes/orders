#!/usr/bin/env bash

# Exercises the complete Orders gRPC lifecycle against a running service.
# Usage: ./scripts/test-orders-grpc.sh [host:port]
# Example: ./scripts/test-orders-grpc.sh localhost:9090

set -euo pipefail

ADDRESS="${1:-localhost:9090}"
GRPCURL_BIN="${GRPCURL_BIN:-grpcurl}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

require_command "$GRPCURL_BIN"
require_command jq

call() {
  "$GRPCURL_BIN" -plaintext -d "$1" "$ADDRESS" "$2"
}

echo "Creating an order at $ADDRESS..."
created_order="$(call '{
  "userId": "42",
  "currencyCode": "USD",
  "items": [
    {
      "productId": "1001",
      "productName": "Notebook",
      "quantity": 2,
      "unitPriceCents": "325"
    }
  ]
}' 'com.system.orders.v1.Orders/CreateOrder')"

echo "$created_order" | jq .
order_id="$(echo "$created_order" | jq -er '.id')"

echo "Fetching order $order_id..."
call "{\"id\": \"$order_id\"}" 'com.system.orders.v1.Orders/GetOrder' | jq .

echo "Listing orders for user 42..."
call '{"userId": "42"}' 'com.system.orders.v1.Orders/ListOrders' | jq .

echo "Confirming order $order_id..."
call "{\"id\": \"$order_id\", \"status\": \"ORDER_STATUS_CONFIRMED\"}" \
  'com.system.orders.v1.Orders/UpdateOrderStatus' | jq .

echo "Cancelling order $order_id..."
cancelled_order="$(call "{\"id\": \"$order_id\"}" 'com.system.orders.v1.Orders/CancelOrder')"
echo "$cancelled_order" | jq .

final_status="$(echo "$cancelled_order" | jq -er '.status')"
if [[ "$final_status" != "ORDER_STATUS_CANCELLED" ]]; then
  echo "Expected ORDER_STATUS_CANCELLED, got $final_status" >&2
  exit 1
fi

echo "Orders gRPC smoke test passed."
