# API

Base URL: `http://localhost:8080`. All JSON endpoints return `application/json`. Send `Content-Type: application/json` with POST requests. IDs in examples are illustrative; retrieve the actual IDs first.

| Method | Endpoint | Result |
| --- | --- | --- |
| GET | `/api/investors` | Investor IDs, names, dates of birth and ages |
| GET | `/api/investors/{id}/portfolio` | Investor details, products, balances, eligibility and recorded balance history |
| POST | `/api/investors/{id}/withdrawals` | Save a withdrawal notice and reduce the selected balance |
| GET | `/api/investors/{id}/withdrawals` | Saved notices, newest first |
| GET | `/api/investors/{id}/withdrawals/{noticeId}` | One notice belonging to that investor |
| GET | `/api/investors/{id}/withdrawals/export` | Download filtered CSV |

## Retrieve a portfolio

```http
GET /api/investors/1/portfolio
```

Example fields before any withdrawals (timestamp fields are generated when data is saved):

```json
{
  "investor": {"id": 1, "name": "Thabo Dlamini", "dateOfBirth": "1959-10-08", "age": 67},
  "totalBalance": 120000.00,
  "availableToWithdraw": 108000.00,
  "noticeCount": 0,
  "products": [
    {
      "id": 1,
      "name": "Retirement investment",
      "type": "RETIREMENT",
      "balance": 100000.00,
      "maximumWithdrawal": 90000.00,
      "withdrawalAllowed": true,
      "eligibilityMessage": "Up to 90% of the current balance.",
      "history": []
    }
  ]
}
```

This example is shortened: the actual response includes both products and their opening balance point. Each history point contains `recordedAt` and `balance`. The date of birth above reflects an example account created on the verification date; it is not a required request value.

## Create a notice

```http
POST /api/investors/1/withdrawals
Content-Type: application/json
```

```json
{"productId": 1, "amount": 10000.00}
```

Both fields are required. The amount must be positive with no more than two decimal places. Check the portfolio's `maximumWithdrawal` and `withdrawalAllowed` values, but the server still validates them against the current balance.

Success returns **201 Created**, a `Location` header for the notice, and these response fields:

```json
{
  "id": 1,
  "productId": 1,
  "productName": "Retirement investment",
  "amount": 10000.00,
  "balanceBefore": 100000.00,
  "remainingBalance": 90000.00,
  "createdAt": "2026-10-08T12:46:00"
}
```

The timestamp is an example format; the server records the actual time.

PowerShell example:

```powershell
$body = @{ productId = 1; amount = 10000 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/investors/1/withdrawals' -ContentType 'application/json' -Body $body
```

## History and export filters

Both list and export accept the same optional query parameters:

| Parameter | Format | Meaning |
| --- | --- | --- |
| `productId` | Integer | Only notices for a product owned by this investor |
| `from` | `YYYY-MM-DD` | Include notices on or after this date |
| `to` | `YYYY-MM-DD` | Include notices on or before this date |

Example:

```http
GET /api/investors/1/withdrawals?productId=1&from=2026-10-01&to=2026-10-31
GET /api/investors/1/withdrawals/export?productId=1&from=2026-10-01&to=2026-10-31
```

Listing returns an array of notice objects, or `[]` if nothing matches. Export returns `text/csv;charset=UTF-8` with an attachment filename. It uses these columns:

```text
Notice ID,Recorded at,Product,Amount ZAR,Balance before ZAR,Remaining balance ZAR
```

The file starts with a UTF-8 byte order mark and an Excel `sep=,` hint so each heading and value opens in its own column, including on computers with a different regional list separator. When importing with another CSV tool, skip the separator-hint line.

Amounts use a decimal point without currency symbols or thousands separators. Text is CSV-quoted. An empty result produces a header-only CSV. The UI disables Download when the visible result is empty.

## Errors

| Status | Meaning |
| --- | --- |
| 400 | Missing/invalid fields, age or amount rule failure, malformed JSON or invalid dates |
| 404 | Investor, product or notice not found, or does not belong to the selected investor |
| 409 | A concurrent update could not obtain the balance lock; refresh before retrying |
| 500 | Unexpected failure; detailed exception information stays in the server log |

Example rule error:

```json
{
  "message": "Retirement withdrawals are only allowed when the investor is older than 65.",
  "fieldErrors": {"productId": "Retirement withdrawals are only allowed when the investor is older than 65."}
}
```

Errors handled by the portfolio controller use an empty `fieldErrors` object when no individual field is involved. Unexpected server errors use Spring Boot's default error response. The browser uses field errors for inline feedback. This local assessment application uses example-account selection instead of authentication.
