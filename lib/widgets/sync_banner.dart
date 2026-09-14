import 'package:flutter/material.dart';
import '../services/sync_service.dart';

/// Bar at the top of the app.
/// Journal/expense/study writes are kept as local drafts. When drafts exist,
/// this shows a "Send" button — tapping it pushes everything to the backend
/// in a few requests.
class SyncBanner extends StatelessWidget {
  final SyncService sync;
  final VoidCallback? onSent;
  const SyncBanner({super.key, required this.sync, this.onSent});

  Future<void> _send(BuildContext context) async {
    await sync.forceSync();
    onSent?.call();
  }

  @override
  Widget build(BuildContext context) {
    final state = sync.state;
    final pending = sync.pendingCount;

    // Nothing to send and server healthy -> hide the bar entirely.
    if (pending == 0 && state != SyncState.offline) {
      return const SizedBox.shrink();
    }

    // Drafts waiting to be sent.
    if (pending > 0) {
      return Container(
        color: Colors.indigo.shade50,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        child: Row(
          children: [
            Icon(Icons.edit_note, size: 18, color: Colors.indigo.shade900),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                '$pending draft${pending == 1 ? '' : 's'} ready to send',
                style: TextStyle(color: Colors.indigo.shade900, fontSize: 13),
              ),
            ),
            state == SyncState.syncing
                ? const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : FilledButton.tonal(
                    onPressed: () => _send(context),
                    style: FilledButton.styleFrom(
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      visualDensity: VisualDensity.compact,
                    ),
                    child: const Text('Send'),
                  ),
          ],
        ),
      );
    }

    // Nothing pending, but the server is unreachable right now.
    return Container(
      color: Colors.orange.shade50,
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      child: Row(
        children: [
          Icon(Icons.cloud_off, size: 16, color: Colors.orange.shade900),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              'Server offline',
              style: TextStyle(color: Colors.orange.shade900, fontSize: 12),
            ),
          ),
          Text('Last sent: ${_shortTime(sync.lastSync)}',
              style: TextStyle(color: Colors.orange.shade900, fontSize: 11)),
        ],
      ),
    );
  }

  String _shortTime(String iso) {
    if (iso == 'never') return iso;
    final d = DateTime.tryParse(iso);
    if (d == null) return iso;
    final h = d.hour.toString().padLeft(2, '0');
    final m = d.minute.toString().padLeft(2, '0');
    return '${d.day}/${d.month} $h:$m';
  }
}