# Afinco Backend

Lightweight personal-finance API foundation built with Java 21, Spring Boot 3,
SQLite, Flyway, and Spring Data JPA.

## Project status

Working today: SQLite-backed email/password authentication, hardened server-side
sessions, TD credit-card statement upload and parsing, SHA-256 duplicate
detection, single and batched transaction persistence, duplicate resolution,
transaction filtering, and account/category lookups. 236 tests pass under
`mvn clean verify`.

The companion UI lives in the separate [afinco_frontend](https://github.com/viniciusmioto/afinco_frontend)
repository.

### Not implemented yet

Ordered roughly by how much each one blocks real use.

1. **Account creation — blocking.** The `accounts` table has no Flyway seed row
   and no write endpoint, so a fresh database returns `[]` from
   `GET /api/v1/accounts` and *no imported transaction can be saved at all*.
   `POST /api/v1/accounts` (and ideally update/delete) is the next thing to
   build.
2. **Analytics endpoints.** `TransactionRepository.aggregateConfirmedByCategory`
   is written and indexed but no service or controller exposes it, so the
   planned dashboards have no data source. This query is currently dead code.
3. **Checking-account parser.** `StatementType.CHECKING_ACCOUNT` is accepted and
   validated but `StatementParserFactory` has no strategy for it, so those
   uploads return `422`. Rows from it are meant to be typed `DEBIT`.
4. **RBC parser.** The factory registers TD only. RBC was intended as the second
   supported institution.
5. **Category management.** The eight default categories are inserted by
   `V1__init_schema.sql` and can only be changed by editing the database.
   Custom user categories need create/update/delete endpoints.
6. **Transaction editing.** Only create, batch-create, resolve-duplicate, and
   delete exist. There is no `PUT`/`PATCH`, so correcting a wrong category or
   amount means deleting and re-creating the row.
7. **Unmapped paths return `500`.** `GlobalExceptionHandler`'s
   `@ExceptionHandler(Exception.class)` catch-all swallows Spring's
   no-handler-found exception, so a typo'd URL reports
   `500 "An unexpected error occurred"` instead of `404`. This is a real defect
   and makes client-side debugging misleading.
8. **Scanned and encrypted PDFs.** OCR is not implemented, and statements that
   require a password to open are rejected. Owner-encrypted PDFs that open
   without a password and allow text extraction do work.

## Authentication

Every finance endpoint requires a server-side session. The two public auth
operations initialize CSRF protection and establish the session; the current
user and logout operations require it:

- `GET /api/v1/auth/csrf`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/logout`

The initial local test user is `test@test.com` with password `123@Test`. The
password is stored only as a BCrypt cost-12 hash in SQLite. It is a temporary,
publicly documented credential and must be replaced before exposing Afinco to
an untrusted network.

See [Authentication architecture](docs/authentication.md) for endpoint
contracts, CSRF/session behavior, the SQLite schema, threat boundaries,
credential rotation guidance, and the `miopiaz` deployment checklist.

## Run with Docker

Docker Compose builds the application, runs all tests during the image build,
and stores the SQLite database in the `afinco-data` named volume.

```shell
docker compose up --build
```

The application listens on `http://localhost:8080`. Stop it with:

```shell
docker compose down
```

Add `--volumes` only when you intentionally want to delete all local Afinco
database data.

## Run tests locally

Java 21 or newer and Maven 3.9+ are required. Maven compiles with Java 21 bytecode.

```shell
mvn clean verify
```

Tests use a temporary SQLite database and never touch `./afinco.db`.

## Transaction API

The API is available under `/api/v1/transactions`:

- `GET /api/v1/transactions` supports `startDate`, `endDate`, `accountId`,
  `categoryId`, `type`, `status`, `page`, and `size` filters. Results default to
  20 items and are ordered by transaction date and ID, newest first.
- `POST /api/v1/transactions` creates a transaction. An existing SHA-256
  signature automatically produces the `DUPLICATE_PENDING` status. The server
  calculates the signature; the optional legacy `hashSignature` input is ignored.
- `POST /api/v1/transactions/batch` persists a reviewed statement upload in one
  request. See **Reviewed batch persistence** below.
- `POST /api/v1/transactions/resolve-duplicate` confirms a pending duplicate
  using `{ "transactionId": 1, "status": "CONFIRMED" }`.
- `DELETE /api/v1/transactions/{id}` removes a transaction, including a pending
  duplicate the user chooses to ignore.

Two read-only lookup endpoints back the review screens, which must know real
database identifiers before they can build a transaction payload:

- `GET /api/v1/accounts` lists accounts ordered by bank name, then ID.
- `GET /api/v1/categories` lists categories ordered by name.

Both return plain JSON arrays. Accounts have no seed data and no create endpoint
yet, so a fresh database returns `[]` here and reviewed rows cannot be saved
until an account row exists.

## Reviewed batch persistence

`POST /api/v1/transactions/batch` saves the rows a reviewer approved after a
statement upload. One statement covers one account, so `accountId` is sent once
instead of being repeated per row:

```json
{
  "accountId": 1,
  "transactions": [
    {
      "categoryId": 1,
      "date": "2026-01-03",
      "amount": 1234.56,
      "type": "CREDIT",
      "description": "ONLINE SERVICE",
      "forceDuplicate": false
    }
  ]
}
```

Rows the reviewer skipped are never sent. `forceDuplicate` carries the explicit
"import anyway" decision for a row the upload preview flagged: a matching
signature is stored as `CONFIRMED` when `forceDuplicate` is `true`, and as
`DUPLICATE_PENDING` when it is `false`. A row with no signature match is always
`CONFIRMED`. Repeated rows inside one batch follow the same rule, so the second
identical row is flagged unless it was force-imported.

As with single creation, the server recalculates every signature from the
resolved account's bank name, so a previewed `hashSignature` is accepted but
never persisted. Existing signatures are looked up in batches of 400.

The response reports `savedCount`, `duplicateCount` (saved rows still awaiting
resolution), and the full `transactions` list. A batch holds 1 to 500 rows and
is written in a single transaction, so an unknown `accountId` or `categoryId`
returns `404` and saves nothing. Per-row validation failures return `400` with
field keys such as `transactions[0].categoryId`. Confirm a pending duplicate
afterwards with the resolution endpoint, or delete it to ignore it.

Controllers only expose validated request and response DTOs; JPA entities remain
inside the domain and persistence layers. API errors consistently include a UTC
timestamp, HTTP status, message, request path, and field-level validation errors.

## TD statement upload

`POST /api/v1/statements/upload` accepts a multipart **file** field containing an
English TD credit-card e-statement and a required text field named
`statementType`. Its allowed values are `CREDIT_CARD` and `CHECKING_ACCOUNT`.
Credit-card parsing is implemented now; checking-account uploads return `422`
until their separate parser is implemented. Apache PDFBox reads the searchable text and
uses the transaction table's column positions to exclude adjacent summary boxes.
The strategy factory currently registers only TD. RBC, deposit-account statements,
scanned PDFs/OCR, password-required PDFs, and unrecognized layouts are unsupported.
Owner-encrypted statements that open without a password and permit text extraction
are supported.
Uploads are limited to 10 MiB and 100 pages. Dates use the transaction date (not
posting date), with the statement date used to resolve year rollover.

The response contains `bankName`, `transactionCount`, `duplicateCount`, `total`,
and a `transactions` list. `total` is the sum of every returned transaction amount.
Each item includes `date`, positive `amount`, `type`,
`description`, `bankName`, `hashSignature`, `status`, and `duplicate`.
Every transaction from a credit-card statement is assigned `CREDIT`. When
checking-account parsing is added, every transaction from that statement type
will be assigned `DEBIT`.

This endpoint is a preview: it does not save transactions or the PDF. Matching
database hashes and repeated rows within an upload receive `DUPLICATE_PENDING`.
Other rows receive `CONFIRMED`. Re-uploading an unsaved preview does not itself
make rows database duplicates. To save approved rows, send them to
`POST /api/v1/transactions/batch` with an existing `accountId` and a
`categoryId` per row; it checks duplicates again. Confirm a saved pending
duplicate using the resolution endpoint, or delete it to ignore it. Upload
previews have no database transaction IDs to resolve yet.

Hashes use SHA-256 over unambiguously length-prefixed date, amount, description,
and bank fields. Dates are ISO, amounts use two decimals, and text normalization
handles Unicode, case and whitespace. `TD`, `TD Canada Trust`, and `TD Bank` share
one bank identity. Type is intentionally excluded per the signature contract;
matches are review candidates, not automatic deletions. Existing rows with older
client-supplied hashes are not automatically rehashed.

In Insomnia, create a POST request to `http://localhost:8080/api/v1/statements/upload`.
Select **Multipart Form**, add a field named `file`, change its type to **File**,
and select your local PDF. Add a text field named `statementType` with the value
`CREDIT_CARD`. An authenticated session and CSRF header are required.
Let Insomnia generate the multipart Content-Type boundary. Example using a
placeholder path (replace it with your private local file):

```shell
curl --request POST 'http://localhost:8080/api/v1/statements/upload' \
  --header 'Accept: application/json' \
  --header 'X-XSRF-TOKEN: <token-from-auth-csrf>' \
  --cookie '<cookies-from-login>' \
  --form 'statementType=CREDIT_CARD' \
  --form 'file=@/absolute/path/to/statement.pdf;type=application/pdf'
```

Invalid/missing uploads return structured `400` errors, unsupported Content-Type
returns `415`, oversized uploads return `413`, and unreadable/unsupported
statements return `422`. Responses use `Cache-Control: no-store`. Accepted PDF
uploads stay in memory; filenames and extracted content are not logged. Private
PDFs are excluded from Git and Docker contexts. Committed fixtures are invented
text; PDF tests generate their documents in memory. Never add real statements or
API response exports to the repository.

Implementation references: [PDFBox 3](https://pdfbox.apache.org/3.0/migration.html)
and the [Teller TD parsing discussion](https://github.com/Bizzaro/Teller).

## Database configuration

At runtime, `AFINCO_DATABASE_PATH` controls the SQLite file location. It defaults
to `./afinco.db` outside Docker and `/data/afinco.db` in the image and Compose. Flyway owns
schema changes in `src/main/resources/db/migration`.
