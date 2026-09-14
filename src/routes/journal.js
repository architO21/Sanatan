const express = require('express');
const router = express.Router();
const { extract } = require('../services/extractor');
const { today } = require('../services/spillover');

// POST /api/journal - Create a new journal entry
router.post('/', (req, res) => {
  const db = req.db;
  const { date, raw_text } = req.body;

  if (!raw_text || !raw_text.trim()) {
    return res.status(400).json({ error: 'raw_text is required' });
  }

  const entryDate = date || today();

  // Extract structured data from raw text
  const extracted = extract(raw_text);

  // Upsert entry (one per date)
  const existing = db.prepare('SELECT id FROM journal_entries WHERE date = ?').get(entryDate);

  let entryId;
  if (existing) {
    db.prepare(
      `UPDATE journal_entries SET raw_text = ?, extracted_data = ?, updated_at = datetime('now') WHERE id = ?`
    ).run(raw_text.trim(), JSON.stringify(extracted), existing.id);
    entryId = existing.id;

    // Clear old structured data
    db.prepare('DELETE FROM calories WHERE entry_id = ?').run(entryId);
    db.prepare('DELETE FROM activities WHERE entry_id = ?').run(entryId);
    db.prepare('DELETE FROM expenses WHERE entry_id = ?').run(entryId);
  } else {
    const info = db.prepare(
      `INSERT INTO journal_entries (date, raw_text, extracted_data) VALUES (?, ?, ?)`
    ).run(entryDate, raw_text.trim(), JSON.stringify(extracted));
    entryId = info.lastInsertRowid;
  }

  // Persist structured data
  const insertCalorie = db.prepare(
    `INSERT INTO calories (entry_id, meal_type, description, calories) VALUES (?, ?, ?, ?)`
  );
  for (const c of extracted.calories) {
    insertCalorie.run(entryId, c.meal_type, c.description, c.calories);
  }

  const insertActivity = db.prepare(
    `INSERT INTO activities (entry_id, activity_type, duration_minutes, steps, calories_burned) VALUES (?, ?, ?, ?, ?)`
  );
  for (const a of extracted.activities) {
    insertActivity.run(entryId, a.activity_type, a.duration_minutes, a.steps, a.calories_burned);
  }

  // Persist expenses (auto-categorized)
  if (extracted.expenses && extracted.expenses.length > 0) {
    const categories = db.prepare('SELECT * FROM expense_categories').all();
    const insertExpense = db.prepare(
      `INSERT INTO expenses (entry_id, date, description, amount, category_id, custom_category)
       VALUES (?, ?, ?, ?, ?, ?)`
    );
    for (const exp of extracted.expenses) {
      let categoryId = null;
      const descLower = exp.description.toLowerCase();
      for (const cat of categories) {
        const keywords = (cat.keywords || '').split(',').map(k => k.trim().toLowerCase());
        if (keywords.some(kw => kw && descLower.includes(kw))) {
          categoryId = cat.id;
          break;
        }
      }
      insertExpense.run(entryId, entryDate, exp.description, exp.amount, categoryId, null);
    }
  }

  const entry = db.prepare('SELECT * FROM journal_entries WHERE id = ?').get(entryId);
  res.status(201).json({ entry: { ...entry, extracted_data: JSON.parse(entry.extracted_data) } });
});

// GET /api/journal/:date - Get entry for a specific date
router.get('/:date', (req, res) => {
  const db = req.db;
  const entry = db.prepare('SELECT * FROM journal_entries WHERE date = ?').get(req.params.date);
  if (!entry) {
    return res.status(404).json({ error: 'No entry for this date' });
  }

  const calories = db.prepare('SELECT * FROM calories WHERE entry_id = ?').all(entry.id);
  const activities = db.prepare('SELECT * FROM activities WHERE entry_id = ?').all(entry.id);

  res.json({
    ...entry,
    extracted_data: JSON.parse(entry.extracted_data),
    calories,
    activities
  });
});

// GET /api/journal - List entries (paginated)
router.get('/', (req, res) => {
  const db = req.db;
  const limit = Math.min(parseInt(req.query.limit) || 30, 100);
  const offset = parseInt(req.query.offset) || 0;

  const entries = db.prepare(
    `SELECT * FROM journal_entries ORDER BY date DESC LIMIT ? OFFSET ?`
  ).all(limit, offset);

  const total = db.prepare('SELECT COUNT(*) as count FROM journal_entries').get().count;

  res.json({
    total,
    entries: entries.map(e => ({ ...e, extracted_data: JSON.parse(e.extracted_data) }))
  });
});

module.exports = router;