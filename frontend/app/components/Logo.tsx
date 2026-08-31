/**
 * Logo som viser en forelder og et barn som holder hender.
 * Rendres inline som JSX (ikke en statisk fil i public/) siden den er liten
 * og brukes ett sted — unngår en ekstra HTTP-request og gjør den enkel å
 * skalere via `size`-prop.
 */
export function Logo({ size = 32 }: { size?: number }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 240 240"
      width={size}
      height={size}
      aria-hidden={false}
      role="img"
    >
      <title>Forelder og barn som holder hender</title>

      {/* Foreldrens arm (tegnes først, så kroppen overlapper toppen) */}
      <path d="M134,90 Q98,112 112,146" fill="none" stroke="#146C6B" strokeWidth={20} strokeLinecap="round" />
      {/* Barnets arm */}
      <path d="M88,142 Q98,144 112,146" fill="none" stroke="#F0985A" strokeWidth={16} strokeLinecap="round" />

      {/* Foreldrens kropp */}
      <path
        d="M130,84
           C119,122 111,162 111,202
           Q111,212 121,212
           L195,212
           Q205,212 205,202
           C205,162 197,122 186,84
           Z"
        fill="#146C6B"
      />
      {/* Foreldrens hode */}
      <circle cx="158" cy="56" r="25" fill="#FFE1B8" />

      {/* Barnets kropp */}
      <path
        d="M62,140
           C55,162 50,186 50,206
           Q50,212 56,212
           L97,212
           Q103,212 103,206
           C103,186 98,162 91,140
           Z"
        fill="#F0985A"
      />
      {/* Barnets hode */}
      <circle cx="76" cy="119" r="18" fill="#FFE1B8" />

      {/* Aksent der hendene møtes */}
      <circle cx="112" cy="146" r="9" fill="#F6C34D" />
    </svg>
  );
}
