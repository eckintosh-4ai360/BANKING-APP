import 'package:banking_api/banking_api.dart';
import 'package:banking_core/banking_core.dart';

/// Responses of the customer API. Amounts are parsed into [Money] exactly as the server sent them; the app never
/// calculates money. Fields the server leaves out are null.

Money _money(JsonMap json, String key, String currency) => Money.parse(readString(json, key), currency);

Money? _optionalMoney(JsonMap json, String key, String currency) {
  final value = readOptionalString(json, key);
  return value == null ? null : Money.parse(value, currency);
}

int _int(JsonMap json, String key) {
  final value = json[key];
  if (value is int) {
    return value;
  }
  throw FormatException('Expected "$key" to be a whole number');
}

/// A calendar date (`2027-03-01`).
DateTime? _date(JsonMap json, String key) {
  final value = readOptionalString(json, key);
  if (value == null) {
    return null;
  }
  final parsed = DateTime.tryParse(value);
  if (parsed == null) {
    throw FormatException('Expected "$key" to be a date');
  }
  return DateTime.utc(parsed.year, parsed.month, parsed.day);
}

DateTime _requiredDate(JsonMap json, String key) =>
    _date(json, key) ?? (throw FormatException('Expected "$key" to be a date'));

DateTime _requiredInstant(JsonMap json, String key) =>
    readOptionalInstant(json, key) ?? (throw FormatException('Expected "$key" to be an instant'));

List<T> _list<T>(Object? data, T Function(JsonMap json) parse) {
  if (data is! List) {
    throw const FormatException('Expected a list');
  }
  return [for (final item in data) parse(asJsonMap(item))];
}

// ------------------------------------------------------------------------------------------------- profile

/// The signed-in customer (`GET /api/v1/customer/me`).
class CustomerProfile {
  const CustomerProfile({
    required this.customerId,
    required this.customerNumber,
    required this.displayName,
    required this.phoneNumber,
    required this.status,
    required this.kycStatus,
    required this.pinLocked,
    this.kycTierCode,
    this.lastLoginAt,
  });

  factory CustomerProfile.fromJson(Object? data) {
    final json = asJsonMap(data, 'profile');
    return CustomerProfile(
      customerId: readString(json, 'customerId'),
      customerNumber: readString(json, 'customerNumber'),
      displayName: readString(json, 'displayName'),
      phoneNumber: readString(json, 'phoneNumber'),
      status: readString(json, 'status'),
      kycStatus: readString(json, 'kycStatus'),
      kycTierCode: readOptionalString(json, 'kycTierCode'),
      lastLoginAt: readOptionalInstant(json, 'lastLoginAt'),
      pinLocked: readBool(json, 'pinLocked', fallback: false),
    );
  }

  final String customerId;
  final String customerNumber;
  final String displayName;

  /// Partly hidden by the server.
  final String phoneNumber;
  final String status;
  final String kycStatus;
  final String? kycTierCode;
  final DateTime? lastLoginAt;
  final bool pinLocked;

  /// Signed up in the app and not yet approved: the app shows the sign-up stages instead of banking.
  bool get isSigningUp => status == 'PENDING';
}

/// Where a texted code went (`CodeSent`). The same answer comes whether or not the request matched anyone.
class CodeSent {
  const CodeSent({required this.challengeToken, required this.sentTo, required this.expiresAt});

  factory CodeSent.fromJson(Object? data) {
    final json = asJsonMap(data, 'code');
    return CodeSent(
      challengeToken: readString(json, 'challengeToken'),
      sentTo: readString(json, 'sentTo'),
      expiresAt: _requiredInstant(json, 'expiresAt'),
    );
  }

  final String challengeToken;
  final String sentTo;
  final DateTime expiresAt;

  @override
  String toString() => 'CodeSent(sentTo: $sentTo, challengeToken: ***)';
}

// ------------------------------------------------------------------------------------------------ accounts

class CustomerAccount {
  const CustomerAccount({
    required this.id,
    required this.accountNumber,
    required this.title,
    required this.productName,
    required this.productType,
    required this.currency,
    required this.status,
    required this.ledgerBalance,
    required this.availableBalance,
    this.openedOn,
  });

  factory CustomerAccount.fromJson(JsonMap json) {
    final currency = readString(json, 'currency');
    return CustomerAccount(
      id: readString(json, 'id'),
      accountNumber: readString(json, 'accountNumber'),
      title: readString(json, 'title'),
      productName: readString(json, 'productName'),
      productType: readString(json, 'productType'),
      currency: currency,
      status: readString(json, 'status'),
      ledgerBalance: _money(json, 'ledgerBalance', currency),
      availableBalance: _money(json, 'availableBalance', currency),
      openedOn: _date(json, 'openedOn'),
    );
  }

  final String id;
  final String accountNumber;
  final String title;
  final String productName;
  final String productType;
  final String currency;
  final String status;
  final Money ledgerBalance;
  final Money availableBalance;
  final DateTime? openedOn;

  /// Money can leave it from the app.
  bool get canPayFrom => status == 'ACTIVE';
}

/// The customer's accounts and what is available in each currency, as the server added it up.
class AccountsOverview {
  const AccountsOverview({required this.accounts, required this.totals});

  factory AccountsOverview.fromJson(Object? data) {
    final json = asJsonMap(data, 'accounts');
    return AccountsOverview(
      accounts: readObjectList(json, 'accounts').map(CustomerAccount.fromJson).toList(),
      totals: [
        for (final total in readObjectList(json, 'totals')) _money(total, 'available', readString(total, 'currency')),
      ],
    );
  }

  final List<CustomerAccount> accounts;
  final List<Money> totals;
}

class StatementLine {
  const StatementLine({required this.date, required this.reference, required this.description, required this.balance, this.debit, this.credit});

  factory StatementLine.fromJson(JsonMap json, String currency) => StatementLine(
        date: _requiredDate(json, 'date'),
        reference: readOptionalString(json, 'reference') ?? '',
        description: readOptionalString(json, 'description') ?? '',
        debit: _optionalMoney(json, 'debit', currency),
        credit: _optionalMoney(json, 'credit', currency),
        balance: _money(json, 'balance', currency),
      );

  final DateTime date;
  final String reference;
  final String description;

  /// Money out of the account.
  final Money? debit;

  /// Money into the account.
  final Money? credit;
  final Money balance;
}

/// An account statement built by the server from the ledger.
class AccountStatement {
  const AccountStatement({
    required this.accountNumber,
    required this.accountTitle,
    required this.currency,
    required this.from,
    required this.to,
    required this.openingBalance,
    required this.closingBalance,
    required this.totalDebits,
    required this.totalCredits,
    required this.lines,
  });

  factory AccountStatement.fromJson(Object? data) {
    final json = asJsonMap(data, 'statement');
    final currency = readString(json, 'currency');
    return AccountStatement(
      accountNumber: readString(json, 'accountNumber'),
      accountTitle: readString(json, 'accountTitle'),
      currency: currency,
      from: _requiredDate(json, 'from'),
      to: _requiredDate(json, 'to'),
      openingBalance: _money(json, 'openingBalance', currency),
      closingBalance: _money(json, 'closingBalance', currency),
      totalDebits: _money(json, 'totalDebits', currency),
      totalCredits: _money(json, 'totalCredits', currency),
      lines: [for (final line in readObjectList(json, 'lines')) StatementLine.fromJson(line, currency)],
    );
  }

  final String accountNumber;
  final String accountTitle;
  final String currency;
  final DateTime from;
  final DateTime to;
  final Money openingBalance;
  final Money closingBalance;
  final Money totalDebits;
  final Money totalCredits;
  final List<StatementLine> lines;
}

// ----------------------------------------------------------------------------------------------- transfers

/// Who an account number belongs to, partly hidden by the server ("Kofi M."), to check before paying.
class TransferDestination {
  const TransferDestination({required this.accountNumber, required this.name, required this.currency});

  factory TransferDestination.fromJson(Object? data) {
    final json = asJsonMap(data, 'destination');
    return TransferDestination(
      accountNumber: readString(json, 'accountNumber'),
      name: readString(json, 'name'),
      currency: readString(json, 'currency'),
    );
  }

  final String accountNumber;
  final String name;
  final String currency;
}

class TransferReceipt {
  const TransferReceipt({
    required this.transactionId,
    required this.reference,
    required this.amount,
    required this.currency,
    required this.fromAccountNumber,
    required this.toAccountNumber,
    this.fee,
    this.toName,
    this.availableAfter,
    this.postedAt,
  });

  factory TransferReceipt.fromJson(Object? data) {
    final json = asJsonMap(data, 'receipt');
    final currency = readString(json, 'currency');
    return TransferReceipt(
      transactionId: readString(json, 'transactionId'),
      reference: readString(json, 'reference'),
      amount: _money(json, 'amount', currency),
      fee: _optionalMoney(json, 'fee', currency),
      currency: currency,
      fromAccountNumber: readString(json, 'fromAccountNumber'),
      toAccountNumber: readString(json, 'toAccountNumber'),
      toName: readOptionalString(json, 'toName'),
      availableAfter: _optionalMoney(json, 'availableAfter', currency),
      postedAt: readOptionalInstant(json, 'postedAt'),
    );
  }

  final String transactionId;
  final String reference;
  final Money amount;
  final Money? fee;
  final String currency;
  final String fromAccountNumber;
  final String toAccountNumber;
  final String? toName;
  final Money? availableAfter;
  final DateTime? postedAt;
}

class Beneficiary {
  const Beneficiary({
    required this.id,
    required this.type,
    required this.nickname,
    required this.accountNumber,
    required this.favourite,
    required this.version,
    this.name,
    this.coolingDownUntil,
  });

  factory Beneficiary.fromJson(JsonMap json) => Beneficiary(
        id: readString(json, 'id'),
        type: readString(json, 'type'),
        nickname: readString(json, 'nickname'),
        accountNumber: readString(json, 'accountNumber'),
        name: readOptionalString(json, 'name'),
        favourite: readBool(json, 'favourite', fallback: false),
        coolingDownUntil: readOptionalInstant(json, 'coolingDownUntil'),
        version: _int(json, 'version'),
      );

  final String id;
  final String type;
  final String nickname;
  final String accountNumber;

  /// The holder's name, partly hidden.
  final String? name;
  final bool favourite;

  /// While newly added, transfers to it are limited.
  final DateTime? coolingDownUntil;
  final int version;
}

// --------------------------------------------------------------------------------------------------- loans

class CustomerLoan {
  const CustomerLoan({
    required this.id,
    required this.loanNumber,
    required this.productName,
    required this.currency,
    required this.principal,
    required this.installments,
    required this.status,
    required this.daysPastDue,
    required this.principalOutstanding,
    required this.interestDue,
    required this.penaltyDue,
    required this.arrears,
    this.annualRate,
    this.repaymentFrequency,
    this.disbursementDate,
    this.maturityDate,
    this.nextDueDate,
    this.nextDueAmount,
    this.repaymentAccountId,
  });

  factory CustomerLoan.fromJson(JsonMap json) {
    final currency = readString(json, 'currency');
    Money zeroOr(String key) => _optionalMoney(json, key, currency) ?? Money.zero(currency);
    return CustomerLoan(
      id: readString(json, 'id'),
      loanNumber: readString(json, 'loanNumber'),
      productName: readString(json, 'productName'),
      currency: currency,
      principal: _money(json, 'principal', currency),
      annualRate: readOptionalString(json, 'annualRate'),
      repaymentFrequency: readOptionalString(json, 'repaymentFrequency'),
      installments: _int(json, 'installments'),
      disbursementDate: _date(json, 'disbursementDate'),
      maturityDate: _date(json, 'maturityDate'),
      status: readString(json, 'status'),
      daysPastDue: _int(json, 'daysPastDue'),
      principalOutstanding: zeroOr('principalOutstanding'),
      interestDue: zeroOr('interestDue'),
      penaltyDue: zeroOr('penaltyDue'),
      arrears: zeroOr('arrears'),
      nextDueDate: _date(json, 'nextDueDate'),
      nextDueAmount: _optionalMoney(json, 'nextDueAmount', currency),
      repaymentAccountId: readOptionalString(json, 'repaymentAccountId'),
    );
  }

  final String id;
  final String loanNumber;
  final String productName;
  final String currency;
  final Money principal;

  /// Percent a year, as the server sent it.
  final String? annualRate;
  final String? repaymentFrequency;
  final int installments;
  final DateTime? disbursementDate;
  final DateTime? maturityDate;
  final String status;
  final int daysPastDue;
  final Money principalOutstanding;
  final Money interestDue;
  final Money penaltyDue;
  final Money arrears;
  final DateTime? nextDueDate;
  final Money? nextDueAmount;
  final String? repaymentAccountId;

  bool get isActive => status == 'ACTIVE';
}

class LoanInstallment {
  const LoanInstallment({required this.number, required this.dueDate, required this.amountDue, required this.paid, required this.outstanding, required this.status, this.paidOn});

  factory LoanInstallment.fromJson(JsonMap json, String currency) => LoanInstallment(
        number: _int(json, 'number'),
        dueDate: _requiredDate(json, 'dueDate'),
        // The server's split; the app shows each part rather than adding them up.
        amountDue: [
          _money(json, 'principalDue', currency),
          _money(json, 'interestDue', currency),
          _optionalMoney(json, 'penaltyDue', currency) ?? Money.zero(currency),
        ],
        paid: _money(json, 'paid', currency),
        outstanding: _money(json, 'outstanding', currency),
        paidOn: _date(json, 'paidOn'),
        status: readString(json, 'status'),
      );

  final int number;
  final DateTime dueDate;

  /// Principal, interest and penalty due.
  final List<Money> amountDue;
  final Money paid;
  final Money outstanding;
  final DateTime? paidOn;
  final String status;
}

class LoanRepayment {
  const LoanRepayment({required this.id, required this.amount, required this.principal, required this.interest, required this.penalty, this.businessDate, this.transactionId});

  factory LoanRepayment.fromJson(JsonMap json, String currency) => LoanRepayment(
        id: readString(json, 'id'),
        transactionId: readOptionalString(json, 'transactionId'),
        amount: _money(json, 'amount', currency),
        principal: _optionalMoney(json, 'principal', currency) ?? Money.zero(currency),
        interest: _optionalMoney(json, 'interest', currency) ?? Money.zero(currency),
        penalty: _optionalMoney(json, 'penalty', currency) ?? Money.zero(currency),
        businessDate: _date(json, 'businessDate'),
      );

  final String id;
  final String? transactionId;
  final Money amount;
  final Money principal;
  final Money interest;
  final Money penalty;
  final DateTime? businessDate;
}

class LoanDetail {
  const LoanDetail({required this.loan, required this.schedule, required this.repayments, this.payoff});

  factory LoanDetail.fromJson(Object? data) {
    final json = asJsonMap(data, 'loan detail');
    final loan = CustomerLoan.fromJson(asJsonMap(json['loan'], 'loan'));
    final payoff = json['payoff'] == null ? null : asJsonMap(json['payoff'], 'payoff');
    return LoanDetail(
      loan: loan,
      schedule: [for (final item in readObjectList(json, 'schedule')) LoanInstallment.fromJson(item, loan.currency)],
      repayments: [for (final item in readObjectList(json, 'repayments')) LoanRepayment.fromJson(item, loan.currency)],
      payoff: payoff == null ? null : _optionalMoney(payoff, 'total', loan.currency),
    );
  }

  final CustomerLoan loan;
  final List<LoanInstallment> schedule;
  final List<LoanRepayment> repayments;

  /// What settles the loan today, as the server quoted it.
  final Money? payoff;
}

class LoanRepaymentReceipt {
  const LoanRepaymentReceipt({required this.repayment, required this.loan, required this.settled});

  factory LoanRepaymentReceipt.fromJson(Object? data) {
    final json = asJsonMap(data, 'repayment receipt');
    final loan = CustomerLoan.fromJson(asJsonMap(json['loan'], 'loan'));
    return LoanRepaymentReceipt(
      repayment: LoanRepayment.fromJson(asJsonMap(json['repayment'], 'repayment'), loan.currency),
      loan: loan,
      settled: readBool(json, 'settled', fallback: false),
    );
  }

  final LoanRepayment repayment;
  final CustomerLoan loan;
  final bool settled;
}

// ---------------------------------------------------------------------------------------------------- susu

class SusuPlan {
  const SusuPlan({
    required this.id,
    required this.planNumber,
    required this.frequencyCode,
    required this.contributionAmount,
    required this.currency,
    required this.cycleLength,
    required this.status,
    required this.currentCycle,
    required this.paid,
    required this.missed,
    required this.totalPaid,
    this.arrears,
    this.nextDue,
    this.targetAmount,
  });

  factory SusuPlan.fromJson(JsonMap json) {
    final currency = readString(json, 'currency');
    return SusuPlan(
      id: readString(json, 'id'),
      planNumber: readString(json, 'planNumber'),
      frequencyCode: readString(json, 'frequencyCode'),
      contributionAmount: _money(json, 'contributionAmount', currency),
      currency: currency,
      cycleLength: _int(json, 'cycleLength'),
      status: readString(json, 'status'),
      currentCycle: _int(json, 'currentCycle'),
      paid: _int(json, 'paid'),
      missed: _int(json, 'missed'),
      arrears: _optionalMoney(json, 'arrears', currency),
      nextDue: _date(json, 'nextDue'),
      targetAmount: _optionalMoney(json, 'targetAmount', currency),
      totalPaid: _optionalMoney(json, 'totalPaid', currency) ?? Money.zero(currency),
    );
  }

  final String id;
  final String planNumber;
  final String frequencyCode;
  final Money contributionAmount;
  final String currency;
  final int cycleLength;
  final String status;
  final int currentCycle;
  final int paid;
  final int missed;
  final Money? arrears;
  final DateTime? nextDue;
  final Money? targetAmount;
  final Money totalPaid;
}

class SusuContribution {
  const SusuContribution({required this.sequenceNo, required this.cycleNo, required this.dueDate, required this.amount, required this.status, this.paidAt});

  factory SusuContribution.fromJson(JsonMap json, String currency) => SusuContribution(
        sequenceNo: _int(json, 'sequenceNo'),
        cycleNo: _int(json, 'cycleNo'),
        dueDate: _requiredDate(json, 'dueDate'),
        amount: _money(json, 'amount', currency),
        status: readString(json, 'status'),
        paidAt: readOptionalInstant(json, 'paidAt'),
      );

  final int sequenceNo;
  final int cycleNo;
  final DateTime dueDate;
  final Money amount;
  final String status;
  final DateTime? paidAt;
}

class SusuPlanDetail {
  const SusuPlanDetail({required this.plan, required this.contributions, required this.commissions});

  factory SusuPlanDetail.fromJson(Object? data) {
    final json = asJsonMap(data, 'susu plan');
    final plan = SusuPlan.fromJson(asJsonMap(json['plan'], 'plan'));
    return SusuPlanDetail(
      plan: plan,
      contributions: [for (final item in readObjectList(json, 'contributions')) SusuContribution.fromJson(item, plan.currency)],
      commissions: [
        for (final item in readObjectList(json, 'commissions')) (cycle: _int(item, 'cycleNo'), amount: _money(item, 'amountCharged', plan.currency)),
      ],
    );
  }

  final SusuPlan plan;
  final List<SusuContribution> contributions;

  /// The collector's commission charged for each closed cycle.
  final List<({int cycle, Money amount})> commissions;
}

// ------------------------------------------------------------------------------------------- notifications

class CustomerNotification {
  const CustomerNotification({
    required this.id,
    required this.category,
    required this.title,
    required this.body,
    required this.createdAt,
    required this.read,
    this.referenceType,
    this.referenceId,
  });

  factory CustomerNotification.fromJson(JsonMap json) => CustomerNotification(
        id: readString(json, 'id'),
        category: readString(json, 'category'),
        title: readString(json, 'title'),
        body: readString(json, 'body'),
        referenceType: readOptionalString(json, 'referenceType'),
        referenceId: readOptionalString(json, 'referenceId'),
        createdAt: _requiredInstant(json, 'createdAt'),
        read: readBool(json, 'read', fallback: false),
      );

  final String id;

  /// TRANSACTION, SECURITY, LOAN, ACCOUNT or GENERAL.
  final String category;
  final String title;
  final String body;
  final String? referenceType;
  final String? referenceId;
  final DateTime createdAt;
  final bool read;
}

class NotificationPage {
  const NotificationPage({required this.items, required this.page, required this.totalPages});

  factory NotificationPage.fromJson(Object? data) {
    final json = asJsonMap(data, 'notifications');
    return NotificationPage(
      items: readObjectList(json, 'items').map(CustomerNotification.fromJson).toList(),
      page: _int(json, 'page'),
      totalPages: _int(json, 'totalPages'),
    );
  }

  final List<CustomerNotification> items;
  final int page;
  final int totalPages;
}

// ------------------------------------------------------------------------------------------------ security

class TrustedDevice {
  const TrustedDevice({required this.id, required this.name, required this.status, required this.boundAt, required this.current, this.platform, this.lastSeenAt});

  factory TrustedDevice.fromJson(JsonMap json) => TrustedDevice(
        id: readString(json, 'id'),
        name: readString(json, 'name'),
        platform: readOptionalString(json, 'platform'),
        status: readString(json, 'status'),
        boundAt: _requiredInstant(json, 'boundAt'),
        lastSeenAt: readOptionalInstant(json, 'lastSeenAt'),
        current: readBool(json, 'current', fallback: false),
      );

  final String id;
  final String name;
  final String? platform;
  final String status;
  final DateTime boundAt;
  final DateTime? lastSeenAt;

  /// This installation.
  final bool current;
}

class SignedInSession {
  const SignedInSession({required this.id, required this.status, required this.createdAt, required this.current, this.deviceName, this.lastActiveAt});

  factory SignedInSession.fromJson(JsonMap json) => SignedInSession(
        id: readString(json, 'id'),
        deviceName: readOptionalString(json, 'deviceName'),
        status: readString(json, 'status'),
        createdAt: _requiredInstant(json, 'createdAt'),
        lastActiveAt: readOptionalInstant(json, 'lastActiveAt'),
        current: readBool(json, 'current', fallback: false),
      );

  final String id;
  final String? deviceName;
  final String status;
  final DateTime createdAt;
  final DateTime? lastActiveAt;
  final bool current;
}

List<T> parseList<T>(Object? data, T Function(JsonMap json) parse) => _list(data, parse);
