const express = require('express');
const cors = require('cors');
const authRoutes = require('./routes/auth');
const journalRoutes = require('./routes/journal');
const dashboardRoutes = require('./routes/dashboard');
const studyRoutes = require('./routes/study');
const goalsRoutes = require('./routes/goals');
const expensesRoutes = require('./routes/expenses');
const { requireAuth } = require('./middleware/auth');

const app = express();
const PORT = process.env.PORT || 3000;
const startTime = Date.now();

app.use(cors());
app.use(express.json());

// Public
app.get('/health', (req, res) => {
  const uptimeMs = Date.now() - startTime;
  const mins = Math.floor(uptimeMs / 60000);
  const secs = Math.floor((uptimeMs % 60000) / 1000);
  res.json({
    status: 'ok',
    service: 'life-journal-backend',
    port: PORT,
    uptime: `${mins}m ${secs}s`,
    auth: ['POST /api/auth/signup', 'POST /api/auth/login'],
    endpoints: [
      'POST /api/journal',
      'GET  /api/journal',
      'GET  /api/journal/:date',
      'GET  /api/dashboard/daily',
      'GET  /api/dashboard/weekly',
      'POST /api/study/assign',
      'GET  /api/study/today',
      'GET  /api/expenses',
      'POST /api/expenses',
      'POST /api/expenses/batch',
      'GET  /api/expenses/summary',
      'GET  /api/categories',
      'POST /api/categories',
      'GET  /api/goals',
      'POST /api/goals',
    ],
  });
});

// Routes (auth routes are public; everything else requires a session)
app.use('/api/auth', authRoutes);
app.use('/api/journal', requireAuth, journalRoutes);
app.use('/api/dashboard', requireAuth, dashboardRoutes);
app.use('/api/study', requireAuth, studyRoutes);
app.use('/api/goals', requireAuth, goalsRoutes);
app.use('/api', requireAuth, expensesRoutes);

// Error handler
app.use((err, req, res, next) => {
  console.error(err.stack);
  res.status(500).json({ error: 'Internal server error' });
});

app.listen(PORT, () => {
  console.log(`Life Journal backend running on http://localhost:${PORT}`);
  console.log(`Health check: http://localhost:${PORT}/health`);
});