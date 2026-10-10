import 'package:banking_core/banking_core.dart';
import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../api/models.dart';
import '../../app/providers.dart';
import '../../widgets/common.dart';

enum _To { beneficiary, accountNumber, ownAccount }

/// Sends money from one of the customer's accounts to their own account, a saved beneficiary or an account number
/// of the institution. The amount is sent exactly as typed; the server checks the balance and the institution's
/// limits. The PIN confirms each transfer, and one idempotency key per transfer makes a retry after a lost answer
/// move the money once.
class TransferScreen extends ConsumerStatefulWidget {
  const TransferScreen({super.key, this.fromAccountId});

  final String? fromAccountId;

  @override
  ConsumerState<TransferScreen> createState() => _TransferScreenState();
}

class _TransferScreenState extends ConsumerState<TransferScreen> {
  final _form = GlobalKey<FormState>();
  final _accountNumber = TextEditingController();
  final _amount = TextEditingController();
  final _narration = TextEditingController();
  String? _fromAccountId;
  _To _to = _To.beneficiary;
  String? _beneficiaryId;
  String? _ownAccountId;
  TransferDestination? _destination;
  String? _error;
  bool _busy = false;
  TransferReceipt? _receipt;

  /// Kept until the server has answered this transfer, so a retry after a lost answer is recognised.
  String? _idempotencyKey;

  @override
  void initState() {
    super.initState();
    _fromAccountId = widget.fromAccountId;
  }

  @override
  void dispose() {
    _accountNumber.dispose();
    _amount.dispose();
    _narration.dispose();
    super.dispose();
  }

  void _changed() {
    // A different transfer: a new key.
    _idempotencyKey = null;
    _error = null;
  }

  Future<void> _lookUp() async {
    final number = _accountNumber.text.trim();
    if (number.isEmpty) {
      return;
    }
    setState(() {
      _destination = null;
      _error = null;
    });
    try {
      final destination = await ref.read(customerApiProvider).destination(number);
      setState(() => _destination = destination);
    } on ApiException catch (error) {
      setState(() => _error = error.message);
    }
  }

  Future<void> _send(List<CustomerAccount> accounts, List<Beneficiary> beneficiaries) async {
    if (!(_form.currentState?.validate() ?? false)) {
      return;
    }
    final from = accounts.firstWhere((account) => account.id == _fromAccountId);
    final String toLabel;
    String? beneficiaryId;
    String? toAccountNumber;
    switch (_to) {
      case _To.beneficiary:
        final beneficiary = beneficiaries.firstWhere((candidate) => candidate.id == _beneficiaryId);
        beneficiaryId = beneficiary.id;
        toLabel = '${beneficiary.nickname} (${beneficiary.accountNumber})';
      case _To.accountNumber:
        final destination = _destination;
        if (destination == null || destination.accountNumber != _accountNumber.text.trim()) {
          setState(() => _error = 'Check the account number first.');
          return;
        }
        toAccountNumber = destination.accountNumber;
        toLabel = '${destination.name} (${destination.accountNumber})';
      case _To.ownAccount:
        final own = accounts.firstWhere((account) => account.id == _ownAccountId);
        toAccountNumber = own.accountNumber;
        toLabel = '${own.title} (${own.accountNumber})';
    }
    final amount = _amount.text.trim();
    final pin = await askForPin(context, action: 'Send ${from.currency} $amount to $toLabel from ${from.accountNumber}.');
    if (pin == null || !mounted) {
      return;
    }
    final key = _idempotencyKey ??= newIdempotencyKey();
    setState(() {
      _error = null;
      _busy = true;
    });
    try {
      final receipt = await ref.read(customerApiProvider).transfer(
            idempotencyKey: key,
            fromAccountId: from.id,
            beneficiaryId: beneficiaryId,
            toAccountNumber: toAccountNumber,
            amount: amount,
            narration: _narration.text.trim().isEmpty ? null : _narration.text.trim(),
            pin: pin,
          );
      ref
        ..invalidate(accountsProvider)
        ..invalidate(profileProvider);
      setState(() {
        _receipt = receipt;
        _idempotencyKey = null;
      });
    } on ApiException catch (error) {
      setState(() {
        // Refused by the server: nothing moved, so the next attempt is a new transfer. Otherwise (no answer) the
        // same key is reused, so the server moves the money at most once.
        final refused = error.kind == ApiErrorKind.rejected || error.kind == ApiErrorKind.unauthenticated;
        if (refused) {
          _idempotencyKey = null;
        }
        _error = refused ? error.message : '${error.message} Sending again is safe: the money moves only once.';
      });
      if (error.code == 'PIN_LOCKED') {
        ref.invalidate(profileProvider);
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final receipt = _receipt;
    return Scaffold(
      appBar: AppBar(title: const Text('Send money')),
      body: SafeArea(child: receipt != null ? _ReceiptView(receipt: receipt) : _formView()),
    );
  }

  Widget _formView() {
    final accounts = ref.watch(accountsProvider);
    final beneficiaries = ref.watch(beneficiariesProvider);
    return AsyncBody(
      value: accounts,
      onRetry: () => ref.invalidate(accountsProvider),
      builder: (overview) {
        final payable = overview.accounts.where((account) => account.canPayFrom).toList();
        if (payable.isEmpty) {
          return const Padding(padding: EdgeInsets.all(Gaps.lg), child: NoticeBanner('You have no account that can send money.'));
        }
        if (!payable.any((account) => account.id == _fromAccountId)) {
          _fromAccountId = payable.first.id;
        }
        final from = payable.firstWhere((account) => account.id == _fromAccountId);
        final saved = beneficiaries.value ?? const <Beneficiary>[];
        final ownTargets = overview.accounts.where((account) => account.id != from.id && account.status == 'ACTIVE').toList();
        return Form(
          key: _form,
          child: ListView(
            padding: const EdgeInsets.all(Gaps.lg),
            children: [
              if (_error != null) ...[NoticeBanner(_error!, error: true), const SizedBox(height: Gaps.md)],
              DropdownButtonFormField<String>(
                initialValue: from.id,
                decoration: const InputDecoration(labelText: 'From'),
                items: [
                  for (final account in payable)
                    DropdownMenuItem(value: account.id, child: Text('${account.title} · ${account.accountNumber}')),
                ],
                onChanged: (id) => setState(() {
                  _fromAccountId = id;
                  _changed();
                }),
              ),
              Padding(
                padding: const EdgeInsets.only(top: Gaps.xs),
                child: Row(children: [const Text('Available: '), MoneyText(from.availableBalance)]),
              ),
              const SizedBox(height: Gaps.md),
              SegmentedButton<_To>(
                segments: const [
                  ButtonSegment(value: _To.beneficiary, label: Text('Saved')),
                  ButtonSegment(value: _To.accountNumber, label: Text('Account no.')),
                  ButtonSegment(value: _To.ownAccount, label: Text('My account')),
                ],
                selected: {_to},
                onSelectionChanged: (selection) => setState(() {
                  _to = selection.single;
                  _changed();
                }),
              ),
              const SizedBox(height: Gaps.md),
              ...switch (_to) {
                _To.beneficiary => [
                    if (saved.isEmpty)
                      const Text('You have no saved beneficiaries yet. Add them under Payments, or pay an account number.')
                    else
                      DropdownButtonFormField<String>(
                        initialValue: _beneficiaryId,
                        decoration: const InputDecoration(labelText: 'To'),
                        items: [
                          for (final beneficiary in saved)
                            DropdownMenuItem(value: beneficiary.id, child: Text('${beneficiary.nickname} · ${beneficiary.accountNumber}')),
                        ],
                        validator: (value) => value == null ? 'Choose who to pay' : null,
                        onChanged: (id) => setState(() {
                          _beneficiaryId = id;
                          _changed();
                        }),
                      ),
                  ],
                _To.accountNumber => [
                    TextFormField(
                      controller: _accountNumber,
                      keyboardType: TextInputType.number,
                      decoration: InputDecoration(
                        labelText: 'Account number',
                        suffixIcon: IconButton(tooltip: 'Check the account', icon: const Icon(Icons.search), onPressed: _lookUp),
                      ),
                      validator: (value) => Validators.required(value, 'Enter the account number'),
                      onChanged: (_) => setState(() {
                        _destination = null;
                        _changed();
                      }),
                      onFieldSubmitted: (_) => _lookUp(),
                    ),
                    if (_destination != null)
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: const Icon(Icons.verified_user_outlined),
                        title: Text(_destination!.name),
                        subtitle: const Text('Make sure this is who you mean to pay.'),
                      ),
                  ],
                _To.ownAccount => [
                    if (ownTargets.isEmpty)
                      const Text('You have no other account to move money to.')
                    else
                      DropdownButtonFormField<String>(
                        initialValue: _ownAccountId,
                        decoration: const InputDecoration(labelText: 'To'),
                        items: [
                          for (final account in ownTargets)
                            DropdownMenuItem(value: account.id, child: Text('${account.title} · ${account.accountNumber}')),
                        ],
                        validator: (value) => value == null ? 'Choose an account' : null,
                        onChanged: (id) => setState(() {
                          _ownAccountId = id;
                          _changed();
                        }),
                      ),
                  ],
              },
              const SizedBox(height: Gaps.md),
              TextFormField(
                controller: _amount,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                decoration: InputDecoration(labelText: 'Amount', prefixText: '${from.currency} '),
                validator: Validators.amount,
                onChanged: (_) => _changed(),
              ),
              const SizedBox(height: Gaps.md),
              TextFormField(
                controller: _narration,
                maxLength: 140,
                decoration: const InputDecoration(labelText: 'Note (optional)'),
                onChanged: (_) => _changed(),
              ),
              const SizedBox(height: Gaps.md),
              ActionButton(label: 'Send', busy: _busy, onPressed: () => _send(overview.accounts, saved)),
            ],
          ),
        );
      },
    );
  }
}

class _ReceiptView extends StatelessWidget {
  const _ReceiptView({required this.receipt});

  final TransferReceipt receipt;

  @override
  Widget build(BuildContext context) => ListView(
        padding: const EdgeInsets.all(Gaps.lg),
        children: [
          const Icon(Icons.check_circle_outline, size: 64),
          const SizedBox(height: Gaps.md),
          Text('Money sent', textAlign: TextAlign.center, style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: Gaps.lg),
          InfoRow('Amount', MoneyText(receipt.amount)),
          if (receipt.fee != null && !receipt.fee!.isZero) InfoRow('Fee', MoneyText(receipt.fee!)),
          InfoRow('To', Text([receipt.toName, receipt.toAccountNumber].whereType<String>().join(' · '))),
          InfoRow('From', Text(receipt.fromAccountNumber)),
          InfoRow('Reference', SelectableText(receipt.reference)),
          if (receipt.postedAt != null) InfoRow('Time', Text(formatDateTime(receipt.postedAt!))),
          if (receipt.availableAfter != null) InfoRow('Available now', MoneyText(receipt.availableAfter!)),
          const SizedBox(height: Gaps.lg),
          PrimaryButton(label: 'Done', onPressed: () async => Navigator.of(context).maybePop()),
        ],
      );
}
