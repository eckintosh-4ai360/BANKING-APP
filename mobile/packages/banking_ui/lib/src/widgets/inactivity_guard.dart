import 'dart:async';

import 'package:flutter/widgets.dart';

/// Calls [onTimeout] after [timeout] without user interaction, or when the app returns from the background after
/// being away longer than [timeout]. Used to sign out unattended sessions on shared or lost devices.
class InactivityGuard extends StatefulWidget {
  const InactivityGuard({
    super.key,
    required this.timeout,
    required this.onTimeout,
    required this.enabled,
    required this.child,
    this.clock,
  });

  final Duration timeout;
  final VoidCallback onTimeout;

  /// Only guard while signed in.
  final bool enabled;
  final Widget child;
  final DateTime Function()? clock;

  @override
  State<InactivityGuard> createState() => _InactivityGuardState();
}

class _InactivityGuardState extends State<InactivityGuard> with WidgetsBindingObserver {
  Timer? _timer;
  DateTime? _backgroundedAt;

  DateTime _now() => (widget.clock ?? DateTime.now)();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _restart();
  }

  @override
  void didUpdateWidget(InactivityGuard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.enabled != widget.enabled || oldWidget.timeout != widget.timeout) {
      _restart();
    }
  }

  void _restart() {
    _timer?.cancel();
    _timer = widget.enabled ? Timer(widget.timeout, _expire) : null;
  }

  void _expire() {
    _timer?.cancel();
    _timer = null;
    if (widget.enabled) {
      widget.onTimeout();
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    switch (state) {
      case AppLifecycleState.paused:
      case AppLifecycleState.hidden:
        _backgroundedAt ??= _now();
        _timer?.cancel();
      case AppLifecycleState.resumed:
        final since = _backgroundedAt;
        _backgroundedAt = null;
        if (since != null && _now().difference(since) >= widget.timeout) {
          _expire();
        } else {
          _restart();
        }
      case AppLifecycleState.inactive:
      case AppLifecycleState.detached:
        break;
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Listener(behavior: HitTestBehavior.translucent, onPointerDown: (_) => _restart(), child: widget.child);
  }
}
