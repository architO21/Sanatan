import 'package:flutter/material.dart';
import '../services/api_service.dart';
import '../services/sync_service.dart';

class JournalScreen extends StatefulWidget {
  final ApiService api;
  final SyncService sync;
  const JournalScreen({super.key, required this.api, required this.sync});

  @override
  State<JournalScreen> createState() => _JournalScreenState();
}

class _JournalScreenState extends State<JournalScreen> {
  final _controller = TextEditingController();
  DateTime _selectedDate = DateTime.now();
  bool _saving = false;
  String? _lastSavedText;

  String get _dateStr =>
      '${_selectedDate.year.toString().padLeft(4, '0')}-'
      '${_selectedDate.month.toString().padLeft(2, '0')}-'
      '${_selectedDate.day.toString().padLeft(2, '0')}';

  @override
  void initState() {
    super.initState();
    _loadEntryForDate();
  }

  Future<void> _loadEntryForDate() async {
    try {
      final data = await widget.api.getJournalEntry(_dateStr);
      final entry = data['entry'];
      if (entry != null && mounted) {
        final raw = entry['raw_text'] as String;
        setState(() {
          _controller.text = raw;
          _lastSavedText = raw;
        });
      } else if (mounted) {
        setState(() {
          _controller.clear();
          _lastSavedText = null;
        });
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to load entry: $e')),
        );
      }
    }
  }

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _selectedDate,
      firstDate: DateTime(2024, 1, 1),
      lastDate: DateTime.now().add(const Duration(days: 1)),
    );
    if (picked != null) {
      setState(() => _selectedDate = picked);
      _loadEntryForDate();
    }
  }

  Future<void> _save() async {
    final text = _controller.text.trim();
    if (text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Write something first!')),
      );
      return;
    }

    setState(() => _saving = true);
    try {
      // Save as a local draft — nothing is sent to the server yet.
      await widget.sync.saveJournalEntry(text, _dateStr);

      if (!mounted) return;
      setState(() => _lastSavedText = text);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Draft saved locally. '
              'Tap "Send" at the top when you are done for the day.'),
          duration: const Duration(seconds: 3),
        ),
      );
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Save failed: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  /// Extracted data for the current date from the last "Send", if any.
  Map<String, dynamic>? _sentExtractedForDate() {
    for (final item in widget.sync.lastSentEntries) {
      if (item['date'] == _dateStr) {
        return (item['extracted_data'] as Map<String, dynamic>?)!;
      }
    }
    return null;
  }

  void _showExtractionSummary(Map<String, dynamic> extracted) {
    final calories = extracted['total_calories'] ?? 0;
    final steps = extracted['total_steps'] ?? 0;
    final study = extracted['study'];
    final hasExtra = (extracted['has_structured_data'] ?? false) as bool;

    if (!hasExtra) {
      showDialog(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('Entry saved'),
          content: const Text(
              'I could not find any structured data (calories, steps, study). '
              'Try mentioning them explicitly, e.g. "had 300 cal salad", "walked 5000 steps".'),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('OK'),
            ),
          ],
        ),
      );
      return;
    }

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('What I extracted'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (calories > 0) ...[
                ListTile(
                  dense: true,
                  leading: const Icon(Icons.restaurant),
                  title: Text('$calories calories'),
                ),
                for (final item in (extracted['calories'] as List? ?? []))
                  if (item is Map && (item['amount_text'] ?? item['description']) != null)
                    Padding(
                      padding: const EdgeInsets.only(left: 56),
                      child: Text(
                        '· ${item['amount_text'] ?? item['description']}  (${item['calories']} cal)',
                        style: TextStyle(fontSize: 13, color: Colors.grey[700]),
                      ),
                    ),
              ],
              if (steps > 0)
                ListTile(
                  dense: true,
                  leading: const Icon(Icons.directions_walk),
                  title: Text('$steps steps'),
                ),
              if (study != null)
                ListTile(
                  dense: true,
                  leading: const Icon(Icons.menu_book),
                  title: Text('Study: ${study['topic']}'),
                  subtitle: Text(
                      '${study['duration_minutes'] ?? 0} min - '
                      '${study['completed'] == true ? "completed" : "incomplete"}'),
                ),
              if (extracted['mood'] != null)
                ListTile(
                  dense: true,
                  leading: const Icon(Icons.mood),
                  title: Text('Mood: ${extracted['mood']}'),
                ),
              if (extracted['sleep_hours'] != null)
                ListTile(
                  dense: true,
                  leading: const Icon(Icons.bedtime),
                  title: Text('Sleep: ${extracted['sleep_hours']} hours'),
                ),
              if (calories == 0 && steps == 0 && study == null)
                const Text(
                    'No structured data found. Try mentioning '
                    'calories, steps, or studying explicitly.'),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Done'),
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Journal'),
        actions: [
          if (_sentExtractedForDate() != null)
            IconButton(
              tooltip: 'What was extracted after sending',
              icon: const Icon(Icons.tips_and_updates_outlined),
              onPressed: () =>
                  _showExtractionSummary(_sentExtractedForDate()!),
            ),
          TextButton(
            onPressed: _pickDate,
            child: Text(
              _dateStr,
              style: const TextStyle(fontWeight: FontWeight.bold),
            ),
          ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(
              child: TextField(
                controller: _controller,
                maxLines: null,
                expands: true,
                textAlignVertical: TextAlignVertical.top,
                decoration: InputDecoration(
                  hintText: 'What did you do today?\n\n'
                      'Example: "Ate oatmeal with banana (300 cal), '
                      'walked 5000 steps, studied Dart for 30 minutes '
                      'but didn\'t finish classes."',
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                  contentPadding:
                      const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                ),
              ),
            ),
            const SizedBox(height: 16),
            FilledButton.icon(
              onPressed: _saving ? null : _save,
              icon: _saving
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.save),
              label: Text(_saving ? 'Saving...' : 'Save entry'),
              style: FilledButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 16),
              ),
            ),
            const SizedBox(height: 8),
            if (_lastSavedText != null)
              const Text(
                'Last saved: today',
                style: TextStyle(color: Colors.grey),
                textAlign: TextAlign.center,
              ),
          ],
        ),
      ),
    );
  }
}