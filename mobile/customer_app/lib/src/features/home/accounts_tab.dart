import 'package:banking_core/banking_core.dart';
import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../api/models.dart';
import '../../app/providers.dart';
import '../../app/router.dart';
import '../../widgets/common.dart';

/// The customer's accounts with the balances the server reports. Balances start hidden (the app is used in public).
class AccountsTab extends ConsumerWidget {
  const AccountsTab({super.key, required this.profile});

  final CustomerProfile profile;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final overview = ref.watch(accountsProvider);
    final text = Theme.of(context).textTheme;
    return RefreshIndicator(
      onRefresh: () => ref.refresh(accountsProvider.future),
      child: AsyncBody(
        value: overview,
        onRetry: () => ref.invalidate(accountsProvider),
        builder: (overview) => ListView(
          padding: const EdgeInsets.all(Gaps.md),
          children: [
            Text('Hello, ${profile.displayName}', style: text.titleLarge),
            const SizedBox(height: Gaps.md),
            for (final total in overview.totals)
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(Gaps.md),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Available in ${total.currency}', style: text.labelLarge),
                      BalanceText(total),
                    ],
                  ),
                ),
              ),
            if (overview.accounts.isEmpty) const _NoAccounts(),
            for (final account in overview.accounts)
              Card(
                child: ListTile(
                  leading: const Icon(Icons.account_balance_wallet_outlined),
                  title: Text(account.title),
                  subtitle: Text('${account.productName} · ${account.accountNumber}'
                      '${account.status == 'ACTIVE' ? '' : ' · ${humanize(account.status)}'}'),
                  trailing: MoneyText(account.availableBalance, style: text.titleMedium),
                  onTap: () => context.push(Routes.account(account.id)),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

/// No accounts yet: someone who signed up opens their first one here once approved; anyone else at a branch.
class _NoAccounts extends ConsumerWidget {
  const _NoAccounts();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final signUp = ref.watch(onboardingProvider);
    final readyToOpen = signUp.value?.nextStage == 'ACTIVATION';
    if (!readyToOpen) {
      return const Card(
        child: ListTile(
          leading: Icon(Icons.info_outline),
          title: Text('You have no accounts yet'),
          subtitle: Text('Visit a branch to open one.'),
        ),
      );
    }
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(Gaps.md),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('Your details are approved', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: Gaps.sm),
            const Text('Open your account to start banking with us.'),
            const SizedBox(height: Gaps.md),
            PrimaryButton(
              label: 'Open my account',
              onPressed: () async {
                try {
                  await ref.read(customerApiProvider).openFirstAccount();
                  ref
                    ..invalidate(onboardingProvider)
                    ..invalidate(accountsProvider)
                    ..invalidate(unreadProvider);
                } on ApiException catch (error) {
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.message)));
                  }
                }
              },
            ),
          ],
        ),
      ),
    );
  }
}
