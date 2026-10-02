> Provenance review: the earlier clean-room claim has not been verified. This is a historical architecture record, not a verified provenance statement.

# OpenCloud Android Next: oCIS Architecture Re-engineering Whitepaper

**Document status:** Phase 1–6 modernization wrap-up  
**Audience:** OpenCloud Android maintainers, reviewers, release engineers, security reviewers, and future feature owners  
**Prepared:** September 9, 2026  
**Project:** `opencloud-android-next`  
**Application ID:** `eu.opencloud.android.next`

---

## 1. Executive Summary

Raiun (formerly OpenCloud Android Next) is a native Android client designed around the actual service boundaries of oCIS rather than around historical Nextcloud server assumptions. The modernization did not attempt to incrementally retrofit the legacy Android application. It established a separate Kotlin/Jetpack Compose application, preserved proven Android platform behaviors where they remain valuable, and replaced legacy protocol assumptions with an explicit combination of Libre Graph, OCS, WebDAV, OIDC/WebFinger, TUS, Room, DataStore, and WorkManager.

The central architectural result is a **space-aware, offline-first client**:

- A resource is identified by account, drive/space, and remote item identity—not by a global path alone.
- Libre Graph is the authority for drive metadata and Graph-native operations such as favorites.
- The server-provided drive root `webDavUrl` is the authority for file-content and hierarchy operations.
- OCS remains intentionally in use for capabilities and the sharing API where oCIS exposes compatibility contracts.
- Room is the durable source of truth for user-visible account, space, resource, transfer, backup, and share state.
- WorkManager executes durable transfers, discovery, offline synchronization, backup scans, and cache cleanup.
- Compose feature modules render immutable UI state and dispatch actions instead of directly owning protocol state.

Phases 1 through 6 established the application foundation, authentication and protocol stack, core file browser, durable transfers and background synchronization, user-facing supporting features, sharing, and a dedicated Spaces experience. Phase 6 completed the visible oCIS Spaces flow and corrected an important domain-boundary bug: the Spaces tab now consumes only enabled, non-deleted drives whose Graph `driveType` is `project`. Personal and virtual drives remain persisted because the file browser and system integration still need them, but they are no longer misrepresented as collaboration Spaces.

This modernization should be understood as a **protocol and state-model correction**, not merely a visual redesign. The UI modularization, persistent application navigation, and oCIS-aligned screens are visible outcomes, but the deeper value is that server capability, drive identity, durable work, and local persistence now have explicit ownership boundaries.

The result is suitable for release hardening, but it is not a declaration that every legacy feature or every oCIS edge case is complete. Dynamic OIDC client registration, cross-space transfers, comprehensive migration testing, large-scale interoperability testing, and several production-operability concerns remain recommended follow-up work.

---

## 2. Modernization Objectives and Principles

### 2.1 Objectives

The Phase 1–6 effort pursued the following objectives:

1. Build a modern Android application without inheriting the legacy Activity/Fragment/XML and service architecture.
2. Align domain identity and remote operations with oCIS drives and Libre Graph.
3. Preserve offline and background behavior through explicit durable state.
4. Preserve Android system integration through the Storage Access Framework and a `DocumentsProvider`.
5. Make server capabilities enforceable application policy rather than informational UI metadata.
6. Reproduce the OpenCloud mobile-web interaction model using native Compose components.
7. Separate features into modules so maintainers can reason about account, files, search, transfers, settings, shares, and spaces independently.
8. Remove unsafe or obsolete compatibility shortcuts rather than perpetuating them in a new codebase.

### 2.2 Governing principles

The implementation follows these principles:

- **Offline-first UI:** Network responses update durable state; Room flows drive the screen.
- **Space-scoped identity:** The stable resource key is `(accountId, spaceId, remoteId)`.
- **Server-advertised roots:** Never synthesize a drive root when Graph provides `root.webDavUrl`.
- **Capability-gated behavior:** Unsupported operations must not be exposed or attempted optimistically.
- **Durable background work:** Persist transfer intent and checkpoints before scheduling WorkManager.
- **Least-privilege security:** Credentials are encrypted with Android Keystore keys; TLS never has a trust-all escape hatch.
- **Protocol specialization:** Graph, OCS, WebDAV, OIDC, and TUS each have a defined purpose; no single compatibility protocol is treated as universal.
- **Feature isolation:** UI modules depend on shared core contracts; protocol and persistence code do not depend on feature UI.
- **Explicit empty and error states:** Missing capabilities, empty filtered datasets, and failed operations are first-class states.

---

## 3. Phase 1–6 Outcome Map

The repository history available at the time of this document includes consolidated commits for later phases and uncommitted Phase 6 work. The architectural progression is best understood by capability rather than by commit count.

| Phase | Primary outcome | Architectural significance |
|---|---|---|
| Phase 1 | Kotlin/Compose project foundation, design system, test/build baseline | Created a separate application boundary and eliminated dependence on legacy Java/XML presentation infrastructure. |
| Phase 2 | Server discovery, Basic and OIDC/PKCE authentication, credential storage, TLS policy, capability discovery | Established secure account/session ownership and server-driven feature policy. |
| Phase 3 | Space-aware Room schema, Graph drive discovery, WebDAV file browsing/CRUD, native file browser, initial SAF integration | Replaced path-only assumptions with account/space/item identity and an offline-first browser. |
| Phase 4 | Persisted transfers, TUS support, WorkManager workers, offline files, discovery, cleanup, folder/camera backup | Made long-running work resumable and process-independent. |
| Phase 5 A/B | Transfer UI, local/remote search, file UX refinements | Exposed durable work and combined Room results with server search when supported. |
| Phase 5 C/D | Favorites, deleted files, settings, account modularization | Mapped favorites to Graph, trash to oCIS WebDAV, and settings/account ownership to dedicated features. |
| Phase 5 E | OCS sharing engine, share recipient search, public-link configuration, capability enforcement, UI polish | Retained OCS where it is the correct oCIS compatibility surface and enforced advertised restrictions. |
| Phase 6 | Dedicated Spaces module and tab, project-drive filtering, owner/description/quota presentation, visual baselines | Completed the collaboration-space UX while preventing personal and virtual drives from leaking into the Spaces domain. |

---

## 4. Current Architecture

### 4.1 Layered data flow

```text
Jetpack Compose feature UI
        │ actions / immutable state
        ▼
ViewModel or feature coordinator
        │ suspend calls / Flow collection
        ▼
Repository or manager in core:sync / feature boundary
        ├──────────────► Protocol client in core:network
        │                    Graph / OCS / WebDAV / OIDC / TUS
        │
        └──────────────► FileBrowserStore in core:database
                             │
                             ▼
                         Room database
                             │ Flow
                             └──────────────► UI state

Durable operation intent
        ▼
TransferEntity / FolderBackupEntity
        ▼
WorkManager workers
        ▼
Protocol client + local cache + Room checkpoint/update
```

The intended invariant is that a protocol response does not become the long-lived UI model by itself. Remote data is normalized into entities, stored transactionally, and observed through Room. Operations that must survive process death are represented in the database before WorkManager is enqueued.

### 4.2 Module responsibilities

#### Application composition

- `:app` owns the single-activity composition root, session-to-destination switching, and assembly of feature routes.
- `OpenCloudNextApp` selects the authenticated or authentication flow and coordinates top-level destinations.

#### Core modules

- `:core:model` contains protocol-independent domain/authentication models and shared constants.
- `:core:designsystem` contains OpenCloud color, typography, dimensions, theme, and reusable navigation components.
- `:core:ui` contains reusable screen/foundation UI primitives.
- `:core:network` contains focused clients for discovery, OIDC, Graph drives, Graph favorites, WebDAV discovery/CRUD/trash/search, OCS sharing, upload/download, and TUS.
- `:core:security` owns encrypted credential persistence and host-scoped TLS pin policy.
- `:core:database` owns the Room schema, DAOs, migrations, and `FileBrowserStore` transaction boundary.
- `:core:sync` owns repositories, transfer orchestration, authorization for workers, sharing coordination, trash coordination, discovery, backup, offline sync, and cleanup workers.
- `:core:documentsprovider` exposes accounts, drives, and cached resources through Android's Storage Access Framework.
- `:core:datastore` owns typed user preferences such as browser layout, active account, and cache retention.

#### Feature modules

- `:feature:auth` — discovery, login, PKCE flow, session restoration, and authentication UI.
- `:feature:files` — primary browser, favorites presentation, deleted files, and backup settings integration.
- `:feature:search` — local Room search and capability-gated remote search composition.
- `:feature:transfers` — durable transfer status, retry/cancel/conflict presentation, and history.
- `:feature:settings` — typed settings presentation.
- `:feature:account` — account switching/removal/add-account entry.
- `:feature:shares` — top-level shares and resource sharing workflows.
- `:feature:spaces` — dedicated project-space list and space-root opening.

### 4.3 Persistence model

The Room database currently persists:

- `AccountEntity` — server URL, user identity, authentication type, selected capability policy, and selected OIDC endpoints.
- `SpaceEntity` — Graph drive identity, `driveType`, root item identity, root WebDAV URL, owner, description, aliases, quota, disabled state, and deleted state.
- `ResourceEntity` — space-scoped file/folder metadata, paths, ETags, favorites, local cache information, and offline pinning.
- `TransferEntity` — transfer intent, progress, state, WorkManager identity, conflict and TUS resume state.
- `FolderBackupEntity` — source URI, destination, policy, and scheduling state.
- `ShareEntity` — normalized owned and received sharing state.

The database schema is versioned and exported. Remote space replacement is transactional: drives missing from the new Graph snapshot and their associated resources are removed, then the current snapshot is upserted.

### 4.4 Security model

- Basic usernames/passwords and OIDC tokens are encrypted before being written to private preferences.
- Encryption uses AES-GCM with a key generated and retained by Android Keystore.
- TLS uses Android platform trust and hostname verification by default.
- Explicit certificate approval is implemented as host-scoped SHA-256 SPKI pinning.
- There is deliberately no permissive hostname verifier, trust-all manager, or global certificate bypass.
- Public share URLs are wrapped as transient values and redact themselves from `toString()` to reduce accidental logging.
- XML parsers used for WebDAV reports disable or restrict external entity processing.

---

## 5. Architecture Mapping: Legacy Nextcloud/WebDAV to oCIS/Libre Graph

The modernization does **not** eliminate WebDAV. It narrows WebDAV to the operations for which oCIS exposes it, while moving drive and item metadata operations to Graph and retaining OCS only where oCIS intentionally supports the compatibility API.

| Concern | Legacy-oriented approach | Current oCIS-aligned approach | Maintainer guidance |
|---|---|---|---|
| Account/server discovery | Server URL assumptions and compatibility discovery mixed into login flows | Normalize server URL, check `status.php`, use WebFinger for issuer/client metadata, and OIDC discovery for endpoints | Keep discovery isolated from UI and persist only validated account/session metadata. |
| Resource identity | Account plus remote path, often assuming one personal root | `(accountId, spaceId, remoteId)` with path as mutable metadata | Never use path alone as a database primary key or cross-drive identity. |
| Drive/space enumeration | Personal WebDAV root and server-specific space workarounds | `GET /graph/v1.0/me/drives`, including pagination and Graph `driveType` | Persist all drives needed by browsing; apply product-specific filters at repository/feature boundaries. |
| Drive root | Constructed DAV paths based on username or historical endpoint layout | Graph `root.id` and `root.webDavUrl` | Treat the advertised WebDAV URL as opaque and authoritative. |
| Spaces tab | Generic all-root/all-drive presentation | Only enabled, non-deleted drives with `driveType == "project"` | Do not infer a Space from display name, quota, or path. Use the Graph type. |
| Personal files | Implicit personal WebDAV namespace | Personal Graph drive persisted as a `SpaceEntity`, opened by drive ID | Personal drives are valid file-browser roots but are not collaboration Spaces. |
| Shares pseudo-root | Virtual filesystem folder or synthetic DAV root mixed with normal drives | Sharing has a dedicated OCS-backed feature; Graph virtual drives may remain persisted for browser/system needs | Do not list virtual `Shares` drives in the Spaces feature. |
| File listing and metadata | Broad PROPFIND use against constructed personal DAV paths | WebDAV discovery rooted at each Graph drive's advertised URL, normalized into Room | Keep XML parsing hardened and update Room snapshots transactionally. |
| File upload/download | Foreground services, ad hoc service state, classic PUT/chunking | Persisted transfer rows + WorkManager; range downloads; PUT; TUS when advertised | Database state must precede scheduling; worker retry policy must distinguish recoverable failures. |
| Favorites | DAV favorite properties or locally maintained favorite flags | Libre Graph follow/unfollow endpoints, mirrored into Room | Graph is authoritative for mutations; Room remains the presentation source. |
| Search | Local database search or Nextcloud-specific search assumptions | Room search plus capability-derived WebDAV `search-files` REPORT endpoint | Remote search must remain optional and capability-gated. Merge/deduplicate by stable identity. |
| Deleted files | Legacy trash APIs or personal-root assumptions | oCIS WebDAV trash collection derived from the drive root | Gate by persisted `trashSupported`; keep operations drive-aware. |
| Sharing | Nextcloud UI and permissive OCS assumptions | Focused OCS Sharing API client/manager, recipient search, normalized shares, public-link policy enforcement | OCS remains valid here; do not replace it merely for architectural purity unless oCIS provides a complete successor contract. |
| Capabilities | Parsed opportunistically; unsupported UI sometimes remained reachable | OCS capabilities parsed into `ServerCapabilities`, persisted onto the account, and enforced before operations | Capabilities are policy. Parse defensively and default to disabled on failure. |
| Settings | SharedPreferences spread across screens/services | Typed protobuf DataStore with validation, repair, corruption handling, and canonical defaults | Add settings through `SettingsRepository`; avoid feature-owned untyped preferences. |
| Credentials | AccountManager/legacy storage coupling | Keystore-backed encrypted credential store | Keep secrets out of Room and logs. |
| Android file access | Historical provider/SQLite and direct app-file assumptions | Versioned SAF document IDs over account/space/resource identities; cached file delivery via transfer manager | Maintain stable document IDs and test concurrent/cancelled access behavior. |
| Navigation | Fragment/activity/drawer-specific state and duplicated toolbars | Compose application shell with persistent top/bottom navigation and feature content injection | Keep the shell stable; features should supply content and callbacks rather than duplicate global chrome. |

### 5.1 Favorites

Favorites illustrate the intended hybrid protocol model. The local `favorite` flag is useful for fast offline rendering, but mutation uses Libre Graph follow/unfollow operations. This avoids relying on legacy WebDAV favorite properties while retaining an offline-first list. A mutation is sent to Graph and then reflected in Room. Future synchronization should ensure server changes made by another client are reconciled during discovery.

### 5.2 Spaces

Graph returns multiple drive types, including personal, project, and virtual drives. They are not interchangeable product concepts:

- **Personal drive:** required as a file-browser root.
- **Project drive:** a dedicated collaboration Space and valid Spaces-tab item.
- **Virtual drive:** a server-generated aggregation such as Shares; useful in some browsing contexts but not a Space.
- **Disabled/deleted drive:** retained only as needed for synchronization decisions and excluded from active UX.

`SpaceRepository.observeProjectSpaces` is the product boundary for the Spaces tab. It filters case-insensitively for `project` and excludes disabled/deleted entities. This fix intentionally does not modify Graph ingestion or remove non-project drives from persistence.

### 5.3 Capabilities

Capabilities are obtained through OCS and normalized into a deliberately small model:

- sharing enabled;
- public sharing enabled;
- Spaces availability;
- TUS support;
- remote search endpoint availability;
- trash support;
- public-link password support and enforcement;
- public-link expiration support, enforcement, and default day count.

The parser is defensive: malformed or unavailable capability data falls back to disabled behavior. This is safer than exposing an operation and discovering incompatibility only after user input. The sharing manager separately enforces sharing and public-sharing policy before issuing requests.

---

## 6. What Was Kept

“Kept” means the capability or architectural role was deliberately preserved, even where the implementation was rewritten.

### 6.1 WebDAV as the file data plane

WebDAV remains the correct transport for folder listing, file content, creation, deletion, move/copy within supported boundaries, trash, and `search-files` REPORT behavior. The modernization removed the assumption that WebDAV also defines the entire product model. Graph now supplies drive roots and drive types; WebDAV operates below that boundary.

### 6.2 OCS where oCIS still exposes a compatibility contract

OCS was retained for:

- server capabilities;
- user/profile compatibility endpoints used during authentication;
- sharing and share-recipient operations.

This is a pragmatic contract decision. Replacing a supported OCS API with an incomplete Graph approximation would reduce interoperability, not improve it.

### 6.3 Offline files and local cache behavior

The proven user requirement that files can be pinned, cached, and opened without an active connection remains. It is now represented explicitly in `ResourceEntity`, transfer state, scheduled offline synchronization, and cache retention policy.

### 6.4 Durable transfers and background scheduling

The legacy client correctly treated uploads, downloads, camera uploads, and maintenance as work that must outlive a screen. That behavior was retained but reimplemented around WorkManager and persisted transfer/backup entities instead of legacy service coupling.

### 6.5 Android Storage Access Framework integration

The application continues to appear as a system document provider. Accounts are roots; drives are directories beneath them; resources use versioned encoded IDs. Opening uncached documents delegates to the durable download pipeline.

### 6.6 Multi-account support

Account switching and removal remain first-class. Active-account selection moved into typed DataStore, while account metadata remains in Room and secrets remain in the encrypted credential store.

### 6.7 Conflict awareness and ETags

The implementation retains precondition-based overwrite protection, ETag tracking, explicit conflict state, and user-visible resolution paths. These are essential for a correct file client and must not be simplified into unconditional overwrite behavior.

### 6.8 OpenCloud visual language

The OpenCloud mobile-web experience remains the visual/product reference. Compose and Material 3 are implementation tools; OpenCloud tokens, spacing, color, navigation, and interaction behavior define the product.

---

## 7. What Was Changed

### 7.1 Clean application and module boundary

The largest structural change is that the new client is not a continuation of the legacy application's package, database, or UI hierarchy. Production code is Kotlin; screens use Compose; feature modules make ownership visible; the application can install alongside the legacy client.

### 7.2 UI modularization and persistent shell

Authentication, files, search, transfers, settings, account management, shares, and spaces have explicit modules or feature boundaries. Global navigation is owned by the FileBrowser/application shell, allowing top and bottom navigation to remain present while feature content changes. This removes duplicated toolbar/navigation implementations and makes top-level destinations consistent.

### 7.3 Drive-aware file model

The database and managers are explicitly space-aware. Paths remain useful for WebDAV addressing and display, but they are no longer treated as globally stable identifiers. Graph drive IDs and remote item IDs anchor state.

### 7.4 Libre Graph drive ingestion

Drive discovery now uses `/graph/v1.0/me/drives`, follows `@odata.nextLink`, persists `driveType`, root metadata, owner, aliases, timestamps, quota, and disabled/deleted state. This is the basis for personal files and project Spaces.

### 7.5 Dedicated Spaces feature

Phase 6 introduced `:feature:spaces` with loading, empty, error, and populated states. Cards present name, description, owner, and quota where available. Selecting a project drive delegates to `FileBrowserViewModel.selectSpace`, reusing the file browser for the drive root instead of building a second file-navigation stack.

### 7.6 Durable transfer engine

Uploads and downloads are persisted before work is scheduled. The transfer stack includes:

- deduplication of active equivalent transfers;
- network constraints and exponential backoff;
- WorkManager IDs/tags;
- resumable range downloads where supported;
- TUS creation, offset validation, and PATCH upload;
- conflict state and retry/cancel behavior;
- available-offline synchronization;
- periodic cache cleanup;
- folder/media backup scanning.

### 7.7 Search architecture

Search combines fast Room-backed local results with optional server-side WebDAV REPORT results. The remote endpoint is derived from capabilities and is not called when unavailable. Results are normalized to the same resource identity model and deduplicated.

### 7.8 Sharing engine

Sharing moved into focused OCS network and synchronization components plus a dedicated feature UI. Owned and received shares are normalized into Room. Public link creation/update behavior is capability-aware, including password and expiration rules. Public URLs are treated as transient sensitive values.

### 7.9 Capability parsing and enforcement

Capabilities changed from descriptive metadata into persisted application policy. The account row contains the subset needed by background workers and managers, eliminating reliance on a currently alive login screen or an in-memory capability object.

### 7.10 Settings and account ownership

Settings moved to a typed protobuf DataStore with range validation, repair, and corruption recovery. Account UI moved out of the file screen, while session restoration verifies that usable encrypted credentials remain available.

### 7.11 Security hardening

The modernization replaced implicit or broad trust workarounds with explicit host-scoped SPKI pin approval. Credential persistence is encrypted and separated from the main database. XML parsing and public-link logging boundaries were hardened.

### 7.12 Test strategy

The project uses focused unit tests, MockWebServer protocol tests, Room tests, state tests, and host-side Compose golden tests. Phase 6 added empty, loading, and populated Spaces baselines plus repository regression coverage proving that personal, virtual, disabled, and deleted drives do not appear in the Spaces stream.

---

## 8. What Was Dropped

### 8.1 Legacy Java/XML presentation infrastructure

Activities, Fragments, XML layouts, view binding patterns, and screen-specific legacy navigation were not carried into the new project. A single Compose application tree replaces those presentation layers.

### 8.2 Legacy database and in-place migration coupling

The new app has its own database and application ID. It does not attempt to import or mutate the legacy client's large historical schema. This avoids encoding decades of compatibility baggage into the new domain model. It also means migration between installed applications is a separate product decision, not an implicit feature.

### 8.3 Constructed personal WebDAV roots

Username-derived or fixed DAV root templates are obsolete for drive-aware oCIS operation. The client uses the Graph-advertised `root.webDavUrl`.

### 8.4 Path-only resource identity

Remote path is no longer sufficient identity. It changes on rename/move and can collide across drives. The database primary key includes account, space, and remote ID.

### 8.5 DAV-based favorites workaround

Favorite mutation no longer depends on legacy DAV favorite properties. Libre Graph follow/unfollow is the mutation surface.

### 8.6 Shares-as-a-normal-Space presentation

Virtual Shares drives are no longer displayed as dedicated project Spaces. Sharing has its own feature and virtual roots remain an implementation detail where needed.

### 8.7 Redundant top-level UI components

Feature-owned copies of global navigation, drawers, and shell controls were removed or avoided. Feature content is hosted inside persistent application chrome.

### 8.8 Unsafe TLS bypasses

Trust-all certificate managers, disabled hostname verification, and global “accept invalid certificate” behavior are explicitly absent. The only non-platform trust extension is a user-approved pin scoped to a host.

### 8.9 Capability bypasses and temporary diagnostics

Temporary logging, capability overrides, permissive sharing paths, and debug bypasses used during implementation were removed before Phase 5 E completion. Unsupported server behavior now fails closed.

### 8.10 Direct network-owned screen state

Long-lived screens no longer treat a successful response object as authoritative state. Data is persisted and observed. This removes refresh races and improves recovery after process recreation.

---

## 9. Phase 6 Detailed Review

### 9.1 Completed behavior

Phase 6 delivered:

- a dedicated `:feature:spaces` Gradle module;
- Spaces integration into the existing authenticated file-browser shell;
- persistent top and bottom navigation while Spaces content is selected;
- loading, empty, error, and populated UI states;
- project Space cards with description, owner, used/available quota, and quota progress;
- selection of a Space by Graph drive ID;
- opening the selected drive root through the existing file-browser ViewModel;
- deterministic visual baselines for empty, loading, and populated states;
- repository tests for positive and negative filtering behavior.

### 9.2 Filtering correction

The initial Spaces UI consumed the repository's all-drive flow. That flow correctly included personal and virtual drives because other parts of the application require them. The error was therefore not in Graph parsing or persistence; it was at the feature consumption boundary.

The correction added a specific repository projection:

```kotlin
space.type.equals("project", ignoreCase = true) &&
    !space.isDisabled &&
    !space.isDeleted
```

This design is preferable to filtering at ingestion because it preserves a complete local model of the account's accessible drives while allowing each product feature to define its own valid subset.

### 9.3 Empty state semantics

If Graph returns drives but none qualify as active project drives, the Spaces UI displays the normal empty state. This is intentional: “no project Spaces” is the user-visible truth even if personal or virtual drives exist internally.

### 9.4 Validation status at wrap-up

Before this whitepaper task, focused repository tests, `ktlintFormat`, and `assembleDebug` had passed for the Phase 6 implementation. The latest reviewed debug APK was:

- Path: `app/build/outputs/apk/debug/app-debug.apk`
- Size: `21,633,614` bytes
- SHA-256: `190203ACDB531DE3BD7EBBACF853E5D1580A779B164C05DBFFEEBD5D97CBFE02`

No UI screencap workflow was used. The visual assets are deterministic Roborazzi test baselines, not manual device screenshots.

---

## 10. Known Missing Items and Technical Debt

The following items should be treated as explicit release limitations or follow-up engineering work.

### 10.1 OIDC dynamic client registration

OIDC discovery records `registration_endpoint`, and WebFinger may advertise a client ID. However, the current flow does not perform RFC 7591-style dynamic client registration. It uses the server-advertised client ID when available and otherwise falls back to the static `OpenCloudAndroid` client ID.

**Risk:** Instances that require per-client registration and do not advertise a usable pre-registered ID cannot complete login.

**Recommendation:** Implement registration as a typed network operation, persist client registration metadata securely per issuer/server, define rotation/re-registration behavior, and cover public-client token endpoint authentication modes.

### 10.2 Cross-space move and copy

The domain and UI support file operations within the space-aware browser, but cross-space move/copy has not been established as a separately tested workflow with documented server guarantees.

**Risk:** A DAV `MOVE`/`COPY` across roots may be rejected, may not be atomic, or may require download/upload semantics. Permissions, quota, conflict, and rollback behavior can differ by source and destination drive.

**Recommendation:** Disable or clearly constrain cross-space actions until an interoperability matrix is complete. If the server cannot guarantee atomic cross-drive operations, model the action as a durable copy transfer followed by verified source deletion, with explicit partial-failure recovery.

### 10.3 Full capability schema coverage

The capability model intentionally includes only fields consumed by current features. `spacesEnabled` and TUS detection are currently derived defensively from broader payload content rather than a strongly typed schema for every server version.

**Risk:** Server payload evolution may produce false negatives or, less desirably, ambiguous positives.

**Recommendation:** Add captured oCIS capability fixtures for supported release versions, type the consumed subtrees, and version the interpretation rules.

### 10.4 Capability refresh lifecycle

Capabilities are persisted at authentication time. A long-lived account may retain stale policy if the server configuration changes.

**Recommendation:** Refresh capabilities during account discovery and after relevant 403/404/405 responses, then atomically update account policy and UI availability.

### 10.5 Graph delta synchronization and pagination scale

Drive pagination is implemented, but file discovery currently relies on folder snapshots rather than a complete Graph delta strategy.

**Risk:** Large accounts can require expensive repeated traversal, and changes made by other clients may not be reflected until the affected folder is refreshed.

**Recommendation:** Evaluate oCIS-supported delta/change tokens before release to large deployments. Persist tokens per drive and provide a safe full-rescan fallback.

### 10.6 Favorite reconciliation

Favorite mutations use Graph and update Room, but comprehensive periodic reconciliation of favorites changed by another client should be verified.

**Recommendation:** Include favorite state in discovery/delta synchronization and add multi-client tests.

### 10.7 DocumentsProvider production hardening

The provider exposes stable account/space/resource IDs and can download uncached files. Additional stress coverage is advisable for concurrent opens, cancellation, stale local files, provider process recreation, large files, writable modes, thumbnails, and external-app retry behavior.

### 10.8 Database migration tests

The Room schema includes explicit migrations through version 9 and exports schemas, but the release gate should include migration tests from every supported installed version and destructive-migration prohibition.

### 10.9 Legacy-app migration and coexistence policy

Because Android Next uses a new application ID and database, user accounts, offline files, and settings are not automatically imported from the legacy client.

**Recommendation:** Make the product decision explicit: side-by-side beta, guided reauthentication, or a separately designed secure migration/export channel. Do not silently read another application's data.

### 10.10 End-to-end server interoperability matrix

Unit and protocol-client tests cannot replace testing against supported oCIS deployments, proxies, identity providers, storage drivers, and policy combinations.

**Recommendation:** Build a release matrix covering at least Basic/OIDC, advertised/static client IDs, project/personal/virtual drives, public-link policies, TUS on/off, trash on/off, remote search on/off, quota exhaustion, expired tokens, certificate rotation, and large datasets.

### 10.11 Token refresh concurrency and revocation

Persisted OIDC tokens support session restoration and refresh-token use. Production hardening should verify single-flight refresh across simultaneous workers, invalid-grant handling, logout/revocation semantics, clock skew, and issuer/client binding.

### 10.12 Dependency injection and test seams

Several ViewModels/managers construct database, repository, security, and HTTP dependencies directly from `Application`/`Context`. This keeps the current project small but increases test setup cost and hides object lifetime policy.

**Recommendation:** Introduce a lightweight application component or confirmed dependency-injection framework only when the project is ready to standardize it. Do not add a framework merely for style; first define singleton, account-scoped, worker-scoped, and feature-scoped lifetimes.

### 10.13 User-facing operation error taxonomy

Some layers still map broad network/protocol failures to generic messages. Transfer HTTP failures preserve codes, but a shared typed error model would improve retry policy and support diagnostics.

### 10.14 Localization and accessibility audit

User-facing strings are currently embedded in several Compose files. A release candidate should move them to resources, verify pluralization and formatting, audit content descriptions, test larger font scales, and verify screen-reader traversal.

### 10.15 Release telemetry and privacy-safe diagnostics

The architecture avoids intentionally logging credentials and redacts public links, but production diagnostics policy is not yet documented end to end.

**Recommendation:** Define structured, privacy-reviewed event/error identifiers; redact URLs, paths, usernames, tokens, share links, and response bodies by default; provide user-controlled diagnostic export.

### 10.16 Performance and storage-pressure validation

Room query/index behavior, Compose list rendering, cache retention, and backup scanning should be measured with large accounts and low-storage conditions. Quota shown for a Space is remote quota and must not be confused with device cache capacity.

---

## 11. Maintainer Guidelines

### 11.1 Adding a new server feature

1. Identify the authoritative protocol: Graph, OCS, WebDAV, OIDC, or TUS.
2. Confirm how support is advertised and add a capability field only if a feature consumes it.
3. Add a focused client in `:core:network`; do not place HTTP/XML/JSON parsing in a ViewModel.
4. Normalize remote data into domain/database state.
5. Add repository/manager policy that enforces capability and account/space scope.
6. Expose a Flow-backed immutable feature state.
7. Cover protocol parsing, policy filtering, persistence, and UI states separately.

### 11.2 Working with drives and Spaces

- Persist Graph drives by `id` and `driveType`.
- Preserve `root.webDavUrl`; do not reconstruct it.
- Use all active drives for file browsing only where the product intends that behavior.
- Use `observeProjectSpaces` for the dedicated Spaces feature.
- Exclude disabled/deleted drives from active operations.
- Never classify a drive by name such as “Personal” or “Shares.”

### 11.3 Working with resources

- Always carry `accountId`, `spaceId`, and `remoteId` through operations.
- Treat `path` as mutable addressing/display data.
- Use ETags/preconditions for destructive or overwriting operations.
- Update Room only after remote success, except when explicitly representing pending durable work.

### 11.4 Working with capabilities

- Default unknown support to false.
- Persist capability fields required by workers.
- Gate both UI affordances and manager/network execution.
- Never add a debug bypass to production logic.
- Add fixtures for missing, malformed, false, true, and enforced variants.

### 11.5 Working with background work

- Persist intent first.
- Use unique work names/tags and deduplicate equivalent active transfers.
- Make workers cancellation-cooperative.
- Checkpoint only after bytes/state are durable.
- Distinguish retryable network/server errors from authentication, permission, conflict, and source-loss failures.
- Keep foreground notifications and Android background restrictions in the release test matrix.

### 11.6 Working with secrets and TLS

- Never place passwords, access tokens, refresh tokens, or public links in Room or logs.
- Use `CredentialStore` for credentials.
- Preserve platform hostname and certificate validation.
- Scope approved pins to a specific host and SHA-256 SPKI value.
- Treat certificate rotation as an explicit user/admin trust event.

### 11.7 Working with UI modules

- Keep global chrome in the application/file-browser shell.
- Keep composables driven by explicit state and callbacks.
- Provide loading, empty, error, and populated states for data features.
- Avoid protocol calls inside composables.
- Add deterministic state fixtures and Roborazzi baselines where the screen is visually significant.

---

## 12. Suggested Next Steps

### 12.1 Recommended next phase: Release Hardening and Interoperability

The next logical phase should not be another broad feature expansion. It should be a release-hardening phase focused on proving the current architecture under production conditions.

#### Priority 0 — release blockers

1. Run the complete unit, Room migration, lint, formatting, Roborazzi verification, and debug/release assembly suite from a clean environment.
2. Test authentication against every supported identity topology, including static, WebFinger-advertised, and dynamically registered OIDC client requirements.
3. Decide whether dynamic client registration is required for the first release; implement it or document incompatible server configurations.
4. Explicitly disable or constrain unverified cross-space move/copy behavior.
5. Validate public-link password/expiration enforcement against representative oCIS policy configurations.
6. Test transfer resume, process death, reboot, expired credentials, quota exhaustion, cancellation, and low-storage behavior on physical devices.
7. Validate Room migrations from every build distributed to testers.

#### Priority 1 — quality and operability

1. Move user-visible strings into localized resources and complete accessibility testing.
2. Add privacy-safe structured diagnostics and a support export path.
3. Add a capability refresh strategy and server-version fixture suite.
4. Expand DocumentsProvider concurrency, cancellation, and stale-cache tests.
5. Add macrobenchmark/baseline-profile coverage for startup, large folder rendering, search, and navigation.
6. Verify backup behavior under scoped storage, revoked URI permissions, charging/Wi-Fi constraints, duplicate media, and destination changes.

#### Priority 2 — post-release evolution

1. Evaluate Graph delta/change synchronization for scalable reconciliation.
2. Implement a durable cross-space transfer workflow if required by product scope.
3. Add richer Space administration only when supported by stable server contracts—creation, membership, roles, quota administration, and lifecycle operations should not be inferred from the current read/open flow.
4. Standardize dependency lifetimes through an application component or an already-approved DI framework.
5. Decide whether and how to support migration from the legacy application.

### 12.2 Proposed release gate

A release candidate should be accepted only when:

- all automated checks pass without updating goldens incidentally;
- no debug capability bypasses or sensitive logs remain;
- authentication succeeds across the supported server matrix;
- project-only Spaces filtering is covered by regression tests;
- unsupported capabilities remove or block their operations;
- large transfers survive interruption and resume safely;
- offline and DocumentsProvider behavior is validated on physical devices;
- database migrations are verified;
- security and privacy review signs off on credential, TLS, sharing, and diagnostics behavior;
- known limitations are included in release notes.

---

## 13. Architectural Decision Summary

| Decision | Rationale | Consequence |
|---|---|---|
| Build a separate Kotlin/Compose application | Avoid inheriting legacy UI/database coupling | Requires explicit coexistence/migration product policy. |
| Use Room as UI source of truth | Offline behavior and process recovery | Remote responses must be normalized and persisted. |
| Model resources by account + space + remote ID | Correct oCIS drive identity | More context must be carried through every operation. |
| Discover drives through Libre Graph | Correct drive metadata/type/root source | Graph availability is foundational to browsing. |
| Keep WebDAV for file operations | It remains the oCIS file data plane | DAV clients must be rooted in Graph-advertised URLs. |
| Keep OCS for capabilities and sharing | oCIS exposes supported compatibility contracts | Capability parsing and sharing must be tested against server variants. |
| Use Graph follow/unfollow for favorites | Align favorite mutation with Libre Graph | Local favorite state requires synchronization/reconciliation. |
| Persist transfer intent and use WorkManager | Survive process death and constraints | Worker/database consistency becomes a core invariant. |
| Use typed DataStore for settings | Validation and schema safety | New settings require repository/schema updates. |
| Encrypt credentials with Android Keystore | Separate secrets from general persistence | Keystore loss/invalidation requires reauthentication handling. |
| Permit only host-scoped SPKI pin approval | Preserve TLS security | Certificate rotation needs explicit trust UX. |
| Filter project Spaces at repository consumption | Preserve complete drive data for other features | Features must choose the correct repository projection. |

---

## 14. Conclusion

The Phase 1–6 modernization replaces a legacy-client mental model with an oCIS-native one while retaining the Android behaviors users depend on: offline access, resilient transfers, multi-account operation, system document access, search, favorites, trash, sharing, and background backup.

The key maintenance lesson is that **oCIS is not “WebDAV with a different base URL.”** It is a composition of service contracts. Libre Graph defines drives and selected metadata operations; WebDAV remains the file transport; OCS remains relevant for capabilities and sharing; OIDC/WebFinger define identity discovery; TUS improves resumable upload; Room and WorkManager provide the Android durability model.

Phase 6 completes the planned modernization arc by giving project Spaces a dedicated, correctly filtered UI without damaging the broader all-drive model used by file browsing. The architecture is now coherent enough to stabilize rather than continue restructuring. The highest-value next work is release proof: interoperability, migration, security, accessibility, performance, and failure-recovery testing.

Maintainers should preserve the explicit boundaries established here. New functionality should be added by identifying the authoritative server contract, persisting normalized state, enforcing capabilities, and integrating through focused feature modules—not by reintroducing global path assumptions, synthetic roots, direct network-owned UI state, or compatibility bypasses.