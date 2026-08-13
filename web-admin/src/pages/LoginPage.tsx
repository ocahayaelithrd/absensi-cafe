import { useState, type FormEvent } from "react";
import { FirebaseError } from "firebase/app";
import { useAuth } from "../hooks/useAuth";

export default function LoginPage() {
  const { login } = useAuth();
  const [email, setEmail] = useState("");
  const [sandi, setSandi] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email.trim(), sandi);
    } catch (err) {
      setError(pesan(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login">
      <form className="card" onSubmit={(e) => void submit(e)}>
        <h1>Admin Absensi Cafe</h1>
        <p className="muted">Masuk dengan akun admin yang dibuat di console Firebase.</p>
        <div style={{ height: 16 }} />

        <label className="field">
          <span>Email</span>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="username"
            required
          />
        </label>
        <label className="field">
          <span>Kata sandi</span>
          <input
            type="password"
            value={sandi}
            onChange={(e) => setSandi(e.target.value)}
            autoComplete="current-password"
            required
          />
        </label>

        {error && <div className="notice bad">{error}</div>}

        <button className="primary" type="submit" disabled={busy}>
          {busy ? "Memeriksa…" : "Masuk"}
        </button>
      </form>
    </div>
  );
}

function pesan(err: unknown): string {
  if (err instanceof FirebaseError) {
    switch (err.code) {
      case "auth/invalid-credential":
      case "auth/wrong-password":
      case "auth/user-not-found":
        return "Email atau kata sandi salah.";
      case "auth/too-many-requests":
        return "Terlalu banyak percobaan. Coba lagi beberapa menit lagi.";
      case "auth/network-request-failed":
        return "Tidak bisa menghubungi Firebase. Periksa koneksi internet.";
      default:
        return `Gagal masuk (${err.code}).`;
    }
  }
  return "Gagal masuk.";
}
