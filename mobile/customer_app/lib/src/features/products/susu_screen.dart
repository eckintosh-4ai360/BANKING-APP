import 'package:banking_core/banking_core.dart';
import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/providers.dart';
import '../../app/router.dart';
import '../../widgets/common.dart';

/// The customer's susu plans: contributions collected by field officers or paid at a branch.
class SusuPlansScreen extends ConsumerWidget {
  const SusuPlansScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final plans = ref.watch(susuPlansProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Susu')),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: () => ref.refresh(susuPlansProvider.future),
          child: AsyncBody(
            value: plans,
            onRetry: () => ref.invalidate(susuPlansProvider),
            builder: (plans) => plans.isEmpty
                ? ListView(children: const [Padding(padding: EdgeInsets.all(Gaps.lg), child: Text('You have no susu plans.'))])
                : ListView(
                    children: [
                      for (final plan in plans)
                        ListTile(
                          leading: const Icon(Icons.savings_outlined),
                          title: Text('${const MoneyFormat().format(plan.contributionAmount)} ${humanize(plan.frequencyCode).toLowerCase()}'),
                          subtitle: Text([
                            plan.planNumber,
                            humanize(plan.status),
                            if (plan.missed > 0) '${plan.missed} missed',
                          ].join(' · ')),
                          trailing: MoneyText(plan.totalPaid),
                          onTap: () => context.push(Routes.susuPlan(plan.id)),
                        ),
                    ],
                  ),
          ),
        ),
      ),
    );
  }
}

class SusuPlanScreen extends ConsumerWidget {
  const SusuPlanScreen({super.key, required this.planId});

  final String planId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final detail = ref.watch(susuPlanProvider(planId));
    final text = Theme.of(context).textTheme;
    return Scaffold(
      appBar: AppBar(title: Text(detail.value?.plan.planNumber ?? 'Susu plan')),
      body: SafeArea(
        child: AsyncBody(
          value: detail,
          onRetry: () => ref.invalidate(susuPlanProvider(planId)),
          builder: (detail) {
            final plan = detail.plan;
            return ListView(
              padding: const EdgeInsets.all(Gaps.md),
              children: [
                InfoRow('Contribution', Text('${const MoneyFormat().format(plan.contributionAmount)} ${humanize(plan.frequencyCode).toLowerCase()}')),
                InfoRow('Status', Text(humanize(plan.status))),
                InfoRow('Cycle', Text('${plan.currentCycle} (of ${plan.cycleLength} contributions)')),
                InfoRow('Paid in total', MoneyText(plan.totalPaid)),
                InfoRow('Contributions paid', Text('${plan.paid}')),
                if (plan.missed > 0) InfoRow('Missed', Text('${plan.missed}')),
                if (plan.arrears != null && !plan.arrears!.isZero) InfoRow('Behind by', MoneyText(plan.arrears!)),
                if (plan.nextDue != null) InfoRow('Next due', Text(formatDate(plan.nextDue!))),
                if (plan.targetAmount != null) InfoRow('Target', MoneyText(plan.targetAmount!)),
                if (detail.commissions.isNotEmpty) ...[
                  const SizedBox(height: Gaps.md),
                  Text('Collector’s commission', style: text.titleMedium),
                  for (final commission in detail.commissions) InfoRow('Cycle ${commission.cycle}', MoneyText(commission.amount)),
                ],
                const SizedBox(height: Gaps.md),
                Text('Contributions', style: text.titleMedium),
                for (final contribution in detail.contributions.reversed)
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(contribution.status == 'PAID' ? Icons.check_circle_outline : Icons.radio_button_unchecked),
                    title: Text(formatDate(contribution.dueDate)),
                    subtitle: Text(humanize(contribution.status)),
                    trailing: MoneyText(contribution.amount),
                  ),
              ],
            );
          },
        ),
      ),
    );
  }
}
