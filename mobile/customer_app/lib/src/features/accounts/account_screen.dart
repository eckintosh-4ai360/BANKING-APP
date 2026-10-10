import 'package:banking_core/banking_core.dart';
import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../api/models.dart';
import '../../app/providers.dart';
import '../../app/router.dart';
import '../../widgets/common.dart';

/// One account: its balances and its statement, built by the server from the ledger, over a chosen period.
class AccountScreen extends ConsumerStatefulWidget {
  const AccountScreen({super.key, required this.accountId, this.today});

  final String accountId;

  /// For tests; the device's date otherwise.
  final DateTime? today;

  @override
  ConsumerState<AccountScreen> createState() => _AccountScreenState();
}

class _AccountScreenState extends ConsumerState<AccountScreen> {
  /// Days back from today; null is the server's default period.
  int? _days;

  ({String accountId, DateTime? from, DateTime? to}) get _query {
    final days = _days;
    if (days == null) {
      return (accountId: widget.accountId, from: null, to: null);
    }
    final now = widget.today ?? DateTime.now();
    final today = DateTime.utc(now.year, now.month, now.day);
    return (accountId: widget.accountId, from: today.subtract(Duration(days: days)), to: today);
  }

  @override
  Widget build(BuildContext context) {
    final account = ref.watch(accountsProvider).value?.accounts.where((candidate) => candidate.id == widget.accountId).firstOrNull;
    final statement = ref.watch(statementProvider(_query));
    final text = Theme.of(context).textTheme;
    return Scaffold(
      appBar: AppBar(title: Text(account?.title ?? 'Account')),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: () {
            ref.invalidate(accountsProvider);
            return ref.refresh(statementProvider(_query).future);
          },
          child: ListView(
            padding: const EdgeInsets.all(Gaps.md),
            children: [
              if (account != null) ...[
                Text('${account.productName} · ${account.accountNumber}', style: text.bodyMedium),
                const SizedBox(height: Gaps.sm),
                Text('Available', style: text.labelLarge),
                BalanceText(account.availableBalance, initiallyHidden: false),
                InfoRow('Balance', MoneyText(account.ledgerBalance)),
                if (account.status != 'ACTIVE') InfoRow('Status', Text(humanize(account.status))),
                const SizedBox(height: Gaps.sm),
                if (account.canPayFrom)
                  ActionButton(label: 'Send money', onPressed: () => context.push('${Routes.transfer}?from=${account.id}')),
                const SizedBox(height: Gaps.md),
              ],
              Wrap(
                spacing: Gaps.sm,
                children: [
                  for (final option in const [(label: 'Recent', days: null), (label: '30 days', days: 30), (label: '90 days', days: 90)])
                    ChoiceChip(
                      label: Text(option.label),
                      selected: _days == option.days,
                      onSelected: (_) => setState(() => _days = option.days),
                    ),
                ],
              ),
              const SizedBox(height: Gaps.sm),
              AsyncBody(
                value: statement,
                onRetry: () => ref.invalidate(statementProvider(_query)),
                builder: (statement) => _StatementView(statement: statement),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _StatementView extends StatelessWidget {
  const _StatementView({required this.statement});

  final AccountStatement statement;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text('${formatDate(statement.from)} to ${formatDate(statement.to)}', style: text.titleSmall),
        InfoRow('Opening balance', MoneyText(statement.openingBalance)),
        InfoRow('Money in', MoneyText(statement.totalCredits)),
        InfoRow('Money out', MoneyText(statement.totalDebits)),
        InfoRow('Closing balance', MoneyText(statement.closingBalance)),
        const Divider(),
        if (statement.lines.isEmpty)
          const Padding(padding: EdgeInsets.all(Gaps.md), child: Text('No transactions in this period.'))
        else
          for (final line in statement.lines.reversed)
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(line.description.isEmpty ? line.reference : line.description),
              subtitle: Text('${formatDate(line.date)} · ${line.reference}'),
              trailing: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  if (line.credit != null && !line.credit!.isZero)
                    Text('+ ${const MoneyFormat().format(line.credit!)}', style: text.bodyLarge?.copyWith(color: Colors.green.shade700))
                  else if (line.debit != null && !line.debit!.isZero)
                    Text('- ${const MoneyFormat().format(line.debit!)}', style: text.bodyLarge),
                  MoneyText(line.balance, style: text.bodySmall),
                ],
              ),
            ),
      ],
    );
  }
}
