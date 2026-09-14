import 'package:flutter/material.dart';
import '../services/api_service.dart';

class StudyScreen extends StatefulWidget {
  final ApiService api;
  const StudyScreen({super.key, required this.api});

  @override
  State<StudyScreen> createState() => _StudyScreenState();
}

class _StudyScreenState extends State<StudyScreen> {
  late Future<Map<String, dynamic>> _todayFuture;
  List<dynamic> _recentTopics = [];

  @override
  void initState() {
    super.initState();
    _todayFuture = widget.api.getTodayTopics();
    _loadRecent();
  }

  Future<void> _reload() async {
    setState(() {
      _todayFuture = widget.api.getTodayTopics();
    });
    await _todayFuture;
    _loadRecent();
  }

  Future<void> _loadRecent() async {
    try {
      final topics = await widget.api.getStudyTopics();
      if (mounted) setState(() => _recentTopics = topics);
    } catch (_) {}
  }

  Future<void> _assignTopic() async {
    final topicController = TextEditingController();
    final durationController = TextEditingController(text: '30');

    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('New study topic'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: topicController,
              autofocus: true,
              decoration: const InputDecoration(
                labelText: 'Topic',
                hintText: 'e.g. Flutter widgets',
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: durationController,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                labelText: 'Target minutes',
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Assign'),
          ),
        ],
      ),
    );

    if (ok == true && mounted) {
      final topic = topicController.text.trim();
      if (topic.isEmpty) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Topic is required')),
        );
        return;
      }
      try {
        await widget.api.assignStudyTopic(
          topic,
          null,
          int.tryParse(durationController.text),
        );
        await _reload();
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Failed to assign: $e')),
          );
        }
      }
    }
  }

  Future<void> _updateStatus(Map topic, String status) async {
    try {
      final minutes = (topic['actual_duration_minutes'] ?? 0) as num;
      await widget.api.updateStudyTopic(
        (topic['id'] as num).toInt(),
        status,
        minutes.toInt(),
      );
      await _reload();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Update failed: $e')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Study'),
        actions: [
          IconButton(
            icon: const Icon(Icons.add),
            onPressed: _assignTopic,
            tooltip: 'Assign topic',
          ),
        ],
      ),
      body: FutureBuilder<Map<String, dynamic>>(
        future: _todayFuture,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text('Could not load topics.\n${snapshot.error}',
                      textAlign: TextAlign.center),
                  const SizedBox(height: 12),
                  FilledButton(
                      onPressed: _reload, child: const Text('Retry')),
                ],
              ),
            );
          }

          final data = snapshot.data!;
          final topics = (data['topics'] as List);
          final spillover = (data['spillover'] as List);

          return RefreshIndicator(
            onRefresh: _reload,
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                if (spillover.isNotEmpty) ...[
                  Text('Carried over from yesterday',
                      style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 8),
                  ...spillover.map((item) => _buildTopicCard(item)),
                  const SizedBox(height: 24),
                ],
                if (topics.isNotEmpty) ...[
                  Text('Today',
                      style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 8),
                  ...topics.map((item) => _buildTopicCard(item)),
                ],
                if (topics.isEmpty && spillover.isEmpty)
                  const Padding(
                    padding: EdgeInsets.symmetric(vertical: 48),
                    child: Center(
                      child: Text(
                          'No topics for today.\nTap + to assign one.',
                          textAlign: TextAlign.center,
                          style: TextStyle(color: Colors.grey)),
                    ),
                  ),
                if (_recentTopics.isNotEmpty) ...[
                  const SizedBox(height: 24),
                  Text('Recent',
                      style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 8),
                  ..._recentTopics.take(10).map((item) {
                    return ListTile(
                      dense: true,
                      leading: _statusIcon((item['status'] ?? '').toString()),
                      title: Text(item['topic']?.toString() ?? ''),
                      subtitle: Text(
                          '${item['assigned_date']} · '
                          '${item['target_duration_minutes']} min'),
                      trailing: Text(_statusLabel(
                          (item['status'] ?? '').toString())),
                    );
                  }),
                ],
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _buildTopicCard(Map item) {
    final status = (item['status'] ?? 'pending').toString();
    final isSpillover = item['spillover_from'] != null;
    final isCompleted = status == 'completed';
    final isPending = status == 'pending' || status == 'in_progress';

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    item['topic']?.toString() ?? '',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                      decoration:
                          isCompleted ? TextDecoration.lineThrough : null,
                    ),
                  ),
                ),
                if (isSpillover)
                  Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 8, vertical: 2),
                    decoration: BoxDecoration(
                      color: Colors.deepOrange.shade50,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Text('spillover',
                        style:
                            TextStyle(color: Colors.deepOrange, fontSize: 11)),
                  ),
              ],
            ),
            if (item['description'] != null) ...[
              const SizedBox(height: 4),
              Text(item['description'].toString(),
                  style: const TextStyle(color: Colors.grey)),
            ],
            const SizedBox(height: 8),
            Text('Target: ${item['target_duration_minutes']} min',
                style: const TextStyle(fontSize: 12)),
            const SizedBox(height: 12),
            Row(
              children: [
                if (isCompleted)
                  const Chip(
                    label: Text('Completed',
                        style: TextStyle(color: Colors.green)),
                    backgroundColor: Colors.green,
                    side: BorderSide(color: Colors.green),
                  )
                else if (isPending) ...[
                  FilledButton.icon(
                    onPressed: () => _updateStatus(item, 'completed'),
                    icon: const Icon(Icons.check, size: 18),
                    label: const Text('Done'),
                  ),
                  const SizedBox(width: 8),
                  OutlinedButton.icon(
                    onPressed: () => _updateStatus(item, 'incomplete'),
                    icon: const Icon(Icons.block, size: 18),
                    label: const Text("Won't do"),
                  ),
                ] else
                  Text(_statusLabel(status),
                      style: const TextStyle(color: Colors.grey)),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _statusIcon(String status) {
    final icon = switch (status) {
      'completed' => Icons.check_circle,
      'incomplete' => Icons.cancel,
      _ => Icons.schedule,
    };
    final color = switch (status) {
      'completed' => Colors.green,
      'incomplete' => Colors.red,
      _ => Colors.orange,
    };
    return Icon(icon, color: color, size: 20);
  }

  String _statusLabel(String status) => switch (status) {
        'completed' => 'completed',
        'incomplete' => 'incomplete',
        'in_progress' => 'in progress',
        _ => 'pending',
      };
}