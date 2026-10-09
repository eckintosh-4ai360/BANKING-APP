// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'field_database.dart';

// ignore_for_file: type=lint
class $QueuedCollectionsTable extends QueuedCollections
    with TableInfo<$QueuedCollectionsTable, QueuedCollection> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $QueuedCollectionsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _clientReferenceMeta = const VerificationMeta(
    'clientReference',
  );
  @override
  late final GeneratedColumn<String> clientReference = GeneratedColumn<String>(
    'client_reference',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _sequenceNoMeta = const VerificationMeta(
    'sequenceNo',
  );
  @override
  late final GeneratedColumn<int> sequenceNo = GeneratedColumn<int>(
    'sequence_no',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways('UNIQUE'),
  );
  static const VerificationMeta _customerIdMeta = const VerificationMeta(
    'customerId',
  );
  @override
  late final GeneratedColumn<String> customerId = GeneratedColumn<String>(
    'customer_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _customerNameMeta = const VerificationMeta(
    'customerName',
  );
  @override
  late final GeneratedColumn<String> customerName = GeneratedColumn<String>(
    'customer_name',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _accountIdMeta = const VerificationMeta(
    'accountId',
  );
  @override
  late final GeneratedColumn<String> accountId = GeneratedColumn<String>(
    'account_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _accountNumberMeta = const VerificationMeta(
    'accountNumber',
  );
  @override
  late final GeneratedColumn<String> accountNumber = GeneratedColumn<String>(
    'account_number',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _susuPlanIdMeta = const VerificationMeta(
    'susuPlanId',
  );
  @override
  late final GeneratedColumn<String> susuPlanId = GeneratedColumn<String>(
    'susu_plan_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _amountMeta = const VerificationMeta('amount');
  @override
  late final GeneratedColumn<String> amount = GeneratedColumn<String>(
    'amount',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _currencyMeta = const VerificationMeta(
    'currency',
  );
  @override
  late final GeneratedColumn<String> currency = GeneratedColumn<String>(
    'currency',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _collectedAtMeta = const VerificationMeta(
    'collectedAt',
  );
  @override
  late final GeneratedColumn<String> collectedAt = GeneratedColumn<String>(
    'collected_at',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _noteMeta = const VerificationMeta('note');
  @override
  late final GeneratedColumn<String> note = GeneratedColumn<String>(
    'note',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  @override
  late final GeneratedColumnWithTypeConverter<QueueStatus, String> status =
      GeneratedColumn<String>(
        'status',
        aliasedName,
        false,
        type: DriftSqlType.string,
        requiredDuringInsert: true,
      ).withConverter<QueueStatus>($QueuedCollectionsTable.$converterstatus);
  static const VerificationMeta _serverCodeMeta = const VerificationMeta(
    'serverCode',
  );
  @override
  late final GeneratedColumn<String> serverCode = GeneratedColumn<String>(
    'server_code',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _serverMessageMeta = const VerificationMeta(
    'serverMessage',
  );
  @override
  late final GeneratedColumn<String> serverMessage = GeneratedColumn<String>(
    'server_message',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _transactionReferenceMeta =
      const VerificationMeta('transactionReference');
  @override
  late final GeneratedColumn<String> transactionReference =
      GeneratedColumn<String>(
        'transaction_reference',
        aliasedName,
        true,
        type: DriftSqlType.string,
        requiredDuringInsert: false,
      );
  static const VerificationMeta _balanceAfterMeta = const VerificationMeta(
    'balanceAfter',
  );
  @override
  late final GeneratedColumn<String> balanceAfter = GeneratedColumn<String>(
    'balance_after',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _attemptsMeta = const VerificationMeta(
    'attempts',
  );
  @override
  late final GeneratedColumn<int> attempts = GeneratedColumn<int>(
    'attempts',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  static const VerificationMeta _lastAttemptAtMeta = const VerificationMeta(
    'lastAttemptAt',
  );
  @override
  late final GeneratedColumn<String> lastAttemptAt = GeneratedColumn<String>(
    'last_attempt_at',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    clientReference,
    sequenceNo,
    customerId,
    customerName,
    accountId,
    accountNumber,
    susuPlanId,
    amount,
    currency,
    collectedAt,
    note,
    status,
    serverCode,
    serverMessage,
    transactionReference,
    balanceAfter,
    attempts,
    lastAttemptAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'queued_collections';
  @override
  VerificationContext validateIntegrity(
    Insertable<QueuedCollection> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('client_reference')) {
      context.handle(
        _clientReferenceMeta,
        clientReference.isAcceptableOrUnknown(
          data['client_reference']!,
          _clientReferenceMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_clientReferenceMeta);
    }
    if (data.containsKey('sequence_no')) {
      context.handle(
        _sequenceNoMeta,
        sequenceNo.isAcceptableOrUnknown(data['sequence_no']!, _sequenceNoMeta),
      );
    } else if (isInserting) {
      context.missing(_sequenceNoMeta);
    }
    if (data.containsKey('customer_id')) {
      context.handle(
        _customerIdMeta,
        customerId.isAcceptableOrUnknown(data['customer_id']!, _customerIdMeta),
      );
    } else if (isInserting) {
      context.missing(_customerIdMeta);
    }
    if (data.containsKey('customer_name')) {
      context.handle(
        _customerNameMeta,
        customerName.isAcceptableOrUnknown(
          data['customer_name']!,
          _customerNameMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_customerNameMeta);
    }
    if (data.containsKey('account_id')) {
      context.handle(
        _accountIdMeta,
        accountId.isAcceptableOrUnknown(data['account_id']!, _accountIdMeta),
      );
    } else if (isInserting) {
      context.missing(_accountIdMeta);
    }
    if (data.containsKey('account_number')) {
      context.handle(
        _accountNumberMeta,
        accountNumber.isAcceptableOrUnknown(
          data['account_number']!,
          _accountNumberMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_accountNumberMeta);
    }
    if (data.containsKey('susu_plan_id')) {
      context.handle(
        _susuPlanIdMeta,
        susuPlanId.isAcceptableOrUnknown(
          data['susu_plan_id']!,
          _susuPlanIdMeta,
        ),
      );
    }
    if (data.containsKey('amount')) {
      context.handle(
        _amountMeta,
        amount.isAcceptableOrUnknown(data['amount']!, _amountMeta),
      );
    } else if (isInserting) {
      context.missing(_amountMeta);
    }
    if (data.containsKey('currency')) {
      context.handle(
        _currencyMeta,
        currency.isAcceptableOrUnknown(data['currency']!, _currencyMeta),
      );
    } else if (isInserting) {
      context.missing(_currencyMeta);
    }
    if (data.containsKey('collected_at')) {
      context.handle(
        _collectedAtMeta,
        collectedAt.isAcceptableOrUnknown(
          data['collected_at']!,
          _collectedAtMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_collectedAtMeta);
    }
    if (data.containsKey('note')) {
      context.handle(
        _noteMeta,
        note.isAcceptableOrUnknown(data['note']!, _noteMeta),
      );
    }
    if (data.containsKey('server_code')) {
      context.handle(
        _serverCodeMeta,
        serverCode.isAcceptableOrUnknown(data['server_code']!, _serverCodeMeta),
      );
    }
    if (data.containsKey('server_message')) {
      context.handle(
        _serverMessageMeta,
        serverMessage.isAcceptableOrUnknown(
          data['server_message']!,
          _serverMessageMeta,
        ),
      );
    }
    if (data.containsKey('transaction_reference')) {
      context.handle(
        _transactionReferenceMeta,
        transactionReference.isAcceptableOrUnknown(
          data['transaction_reference']!,
          _transactionReferenceMeta,
        ),
      );
    }
    if (data.containsKey('balance_after')) {
      context.handle(
        _balanceAfterMeta,
        balanceAfter.isAcceptableOrUnknown(
          data['balance_after']!,
          _balanceAfterMeta,
        ),
      );
    }
    if (data.containsKey('attempts')) {
      context.handle(
        _attemptsMeta,
        attempts.isAcceptableOrUnknown(data['attempts']!, _attemptsMeta),
      );
    }
    if (data.containsKey('last_attempt_at')) {
      context.handle(
        _lastAttemptAtMeta,
        lastAttemptAt.isAcceptableOrUnknown(
          data['last_attempt_at']!,
          _lastAttemptAtMeta,
        ),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {clientReference};
  @override
  QueuedCollection map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return QueuedCollection(
      clientReference: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}client_reference'],
      )!,
      sequenceNo: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}sequence_no'],
      )!,
      customerId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}customer_id'],
      )!,
      customerName: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}customer_name'],
      )!,
      accountId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}account_id'],
      )!,
      accountNumber: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}account_number'],
      )!,
      susuPlanId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}susu_plan_id'],
      ),
      amount: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}amount'],
      )!,
      currency: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}currency'],
      )!,
      collectedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}collected_at'],
      )!,
      note: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}note'],
      ),
      status: $QueuedCollectionsTable.$converterstatus.fromSql(
        attachedDatabase.typeMapping.read(
          DriftSqlType.string,
          data['${effectivePrefix}status'],
        )!,
      ),
      serverCode: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}server_code'],
      ),
      serverMessage: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}server_message'],
      ),
      transactionReference: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}transaction_reference'],
      ),
      balanceAfter: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}balance_after'],
      ),
      attempts: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}attempts'],
      )!,
      lastAttemptAt: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}last_attempt_at'],
      ),
    );
  }

  @override
  $QueuedCollectionsTable createAlias(String alias) {
    return $QueuedCollectionsTable(attachedDatabase, alias);
  }

  static JsonTypeConverter2<QueueStatus, String, String> $converterstatus =
      const EnumNameConverter<QueueStatus>(QueueStatus.values);
}

class QueuedCollection extends DataClass
    implements Insertable<QueuedCollection> {
  final String clientReference;
  final int sequenceNo;
  final String customerId;
  final String customerName;
  final String accountId;
  final String accountNumber;
  final String? susuPlanId;
  final String amount;
  final String currency;
  final String collectedAt;
  final String? note;
  final QueueStatus status;
  final String? serverCode;
  final String? serverMessage;
  final String? transactionReference;
  final String? balanceAfter;
  final int attempts;
  final String? lastAttemptAt;
  const QueuedCollection({
    required this.clientReference,
    required this.sequenceNo,
    required this.customerId,
    required this.customerName,
    required this.accountId,
    required this.accountNumber,
    this.susuPlanId,
    required this.amount,
    required this.currency,
    required this.collectedAt,
    this.note,
    required this.status,
    this.serverCode,
    this.serverMessage,
    this.transactionReference,
    this.balanceAfter,
    required this.attempts,
    this.lastAttemptAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['client_reference'] = Variable<String>(clientReference);
    map['sequence_no'] = Variable<int>(sequenceNo);
    map['customer_id'] = Variable<String>(customerId);
    map['customer_name'] = Variable<String>(customerName);
    map['account_id'] = Variable<String>(accountId);
    map['account_number'] = Variable<String>(accountNumber);
    if (!nullToAbsent || susuPlanId != null) {
      map['susu_plan_id'] = Variable<String>(susuPlanId);
    }
    map['amount'] = Variable<String>(amount);
    map['currency'] = Variable<String>(currency);
    map['collected_at'] = Variable<String>(collectedAt);
    if (!nullToAbsent || note != null) {
      map['note'] = Variable<String>(note);
    }
    {
      map['status'] = Variable<String>(
        $QueuedCollectionsTable.$converterstatus.toSql(status),
      );
    }
    if (!nullToAbsent || serverCode != null) {
      map['server_code'] = Variable<String>(serverCode);
    }
    if (!nullToAbsent || serverMessage != null) {
      map['server_message'] = Variable<String>(serverMessage);
    }
    if (!nullToAbsent || transactionReference != null) {
      map['transaction_reference'] = Variable<String>(transactionReference);
    }
    if (!nullToAbsent || balanceAfter != null) {
      map['balance_after'] = Variable<String>(balanceAfter);
    }
    map['attempts'] = Variable<int>(attempts);
    if (!nullToAbsent || lastAttemptAt != null) {
      map['last_attempt_at'] = Variable<String>(lastAttemptAt);
    }
    return map;
  }

  QueuedCollectionsCompanion toCompanion(bool nullToAbsent) {
    return QueuedCollectionsCompanion(
      clientReference: Value(clientReference),
      sequenceNo: Value(sequenceNo),
      customerId: Value(customerId),
      customerName: Value(customerName),
      accountId: Value(accountId),
      accountNumber: Value(accountNumber),
      susuPlanId: susuPlanId == null && nullToAbsent
          ? const Value.absent()
          : Value(susuPlanId),
      amount: Value(amount),
      currency: Value(currency),
      collectedAt: Value(collectedAt),
      note: note == null && nullToAbsent ? const Value.absent() : Value(note),
      status: Value(status),
      serverCode: serverCode == null && nullToAbsent
          ? const Value.absent()
          : Value(serverCode),
      serverMessage: serverMessage == null && nullToAbsent
          ? const Value.absent()
          : Value(serverMessage),
      transactionReference: transactionReference == null && nullToAbsent
          ? const Value.absent()
          : Value(transactionReference),
      balanceAfter: balanceAfter == null && nullToAbsent
          ? const Value.absent()
          : Value(balanceAfter),
      attempts: Value(attempts),
      lastAttemptAt: lastAttemptAt == null && nullToAbsent
          ? const Value.absent()
          : Value(lastAttemptAt),
    );
  }

  factory QueuedCollection.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return QueuedCollection(
      clientReference: serializer.fromJson<String>(json['clientReference']),
      sequenceNo: serializer.fromJson<int>(json['sequenceNo']),
      customerId: serializer.fromJson<String>(json['customerId']),
      customerName: serializer.fromJson<String>(json['customerName']),
      accountId: serializer.fromJson<String>(json['accountId']),
      accountNumber: serializer.fromJson<String>(json['accountNumber']),
      susuPlanId: serializer.fromJson<String?>(json['susuPlanId']),
      amount: serializer.fromJson<String>(json['amount']),
      currency: serializer.fromJson<String>(json['currency']),
      collectedAt: serializer.fromJson<String>(json['collectedAt']),
      note: serializer.fromJson<String?>(json['note']),
      status: $QueuedCollectionsTable.$converterstatus.fromJson(
        serializer.fromJson<String>(json['status']),
      ),
      serverCode: serializer.fromJson<String?>(json['serverCode']),
      serverMessage: serializer.fromJson<String?>(json['serverMessage']),
      transactionReference: serializer.fromJson<String?>(
        json['transactionReference'],
      ),
      balanceAfter: serializer.fromJson<String?>(json['balanceAfter']),
      attempts: serializer.fromJson<int>(json['attempts']),
      lastAttemptAt: serializer.fromJson<String?>(json['lastAttemptAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'clientReference': serializer.toJson<String>(clientReference),
      'sequenceNo': serializer.toJson<int>(sequenceNo),
      'customerId': serializer.toJson<String>(customerId),
      'customerName': serializer.toJson<String>(customerName),
      'accountId': serializer.toJson<String>(accountId),
      'accountNumber': serializer.toJson<String>(accountNumber),
      'susuPlanId': serializer.toJson<String?>(susuPlanId),
      'amount': serializer.toJson<String>(amount),
      'currency': serializer.toJson<String>(currency),
      'collectedAt': serializer.toJson<String>(collectedAt),
      'note': serializer.toJson<String?>(note),
      'status': serializer.toJson<String>(
        $QueuedCollectionsTable.$converterstatus.toJson(status),
      ),
      'serverCode': serializer.toJson<String?>(serverCode),
      'serverMessage': serializer.toJson<String?>(serverMessage),
      'transactionReference': serializer.toJson<String?>(transactionReference),
      'balanceAfter': serializer.toJson<String?>(balanceAfter),
      'attempts': serializer.toJson<int>(attempts),
      'lastAttemptAt': serializer.toJson<String?>(lastAttemptAt),
    };
  }

  QueuedCollection copyWith({
    String? clientReference,
    int? sequenceNo,
    String? customerId,
    String? customerName,
    String? accountId,
    String? accountNumber,
    Value<String?> susuPlanId = const Value.absent(),
    String? amount,
    String? currency,
    String? collectedAt,
    Value<String?> note = const Value.absent(),
    QueueStatus? status,
    Value<String?> serverCode = const Value.absent(),
    Value<String?> serverMessage = const Value.absent(),
    Value<String?> transactionReference = const Value.absent(),
    Value<String?> balanceAfter = const Value.absent(),
    int? attempts,
    Value<String?> lastAttemptAt = const Value.absent(),
  }) => QueuedCollection(
    clientReference: clientReference ?? this.clientReference,
    sequenceNo: sequenceNo ?? this.sequenceNo,
    customerId: customerId ?? this.customerId,
    customerName: customerName ?? this.customerName,
    accountId: accountId ?? this.accountId,
    accountNumber: accountNumber ?? this.accountNumber,
    susuPlanId: susuPlanId.present ? susuPlanId.value : this.susuPlanId,
    amount: amount ?? this.amount,
    currency: currency ?? this.currency,
    collectedAt: collectedAt ?? this.collectedAt,
    note: note.present ? note.value : this.note,
    status: status ?? this.status,
    serverCode: serverCode.present ? serverCode.value : this.serverCode,
    serverMessage: serverMessage.present
        ? serverMessage.value
        : this.serverMessage,
    transactionReference: transactionReference.present
        ? transactionReference.value
        : this.transactionReference,
    balanceAfter: balanceAfter.present ? balanceAfter.value : this.balanceAfter,
    attempts: attempts ?? this.attempts,
    lastAttemptAt: lastAttemptAt.present
        ? lastAttemptAt.value
        : this.lastAttemptAt,
  );
  QueuedCollection copyWithCompanion(QueuedCollectionsCompanion data) {
    return QueuedCollection(
      clientReference: data.clientReference.present
          ? data.clientReference.value
          : this.clientReference,
      sequenceNo: data.sequenceNo.present
          ? data.sequenceNo.value
          : this.sequenceNo,
      customerId: data.customerId.present
          ? data.customerId.value
          : this.customerId,
      customerName: data.customerName.present
          ? data.customerName.value
          : this.customerName,
      accountId: data.accountId.present ? data.accountId.value : this.accountId,
      accountNumber: data.accountNumber.present
          ? data.accountNumber.value
          : this.accountNumber,
      susuPlanId: data.susuPlanId.present
          ? data.susuPlanId.value
          : this.susuPlanId,
      amount: data.amount.present ? data.amount.value : this.amount,
      currency: data.currency.present ? data.currency.value : this.currency,
      collectedAt: data.collectedAt.present
          ? data.collectedAt.value
          : this.collectedAt,
      note: data.note.present ? data.note.value : this.note,
      status: data.status.present ? data.status.value : this.status,
      serverCode: data.serverCode.present
          ? data.serverCode.value
          : this.serverCode,
      serverMessage: data.serverMessage.present
          ? data.serverMessage.value
          : this.serverMessage,
      transactionReference: data.transactionReference.present
          ? data.transactionReference.value
          : this.transactionReference,
      balanceAfter: data.balanceAfter.present
          ? data.balanceAfter.value
          : this.balanceAfter,
      attempts: data.attempts.present ? data.attempts.value : this.attempts,
      lastAttemptAt: data.lastAttemptAt.present
          ? data.lastAttemptAt.value
          : this.lastAttemptAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('QueuedCollection(')
          ..write('clientReference: $clientReference, ')
          ..write('sequenceNo: $sequenceNo, ')
          ..write('customerId: $customerId, ')
          ..write('customerName: $customerName, ')
          ..write('accountId: $accountId, ')
          ..write('accountNumber: $accountNumber, ')
          ..write('susuPlanId: $susuPlanId, ')
          ..write('amount: $amount, ')
          ..write('currency: $currency, ')
          ..write('collectedAt: $collectedAt, ')
          ..write('note: $note, ')
          ..write('status: $status, ')
          ..write('serverCode: $serverCode, ')
          ..write('serverMessage: $serverMessage, ')
          ..write('transactionReference: $transactionReference, ')
          ..write('balanceAfter: $balanceAfter, ')
          ..write('attempts: $attempts, ')
          ..write('lastAttemptAt: $lastAttemptAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    clientReference,
    sequenceNo,
    customerId,
    customerName,
    accountId,
    accountNumber,
    susuPlanId,
    amount,
    currency,
    collectedAt,
    note,
    status,
    serverCode,
    serverMessage,
    transactionReference,
    balanceAfter,
    attempts,
    lastAttemptAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is QueuedCollection &&
          other.clientReference == this.clientReference &&
          other.sequenceNo == this.sequenceNo &&
          other.customerId == this.customerId &&
          other.customerName == this.customerName &&
          other.accountId == this.accountId &&
          other.accountNumber == this.accountNumber &&
          other.susuPlanId == this.susuPlanId &&
          other.amount == this.amount &&
          other.currency == this.currency &&
          other.collectedAt == this.collectedAt &&
          other.note == this.note &&
          other.status == this.status &&
          other.serverCode == this.serverCode &&
          other.serverMessage == this.serverMessage &&
          other.transactionReference == this.transactionReference &&
          other.balanceAfter == this.balanceAfter &&
          other.attempts == this.attempts &&
          other.lastAttemptAt == this.lastAttemptAt);
}

class QueuedCollectionsCompanion extends UpdateCompanion<QueuedCollection> {
  final Value<String> clientReference;
  final Value<int> sequenceNo;
  final Value<String> customerId;
  final Value<String> customerName;
  final Value<String> accountId;
  final Value<String> accountNumber;
  final Value<String?> susuPlanId;
  final Value<String> amount;
  final Value<String> currency;
  final Value<String> collectedAt;
  final Value<String?> note;
  final Value<QueueStatus> status;
  final Value<String?> serverCode;
  final Value<String?> serverMessage;
  final Value<String?> transactionReference;
  final Value<String?> balanceAfter;
  final Value<int> attempts;
  final Value<String?> lastAttemptAt;
  final Value<int> rowid;
  const QueuedCollectionsCompanion({
    this.clientReference = const Value.absent(),
    this.sequenceNo = const Value.absent(),
    this.customerId = const Value.absent(),
    this.customerName = const Value.absent(),
    this.accountId = const Value.absent(),
    this.accountNumber = const Value.absent(),
    this.susuPlanId = const Value.absent(),
    this.amount = const Value.absent(),
    this.currency = const Value.absent(),
    this.collectedAt = const Value.absent(),
    this.note = const Value.absent(),
    this.status = const Value.absent(),
    this.serverCode = const Value.absent(),
    this.serverMessage = const Value.absent(),
    this.transactionReference = const Value.absent(),
    this.balanceAfter = const Value.absent(),
    this.attempts = const Value.absent(),
    this.lastAttemptAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  QueuedCollectionsCompanion.insert({
    required String clientReference,
    required int sequenceNo,
    required String customerId,
    required String customerName,
    required String accountId,
    required String accountNumber,
    this.susuPlanId = const Value.absent(),
    required String amount,
    required String currency,
    required String collectedAt,
    this.note = const Value.absent(),
    required QueueStatus status,
    this.serverCode = const Value.absent(),
    this.serverMessage = const Value.absent(),
    this.transactionReference = const Value.absent(),
    this.balanceAfter = const Value.absent(),
    this.attempts = const Value.absent(),
    this.lastAttemptAt = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : clientReference = Value(clientReference),
       sequenceNo = Value(sequenceNo),
       customerId = Value(customerId),
       customerName = Value(customerName),
       accountId = Value(accountId),
       accountNumber = Value(accountNumber),
       amount = Value(amount),
       currency = Value(currency),
       collectedAt = Value(collectedAt),
       status = Value(status);
  static Insertable<QueuedCollection> custom({
    Expression<String>? clientReference,
    Expression<int>? sequenceNo,
    Expression<String>? customerId,
    Expression<String>? customerName,
    Expression<String>? accountId,
    Expression<String>? accountNumber,
    Expression<String>? susuPlanId,
    Expression<String>? amount,
    Expression<String>? currency,
    Expression<String>? collectedAt,
    Expression<String>? note,
    Expression<String>? status,
    Expression<String>? serverCode,
    Expression<String>? serverMessage,
    Expression<String>? transactionReference,
    Expression<String>? balanceAfter,
    Expression<int>? attempts,
    Expression<String>? lastAttemptAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (clientReference != null) 'client_reference': clientReference,
      if (sequenceNo != null) 'sequence_no': sequenceNo,
      if (customerId != null) 'customer_id': customerId,
      if (customerName != null) 'customer_name': customerName,
      if (accountId != null) 'account_id': accountId,
      if (accountNumber != null) 'account_number': accountNumber,
      if (susuPlanId != null) 'susu_plan_id': susuPlanId,
      if (amount != null) 'amount': amount,
      if (currency != null) 'currency': currency,
      if (collectedAt != null) 'collected_at': collectedAt,
      if (note != null) 'note': note,
      if (status != null) 'status': status,
      if (serverCode != null) 'server_code': serverCode,
      if (serverMessage != null) 'server_message': serverMessage,
      if (transactionReference != null)
        'transaction_reference': transactionReference,
      if (balanceAfter != null) 'balance_after': balanceAfter,
      if (attempts != null) 'attempts': attempts,
      if (lastAttemptAt != null) 'last_attempt_at': lastAttemptAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  QueuedCollectionsCompanion copyWith({
    Value<String>? clientReference,
    Value<int>? sequenceNo,
    Value<String>? customerId,
    Value<String>? customerName,
    Value<String>? accountId,
    Value<String>? accountNumber,
    Value<String?>? susuPlanId,
    Value<String>? amount,
    Value<String>? currency,
    Value<String>? collectedAt,
    Value<String?>? note,
    Value<QueueStatus>? status,
    Value<String?>? serverCode,
    Value<String?>? serverMessage,
    Value<String?>? transactionReference,
    Value<String?>? balanceAfter,
    Value<int>? attempts,
    Value<String?>? lastAttemptAt,
    Value<int>? rowid,
  }) {
    return QueuedCollectionsCompanion(
      clientReference: clientReference ?? this.clientReference,
      sequenceNo: sequenceNo ?? this.sequenceNo,
      customerId: customerId ?? this.customerId,
      customerName: customerName ?? this.customerName,
      accountId: accountId ?? this.accountId,
      accountNumber: accountNumber ?? this.accountNumber,
      susuPlanId: susuPlanId ?? this.susuPlanId,
      amount: amount ?? this.amount,
      currency: currency ?? this.currency,
      collectedAt: collectedAt ?? this.collectedAt,
      note: note ?? this.note,
      status: status ?? this.status,
      serverCode: serverCode ?? this.serverCode,
      serverMessage: serverMessage ?? this.serverMessage,
      transactionReference: transactionReference ?? this.transactionReference,
      balanceAfter: balanceAfter ?? this.balanceAfter,
      attempts: attempts ?? this.attempts,
      lastAttemptAt: lastAttemptAt ?? this.lastAttemptAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (clientReference.present) {
      map['client_reference'] = Variable<String>(clientReference.value);
    }
    if (sequenceNo.present) {
      map['sequence_no'] = Variable<int>(sequenceNo.value);
    }
    if (customerId.present) {
      map['customer_id'] = Variable<String>(customerId.value);
    }
    if (customerName.present) {
      map['customer_name'] = Variable<String>(customerName.value);
    }
    if (accountId.present) {
      map['account_id'] = Variable<String>(accountId.value);
    }
    if (accountNumber.present) {
      map['account_number'] = Variable<String>(accountNumber.value);
    }
    if (susuPlanId.present) {
      map['susu_plan_id'] = Variable<String>(susuPlanId.value);
    }
    if (amount.present) {
      map['amount'] = Variable<String>(amount.value);
    }
    if (currency.present) {
      map['currency'] = Variable<String>(currency.value);
    }
    if (collectedAt.present) {
      map['collected_at'] = Variable<String>(collectedAt.value);
    }
    if (note.present) {
      map['note'] = Variable<String>(note.value);
    }
    if (status.present) {
      map['status'] = Variable<String>(
        $QueuedCollectionsTable.$converterstatus.toSql(status.value),
      );
    }
    if (serverCode.present) {
      map['server_code'] = Variable<String>(serverCode.value);
    }
    if (serverMessage.present) {
      map['server_message'] = Variable<String>(serverMessage.value);
    }
    if (transactionReference.present) {
      map['transaction_reference'] = Variable<String>(
        transactionReference.value,
      );
    }
    if (balanceAfter.present) {
      map['balance_after'] = Variable<String>(balanceAfter.value);
    }
    if (attempts.present) {
      map['attempts'] = Variable<int>(attempts.value);
    }
    if (lastAttemptAt.present) {
      map['last_attempt_at'] = Variable<String>(lastAttemptAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('QueuedCollectionsCompanion(')
          ..write('clientReference: $clientReference, ')
          ..write('sequenceNo: $sequenceNo, ')
          ..write('customerId: $customerId, ')
          ..write('customerName: $customerName, ')
          ..write('accountId: $accountId, ')
          ..write('accountNumber: $accountNumber, ')
          ..write('susuPlanId: $susuPlanId, ')
          ..write('amount: $amount, ')
          ..write('currency: $currency, ')
          ..write('collectedAt: $collectedAt, ')
          ..write('note: $note, ')
          ..write('status: $status, ')
          ..write('serverCode: $serverCode, ')
          ..write('serverMessage: $serverMessage, ')
          ..write('transactionReference: $transactionReference, ')
          ..write('balanceAfter: $balanceAfter, ')
          ..write('attempts: $attempts, ')
          ..write('lastAttemptAt: $lastAttemptAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $QueuedVisitsTable extends QueuedVisits
    with TableInfo<$QueuedVisitsTable, QueuedVisit> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $QueuedVisitsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _clientReferenceMeta = const VerificationMeta(
    'clientReference',
  );
  @override
  late final GeneratedColumn<String> clientReference = GeneratedColumn<String>(
    'client_reference',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _customerIdMeta = const VerificationMeta(
    'customerId',
  );
  @override
  late final GeneratedColumn<String> customerId = GeneratedColumn<String>(
    'customer_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _customerNameMeta = const VerificationMeta(
    'customerName',
  );
  @override
  late final GeneratedColumn<String> customerName = GeneratedColumn<String>(
    'customer_name',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _purposeMeta = const VerificationMeta(
    'purpose',
  );
  @override
  late final GeneratedColumn<String> purpose = GeneratedColumn<String>(
    'purpose',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _outcomeMeta = const VerificationMeta(
    'outcome',
  );
  @override
  late final GeneratedColumn<String> outcome = GeneratedColumn<String>(
    'outcome',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _notesMeta = const VerificationMeta('notes');
  @override
  late final GeneratedColumn<String> notes = GeneratedColumn<String>(
    'notes',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _visitedAtMeta = const VerificationMeta(
    'visitedAt',
  );
  @override
  late final GeneratedColumn<String> visitedAt = GeneratedColumn<String>(
    'visited_at',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  @override
  late final GeneratedColumnWithTypeConverter<QueueStatus, String> status =
      GeneratedColumn<String>(
        'status',
        aliasedName,
        false,
        type: DriftSqlType.string,
        requiredDuringInsert: true,
      ).withConverter<QueueStatus>($QueuedVisitsTable.$converterstatus);
  static const VerificationMeta _serverCodeMeta = const VerificationMeta(
    'serverCode',
  );
  @override
  late final GeneratedColumn<String> serverCode = GeneratedColumn<String>(
    'server_code',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _serverMessageMeta = const VerificationMeta(
    'serverMessage',
  );
  @override
  late final GeneratedColumn<String> serverMessage = GeneratedColumn<String>(
    'server_message',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _attemptsMeta = const VerificationMeta(
    'attempts',
  );
  @override
  late final GeneratedColumn<int> attempts = GeneratedColumn<int>(
    'attempts',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  @override
  List<GeneratedColumn> get $columns => [
    clientReference,
    customerId,
    customerName,
    purpose,
    outcome,
    notes,
    visitedAt,
    status,
    serverCode,
    serverMessage,
    attempts,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'queued_visits';
  @override
  VerificationContext validateIntegrity(
    Insertable<QueuedVisit> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('client_reference')) {
      context.handle(
        _clientReferenceMeta,
        clientReference.isAcceptableOrUnknown(
          data['client_reference']!,
          _clientReferenceMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_clientReferenceMeta);
    }
    if (data.containsKey('customer_id')) {
      context.handle(
        _customerIdMeta,
        customerId.isAcceptableOrUnknown(data['customer_id']!, _customerIdMeta),
      );
    } else if (isInserting) {
      context.missing(_customerIdMeta);
    }
    if (data.containsKey('customer_name')) {
      context.handle(
        _customerNameMeta,
        customerName.isAcceptableOrUnknown(
          data['customer_name']!,
          _customerNameMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_customerNameMeta);
    }
    if (data.containsKey('purpose')) {
      context.handle(
        _purposeMeta,
        purpose.isAcceptableOrUnknown(data['purpose']!, _purposeMeta),
      );
    } else if (isInserting) {
      context.missing(_purposeMeta);
    }
    if (data.containsKey('outcome')) {
      context.handle(
        _outcomeMeta,
        outcome.isAcceptableOrUnknown(data['outcome']!, _outcomeMeta),
      );
    } else if (isInserting) {
      context.missing(_outcomeMeta);
    }
    if (data.containsKey('notes')) {
      context.handle(
        _notesMeta,
        notes.isAcceptableOrUnknown(data['notes']!, _notesMeta),
      );
    }
    if (data.containsKey('visited_at')) {
      context.handle(
        _visitedAtMeta,
        visitedAt.isAcceptableOrUnknown(data['visited_at']!, _visitedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_visitedAtMeta);
    }
    if (data.containsKey('server_code')) {
      context.handle(
        _serverCodeMeta,
        serverCode.isAcceptableOrUnknown(data['server_code']!, _serverCodeMeta),
      );
    }
    if (data.containsKey('server_message')) {
      context.handle(
        _serverMessageMeta,
        serverMessage.isAcceptableOrUnknown(
          data['server_message']!,
          _serverMessageMeta,
        ),
      );
    }
    if (data.containsKey('attempts')) {
      context.handle(
        _attemptsMeta,
        attempts.isAcceptableOrUnknown(data['attempts']!, _attemptsMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {clientReference};
  @override
  QueuedVisit map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return QueuedVisit(
      clientReference: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}client_reference'],
      )!,
      customerId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}customer_id'],
      )!,
      customerName: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}customer_name'],
      )!,
      purpose: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}purpose'],
      )!,
      outcome: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}outcome'],
      )!,
      notes: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}notes'],
      ),
      visitedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}visited_at'],
      )!,
      status: $QueuedVisitsTable.$converterstatus.fromSql(
        attachedDatabase.typeMapping.read(
          DriftSqlType.string,
          data['${effectivePrefix}status'],
        )!,
      ),
      serverCode: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}server_code'],
      ),
      serverMessage: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}server_message'],
      ),
      attempts: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}attempts'],
      )!,
    );
  }

  @override
  $QueuedVisitsTable createAlias(String alias) {
    return $QueuedVisitsTable(attachedDatabase, alias);
  }

  static JsonTypeConverter2<QueueStatus, String, String> $converterstatus =
      const EnumNameConverter<QueueStatus>(QueueStatus.values);
}

class QueuedVisit extends DataClass implements Insertable<QueuedVisit> {
  final String clientReference;
  final String customerId;
  final String customerName;
  final String purpose;
  final String outcome;
  final String? notes;
  final String visitedAt;
  final QueueStatus status;
  final String? serverCode;
  final String? serverMessage;
  final int attempts;
  const QueuedVisit({
    required this.clientReference,
    required this.customerId,
    required this.customerName,
    required this.purpose,
    required this.outcome,
    this.notes,
    required this.visitedAt,
    required this.status,
    this.serverCode,
    this.serverMessage,
    required this.attempts,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['client_reference'] = Variable<String>(clientReference);
    map['customer_id'] = Variable<String>(customerId);
    map['customer_name'] = Variable<String>(customerName);
    map['purpose'] = Variable<String>(purpose);
    map['outcome'] = Variable<String>(outcome);
    if (!nullToAbsent || notes != null) {
      map['notes'] = Variable<String>(notes);
    }
    map['visited_at'] = Variable<String>(visitedAt);
    {
      map['status'] = Variable<String>(
        $QueuedVisitsTable.$converterstatus.toSql(status),
      );
    }
    if (!nullToAbsent || serverCode != null) {
      map['server_code'] = Variable<String>(serverCode);
    }
    if (!nullToAbsent || serverMessage != null) {
      map['server_message'] = Variable<String>(serverMessage);
    }
    map['attempts'] = Variable<int>(attempts);
    return map;
  }

  QueuedVisitsCompanion toCompanion(bool nullToAbsent) {
    return QueuedVisitsCompanion(
      clientReference: Value(clientReference),
      customerId: Value(customerId),
      customerName: Value(customerName),
      purpose: Value(purpose),
      outcome: Value(outcome),
      notes: notes == null && nullToAbsent
          ? const Value.absent()
          : Value(notes),
      visitedAt: Value(visitedAt),
      status: Value(status),
      serverCode: serverCode == null && nullToAbsent
          ? const Value.absent()
          : Value(serverCode),
      serverMessage: serverMessage == null && nullToAbsent
          ? const Value.absent()
          : Value(serverMessage),
      attempts: Value(attempts),
    );
  }

  factory QueuedVisit.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return QueuedVisit(
      clientReference: serializer.fromJson<String>(json['clientReference']),
      customerId: serializer.fromJson<String>(json['customerId']),
      customerName: serializer.fromJson<String>(json['customerName']),
      purpose: serializer.fromJson<String>(json['purpose']),
      outcome: serializer.fromJson<String>(json['outcome']),
      notes: serializer.fromJson<String?>(json['notes']),
      visitedAt: serializer.fromJson<String>(json['visitedAt']),
      status: $QueuedVisitsTable.$converterstatus.fromJson(
        serializer.fromJson<String>(json['status']),
      ),
      serverCode: serializer.fromJson<String?>(json['serverCode']),
      serverMessage: serializer.fromJson<String?>(json['serverMessage']),
      attempts: serializer.fromJson<int>(json['attempts']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'clientReference': serializer.toJson<String>(clientReference),
      'customerId': serializer.toJson<String>(customerId),
      'customerName': serializer.toJson<String>(customerName),
      'purpose': serializer.toJson<String>(purpose),
      'outcome': serializer.toJson<String>(outcome),
      'notes': serializer.toJson<String?>(notes),
      'visitedAt': serializer.toJson<String>(visitedAt),
      'status': serializer.toJson<String>(
        $QueuedVisitsTable.$converterstatus.toJson(status),
      ),
      'serverCode': serializer.toJson<String?>(serverCode),
      'serverMessage': serializer.toJson<String?>(serverMessage),
      'attempts': serializer.toJson<int>(attempts),
    };
  }

  QueuedVisit copyWith({
    String? clientReference,
    String? customerId,
    String? customerName,
    String? purpose,
    String? outcome,
    Value<String?> notes = const Value.absent(),
    String? visitedAt,
    QueueStatus? status,
    Value<String?> serverCode = const Value.absent(),
    Value<String?> serverMessage = const Value.absent(),
    int? attempts,
  }) => QueuedVisit(
    clientReference: clientReference ?? this.clientReference,
    customerId: customerId ?? this.customerId,
    customerName: customerName ?? this.customerName,
    purpose: purpose ?? this.purpose,
    outcome: outcome ?? this.outcome,
    notes: notes.present ? notes.value : this.notes,
    visitedAt: visitedAt ?? this.visitedAt,
    status: status ?? this.status,
    serverCode: serverCode.present ? serverCode.value : this.serverCode,
    serverMessage: serverMessage.present
        ? serverMessage.value
        : this.serverMessage,
    attempts: attempts ?? this.attempts,
  );
  QueuedVisit copyWithCompanion(QueuedVisitsCompanion data) {
    return QueuedVisit(
      clientReference: data.clientReference.present
          ? data.clientReference.value
          : this.clientReference,
      customerId: data.customerId.present
          ? data.customerId.value
          : this.customerId,
      customerName: data.customerName.present
          ? data.customerName.value
          : this.customerName,
      purpose: data.purpose.present ? data.purpose.value : this.purpose,
      outcome: data.outcome.present ? data.outcome.value : this.outcome,
      notes: data.notes.present ? data.notes.value : this.notes,
      visitedAt: data.visitedAt.present ? data.visitedAt.value : this.visitedAt,
      status: data.status.present ? data.status.value : this.status,
      serverCode: data.serverCode.present
          ? data.serverCode.value
          : this.serverCode,
      serverMessage: data.serverMessage.present
          ? data.serverMessage.value
          : this.serverMessage,
      attempts: data.attempts.present ? data.attempts.value : this.attempts,
    );
  }

  @override
  String toString() {
    return (StringBuffer('QueuedVisit(')
          ..write('clientReference: $clientReference, ')
          ..write('customerId: $customerId, ')
          ..write('customerName: $customerName, ')
          ..write('purpose: $purpose, ')
          ..write('outcome: $outcome, ')
          ..write('notes: $notes, ')
          ..write('visitedAt: $visitedAt, ')
          ..write('status: $status, ')
          ..write('serverCode: $serverCode, ')
          ..write('serverMessage: $serverMessage, ')
          ..write('attempts: $attempts')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    clientReference,
    customerId,
    customerName,
    purpose,
    outcome,
    notes,
    visitedAt,
    status,
    serverCode,
    serverMessage,
    attempts,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is QueuedVisit &&
          other.clientReference == this.clientReference &&
          other.customerId == this.customerId &&
          other.customerName == this.customerName &&
          other.purpose == this.purpose &&
          other.outcome == this.outcome &&
          other.notes == this.notes &&
          other.visitedAt == this.visitedAt &&
          other.status == this.status &&
          other.serverCode == this.serverCode &&
          other.serverMessage == this.serverMessage &&
          other.attempts == this.attempts);
}

class QueuedVisitsCompanion extends UpdateCompanion<QueuedVisit> {
  final Value<String> clientReference;
  final Value<String> customerId;
  final Value<String> customerName;
  final Value<String> purpose;
  final Value<String> outcome;
  final Value<String?> notes;
  final Value<String> visitedAt;
  final Value<QueueStatus> status;
  final Value<String?> serverCode;
  final Value<String?> serverMessage;
  final Value<int> attempts;
  final Value<int> rowid;
  const QueuedVisitsCompanion({
    this.clientReference = const Value.absent(),
    this.customerId = const Value.absent(),
    this.customerName = const Value.absent(),
    this.purpose = const Value.absent(),
    this.outcome = const Value.absent(),
    this.notes = const Value.absent(),
    this.visitedAt = const Value.absent(),
    this.status = const Value.absent(),
    this.serverCode = const Value.absent(),
    this.serverMessage = const Value.absent(),
    this.attempts = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  QueuedVisitsCompanion.insert({
    required String clientReference,
    required String customerId,
    required String customerName,
    required String purpose,
    required String outcome,
    this.notes = const Value.absent(),
    required String visitedAt,
    required QueueStatus status,
    this.serverCode = const Value.absent(),
    this.serverMessage = const Value.absent(),
    this.attempts = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : clientReference = Value(clientReference),
       customerId = Value(customerId),
       customerName = Value(customerName),
       purpose = Value(purpose),
       outcome = Value(outcome),
       visitedAt = Value(visitedAt),
       status = Value(status);
  static Insertable<QueuedVisit> custom({
    Expression<String>? clientReference,
    Expression<String>? customerId,
    Expression<String>? customerName,
    Expression<String>? purpose,
    Expression<String>? outcome,
    Expression<String>? notes,
    Expression<String>? visitedAt,
    Expression<String>? status,
    Expression<String>? serverCode,
    Expression<String>? serverMessage,
    Expression<int>? attempts,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (clientReference != null) 'client_reference': clientReference,
      if (customerId != null) 'customer_id': customerId,
      if (customerName != null) 'customer_name': customerName,
      if (purpose != null) 'purpose': purpose,
      if (outcome != null) 'outcome': outcome,
      if (notes != null) 'notes': notes,
      if (visitedAt != null) 'visited_at': visitedAt,
      if (status != null) 'status': status,
      if (serverCode != null) 'server_code': serverCode,
      if (serverMessage != null) 'server_message': serverMessage,
      if (attempts != null) 'attempts': attempts,
      if (rowid != null) 'rowid': rowid,
    });
  }

  QueuedVisitsCompanion copyWith({
    Value<String>? clientReference,
    Value<String>? customerId,
    Value<String>? customerName,
    Value<String>? purpose,
    Value<String>? outcome,
    Value<String?>? notes,
    Value<String>? visitedAt,
    Value<QueueStatus>? status,
    Value<String?>? serverCode,
    Value<String?>? serverMessage,
    Value<int>? attempts,
    Value<int>? rowid,
  }) {
    return QueuedVisitsCompanion(
      clientReference: clientReference ?? this.clientReference,
      customerId: customerId ?? this.customerId,
      customerName: customerName ?? this.customerName,
      purpose: purpose ?? this.purpose,
      outcome: outcome ?? this.outcome,
      notes: notes ?? this.notes,
      visitedAt: visitedAt ?? this.visitedAt,
      status: status ?? this.status,
      serverCode: serverCode ?? this.serverCode,
      serverMessage: serverMessage ?? this.serverMessage,
      attempts: attempts ?? this.attempts,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (clientReference.present) {
      map['client_reference'] = Variable<String>(clientReference.value);
    }
    if (customerId.present) {
      map['customer_id'] = Variable<String>(customerId.value);
    }
    if (customerName.present) {
      map['customer_name'] = Variable<String>(customerName.value);
    }
    if (purpose.present) {
      map['purpose'] = Variable<String>(purpose.value);
    }
    if (outcome.present) {
      map['outcome'] = Variable<String>(outcome.value);
    }
    if (notes.present) {
      map['notes'] = Variable<String>(notes.value);
    }
    if (visitedAt.present) {
      map['visited_at'] = Variable<String>(visitedAt.value);
    }
    if (status.present) {
      map['status'] = Variable<String>(
        $QueuedVisitsTable.$converterstatus.toSql(status.value),
      );
    }
    if (serverCode.present) {
      map['server_code'] = Variable<String>(serverCode.value);
    }
    if (serverMessage.present) {
      map['server_message'] = Variable<String>(serverMessage.value);
    }
    if (attempts.present) {
      map['attempts'] = Variable<int>(attempts.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('QueuedVisitsCompanion(')
          ..write('clientReference: $clientReference, ')
          ..write('customerId: $customerId, ')
          ..write('customerName: $customerName, ')
          ..write('purpose: $purpose, ')
          ..write('outcome: $outcome, ')
          ..write('notes: $notes, ')
          ..write('visitedAt: $visitedAt, ')
          ..write('status: $status, ')
          ..write('serverCode: $serverCode, ')
          ..write('serverMessage: $serverMessage, ')
          ..write('attempts: $attempts, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $CachedCustomersTable extends CachedCustomers
    with TableInfo<$CachedCustomersTable, CachedCustomer> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $CachedCustomersTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _customerIdMeta = const VerificationMeta(
    'customerId',
  );
  @override
  late final GeneratedColumn<String> customerId = GeneratedColumn<String>(
    'customer_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _customerNumberMeta = const VerificationMeta(
    'customerNumber',
  );
  @override
  late final GeneratedColumn<String> customerNumber = GeneratedColumn<String>(
    'customer_number',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _displayNameMeta = const VerificationMeta(
    'displayName',
  );
  @override
  late final GeneratedColumn<String> displayName = GeneratedColumn<String>(
    'display_name',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _phoneMeta = const VerificationMeta('phone');
  @override
  late final GeneratedColumn<String> phone = GeneratedColumn<String>(
    'phone',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _payloadMeta = const VerificationMeta(
    'payload',
  );
  @override
  late final GeneratedColumn<String> payload = GeneratedColumn<String>(
    'payload',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _positionMeta = const VerificationMeta(
    'position',
  );
  @override
  late final GeneratedColumn<int> position = GeneratedColumn<int>(
    'position',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [
    customerId,
    customerNumber,
    displayName,
    phone,
    payload,
    position,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'cached_customers';
  @override
  VerificationContext validateIntegrity(
    Insertable<CachedCustomer> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('customer_id')) {
      context.handle(
        _customerIdMeta,
        customerId.isAcceptableOrUnknown(data['customer_id']!, _customerIdMeta),
      );
    } else if (isInserting) {
      context.missing(_customerIdMeta);
    }
    if (data.containsKey('customer_number')) {
      context.handle(
        _customerNumberMeta,
        customerNumber.isAcceptableOrUnknown(
          data['customer_number']!,
          _customerNumberMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_customerNumberMeta);
    }
    if (data.containsKey('display_name')) {
      context.handle(
        _displayNameMeta,
        displayName.isAcceptableOrUnknown(
          data['display_name']!,
          _displayNameMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_displayNameMeta);
    }
    if (data.containsKey('phone')) {
      context.handle(
        _phoneMeta,
        phone.isAcceptableOrUnknown(data['phone']!, _phoneMeta),
      );
    }
    if (data.containsKey('payload')) {
      context.handle(
        _payloadMeta,
        payload.isAcceptableOrUnknown(data['payload']!, _payloadMeta),
      );
    } else if (isInserting) {
      context.missing(_payloadMeta);
    }
    if (data.containsKey('position')) {
      context.handle(
        _positionMeta,
        position.isAcceptableOrUnknown(data['position']!, _positionMeta),
      );
    } else if (isInserting) {
      context.missing(_positionMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {customerId};
  @override
  CachedCustomer map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return CachedCustomer(
      customerId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}customer_id'],
      )!,
      customerNumber: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}customer_number'],
      )!,
      displayName: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}display_name'],
      )!,
      phone: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}phone'],
      ),
      payload: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}payload'],
      )!,
      position: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}position'],
      )!,
    );
  }

  @override
  $CachedCustomersTable createAlias(String alias) {
    return $CachedCustomersTable(attachedDatabase, alias);
  }
}

class CachedCustomer extends DataClass implements Insertable<CachedCustomer> {
  final String customerId;
  final String customerNumber;
  final String displayName;
  final String? phone;
  final String payload;
  final int position;
  const CachedCustomer({
    required this.customerId,
    required this.customerNumber,
    required this.displayName,
    this.phone,
    required this.payload,
    required this.position,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['customer_id'] = Variable<String>(customerId);
    map['customer_number'] = Variable<String>(customerNumber);
    map['display_name'] = Variable<String>(displayName);
    if (!nullToAbsent || phone != null) {
      map['phone'] = Variable<String>(phone);
    }
    map['payload'] = Variable<String>(payload);
    map['position'] = Variable<int>(position);
    return map;
  }

  CachedCustomersCompanion toCompanion(bool nullToAbsent) {
    return CachedCustomersCompanion(
      customerId: Value(customerId),
      customerNumber: Value(customerNumber),
      displayName: Value(displayName),
      phone: phone == null && nullToAbsent
          ? const Value.absent()
          : Value(phone),
      payload: Value(payload),
      position: Value(position),
    );
  }

  factory CachedCustomer.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return CachedCustomer(
      customerId: serializer.fromJson<String>(json['customerId']),
      customerNumber: serializer.fromJson<String>(json['customerNumber']),
      displayName: serializer.fromJson<String>(json['displayName']),
      phone: serializer.fromJson<String?>(json['phone']),
      payload: serializer.fromJson<String>(json['payload']),
      position: serializer.fromJson<int>(json['position']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'customerId': serializer.toJson<String>(customerId),
      'customerNumber': serializer.toJson<String>(customerNumber),
      'displayName': serializer.toJson<String>(displayName),
      'phone': serializer.toJson<String?>(phone),
      'payload': serializer.toJson<String>(payload),
      'position': serializer.toJson<int>(position),
    };
  }

  CachedCustomer copyWith({
    String? customerId,
    String? customerNumber,
    String? displayName,
    Value<String?> phone = const Value.absent(),
    String? payload,
    int? position,
  }) => CachedCustomer(
    customerId: customerId ?? this.customerId,
    customerNumber: customerNumber ?? this.customerNumber,
    displayName: displayName ?? this.displayName,
    phone: phone.present ? phone.value : this.phone,
    payload: payload ?? this.payload,
    position: position ?? this.position,
  );
  CachedCustomer copyWithCompanion(CachedCustomersCompanion data) {
    return CachedCustomer(
      customerId: data.customerId.present
          ? data.customerId.value
          : this.customerId,
      customerNumber: data.customerNumber.present
          ? data.customerNumber.value
          : this.customerNumber,
      displayName: data.displayName.present
          ? data.displayName.value
          : this.displayName,
      phone: data.phone.present ? data.phone.value : this.phone,
      payload: data.payload.present ? data.payload.value : this.payload,
      position: data.position.present ? data.position.value : this.position,
    );
  }

  @override
  String toString() {
    return (StringBuffer('CachedCustomer(')
          ..write('customerId: $customerId, ')
          ..write('customerNumber: $customerNumber, ')
          ..write('displayName: $displayName, ')
          ..write('phone: $phone, ')
          ..write('payload: $payload, ')
          ..write('position: $position')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    customerId,
    customerNumber,
    displayName,
    phone,
    payload,
    position,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is CachedCustomer &&
          other.customerId == this.customerId &&
          other.customerNumber == this.customerNumber &&
          other.displayName == this.displayName &&
          other.phone == this.phone &&
          other.payload == this.payload &&
          other.position == this.position);
}

class CachedCustomersCompanion extends UpdateCompanion<CachedCustomer> {
  final Value<String> customerId;
  final Value<String> customerNumber;
  final Value<String> displayName;
  final Value<String?> phone;
  final Value<String> payload;
  final Value<int> position;
  final Value<int> rowid;
  const CachedCustomersCompanion({
    this.customerId = const Value.absent(),
    this.customerNumber = const Value.absent(),
    this.displayName = const Value.absent(),
    this.phone = const Value.absent(),
    this.payload = const Value.absent(),
    this.position = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  CachedCustomersCompanion.insert({
    required String customerId,
    required String customerNumber,
    required String displayName,
    this.phone = const Value.absent(),
    required String payload,
    required int position,
    this.rowid = const Value.absent(),
  }) : customerId = Value(customerId),
       customerNumber = Value(customerNumber),
       displayName = Value(displayName),
       payload = Value(payload),
       position = Value(position);
  static Insertable<CachedCustomer> custom({
    Expression<String>? customerId,
    Expression<String>? customerNumber,
    Expression<String>? displayName,
    Expression<String>? phone,
    Expression<String>? payload,
    Expression<int>? position,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (customerId != null) 'customer_id': customerId,
      if (customerNumber != null) 'customer_number': customerNumber,
      if (displayName != null) 'display_name': displayName,
      if (phone != null) 'phone': phone,
      if (payload != null) 'payload': payload,
      if (position != null) 'position': position,
      if (rowid != null) 'rowid': rowid,
    });
  }

  CachedCustomersCompanion copyWith({
    Value<String>? customerId,
    Value<String>? customerNumber,
    Value<String>? displayName,
    Value<String?>? phone,
    Value<String>? payload,
    Value<int>? position,
    Value<int>? rowid,
  }) {
    return CachedCustomersCompanion(
      customerId: customerId ?? this.customerId,
      customerNumber: customerNumber ?? this.customerNumber,
      displayName: displayName ?? this.displayName,
      phone: phone ?? this.phone,
      payload: payload ?? this.payload,
      position: position ?? this.position,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (customerId.present) {
      map['customer_id'] = Variable<String>(customerId.value);
    }
    if (customerNumber.present) {
      map['customer_number'] = Variable<String>(customerNumber.value);
    }
    if (displayName.present) {
      map['display_name'] = Variable<String>(displayName.value);
    }
    if (phone.present) {
      map['phone'] = Variable<String>(phone.value);
    }
    if (payload.present) {
      map['payload'] = Variable<String>(payload.value);
    }
    if (position.present) {
      map['position'] = Variable<int>(position.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('CachedCustomersCompanion(')
          ..write('customerId: $customerId, ')
          ..write('customerNumber: $customerNumber, ')
          ..write('displayName: $displayName, ')
          ..write('phone: $phone, ')
          ..write('payload: $payload, ')
          ..write('position: $position, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $DeviceStatesTable extends DeviceStates
    with TableInfo<$DeviceStatesTable, DeviceState> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $DeviceStatesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _registrationIdMeta = const VerificationMeta(
    'registrationId',
  );
  @override
  late final GeneratedColumn<String> registrationId = GeneratedColumn<String>(
    'registration_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _nextSequenceNoMeta = const VerificationMeta(
    'nextSequenceNo',
  );
  @override
  late final GeneratedColumn<int> nextSequenceNo = GeneratedColumn<int>(
    'next_sequence_no',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(1),
  );
  static const VerificationMeta _lastSyncedAtMeta = const VerificationMeta(
    'lastSyncedAt',
  );
  @override
  late final GeneratedColumn<String> lastSyncedAt = GeneratedColumn<String>(
    'last_synced_at',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _officerMeta = const VerificationMeta(
    'officer',
  );
  @override
  late final GeneratedColumn<String> officer = GeneratedColumn<String>(
    'officer',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    registrationId,
    nextSequenceNo,
    lastSyncedAt,
    officer,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'device_states';
  @override
  VerificationContext validateIntegrity(
    Insertable<DeviceState> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('registration_id')) {
      context.handle(
        _registrationIdMeta,
        registrationId.isAcceptableOrUnknown(
          data['registration_id']!,
          _registrationIdMeta,
        ),
      );
    }
    if (data.containsKey('next_sequence_no')) {
      context.handle(
        _nextSequenceNoMeta,
        nextSequenceNo.isAcceptableOrUnknown(
          data['next_sequence_no']!,
          _nextSequenceNoMeta,
        ),
      );
    }
    if (data.containsKey('last_synced_at')) {
      context.handle(
        _lastSyncedAtMeta,
        lastSyncedAt.isAcceptableOrUnknown(
          data['last_synced_at']!,
          _lastSyncedAtMeta,
        ),
      );
    }
    if (data.containsKey('officer')) {
      context.handle(
        _officerMeta,
        officer.isAcceptableOrUnknown(data['officer']!, _officerMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  DeviceState map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return DeviceState(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      registrationId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}registration_id'],
      ),
      nextSequenceNo: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}next_sequence_no'],
      )!,
      lastSyncedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}last_synced_at'],
      ),
      officer: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}officer'],
      ),
    );
  }

  @override
  $DeviceStatesTable createAlias(String alias) {
    return $DeviceStatesTable(attachedDatabase, alias);
  }
}

class DeviceState extends DataClass implements Insertable<DeviceState> {
  final int id;
  final String? registrationId;
  final int nextSequenceNo;
  final String? lastSyncedAt;
  final String? officer;
  const DeviceState({
    required this.id,
    this.registrationId,
    required this.nextSequenceNo,
    this.lastSyncedAt,
    this.officer,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    if (!nullToAbsent || registrationId != null) {
      map['registration_id'] = Variable<String>(registrationId);
    }
    map['next_sequence_no'] = Variable<int>(nextSequenceNo);
    if (!nullToAbsent || lastSyncedAt != null) {
      map['last_synced_at'] = Variable<String>(lastSyncedAt);
    }
    if (!nullToAbsent || officer != null) {
      map['officer'] = Variable<String>(officer);
    }
    return map;
  }

  DeviceStatesCompanion toCompanion(bool nullToAbsent) {
    return DeviceStatesCompanion(
      id: Value(id),
      registrationId: registrationId == null && nullToAbsent
          ? const Value.absent()
          : Value(registrationId),
      nextSequenceNo: Value(nextSequenceNo),
      lastSyncedAt: lastSyncedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(lastSyncedAt),
      officer: officer == null && nullToAbsent
          ? const Value.absent()
          : Value(officer),
    );
  }

  factory DeviceState.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return DeviceState(
      id: serializer.fromJson<int>(json['id']),
      registrationId: serializer.fromJson<String?>(json['registrationId']),
      nextSequenceNo: serializer.fromJson<int>(json['nextSequenceNo']),
      lastSyncedAt: serializer.fromJson<String?>(json['lastSyncedAt']),
      officer: serializer.fromJson<String?>(json['officer']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'registrationId': serializer.toJson<String?>(registrationId),
      'nextSequenceNo': serializer.toJson<int>(nextSequenceNo),
      'lastSyncedAt': serializer.toJson<String?>(lastSyncedAt),
      'officer': serializer.toJson<String?>(officer),
    };
  }

  DeviceState copyWith({
    int? id,
    Value<String?> registrationId = const Value.absent(),
    int? nextSequenceNo,
    Value<String?> lastSyncedAt = const Value.absent(),
    Value<String?> officer = const Value.absent(),
  }) => DeviceState(
    id: id ?? this.id,
    registrationId: registrationId.present
        ? registrationId.value
        : this.registrationId,
    nextSequenceNo: nextSequenceNo ?? this.nextSequenceNo,
    lastSyncedAt: lastSyncedAt.present ? lastSyncedAt.value : this.lastSyncedAt,
    officer: officer.present ? officer.value : this.officer,
  );
  DeviceState copyWithCompanion(DeviceStatesCompanion data) {
    return DeviceState(
      id: data.id.present ? data.id.value : this.id,
      registrationId: data.registrationId.present
          ? data.registrationId.value
          : this.registrationId,
      nextSequenceNo: data.nextSequenceNo.present
          ? data.nextSequenceNo.value
          : this.nextSequenceNo,
      lastSyncedAt: data.lastSyncedAt.present
          ? data.lastSyncedAt.value
          : this.lastSyncedAt,
      officer: data.officer.present ? data.officer.value : this.officer,
    );
  }

  @override
  String toString() {
    return (StringBuffer('DeviceState(')
          ..write('id: $id, ')
          ..write('registrationId: $registrationId, ')
          ..write('nextSequenceNo: $nextSequenceNo, ')
          ..write('lastSyncedAt: $lastSyncedAt, ')
          ..write('officer: $officer')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode =>
      Object.hash(id, registrationId, nextSequenceNo, lastSyncedAt, officer);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is DeviceState &&
          other.id == this.id &&
          other.registrationId == this.registrationId &&
          other.nextSequenceNo == this.nextSequenceNo &&
          other.lastSyncedAt == this.lastSyncedAt &&
          other.officer == this.officer);
}

class DeviceStatesCompanion extends UpdateCompanion<DeviceState> {
  final Value<int> id;
  final Value<String?> registrationId;
  final Value<int> nextSequenceNo;
  final Value<String?> lastSyncedAt;
  final Value<String?> officer;
  const DeviceStatesCompanion({
    this.id = const Value.absent(),
    this.registrationId = const Value.absent(),
    this.nextSequenceNo = const Value.absent(),
    this.lastSyncedAt = const Value.absent(),
    this.officer = const Value.absent(),
  });
  DeviceStatesCompanion.insert({
    this.id = const Value.absent(),
    this.registrationId = const Value.absent(),
    this.nextSequenceNo = const Value.absent(),
    this.lastSyncedAt = const Value.absent(),
    this.officer = const Value.absent(),
  });
  static Insertable<DeviceState> custom({
    Expression<int>? id,
    Expression<String>? registrationId,
    Expression<int>? nextSequenceNo,
    Expression<String>? lastSyncedAt,
    Expression<String>? officer,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (registrationId != null) 'registration_id': registrationId,
      if (nextSequenceNo != null) 'next_sequence_no': nextSequenceNo,
      if (lastSyncedAt != null) 'last_synced_at': lastSyncedAt,
      if (officer != null) 'officer': officer,
    });
  }

  DeviceStatesCompanion copyWith({
    Value<int>? id,
    Value<String?>? registrationId,
    Value<int>? nextSequenceNo,
    Value<String?>? lastSyncedAt,
    Value<String?>? officer,
  }) {
    return DeviceStatesCompanion(
      id: id ?? this.id,
      registrationId: registrationId ?? this.registrationId,
      nextSequenceNo: nextSequenceNo ?? this.nextSequenceNo,
      lastSyncedAt: lastSyncedAt ?? this.lastSyncedAt,
      officer: officer ?? this.officer,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (registrationId.present) {
      map['registration_id'] = Variable<String>(registrationId.value);
    }
    if (nextSequenceNo.present) {
      map['next_sequence_no'] = Variable<int>(nextSequenceNo.value);
    }
    if (lastSyncedAt.present) {
      map['last_synced_at'] = Variable<String>(lastSyncedAt.value);
    }
    if (officer.present) {
      map['officer'] = Variable<String>(officer.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('DeviceStatesCompanion(')
          ..write('id: $id, ')
          ..write('registrationId: $registrationId, ')
          ..write('nextSequenceNo: $nextSequenceNo, ')
          ..write('lastSyncedAt: $lastSyncedAt, ')
          ..write('officer: $officer')
          ..write(')'))
        .toString();
  }
}

abstract class _$FieldDatabase extends GeneratedDatabase {
  _$FieldDatabase(QueryExecutor e) : super(e);
  $FieldDatabaseManager get managers => $FieldDatabaseManager(this);
  late final $QueuedCollectionsTable queuedCollections =
      $QueuedCollectionsTable(this);
  late final $QueuedVisitsTable queuedVisits = $QueuedVisitsTable(this);
  late final $CachedCustomersTable cachedCustomers = $CachedCustomersTable(
    this,
  );
  late final $DeviceStatesTable deviceStates = $DeviceStatesTable(this);
  @override
  Iterable<TableInfo<Table, Object?>> get allTables =>
      allSchemaEntities.whereType<TableInfo<Table, Object?>>();
  @override
  List<DatabaseSchemaEntity> get allSchemaEntities => [
    queuedCollections,
    queuedVisits,
    cachedCustomers,
    deviceStates,
  ];
}

typedef $$QueuedCollectionsTableCreateCompanionBuilder =
    QueuedCollectionsCompanion Function({
      required String clientReference,
      required int sequenceNo,
      required String customerId,
      required String customerName,
      required String accountId,
      required String accountNumber,
      Value<String?> susuPlanId,
      required String amount,
      required String currency,
      required String collectedAt,
      Value<String?> note,
      required QueueStatus status,
      Value<String?> serverCode,
      Value<String?> serverMessage,
      Value<String?> transactionReference,
      Value<String?> balanceAfter,
      Value<int> attempts,
      Value<String?> lastAttemptAt,
      Value<int> rowid,
    });
typedef $$QueuedCollectionsTableUpdateCompanionBuilder =
    QueuedCollectionsCompanion Function({
      Value<String> clientReference,
      Value<int> sequenceNo,
      Value<String> customerId,
      Value<String> customerName,
      Value<String> accountId,
      Value<String> accountNumber,
      Value<String?> susuPlanId,
      Value<String> amount,
      Value<String> currency,
      Value<String> collectedAt,
      Value<String?> note,
      Value<QueueStatus> status,
      Value<String?> serverCode,
      Value<String?> serverMessage,
      Value<String?> transactionReference,
      Value<String?> balanceAfter,
      Value<int> attempts,
      Value<String?> lastAttemptAt,
      Value<int> rowid,
    });

class $$QueuedCollectionsTableFilterComposer
    extends Composer<_$FieldDatabase, $QueuedCollectionsTable> {
  $$QueuedCollectionsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get clientReference => $composableBuilder(
    column: $table.clientReference,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get sequenceNo => $composableBuilder(
    column: $table.sequenceNo,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get customerId => $composableBuilder(
    column: $table.customerId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get customerName => $composableBuilder(
    column: $table.customerName,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get accountId => $composableBuilder(
    column: $table.accountId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get accountNumber => $composableBuilder(
    column: $table.accountNumber,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get susuPlanId => $composableBuilder(
    column: $table.susuPlanId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get amount => $composableBuilder(
    column: $table.amount,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get currency => $composableBuilder(
    column: $table.currency,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get collectedAt => $composableBuilder(
    column: $table.collectedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get note => $composableBuilder(
    column: $table.note,
    builder: (column) => ColumnFilters(column),
  );

  ColumnWithTypeConverterFilters<QueueStatus, QueueStatus, String> get status =>
      $composableBuilder(
        column: $table.status,
        builder: (column) => ColumnWithTypeConverterFilters(column),
      );

  ColumnFilters<String> get serverCode => $composableBuilder(
    column: $table.serverCode,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get serverMessage => $composableBuilder(
    column: $table.serverMessage,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get transactionReference => $composableBuilder(
    column: $table.transactionReference,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get balanceAfter => $composableBuilder(
    column: $table.balanceAfter,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get attempts => $composableBuilder(
    column: $table.attempts,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get lastAttemptAt => $composableBuilder(
    column: $table.lastAttemptAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $$QueuedCollectionsTableOrderingComposer
    extends Composer<_$FieldDatabase, $QueuedCollectionsTable> {
  $$QueuedCollectionsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get clientReference => $composableBuilder(
    column: $table.clientReference,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get sequenceNo => $composableBuilder(
    column: $table.sequenceNo,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get customerId => $composableBuilder(
    column: $table.customerId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get customerName => $composableBuilder(
    column: $table.customerName,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get accountId => $composableBuilder(
    column: $table.accountId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get accountNumber => $composableBuilder(
    column: $table.accountNumber,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get susuPlanId => $composableBuilder(
    column: $table.susuPlanId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get amount => $composableBuilder(
    column: $table.amount,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get currency => $composableBuilder(
    column: $table.currency,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get collectedAt => $composableBuilder(
    column: $table.collectedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get note => $composableBuilder(
    column: $table.note,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get serverCode => $composableBuilder(
    column: $table.serverCode,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get serverMessage => $composableBuilder(
    column: $table.serverMessage,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get transactionReference => $composableBuilder(
    column: $table.transactionReference,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get balanceAfter => $composableBuilder(
    column: $table.balanceAfter,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get attempts => $composableBuilder(
    column: $table.attempts,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get lastAttemptAt => $composableBuilder(
    column: $table.lastAttemptAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$QueuedCollectionsTableAnnotationComposer
    extends Composer<_$FieldDatabase, $QueuedCollectionsTable> {
  $$QueuedCollectionsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get clientReference => $composableBuilder(
    column: $table.clientReference,
    builder: (column) => column,
  );

  GeneratedColumn<int> get sequenceNo => $composableBuilder(
    column: $table.sequenceNo,
    builder: (column) => column,
  );

  GeneratedColumn<String> get customerId => $composableBuilder(
    column: $table.customerId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get customerName => $composableBuilder(
    column: $table.customerName,
    builder: (column) => column,
  );

  GeneratedColumn<String> get accountId =>
      $composableBuilder(column: $table.accountId, builder: (column) => column);

  GeneratedColumn<String> get accountNumber => $composableBuilder(
    column: $table.accountNumber,
    builder: (column) => column,
  );

  GeneratedColumn<String> get susuPlanId => $composableBuilder(
    column: $table.susuPlanId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get amount =>
      $composableBuilder(column: $table.amount, builder: (column) => column);

  GeneratedColumn<String> get currency =>
      $composableBuilder(column: $table.currency, builder: (column) => column);

  GeneratedColumn<String> get collectedAt => $composableBuilder(
    column: $table.collectedAt,
    builder: (column) => column,
  );

  GeneratedColumn<String> get note =>
      $composableBuilder(column: $table.note, builder: (column) => column);

  GeneratedColumnWithTypeConverter<QueueStatus, String> get status =>
      $composableBuilder(column: $table.status, builder: (column) => column);

  GeneratedColumn<String> get serverCode => $composableBuilder(
    column: $table.serverCode,
    builder: (column) => column,
  );

  GeneratedColumn<String> get serverMessage => $composableBuilder(
    column: $table.serverMessage,
    builder: (column) => column,
  );

  GeneratedColumn<String> get transactionReference => $composableBuilder(
    column: $table.transactionReference,
    builder: (column) => column,
  );

  GeneratedColumn<String> get balanceAfter => $composableBuilder(
    column: $table.balanceAfter,
    builder: (column) => column,
  );

  GeneratedColumn<int> get attempts =>
      $composableBuilder(column: $table.attempts, builder: (column) => column);

  GeneratedColumn<String> get lastAttemptAt => $composableBuilder(
    column: $table.lastAttemptAt,
    builder: (column) => column,
  );
}

class $$QueuedCollectionsTableTableManager
    extends
        RootTableManager<
          _$FieldDatabase,
          $QueuedCollectionsTable,
          QueuedCollection,
          $$QueuedCollectionsTableFilterComposer,
          $$QueuedCollectionsTableOrderingComposer,
          $$QueuedCollectionsTableAnnotationComposer,
          $$QueuedCollectionsTableCreateCompanionBuilder,
          $$QueuedCollectionsTableUpdateCompanionBuilder,
          (
            QueuedCollection,
            BaseReferences<
              _$FieldDatabase,
              $QueuedCollectionsTable,
              QueuedCollection
            >,
          ),
          QueuedCollection,
          PrefetchHooks Function()
        > {
  $$QueuedCollectionsTableTableManager(
    _$FieldDatabase db,
    $QueuedCollectionsTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$QueuedCollectionsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$QueuedCollectionsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$QueuedCollectionsTableAnnotationComposer(
                $db: db,
                $table: table,
              ),
          updateCompanionCallback:
              ({
                Value<String> clientReference = const Value.absent(),
                Value<int> sequenceNo = const Value.absent(),
                Value<String> customerId = const Value.absent(),
                Value<String> customerName = const Value.absent(),
                Value<String> accountId = const Value.absent(),
                Value<String> accountNumber = const Value.absent(),
                Value<String?> susuPlanId = const Value.absent(),
                Value<String> amount = const Value.absent(),
                Value<String> currency = const Value.absent(),
                Value<String> collectedAt = const Value.absent(),
                Value<String?> note = const Value.absent(),
                Value<QueueStatus> status = const Value.absent(),
                Value<String?> serverCode = const Value.absent(),
                Value<String?> serverMessage = const Value.absent(),
                Value<String?> transactionReference = const Value.absent(),
                Value<String?> balanceAfter = const Value.absent(),
                Value<int> attempts = const Value.absent(),
                Value<String?> lastAttemptAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => QueuedCollectionsCompanion(
                clientReference: clientReference,
                sequenceNo: sequenceNo,
                customerId: customerId,
                customerName: customerName,
                accountId: accountId,
                accountNumber: accountNumber,
                susuPlanId: susuPlanId,
                amount: amount,
                currency: currency,
                collectedAt: collectedAt,
                note: note,
                status: status,
                serverCode: serverCode,
                serverMessage: serverMessage,
                transactionReference: transactionReference,
                balanceAfter: balanceAfter,
                attempts: attempts,
                lastAttemptAt: lastAttemptAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String clientReference,
                required int sequenceNo,
                required String customerId,
                required String customerName,
                required String accountId,
                required String accountNumber,
                Value<String?> susuPlanId = const Value.absent(),
                required String amount,
                required String currency,
                required String collectedAt,
                Value<String?> note = const Value.absent(),
                required QueueStatus status,
                Value<String?> serverCode = const Value.absent(),
                Value<String?> serverMessage = const Value.absent(),
                Value<String?> transactionReference = const Value.absent(),
                Value<String?> balanceAfter = const Value.absent(),
                Value<int> attempts = const Value.absent(),
                Value<String?> lastAttemptAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => QueuedCollectionsCompanion.insert(
                clientReference: clientReference,
                sequenceNo: sequenceNo,
                customerId: customerId,
                customerName: customerName,
                accountId: accountId,
                accountNumber: accountNumber,
                susuPlanId: susuPlanId,
                amount: amount,
                currency: currency,
                collectedAt: collectedAt,
                note: note,
                status: status,
                serverCode: serverCode,
                serverMessage: serverMessage,
                transactionReference: transactionReference,
                balanceAfter: balanceAfter,
                attempts: attempts,
                lastAttemptAt: lastAttemptAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$QueuedCollectionsTable, QueuedCollection>(table),
                  BaseReferences<
                    _$FieldDatabase,
                    $QueuedCollectionsTable,
                    QueuedCollection
                  >(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$QueuedCollectionsTableProcessedTableManager =
    ProcessedTableManager<
      _$FieldDatabase,
      $QueuedCollectionsTable,
      QueuedCollection,
      $$QueuedCollectionsTableFilterComposer,
      $$QueuedCollectionsTableOrderingComposer,
      $$QueuedCollectionsTableAnnotationComposer,
      $$QueuedCollectionsTableCreateCompanionBuilder,
      $$QueuedCollectionsTableUpdateCompanionBuilder,
      (
        QueuedCollection,
        BaseReferences<
          _$FieldDatabase,
          $QueuedCollectionsTable,
          QueuedCollection
        >,
      ),
      QueuedCollection,
      PrefetchHooks Function()
    >;
typedef $$QueuedVisitsTableCreateCompanionBuilder =
    QueuedVisitsCompanion Function({
      required String clientReference,
      required String customerId,
      required String customerName,
      required String purpose,
      required String outcome,
      Value<String?> notes,
      required String visitedAt,
      required QueueStatus status,
      Value<String?> serverCode,
      Value<String?> serverMessage,
      Value<int> attempts,
      Value<int> rowid,
    });
typedef $$QueuedVisitsTableUpdateCompanionBuilder =
    QueuedVisitsCompanion Function({
      Value<String> clientReference,
      Value<String> customerId,
      Value<String> customerName,
      Value<String> purpose,
      Value<String> outcome,
      Value<String?> notes,
      Value<String> visitedAt,
      Value<QueueStatus> status,
      Value<String?> serverCode,
      Value<String?> serverMessage,
      Value<int> attempts,
      Value<int> rowid,
    });

class $$QueuedVisitsTableFilterComposer
    extends Composer<_$FieldDatabase, $QueuedVisitsTable> {
  $$QueuedVisitsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get clientReference => $composableBuilder(
    column: $table.clientReference,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get customerId => $composableBuilder(
    column: $table.customerId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get customerName => $composableBuilder(
    column: $table.customerName,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get purpose => $composableBuilder(
    column: $table.purpose,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get outcome => $composableBuilder(
    column: $table.outcome,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get notes => $composableBuilder(
    column: $table.notes,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get visitedAt => $composableBuilder(
    column: $table.visitedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnWithTypeConverterFilters<QueueStatus, QueueStatus, String> get status =>
      $composableBuilder(
        column: $table.status,
        builder: (column) => ColumnWithTypeConverterFilters(column),
      );

  ColumnFilters<String> get serverCode => $composableBuilder(
    column: $table.serverCode,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get serverMessage => $composableBuilder(
    column: $table.serverMessage,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get attempts => $composableBuilder(
    column: $table.attempts,
    builder: (column) => ColumnFilters(column),
  );
}

class $$QueuedVisitsTableOrderingComposer
    extends Composer<_$FieldDatabase, $QueuedVisitsTable> {
  $$QueuedVisitsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get clientReference => $composableBuilder(
    column: $table.clientReference,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get customerId => $composableBuilder(
    column: $table.customerId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get customerName => $composableBuilder(
    column: $table.customerName,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get purpose => $composableBuilder(
    column: $table.purpose,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get outcome => $composableBuilder(
    column: $table.outcome,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get notes => $composableBuilder(
    column: $table.notes,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get visitedAt => $composableBuilder(
    column: $table.visitedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get serverCode => $composableBuilder(
    column: $table.serverCode,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get serverMessage => $composableBuilder(
    column: $table.serverMessage,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get attempts => $composableBuilder(
    column: $table.attempts,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$QueuedVisitsTableAnnotationComposer
    extends Composer<_$FieldDatabase, $QueuedVisitsTable> {
  $$QueuedVisitsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get clientReference => $composableBuilder(
    column: $table.clientReference,
    builder: (column) => column,
  );

  GeneratedColumn<String> get customerId => $composableBuilder(
    column: $table.customerId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get customerName => $composableBuilder(
    column: $table.customerName,
    builder: (column) => column,
  );

  GeneratedColumn<String> get purpose =>
      $composableBuilder(column: $table.purpose, builder: (column) => column);

  GeneratedColumn<String> get outcome =>
      $composableBuilder(column: $table.outcome, builder: (column) => column);

  GeneratedColumn<String> get notes =>
      $composableBuilder(column: $table.notes, builder: (column) => column);

  GeneratedColumn<String> get visitedAt =>
      $composableBuilder(column: $table.visitedAt, builder: (column) => column);

  GeneratedColumnWithTypeConverter<QueueStatus, String> get status =>
      $composableBuilder(column: $table.status, builder: (column) => column);

  GeneratedColumn<String> get serverCode => $composableBuilder(
    column: $table.serverCode,
    builder: (column) => column,
  );

  GeneratedColumn<String> get serverMessage => $composableBuilder(
    column: $table.serverMessage,
    builder: (column) => column,
  );

  GeneratedColumn<int> get attempts =>
      $composableBuilder(column: $table.attempts, builder: (column) => column);
}

class $$QueuedVisitsTableTableManager
    extends
        RootTableManager<
          _$FieldDatabase,
          $QueuedVisitsTable,
          QueuedVisit,
          $$QueuedVisitsTableFilterComposer,
          $$QueuedVisitsTableOrderingComposer,
          $$QueuedVisitsTableAnnotationComposer,
          $$QueuedVisitsTableCreateCompanionBuilder,
          $$QueuedVisitsTableUpdateCompanionBuilder,
          (
            QueuedVisit,
            BaseReferences<_$FieldDatabase, $QueuedVisitsTable, QueuedVisit>,
          ),
          QueuedVisit,
          PrefetchHooks Function()
        > {
  $$QueuedVisitsTableTableManager(_$FieldDatabase db, $QueuedVisitsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$QueuedVisitsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$QueuedVisitsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$QueuedVisitsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> clientReference = const Value.absent(),
                Value<String> customerId = const Value.absent(),
                Value<String> customerName = const Value.absent(),
                Value<String> purpose = const Value.absent(),
                Value<String> outcome = const Value.absent(),
                Value<String?> notes = const Value.absent(),
                Value<String> visitedAt = const Value.absent(),
                Value<QueueStatus> status = const Value.absent(),
                Value<String?> serverCode = const Value.absent(),
                Value<String?> serverMessage = const Value.absent(),
                Value<int> attempts = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => QueuedVisitsCompanion(
                clientReference: clientReference,
                customerId: customerId,
                customerName: customerName,
                purpose: purpose,
                outcome: outcome,
                notes: notes,
                visitedAt: visitedAt,
                status: status,
                serverCode: serverCode,
                serverMessage: serverMessage,
                attempts: attempts,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String clientReference,
                required String customerId,
                required String customerName,
                required String purpose,
                required String outcome,
                Value<String?> notes = const Value.absent(),
                required String visitedAt,
                required QueueStatus status,
                Value<String?> serverCode = const Value.absent(),
                Value<String?> serverMessage = const Value.absent(),
                Value<int> attempts = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => QueuedVisitsCompanion.insert(
                clientReference: clientReference,
                customerId: customerId,
                customerName: customerName,
                purpose: purpose,
                outcome: outcome,
                notes: notes,
                visitedAt: visitedAt,
                status: status,
                serverCode: serverCode,
                serverMessage: serverMessage,
                attempts: attempts,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$QueuedVisitsTable, QueuedVisit>(table),
                  BaseReferences<
                    _$FieldDatabase,
                    $QueuedVisitsTable,
                    QueuedVisit
                  >(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$QueuedVisitsTableProcessedTableManager =
    ProcessedTableManager<
      _$FieldDatabase,
      $QueuedVisitsTable,
      QueuedVisit,
      $$QueuedVisitsTableFilterComposer,
      $$QueuedVisitsTableOrderingComposer,
      $$QueuedVisitsTableAnnotationComposer,
      $$QueuedVisitsTableCreateCompanionBuilder,
      $$QueuedVisitsTableUpdateCompanionBuilder,
      (
        QueuedVisit,
        BaseReferences<_$FieldDatabase, $QueuedVisitsTable, QueuedVisit>,
      ),
      QueuedVisit,
      PrefetchHooks Function()
    >;
typedef $$CachedCustomersTableCreateCompanionBuilder =
    CachedCustomersCompanion Function({
      required String customerId,
      required String customerNumber,
      required String displayName,
      Value<String?> phone,
      required String payload,
      required int position,
      Value<int> rowid,
    });
typedef $$CachedCustomersTableUpdateCompanionBuilder =
    CachedCustomersCompanion Function({
      Value<String> customerId,
      Value<String> customerNumber,
      Value<String> displayName,
      Value<String?> phone,
      Value<String> payload,
      Value<int> position,
      Value<int> rowid,
    });

class $$CachedCustomersTableFilterComposer
    extends Composer<_$FieldDatabase, $CachedCustomersTable> {
  $$CachedCustomersTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get customerId => $composableBuilder(
    column: $table.customerId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get customerNumber => $composableBuilder(
    column: $table.customerNumber,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get displayName => $composableBuilder(
    column: $table.displayName,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get phone => $composableBuilder(
    column: $table.phone,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get payload => $composableBuilder(
    column: $table.payload,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get position => $composableBuilder(
    column: $table.position,
    builder: (column) => ColumnFilters(column),
  );
}

class $$CachedCustomersTableOrderingComposer
    extends Composer<_$FieldDatabase, $CachedCustomersTable> {
  $$CachedCustomersTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get customerId => $composableBuilder(
    column: $table.customerId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get customerNumber => $composableBuilder(
    column: $table.customerNumber,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get displayName => $composableBuilder(
    column: $table.displayName,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get phone => $composableBuilder(
    column: $table.phone,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get payload => $composableBuilder(
    column: $table.payload,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get position => $composableBuilder(
    column: $table.position,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$CachedCustomersTableAnnotationComposer
    extends Composer<_$FieldDatabase, $CachedCustomersTable> {
  $$CachedCustomersTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get customerId => $composableBuilder(
    column: $table.customerId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get customerNumber => $composableBuilder(
    column: $table.customerNumber,
    builder: (column) => column,
  );

  GeneratedColumn<String> get displayName => $composableBuilder(
    column: $table.displayName,
    builder: (column) => column,
  );

  GeneratedColumn<String> get phone =>
      $composableBuilder(column: $table.phone, builder: (column) => column);

  GeneratedColumn<String> get payload =>
      $composableBuilder(column: $table.payload, builder: (column) => column);

  GeneratedColumn<int> get position =>
      $composableBuilder(column: $table.position, builder: (column) => column);
}

class $$CachedCustomersTableTableManager
    extends
        RootTableManager<
          _$FieldDatabase,
          $CachedCustomersTable,
          CachedCustomer,
          $$CachedCustomersTableFilterComposer,
          $$CachedCustomersTableOrderingComposer,
          $$CachedCustomersTableAnnotationComposer,
          $$CachedCustomersTableCreateCompanionBuilder,
          $$CachedCustomersTableUpdateCompanionBuilder,
          (
            CachedCustomer,
            BaseReferences<
              _$FieldDatabase,
              $CachedCustomersTable,
              CachedCustomer
            >,
          ),
          CachedCustomer,
          PrefetchHooks Function()
        > {
  $$CachedCustomersTableTableManager(
    _$FieldDatabase db,
    $CachedCustomersTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$CachedCustomersTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$CachedCustomersTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$CachedCustomersTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> customerId = const Value.absent(),
                Value<String> customerNumber = const Value.absent(),
                Value<String> displayName = const Value.absent(),
                Value<String?> phone = const Value.absent(),
                Value<String> payload = const Value.absent(),
                Value<int> position = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => CachedCustomersCompanion(
                customerId: customerId,
                customerNumber: customerNumber,
                displayName: displayName,
                phone: phone,
                payload: payload,
                position: position,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String customerId,
                required String customerNumber,
                required String displayName,
                Value<String?> phone = const Value.absent(),
                required String payload,
                required int position,
                Value<int> rowid = const Value.absent(),
              }) => CachedCustomersCompanion.insert(
                customerId: customerId,
                customerNumber: customerNumber,
                displayName: displayName,
                phone: phone,
                payload: payload,
                position: position,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$CachedCustomersTable, CachedCustomer>(table),
                  BaseReferences<
                    _$FieldDatabase,
                    $CachedCustomersTable,
                    CachedCustomer
                  >(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$CachedCustomersTableProcessedTableManager =
    ProcessedTableManager<
      _$FieldDatabase,
      $CachedCustomersTable,
      CachedCustomer,
      $$CachedCustomersTableFilterComposer,
      $$CachedCustomersTableOrderingComposer,
      $$CachedCustomersTableAnnotationComposer,
      $$CachedCustomersTableCreateCompanionBuilder,
      $$CachedCustomersTableUpdateCompanionBuilder,
      (
        CachedCustomer,
        BaseReferences<_$FieldDatabase, $CachedCustomersTable, CachedCustomer>,
      ),
      CachedCustomer,
      PrefetchHooks Function()
    >;
typedef $$DeviceStatesTableCreateCompanionBuilder =
    DeviceStatesCompanion Function({
      Value<int> id,
      Value<String?> registrationId,
      Value<int> nextSequenceNo,
      Value<String?> lastSyncedAt,
      Value<String?> officer,
    });
typedef $$DeviceStatesTableUpdateCompanionBuilder =
    DeviceStatesCompanion Function({
      Value<int> id,
      Value<String?> registrationId,
      Value<int> nextSequenceNo,
      Value<String?> lastSyncedAt,
      Value<String?> officer,
    });

class $$DeviceStatesTableFilterComposer
    extends Composer<_$FieldDatabase, $DeviceStatesTable> {
  $$DeviceStatesTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get registrationId => $composableBuilder(
    column: $table.registrationId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get nextSequenceNo => $composableBuilder(
    column: $table.nextSequenceNo,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get lastSyncedAt => $composableBuilder(
    column: $table.lastSyncedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get officer => $composableBuilder(
    column: $table.officer,
    builder: (column) => ColumnFilters(column),
  );
}

class $$DeviceStatesTableOrderingComposer
    extends Composer<_$FieldDatabase, $DeviceStatesTable> {
  $$DeviceStatesTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get registrationId => $composableBuilder(
    column: $table.registrationId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get nextSequenceNo => $composableBuilder(
    column: $table.nextSequenceNo,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get lastSyncedAt => $composableBuilder(
    column: $table.lastSyncedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get officer => $composableBuilder(
    column: $table.officer,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$DeviceStatesTableAnnotationComposer
    extends Composer<_$FieldDatabase, $DeviceStatesTable> {
  $$DeviceStatesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get registrationId => $composableBuilder(
    column: $table.registrationId,
    builder: (column) => column,
  );

  GeneratedColumn<int> get nextSequenceNo => $composableBuilder(
    column: $table.nextSequenceNo,
    builder: (column) => column,
  );

  GeneratedColumn<String> get lastSyncedAt => $composableBuilder(
    column: $table.lastSyncedAt,
    builder: (column) => column,
  );

  GeneratedColumn<String> get officer =>
      $composableBuilder(column: $table.officer, builder: (column) => column);
}

class $$DeviceStatesTableTableManager
    extends
        RootTableManager<
          _$FieldDatabase,
          $DeviceStatesTable,
          DeviceState,
          $$DeviceStatesTableFilterComposer,
          $$DeviceStatesTableOrderingComposer,
          $$DeviceStatesTableAnnotationComposer,
          $$DeviceStatesTableCreateCompanionBuilder,
          $$DeviceStatesTableUpdateCompanionBuilder,
          (
            DeviceState,
            BaseReferences<_$FieldDatabase, $DeviceStatesTable, DeviceState>,
          ),
          DeviceState,
          PrefetchHooks Function()
        > {
  $$DeviceStatesTableTableManager(_$FieldDatabase db, $DeviceStatesTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$DeviceStatesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$DeviceStatesTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$DeviceStatesTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String?> registrationId = const Value.absent(),
                Value<int> nextSequenceNo = const Value.absent(),
                Value<String?> lastSyncedAt = const Value.absent(),
                Value<String?> officer = const Value.absent(),
              }) => DeviceStatesCompanion(
                id: id,
                registrationId: registrationId,
                nextSequenceNo: nextSequenceNo,
                lastSyncedAt: lastSyncedAt,
                officer: officer,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String?> registrationId = const Value.absent(),
                Value<int> nextSequenceNo = const Value.absent(),
                Value<String?> lastSyncedAt = const Value.absent(),
                Value<String?> officer = const Value.absent(),
              }) => DeviceStatesCompanion.insert(
                id: id,
                registrationId: registrationId,
                nextSequenceNo: nextSequenceNo,
                lastSyncedAt: lastSyncedAt,
                officer: officer,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$DeviceStatesTable, DeviceState>(table),
                  BaseReferences<
                    _$FieldDatabase,
                    $DeviceStatesTable,
                    DeviceState
                  >(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$DeviceStatesTableProcessedTableManager =
    ProcessedTableManager<
      _$FieldDatabase,
      $DeviceStatesTable,
      DeviceState,
      $$DeviceStatesTableFilterComposer,
      $$DeviceStatesTableOrderingComposer,
      $$DeviceStatesTableAnnotationComposer,
      $$DeviceStatesTableCreateCompanionBuilder,
      $$DeviceStatesTableUpdateCompanionBuilder,
      (
        DeviceState,
        BaseReferences<_$FieldDatabase, $DeviceStatesTable, DeviceState>,
      ),
      DeviceState,
      PrefetchHooks Function()
    >;

class $FieldDatabaseManager {
  final _$FieldDatabase _db;
  $FieldDatabaseManager(this._db);
  $$QueuedCollectionsTableTableManager get queuedCollections =>
      $$QueuedCollectionsTableTableManager(_db, _db.queuedCollections);
  $$QueuedVisitsTableTableManager get queuedVisits =>
      $$QueuedVisitsTableTableManager(_db, _db.queuedVisits);
  $$CachedCustomersTableTableManager get cachedCustomers =>
      $$CachedCustomersTableTableManager(_db, _db.cachedCustomers);
  $$DeviceStatesTableTableManager get deviceStates =>
      $$DeviceStatesTableTableManager(_db, _db.deviceStates);
}
