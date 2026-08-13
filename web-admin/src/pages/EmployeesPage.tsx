import { useState } from "react";
import { addDoc, collection, deleteDoc, doc, updateDoc } from "firebase/firestore";
import { db } from "../firebase";
import { useEmployees, useSettings } from "../hooks/useData";
import { isValidPinFormat, makePinFields, hashPin } from "../lib/pin";
import Dialog from "../components/Dialog";
import type { Employee } from "../lib/types";

interface Draft {
  id: string | null;
  name: string;
  tolerance: string;
  active: boolean;
  pin: string;
  hasPin: boolean;
}

const kosong: Draft = {
  id: null,
  name: "",
  tolerance: "",
  active: true,
  pin: "",
  hasPin: false,
};

export default function EmployeesPage() {
  const employees = useEmployees(true);
  const settings = useSettings();
  const [draft, setDraft] = useState<Draft | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const tanpaPin = employees.filter((e) => e.active && !e.pinHash);

  function edit(e: Employee) {
    setError(null);
    setDraft({
      id: e.id,
      name: e.name,
      tolerance: e.toleranceMinutes === null ? "" : String(e.toleranceMinutes),
      active: e.active,
      pin: "",
      hasPin: Boolean(e.pinHash),
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
      const data: Record<string, unknown> = {
        name: nama,
        active: draft.active,
        toleranceMinutes: toleransi === "" ? null : Number.parseInt(toleransi, 10),
      };
      if (draft.pin) Object.assign(data, await makePinFields(draft.pin));

      if (draft.id) {
        await updateDoc(doc(db, "employees", draft.id), data);
      } else {
        await addDoc(collection(db, "employees"), {
          pinHash: "",
          pinSalt: "",
          pinIterations: 0,
          ...data,
        });
      }
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
    await updateDoc(doc(db, "employees", e.id), {
      pinHash: "",
      pinSalt: "",
      pinIterations: 0,
    });
  }

  async function hapus(e: Employee) {
    const pesan =
      `Hapus ${e.name} dari daftar karyawan?\n\n` +
      "Catatan absennya tidak ikut terhapus dan tetap muncul di rekap bulan " +
      "berjalan. Kalau dia hanya berhenti bekerja, lebih baik dinonaktifkan " +
      "saja lewat tombol Ubah.";
    if (!confirm(pesan)) return;
    await deleteDoc(doc(db, "employees", e.id));
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

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Karyawan</th>
              <th>PIN</th>
              <th className="num">Toleransi</th>
              <th>Status</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {employees.length === 0 && (
              <tr>
                <td colSpan={5} className="center muted">
                  Belum ada karyawan.
                </td>
              </tr>
            )}
            {employees.map((e) => (
              <tr key={e.id}>
                <td>{e.name}</td>
                <td>
                  {e.pinHash ? (
                    <span className="tag good">terpasang</span>
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
                    {e.pinHash && (
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
          <label className="field">
            <span>Nama</span>
            <input
              value={draft.name}
              onChange={(e) => setDraft({ ...draft, name: e.target.value })}
              autoFocus
            />
          </label>

          <label className="field">
            <span>
              PIN 4 angka{" "}
              {draft.hasPin && <em>— kosongkan untuk membiarkan PIN yang sekarang</em>}
            </span>
            <input
              inputMode="numeric"
              maxLength={4}
              value={draft.pin}
              placeholder={draft.hasPin ? "••••" : "mis. 4820"}
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
 * PIN disimpan sebagai hash bergaram, jadi tidak bisa dibandingkan langsung:
 * setiap karyawan punya garam sendiri, dan PIN calon harus di-hash ulang
 * dengan garam masing-masing untuk diperiksa.
 */
async function cariPinBentrok(
  pin: string,
  employees: Employee[],
  kecualiId: string | null,
): Promise<Employee | null> {
  for (const e of employees) {
    if (e.id === kecualiId || !e.pinHash || !e.pinSalt) continue;
    const calon = await hashPin(pin, e.pinSalt, e.pinIterations || undefined);
    if (calon === e.pinHash) return e;
  }
  return null;
}
