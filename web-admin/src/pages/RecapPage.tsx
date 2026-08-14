import { useMemo, useState } from "react";
import {
  useEmployees,
  useRecords,
  useRosterRange,
  useSettings,
  useShifts,
} from "../hooks/useData";
import { buildRecap, totalRow } from "../lib/recap";
import { exportWorkbook } from "../lib/excel";
import { durasi, hariDalamBulan, jamDesimal, namaBulan, rupiah } from "../lib/format";

export default function RecapPage() {
  const sekarang = new Date();
  const [tahun, setTahun] = useState(sekarang.getFullYear());
  const [bulan, setBulan] = useState(sekarang.getMonth() + 1);
  const [sibuk, setSibuk] = useState(false);

  const dates = useMemo(() => hariDalamBulan(tahun, bulan), [tahun, bulan]);
  const from = dates[0]!;
  const to = dates[dates.length - 1]!;

  const settings = useSettings();
  const employees = useEmployees(true);
  const shifts = useShifts();
  const records = useRecords(from, to);
  const roster = useRosterRange(from, to);

  const rows = useMemo(
    () => buildRecap(dates, employees, records, roster, settings),
    [dates, employees, records, roster, settings],
  );
  const total = useMemo(() => totalRow(rows), [rows]);

  async function ekspor() {
    setSibuk(true);
    try {
      await exportWorkbook({
        dates,
        from,
        to,
        employees,
        shifts,
        records,
        roster,
        settings,
        periodLabel: `${namaBulan(bulan)} ${tahun}`,
      });
    } finally {
      setSibuk(false);
    }
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Rekap &amp; Ekspor</h1>
          <p>
            {namaBulan(bulan)} {tahun} · {records.length} catatan absen
          </p>
        </div>
        <div className="row">
          <select value={bulan} onChange={(e) => setBulan(Number(e.target.value))}>
            {Array.from({ length: 12 }, (_, i) => (
              <option key={i + 1} value={i + 1}>
                {namaBulan(i + 1)}
              </option>
            ))}
          </select>
          <input
            style={{ width: 100 }}
            inputMode="numeric"
            value={tahun}
            onChange={(e) => setTahun(Number(e.target.value.replace(/\D/g, "")) || tahun)}
          />
          <button className="primary" disabled={sibuk} onClick={() => void ekspor()}>
            {sibuk ? "Menyusun…" : "Ekspor Excel"}
          </button>
        </div>
      </div>

      <div className="grid cols-4" style={{ marginBottom: 18 }}>
        <Kartu label="Hadir" nilai={String(total.present)} />
        <Kartu label="Alpa" nilai={String(total.absent)} />
        <Kartu label="Kali telat" nilai={String(total.lateCount)} />
        {settings.fineEnabled && <Kartu label="Total denda" nilai={rupiah(total.fine)} />}
      </div>

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Karyawan</th>
              <th className="num">Dijadwalkan</th>
              <th className="num">Hadir</th>
              <th className="num">Alpa</th>
              <th className="num">Belum pulang</th>
              <th className="num">Kali telat</th>
              <th className="num">Total telat</th>
              {settings.fineEnabled && <th className="num">Denda</th>}
              <th className="num">Pulang cepat</th>
              <th className="num">Lembur</th>
              <th className="num">Jam kerja</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 && (
              <tr>
                <td colSpan={11} className="center muted">
                  Belum ada karyawan.
                </td>
              </tr>
            )}
            {rows.map((r) => (
              <tr key={r.employeeId}>
                <td>{r.name}</td>
                <td className="num">{r.scheduled}</td>
                <td className="num">{r.present}</td>
                <td className="num">
                  {r.absent > 0 ? <span className="tag bad">{r.absent}</span> : "—"}
                </td>
                <td className="num">
                  {r.incomplete > 0 ? <span className="tag warn">{r.incomplete}</span> : "—"}
                </td>
                <td className="num">{r.lateCount || "—"}</td>
                <td className="num">{r.lateMinutes ? durasi(r.lateMinutes) : "—"}</td>
                {settings.fineEnabled && (
                  <td className="num">{r.fine ? rupiah(r.fine) : "—"}</td>
                )}
                <td className="num">
                  {r.earlyLeaveMinutes ? durasi(r.earlyLeaveMinutes) : "—"}
                </td>
                <td className="num">
                  {r.overtimeMinutes ? durasi(r.overtimeMinutes) : "—"}
                </td>
                <td className="num">{jamDesimal(r.workMinutes)}</td>
              </tr>
            ))}
            {rows.length > 0 && (
              <tr style={{ fontWeight: 700 }}>
                <td>TOTAL</td>
                <td className="num">{total.scheduled}</td>
                <td className="num">{total.present}</td>
                <td className="num">{total.absent}</td>
                <td className="num">{total.incomplete}</td>
                <td className="num">{total.lateCount}</td>
                <td className="num">{durasi(total.lateMinutes)}</td>
                {settings.fineEnabled && <td className="num">{rupiah(total.fine)}</td>}
                <td className="num">{durasi(total.earlyLeaveMinutes)}</td>
                <td className="num">{durasi(total.overtimeMinutes)}</td>
                <td className="num">{jamDesimal(total.workMinutes)}</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <p className="small muted" style={{ marginTop: 12 }}>
        Alpa dihitung dari jadwal: hari yang dijadwalkan tapi tidak ada catatan
        absen sama sekali. Hari libur dan hari yang belum dijadwalkan tidak
        pernah dihitung alpa. Ekspor menghasilkan satu berkas berisi tiga lembar:
        Rekap, Detail, dan Info.
      </p>
    </>
  );
}

function Kartu({ label, nilai }: { label: string; nilai: string }) {
  return (
    <div className="stat">
      <div className="label">{label}</div>
      <div className="value">{nilai}</div>
    </div>
  );
}
