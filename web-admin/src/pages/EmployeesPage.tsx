import { useState } from "react";
import { useEmployees, useSettings } from "../hooks/useData";
import { hashPin, isValidPinFormat, makePinFields } from "../lib/pin";
import {
  clearPin,
  deleteEmployee,
  employeesWithPlainPin,
  saveEmployee,
} from "../lib/write";
import Dialog from "../components/Dialog";
import { hasPin, type Employee } from "../lib/types";

interface Draft {
  id: string | null;
  name: string;
  role: string;
  tolerance: string;
  active: boolean;
  pin: string;
  sudahPunyaPin: boolean;
}

const kosong: Draft = {
  id: null,
  name: "",
  role: "",
  tolerance: "",
  active: true,
  pin: "",
  sudahPunyaPin: false,
};

export default function EmployeesPage() {
  const employees = useEmployees(true);
  const settings = useSettings();
  const [draft, setDraft] = useState<Draft | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const tanpaPin = employees.filter((e) => e.active && !hasPin(e));
  const pinPolos = employeesWithPlainPin(employees);

  function edit(e: Employee) {
    setError(null);
    setDraft({
      id: e.id,
      name: e.name,
      role: e.role,
      tolerance: e.toleranceMinutes === null ? "" : String(e.toleranceMinutes),
      active: e.active,
      pin: "",
      sudahPunyaPin: hasPin(e),
    });
  }

  async function simpan() {
    if (!draft) return;
    const nama = draft.name.trim();
    if (!nama) {
      setError("Nama tidak boleh kosong.");
      return;
    }
    if (draft.pin && !isValidPinFormat(draft.pin)) {
      setError("PIN harus tepat 4 angka.");
      return;
    }

    setBusy(true);
    setError(null);
    try {
      // PIN tidak boleh sama antar karyawan: dua orang dengan PIN sama membuat
      // bukti absen tidak lagi membuktikan siapa yang datang.
      if (draft.pin) {
        const bentrok = await cariPinBentrok(draft.pin, employees, draft.id);
        if (bentrok) {
          setError(`PIN itu sudah dipakai ${bentrok.name}. Pilih angka lain.`);
          setBusy(false);
          return;
        }
      }

      const toleransi = draft.tolerance.trim();
      await saveEmployee(
        draft.id,
        {
          name: nama,
          role: draft.role.trim(),
          toleranceMinutes: toleransi === "" ? null : Number.parseInt(toleransi, 10),
          active: draft.active,
        },
        draft.pin ? await makePinFields(draft.pin) : null,
      );
      setDraft(null);
    } catch (e) {
      setError(`Gagal menyimpan: ${(e as Error).message}`);
    } finally {
      setBusy(false);
    }
  }

  async function hapusPin(e: Employee) {
    if (!confirm(`Hapus PIN ${e.name}? Dia tetap bisa absen, tapi ditandai tanpa PIN.`)) {
      return;
    }
    await clearPin(e.id);
  }

  async function hapus(e: Employee) {
    const pesan =
      `Hapus ${e.name} dari daftar karyawan?\n\n` +
      "Catatan absennya tidak ikut terhapus, tapi namanya tidak akan muncul lagi " +
      "di rekap. Kalau dia hanya berhenti bekerja, lebih baik dinonaktifkan saja " +
      "lewat tombol Ubah.";
    if (!confirm(pesan)) return;
    await deleteEmployee(e.id);
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Karyawan</h1>
          <p>
            Toleransi umum saat ini {settings.toleranceMinutes} menit. Kosongkan
            toleransi karyawan untuk mengikutinya.
          </p>
        </div>
        <button className="primary" onClick={() => setDraft({ ...kosong })}>
          Tambah karyawan
        </button>
      </div>

      {tanpaPin.length > 0 && (
        <div className="notice warn">
          PIN belum diatur untuk {tanpaPin.map((e) => e.name).join(", ")}. Mereka
          tetap bisa absen supaya shift pagi tidak macet, tapi catatannya ditandai{" "}
          <em>tanpa PIN</em>.
        </div>
      )}

      {pinPolos.length > 0 && (
        <div className="notice">
          PIN {pinPolos.map((e) => e.name).join(", ")} masih tersimpan apa adanya
          dari aplikasi versi lama. PIN-nya tetap berfungsi; begitu Anda
          mengubahnya lewat tombol <strong>Ubah</strong>, yang tersimpan berganti
          menjadi hash bergaram yang tidak bisa dibaca balik.
        </div>
      )}

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Karyawan</th>
              <th>Jabatan</th>
              <th>PIN</th>
              <th className="num">Toleransi</th>
              <th>Status</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {employees.length === 0 && (
              <tr>
                <td colSpan={6} className="center muted">
                  Belum ada karyawan.
                </td>
              </tr>
            )}
            {employees.map((e) => (
              <tr key={e.id}>
                <td>{e.name}</td>
                <td className="muted">{e.role || "—"}</td>
                <td>
                  {e.pinHash ? (
                    <span className="tag good">terpasang</span>
                  ) : e.plainPin ? (
                    <span className="tag warn">tersimpan polos</span>
                  ) : (
                    <span className="tag bad">belum diatur</span>
                  )}
                </td>
                <td className="num">
                  {e.toleranceMinutes === null ? (
                    <span className="muted">umum ({settings.toleranceMinutes}m)</span>
                  ) : (
                    `${e.toleranceMinutes}m`
                  )}
                </td>
                <td>
                  {e.active ? (
                    <span className="tag good">aktif</span>
                  ) : (
                    <span className="tag">nonaktif</span>
                  )}
                </td>
                <td>
                  <div className="row">
                    <button className="small" onClick={() => edit(e)}>
                      Ubah
                    </button>
                    {hasPin(e) && (
                      <button className="small" onClick={() => void hapusPin(e)}>
                        Hapus PIN
                      </button>
                    )}
                    <button className="small danger" onClick={() => void hapus(e)}>
                      Hapus
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {draft && (
        <Dialog
          title={draft.id ? "Ubah karyawan" : "Tambah karyawan"}
          onClose={() => setDraft(null)}
          footer={
            <>
              <button onClick={() => setDraft(null)}>Batal</button>
              <button className="primary" disabled={busy} onClick={() => void simpan()}>
                {busy ? "Menyimpan…" : "Simpan"}
              </button>
            </>
          }
        >
          <div className="grid cols-2">
            <label className="field">
              <span>Nama</span>
              <input
                value={draft.name}
                onChange={(e) => setDraft({ ...draft, name: e.target.value })}
                autoFocus
              />
            </label>
            <label className="field">
              <span>Jabatan</span>
              <input
                value={draft.role}
                placeholder="Barista"
                onChange={(e) => setDraft({ ...draft, role: e.target.value })}
              />
            </label>
          </div>

          <label className="field">
            <span>
              PIN 4 angka{" "}
              {draft.sudahPunyaPin && (
                <em>— kosongkan untuk membiarkan PIN yang sekarang</em>
              )}
            </span>
            <input
              inputMode="numeric"
              maxLength={4}
              value={draft.pin}
              placeholder={draft.sudahPunyaPin ? "••••" : "mis. 4820"}
              onChange={(e) =>
                setDraft({ ...draft, pin: e.target.value.replace(/\D/g, "").slice(0, 4) })
              }
            />
          </label>

          <label className="field">
            <span>Toleransi telat khusus (menit) — kosongkan untuk ikut pengaturan umum</span>
            <input
              inputMode="numeric"
              value={draft.tolerance}
              placeholder={`${settings.toleranceMinutes}`}
              onChange={(e) =>
                setDraft({ ...draft, tolerance: e.target.value.replace(/\D/g, "") })
              }
            />
          </label>

          <label className="check">
            <input
              type="checkbox"
              checked={draft.active}
              onChange={(e) => setDraft({ ...draft, active: e.target.checked })}
            />
            <span>Aktif — muncul di layar absen tablet</span>
          </label>

          {error && <div className="notice bad">{error}</div>}
        </Dialog>
      )}
    </>
  );
}

/**
 * Mencari karyawan lain yang PIN-nya sama.
 *
 * Dua bentuk harus diperiksa sekaligus. PIN lama tersimpan apa adanya sehingga
 * bisa dibandingkan langsung; PIN baru tersimpan sebagai hash bergaram,
 * sehingga PIN calon harus di-hash ulang dengan garam milik tiap karyawan.
 */
async function cariPinBentrok(
  pin: string,
  employees: Employee[],
  kecualiId: string | null,
): Promise<Employee | null> {
  for (const e of employees) {
    if (e.id === kecualiId) continue;
    if (e.plainPin && e.plainPin === pin) return e;
    if (e.pinHash && e.pinSalt) {
      const calon = await hashPin(pin, e.pinSalt, e.pinIterations || undefined);
      if (calon === e.pinHash) return e;
    }
  }
  return null;
}
