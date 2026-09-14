// Best-effort nutrition lookup with a shared SQLite cache.
// Provider: Open Food Facts — free open API, NO API key required.
// Circular dependency-safe: functions import the DB lazily.
//
// Fallback design:
//   1. Look in the shared food_cache table (never re-queries the same food).
//   2. Query Open Food Facts (search by name, per-100g energy).
//   3. Cache the result so later journal entries pay zero network cost.

let db = null;
function dbc() {
  if (!db) db = require('../db/database');
  return db;
}

const API_URL = 'https://world.openfoodfacts.org/cgi/search.pl';
const REQUEST_TIMEOUT_MS = 4000;

const memoryCache = new Map(); // name -> { kcal_per_100g, source }

/**
 * Resolve calorie-per-100g (or per-100ml approximation) for a food name.
 * Returns { kcalPer100, source } or null.
 */
async function getKcalPer100(foodName) {
  const name = foodName.toLowerCase().trim();
  if (!name) return null;

  // 1. In-memory
  if (memoryCache.has(name)) return memoryCache.get(name);

  // 2. Shared DB cache
  const db = dbc();
  try {
    const cached = db.getFoodCache(name);
    if (cached) {
      memoryCache.set(name, { kcalPer100: cached.kcal_per_100g, source: cached.source });
      return memoryCache.get(name);
    }
  } catch (_) {}

  // 3. Open Food Facts (no key)
  try {
    const url = `${API_URL}?action=process&json=1&page_size=3&search_terms=${encodeURIComponent(name)}`;
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
    const res = await fetch(url, { signal: controller.signal });
    clearTimeout(timer);
    if (!res.ok) return null;

    const data = await res.json();
    const products = Array.isArray(data.products) ? data.products : [];
    const hit = products.find(p => {
      const kcal = Number(p['energy-kcal_100g']);
      return Number.isFinite(kcal) && kcal > 0;
    }) || products.find(p => {
      const kj = Number(p['energy_100g']);
      return Number.isFinite(kj) && kj > 0;
    });

    let kcalPer100 = null;
    let source = 'openfoodfacts';
    if (hit) {
      const kcal = Number(hit['energy-kcal_100g']);
      if (Number.isFinite(kcal) && kcal > 0) {
        kcalPer100 = Math.round(kcal);
      } else {
        const kj = Number(hit['energy_100g']);
        if (Number.isFinite(kj) && kj > 0) {
          kcalPer100 = Math.round(kj / 4.184);
        }
      }
    }

    if (kcalPer100 == null || kcalPer100 <= 0) {
      memoryCache.set(name, null);
      return null;
    }

    const result = { kcalPer100, source };
    memoryCache.set(name, result);
    try { db.setFoodCache(name, kcalPer100, source); } catch (_) {}
    return result;
  } catch (_) {
    memoryCache.set(name, null);
    return null;
  }
}

module.exports = { getKcalPer100 };