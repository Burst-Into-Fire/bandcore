# Requirements

[English](REQUIREMENTS.md) | [Português (Brasil)](REQUIREMENTS-PTBR.md)

Full list of functional requirements (RF), non-functional requirements (RNF) and business rules (RN) for **bandcore**. The README shows a compact version of this list.

**Priority legend:** **MVP** is what the first release needs. **Next** comes right after the MVP. **Later** is planned but not scheduled.

**Scope:** account management (expenses, split and settlement) and merch (catalog, stock and sales). All money is handled in Brazilian real (BRL) and US dollars (USD).

## Functional requirements

### E1: Access and band

| ID | Requirement | Priority | Description |
|:---|:---|:---:|:---|
| RF01 | **Sign up and sign in** | MVP | Users register and sign in with e-mail and password. Passwords are stored hashed and sessions use JWT access and refresh tokens. |
| RF02 | **Password recovery** | Next | Users reset a forgotten password through a time-limited link sent by e-mail. |
| RF03 | **Create a band** | MVP | A signed-in user creates a band and becomes its admin. The band is the tenant that isolates all of its data. |
| RF04 | **Invite members** | MVP | Admins invite people by e-mail with an acceptance link. Invitations expire and can be revoked. |
| RF05 | **Roles per band** | MVP | Each member has a role in the band: admin (manages everything), member (records and views) or read-only. |
| RF06 | **Multiple bands** | Next | A user can belong to more than one band and switch between them. |
| RF07 | **Deactivate member** | Later | Admins deactivate a member without losing the history linked to that person. |

### E2: Expenses

| ID | Requirement | Priority | Description |
|:---|:---|:---:|:---|
| RF08 | **Register expense** | MVP | Record an expense with description, category, amount, currency (BRL or USD), date, status (planned or paid) and who paid it. |
| RF09 | **Future-dated expenses** | MVP | Expenses may have future dates so the band can project upcoming costs. |
| RF10 | **Default categories** | MVP | The system ships with default categories such as rehearsal, gear, video, photography, travel and press. |
| RF11 | **Custom categories** | Next | Bands create, rename and archive their own categories. |
| RF12 | **Edit, cancel and pay expenses** | MVP | Expenses can be edited, canceled and marked as paid. Changes keep history and nothing is physically deleted. |
| RF13 | **Receipt attachment** | Next | Attach a receipt (image or PDF) to an expense. |
| RF14 | **List and filter expenses** | MVP | List expenses filtered by period, category, status, currency and payer, with pagination. |

### E3: Split and settlement

| ID | Requirement | Priority | Description |
|:---|:---|:---:|:---|
| RF15 | **Equal split** | MVP | Split an expense equally among the selected members. |
| RF16 | **Custom split** | MVP | Split by custom amounts. The shares must add up to the total. |
| RF17 | **Member balances** | MVP | Show each member's balance per currency: what they owe and what they are owed. |
| RF18 | **Settlement payments** | MVP | Record a payment between members, in a single currency, to reduce balances. |
| RF19 | **Suggested settlement** | Next | Suggest the smallest set of transfers that settles all balances. |
| RF20 | **Manual income** | Next | Record income such as gig fees or member contributions into the band cash. |

### E4: Merch: catalog and stock

| ID | Requirement | Priority | Description |
|:---|:---|:---:|:---|
| RF21 | **Create products** | MVP | Create a product with name, selling price and unit cost, in BRL, USD or both. |
| RF22 | **Product variations** | MVP | Products have variations (size, color), each with its own stock. |
| RF23 | **Product photo** | Next | Attach a photo to a product. |
| RF24 | **Stock entry** | MVP | Register incoming stock with quantity and batch cost. |
| RF25 | **Stock adjustment** | Next | Adjust stock manually with a reason: loss, gift or recount. |
| RF26 | **Low-stock alert** | Next | Alert when a variation falls below a limit set by the band. |
| RF27 | **Deactivate product** | Later | Deactivate a product while keeping past sales intact. |

### E5: Merch: sales

| ID | Requirement | Priority | Description |
|:---|:---|:---:|:---|
| RF28 | **Register sale** | MVP | Record a sale with items, quantities, discount, currency, payment method and event or location. |
| RF29 | **Automatic stock deduction** | MVP | Every sale deducts stock automatically and is rejected if stock is insufficient. |
| RF30 | **Refund sale** | MVP | Cancel or refund a sale, returning items to stock and reverting the revenue. |
| RF31 | **Quick sale screen** | MVP | A mobile-first screen to sell in a few taps. |
| RF32 | **Offline sales** | Next | Record sales without internet and sync them later without duplicates. |
| RF33 | **Event closing** | Next | Close an event with total sold, split by payment method and currency, and profit. |

### E6: Accounts and merch integration

| ID | Requirement | Priority | Description |
|:---|:---|:---:|:---|
| RF34 | **Batch cost as expense** | Next | Turn the cost of a merch batch into an expense split among members. |
| RF35 | **Merch profit allocation** | Later | Send merch profit to the band cash or use it to offset member balances. |

### E7: Reports and data

| ID | Requirement | Priority | Description |
|:---|:---|:---:|:---|
| RF36 | **Monthly summary** | MVP | Show monthly paid and planned expenses, sales and profit, separated by currency. |
| RF37 | **Reports** | Next | Reports by category, product and period. |
| RF38 | **CSV export** | Next | Export expenses, sales and stock to CSV. |
| RF39 | **CSV import** | Later | Import expenses from a spreadsheet CSV. |

### E8: Currency (BRL and USD)

| ID | Requirement | Priority | Description |
|:---|:---|:---:|:---|
| RF40 | **Band default currency** | MVP | Each band sets a default currency (BRL or USD), used to pre-fill forms and format totals. |
| RF41 | **Manual exchange rate** | Later | Register a manual exchange rate to show a consolidated total in a single currency. |

## Non-functional requirements

### Security and privacy

| ID | Requirement | Priority | Description |
|:---|:---|:---:|:---|
| RNF01 | **Tenant isolation** | MVP | Every query is scoped by band. Automated tests try to read another band's data and must fail. |
| RNF02 | **Authentication** | MVP | Passwords hashed with Argon2 or bcrypt. JWT access tokens with short expiry plus refresh tokens. |
| RNF03 | **Authorization** | MVP | Role checks are always enforced in the backend, never only in the interface. |
| RNF04 | **Transport and secrets** | MVP | HTTPS only. Secrets live in environment variables, never in the repository. |
| RNF05 | **Login rate limit** | MVP | Limit failed login attempts per account and IP. |
| RNF06 | **LGPD** | Next | Terms of use, privacy policy, account deletion and data export on request. |
| RNF07 | **Row Level Security** | Later | PostgreSQL RLS as a second barrier behind application-level isolation. |

### Data integrity

| ID | Requirement | Priority | Description |
|:---|:---|:---:|:---|
| RNF08 | **Money representation** | MVP | Amounts are stored as integer minor units with an ISO 4217 currency code (BRL or USD), never as floating point. |
| RNF09 | **Transactional sales** | MVP | A sale and its stock deduction happen in the same transaction. |
| RNF10 | **Non-negative stock** | MVP | A database constraint prevents negative stock. |
| RNF11 | **Audit trail** | Next | Record who created, changed or canceled expenses and sales, and when. |
| RNF12 | **Soft delete** | Next | Expenses and sales are logically deleted, never physically. |
| RNF13 | **Idempotent sync** | Next | Offline sales carry a client-generated key, so retries never create duplicates. |

### Usability and platform

| ID | Requirement | Priority | Description |
|:---|:---|:---:|:---|
| RNF14 | **Responsive layout** | MVP | Usable from 360 px wide screens up to desktop. |
| RNF15 | **Installable PWA** | Next | Installable on phones, with a cached app shell that opens without connection. |
| RNF16 | **Fast sale** | MVP | Selling one item takes at most three taps. |
| RNF17 | **Accessibility** | Next | Sufficient contrast, labeled controls and 44 px touch targets. |
| RNF18 | **Locale and time zone** | Next | Texts are externalized for pt-BR and English. Numbers, dates and currencies follow the locale. Each band has a time zone (default America/Sao_Paulo). |

### Performance and availability

| ID | Requirement | Priority | Description |
|:---|:---|:---:|:---|
| RNF19 | **Response time** | Next | Main screens respond in under 2 seconds on 4G. |
| RNF20 | **Pagination** | Next | Long lists are paginated. |
| RNF21 | **Availability** | Next | Initial availability target of 99%. |
| RNF22 | **Initial sizing** | Next | Plan for dozens of bands and a few dozen concurrent users, and pick the hosting plan accordingly. |

### Portability and deploy

| ID | Requirement | Priority | Description |
|:---|:---|:---:|:---|
| RNF23 | **Containers** | MVP | API and web run in Docker containers, and docker-compose runs everything locally. |
| RNF24 | **Standard PostgreSQL** | MVP | Only standard PostgreSQL features, with no platform-specific extensions. |
| RNF25 | **Environment configuration** | MVP | All configuration comes from environment variables. |
| RNF26 | **CI/CD from GitHub** | MVP | A push to main runs tests, builds and deploys. Workflows filter by folder (api, web). |
| RNF27 | **Separate environments** | Next | Development and production are isolated. |
| RNF28 | **Versioned migrations** | MVP | Database changes are Flyway migrations applied at startup. |
| RNF29 | **Off-platform backups** | MVP | Daily database backups stored outside the hosting platform, with a tested restore. |

### Maintainability and operations

| ID | Requirement | Priority | Description |
|:---|:---|:---:|:---|
| RNF30 | **API documentation** | Next | REST API documented with OpenAPI. |
| RNF31 | **Tests on critical rules** | MVP | Automated tests cover splitting, balances, stock, refunds, tenant isolation and currency handling. |
| RNF32 | **Logs and error monitoring** | Next | Structured logs and error monitoring. |
| RNF33 | **Health check** | Next | A health endpoint for uptime monitoring. |
| RNF34 | **Cost ceiling** | Next | Define a monthly cost ceiling and track consumption. |

## Business rules

These rules usually become acceptance criteria of the user stories.

| ID | Rule |
|:---|:---|
| RN01 | The shares of an expense always add up to its total. |
| RN02 | Member balance = amount paid minus the member's shares, adjusted by settlement payments, computed per currency. |
| RN03 | The stock of a variation never goes below zero. |
| RN04 | Refunding a sale returns the stock and reverts the revenue. |
| RN05 | A sale stores the price and cost in effect at that moment, so historical profit never changes. |
| RN06 | Only admins change roles or remove members. |
| RN07 | Changing the amount of a paid expense requires an audit record. |
| RN08 | Amounts in different currencies are never added together. Totals and balances are shown per currency unless a manual exchange rate is registered. |