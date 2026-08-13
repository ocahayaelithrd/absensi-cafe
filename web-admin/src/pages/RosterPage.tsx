import { useMemo, useState } from "react";
import { deleteField, doc, getDoc, setDoc } from "firebase/firestore";
import { db } from "../firebase";
import { useEmployees, useRoster, useShifts } from "../hooks/useData";
import { addDays, formatDate } from "../lib/rules";
import { awalMinggu, labelHari, tanggalPanjang } from "../lib/format";
import Dialog from "../components/Dialog";
import { ROSTER_OFF, type Shift } from "../lib/types";

/** Sasaran pengisian: satu sel, satu baris karyawan, atau satu kolom hari. */
type Target =
  | { mode: "cell"; employeeId: string; date: string; nama: string }
  | { mode: "row"; employeeId: string; nama: string }
  | { mode: "col"; date: string };

export default function RosterPage() {
  const employees = useEmployees();
  const shifts = useShifts();

  const [senin, setSenin] = useState(() => awalMinggu(formatDate(new Date())));
  const hari = useMemo(() => Array.from({ length: 7 }, (_, i) => addDays(senin, i)), [senin]);
  const roster = useRoster(hari);

  const [target, setTarget] = useState<Target | null>(null);
  const [pesan, setPesan] = useState<string | null>(null);

  async function isi(shiftId: string | null) {
    if (!target) return;
    const nilai = shiftId === null ? deleteField() : shiftId;

    // Penugasan ditulis sebagai peta bersarang dengan merge, bukan jalur
    // bertitik: titik baru berarti "masuk ke dalam peta" pada updateDoc, dan
    // pada setDoc justru membuat field bernama "assign.xxx" secara harfiah.
    const tulis = async (tanggal: string, employeeIds: string[]) => {
      const assign: Record<string, unknown> = {};
      for (const id of employeeIds) assign[id] = nilai;
      await setDoc(doc(db, "roster", tanggal), { date: tanggal, assign }, { merge: true });
    };

    if (target.mode === "cell") await tulis(target.date, [target.employeeId]);
    else if (target.mode === "col") await tulis(target.date, employees.map((e) => e.id));
    else for (const tanggal of hari) await tulis(tanggal, [target.employeeId]);

    setTarget(null);
  }

  /**
   * Menyalin roster minggu lalu ke minggu yang sedang dibuka.
   *
   * Sel yang sudah terisi minggu ini tidak ditimpa: pola shift cafe berulang,
   * tapi perubahan yang sudah disepakati dengan karyawan lebih penting
   * daripada polanya.
   */
  async function salinMingguLalu() {
    setPesan(null);
    let ditulis = 0;
    for (let i = 0; i < 7; i++) {
      const sumber = addDays(senin, i - 7);
      const tujuan = addDays(senin, i);
      const snap = await getDoc(doc(db, "roster", sumber));
      if (!snap.exists()) continue;
      const assign = (snap.data().assign ?? {}) as Record<string, string>;
      const sudah = roster[tujuan]?.assign ?? {};

      const patch: Record<string, string> = {};
      for (const [id, shiftId] of Object.entries(assign)) {
        if (sudah[id] !== undefined) continue;
        patch[id] = shiftId;
        ditulis++;
      }
      if (Object.keys(patch).length === 0) continue;
      await setDoc(
        doc(db, "roster", tujuan),
        { date: tujuan, assign: patch },
        { merge: true },
      );
    }
    setPesan(
      ditulis === 0
        ? "Tidak ada yang disalin — minggu lalu kosong atau semua sel minggu ini sudah terisi."
        : `${ditulis} sel disalin dari minggu lalu. Sel yang sudah terisi dibiarkan.`,
    );
  }

  const judulTarget = (t: Target) => {
    if (t.mode === "cell") return `${t.nama} — ${tanggalPanjang(t.date)}`;
    if (t.mode === "row") return `${t.nama} — seminggu penuh`;
    return `Semua karyawan — ${tanggalPanjang(t.date)}`;
  };

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Jadwal</h1>
          <p>
            Ketuk satu sel untuk mengubah sehari, nama karyawan untuk seminggu,
            atau kepala kolom untuk semua karyawan pada hari itu.
          </p>
        </div>
        <div className="row">
          <button onClick={() => setSenin(addDays(senin, -7))}>← Minggu lalu</button>
          <button onClick={() => setSenin(awalMinggu(formatDate(new Date())))}>
            Minggu ini
          </button>
          <button onClick={() => setSenin(addDays(senin, 7))}>Minggu depan →</button>
          <button className="primary" onClick={() => void salinMingguLalu()}>
            Salin Minggu Lalu
          </button>
        </div>
      </div>

      <p className="muted">
        {tanggalPanjang(senin)} – {tanggalPanjang(addDays(senin, 6))}
      </p>

      {pesan && <div className="notice">{pesan}</div>}

      {shifts.length === 0 && (
        <div className="notice warn">
          Belum ada pola shift. Buat dulu di halaman <strong>Pola Shift</strong>,
          baru jadwal bisa diisi.
        </div>
      )}

      <div className="table-wrap">
        <table className="roster">
          <thead>
            <tr>
              <th>Karyawan</th>
              {hari.map((tanggal) => (
                <th
                  key={tanggal}
                  className="day"
                  onClick={() => setTarget({ mode: "col", date: tanggal })}
                  title="Atur semua karyawan pada hari ini"
                >
                  {labelHari(tanggal).slice(0, 3)}
                  <br />
                  <span className="muted small">{tanggal.slice(8)}</span>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {employees.length === 0 && (
              <tr>
                <td colSpan={8} className="center muted">
                  Belum ada karyawan aktif.
                </td>
              </tr>
            )}
            {employees.map((e) => (
              <tr key={e.id}>
                <td
                  className="name"
                  onClick={() => setTarget({ mode: "row", employeeId: e.id, nama: e.name })}
                  title="Isi seminggu untuk karyawan ini"
                >
                  {e.name}
                </td>
                {hari.map((tanggal) => {
                  const nilai = roster[tanggal]?.assign?.[e.id];
                  const shift = shifts.find((s) => s.id === nilai);
                  const kelas =
                    nilai === ROSTER_OFF ? "cell off" : shift ? "cell" : "cell empty";
                  return (
                    <td
                      key={tanggal}
                      className={kelas}
                      onClick={() =>
                        setTarget({
                          mode: "cell",
                          employeeId: e.id,
                          date: tanggal,
                          nama: e.name,
                        })
                      }
                      title={shift ? `${shift.name} ${shift.start}–${shift.end}` : undefined}
                    >
                      {nilai === ROSTER_OFF ? "Libur" : shift ? shift.code : "—"}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <p className="small muted" style={{ marginTop: 12 }}>
        Sel kosong berarti belum dijadwalkan. Karyawan tetap bisa absen di hari
        itu — catatannya tersimpan dan ditandai <em>di luar jadwal</em>, tapi
        telat dan lembur tidak dihitung karena tidak ada jam acuan.
      </p>

      {target && (
        <Dialog
          title={judulTarget(target)}
          onClose={() => setTarget(null)}
          footer={<button onClick={() => setTarget(null)}>Tutup</button>}
        >
          <div className="grid cols-3">
            {shifts.map((s: Shift) => (
              <button key={s.id} onClick={() => void isi(s.id)}>
                {s.code} · {s.name}
                <br />
                <span className="small muted">
                  {s.start}–{s.end}
                </span>
              </button>
            ))}
            <button onClick={() => void isi(ROSTER_OFF)}>Libur</button>
            <button onClick={() => void isi(null)}>Kosongkan</button>
          </div>
        </Dialog>
      )}
    </>
  );
}
