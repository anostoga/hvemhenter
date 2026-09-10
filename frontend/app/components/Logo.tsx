export function Logo({ height = 32 }: { height?: number }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 620 200"
      height={height}
      width={(465 / 150) * height}
      aria-hidden={false}
      role="img"
    >
      <title>Hvem henter?</title>

      <g transform="translate(1,23) scale(0.75)">
        {}
        <circle cx="100" cy="102" r="72" fill="#F7CBA4" stroke="#FBF7EF" strokeWidth={5} />
        {}
        <path
          d="M87 33 C79 19, 99 15, 103 29"
          fill="none"
          stroke="#FBF7EF"
          strokeWidth={5}
          strokeLinecap="round"
        />
        {}
        <circle cx="58" cy="118" r="12" fill="#F2977E" opacity={0.55} />
        <circle cx="142" cy="118" r="12" fill="#F2977E" opacity={0.55} />
        {}
        <circle cx="74" cy="94" r="6.5" fill="#2E2A25" />
        <circle cx="126" cy="94" r="6.5" fill="#2E2A25" />
        {}
        <path
          d="M70 122 Q100 152, 130 122"
          fill="none"
          stroke="#2E2A25"
          strokeWidth={6}
          strokeLinecap="round"
        />
      </g>

      <text
        x="162"
        y="118"
        fontFamily="'Segoe UI', system-ui, -apple-system, 'Helvetica Neue', Arial, sans-serif"
        fontSize={54}
        fontWeight={700}
        fill="#FBF7EF"
        letterSpacing="-0.5"
      >
        Hvem henter<tspan fill="#FFD9C2">?</tspan>
      </text>
    </svg>
  );
}
