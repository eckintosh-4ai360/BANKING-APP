import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../api/models.dart';
import '../../app/providers.dart';
import '../../app/router.dart';
import '../../widgets/common.dart';
import '../onboarding/onboarding_screen.dart';
import 'accounts_tab.dart';
import 'inbox_tab.dart';
import 'more_tab.dart';

/// The signed-in customer's home: banking for customers, the sign-up stages for someone still signing up.
class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profile = ref.watch(profileProvider);
    return switch (profile) {
      AsyncData(:final value) => value.isSigningUp ? const OnboardingScreen() : _BankingHome(profile: value),
      AsyncError(:final error) => Scaffold(
          appBar: AppBar(actions: [_signOutButton(ref)]),
          body: ErrorView(message: messageOf(error), onRetry: () => ref.invalidate(profileProvider)),
        ),
      _ => const Scaffold(body: LoadingView()),
    };
  }
}

Widget _signOutButton(WidgetRef ref) =>
    IconButton(tooltip: 'Sign out', icon: const Icon(Icons.logout), onPressed: () => ref.read(sessionProvider).signOut());

class _BankingHome extends ConsumerStatefulWidget {
  const _BankingHome({required this.profile});

  final CustomerProfile profile;

  @override
  ConsumerState<_BankingHome> createState() => _BankingHomeState();
}

class _BankingHomeState extends ConsumerState<_BankingHome> {
  int _tab = 0;

  @override
  Widget build(BuildContext context) {
    final branding = ref.watch(brandingProvider).requireValue;
    final unread = ref.watch(unreadProvider).value ?? 0;
    const titles = ['Accounts', 'Payments', 'Inbox', 'More'];
    return Scaffold(
      appBar: AppBar(title: Text(_tab == 0 ? branding.displayName : titles[_tab]), actions: [_signOutButton(ref)]),
      body: SafeArea(
        child: switch (_tab) {
          0 => AccountsTab(profile: widget.profile),
          1 => const _PaymentsTab(),
          2 => const InboxTab(),
          _ => MoreTab(profile: widget.profile),
        },
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        onDestinationSelected: (index) => setState(() => _tab = index),
        destinations: [
          const NavigationDestination(icon: Icon(Icons.account_balance_wallet_outlined), label: 'Accounts'),
          const NavigationDestination(icon: Icon(Icons.swap_horiz), label: 'Payments'),
          NavigationDestination(
            icon: Badge(isLabelVisible: unread > 0, label: Text('$unread'), child: const Icon(Icons.notifications_outlined)),
            label: 'Inbox',
          ),
          const NavigationDestination(icon: Icon(Icons.menu), label: 'More'),
        ],
      ),
    );
  }
}

class _PaymentsTab extends StatelessWidget {
  const _PaymentsTab();

  @override
  Widget build(BuildContext context) => ListView(
        padding: const EdgeInsets.all(Gaps.md),
        children: [
          FeatureTile(
            icon: Icons.send_outlined,
            title: 'Send money',
            subtitle: 'To your own accounts, a saved beneficiary or an account number',
            onTap: () => context.push(Routes.transfer),
          ),
          FeatureTile(
            icon: Icons.people_outline,
            title: 'Beneficiaries',
            subtitle: 'People and accounts you pay often',
            onTap: () => context.push(Routes.beneficiaries),
          ),
          FeatureTile(
            icon: Icons.request_quote_outlined,
            title: 'Repay a loan',
            subtitle: 'From the loan’s repayment account',
            onTap: () => context.push(Routes.loans),
          ),
        ],
      );
}
