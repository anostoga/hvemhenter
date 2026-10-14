import Image from "next/image";

export function Logo({ height = 32 }: { height?: number }) {
  return (
    <>
      <Image
        src="/logo.svg"
        alt="Hvem henter?"
        width={Math.round((1289 / 344) * height)}
        height={height}
        priority
        className="hidden sm:block"
      />
      <Image
        src="/logo-mobile.svg"
        alt="Hvem henter?"
        width={Math.round((759 / 316) * height)}
        height={height}
        priority
        className="sm:hidden"
      />
    </>
  );
}
