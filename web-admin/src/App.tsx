import { Navigate, NavLink, Route, Routes } from "react-router-dom";
import { useAuth } from "./hooks/useAuth";
import LoginPage from "./pages/LoginPage";
import DashboardPage from "./pages/DashboardPage";
import EmployeesPage from "./pages/EmployeesPage";
import ShiftsPage from "./pages/ShiftsPage";
import RosterPage from "./pages/RosterPage";
import RecordsPage from "./pages/RecordsPage";
import RecapPage from "./pages/RecapPage";
import SettingsPage from "./pages/SettingsPage";

const menu = [
  { to: "/", label: "Ringkasan", end: true },
  { to: "/absensi", label: "Absensi" },
  { to: "/rekap", label: "Rekap & Ekspor" },
  { to: "/jadwal", label: "Jadwal" },
  { to: "/karyawan", label: "Karyawan" },
  { to: "/shift", label: "Pola Shift" },
  { to: "/pengaturan", label: "Pengaturan" },
];

export default function App() {
  const { user, role, loading, logout } = useAuth();

  if (loading) {
    return (
      <div className="login">
        <p className="muted">Memuat…</p>
      </div>
    );
  }

  if (!user) return <LoginPage />;

  if (role !== "admin") {
    return (
      <div className="login">
        <div className="card">
          <h2>Akun ini bukan admin</h2>
          <p className="muted">
            {user.email} tidak punya peran <code>admin</code>. Web ini hanya untuk
            admin; akun kios dipakai di tablet. Minta admin menambahkan dokumen{" "}
            <code>users/{user.uid}</code> berisi <code>role: "admin"</code> di console
            Firebase.
          </p>
          <button onClick={() => void logout()}>Keluar</button>
        </div>
      </div>
    );
  }

  return (
    <div className="shell">
      <nav className="sidebar">
        <div className="brand">
          Absensi Cafe
          <small>Panel admin</small>
        </div>
        {menu.map((m) => (
          <NavLink key={m.to} to={m.to} end={m.end}>
            {m.label}
          </NavLink>
        ))}
        <div className="spacer" />
        <div className="account">{user.email}</div>
        <button className="ghost" onClick={() => void logout()}>
          Keluar
        </button>
      </nav>

      <main className="main">
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/absensi" element={<RecordsPage />} />
          <Route path="/rekap" element={<RecapPage />} />
          <Route path="/jadwal" element={<RosterPage />} />
          <Route path="/karyawan" element={<EmployeesPage />} />
          <Route path="/shift" element={<ShiftsPage />} />
          <Route path="/pengaturan" element={<SettingsPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  );
}
