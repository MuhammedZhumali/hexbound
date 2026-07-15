import heroEngineer from './optimized/hero-engineer.jpg';
import heroKnight from './optimized/hero-knight.jpg';
import heroMage from './optimized/hero-mage.jpg';
import heroMerchant from './optimized/hero-merchant.jpg';
import heroPriest from './optimized/hero-priest.jpg';
import heroRanger from './optimized/hero-ranger.jpg';
import heroTokenEngineer from './processed/hero-token-engineer.png';
import heroTokenKnight from './processed/hero-token-knight.png';
import heroTokenMage from './processed/hero-token-mage.png';
import heroTokenMerchant from './processed/hero-token-merchant.png';
import heroTokenPriest from './processed/hero-token-priest.png';
import heroTokenRanger from './processed/hero-token-ranger.png';
import monsterGoblin from './processed/monster-goblin.png';
import monsterGolem from './processed/monster-golem.png';
import resourceFood from './processed/resource-food.png';
import resourceGold from './processed/resource-gold.png';
import resourceOre from './processed/resource-ore.png';
import resourceStone from './processed/resource-stone.png';
import resourceWood from './processed/resource-wood.png';

export const heroCardArt: Record<string, string> = {
  ENGINEER: heroEngineer,
  KNIGHT: heroKnight,
  MAGE: heroMage,
  MERCHANT: heroMerchant,
  PRIEST: heroPriest,
  RANGER: heroRanger,
};

export const heroTokenArt: Record<string, string> = {
  ENGINEER: heroTokenEngineer,
  KNIGHT: heroTokenKnight,
  MAGE: heroTokenMage,
  MERCHANT: heroTokenMerchant,
  PRIEST: heroTokenPriest,
  RANGER: heroTokenRanger,
};

export const resourceArt: Record<string, string> = {
  food: resourceFood,
  gold: resourceGold,
  ore: resourceOre,
  stone: resourceStone,
  wood: resourceWood,
};

export function monsterArt(type?: string) {
  const normalized = (type ?? '').toLowerCase();
  if (normalized.includes('golem') || normalized.includes('guardian')) return monsterGolem;
  return monsterGoblin;
}
