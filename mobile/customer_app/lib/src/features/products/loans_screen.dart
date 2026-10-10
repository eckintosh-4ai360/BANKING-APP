import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/providers.dart';
import '../../app/router.dart';
import '../../widgets/common.dart';

class LoansScreen extends ConsumerWidget {
  const LoansScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final loans = ref.watch(loansProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Loans')),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: () => ref.refresh(loansProvider.future),
          child: AsyncBody(
            value: loans,
            onRetry: () => ref.invalidate(loansProvider),
            builder: (loans) => loans.isEmpty
                ? ListView(children: const [Padding(padding: EdgeInsets.all(Gaps.lg), child: Text('You have no loans.'))])
                : ListView(
                    children: [
                      for (final loan in loans)
                        ListTile(
                          leading: const Icon(Icons.request_quote_outlined),
                          title: Text('${loan.productName} · ${loan.loanNumber}'),
                          subtitle: Text([
                            humanize(loan.status),
                            if (loan.nextDueDate != null && loan.isActive) 'next due ${formatDate(loan.nextDueDate!)}',
                            if (loan.daysPastDue > 0) '${loan.daysPastDue} days late',
                          ].join(' · ')),
                          trailing: MoneyText(loan.principalOutstanding),
                          onTap: () => context.push(Routes.loan(loan.id)),
                        ),
                    ],
                  ),
          ),
        ),
      ),
    );
  }
}
