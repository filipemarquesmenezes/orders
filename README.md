# Orders

A Java and Spring Boot service for creating, retrieving, listing, updating, and cancelling orders through a versioned gRPC API. Orders and their items are stored in an in-memory H2 database using Spring Data JPA.

The repository also includes a Docker Compose environment for AWS Emulator and an automatically initialized S3 bucket, `orders-test`. The application currently has no AWS SDK dependency or S3 integration; this environment is available for local experimentation and future development.

## Technology stack

| Technology | Role in this project |
| --- | --- |
| Java 27 | Application language and Gradle Java toolchain target. |
| Spring Boot 4.1.2 | Application startup, dependency injection, configuration, and development Compose integration. |
| Spring gRPC | Hosts the Orders gRPC service. |
| Protocol Buffers (proto3) | Defines the API contract and generates Java messages and gRPC stubs during the build. |
| Spring Data JPA / Hibernate | Repository implementation, transactions, entity mapping, and schema generation. |
| H2 | In-memory relational database for orders and order items. |
| Gradle 9.9.1 | Dependency management, code generation, build, and test execution through the included wrapper. |
| JUnit Jupiter | Integration tests that call a real gRPC server on a random port. |
| JaCoCo | HTML and XML coverage reports, excluding generated protobuf classes. |
| Docker Compose / AWS Emulator | Local HTTPS AWS endpoint with S3 bucket initialization. |
| Lombok | Configured as a compile-time dependency and annotation processor. |
| grpcurl / jq | Command-line gRPC smoke testing and JSON processing. |

Versions above reflect the repository configuration. Most library versions are managed by Spring Boot; the gRPC Java code-generation plugin is explicitly set to `1.84.1`.

## Prerequisites

- JDK 27, available to Gradle's Java toolchain discovery.
- Docker with Compose v2, with the Docker daemon running, for the local emulator.
- Internet access on the first build to download Gradle, dependencies, and container packages.
- Optional: `grpcurl` and `jq` for the smoke-test script; AWS CLI v2 for host-side emulator commands.

A system Gradle installation is unnecessary: use `./gradlew`. The optional `mise.toml` requests the latest Java release; make sure the installed JDK satisfies the project's explicit Java 27 toolchain requirement.

## Quick start

From the repository root:

```bash
mkdir -p .aws-emulator/9297
docker compose up -d --build
./gradlew bootRun
```

Compose builds AWS Emulator from the latest AWS package. The initialization service waits for its HTTPS health check, verifies account `000000000000`, and creates `orders-test` if it does not exist. An exited initialization container with exit code `0` is expected.

Spring Boot's Docker Compose development integration is also enabled, so `bootRun` can discover the root `compose.yaml` and manage its services. To run only the orders application without Compose integration:

```bash
SPRING_DOCKER_COMPOSE_ENABLED=false ./gradlew bootRun
```

The gRPC examples and smoke-test script use `localhost:9090`. To set the port explicitly:

```bash
SPRING_GRPC_SERVER_PORT=9090 SPRING_DOCKER_COMPOSE_ENABLED=false ./gradlew bootRun
```

The gRPC client examples use plaintext for local development. The AWS emulator uses a separate HTTPS endpoint on `https://127.0.0.1:9297`.

## gRPC API

Contract: [`src/main/proto/orders_service_v1.proto`](src/main/proto/orders_service_v1.proto)

Service name: `com.system.orders.v1.Orders`

| RPC | Behavior |
| --- | --- |
| `CreateOrder` | Creates an order with status `ORDER_STATUS_PENDING` and calculates its total. |
| `GetOrder` | Retrieves an order by ID. |
| `ListOrders` | Filters by `user_id`; `0` returns all orders. User-filtered results are sorted newest first; the unfiltered list has no explicit ordering. |
| `UpdateOrderStatus` | Assigns a concrete order status. |
| `CancelOrder` | Sets the order status to `ORDER_STATUS_CANCELLED`. |

Create an order using the local proto file, without depending on server reflection:

```bash
grpcurl -plaintext \
  -import-path src/main/proto -proto orders_service_v1.proto \
  -d '{"userId":"42","currencyCode":"USD","items":[{"productId":"1001","productName":"Notebook","quantity":2,"unitPriceCents":"325"}]}' \
  localhost:9090 com.system.orders.v1.Orders/CreateOrder
```

This request creates an order with `totalCents` equal to `650`. Use the returned ID to retrieve it:

```bash
grpcurl -plaintext \
  -import-path src/main/proto -proto orders_service_v1.proto \
  -d '{"id":"1"}' \
  localhost:9090 com.system.orders.v1.Orders/GetOrder
```

Replace `1` with the actual returned ID. Protobuf JSON represents 64-bit integers as strings.

### Validation and status behavior

- User and product IDs must be positive; an order needs at least one item.
- Product names must be nonblank, quantities positive, and unit prices nonnegative.
- Currency codes must contain exactly three ASCII letters and are normalized to uppercase. Validation checks the format, not membership in the ISO 4217 currency list.
- Monetary values use integer minor units in fields named `*_cents`. Totals are computed as the sum of quantity times unit price, with integer overflow detection.
- Supported statuses are `PENDING`, `CONFIRMED`, `SHIPPED`, `DELIVERED`, and `CANCELLED`, prefixed with `ORDER_STATUS_` in the protobuf API.
- Status updates do not enforce a transition sequence. For example, the current implementation permits changing a cancelled order back to pending.

Invalid requests and arithmetic overflow return gRPC `INVALID_ARGUMENT`. Unknown orders return `NOT_FOUND`; other runtime failures return `INTERNAL` with a generic message.

## Tests and build

```bash
# Run integration tests and generate coverage reports
./gradlew test

# Compile, test, and package the application
./gradlew build

# Regenerate protobuf Java messages and gRPC stubs
./gradlew generateProto
```

The integration tests cover the order lifecycle, user filtering, totals, invalid requests, and missing orders. They use a random gRPC port and clear the order repository before each test.

Reports:

- Test results: `build/reports/tests/test/index.html`
- Coverage: `build/reports/jacoco/test/html/index.html`
- Coverage XML: `build/reports/jacoco/test/jacocoTestReport.xml`

With the application running, exercise its lifecycle from the command line:

```bash
bash scripts/test-orders-grpc.sh
# Or use a different address:
bash scripts/test-orders-grpc.sh localhost:9091
```

This script requires `grpcurl`, `jq`, and server reflection because it does not pass a proto file. It creates, fetches, lists, confirms, and cancels an order, then checks the final cancellation status. `GRPCURL_BIN` can select a different grpcurl executable.

To run the packaged application without the development Compose integration:

```bash
java -jar build/libs/orders-1.0.jar
```

Start Compose separately if you need the AWS emulator alongside the packaged app.

## Local AWS / S3 environment

The emulator is published only on host loopback, with telemetry disabled. It checks signed requests using credentials generated for each run. The initializer shares the emulator's network namespace and trusts its generated CA certificate.

Useful commands:

```bash
docker compose ps -a
docker compose logs aws-emulator aws-emulator-init
docker compose stop

# Start again and rerun the bucket initializer
docker compose up -d aws-emulator
docker compose run --rm aws-emulator-init

# Remove the Compose containers and network
docker compose down
```

After a fresh clone, create `.aws-emulator/9297` before starting Compose so Docker does not create the bind-mount directory with unexpected ownership.

### Connect the AWS CLI from the host

The checked-in `.aws-emulator/9297.env` currently contains absolute paths from the original development machine. For a portable invocation, run the following subshell from the repository root:

```bash
(
  unset AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN \
    AWS_PROFILE AWS_DEFAULT_PROFILE AWS_WEB_IDENTITY_TOKEN_FILE AWS_ROLE_ARN \
    AWS_ROLE_SESSION_NAME AWS_CONTAINER_CREDENTIALS_RELATIVE_URI \
    AWS_CONTAINER_CREDENTIALS_FULL_URI AWS_CONTAINER_AUTHORIZATION_TOKEN \
    AWS_CONTAINER_AUTHORIZATION_TOKEN_FILE
  export AWS_ENDPOINT_URL=https://127.0.0.1:9297
  export AWS_SHARED_CREDENTIALS_FILE="$PWD/.aws-emulator/9297/emulator_credentials"
  export AWS_CONFIG_FILE="$PWD/.aws-emulator/9297/config"
  export AWS_CA_BUNDLE="$PWD/.aws-emulator/9297/ca.pem"
  export AWS_EC2_METADATA_DISABLED=true
  export AWS_REGION=us-east-1 AWS_DEFAULT_REGION=us-east-1 AWS_PAGER=""

  aws sts get-caller-identity
  # Expected Account: 000000000000
  aws s3 ls s3://orders-test
)
```

The subshell keeps these settings separate from your normal AWS environment. Always trust the generated CA rather than disabling TLS verification. The generated config refers to a container CA path, so host clients need the `AWS_CA_BUNDLE` override above.

### State and generated files

Both local data stores are ephemeral:

- H2 uses `jdbc:h2:mem:orders`; Hibernate uses `create-drop`. Restarting the Java application loses orders.
- AWS Emulator keeps resources in memory. Stopping it loses buckets and objects; rerun the initializer to recreate the empty test bucket.
- `.aws-emulator/9297/` contains the per-run config, credentials, and CA. The emulator removes these files on a clean shutdown and generates new ones on startup. This directory is ignored by Git.

## Project layout

```text
src/main/java/com/system/
  OrdersApplication.java          Spring Boot entry point
  orders/
    grpc/OrdersGrpcService.java   gRPC adapter and error mapping
    OrderService.java             Validation and transactional order operations
    OrderRepository.java          Spring Data JPA repository
    OrderEntity.java              Order persistence model
    OrderItemEntity.java          Order-line persistence model
src/main/proto/                   Versioned protobuf API contract
src/main/resources/               Application and database configuration
src/test/java/                    gRPC integration tests
scripts/test-orders-grpc.sh       Smoke test for a running application
.aws-emulator/Dockerfile          Latest-package emulator image build
compose.yaml                     Emulator and S3 initialization services
```

Generated protobuf sources live under `build/generated/sources/proto/`. Edit the `.proto` contract and regenerate them rather than editing generated Java files.

## Current scope

This is a local development service. It currently has no authentication or authorization layer, durable database configuration, schema migrations, pagination, enforced order-status transitions, or application-level S3 integration. The Compose file runs AWS tooling only; the Java service runs on the host. No project license file is currently included.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Java toolchain cannot be found | Install JDK 27 and check `./gradlew --version` and Java toolchain discovery. |
| Compose cannot connect to Docker | Start Docker Desktop or the Docker daemon. |
| Port `9090` or `9297` is occupied | Stop the conflicting process; use `SPRING_GRPC_SERVER_PORT` for the app. The emulator's port, mount paths, and connection settings must stay aligned if changed. |
| S3 bucket is missing after a restart | Run `docker compose run --rm aws-emulator-init` after the emulator becomes healthy. |
| AWS TLS or credential errors | Use the current run's files and the host CA override; remove inherited AWS credentials as shown above. |
| grpcurl cannot discover the service | Use the explicit `-import-path` and `-proto` example above. |
| The emulator folder becomes empty | A clean emulator shutdown removes its generated files; starting it recreates them. |
