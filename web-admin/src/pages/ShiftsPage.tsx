import { useState } from "react";
import { useShifts } from "../hooks/useData";
import { deleteShift, saveShift } from "../lib/write";
import { crossesMidnight, minutesOf } from "../lib/rules";
import { durasi } from "../lib/format";
import Dialog from "../components/Dialog";
import type { Shift } from "../lib/types";

interface Draft {
  id: string | null;
  code: string;
  name: string;
  start: string;
  end: string;
}

const kosong: Draft = { id: null, code: "", name: "", start: "07:00", end: "15:00" };

export default function ShiftsPage() {
  const shifts = useShifts();
  const [draft, setDraft] = useState<Draft | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function simpan() {
    if (!draft) return;
    const nama = draft.name.trim();
    const kode = draft.code.trim().toUpperCase();
    if (!nama) {
      setError("Nama shift tidak boleh kosong.");
      return;
    }
    if (!kode) {
      setError("Kode shift tidak boleh kosong — kode itu yang tampil di grid jadwal.");
      return;
    }
    if (shifts.some((s) => s.code.toUpperCase() === kode && s.id !== draft.id)) {
      setError(`Kode ${kode} sudah dipakai shift lain.`);
      return;
    }
    if (!/^\d{2}:\d{2}$/.test(draft.start) || !/^\d{2}:\d{2}$/.test(draft.end)) {
      setError("Jam harus berformat HH:mm.");
      return;
    }

    await saveShift(draft.id, {
      code: kode,
      name: nama,
      start: draft.start,
      end: draft.end,
    });
    setDraft(null);
    setError(null);
  }

  async function hapus(s: Shift) {
    const pesan =
      `Hapus shift ${s.name}?\n\n` +
      "Sel jadwal yang memakai shift ini akan tampil kosong, dan absen lama " +
      "yang menunjuk ke shift ini akan berubah menjadi di luar jadwal saat " +
      "dihitung ulang.";
    if (!confirm(pesan)) return;
    await deleteShift(s.id);
  }

  async function isiBawaan() {
    const bawaan = [
      { code: "P", name: "Pagi", start: "07:00", end: "15:00" },
      { code: "S", name: "Sore", start: "15:00", end: "23:00" },
      { code: "M", name: "Malam", start: "23:00", end: "07:00" },
    ];
    for (const s of bawaan) await saveShift(null, s);
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Pola shift</h1>
          <p>
            Jam selesai yang lebih awal dari jam mulai berarti shift lewat tengah
            malam.
          </p>
        </div>
        <div className="row">
          {shifts.length === 0 && (
            <button onClick={() => void isiBawaan()}>Isi Pagi/Sore/Malam</button>
          )}
          <button className="primary" onClick={() => setDraft({ ...kosong })}>
            Tambah shift
          </button>
        </div>
      </div>

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Kode</th>
              <th>Nama</th>
              <th>Mulai</th>
              <th>Selesai</th>
              <th className="num">Durasi</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {shifts.length === 0 && (
              <tr>
                <td colSpan={6} className="center muted">
                  Belum ada pola shift.
                </td>
              </tr>
            )}
            {shifts.map((s) => {
              const panjang = crossesMidnight(s)
                ? 24 * 60 - minutesOf(s.start) + minutesOf(s.end)
                : minutesOf(s.end) - minutesOf(s.start);
              return (
                <tr key={s.id}>
                  <td>
                    <span className="tag">{s.code}</span>
                  </td>
                  <td>{s.name}</td>
                  <td>{s.start}</td>
                  <td>
                    {s.end}
                    {crossesMidnight(s) && <span className="tag warn">+1 hari</span>}
                  </td>
                  <td className="num">{durasi(panjang)}</td>
                  <td>
                    <div className="row">
                      <button
                        className="small"
                        onClick={() =>
                          setDraft({
                            id: s.id,
                            code: s.code,
                            name: s.name,
                            start: s.start,
                            end: s.end,
                          })
                        }
                      >
                        Ubah
                      </button>
                      <button className="small danger" onClick={() => void hapus(s)}>
                        Hapus
                      </button>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {draft && (
        <Dialog
          title={draft.id ? "Ubah shift" : "Tambah shift"}
          onClose={() => setDraft(null)}
          footer={
            <>
              <button onClick={() => setDraft(null)}>Batal</button>
              <button className="primary" onClick={() => void simpan()}>
                Simpan
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
                placeholder="Pagi"
                autoFocus
              />
            </label>
            <label className="field">
              <span>Kode (tampil di grid jadwal)</span>
              <input
                value={draft.code}
                maxLength={3}
                onChange={(e) => setDraft({ ...draft, code: e.target.value })}
                placeholder="P"
              />
            </label>
            <label className="field">
              <span>Jam mulai</span>
              <input
                type="time"
                value={draft.start}
                onChange={(e) => setDraft({ ...draft, start: e.target.value })}
              />
            </label>
            <label className="field">
              <span>Jam selesai</span>
              <input
                type="time"
                value={draft.end}
                onChange={(e) => setDraft({ ...draft, end: e.target.value })}
              />
            </label>
          </div>

          {error && <div className="notice bad">{error}</div>}
        </Dialog>
      )}
    </>
  );
}
