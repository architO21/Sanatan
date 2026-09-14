import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'api_service.dart';

/// Offline-first sync layer.
/// Writes are stored locally first (SharedPreferences - works on web,
/// Android, iOS), then pushed to the backend when connectivity is
/// available. The UI can observe [pendingCount] and [lastSync].
///
/// When the backend comes back online, call [forceSync] (e.g. the sync
/// banner) and all queued items are pushed automatically.

enum SyncState { idle, syncing, offline, error }

class SyncService extends ChangeNotifier {
  final ApiService api;

  SyncService(this.api);

  // Queue keys are namespaced by user so one device never syncs
  // another user's pending data.
  static const _kMeta = 'sync_meta';

  // ── State ──────────────────────────────────────────────────────────────
  SyncState _state = SyncState.idle;
  SyncState get state => _state;

  String get _ns => 'u${ApiService.userId ?? 'anon'}';
  String get _keyJournals => '${_ns}_pending_journals';
  String get _keyExpenses => '${_ns}_pending_expenses';
  String get _keyStudy => '${_ns}_pending_study';

  int _pendingCount = 0;
  int get pendingCount => _pendingCount;

  String _lastSync = 'never';
  String get lastSync => _lastSync;

  int? _lastInitUserId;

  // ── Initialisation ─────────────────────────────────────────────────────
  Future<void> init() async {
    final prefs = await SharedPreferences.getInstance();

    // Re-init when the signed-in user changes so pending counts/sync
    // always reflect the current user's queues.
    if (_lastInitUserId == ApiService.userId) return;

    _lastInitUserId = ApiService.userId;
    _lastSync = prefs.getString(_kMeta) ?? 'never';
    await _recountPending();
    await _trySync();
  }

  // ── Pending queue helpers ──────────────────────────────────────────────
  Future<SharedPreferences> _prefs() => SharedPreferences.getInstance();

  Future<List<Map<String, dynamic>>> _readQueue(String key) async {
    final prefs = await _prefs();
    final raw = prefs.getString(key);
    if (raw == null || raw.isEmpty) return [];
    try {
      final data = jsonDecode(raw);
      return List<Map<String, dynamic>>.from(data);
    } catch (_) {
      return [];
    }
  }

  Future<void> _writeQueue(String key, List<Map<String, dynamic>> queue) async {
    final prefs = await _prefs();
    await prefs.setString(key, jsonEncode(queue));
  }

  Future<void> _recountPending() async {
    final j = await _readQueue(_keyJournals);
    final e = await _readQueue(_keyExpenses);
    final s = await _readQueue(_keyStudy);
    _pendingCount = j.length + e.length + s.length;
    notifyListeners();
  }

  // ── Public: queue a journal entry ──────────────────────────────────────
  Future<void> saveJournalEntry(String rawText, String date) async {
    final queue = await _readQueue(_keyJournals);
    // Latest entry for a date wins.
    queue.removeWhere((item) => item['date'] == date);
    queue.add({
      'date': date,
      'raw_text': rawText,
    });
    await _writeQueue(_keyJournals, queue);
    await _recountPending();
    unawaited(_trySync());
  }

  // ── Public: queue an expense ───────────────────────────────────────────
  Future<void> saveExpense({
    required String description,
    required double amount,
    String? date,
    int? categoryId,
    String? customCategory,
  }) async {
    final queue = await _readQueue(_keyExpenses);
    queue.add({
      'description': description,
      'amount': amount,
      'date': ?date,
      'category_id': ?categoryId,
      'custom_category': ?customCategory,
    });
    await _writeQueue(_keyExpenses, queue);
    await _recountPending();
    unawaited(_trySync());
  }

  // ── Public: queue a study topic update ─────────────────────────────────
  Future<void> saveStudyUpdate(
      int topicId, String status, {int? actualMinutes}) async {
    final queue = await _readQueue(_keyStudy);
    queue.add({
      'topic_id': topicId,
      'status': status,
      'actual_duration_minutes': ?actualMinutes,
    });
    await _writeQueue(_keyStudy, queue);
    await _recountPending();
    unawaited(_trySync());
  }

  // ── Public: queue a new study topic assignment ─────────────────────────
  Future<void> saveStudyAssignment(
      String topic, String? description, int? targetMinutes) async {
    final queue = await _readQueue(_keyStudy);
    queue.add({
      'assign': true,
      'topic': topic,
      'description': ?description,
      'target_duration_minutes': ?targetMinutes,
    });
    await _writeQueue(_keyStudy, queue);
    await _recountPending();
    unawaited(_trySync());
  }

  // ── Connectivity check + sync ──────────────────────────────────────────
  Future<bool> _isServerReachable() async {
    try {
      return await api.getHealth().timeout(const Duration(seconds: 3));
    } catch (_) {
      return false;
    }
  }

  /// Try syncing pending items. Safe to call repeatedly - concurrent callers
  /// share the in-flight sync and await it, so callers always observe the
  /// final state.
  Future<void> _trySync() {
    if (_pendingCount == 0) return Future.value();
    _inflight ??= _doSync().whenComplete(() => _inflight = null);
    return _inflight!;
  }

  Future<void>? _inflight;

  Future<void> _doSync() async {
    if (_state != SyncState.syncing) {
      _state = SyncState.syncing;
      notifyListeners();
    }

    try {
      final reachable = await _isServerReachable();
      if (!reachable) {
        _state = SyncState.offline;
        notifyListeners();
      } else {
        await _syncAll();
        _state = _pendingCount == 0 ? SyncState.idle : SyncState.offline;
        notifyListeners();
      }
    } catch (e) {
      debugPrint('Sync error: $e');
      _state = SyncState.error;
      notifyListeners();
    }
  }

  Future<void> _syncAll() async {
    int synced = 0;

    // 1. Journal entries
    final journalQueue = await _readQueue(_keyJournals);
    if (journalQueue.isNotEmpty) {
      final remaining = <Map<String, dynamic>>[];
      for (final item in journalQueue) {
        try {
          await api.createJournalEntry(
            item['raw_text'] as String,
            item['date'] as String,
          );
          synced++;
        } catch (e) {
          debugPrint('Failed to sync journal entry: $e');
          remaining.add(item);
        }
      }
      await _writeQueue(_keyJournals, remaining);
    }

    // 2. Expenses
    final expenseQueue = await _readQueue(_keyExpenses);
    if (expenseQueue.isNotEmpty) {
      final remaining = <Map<String, dynamic>>[];
      for (final item in expenseQueue) {
        try {
          await api.addExpense(
            description: item['description'] as String,
            amount: (item['amount'] as num).toDouble(),
            date: item['date'] as String?,
            categoryId: item['category_id'] as int?,
            customCategory: item['custom_category'] as String?,
          );
          synced++;
        } catch (e) {
          debugPrint('Failed to sync expense: $e');
          remaining.add(item);
        }
      }
      await _writeQueue(_keyExpenses, remaining);
    }

    // 3. Study topics
    final studyQueue = await _readQueue(_keyStudy);
    if (studyQueue.isNotEmpty) {
      final remaining = <Map<String, dynamic>>[];
      for (final item in studyQueue) {
        try {
          if (item['assign'] == true) {
            await api.assignStudyTopic(
              item['topic'] as String,
              item['description'] as String?,
              item['target_duration_minutes'] as int?,
            );
          } else {
            await api.updateStudyTopic(
              item['topic_id'] as int,
              item['status'] as String,
              item['actual_duration_minutes'] as int?,
            );
          }
          synced++;
        } catch (e) {
          debugPrint('Failed to sync study item: $e');
          remaining.add(item);
        }
      }
      await _writeQueue(_keyStudy, remaining);
    }

    if (synced > 0) {
      _lastSync = DateTime.now().toString().substring(0, 19);
      final prefs = await _prefs();
      await prefs.setString(_kMeta, _lastSync);
    }

    await _recountPending();
  }

  /// Force a sync attempt - call from the UI "Sync" button or after the
  /// backend comes back up.
  Future<void> forceSync() async {
    await _trySync();
  }
}