import 'package:banking_ui/banking_ui.dart';
import 'package:decimal/decimal.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/providers.dart';
import '../../offline/field_api.dart';
import '../../offline/field_queue.dart';

/// One of the officer's customers, as of the last sync: where cash can go, and buttons to collect or record a visit.
class CustomerScreen extends ConsumerWidget {
  const CustomerScreen({super.key, required this.customerId});

  final String customerId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final customers = ref.watch(customersProvider).value;
    final customer = customers?.where((candidate) => candidate.customerId == customerId).firstOrNull;
    return Scaffold(
      appBar: AppBar(title: Text(customer?.displayName ?? 'Customer')),
      body: SafeArea(
        child: customer == null
            ? const LoadingView()
            : ListView(
                padding: const EdgeInsets.all(Gaps.md),
                children: [
                  Text(customer.customerNumber, style: Theme.of(context).textTheme.bodyLarge),
                  if (customer.phone != null) Text(customer.phone!),
                  const SizedBox(height: Gaps.md),
                  if (customer.susuPlans.isNotEmpty) ...[
                    Text('Susu plans', style: Theme.of(context).textTheme.titleMedium),
                    for (final plan in customer.susuPlans)
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: const Icon(Icons.savings_outlined),
                        title: Text('${plan.currency} ${plan.contributionAmount} ${plan.frequencyCode.toLowerCase()}'),
                        subtitle: Text([
                          if (plan.nextDue != null) 'next due ${plan.nextDue}',
                          if (plan.missed > 0) '${plan.missed} missed',
                        ].join(' · ')),
                        trailing: Icon(plan.unpaidScheduled == 0 ? Icons.check_circle_outline : Icons.add_circle_outline),
                        enabled: plan.unpaidScheduled > 0,
                        onTap: () => _collect(context, customer, plan: plan),
                      ),
                    const SizedBox(height: Gaps.md),
                  ],
                  Text('Accounts (tap one to collect)', style: Theme.of(context).textTheme.titleMedium),
                  for (final account in customer.accounts)
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: const Icon(Icons.account_balance_wallet_outlined),
                      title: Text(account.title),
                      subtitle: Text('${account.accountNumber} · ${account.productType.toLowerCase()}'),
                      trailing: const Icon(Icons.add_circle_outline),
                      onTap: () => _collect(context, customer, account: account),
                    ),
                  const SizedBox(height: Gaps.lg),
                  OutlinedButton.icon(
                    onPressed: () => showModalBottomSheet<void>(
                      context: context,
                      isScrollControlled: true,
                      builder: (_) => VisitSheet(customer: customer),
                    ),
                    icon: const Icon(Icons.route_outlined),
                    label: const Text('Record a visit'),
                  ),
                ],
              ),
      ),
    );
  }

  void _collect(BuildContext context, MyCustomer customer, {CollectableAccount? account, CollectablePlan? plan}) {
    final target = account ?? customer.accounts.firstWhere((candidate) => candidate.accountId == plan!.accountId, orElse: () => _planAccount(plan!));
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (_) => CollectSheet(customer: customer, account: target, plan: plan),
    );
  }

  static CollectableAccount _planAccount(CollectablePlan plan) =>
      CollectableAccount(accountId: plan.accountId, accountNumber: plan.planNumber, title: 'Susu', productType: 'SUSU', currency: plan.currency);
}

/// Records cash taken. For a susu plan the officer picks how many contributions were paid; the amount shown is
/// that many contributions, and the server checks it again.
class CollectSheet extends ConsumerStatefulWidget {
  const CollectSheet({super.key, required this.customer, required this.account, this.plan});

  final MyCustomer customer;
  final CollectableAccount account;
  final CollectablePlan? plan;

  @override
  ConsumerState<CollectSheet> createState() => _CollectSheetState();
}

class _CollectSheetState extends ConsumerState<CollectSheet> {
  final _amount = TextEditingController();
  final _note = TextEditingController();
  int _contributions = 1;
  String? _error;
  bool _saving = false;

  String get _amountText {
    final plan = widget.plan;
    if (plan == null) {
      return _amount.text.trim();
    }
    return (Decimal.parse(plan.contributionAmount) * Decimal.fromInt(_contributions)).toStringAsFixed(2);
  }

  Future<void> _save() async {
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      final saved = await ref.read(fieldQueueProvider).recordCollection(
            customer: widget.customer,
            account: widget.account,
            plan: widget.plan,
            amount: _amountText,
            note: _note.text,
          );
      if (!mounted) {
        return;
      }
      Navigator.of(context).pop();
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('Saved on the phone as collection no. ${saved.sequenceNo}. Give the customer this number.'),
      ));
    } on OfflineLimitException catch (error) {
      setState(() => _error = error.message);
    } on FormatException catch (error) {
      setState(() => _error = error.message);
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  @override
  void dispose() {
    _amount.dispose();
    _note.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final plan = widget.plan;
    return Padding(
      padding: EdgeInsets.fromLTRB(Gaps.md, Gaps.md, Gaps.md, MediaQuery.viewInsetsOf(context).bottom + Gaps.md),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('Collect from ${widget.customer.displayName}', style: Theme.of(context).textTheme.titleMedium),
          Text(plan == null ? widget.account.accountNumber : 'Susu plan ${plan.planNumber}'),
          const SizedBox(height: Gaps.md),
          if (plan == null)
            TextField(
              controller: _amount,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: InputDecoration(labelText: 'Amount (${widget.account.currency})'),
            )
          else
            Row(
              children: [
                const Expanded(child: Text('Contributions paid')),
                IconButton(
                  tooltip: 'Fewer',
                  onPressed: _contributions > 1 ? () => setState(() => _contributions--) : null,
                  icon: const Icon(Icons.remove),
                ),
                Text('$_contributions'),
                IconButton(
                  tooltip: 'More',
                  onPressed: _contributions < plan.unpaidScheduled ? () => setState(() => _contributions++) : null,
                  icon: const Icon(Icons.add),
                ),
              ],
            ),
          if (plan != null) Text('Amount: ${plan.currency} $_amountText', style: Theme.of(context).textTheme.titleSmall),
          TextField(controller: _note, maxLength: 200, decoration: const InputDecoration(labelText: 'Note (optional)')),
          if (_error != null) Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
          const SizedBox(height: Gaps.sm),
          FilledButton(onPressed: _saving ? null : _save, child: const Text('Save collection')),
        ],
      ),
    );
  }
}

class VisitSheet extends ConsumerStatefulWidget {
  const VisitSheet({super.key, required this.customer});

  final MyCustomer customer;

  @override
  ConsumerState<VisitSheet> createState() => _VisitSheetState();
}

class _VisitSheetState extends ConsumerState<VisitSheet> {
  static const purposes = {'COLLECTION': 'Collection', 'FOLLOW_UP': 'Follow-up', 'ONBOARDING': 'Onboarding', 'LOAN_MONITORING': 'Loan monitoring', 'RECOVERY': 'Recovery', 'OTHER': 'Other'};
  static const outcomes = {'MET': 'Met the customer', 'NOT_AVAILABLE': 'Not available', 'PROMISED_TO_PAY': 'Promised to pay', 'REFUSED': 'Refused', 'RELOCATED': 'Relocated', 'OTHER': 'Other'};

  String _purpose = 'COLLECTION';
  String _outcome = 'MET';
  final _notes = TextEditingController();

  @override
  void dispose() {
    _notes.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    await ref.read(fieldQueueProvider).recordVisit(customer: widget.customer, purpose: _purpose, outcome: _outcome, notes: _notes.text);
    if (mounted) {
      Navigator.of(context).pop();
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Visit saved on the phone.')));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.fromLTRB(Gaps.md, Gaps.md, Gaps.md, MediaQuery.viewInsetsOf(context).bottom + Gaps.md),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('Visit to ${widget.customer.displayName}', style: Theme.of(context).textTheme.titleMedium),
          DropdownButtonFormField<String>(
            initialValue: _purpose,
            decoration: const InputDecoration(labelText: 'Purpose'),
            items: [for (final entry in purposes.entries) DropdownMenuItem(value: entry.key, child: Text(entry.value))],
            onChanged: (value) => setState(() => _purpose = value ?? _purpose),
          ),
          DropdownButtonFormField<String>(
            initialValue: _outcome,
            decoration: const InputDecoration(labelText: 'Outcome'),
            items: [for (final entry in outcomes.entries) DropdownMenuItem(value: entry.key, child: Text(entry.value))],
            onChanged: (value) => setState(() => _outcome = value ?? _outcome),
          ),
          TextField(controller: _notes, maxLength: 1000, maxLines: 3, decoration: const InputDecoration(labelText: 'Notes')),
          FilledButton(onPressed: _save, child: const Text('Save visit')),
        ],
      ),
    );
  }
}
