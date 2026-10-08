# banking_core

Networking, session and money handling for the mobile apps.

- `BankingBackend.create` wires a public and an authorised Dio client, the `SessionController` and secure storage.
- Access tokens stay in memory; the refresh token is in the platform keystore and rotated through a single in-flight refresh.
- `Money` holds exact decimals from the API and has no arithmetic; `MoneyFormat` formats them without floating point.
- `package:banking_core/testing.dart`: `FakeBackend`, a Dio adapter for tests.
