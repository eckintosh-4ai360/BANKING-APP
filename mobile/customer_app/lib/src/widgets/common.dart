import 'package:banking_core/banking_core.dart';
import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

const _months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/// `1 Mar 2027`.
String formatDate(DateTime date) => '${date.day} ${_months[date.month - 1]} ${date.year}';

/// `1 Mar 2027, 14:05` in the phone's time zone.
String formatDateTime(DateTime instant) {
  final local = instant.toLocal();
  return '${formatDate(local)}, ${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}';
}

/// `SELF_EMPLOYED` → `Self employed`.
String humanize(String code) {
  final text = code.replaceAll('_', ' ').toLowerCase();
  return text.isEmpty ? text : text[0].toUpperCase() + text.substring(1);
}

/// What to tell the customer when a call fails.
String messageOf(Object error) => error is ApiException ? error.message : 'Something went wrong. Please try again.';

/// Loading, error (with retry) or the data.
class AsyncBody<T> extends StatelessWidget {
  const AsyncBody({super.key, required this.value, required this.builder, this.onRetry});

  final AsyncValue<T> value;
  final Widget Function(T data) builder;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) => switch (value) {
        AsyncData(:final value) => builder(value),
        AsyncError(:final error) => ErrorView(message: messageOf(error), onRetry: onRetry),
        _ => const LoadingView(),
      };
}

/// A full-width button for an action that asks the customer something first (such as the PIN): it shows progress
/// only while [busy] (the call to the server), never while a dialog waits for the customer.
class ActionButton extends StatelessWidget {
  const ActionButton({super.key, required this.label, required this.onPressed, this.busy = false});

  final String label;
  final VoidCallback? onPressed;
  final bool busy;

  @override
  Widget build(BuildContext context) => FilledButton(
        onPressed: busy ? null : onPressed,
        child: busy ? const SizedBox.square(dimension: 20, child: CircularProgressIndicator(strokeWidth: 2)) : Text(label),
      );
}

/// A digits-only, hidden field for a transaction PIN.
class PinField extends StatelessWidget {
  const PinField({super.key, required this.controller, this.label = 'Transaction PIN', this.validator, this.autofocus = false});

  final TextEditingController controller;
  final String label;
  final FormFieldValidator<String>? validator;
  final bool autofocus;

  @override
  Widget build(BuildContext context) => TextFormField(
        controller: controller,
        autofocus: autofocus,
        obscureText: true,
        enableSuggestions: false,
        autocorrect: false,
        keyboardType: TextInputType.number,
        inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(6)],
        decoration: InputDecoration(labelText: label),
        validator: validator ?? Validators.pin,
      );
}

/// Asks for the transaction PIN to confirm [action]; null when the customer cancels. The PIN is never kept.
Future<String?> askForPin(BuildContext context, {required String action}) => showDialog<String>(
      context: context,
      builder: (context) => _PinDialog(action: action),
    );

class _PinDialog extends StatefulWidget {
  const _PinDialog({required this.action});

  final String action;

  @override
  State<_PinDialog> createState() => _PinDialogState();
}

class _PinDialogState extends State<_PinDialog> {
  final _form = GlobalKey<FormState>();
  final _pin = TextEditingController();

  @override
  void dispose() {
    _pin.dispose();
    super.dispose();
  }

  void _confirm() {
    if (_form.currentState?.validate() ?? false) {
      Navigator.of(context).pop(_pin.text);
    }
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
        title: const Text('Enter your PIN'),
        content: Form(
          key: _form,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [Text(widget.action), const SizedBox(height: Gaps.md), PinField(controller: _pin, autofocus: true)],
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Cancel')),
          FilledButton(onPressed: _confirm, child: const Text('Confirm')),
        ],
      );
}

/// A new password and its confirmation.
class NewPasswordFields extends StatelessWidget {
  const NewPasswordFields({super.key, required this.password, required this.confirmation, this.label = 'New password'});

  final TextEditingController password;
  final TextEditingController confirmation;
  final String label;

  @override
  Widget build(BuildContext context) => Column(
        children: [
          PasswordField(
            controller: password,
            label: label,
            autofillHints: const [AutofillHints.newPassword],
            textInputAction: TextInputAction.next,
            validator: Validators.newPassword,
          ),
          const SizedBox(height: Gaps.sm),
          Text('At least 12 characters. A short sentence is easy to remember.', style: Theme.of(context).textTheme.bodySmall),
          const SizedBox(height: Gaps.md),
          PasswordField(
            controller: confirmation,
            label: 'Repeat the password',
            autofillHints: const [AutofillHints.newPassword],
            textInputAction: TextInputAction.next,
            validator: (value) => value == password.text ? null : 'The passwords do not match',
          ),
        ],
      );
}

/// A new transaction PIN and its confirmation.
class NewPinFields extends StatelessWidget {
  const NewPinFields({super.key, required this.pin, required this.confirmation, this.label = 'New transaction PIN'});

  final TextEditingController pin;
  final TextEditingController confirmation;
  final String label;

  @override
  Widget build(BuildContext context) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          PinField(controller: pin, label: label, validator: Validators.newPin),
          const SizedBox(height: Gaps.sm),
          Text('You confirm payments with it. 4 to 6 digits, not easy to guess.', style: Theme.of(context).textTheme.bodySmall),
          const SizedBox(height: Gaps.md),
          PinField(
            controller: confirmation,
            label: 'Repeat the PIN',
            validator: (value) => value == pin.text ? null : 'The PINs do not match',
          ),
        ],
      );
}

/// A labelled value in a summary.
class InfoRow extends StatelessWidget {
  const InfoRow(this.label, this.value, {super.key});

  final String label;
  final Widget value;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(vertical: Gaps.xs),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(child: Text(label, style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant))),
            const SizedBox(width: Gaps.md),
            Flexible(child: DefaultTextStyle.merge(textAlign: TextAlign.end, child: value)),
          ],
        ),
      );
}
