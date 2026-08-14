/**
 * Foto bukti absen.
 *
 * Fotonya tertanam langsung di dokumen absen sebagai data URL JPEG kecil
 * (360×360, sekitar 5 KB), bukan berkas terpisah di Cloud Storage. Dengan
 * begitu tablet bisa menyimpan absen berikut buktinya dalam satu penulisan
 * yang tetap mengantre saat internet cafe mati.
 */
export default function PunchPhoto({ photo }: { photo: string }) {
  if (!photo) return <span className="muted small">tidak ada foto</span>;

  return (
    <a href={photo} target="_blank" rel="noreferrer">
      <img className="photo" src={photo} alt="Foto absen" />
    </a>
  );
}
