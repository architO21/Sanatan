// Curated food database for calorie estimation.
// Each food provides the fields that the extractor can use:
//   kcalPer100g   - solids, when the journal mentions grams
//   kcalPer100ml  - liquids, when the journal mentions ml/litres
//   kcalPerPiece  - when the journal counts pieces (e.g. "2 chapatis")
// Numbers are rough averages (Indian home portions) and intentionally
// rounded — this is an estimate feature, not nutrition science.

const FOODS = [
  // ── Grains ─────────────────────────────────────────────────────────────
  { name: 'rice (boiled/steamed)', aliases: ['rice', 'boiled rice', 'steamed rice', 'white rice', 'basmati rice', 'jeera rice', 'plain rice'], kcalPer100g: 130 },
  { name: 'curd rice', aliases: ['curd rice', 'yogurt rice', 'dahi rice'], kcalPer100g: 110 },
  { name: 'fried rice', aliases: ['fried rice', 'veg fried rice'], kcalPer100g: 160 },
  { name: 'biryani', aliases: ['biryani', 'chicken biryani', 'veg biryani'], kcalPer100g: 163 },
  { name: 'chapati / roti', aliases: ['chapati', 'chapatis', 'roti', 'rotis', 'phulka', 'phulkas'], kcalPer100g: 297, kcalPerPiece: 120 },
  { name: 'aloo paratha', aliases: ['aloo paratha', 'paratha', 'parathas', 'butter paratha'], kcalPer100g: 320, kcalPerPiece: 310 },
  { name: 'naan', aliases: ['naan', 'butter naan', 'garlic naan'], kcalPer100g: 262, kcalPerPiece: 260 },
  { name: 'dosa', aliases: ['dosa', 'plain dosa', 'masala dosa', 'dosai'], kcalPer100g: 168, kcalPerPiece: 150 },
  { name: 'idli', aliases: ['idli', 'idlis'], kcalPer100g: 78, kcalPerPiece: 58 },
  { name: 'vada', aliases: ['vada', 'medu vada', 'sambar vada'], kcalPer100g: 153, kcalPerPiece: 120 },
  { name: 'poha', aliases: ['poha', 'batata poha'], kcalPer100g: 130 },
  { name: 'upma', aliases: ['upma', 'rava upma'], kcalPer100g: 90 },
  { name: 'oats (cooked)', aliases: ['oats', 'oatmeal', 'porridge', 'dalia'], kcalPer100g: 71 },
  { name: 'bread', aliases: ['bread', 'toast'], kcalPer100g: 265, kcalPerPiece: 80 },
  { name: 'noodles', aliases: ['noodles', 'maggi', 'instant noodles'], kcalPer100g: 138 },
  { name: 'pasta', aliases: ['pasta', 'macaroni', 'spaghetti'], kcalPer100g: 131 },

  // ── Pulses / mains ──────────────────────────────────────────────────────
  { name: 'dal / lentils', aliases: ['dal', 'dhal', 'lentils', 'dal tadka', 'moong dal', 'masoor dal', 'arhar dal'], kcalPer100g: 116 },
  { name: 'chana / chickpeas', aliases: ['chana', 'chole', 'chickpeas', 'chickpea curry'], kcalPer100g: 164 },
  { name: 'rajma', aliases: ['rajma', 'kidney beans'], kcalPer100g: 127 },
  { name: 'mixed vegetable curry', aliases: ['sabzi', 'sabji', 'subzi', 'mixed veg', 'vegetable curry', 'veg curry'], kcalPer100g: 90 },
  { name: 'paneer', aliases: ['paneer', 'paneer tikka', 'shahi paneer', 'matar paneer', 'palak paneer'], kcalPer100g: 265 },
  { name: 'butter chicken', aliases: ['butter chicken', 'chicken curry', 'chicken tikka masala'], kcalPer100g: 165 },
  { name: 'grilled chicken', aliases: ['grilled chicken', 'roasted chicken', 'chicken breast', 'tandoori chicken'], kcalPer100g: 165, kcalPerPiece: 200 },
  { name: 'mutton curry', aliases: ['mutton', 'mutton curry', 'goat curry'], kcalPer100g: 200 },
  { name: 'fish', aliases: ['fish', 'grilled fish', 'fried fish'], kcalPer100g: 120 },
  { name: 'egg', aliases: ['egg', 'eggs', 'boiled egg', 'omelette', 'egg curry'], kcalPer100g: 150, kcalPerPiece: 78 },

  // ── Fruits & veg ────────────────────────────────────────────────────────
  { name: 'banana', aliases: ['banana'], kcalPer100g: 89, kcalPerPiece: 105 },
  { name: 'apple', aliases: ['apple'], kcalPer100g: 52, kcalPerPiece: 95 },
  { name: 'orange', aliases: ['orange'], kcalPer100g: 47, kcalPerPiece: 62 },
  { name: 'mango', aliases: ['mango'], kcalPer100g: 60, kcalPerPiece: 150 },
  { name: 'grapes', aliases: ['grapes'], kcalPer100g: 69 },
  { name: 'watermelon', aliases: ['watermelon'], kcalPer100g: 30 },
  { name: 'potato', aliases: ['potato', 'aloo', 'boiled potato', 'baked potato'], kcalPer100g: 77 },
  { name: 'mashed potato', aliases: ['mashed potato', 'mashed potatoes'], kcalPer100g: 88 },
  { name: 'salad', aliases: ['salad', 'green salad', 'garden salad'], kcalPer100g: 55 },

  // ── Snacks / street ─────────────────────────────────────────────────────
  { name: 'samosa', aliases: ['samosa', 'samosas'], kcalPer100g: 262, kcalPerPiece: 260 },
  { name: 'burger', aliases: ['burger', 'veggie burger'], kcalPer100g: 260, kcalPerPiece: 350 },
  { name: 'pizza', aliases: ['pizza'], kcalPer100g: 266 },
  { name: 'fries', aliases: ['fries', 'french fries'], kcalPer100g: 312 },
  { name: 'biscuits', aliases: ['biscuit', 'biscuits', 'cookie', 'cookies', 'parle-g'], kcalPer100g: 480, kcalPerPiece: 40 },
  { name: 'chips / namkeen', aliases: ['chips', 'potato chips', 'namkeen', 'murukku'], kcalPer100g: 536 },

  // ── Desserts ────────────────────────────────────────────────────────────
  { name: 'chocolate', aliases: ['chocolate', 'chocolate bar', 'milk chocolate', 'dark chocolate'], kcalPer100g: 546, kcalPerPiece: 280 },
  { name: 'ice cream', aliases: ['ice cream', 'kulfi'], kcalPer100g: 207 },
  { name: 'gulab jamun', aliases: ['gulab jamun'], kcalPer100g: 300, kcalPerPiece: 150 },
  { name: 'jalebi', aliases: ['jalebi'], kcalPer100g: 320, kcalPerPiece: 130 },

  // ── Spreads / additions ─────────────────────────────────────────────────
  { name: 'butter', aliases: ['butter'], kcalPer100g: 717 },
  { name: 'sugar', aliases: ['sugar'], kcalPer100g: 387 },
  { name: 'honey', aliases: ['honey'], kcalPer100g: 304 },
  { name: 'peanut butter', aliases: ['peanut butter'], kcalPer100g: 588 },
  { name: 'jam', aliases: ['jam'], kcalPer100g: 250 },

  // ── Drinks ──────────────────────────────────────────────────────────────
  { name: 'water', aliases: ['water'], kcalPer100ml: 0 },
  { name: 'coconut water', aliases: ['coconut water', 'nariyal pani'], kcalPer100ml: 19 },
  { name: 'milk (toned)', aliases: ['milk', 'toned milk', 'full cream milk'], kcalPer100ml: 62 },
  { name: 'buttermilk', aliases: ['buttermilk', 'chaas', 'chhach'], kcalPer100ml: 30 },
  { name: 'curd / yogurt', aliases: ['curd', 'yogurt', 'yoghurt', 'dahi'], kcalPer100g: 61, kcalPer100ml: 61 },
  { name: 'tea (with milk + sugar)', aliases: ['tea', 'chai', 'milk tea', 'masala chai'], kcalPer100ml: 30 },
  { name: 'coffee', aliases: ['coffee', 'filter coffee', 'latte'], kcalPer100ml: 2 },
  { name: 'fruit juice', aliases: ['juice', 'orange juice', 'apple juice', 'mango juice', 'sugarcane juice'], kcalPer100ml: 45 },
  { name: 'soft drink', aliases: ['coke', 'cola', 'soft drink', 'cold drink', 'pepsi', 'sprite'], kcalPer100ml: 42 },
  { name: 'soup', aliases: ['soup', 'tomato soup', 'veg soup', 'chicken soup', 'corn soup'], kcalPer100ml: 45 },
  { name: 'beer', aliases: ['beer'], kcalPer100ml: 43 },
  { name: 'wine', aliases: ['wine'], kcalPer100ml: 85 },
];

// Longest-alias-first so multi-word names (e.g. "butter chicken")
// win over single words.
const SORTED = [...FOODS].sort((a, b) =>
  Math.max(...b.aliases.map(aliasLength)) - Math.max(...a.aliases.map(aliasLength))
);

function aliasLength(a) {
  return a.split(' ').length * 100 + a.length;
}

/**
 * Match a free-text phrase (e.g. "boiled rice", "masala dosa") to a food.
 * Returns the food entry or null.
 */
function matchFood(phrase) {
  if (!phrase) return null;
  const norm = phrase.toLowerCase().replace(/\s+/g, ' ').trim();
  for (const food of SORTED) {
    for (const alias of food.aliases) {
      const words = alias.split(' ');
      // Match as whole words at word boundaries.
      const re = new RegExp(`\\b${words.map(escapeRe).join('\\s+')}\\b`);
      if (re.test(norm)) return food;
    }
  }
  return null;
}

function escapeRe(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Exact food match: the whole phrase (or a whole alias within it) must equal
 * one of the food's aliases word-for-word. Used to fix phrase boundaries so a
 * known food word later in a sentence doesn't get pulled into a wrong match.
 * Returns { food, alias } or null.
 */
function matchExact(phrase) {
  if (!phrase) return null;
  const norm = phrase.toLowerCase().replace(/\s+/g, ' ').trim();
  for (const food of SORTED) {
    for (const alias of food.aliases) {
      const words = alias.split(' ');
      const re = new RegExp(`^${words.map(escapeRe).join('\\s+')}$`);
      if (re.test(norm)) return { food, alias };
    }
  }
  return null;
}

/**
 * Rough calorie estimate for a food + quantity.
 * Returns { name, calories } or null when no estimate is possible.
 */
function estimate(food, { grams, ml, pieces, volumeMl }) {
  if (ml != null && food.kcalPer100ml != null) {
    return { name: food.name, calories: Math.round((ml / 100) * food.kcalPer100ml) };
  }
  if (grams != null && food.kcalPer100g != null) {
    return { name: food.name, calories: Math.round((grams / 100) * food.kcalPer100g) };
  }
  if (volumeMl != null && food.kcalPer100ml != null) {
    return { name: food.name, calories: Math.round((volumeMl / 100) * food.kcalPer100ml) };
  }
  if (pieces != null && food.kcalPerPiece != null) {
    return { name: food.name, calories: Math.round(pieces * food.kcalPerPiece) };
  }
  return null;
}

module.exports = { FOODS, matchFood, matchExact, estimate };