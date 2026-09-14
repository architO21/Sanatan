const express = require('express');
const router = express.Router();
const { today } = require('../services/spillover');

// GET /api/categories - list all expense categories
router.get('/categories', (req, res) => {
  const db = req.db;
  const cats = db.prepare('SELECT * FROM expense_categories ORDER BY name').all();
  res.json(cats);
});

// POST /api/categories - create a new category
router.post('/categories', (req, res) => {
  const db = req.db;
  const { name, keywords, color } = req.body;
  if (!name || !name.trim()) {
    return res.status(400).json({ error: 'name is required' });
  }
  try {
    const info = db.prepare(
      'INSERT INTO expense_categories (name, keywords, color) VALUES (?, ?, ?)'
    ).run(name.trim().toLowerCase(), keywords || null, color || '#9E9E9E');
    res.status(201).json(db.prepare('SELECT * FROM expense_categories WHERE id = ?').get(info.lastInsertRowid));
  } catch (err) {
    if (err.message.includes('UNIQUE')) {
      return res.status(409).json({ error: 'Category already exists' });
    }
    throw err;
  }
});

// DELETE /api/categories/:id
router.delete('/categories/:id', (req, res) => {
  const db = req.db;
  db.prepare('DELETE FROM expense_categories WHERE id = ?').run(req.params.id);
  res.status(204).end();
});

// GET /api/expenses?date=YYYY-MM-DD&start=&end=
router.get('/expenses', (req, res) => {
  const db = req.db;
  const { date, start, end } = req.query;
  let query, params;

  if (date) {
    query = `
      SELECT e.*, c.name as category_name, c.color as category_color
      FROM expenses e
      LEFT JOIN expense_categories c ON e.category_id = c.id
      WHERE e.date = ?
      ORDER BY e.created_at DESC
    `;
    params = [date];
  } else if (start && end) {
    query = `
      SELECT e.*, c.name as category_name, c.color as category_color
      FROM expenses e
      LEFT JOIN expense_categories c ON e.category_id = c.id
      WHERE e.date BETWEEN ? AND ?
      ORDER BY e.date DESC, e.created_at DESC
    `;
    params = [start, end];
  } else {
    query = `
      SELECT e.*, c.name as category_name, c.color as category_color
      FROM expenses e
      LEFT JOIN expense_categories c ON e.category_id = c.id
      ORDER BY e.date DESC, e.created_at DESC
      LIMIT 100
    `;
    params = [];
  }

  res.json(db.prepare(query).all(...params));
});

// POST /api/expenses
router.post('/expenses', (req, res) => {
  const db = req.db;
  const { date, description, amount, category_id, custom_category } = req.body;

  if (!description || !description.trim()) {
    return res.status(400).json({ error: 'description is required' });
  }
  if (!amount || amount <= 0) {
    return res.status(400).json({ error: 'amount must be a positive number' });
  }

  const expenseDate = date || today();

  // Auto-categorize if no category provided
  let resolvedCategoryId = category_id || null;
  let resolvedCustom = custom_category || null;

  if (!resolvedCategoryId && !resolvedCustom) {
    const categories = db.prepare('SELECT * FROM expense_categories').all();
    const descLower = description.toLowerCase();
    for (const cat of categories) {
      const keywords = (cat.keywords || '').split(',').map(k => k.trim().toLowerCase());
      if (keywords.some(kw => kw && descLower.includes(kw))) {
        resolvedCategoryId = cat.id;
        break;
      }
    }
  }

  const info = db.prepare(
    `INSERT INTO expenses (date, description, amount, category_id, custom_category)
     VALUES (?, ?, ?, ?, ?)`
  ).run(expenseDate, description.trim(), amount, resolvedCategoryId, resolvedCustom);

  const expense = db.prepare(
    `SELECT e.*, c.name as category_name, c.color as category_color
     FROM expenses e
     LEFT JOIN expense_categories c ON e.category_id = c.id
     WHERE e.id = ?`
  ).get(info.lastInsertRowid);

  res.status(201).json(expense);
});

// DELETE /api/expenses/:id
router.delete('/expenses/:id', (req, res) => {
  const db = req.db;
  db.prepare('DELETE FROM expenses WHERE id = ?').run(req.params.id);
  res.status(204).end();
});

// GET /api/expenses/summary?start=&end=
router.get('/expenses/summary', (req, res) => {
  const db = req.db;
  const start = req.query.start || today();
  const end = req.query.end || today();

  const byCategory = db.prepare(`
    SELECT c.name as category_name, c.color as category_color,
           SUM(e.amount) as total, COUNT(e.id) as count
    FROM expenses e
    LEFT JOIN expense_categories c ON e.category_id = c.id
    WHERE e.date BETWEEN ? AND ?
    GROUP BY c.name
    ORDER BY total DESC
  `).all(start, end);

  const total = db.prepare(`
    SELECT COALESCE(SUM(amount), 0) as total
    FROM expenses WHERE date BETWEEN ? AND ?
  `).get(start, end).total;

  res.json({ start, end, total, by_category: byCategory });
});

// POST /api/expenses/batch - save multiple expenses at once (from journal extraction)
router.post('/expenses/batch', (req, res) => {
  const db = req.db;
  const { date, expenses: items } = req.body;

  if (!Array.isArray(items) || items.length === 0) {
    return res.status(400).json({ error: 'expenses array is required' });
  }

  const expenseDate = date || today();
  const categories = db.prepare('SELECT * FROM expense_categories').all();

  const insert = db.prepare(
    `INSERT INTO expenses (date, description, amount, category_id, custom_category)
     VALUES (?, ?, ?, ?, ?)`
  );

  const results = [];
  const batchInsert = db.transaction(() => {
    for (const item of items) {
      let resolvedCategoryId = item.category_id || null;
      let resolvedCustom = item.custom_category || null;

      if (!resolvedCategoryId && !resolvedCustom) {
        const descLower = (item.description || '').toLowerCase();
        for (const cat of categories) {
          const keywords = (cat.keywords || '').split(',').map(k => k.trim().toLowerCase());
          if (keywords.some(kw => kw && descLower.includes(kw))) {
            resolvedCategoryId = cat.id;
            break;
          }
        }
      }

      const info = insert.run(
        expenseDate,
        (item.description || '').trim(),
        item.amount,
        resolvedCategoryId,
        resolvedCustom
      );

      results.push({
        id: Number(info.lastInsertRowid),
        date: expenseDate,
        description: (item.description || '').trim(),
        amount: item.amount,
        category_id: resolvedCategoryId,
        custom_category: resolvedCustom
      });
    }
  });

  batchInsert();
  res.status(201).json(results);
});

module.exports = router;