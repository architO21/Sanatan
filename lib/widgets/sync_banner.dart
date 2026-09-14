import 'package:flutter/material.dart';
import '../services/sync_service.dart';

/// Thin status bar shown above the content when data is offline/pending.
/// Tapping it forces a sync attempt.
class SyncBanner extends StatelessWidget {
  final SyncService sync;
  const SyncBanner({super.key, required this.sync});

  @override
  Widget build(BuildContext context) {
    final state = sync.state;
    final pending = sync.pendingCount;

    // Connected and nothing pending → hide the banner.
    if (state != SyncState.offline &&
        state != SyncState.error &&
        state != SyncState.syncing &&
        pending == 0) {
      return const SizedBox.shrink();
    }

    final Color bg;
    final Color fg;
    final IconData icon;
    final String text;

    switch (state) {
      case SyncState.syncing:
        bg = Colors.blue.shade50;
        fg = Colors.blue.shade900;
        icon = Icons.sync;
        text = 'Syncing…';
      case SyncState.offline:
      case SyncState.error:
        bg = Colors.orange.shade50;
        fg = Colors.orange.shade900;
        icon = Icons.cloud_off;
        text = pending > 0
            ? 'Offline — $pending item${pending == 1 ? '' : 's'} saved locally. Tap to sync'
            : 'Server offline';
      case SyncState.idle:
        bg = Colors.green.shade50;
        fg = Colors.green.shade900;
        icon = Icons.cloud_done;
        text = 'All synced · last: ${sync.lastSync}';
    }

    return Material(
      color: bg,
      child: InkWell(
        onTap: sync.forceSync,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          child: Row(
            children: [
              Icon(icon, size: 16, color: fg),
              const SizedBox(width: 8),
              Expanded(
                child: Text(text, style: TextStyle(color: fg, fontSize: 12)),
              ),
              if (state == SyncState.syncing)
                const SizedBox(
                  width: 12,
                  height: 12,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              else
                const Icon(Icons.sync, size: 14, color: Colors.black38),
            ],
          ),
        ),
      ),
    );
  }
}