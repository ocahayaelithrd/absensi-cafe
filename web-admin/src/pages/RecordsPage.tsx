import { useMemo, useState } from "react";
import {
  useEmployeeNames,
  useEmployees,
  useRecords,
  useSettings,
  useShifts,
} from "../hooks/useData";
import { deleteRecord, saveRecordCorrection } from "../lib/write";
import { fineFor, formatDate, recompute } from "../lib/rules";
import { durasi, jam, meter, rupiah, tanggalPendek } from "../lib/format";
import Dialog from "../components/Dialog";
import PunchPhoto from "../components/PunchPhoto";
import type { AttendanceRecord, Employee, Punch, Settings, Shift } from "../lib/types";

export default function RecordsPage() {
  const settings = useSettings();
  const employees = useEmployees(true);
  const names = useEmployeeNames(employees);
  const shifts = useShifts();

  const hariIni = formatDate(new Date());
  const [from, setFrom] = useState(hariIni);
  const [to, setTo] = useState(hariIni);
  const [filterKaryawan, setFilterKaryawan] = useState("");

  const records = useRecords(from, to);
  const [dibuka, setDibuka] = useState<AttendanceRecord | null>(null);

  const tampil = useMemo(() => {
    const list = filterKaryawan
      ? records.filter((r) => r.employeeId === filterKaryawan)
      : records;
    return [...list].sort(
      (a, b) =>
        b.date.localeCompare(a.date) ||
        (b.checkIn?.at.getTime() ?? 0) - (a.checkIn?.at.getTime() ?? 0),
    );
  }, [records, filterKaryawan]);

  const nama = (id: string) => names.get(id) ?? "(karyawan dihapus)";

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Absensi</h1>
          <p>Ketuk satu baris untuk melihat foto, lokasi, dan mengoreksi jamnya.</p>
        </div>
      </div>

      <div className="card">
        <div className="grid cols-3">
          <label className="field">
            <span>Dari tanggal</span>
            <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          </label>
          <label className="field">
            <span>Sampai tanggal</span>
            <input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
          </label>
          <label className="field">
            <span>Karyawan</span>
            <select
              value={filterKaryawan}
              onChange={(e) => setFilterKaryawan(e.target.value)}
            >
              <option value="">Semua</option>
              {employees.map((e) => (
                <option key={e.id} value={e.id}>
                  {e.name}
                </option>
              ))}
            </select>
          </label>
        </div>
      </div>

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Tanggal</th>
              <th>Karyawan</th>
              <th>Shift</th>
              <th>Masuk</th>
              <th>Pulang</th>
              <th className="num">Telat</th>
              {settings.fineEnabled && <th className="num">Denda</th>}
              <th className="num">Kerja</th>
              <th className="num">Lembur</th>
              {settings.faceMode !== "off" && <th className="num">Wajah</th>}
              <th>Tanda</th>
            </tr>
          </thead>
          <tbody>
            {tampil.length === 0 && (
              <tr>
                <td
                  colSpan={settings.faceMode !== "off" ? 11 : 10}
                  className="center muted"
                >
                  Tidak ada absen pada rentang ini.
                </td>
              </tr>
            )}
            {tampil.map((r) => {
              const shift = shifts.find((s) => s.id === r.shiftId);
              return (
                <tr key={r.id} onClick={() => setDibuka(r)} style={{ cursor: "pointer" }}>
                  <td>{tanggalPendek(r.date)}</td>
                  <td>{nama(r.employeeId)}</td>
                  <td>{shift ? shift.name : <span className="muted">—</span>}</td>
                  <td>{jam(r.checkIn?.at)}</td>
                  <td>
                    {r.checkOut ? (
                      jam(r.checkOut.at)
                    ) : (
                      <span className="tag warn">belum pulang</span>
                    )}
                  </td>
                  <td className="num">
                    {r.lateMinutes > 0 ? (
                      <span className="tag bad">{durasi(r.lateMinutes)}</span>
                    ) : (
                      "—"
                    )}
                  </td>
                  {settings.fineEnabled && (
                    <td className="num">
                      {r.lateMinutes > 0 ? rupiah(fineFor(r.lateMinutes, settings)) : "—"}
                    </td>
                  )}
                  <td className="num">{r.workMinutes > 0 ? durasi(r.workMinutes) : "—"}</td>
                  <td className="num">
                    {r.overtimeMinutes > 0 ? durasi(r.overtimeMinutes) : "—"}
                  </td>
                  {settings.faceMode !== "off" && (
                    <td className="num">
                      <Kemiripan record={r} threshold={settings.faceThreshold} />
                    </td>
                  )}
                  <td>
                    <Tanda record={r} />
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {dibuka && (
        <DetailDialog
          record={dibuka}
          employeeName={nama(dibuka.employeeId)}
          employee={employees.find((e) => e.id === dibuka.employeeId)}
          onClose={() => setDibuka(null)}
          shifts={shifts}
          settings={settings}
        />
      )}
    </>
  );
}

export function Tanda({ record }: { record: AttendanceRecord }) {
  const tanda: { teks: string; kelas: string }[] = [];
  if (record.offSchedule) tanda.push({ teks: "di luar jadwal", kelas: "warn" });
  if (record.checkIn?.pinBy === "kosong" || record.checkOut?.pinBy === "kosong") {
    tanda.push({ teks: "tanpa PIN", kelas: "bad" });
  }
  if (record.checkIn?.pinBy === "admin" || record.checkOut?.pinBy === "admin") {
    tanda.push({ teks: "izin penyelia", kelas: "warn" });
  }
  if (record.checkIn?.outsideGeofence || record.checkOut?.outsideGeofence) {
    tanda.push({ teks: "di luar area", kelas: "bad" });
  }
  if (record.checkIn?.faceFlag || record.checkOut?.faceFlag) {
    tanda.push({ teks: "wajah tidak cocok", kelas: "bad" });
  }
  if (record.earlyLeaveMinutes > 0) {
    tanda.push({ teks: `pulang cepat ${durasi(record.earlyLeaveMinutes)}`, kelas: "warn" });
  }
  if (record.edited) tanda.push({ teks: "dikoreksi", kelas: "" });

  if (tanda.length === 0) return <span className="muted">—</span>;
  return (
    <>
      {tanda.map((t) => (
        <span key={t.teks} className={`tag ${t.kelas}`}>
          {t.teks}
        </span>
      ))}
    </>
  );
}

/**
 * Kemiripan wajah kedua sisi absen, angka yang dipakai admin menyetel ambang.
 *
 * Yang ditampilkan yang paling rendah antara masuk dan pulang: itu yang
 * menentukan apakah absen tertahan, dan yang perlu diperhatikan saat menyetel.
 */
function Kemiripan({
  record,
  threshold,
}: {
  record: AttendanceRecord;
  threshold: number;
}) {
  const skor = [record.checkIn?.faceScore, record.checkOut?.faceScore].filter(
    (v): v is number => typeof v === "number",
  );
  if (skor.length === 0) return <span className="muted">—</span>;
  const terendah = Math.min(...skor);
  return (
    <span className={`tag ${terendah >= threshold ? "good" : "bad"}`}>{terendah}%</span>
  );
}

interface DetailProps {
  record: AttendanceRecord;
  employeeName: string;
  employee: Employee | undefined;
  shifts: Shift[];
  settings: Settings;
  onClose: () => void;
}

/**
 * Rincian dan koreksi satu catatan.
 *
 * Yang bisa diubah hanya jam, shift, dan catatan. Telat, lembur, dan jam kerja
 * selalu dihitung ulang dari jam yang tersimpan — tidak pernah diketik tangan —
 * supaya rekap tidak bisa berbeda dari bukti yang ada.
 */
function DetailDialog({
  record,
  employeeName,
  employee,
  shifts,
  settings,
  onClose,
}: DetailProps) {
  const [masuk, setMasuk] = useState(toLocalInput(record.checkIn?.at));
  const [pulang, setPulang] = useState(toLocalInput(record.checkOut?.at));
  const [shiftId, setShiftId] = useState(record.shiftId);
  const [note, setNote] = useState(record.note);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function simpan() {
    setBusy(true);
    setError(null);
    try {
      if (!masuk) {
        setError("Jam masuk tidak boleh kosong.");
        setBusy(false);
        return;
      }
      const waktuMasuk = new Date(masuk);
      const waktuPulang = pulang ? new Date(pulang) : null;
      if (waktuPulang && waktuPulang < waktuMasuk) {
        setError("Jam pulang lebih awal dari jam masuk.");
        setBusy(false);
        return;
      }

      const shift = shifts.find((s) => s.id === shiftId) ?? null;

      /* Sisi yang dibuat admin dari nol ditandai "admin": jamnya memang tidak
         berasal dari karyawan yang berdiri di depan kamera. */
      const sisiPulang: Punch | null = waktuPulang
        ? record.checkOut
          ? { ...record.checkOut, at: waktuPulang }
          : {
              at: waktuPulang,
              lat: null,
              lon: null,
              accuracyMeters: null,
              distanceMeters: null,
              outsideGeofence: false,
              pinBy: "admin",
              faceScore: null,
              faceFlag: false,
              photo: "",
            }
        : null;

      const diperbarui = recompute(
        {
          ...record,
          shiftId: shift?.id ?? "",
          offSchedule: shift === null,
          checkIn: record.checkIn ? { ...record.checkIn, at: waktuMasuk } : null,
          checkOut: sisiPulang,
          note,
        },
        shift,
        employee,
        settings,
      );

      await saveRecordCorrection(diperbarui);
      onClose();
    } catch (e) {
      setError(`Gagal menyimpan: ${(e as Error).message}`);
    } finally {
      setBusy(false);
    }
  }

  async function hapus() {
    if (
      !confirm(
        `Hapus catatan absen ${employeeName} tanggal ${record.date}?\n\n` +
          "Foto buktinya ikut terhapus karena tersimpan di dalam catatan itu.",
      )
    ) {
      return;
    }
    await deleteRecord(record.id);
    onClose();
  }

  return (
    <Dialog
      title={`${employeeName} — ${record.date}`}
      onClose={onClose}
      footer={
        <>
          <button className="danger" onClick={() => void hapus()}>
            Hapus catatan
          </button>
          <button onClick={onClose}>Tutup</button>
          <button className="primary" disabled={busy} onClick={() => void simpan()}>
            {busy ? "Menyimpan…" : "Simpan koreksi"}
          </button>
        </>
      }
    >
      <div className="grid cols-2">
        <label className="field">
          <span>Jam masuk</span>
          <input
            type="datetime-local"
            value={masuk}
            onChange={(e) => setMasuk(e.target.value)}
          />
        </label>
        <label className="field">
          <span>Jam pulang — kosongkan bila memang belum pulang</span>
          <input
            type="datetime-local"
            value={pulang}
            onChange={(e) => setPulang(e.target.value)}
          />
        </label>
      </div>

      <label className="field">
        <span>Shift</span>
        <select value={shiftId} onChange={(e) => setShiftId(e.target.value)}>
          <option value="">Tanpa shift (di luar jadwal)</option>
          {shifts.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name} ({s.start}–{s.end})
            </option>
          ))}
        </select>
      </label>

      <label className="field">
        <span>Catatan</span>
        <textarea rows={2} value={note} onChange={(e) => setNote(e.target.value)} />
      </label>

      {error && <div className="notice bad">{error}</div>}

      <div className="grid cols-2">
        <div>
          <h3>Masuk</h3>
          {record.checkIn ? (
            <>
              <PunchPhoto photo={record.checkIn.photo} />
              <SkorWajah punch={record.checkIn} threshold={settings.faceThreshold} />
              <Lokasi punch={record.checkIn} />
            </>
          ) : (
            <p className="muted small">tidak ada</p>
          )}
        </div>
        <div>
          <h3>Pulang</h3>
          {record.checkOut ? (
            <>
              <PunchPhoto photo={record.checkOut.photo} />
              <SkorWajah punch={record.checkOut} threshold={settings.faceThreshold} />
              <Lokasi punch={record.checkOut} />
            </>
          ) : (
            <p className="muted small">belum absen pulang</p>
          )}
        </div>
      </div>
    </Dialog>
  );
}

function SkorWajah({ punch, threshold }: { punch: Punch; threshold: number }) {
  if (punch.faceScore === null) {
    return (
      <p className="small muted">
        {punch.faceFlag ? "Wajah tidak terdeteksi." : "Wajah tidak diperiksa."}
      </p>
    );
  }
  return (
    <p className="small muted">
      Kemiripan wajah{" "}
      <span className={`tag ${punch.faceScore >= threshold ? "good" : "bad"}`}>
        {punch.faceScore}%
      </span>{" "}
      (ambang {threshold}%)
    </p>
  );
}

function Lokasi({ punch }: { punch: Punch }) {
  if (punch.lat === null || punch.lon === null) {
    return <p className="small muted">Lokasi tidak tercatat.</p>;
  }
  return (
    <p className="small muted">
      <a
        href={`https://www.google.com/maps?q=${punch.lat},${punch.lon}`}
        target="_blank"
        rel="noreferrer"
      >
        {punch.lat.toFixed(6)}, {punch.lon.toFixed(6)}
      </a>
      {punch.accuracyMeters !== null && ` · ±${Math.round(punch.accuracyMeters)} m`}
      {punch.distanceMeters !== null && ` · ${meter(punch.distanceMeters)} dari cafe`}
    </p>
  );
}

/** Date -> nilai untuk <input type="datetime-local"> dalam zona lokal. */
function toLocalInput(date: Date | undefined | null): string {
  if (!date) return "";
  const p = (n: number) => String(n).padStart(2, "0");
  return (
    `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}` +
    `T${p(date.getHours())}:${p(date.getMinutes())}`
  );
}
