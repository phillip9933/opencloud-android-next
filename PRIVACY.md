# Privacy

OpenCloud Android Next connects to the server and identity provider you configure. File contents, names and account information are sent to those services when needed for your requested operations. The app does not operate its own analytics service or advertising backend.

Files and upload staging are kept in private app storage. Temporary copies can be removed through Settings; files deliberately pinned offline are handled separately. Uninstalling deletes private local data. Device backup/transfer exclusions are declared. App locking is an optional access control, not separate encryption of each file.

Scanning runs locally through the bundled SDK. Avatars are fetched from your server, not Gravatar. Android may include or omit photo metadata; this app does not strip metadata or promise anonymization. Notifications may show filenames. Android notification settings control visibility.

Optional local diagnostics are disabled by default and use a bounded set of event types and timestamps. Disabling diagnostics clears their history. Explicitly opening/sending files or browser links transfers control to the app/site you choose; those recipients have their own policies.

Server administrators and identity providers control their own retention and logging. Review their policies separately. Please exclude private documents, passwords, tokens and server logs containing secrets from public bug reports.
