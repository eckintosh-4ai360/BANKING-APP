import 'package:banking_core/banking_core.dart';
import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../api/onboarding.dart';
import '../../app/providers.dart';
import '../../app/router.dart';
import '../../widgets/common.dart';

/// Stages the customer completes themselves (the others are done on registering or by the institution).
const capturedStages = {
  'PERSONAL_DETAILS',
  'IDENTITY_DOCUMENT',
  'SELFIE',
  'ADDRESS',
  'EMPLOYMENT',
  'NEXT_OF_KIN',
  'SIGNATURE',
  'RISK_PROFILE',
};

/// Someone signing up: the stages with what is done, what is next, the review, and the first account once approved.
class OnboardingScreen extends ConsumerWidget {
  const OnboardingScreen({super.key});

  Future<void> _reload(WidgetRef ref) {
    ref.invalidate(profileProvider);
    return ref.refresh(onboardingProvider.future);
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final progress = ref.watch(onboardingProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Open your account'),
        actions: [
          IconButton(tooltip: 'Sign out', icon: const Icon(Icons.logout), onPressed: () => ref.read(sessionProvider).signOut()),
        ],
      ),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: () => _reload(ref),
          child: AsyncBody(
            value: progress,
            onRetry: () => ref.invalidate(onboardingProvider),
            builder: (progress) => _ProgressView(progress: progress),
          ),
        ),
      ),
    );
  }
}

class _ProgressView extends ConsumerWidget {
  const _ProgressView({required this.progress});

  final OnboardingProgress progress;

  Future<void> _submit(BuildContext context, WidgetRef ref) async {
    try {
      await ref.read(customerApiProvider).submitSignUp();
      ref.invalidate(onboardingProvider);
    } on ApiException catch (error) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.message)));
      }
    }
  }

  Future<void> _openAccount(BuildContext context, WidgetRef ref) async {
    try {
      await ref.read(customerApiProvider).openFirstAccount();
      ref
        ..invalidate(onboardingProvider)
        ..invalidate(profileProvider)
        ..invalidate(accountsProvider);
    } on ApiException catch (error) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.message)));
      }
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final text = Theme.of(context).textTheme;
    final review = progress.review;
    final notApproved = progress.stage('COMPLIANCE')?.state == 'NOT_APPROVED';
    final readyToSubmit = progress.nextStage == 'COMPLIANCE' && !progress.inReview;
    return ListView(
      padding: const EdgeInsets.all(Gaps.md),
      children: [
        Text('Your customer number is ${progress.customerNumber}', style: text.bodyMedium),
        const SizedBox(height: Gaps.md),
        if (notApproved)
          const NoticeBanner('We could not approve your sign-up. Please contact us for help.', error: true)
        else if (progress.inReview)
          const NoticeBanner('Thank you. We are checking your details and will let you know when they are approved.')
        else if (review?.status == 'RETURNED')
          NoticeBanner('Some details need correcting: ${review?.note ?? 'please check them'}. Then submit again.', error: true)
        else if (progress.approved && progress.account == null)
          const NoticeBanner('Your details are approved. Open your account below.'),
        const SizedBox(height: Gaps.md),
        for (final stage in progress.stages) _StageTile(stage: stage, progress: progress),
        const SizedBox(height: Gaps.lg),
        if (readyToSubmit) PrimaryButton(label: 'Submit for review', onPressed: () => _submit(context, ref)),
        if (progress.nextStage == 'ACTIVATION') PrimaryButton(label: 'Open my account', onPressed: () => _openAccount(context, ref)),
        if (progress.account != null) ...[
          NoticeBanner('Your ${progress.account!.productName} account ${progress.account!.accountNumber} is open.'),
          const SizedBox(height: Gaps.md),
          PrimaryButton(
            label: 'Go to my accounts',
            onPressed: () async => ref.invalidate(profileProvider),
          ),
        ],
        if (progress.requirements.isNotEmpty) ...[
          const SizedBox(height: Gaps.lg),
          Text('What we check', style: text.titleMedium),
          for (final requirement in progress.requirements)
            ListTile(
              contentPadding: EdgeInsets.zero,
              dense: true,
              leading: Icon(requirement.accepted
                  ? Icons.verified_outlined
                  : requirement.captured
                      ? Icons.check_circle_outline
                      : Icons.radio_button_unchecked),
              title: Text(requirement.description),
            ),
        ],
      ],
    );
  }
}

class _StageTile extends StatelessWidget {
  const _StageTile({required this.stage, required this.progress});

  final OnboardingStage stage;
  final OnboardingProgress progress;

  @override
  Widget build(BuildContext context) {
    final editable = capturedStages.contains(stage.code) && !progress.inReview && !progress.approved;
    final isNext = progress.nextStage == stage.code;
    final (icon, note) = switch (stage.state) {
      'DONE' => (Icons.check_circle, null),
      'IN_REVIEW' => (Icons.hourglass_top, 'Being checked'),
      'ACTION_NEEDED' => (Icons.error_outline, 'Needs correcting'),
      'NOT_APPROVED' => (Icons.cancel_outlined, 'Not approved'),
      _ => (Icons.radio_button_unchecked, stage.required ? null : 'Optional'),
    };
    return ListTile(
      leading: Icon(icon, color: stage.done ? Theme.of(context).colorScheme.primary : null),
      title: Text(stage.title, style: isNext ? const TextStyle(fontWeight: FontWeight.bold) : null),
      subtitle: note == null ? null : Text(note),
      trailing: editable ? const Icon(Icons.chevron_right) : null,
      onTap: editable ? () => context.push(Routes.signUpStage(stage.code)) : null,
    );
  }
}
