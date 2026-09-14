import 'package:flutter/material.dart';
import '../services/api_service.dart';

class GoalsScreen extends StatefulWidget {
  final ApiService api;
  const GoalsScreen({super.key, required this.api});

  @override
  State<GoalsScreen> createState() => _GoalsScreenState();
}

class _GoalsScreenState extends State<GoalsScreen> {
  List<dynamic> _goals = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final goals = await widget.api.getGoals();
      if (mounted) {
        setState(() {
          _goals = goals;
          _loading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e.toString();
          _loading = false;
        });
      }
    }
  }

  Future<void> _addGoal() async {
    final valueController = TextEditingController();
    final unitController = TextEditingController();
    String? category = 'calories';

    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          title: const Text('New goal'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              DropdownButtonFormField<String>(
                initialValue: category,
                decoration: const InputDecoration(labelText: 'Category'),
                items: const [
                  DropdownMenuItem(value: 'calories', child: Text('Calories')),
                  DropdownMenuItem(value: 'steps', child: Text('Steps')),
                  DropdownMenuItem(value: 'study', child: Text('Study')),
                ],
                onChanged: (v) => setDialogState(() => category = v),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: valueController,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: 'Target value'),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: unitController,
                decoration: const InputDecoration(
                  labelText: 'Unit (optional)',
                  hintText: 'e.g. minutes, steps',
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
              child: const Text('Save'),
            ),
          ],
        ),
      ),
    );

    if (ok == true && mounted) {
      final value = int.tryParse(valueController.text);
      if (value == null || value <= 0) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Enter a valid target value')),
        );
        return;
      }
      try {
        await widget.api.createGoal(
            category!, value, unitController.text.trim().isEmpty
                ? null
                : unitController.text.trim());
        await _load();
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Failed to save goal: $e')),
          );
        }
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Goals'),
        actions: [
          IconButton(
            icon: const Icon(Icons.add),
            onPressed: _addGoal,
            tooltip: 'Add goal',
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text('Could not load goals.\n$_error',
                          textAlign: TextAlign.center),
                      const SizedBox(height: 12),
                      FilledButton(
                          onPressed: _load, child: const Text('Retry')),
                    ],
                  ),
                )
              : RefreshIndicator(
                  onRefresh: _load,
                  child: _goals.isEmpty
                      ? ListView(
                          children: const [
                            Padding(
                              padding: EdgeInsets.only(top: 48),
                              child: Center(
                                child: Text(
                                  'No goals yet.\nSet a daily calorie, step,\nor study target.',
                                  textAlign: TextAlign.center,
                                  style: TextStyle(color: Colors.grey),
                                ),
                              ),
                            ),
                          ],
                        )
                      : ListView.builder(
                          padding: const EdgeInsets.all(16),
                          itemCount: _goals.length,
                          itemBuilder: (context, i) {
                            final g = _goals[i];
                            return Card(
                              child: ListTile(
                                leading: Icon(
                                  _goalIcon((g['category'] ?? '').toString()),
                                  color: _goalColor(
                                      (g['category'] ?? '').toString()),
                                ),
                                title: Text(g['category']
                                    .toString()
                                    .toUpperCase()),
                                subtitle: Text(
                                    '${g['target_value']} '
                                    '${g['unit'] ?? ''}'.trim()),
                                trailing: Icon(
                                  (g['is_active'] == 1 || g['is_active'] == true)
                                      ? Icons.check_circle
                                      : Icons.radio_button_unchecked,
                                  color:
                                      (g['is_active'] == 1 || g['is_active'] == true)
                                          ? Colors.green
                                          : Colors.grey,
                                ),
                              ),
                            );
                          },
                        ),
                ),
    );
  }

  IconData _goalIcon(String category) => switch (category) {
        'calories' => Icons.restaurant,
        'steps' => Icons.directions_walk,
        'study' => Icons.menu_book,
        _ => Icons.flag,
      };

  Color _goalColor(String category) => switch (category) {
        'calories' => Colors.orange,
        'steps' => Colors.blue,
        'study' => Colors.green,
        _ => Colors.grey,
      };
}