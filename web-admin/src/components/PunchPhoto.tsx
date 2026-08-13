import { useEffect, useState } from "react";
import { getDownloadURL, ref } from "firebase/storage";
import { storage } from "../firebase";

/**
 * Foto bukti absen.
 *
 * Jalur foto baru terisi setelah tablet berhasil mengunggahnya, jadi catatan
 * yang dibuat saat internet cafe mati akan tampil "menunggu unggah" sampai
 * jaringan hidup lagi — itu keadaan normal, bukan kesalahan.
 */
export default function PunchPhoto({ path }: { path: string }) {
  const [url, setUrl] = useState<string | null>(null);
  const [gagal, setGagal] = useState(false);

  useEffect(() => {
    let batal = false;
    if (!path) return;
    getDownloadURL(ref(storage, path))
      .then((u) => {
        if (!batal) setUrl(u);
      })
      .catch(() => {
        if (!batal) setGagal(true);
      });
    return () => {
      batal = true;
    };
  }, [path]);

  if (!path) return <span className="muted small">menunggu unggah</span>;
  if (gagal) return <span className="muted small">foto tidak ditemukan</span>;
  if (!url) return <span className="muted small">memuat…</span>;

  return (
    <a href={url} target="_blank" rel="noreferrer">
      <img className="photo" src={url} alt="Foto absen" />
    </a>
  );
}
