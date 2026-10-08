import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/providers.dart';

/// Home skeleton. Accounts, balances and payments come from the customer banking APIs (customer app phase); the
/// app shows nothing it can't back with real data, so there are no placeholder balances here.
class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final branding = ref.watch(brandingProvider).requireValue;
    final text = Theme.of(context).textTheme;
    return Scaffold(
      appBar: AppBar(
        title: Text(branding.displayName),
        actions: [
          IconButton(tooltip: 'Sign out', icon: const Icon(Icons.logout), onPressed: () => ref.read(sessionProvider).signOut()),
        ],
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(Gaps.md),
          children: [
            Text('Welcome', style: text.headlineSmall),
            const SizedBox(height: Gaps.md),
            const Card(
              child: ListTile(
                leading: Icon(Icons.account_balance_wallet_outlined),
                title: Text('Your accounts'),
                subtitle: Text('Your accounts and balances will appear here.'),
              ),
            ),
            const SizedBox(height: Gaps.md),
            const FeatureTile(icon: Icons.swap_horiz, title: 'Transfers', unavailableReason: 'Coming soon'),
            const FeatureTile(icon: Icons.receipt_long_outlined, title: 'Statements', unavailableReason: 'Coming soon'),
            const FeatureTile(icon: Icons.request_quote_outlined, title: 'Loans', unavailableReason: 'Coming soon'),
            const FeatureTile(icon: Icons.savings_outlined, title: 'Savings goals', unavailableReason: 'Coming soon'),
          ],
        ),
      ),
    );
  }
}
