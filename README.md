# Filc Mod

[![build](https://github.com/mikidev06/filc-mod/actions/workflows/build.yml/badge.svg)](https://github.com/mikidev06/filc-mod/actions/workflows/build.yml)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen)](https://fabricmc.net/)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue)](LICENSE)

A client-side Fabric mod that puts the [filc.petrik.hu](https://filc.petrik.hu) timetable
board inside Minecraft. Type `/filc` and your week opens as a chest GUI — lessons as items,
substitutions marked, no alt-tabbing.

Built for **Minecraft 1.21.1** / Fabric.

```
     P1        P2        P3        P4        P5        P6        P7        P8        
Hé   |Angol*2  |Komplex  |Kémia.a*2|DiKu*3   |Kémia.a*2|Irodalom |Töri     |Irinyi   |
Ke   |Töri     |Magyar   |Angol*2  |Vegy.al*3|Vegy.al*3|Vegy.al*3|Vegy.al*3|Vegy.al*3|
Sze  |Töri     |Komplex  |Tesi*2   |Angol*2  |Mv.ism*2 |Kémia.a*2|Angol*2  |Ofő      |
Cs   |Mat.ang  |Angol*2  |Komplex  |Angol*2  |Műsz.dig.|Tesi*2   |Angol    |Irinyi   |
Pé   |Kémia.a*2|Irodalom |Magyar   |Komple*2!|Tesi*2   |Angol*2  |Angol*2  |         |
```

<sub>Real 9.A data. `*N` is N parallel group lessons in one period; `!` is a substitution.
In game these are the item's stack count and an enchantment glint.</sub>

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.5 or newer for Minecraft 1.21.1.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) in your `mods` folder.
3. Put `filc-mod-<version>.jar` next to it.

The mod is **client-side only**. Nothing goes on the server, and it works on any server you
join — vanilla, modded, a friend's realm.

## Using it

```
/filc                          your class, if you have set one
/filc setclass <class> [group] remember a class and group
/filc class <class> [group]    any class, e.g. /filc class 12.C
/filc teacher <name>           a teacher's week
/filc room <name>              a room's week
/filc subs [class]             substitutions
/filc refresh                  drop cached data and re-fetch
```

Class, teacher and room names tab-complete once the reference lists have loaded, which
happens automatically when you join a world.

Set your class once and `/filc` is a single keystroke away from your week:

```
/filc setclass 9.A info1
```

### Reading the grid

Rows are Monday to Friday, columns are periods, and today's row is tinted green. Hovering a
slot gives you the subject, teachers, room and bell times; clicking one closes the GUI and
prints the same detail to chat so you can keep it.

- **Item count** — a class that splits into groups has two or three lessons running in the
  same period. The count is how many; the tooltip lists each with its group.
- **Enchantment glint** — a lesson with a substitution against it. The tooltip names the
  covering teacher in red.
- **Bottom row** — refresh, substitutions, and close.

Passing a group (`/filc class 9.A info2`) filters the grid down to one lesson per slot,
keeping the lessons taught to the whole class.

## Data

Everything comes from the public Chronos API behind filc.petrik.hu. No login, no account,
no credentials stored anywhere — the timetable endpoints are unauthenticated.

| What | Endpoint |
| --- | --- |
| Current timetable | `/timetable/timetables/latestValid` |
| Classes | `/timetable/cohorts/getAllForTimetable/:timetableId` |
| Bell times | `/timetable/periods/getAll?timetableId=` |
| Teachers, rooms | `/timetable/teachers/getAll`, `/timetable/classrooms/getAll` |
| Lessons | `/timetable/lessons/getForCohort\|getForTeacher\|getForRoom/:id` |
| Groups | `/timetable/groups/getForCohort/:cohortId` |
| Substitutions | `/timetable/substitutions/cohort/:cohortId` |

The API allows **180 requests per 30 seconds** per address and a single class timetable is
84 KB, so responses are cached rather than re-fetched per command: reference lists for a
day, the current timetable for an hour, lessons for six hours, substitutions for five
minutes. The cache survives restarts in `config/filc_mod/cache/`. Concurrent requests for
the same URL are shared, and a `429` is retried once honouring `Retry-After`.

`/filc refresh` clears all of it.

### Configuration

`config/filc_mod/config.json`, written by `/filc setclass`:

```json
{
  "defaultCohort": "9.A",
  "defaultGroup": "info1",
  "baseUrl": "https://filc.petrik.hu/api"
}
```

## Building

```bash
./gradlew build          # jar lands in build/libs/
./gradlew runClient      # dev Minecraft with the mod loaded
./gradlew test           # 50 unit tests
```

**Requires JDK 25 or newer.** Fabric Loom 1.18.2 ships Java 25 bytecode, so an older
JDK cannot resolve the plugin at all — Gradle fails with a `no matching variant` error
before the build starts. Loom also needs Gradle 9.7+, which the committed wrapper pins.
The mod itself is compiled against Java 21, so it runs on any Minecraft 1.21.1 install.

### Releasing

Releases are cut by pushing a version tag, not by pushing to `main`:

```bash
git tag v1.0.0
git push origin v1.0.0
```

CI then builds with `-Pmod_version=1.0.0`, checks that the version baked into
`fabric.mod.json` matches the tag, and publishes a GitHub Release with the mod jar and
sources jar attached. `gradle.properties` keeps saying `1.0-SNAPSHOT` — the tag is the
single source of truth for a release version.

A tag with a hyphen (`v1.0.0-rc1`) is published as a pre-release and is not marked latest.
You can also trigger a release by hand from the Actions tab.

## How it is put together

The code splits along one line: **anything that imports `net.minecraft` lives in
`src/client/java`, and everything else lives in `src/main/java`.** That keeps parsing,
caching and grid layout testable with plain JUnit, no game runtime involved.

```
src/main/java/me/mikidev06/filc_mod/
├── api/      FilcClient, FilcJson, Ttl, FilcApiException
├── model/    12 records mirroring the wire format
├── cache/    ResourceCache — raw bodies, TTL, disk-backed
├── grid/     Day, PeriodRange, Cell, WeekGrid
└── config/   FilcConfig

src/client/java/me/mikidev06/filc_mod/client/
├── command/  FilcCommand — Brigadier tree and completion
├── gui/      ChestScreen, TimetableScreen, SubstitutionsScreen
├── icon/     SubjectIcons — 48 keyword rules over 137 subjects
└── FilcState, Filc_modClient
```

Two decisions worth knowing about:

**The GUI is not a `HandledScreen`.** A real container screen needs a `ScreenHandler` synced
from the server, and a client-only mod has none. Faking one would make the client send
slot-click packets that a server will reject. `ChestScreen` instead draws
`generic_54.png` and hit-tests the slots itself — same appearance, zero server interaction,
and a "slot" can mean whatever the screen wants.

**JSON is parsed by hand, not bound reflectively.** The API sends a field called `short`,
which cannot be a Java record component, so there was never a reflective option. Doing it
explicitly also means missing and null fields are handled where they occur rather than
becoming a surprise at render time.

Screens are opened on the next client tick rather than directly from the command, because
the chat screen closes itself immediately afterwards and would otherwise wipe whatever the
command set.

### Tests

`./gradlew test` runs 50 tests against real API responses captured into
`src/test/resources/fixtures/` — parsing, group filtering, the day/period to slot mapping,
period-range clamping, cache expiry and disk round-tripping, and config handling. Testing
against captured payloads rather than hand-written ones is deliberate: every field name,
null and nesting quirk in them came off the wire.

## Credits

Timetable data and API by the [Filc](https://github.com/filcdev/filc) project (AGPL-3.0).
This mod is an independent client and is not affiliated with them or with Petrik Lajos.

Licensed under GPL-3.0 — see `LICENSE`.
