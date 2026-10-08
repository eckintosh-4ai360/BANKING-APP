import 'package:flutter/material.dart';

import '../theme/banking_theme.dart';

/// Password input with a show/hide toggle. Never pre-filled, never autocorrected.
class PasswordField extends StatefulWidget {
  const PasswordField({
    super.key,
    required this.controller,
    this.label = 'Password',
    this.validator,
    this.autofillHints = const [AutofillHints.password],
    this.textInputAction = TextInputAction.done,
    this.onSubmitted,
  });

  final TextEditingController controller;
  final String label;
  final FormFieldValidator<String>? validator;
  final Iterable<String> autofillHints;
  final TextInputAction textInputAction;
  final ValueChanged<String>? onSubmitted;

  @override
  State<PasswordField> createState() => _PasswordFieldState();
}

class _PasswordFieldState extends State<PasswordField> {
  bool _obscured = true;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller: widget.controller,
      obscureText: _obscured,
      enableSuggestions: false,
      autocorrect: false,
      autofillHints: widget.autofillHints,
      textInputAction: widget.textInputAction,
      onFieldSubmitted: widget.onSubmitted,
      validator: widget.validator,
      decoration: InputDecoration(
        labelText: widget.label,
        suffixIcon: IconButton(
          tooltip: _obscured ? 'Show password' : 'Hide password',
          icon: Icon(_obscured ? Icons.visibility_outlined : Icons.visibility_off_outlined),
          onPressed: () => setState(() => _obscured = !_obscured),
        ),
      ),
    );
  }
}

/// A message banner for errors and notices.
class NoticeBanner extends StatelessWidget {
  const NoticeBanner(this.message, {super.key, this.error = false});

  final String message;
  final bool error;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Semantics(
      liveRegion: true,
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.all(Gaps.md),
        decoration: BoxDecoration(
          color: error ? scheme.errorContainer : scheme.secondaryContainer,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          children: [
            Icon(error ? Icons.error_outline : Icons.info_outline, color: error ? scheme.onErrorContainer : scheme.onSecondaryContainer),
            const SizedBox(width: Gaps.sm),
            Expanded(
              child: Text(message, style: TextStyle(color: error ? scheme.onErrorContainer : scheme.onSecondaryContainer)),
            ),
          ],
        ),
      ),
    );
  }
}

class LoadingView extends StatelessWidget {
  const LoadingView({super.key, this.message});

  final String? message;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const CircularProgressIndicator(),
          if (message != null) ...[const SizedBox(height: Gaps.md), Text(message!)],
        ],
      ),
    );
  }
}

class ErrorView extends StatelessWidget {
  const ErrorView({super.key, required this.message, this.onRetry});

  final String message;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(Gaps.lg),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.cloud_off_outlined, size: 48, color: Theme.of(context).colorScheme.error),
            const SizedBox(height: Gaps.md),
            Text(message, textAlign: TextAlign.center),
            if (onRetry != null) ...[
              const SizedBox(height: Gaps.md),
              OutlinedButton(onPressed: onRetry, child: const Text('Try again')),
            ],
          ],
        ),
      ),
    );
  }
}

/// Institution logo (https only) with an initials fallback, and its name.
class InstitutionHeader extends StatelessWidget {
  const InstitutionHeader({super.key, required this.name, this.logoUrl, this.subtitle});

  final String name;
  final String? logoUrl;
  final String? subtitle;

  static String initialsOf(String name) {
    final words = name.trim().split(RegExp(r'\s+')).where((word) => word.isNotEmpty).toList();
    if (words.isEmpty) {
      return '?';
    }
    return words.take(2).map((word) => word.characters.first.toUpperCase()).join();
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final fallback = CircleAvatar(
      radius: 32,
      backgroundColor: scheme.primary,
      foregroundColor: scheme.onPrimary,
      child: Text(initialsOf(name), style: Theme.of(context).textTheme.titleLarge?.copyWith(color: scheme.onPrimary)),
    );
    final url = logoUrl;
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (url != null && url.startsWith('https://'))
          Image.network(url, height: 64, semanticLabel: '$name logo', errorBuilder: (_, _, _) => fallback)
        else
          fallback,
        const SizedBox(height: Gaps.sm),
        Text(name, style: Theme.of(context).textTheme.titleLarge, textAlign: TextAlign.center),
        if (subtitle != null) Text(subtitle!, style: Theme.of(context).textTheme.bodyMedium, textAlign: TextAlign.center),
      ],
    );
  }
}

/// Home-screen entry. Features not yet available are shown disabled with the reason, never as a dead button.
class FeatureTile extends StatelessWidget {
  const FeatureTile({super.key, required this.icon, required this.title, this.subtitle, this.onTap, this.unavailableReason});

  final IconData icon;
  final String title;
  final String? subtitle;
  final VoidCallback? onTap;
  final String? unavailableReason;

  @override
  Widget build(BuildContext context) {
    final available = onTap != null && unavailableReason == null;
    return Card(
      child: ListTile(
        enabled: available,
        leading: Icon(icon),
        title: Text(title),
        subtitle: Text(unavailableReason ?? subtitle ?? ''),
        trailing: available ? const Icon(Icons.chevron_right) : null,
        onTap: available ? onTap : null,
      ),
    );
  }
}
