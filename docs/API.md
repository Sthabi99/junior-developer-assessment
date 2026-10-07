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

## Select an investor

Request the accounts first and use the returned investor ID in subsequent URLs.

```http
GET /api/investors
```

Complete example response when the database is first created on 8 October 2026:

```json
[
  {
    "id": 1,
    "name": "Thabo Dlamini",
    "dateOfBirth": "1959-10-08",
    "age": 67
  },
  {
    "id": 2,
    "name": "Naledi Mokoena",
    "dateOfBirth": "1961-10-08",
    "age": 65
  },
  {
    "id": 3,
    "name": "Sipho Nkosi",
    "dateOfBirth": "1986-10-08",
    "age": 40
  }
]
```

Selecting an investor in the UI loads their portfolio and withdrawal history. The account selector is for the assessment; it does not authenticate a user.

## Retrieve a portfolio

```http
GET /api/investors/1/portfolio
```

Complete example for a new database initialised on 8 October 2026, including Thabo's three sample notices and both products. IDs and timestamps are illustrative; existing databases may contain other records. No fields have been omitted.

```json
{
  "investor": {
    "id": 1,
    "name": "Thabo Dlamini",
    "dateOfBirth": "1959-10-08",
    "age": 67
  },
  "totalBalance": 120000.0,
  "availableToWithdraw": 108000.0,
  "noticeCount": 3,
  "products": [
    {
      "id": 1,
      "name": "Retirement investment",
      "type": "RETIREMENT",
      "balance": 100000,
      "maximumWithdrawal": 90000.0,
      "withdrawalAllowed": true,
      "eligibilityMessage": "Up to 90% of the current balance.",
      "history": [
        {
          "recordedAt": "2026-06-08T10:00:00",
          "balance": 101800
        },
        {
          "recordedAt": "2026-07-08T10:00:00",
          "balance": 100000
        }
      ]
    },
    {
      "id": 2,
      "name": "Savings investment",
      "type": "SAVINGS",
      "balance": 20000,
      "maximumWithdrawal": 18000.0,
      "withdrawalAllowed": true,
      "eligibilityMessage": "Up to 90% of the current balance.",
      "history": [
        {
          "recordedAt": "2026-07-08T10:00:00",
          "balance": 21350
        },
        {
          "recordedAt": "2026-08-08T10:00:00",
          "balance": 20900
        },
        {
          "recordedAt": "2026-09-08T10:00:00",
          "balance": 20000
        }
      ]
    }
  ]
}
```

`totalBalance` adds both product balances. `availableToWithdraw` adds 90% of each eligible product's balance. For an investor aged 65 or younger, retirement has `withdrawalAllowed: false`, `maximumWithdrawal: 0.00`, and the message `Retirement withdrawals require an age above 65.` Savings remains eligible. `history` begins with the product's opening balance and includes the remaining balance after each saved withdrawal; it does not represent investment growth.

## Create a notice

```http
POST /api/investors/1/withdrawals
Content-Type: application/json
```

```json
{"productId": 1, "amount": 10000.00}
```

Both fields are required. The amount must be positive with no more than two decimal places. Check the portfolio's `maximumWithdrawal` and `withdrawalAllowed` values, but the server still validates them against the current balance.

Success returns **201 Created**, with `Location: /api/investors/1/withdrawals/8` in this example. Seven initial sample notices already exist across the accounts. The complete example response is:

```json
{
  "id": 8,
  "productId": 1,
  "productName": "Retirement investment",
  "amount": 10000.00,
  "balanceBefore": 100000.00,
  "remainingBalance": 90000.00,
  "createdAt": "2026-10-08T12:46:00"
}
```

The timestamp is illustrative; the server records the actual time. This withdrawal changes retirement from R 100 000 to R 90 000. Savings stays at R 20 000, so the new total balance is R 110 000, the available withdrawal amount is R 99 000 and the notice count becomes 4. The frontend reloads the portfolio and history after success.

PowerShell example:

```powershell
$body = @{ productId = 1; amount = 10000 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/investors/1/withdrawals' -ContentType 'application/json' -Body $body
```

## Retrieve the saved notice

Use the URL from the creation response's `Location` header. The notice must belong to the investor in the URL.

```http
GET /api/investors/1/withdrawals/8
```

Complete example response after the successful withdrawal above:

```json
{
  "id": 8,
  "productId": 1,
  "productName": "Retirement investment",
  "amount": 10000.0,
  "balanceBefore": 100000.0,
  "remainingBalance": 90000.0,
  "createdAt": "2026-10-08T12:46:00"
}
```

## Retrieve all withdrawal history

```http
GET /api/investors/1/withdrawals
```

The response is an array of complete notice objects, ordered newest first. After the withdrawal above, the complete example is:

```json
[
  {
    "id": 8,
    "productId": 1,
    "productName": "Retirement investment",
    "amount": 10000.0,
    "balanceBefore": 100000.0,
    "remainingBalance": 90000.0,
    "createdAt": "2026-10-08T12:46:00"
  },
  {
    "id": 3,
    "productId": 2,
    "productName": "Savings investment",
    "amount": 900.0,
    "balanceBefore": 20900.0,
    "remainingBalance": 20000.0,
    "createdAt": "2026-09-08T10:00:00"
  },
  {
    "id": 2,
    "productId": 2,
    "productName": "Savings investment",
    "amount": 450.0,
    "balanceBefore": 21350.0,
    "remainingBalance": 20900.0,
    "createdAt": "2026-08-08T10:00:00"
  },
  {
    "id": 1,
    "productId": 1,
    "productName": "Retirement investment",
    "amount": 1800.0,
    "balanceBefore": 101800.0,
    "remainingBalance": 100000.0,
    "createdAt": "2026-07-08T10:00:00"
  }
]
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
GET /api/investors/1/withdrawals?productId=2&from=2026-08-01&to=2026-09-30
GET /api/investors/1/withdrawals/export?productId=2&from=2026-08-01&to=2026-09-30
```

Complete listing response for the savings/date filters above:

```json
[
  {
    "id": 3,
    "productId": 2,
    "productName": "Savings investment",
    "amount": 900.0,
    "balanceBefore": 20900.0,
    "remainingBalance": 20000.0,
    "createdAt": "2026-09-08T10:00:00"
  },
  {
    "id": 2,
    "productId": 2,
    "productName": "Savings investment",
    "amount": 450.0,
    "balanceBefore": 21350.0,
    "remainingBalance": 20900.0,
    "createdAt": "2026-08-08T10:00:00"
  }
]
```

The dashboard total withdrawn for these filters is R 1 350 (R900 + R450). All-products history after the new R 10 000 retirement withdrawal totals R 13 150. These are withdrawal amounts, not the current investment balances. With no matching notices, the list returns `[]` and the displayed total is R0,00.

 Export returns `text/csv;charset=UTF-8` with an attachment filename. The complete CSV for the same filtered savings records is:

```text
sep=,
Notice ID,Recorded at,Product,Amount (R),Balance before (R),Remaining balance (R)
3,2026-09-08T10:00:00,"Savings investment",900.00,20900.00,20000.00
2,2026-08-08T10:00:00,"Savings investment",450.00,21350.00,20900.00
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

For example, this request is rejected because Naledi is exactly 65 and product 3 is her retirement investment:

```http
POST /api/investors/2/withdrawals
Content-Type: application/json
```

```json
{
  "productId": 3,
  "amount": 100.00
}
```

The response is **400 Bad Request**. No notice is saved and no balance changes. Complete response:

```json
{
  "message": "Retirement withdrawals are only allowed when the investor is older than 65.",
  "fieldErrors": {"productId": "Retirement withdrawals are only allowed when the investor is older than 65."}
}
```

Errors handled by the portfolio controller use an empty `fieldErrors` object when no individual field is involved. Unexpected server errors use Spring Boot's default error response. The browser uses field errors for inline feedback. This local assessment application uses example-account selection instead of authentication.
