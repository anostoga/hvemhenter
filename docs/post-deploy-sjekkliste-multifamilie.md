# Post-deploy-verifiseringssjekkliste — Multi-familie-cutover

Kjør denne listen ETTER at rødsone-delene er implementert og deployet, IKKE for
denne forberedende leveransen (som ikke endrer runtime-adferd).

## Før deploy
- [ ] `./gradlew build` grønn lokalt (inkl. karakteriseringstester og nye rødsone-tester)
- [ ] `./gradlew flywayMigrate` kjørt mot Supabase `DATABASE_URL` (dev/staging først)
- [ ] `DATABASE_URL`, evt. `SESSION_SIGNING_SECRET` satt som Fly-secrets
- [ ] Lokal backup av `data/barnehage.db` tatt (kopiert et trygt sted utenfor Fly-volumet)

## Rett etter deploy
- [ ] `/health` svarer 200
- [ ] Din egen familie (eksisterende data) fungerer som før — foreslå/tildel/hent kalender
- [ ] Ny bruker kan logge inn med Google og opprette en ny testfamilie via `/velkommen` (uten kode)
- [ ] Testfamilie kan invitere og legge til forelder #2 med invitasjonskoden
- [ ] Testfamilie kan **ikke** se eller påvirke din families tildelinger (manuelt sjekk!)
- [ ] Cookie i nettleseren har `Secure`, `HttpOnly`, `SameSite=None` (sjekk DevTools → Application → Cookies)

## En uke etter
- [ ] Ingen uventede feil i Fly-loggene knyttet til DB-tilkobling eller sesjoner
- [ ] SQLite-volum (`barnehage_data`) kan fjernes fra `fly.toml` og slettes med `fly volumes destroy`
