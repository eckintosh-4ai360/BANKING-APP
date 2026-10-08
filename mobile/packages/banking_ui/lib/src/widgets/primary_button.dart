import 'package:flutter/material.dart';

/// A full-width button that runs an async action and stays disabled until it finishes, so a nervous double tap
/// can't submit the same request twice.
class PrimaryButton extends StatefulWidget {
  const PrimaryButton({super.key, required this.label, required this.onPressed, this.outlined = false});

  final String label;

  /// Null disables the button.
  final Future<void> Function()? onPressed;
  final bool outlined;

  @override
  State<PrimaryButton> createState() => _PrimaryButtonState();
}

class _PrimaryButtonState extends State<PrimaryButton> {
  bool _busy = false;

  Future<void> _run() async {
    final action = widget.onPressed;
    if (_busy || action == null) {
      return;
    }
    setState(() => _busy = true);
    try {
      await action();
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final onPressed = widget.onPressed == null || _busy ? null : _run;
    final child = _busy
        ? const SizedBox.square(dimension: 20, child: CircularProgressIndicator(strokeWidth: 2))
        : Text(widget.label);
    return Semantics(
      button: true,
      enabled: onPressed != null,
      label: _busy ? '${widget.label}, in progress' : null,
      child: widget.outlined
          ? OutlinedButton(onPressed: onPressed, child: child)
          : FilledButton(onPressed: onPressed, child: child),
    );
  }
}
