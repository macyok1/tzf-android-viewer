# Trimble X7 scan import

## Goal

Allow a user working in an existing TZF Viewer project to start a scan on a
connected Trimble X7, wait for it to finish, download its TZF file into the
local project, and open it in the viewer.

## Scope

The viewer keeps its existing local project model. It does not create or
rename projects on the scanner. Before a scan, it lists the scanner projects
and asks the user to select one. The selected scanner project supplies the
project name, UUID, and next scan ID required by the X7 scan request.

## Protocol boundary

The X7 is reached at its Wi-Fi address (currently `192.168.43.1`). Its
control API is plain HTTP on port `34567`; scan files are downloaded with
passive FTP on port `21`.

The implementation must:

1. Request `/api/v1/projects` and present its projects.
2. Request the selected project's scan configuration.
3. Submit `POST /api/v1/scans` using the returned configuration.
4. Poll the task/status endpoints until the scan is completed or fails.
5. Locate the newly completed TZF record for that scanner project.
6. Use the scanner's observed FTP workflow to download the TZF to the local
   project directory.
7. Add the downloaded file as a scan in the current project, persist the
   project, and load it into the existing viewer pipeline.

Credentials and Wi-Fi secrets observed in traffic are not committed, logged,
or displayed. Connection settings are stored only in application-private
storage.

## UI and state

The workspace gets a `Scan X7` action. It opens a scanner-project selector;
the current local project remains the destination. After selection, the
action is disabled while a compact status reports the phase: connecting,
starting, scanning with percentage, preparing download, downloading with
byte progress, and opening. A failure leaves the local project unchanged and
shows an actionable error. A successful import creates a local scan node and
opens it immediately.

## Error handling

The flow must stop safely on: no Wi-Fi route to X7, API timeout/non-success
response, incompatible/missing selected project configuration, failed scan
task, missing TZF metadata, FTP negotiation/authentication failure,
interrupted download, or invalid downloaded TZF. Partial downloads are
deleted and never added to the project.

## Validation

Unit tests will cover API JSON parsing, project-to-scan configuration mapping,
task state handling, file-name validation, and local import behavior. A
manual device test will cover a successful real scan, cancellation/network
loss, scanner task failure, and reopening the local project after an import.
