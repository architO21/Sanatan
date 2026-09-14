import 'package:flutter/material.dart';
import '../services/api_service.dart';

class ExpensesScreen extends StatefulWidget {
  final ApiService api;
  const ExpensesScreen({super.key, required this.api});

  @override
  State<ExpensesScreen> createState() => _ExpensesScreenState();
}

class _ExpensesScreenState extends State<ExpensesScreen> {
  DateTime _selectedDate = DateTime.now();
  List<dynamic> _expenses = [];
  List<dynamic> _categories = [];
  Map<String, dynamic>? _summary;
  bool _loading = true;
  String? _error;

  String get _dateStr =>
      '${_selectedDate.year.toString().padLeft(4, '0')}-'
      '${_selectedDate.month.toString().padLeft(2, '0')}-'
      '${_selectedDate.day.toString().padLeft(2, '0')}';

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
      final expenses = await widget.api.getExpenses(date: _dateStr);
      final categories = await widget.api.getCategories();
      final summary = await widget.api.getExpenseSummary(start: _dateStr, end: _dateStr);
      if (mounted) {
        setState(() {
          _expenses = expenses;
          _categories = categories;
          _summary = summary;
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

  String _catName(dynamic exp, int? catId) {
    for (final c in _categories) {
      if ((c['id'] as num).toInt() == catId) return c['name'].toString();
    }
    return exp['custom_category']?.toString() ?? 'uncategorized';
  }

  Color _catColor(int? catId) {
    for (final c in _categories) {
      if ((c['id'] as num).toInt() == catId) {
        final hex = c['color']?.toString() ?? '#9E9E9E';
        try {
          return Color(int.parse(hex.replaceFirst('#', ''), radix: 16) | 0xFF000000);
        } catch (_) {}
      }
    }
    return Colors.grey;
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
      _load();
    }
  }

  Future<void> _addExpense() async {
    final descController = TextEditingController();
    final amountController = TextEditingController();
    int? selectedCat;
    String? customCat;

    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          title: const Text('Add expense'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: descController,
                  autofocus: true,
                  decoration: const InputDecoration(
                      labelText: 'Description', hintText: 'e.g. lunch, movie'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: amountController,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Amount'),
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<int?>(
                  initialValue: selectedCat,
                  decoration: const InputDecoration(labelText: 'Category'),
                  items: [
                    const DropdownMenuItem<int?>(value: null, child: Text('Auto / uncategorized')),
                    ..._categories.map((c) => DropdownMenuItem<int?>(
                          value: (c['id'] as num).toInt(),
                          child: Text(c['name'].toString()),
                        )),
                  ],
                  onChanged: (v) => setDialogState(() => selectedCat = v),
                ),
              ],
            ),
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
      final amount = double.tryParse(amountController.text);
      final desc = descController.text.trim();
      if (amount == null || amount <= 0 || desc.isEmpty) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Enter a description and amount')),
        );
        return;
      }
      try {
        await widget.api.addExpense(
          description: desc,
          amount: amount,
          date: _dateStr,
          categoryId: selectedCat,
          customCategory: customCat,
        );
        await _load();
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Failed to add: $e')),
          );
        }
      }
    }
  }

  Future<void> _manageCategories() async {
    final result = await showDialog<bool>(
      context: context,
      builder: (ctx) => _CategoriesDialog(api: widget.api, categories: _categories),
    );
    if (result == true) _load();
  }

  Future<void> _deleteExpense(int id) async {
    try {
      await widget.api.deleteExpense(id);
      await _load();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Delete failed: $e')),
        );
      }
    }
  }

  double get _totalToday =>
      _expenses.fold(0.0, (sum, e) => sum + ((e['amount'] ?? 0) as num).toDouble());

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Expenses'),
        actions: [
          IconButton(
            icon: const Icon(Icons.category_outlined),
            onPressed: _manageCategories,
            tooltip: 'Categories',
          ),
          IconButton(
            icon: const Icon(Icons.add),
            onPressed: _addExpense,
            tooltip: 'Add expense',
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
                      Text('Could not load expenses.\n$_error',
                          textAlign: TextAlign.center),
                      const SizedBox(height: 12),
                      FilledButton(
                          onPressed: _load, child: const Text('Retry')),
                    ],
                  ),
                )
              : RefreshIndicator(
                  onRefresh: _load,
                  child: ListView(
                    padding: const EdgeInsets.all(16),
                    children: [
                      Row(
                        children: [
                          Text('Date: ',
                              style: Theme.of(context).textTheme.titleMedium),
                          TextButton(
                            onPressed: _pickDate,
                            child: Text(_dateStr,
                                style: const TextStyle(
                                    fontWeight: FontWeight.bold)),
                          ),
                        ],
                      ),
                      _buildTotalCard(),
                      const SizedBox(height: 20),
                      Text('Breakdown',
                          style: Theme.of(context).textTheme.titleMedium),
                      const SizedBox(height: 8),
                      _buildBreakdown(),
                      const SizedBox(height: 20),
                      Text('Transactions',
                          style: Theme.of(context).textTheme.titleMedium),
                      const SizedBox(height: 8),
                      if (_expenses.isEmpty)
                        const Padding(
                          padding: EdgeInsets.symmetric(vertical: 24),
                          child: Center(
                            child: Text('No expenses for this date.',
                                style: TextStyle(color: Colors.grey)),
                          ),
                        )
                      else
                        ..._expenses.map((e) {
                          final catId = (e['category_id'] as num?)?.toInt();
                          return Dismissible(
                            key: Key('exp_${e['id']}'),
                            direction: DismissDirection.endToStart,
                            background: Container(
                              color: Colors.red,
                              alignment: Alignment.centerRight,
                              padding: const EdgeInsets.only(right: 16),
                              child: const Icon(Icons.delete, color: Colors.white),
                            ),
                            onDismissed: (_) =>
                                _deleteExpense((e['id'] as num).toInt()),
                            child: Card(
                              child: ListTile(
                                leading: CircleAvatar(
                                  backgroundColor:
                                      _catColor(catId).withValues(alpha: 0.2),
                                  child: Icon(Icons.receipt_long,
                                      color: _catColor(catId), size: 20),
                                ),
                                title: Text(e['description']?.toString() ?? ''),
                                subtitle: Text(_catName(e, catId)),
                                trailing: Text(
                                  '₹${e['amount']}',
                                  style: const TextStyle(
                                      fontWeight: FontWeight.w600, fontSize: 16),
                                ),
                              ),
                            ),
                          );
                        }),
                    ],
                  ),
                ),
    );
  }

  Widget _buildTotalCard() {
    return Card(
      color: Colors.indigo.shade50,
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            const Text('Total spent',
                style: TextStyle(color: Colors.indigo, fontSize: 13)),
            const SizedBox(height: 4),
            Text('₹${_totalToday.toStringAsFixed(0)}',
                style: const TextStyle(
                    fontSize: 36, fontWeight: FontWeight.bold)),
          ],
        ),
      ),
    );
  }

  Widget _buildBreakdown() {
    final summary = _summary;
    if (summary == null) return const SizedBox.shrink();
    final byCategory = (summary['by_category'] as List?) ?? [];
    if (byCategory.isEmpty) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 8),
        child: Text('No category data yet.',
            style: TextStyle(color: Colors.grey, fontSize: 13)),
      );
    }

    final total = (summary['total'] ?? 0) as num;

    return Column(
      children: byCategory.map((cat) {
        final catTotal = (cat['total'] ?? 0) as num;
        final fraction = total > 0 ? catTotal / total : 0.0;
        final count = (cat['count'] ?? 0) as num;
        final color = _hexColor(cat['category_color']?.toString());
        return Padding(
          padding: const EdgeInsets.symmetric(vertical: 4),
          child: Column(
            children: [
              Row(
                children: [
                  Icon(Icons.circle, size: 10, color: color),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                        cat['category_name']?.toString() ?? 'uncategorized'),
                  ),
                  Text('₹${catTotal.toStringAsFixed(0)}',
                      style: const TextStyle(fontWeight: FontWeight.w600)),
                  const SizedBox(width: 6),
                  Text('($count)',
                      style: const TextStyle(color: Colors.grey, fontSize: 12)),
                ],
              ),
              const SizedBox(height: 4),
              ClipRRect(
                borderRadius: BorderRadius.circular(4),
                child: LinearProgressIndicator(
                  value: fraction.toDouble(),
                  minHeight: 6,
                  backgroundColor: color.withValues(alpha: 0.15),
                  valueColor: AlwaysStoppedAnimation(color),
                ),
              ),
            ],
          ),
        );
      }).toList(),
    );
  }

  Color _hexColor(String? hex) {
    if (hex == null) return Colors.grey;
    try {
      return Color(
          int.parse(hex.replaceFirst('#', ''), radix: 16) | 0xFF000000);
    } catch (_) {
      return Colors.grey;
    }
  }
}

class _CategoriesDialog extends StatefulWidget {
  final ApiService api;
  final List<dynamic> categories;
  const _CategoriesDialog({required this.api, required this.categories});

  @override
  State<_CategoriesDialog> createState() => _CategoriesDialogState();
}

class _CategoriesDialogState extends State<_CategoriesDialog> {
  late List<dynamic> _cats;

  @override
  void initState() {
    super.initState();
    _cats = List.of(widget.categories);
  }

  Future<void> _add() async {
    final nameController = TextEditingController();
    final keywordsController = TextEditingController();
    final colorOptions = [
      '#FF9800', '#2196F3', '#9C27B0', '#E91E63',
      '#4CAF50', '#607D8B', '#FF5722', '#795548',
    ];
    String selectedColor = colorOptions[0];

    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          title: const Text('New category'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: nameController,
                autofocus: true,
                decoration: const InputDecoration(
                    labelText: 'Name', hintText: 'e.g. bestfriendspends'),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: keywordsController,
                decoration: const InputDecoration(
                    labelText: 'Keywords (comma separated)',
                    hintText: 'bestie, bro, friend'),
              ),
              const SizedBox(height: 16),
              Wrap(
                spacing: 8,
                children: colorOptions.map((hex) {
                  final color = Color(
                      int.parse(hex.replaceFirst('#', ''), radix: 16) |
                          0xFF000000);
                  return GestureDetector(
                    onTap: () => setDialogState(() => selectedColor = hex),
                    child: Container(
                      width: 32,
                      height: 32,
                      decoration: BoxDecoration(
                        color: color,
                        shape: BoxShape.circle,
                        border: Border.all(
                          width: 3,
                          color: selectedColor == hex
                              ? Colors.black54
                              : Colors.transparent,
                        ),
                      ),
                      child: selectedColor == hex
                          ? const Icon(Icons.check, color: Colors.white, size: 18)
                          : null,
                    ),
                  );
                }).toList(),
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
              child: const Text('Create'),
            ),
          ],
        ),
      ),
    );

    if (ok == true && mounted) {
      final name = nameController.text.trim().toLowerCase();
      if (name.isEmpty) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Name is required')),
        );
        return;
      }
      try {
        await widget.api.createCategory(
            name, keywordsController.text.trim(), selectedColor);
        await _refresh();
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Failed: $e')),
          );
        }
      }
    }
  }

  Future<void> _refresh() async {
    final cats = await widget.api.getCategories();
    if (mounted) setState(() => _cats = cats);
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Categories'),
      content: SizedBox(
        width: double.maxFinite,
        child: ListView(
          shrinkWrap: true,
          children: [
            ..._cats.map((c) {
              final hex = c['color']?.toString() ?? '#9E9E9E';
              final color = Color(
                  int.parse(hex.replaceFirst('#', ''), radix: 16) | 0xFF000000);
              return ListTile(
                dense: true,
                leading: Icon(Icons.circle, color: color, size: 14),
                title: Text(c['name'].toString()),
                subtitle: c['keywords'] != null
                    ? Text(c['keywords'].toString(),
                        maxLines: 1, overflow: TextOverflow.ellipsis,
                        style: const TextStyle(fontSize: 11))
                    : null,
                trailing: IconButton(
                  icon: const Icon(Icons.delete_outline, size: 20),
                  onPressed: () async {
                    await widget.api
                        .deleteCategory((c['id'] as num).toInt());
                    await _refresh();
                  },
                ),
              );
            }),
            const Divider(),
            ListTile(
              leading: const Icon(Icons.add, size: 20),
              title: const Text('Add category', style: TextStyle(fontSize: 14)),
              onTap: _add,
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context, true),
          child: const Text('Done'),
        ),
      ],
    );
  }
}