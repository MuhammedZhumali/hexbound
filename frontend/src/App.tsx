import { Routes, Route, Navigate } from 'react-router-dom';
import { SetupPage } from './features/game-setup/SetupPage';
import { GamePage } from './features/game/GamePage';
export default function App() {
  return (
    <Routes>
      <Route path="/" element={<SetupPage />} />
      <Route path="/games/:id" element={<GamePage />} />
      <Route path="*" element={<Navigate to="/" />} />
    </Routes>
  );
}
