const express = require('express');
const router = express.Router();

// GET /api/goals - Get active goals
router.get('/', (req, res) => {
  const db = req.db;
  const goals = db.prepare(
    'SELECT * FROM goals WHERE is_active = 1 ORDER BY category'
  ).all();
  res.json(goals);
});

// POST /api/goals - Set a new goal
router.post('/', (req, res) => {
  const db = req.db;
  const { category, target_value, unit } = req.body;

  const validCategories = ['calories', 'steps', 'study'];
  if (!validCategories.includes(category)) {
    return res.status(400).json({ error: `category must be one of: ${validCategories.join(', ')}` });
  }
  if (!target_value || target_value <= 0) {
    return res.status(400).json({ error: 'target_value must be a positive number' });
  }

  // Deactivate existing goals for this category
  db.prepare('UPDATE goals SET is_active = 0 WHERE category = ?').run(category);

  const info = db.prepare(
    'INSERT INTO goals (category, target_value, unit) VALUES (?, ?, ?)'
  ).run(category, parseInt(target_value), unit || null);

  res.status(201).json(db.prepare('SELECT * FROM goals WHERE id = ?').get(info.lastInsertRowid));
});

// PUT /api/goals/:id - Update a goal
router.put('/:id', (req, res) => {
  const db = req.db;
  const { target_value, unit, is_active } = req.body;
  const goal = db.prepare('SELECT * FROM goals WHERE id = ?').get(req.params.id);
  if (!goal) {
    return res.status(404).json({ error: 'Goal not found' });
  }

  db.prepare(
    'UPDATE goals SET target_value = COALESCE(?, target_value), unit = COALESCE(?, unit), is_active = COALESCE(?, is_active) WHERE id = ?'
  ).run(target_value || null, unit || null, is_active === undefined ? null : (is_active ? 1 : 0), req.params.id);

  res.json(db.prepare('SELECT * FROM goals WHERE id = ?').get(req.params.id));
});

// DELETE /api/goals/:id - Delete a goal
router.delete('/:id', (req, res) => {
  const db = req.db;
  db.prepare('DELETE FROM goals WHERE id = ?').run(req.params.id);
  res.status(204).end();
});

module.exports = router;