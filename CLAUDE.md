# Shizuku+-API — Claude Code Guidelines

Client library for [Shizuku+](https://github.com/thejaustin/ShizukuPlus); backward compatible with stock Shizuku and Sui servers.

## Branding

- Library name: `Shizuku+-API`. Maven group `af.shizuku.plus` (`groupIdBase` in `build.gradle`); JitPack coordinates `com.github.thejaustin:Shizuku+-API:<ver>-plus`.
- Module packages stay `rikka.shizuku.*` (api, aidl, provider, shared, server) so existing Shizuku clients can switch with no import changes — don't rename them to `af.shizuku.*`.
- Plus-only interfaces carry a `Plus` suffix (e.g. `IDeviceControlPlus`).

### Project family (all by thejaustin)

| Product | Display name | Repo | Package / coordinates | Upstream |
|---------|-------------|------|------------------------|----------|
| Shizuku+ | `Shizuku+` | `thejaustin/ShizukuPlus` | `af.shizuku.plus.api` (Plus flavor), `moe.shizuku.privileged.api` (Drop-In flavor) | thedjchi/Shizuku ← RikkaApps/Shizuku |
| Shizuku+-API | `Shizuku+-API` | `thejaustin/ShizukuPlus-API` | Maven group `af.shizuku.plus`; JitPack `com.github.thejaustin:Shizuku+-API:<ver>-plus` | RikkaApps/Shizuku-API |
| Obtainium+ | `Obtainium+` | `thejaustin/ObtainiumPlus` | `dev.thejaustin.obtainiumplus` | ImranR98/Obtainium |
| SuperShade | `SuperShade` | `thejaustin/SuperShade` | `com.supershade` | original (no upstream) |

Naming rules:
- User-facing text uses the `+` form (`Shizuku+`, `Obtainium+`); repo names, URLs, and code identifiers spell it `Plus` (`ShizukuPlus`, `PlusSettingsProvider`). Never write "Shizuku Plus" or "ObtainiumPlus" in UI strings.
- `SuperShade` is one word, capital S twice — never "Super Shade" / "Supershade".
- Refer to upstreams by their own names (Shizuku, Obtainium) and credit them; don't rebrand upstream attributions or license notices.
