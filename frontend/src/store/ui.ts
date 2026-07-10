import { create } from 'zustand';
import type { Coord } from '../types/game';
type Ui = {
  selected?: Coord;
  zoom: number;
  pan: { x: number; y: number };
  connection: string;
  select: (c?: Coord) => void;
  setZoom: (z: number) => void;
  setPan: (p: { x: number; y: number }) => void;
  reset: () => void;
  setConnection: (s: string) => void;
};
export const useUi = create<Ui>((set) => ({
  zoom: 1,
  pan: { x: 0, y: 0 },
  connection: 'connecting',
  select: (selected) => set({ selected }),
  setZoom: (zoom) => set({ zoom }),
  setPan: (pan) => set({ pan }),
  reset: () => set({ zoom: 1, pan: { x: 0, y: 0 } }),
  setConnection: (connection) => set({ connection }),
}));
